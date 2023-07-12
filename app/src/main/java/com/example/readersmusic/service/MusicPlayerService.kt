package com.example.readersmusic.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.example.readersmusic.MediaNotification
import com.example.readersmusic.MediaProgress
import com.example.readersmusic.Music
import com.example.readersmusic.MusicLoader
import com.example.readersmusic.MusicType
import com.example.readersmusic.ServiceCallbacks

enum class PlaybackStatus {
    PLAYING, PAUSED
}

class MusicPlayerService : Service(), OnCompletionListener, OnPreparedListener,
    MediaPlayer.OnErrorListener{
    var serviceCallbacks: ServiceCallbacks? = null

    // Media player variables
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var loader: MusicLoader
    private lateinit var currentMusic: Music
    private var pausePosition = 0
    private val iBinder: IBinder = LocalBinder()
    private var currentVolume = 0f
    private var mediaSessionManager: MediaSessionManager? = null
    private lateinit var mediaSession: MediaSessionCompat
    lateinit var transportControls: MediaControllerCompat.TransportControls
        private set
    private var mediaProgressThread: Thread? = null

    // media progress observer
    private var mediaProgress: MediaProgress? = null

    private lateinit var mediaNotification: MediaNotification

    // Main function when service gets called for the first time or an intent is invoked
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val musicType = intent.getSerializableExtra("type") as MusicType
        if (mediaSessionManager == null) {
            setMediaFile(musicType)
            initMediaPlayer()
            initMediaSession()
            mediaNotification = MediaNotification(this, mediaSession.sessionToken)
            val notification = mediaNotification.buildNotification(PlaybackStatus.PLAYING, currentMusic)
            startForeground(MediaNotification.NOTIFICATION_ID, notification)
            Log.i(INITIALIZATION_TAG, "Initializations finished")
        }
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        stopMedia()
        mediaPlayer.release()

        mediaNotification.removeNotification()
        Log.i("MPS", "Service destroyed.")
        super.onDestroy()
    }

    // Initialize media session and assign transport controls
    private fun initMediaSession() {
        // get the media session
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        transportControls = mediaSession.controller.transportControls
        Log.i(INITIALIZATION_TAG, "Media session created")

        // activate media session
        mediaSession.isActive = true
        Log.i(INITIALIZATION_TAG, "Media session is active")

        //set session metadata
        updateMetaData()

        // set callbacks
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                mediaNotification.buildNotification(PlaybackStatus.PLAYING, currentMusic)
                serviceCallbacks?.changePlayBtnState(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                mediaNotification.buildNotification(PlaybackStatus.PAUSED, currentMusic)
                serviceCallbacks?.changePlayBtnState(PlaybackStatus.PAUSED)
            }

            override fun onStop() {
                super.onStop()
                Log.d("stop", "stopping media")
                stopMedia()
                mediaNotification.removeNotification()
                serviceCallbacks?.changeControllerVisibility(false)
                stopSelf()
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
                updateMetaData()
                mediaNotification.buildNotification(PlaybackStatus.PLAYING, currentMusic)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                mediaNotification.buildNotification(PlaybackStatus.PLAYING, currentMusic)
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }
        })
        Log.i(INITIALIZATION_TAG, "Media session controls callback set")
    }

    private fun skipToNext() {
        currentMusic = loader.musicInOrder
        stopMedia()
        resetMediaPlayer()
    }

    private fun skipToPrevious() {
        currentMusic = loader.lastMusic
        stopMedia()
        resetMediaPlayer()
    }

    private fun updateMetaData() {
        // set metadata for media session
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentMusic.cover)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMusic.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMusic.name)
                .build()
        )
    }

    // Handle callback actions from media session buttons
    private fun handleIncomingActions(playbackAction: Intent?) {
        if ((playbackAction == null) || (playbackAction.action == null)) return

        val actionString = playbackAction.action
        when {
            actionString.equals(ACTION_PLAY, ignoreCase = true) -> {
                transportControls.play()
            }
            actionString.equals(ACTION_PAUSE, ignoreCase = true) -> {
                transportControls.pause()
            }
            actionString.equals(ACTION_STOP, ignoreCase = true) -> {
                transportControls.stop()
            }
            actionString.equals(ACTION_NEXT, ignoreCase = true) -> {
                transportControls.skipToNext()
            }
            actionString.equals(ACTION_PREVIOUS, ignoreCase = true) -> {
                transportControls.skipToPrevious()
            }
        }
    }

    fun changeMusicType(type: MusicType){
        setMediaFile(type)
        resetMediaPlayer()
    }

    fun controllerHandleIncomingActions(action: String) {
        if (action == ACTION_PLAY) transportControls.play() else if (action == ACTION_PAUSE) transportControls.pause() else if (action == ACTION_STOP) transportControls.stop()
    }

    private fun resetMediaPlayer(){
        mediaPlayer.reset()
        initMediaPlayer()
    }
    // Initialize media player
    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, currentMusic.source)
        Log.i(INITIALIZATION_TAG, "Media player created")
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnErrorListener(this)
        mediaPlayer.setOnPreparedListener(this)
        if (serviceCallbacks != null) {
            initMediaProgressBar()
        }
    }

    private fun initMediaProgressBar() {
        if (serviceCallbacks == null) return
        mediaProgress = MediaProgress(mediaPlayer, serviceCallbacks!!.mediaProgressBar)
        mediaProgressThread = Thread(mediaProgress)
        mediaProgressThread!!.start()
    }

    //Initialize media loader and set the first music
    private fun setMediaFile(musicType: MusicType) {
        loader = MusicLoader(musicType, applicationContext)
        currentMusic = loader.musicInOrder
        Log.i(INITIALIZATION_TAG, "Media file set: ${currentMusic.name}")
    }

    fun changeVolume(volume: Float) {
        currentVolume = volume
        mediaPlayer.setVolume(volume, volume)
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        currentMusic = loader.musicInOrder
        stopMedia()
        resetMediaPlayer()
    }

    override fun onError(mediaPlayer: MediaPlayer, i: Int, i1: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation
        when (i) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $i1"
            )

            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR SERVER DIED $i1"
            )

            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $i1")
        }
        return false
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        playMedia()
        Log.i(javaClass.name, "Media prepared.")
        prepareController()
    }
    fun prepareController() {
        serviceCallbacks?.changePlayBtnState(PlaybackStatus.PLAYING)
        serviceCallbacks?.changeControllerVisibility(true)
        serviceCallbacks?.setControllerTitle(currentMusic.name, currentMusic.type)
        serviceCallbacks?.changeControllerBackgroundFromCover(currentMusic.cover)
    }

    private fun playMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.setVolume(currentVolume, currentVolume)
            mediaPlayer.start()
        }
    }

    private fun stopMedia() {
        if (mediaProgress != null) mediaProgressThread!!.interrupt()
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
    }

    private fun pauseMedia() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            pausePosition = mediaPlayer.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.seekTo(pausePosition)
            mediaPlayer.setVolume(currentVolume, currentVolume)
            mediaPlayer.start()
        }
    }

    // Methods for binding service
    override fun onBind(intent: Intent): IBinder {
        return iBinder
    }

    inner class LocalBinder : Binder() {
        val service: MusicPlayerService
            get() = this@MusicPlayerService
    }

    fun setCallbacks(callbacks: ServiceCallbacks?) {
        serviceCallbacks = callbacks
        initMediaProgressBar()
        Log.i(javaClass.name, "Callbacks set.")
    }

    companion object {
        //Media session manager variables
        const val ACTION_PLAY = "AudioPlayer.ACTION_PLAY"
        const val ACTION_PAUSE = "AudioPlayer.ACTION_PAUSE"
        const val ACTION_STOP = "AudioPlayer.ACTION_STOP"
        const val ACTION_NEXT = "AudioPlayer.ACTION_NEXT"
        const val ACTION_PREVIOUS = "AudioPlayer.ACTION_PREVIOUS"

        private const val INITIALIZATION_TAG = "Media Initialization"
    }
}