package com.example.readersmusic.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.media.app.NotificationCompat
import com.example.readersmusic.MainActivity
import com.example.readersmusic.MediaProgress
import com.example.readersmusic.Music
import com.example.readersmusic.MusicLoader
import com.example.readersmusic.MusicType
import com.example.readersmusic.R
import com.example.readersmusic.ServiceCallbacks

public enum class PlaybackStatus {
    PLAYING, PAUSED
}

public class MusicPlayerService : Service(), OnCompletionListener, OnPreparedListener,
    MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {
    var serviceCallbacks: ServiceCallbacks? = null

    // Media player variables
    var mediaPlayer: MediaPlayer? = null
        private set
    private var loader: MusicLoader? = null
    private var currentMusic: Music? = null
    private var pausePosition = 0
    private val iBinder: IBinder = LocalBinder()
    private var currentVolume = 0f
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    var transportControls: MediaControllerCompat.TransportControls? = null
        private set
    private var mediaProgressThread: Thread? = null



    // Phone call handler variables
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    // media progress observer
    var mediaProgress: MediaProgress? = null

    // Handle change of audio output using broadcast receiver
    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            transportControls!!.pause()
        }
    }

    // Main function whens calling the service
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val musicType = intent.getSerializableExtra("type") as MusicType?
        if (mediaSessionManager == null) {
            setMediaFile(musicType)
            initMediaSession()
            initMediaPlayer()
            Log.i("notif", "reached2!")
            val notification = buildNotification(PlaybackStatus.PLAYING)
            startForeground(NOTIFICATION_ID, notification)
        }
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        phoneCallStateListener()
        registerNewAudioReceiver()
        registerBecomingNoisyReceiver()
    }

    override fun onDestroy() {
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        if (phoneStateListener != null) {
            telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        removeNotification()
        unregisterReceiver(broadcastReceiver)
        unregisterReceiver(becomingNoisyReceiver)
        Log.i("MPS", "Service destroyed.")
        super.onDestroy()
    }

    // Initialize media session manager
    private fun initMediaSession() {
        if (mediaSessionManager != null) return
        // initializations
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        transportControls = mediaSession!!.controller.transportControls
        // activate media session
        mediaSession!!.isActive = true
        //set session metadata
        updateMetaData()
        // set callbacks
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
                if (serviceCallbacks != null) serviceCallbacks!!.changePlayBtnState(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
                if (serviceCallbacks != null) serviceCallbacks!!.changePlayBtnState(PlaybackStatus.PAUSED)
            }

            override fun onStop() {
                super.onStop()
                Log.d("stop", "stopping media")
                stopMedia()
                stopSelf()
                removeNotification()
                if (serviceCallbacks != null) serviceCallbacks!!.changeControllerVisibility(false)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }
        })
    }

    private fun skipToNext() {
        currentMusic = loader!!.musicInOrder
        stopMedia()
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        val previous = loader!!.lastMusic ?: return
        currentMusic = previous
        stopMedia()
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun updateMetaData() {
        // set metadata for media session
        mediaSession!!.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentMusic!!.cover)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMusic!!.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMusic!!.name)
                .build()
        )
    }

    // Build media session notification
    private fun buildNotification(status: PlaybackStatus): Notification {
        var notificationAction = android.R.drawable.ic_media_play
        var play_pauseAction: PendingIntent? = null
        if (status == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            play_pauseAction = playbackAction(1)
        } else if (status == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            play_pauseAction = playbackAction(0)
        }

//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//        notificationIntent.setAction(Intent. ACTION_MAIN ) ;
//        notificationIntent.setFlags(Intent. FLAG_ACTIVITY_CLEAR_TOP | Intent. FLAG_ACTIVITY_SINGLE_TOP );
//        PendingIntent resultIntent = PendingIntent.getActivity(this, 0, notificationIntent,  0);
        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setShowWhen(false) //                .setContentIntent(resultIntent)
            .setStyle(
                NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession!!.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setLargeIcon(currentMusic!!.cover)
            .setSmallIcon(R.drawable.audio_book_icon)
            .setContentTitle(currentMusic!!.name)
            .setContentText(currentMusic!!.artist)
            .setContentInfo("that")
            .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
            .addAction(notificationAction, "pause", play_pauseAction)
            .addAction(android.R.drawable.ic_media_next, "next", playbackAction(4))
            .addAction(android.R.drawable.ic_delete, "stop", playbackAction(2))
        val notification = notificationBuilder.build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID,
            notification
        )
        return notification
    }

    // Cancel media session notification
    private fun removeNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    // Callback actions for media session buttons
    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackIntent = Intent(this, MusicPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackIntent.action = ACTION_PLAY
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            1 -> {
                // Pause
                playbackIntent.action = ACTION_PAUSE
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            2 -> {
                // Stop
                playbackIntent.action = ACTION_STOP
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            3 -> {
                // Previous track
                playbackIntent.action = ACTION_PREVIOUS
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            4 -> {
                // Next track
                playbackIntent.action = ACTION_NEXT
                return PendingIntent.getService(
                    this,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            else -> {}
        }
        return null
    }

    // Handle callback actions from media session buttons
    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
            transportControls!!.play()
        } else if (actionString.equals(ACTION_PAUSE, ignoreCase = true)) {
            transportControls!!.pause()
        } else if (actionString.equals(ACTION_STOP, ignoreCase = true)) {
            transportControls!!.stop()
        } else if (actionString.equals(ACTION_NEXT, ignoreCase = true)) {
            transportControls!!.skipToNext()
        } else if (actionString.equals(ACTION_PREVIOUS, ignoreCase = true)) {
            transportControls!!.skipToPrevious()
        }
    }

    fun controllerHandleIncomingActions(action: String) {
        if (action == ACTION_PLAY) transportControls!!.play() else if (action == ACTION_PAUSE) transportControls!!.pause() else if (action == ACTION_STOP) transportControls!!.stop()
    }

    // Handle change of music type using broadcast receiver
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopMedia()
            mediaPlayer!!.reset()
            val musicType = intent.getSerializableExtra("type") as MusicType?
            setMediaFile(musicType)
            initMediaPlayer()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerNewAudioReceiver() {
        val filter = IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO)
        registerReceiver(broadcastReceiver, filter)
    }

    // Initialize media player
    private fun initMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
        mediaPlayer = MediaPlayer.create(this, currentMusic!!.source)
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnInfoListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
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

    private fun setMediaFile(musicType: MusicType?) {
        loader = MusicLoader(musicType, applicationContext)
        currentMusic = loader!!.musicInOrder
    }

    fun prepareController() {
        serviceCallbacks!!.changePlayBtnState(PlaybackStatus.PLAYING)
        serviceCallbacks!!.changeControllerVisibility(true)
        serviceCallbacks!!.setControllerTitle(currentMusic!!.name, currentMusic!!.type)
        serviceCallbacks!!.changeControllerBackgroundFromCover(currentMusic!!.cover)
    }

    fun changeVolume(volume: Float) {
        currentVolume = volume
        if (mediaPlayer != null) mediaPlayer!!.setVolume(volume, volume)
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        currentMusic = loader!!.musicInOrder
        stopMedia()
        mediaPlayer.reset()
        initMediaPlayer()
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

    // Basic functions for media player controller
    override fun onInfo(mediaPlayer: MediaPlayer, i: Int, i1: Int): Boolean {
        return false
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        playMedia()
        Log.i(javaClass.name, "Media prepared.")
        if (serviceCallbacks != null) {
            prepareController()
        }
    }

    private fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.setVolume(currentVolume, currentVolume)
            mediaPlayer!!.start()
        }
    }

    private fun stopMedia() {
        if (mediaProgress != null) mediaProgressThread!!.interrupt()
        Log.i("Here", "IAM")
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
    }

    private fun pauseMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            pausePosition = mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMedia() {
        if (mediaPlayer == null) return
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(pausePosition)
            mediaPlayer!!.setVolume(currentVolume, currentVolume)
            mediaPlayer!!.start()
        }
    }

    // Phone call handler functions
    private fun registerBecomingNoisyReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    private fun phoneCallStateListener() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> if (mediaPlayer != null) {
                        transportControls!!.pause()
                        ongoingCall = true
                    }

                    TelephonyManager.CALL_STATE_IDLE -> if (mediaPlayer != null) {
                        transportControls!!.play()
                        ongoingCall = false
                    }
                }
            }
        }
        telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    // Methods for binding service
    override fun onBind(intent: Intent): IBinder? {
        return iBinder
    }

    inner class LocalBinder : Binder() {
        val service: MusicPlayerService?
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

        // Notification variables
        private const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "200"
    }
}