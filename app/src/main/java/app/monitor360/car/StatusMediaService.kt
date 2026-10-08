package app.monitor360.car

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import app.monitor360.app
import app.monitor360.data.Monitor
import app.monitor360.data.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

class StatusMediaService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var cached: Pair<Long, StatusData>? = null
    private var lastSpoken: Pair<String, String>? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, SESSION_TAG).apply {
            setCallback(callback)
            setPlaybackState(state(PlaybackStateCompat.STATE_STOPPED))
            isActive = true
        }
        sessionToken = session.sessionToken
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.ENGLISH
                tts?.setAudioAttributes(audioAttributes())
                tts?.setOnUtteranceProgressListener(progress)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        tts?.shutdown()
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        CarConnection.recordMedia(applicationContext, clientPackageName)
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, STYLE_LIST)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, STYLE_LIST)
        }
        return BrowserRoot(StatusTree.ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        scope.launch {
            val data = load()
            result.sendResult(StatusTree.children(parentId, data).map { it.toMediaItem() }.toMutableList())
        }
    }

    private suspend fun load(): StatusData? {
        val app = applicationContext.app
        val prefs = app.settings.current
        if (!prefs.isConfigured) return null
        cached?.let { (time, data) -> if (System.currentTimeMillis() - time < CACHE_MS) return data }
        val (servers, monitors) = coroutineScope {
            val s = async { runCatching { app.repo.servers() } }
            val m = async { runCatching { app.repo.monitors() } }
            s.await() to m.await()
        }
        val last = cached?.second
        val data = StatusData(
            servers.getOrNull() ?: last?.servers ?: emptyList<Server>(),
            monitors.getOrNull() ?: last?.monitors ?: emptyList<Monitor>(),
            prefs,
        )
        cached = System.currentTimeMillis() to data
        return data
    }

    private fun TreeItem.toMediaItem(): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        val flag = if (browsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        return MediaBrowserCompat.MediaItem(description, flag)
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            scope.launch {
                val data = load()
                val text = StatusTree.speech(mediaId, data)
                val title = StatusTree.children(StatusTree.ROOT, data).firstOrNull { it.id == mediaId }?.title ?: mediaId.substringAfter(':')
                speak(mediaId, title, text)
            }
        }

        override fun onPlay() {
            lastSpoken?.let { (id, title) -> onPlayFromMediaId(id, null).also { session.setMetadata(metadata(title)) } }
                ?: onPlayFromMediaId(StatusTree.OVERVIEW, null)
        }

        override fun onStop() {
            tts?.stop()
            release()
        }

        override fun onPause() = onStop()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = release()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = release()
    }

    private fun speak(id: String, title: String, text: String) {
        lastSpoken = id to title
        session.setMetadata(metadata(title))
        val engine = tts
        if (engine == null || !ttsReady) {
            session.setPlaybackState(state(PlaybackStateCompat.STATE_ERROR, "Text to speech is not available"))
            return
        }
        val audio = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes())
            .build()
        focusRequest = request
        audio.requestAudioFocus(request)
        session.setPlaybackState(state(PlaybackStateCompat.STATE_PLAYING))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "status")
    }

    private fun release() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        session.setPlaybackState(state(PlaybackStateCompat.STATE_STOPPED))
    }

    private fun metadata(title: String) = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "360 Monitor")
        .build()

    private fun state(code: Int, error: String? = null): PlaybackStateCompat = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE,
        )
        .setState(code, 0, 1f)
        .apply { if (error != null) setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, error) }
        .build()

    private fun audioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private companion object {
        const val SESSION_TAG = "360Monitor"
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val STYLE_LIST = 1
        const val CACHE_MS = 20_000L
    }
}
