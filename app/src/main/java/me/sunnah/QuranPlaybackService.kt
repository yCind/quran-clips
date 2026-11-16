package me.sunnah

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class QuranPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val persist = object : Runnable {
        override fun run() {
            player?.let { p ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("url", p.currentMediaItem?.localConfiguration?.uri?.toString())
                    .putString("title", p.mediaMetadata.title?.toString())
                    .putString("artist", p.mediaMetadata.artist?.toString())
                    .putLong("position", p.currentPosition)
                    .apply()
            }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) { saveState() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { saveState() }
                override fun onPlayerError(error: PlaybackException) { saveState(); stopSelf() }
            })
        }
        mediaSession = MediaSession.Builder(this, player!!).build()
        handler.post(persist)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank() && url.startsWith("https://")) {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "القرآن الكريم"
                    val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "حسنة"
                    val item = MediaItem.Builder().setUri(url).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
                    ).build()
                    player?.setMediaItem(item)
                    player?.prepare()
                    player?.play()
                }
            }
            ACTION_RESTORE -> restoreSavedTrack(false)
            ACTION_PAUSE -> player?.pause()
            ACTION_RESUME -> {
                if (player?.currentMediaItem == null) restoreSavedTrack(true)
                else { if (player?.playbackState == Player.STATE_IDLE) player?.prepare(); player?.play() }
            }
            ACTION_STOP -> { saveState(); player?.pause(); stopSelf() }
            ACTION_SEEK -> player?.seekTo(intent.getLongExtra(EXTRA_POSITION, 0L))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun restoreSavedTrack(autoPlay: Boolean) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val url = prefs.getString("url", null) ?: return
        val title = prefs.getString("title", "القرآن الكريم") ?: "القرآن الكريم"
        val artist = prefs.getString("artist", "حسنة") ?: "حسنة"
        val position = prefs.getLong("position", 0L)
        val p = player ?: return
        if (p.currentMediaItem == null) {
            p.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
            ).build())
            p.seekTo(position)
        }
        if (autoPlay) { p.prepare(); p.play() }
    }

    private fun saveState() {
        player?.let { p ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("url", p.currentMediaItem?.localConfiguration?.uri?.toString())
                .putString("title", p.mediaMetadata.title?.toString())
                .putString("artist", p.mediaMetadata.artist?.toString())
                .putLong("position", p.currentPosition).apply()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onTaskRemoved(rootIntent: Intent?) { saveState(); if (player?.isPlaying != true) stopSelf() }
    override fun onDestroy() {
        handler.removeCallbacks(persist); saveState()
        player?.release()
        mediaSession?.release()
        mediaSession = null; player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "me.sunnah.PLAY"
        const val ACTION_RESTORE = "me.sunnah.RESTORE"
        const val ACTION_PAUSE = "me.sunnah.PAUSE"
        const val ACTION_RESUME = "me.sunnah.RESUME"
        const val ACTION_STOP = "me.sunnah.STOP"
        const val ACTION_SEEK = "me.sunnah.SEEK"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_POSITION = "position"
        private const val PREFS = "quran_playback"
    }
}
