package com.example.readersmusic;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


public class MusicPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener{

    ServiceCallbacks serviceCallbacks;

    // Media player variables
    private MediaPlayer mediaPlayer;
    private MusicLoader loader;
    private Music currentMusic;
    private int pausePosition;
    private final IBinder iBinder = new LocalBinder();
    private float currentVolume;

    //Media session manager variables
    public static final String ACTION_PLAY = "AudioPlayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "AudioPlayer.ACTION_PAUSE";
    public static final String ACTION_STOP = "AudioPlayer.ACTION_STOP";
    public static final String ACTION_NEXT = "AudioPlayer.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "AudioPlayer.ACTION_PREVIOUS";

    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;


    // Notification variables
    private static final int NOTIFICATION_ID = 101;
    public static final String CHANNEL_ID = "200";
    private Thread mediaProgressThread;


    public enum PlaybackStatus{PLAYING, PAUSED}


    // Phone call handler variables
    private boolean ongoingCall;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    // media progress observer
    MediaProgress mediaProgress;


    // Handle change of audio output using broadcast receiver
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            transportControls.pause();
        }
    };

    // Main function whens calling the service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MusicType musicType = (MusicType) intent.getSerializableExtra("type");

        if (mediaSessionManager == null) {
            setMediaFile(musicType);
            initMediaSession();
            initMediaPlayer();
            Log.i("notif", "reached2!");
            Notification notification = buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
        }
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        phoneCallStateListener();
        registerNewAudioReceiver();
        registerBecomingNoisyReceiver();
    }

    @Override
    public void onDestroy() {
        if(mediaPlayer!=null) {
            stopMedia();
            mediaPlayer.release();
        }


        if(phoneStateListener != null){
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        unregisterReceiver(broadcastReceiver);
        unregisterReceiver(becomingNoisyReceiver);

        Log.i("MPS", "Service destroyed.");
        super.onDestroy();
    }



    // Initialize media session manager
    private void initMediaSession(){
        if(mediaSessionManager != null)
            return;
        // initializations
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        // activate media session
        mediaSession.setActive(true);
        //set session metadata
        updateMetaData();
        // set callbacks
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
                if(serviceCallbacks!=null)
                    serviceCallbacks.changePlayBtnState(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
                if(serviceCallbacks!=null)
                    serviceCallbacks.changePlayBtnState(PlaybackStatus.PAUSED);
            }

            @Override
            public void onStop() {
                super.onStop();
                Log.d("stop", "stopping media");
                stopMedia();
                stopSelf();
                removeNotification();
                if(serviceCallbacks!=null)
                    serviceCallbacks.changeControllerVisibility(false);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }
        });
    }

    private void skipToNext() {
        currentMusic = loader.getMusicInOrder();
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }
    private void skipToPrevious(){
        Music previous = loader.getLastMusic();
        if(previous == null)
            return;
        currentMusic = previous;
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void updateMetaData() {
        // set metadata for media session
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentMusic.getCover())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMusic.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMusic.getName())
                .build());

    }

    // Build media session notification
    private Notification buildNotification(PlaybackStatus status) {
        int notificationAction = android.R.drawable.ic_media_play;
        PendingIntent play_pauseAction = null;

        if(status == PlaybackStatus.PLAYING){
            notificationAction = android.R.drawable.ic_media_pause;
            play_pauseAction = playbackAction(1);
        } else if (status == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            play_pauseAction = playbackAction(0);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.setAction(Intent. ACTION_MAIN ) ;
        notificationIntent.setFlags(Intent. FLAG_ACTIVITY_CLEAR_TOP | Intent. FLAG_ACTIVITY_SINGLE_TOP );
        PendingIntent resultIntent = PendingIntent.getActivity(this, 0, notificationIntent,  0);


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setShowWhen(false)
                .setContentIntent(resultIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setLargeIcon(currentMusic.getCover())
                .setSmallIcon(R.drawable.audio_book_icon)
                .setContentTitle(currentMusic.getName())
                .setContentText(currentMusic.getArtist())
                .setContentInfo("that")
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(4))
                .addAction(android.R.drawable.ic_delete, "stop", playbackAction(2));
        Notification notification = notificationBuilder.build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification);

        return notification;
    }

    // Cancel media session notification
    private void removeNotification() {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
    }

    // Callback actions for media session buttons
    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackIntent = new Intent(this, MusicPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackIntent.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackIntent, 0);
            case 1:
                // Pause
                playbackIntent.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackIntent, 0);
            case 2:
                // Stop
                playbackIntent.setAction(ACTION_STOP);
                return PendingIntent.getService(this, actionNumber, playbackIntent, 0);
            case 3:
                // Previous track
                playbackIntent.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackIntent, 0);
            case 4:
                // Next track
                playbackIntent.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackIntent, 0);

            default:
                break;
        }
        return null;
    }

    // Handle callback actions from media session buttons
    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        }
    }

    public void controllerHandleIncomingActions(String action){
        if(action.equals(ACTION_PLAY))
            transportControls.play();
        else if(action.equals(ACTION_PAUSE))
            transportControls.pause();
        else if(action.equals(ACTION_STOP))
            transportControls.stop();
    }


    // Handle change of music type using broadcast receiver
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopMedia();
            mediaPlayer.reset();

            MusicType musicType = (MusicType) intent.getSerializableExtra("type");
            setMediaFile(musicType);

            initMediaPlayer();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    private void registerNewAudioReceiver(){
        IntentFilter filter = new IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO);
        registerReceiver(broadcastReceiver, filter);
    }


    // Initialize media player
    private void initMediaPlayer(){
        if(mediaPlayer != null){
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = MediaPlayer.create(this, currentMusic.getSource());
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnPreparedListener(this);

        if(serviceCallbacks != null){
            initMediaProgressBar();
        }
    }

    private void initMediaProgressBar(){
        if(serviceCallbacks == null)
            return;
        mediaProgress = new MediaProgress(mediaPlayer, serviceCallbacks.getMediaProgressBar());
        mediaProgressThread = new Thread(mediaProgress);
        mediaProgressThread.start();
    }

    private void setMediaFile(MusicType musicType){
        loader = new MusicLoader(musicType, getApplicationContext());
        currentMusic = loader.getMusicInOrder();
    }

    public void prepareController(){
        serviceCallbacks.changePlayBtnState(PlaybackStatus.PLAYING);
        serviceCallbacks.changeControllerVisibility(true);
        serviceCallbacks.setControllerTitle(currentMusic.getName());
        serviceCallbacks.setControllerType(currentMusic.getType());
        serviceCallbacks.changeControllerBackgroundFromCover(currentMusic.getCover());
    }

    public void changeVolume(float volume){
        currentVolume = volume;
        if(mediaPlayer != null)
            mediaPlayer.setVolume(volume, volume);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        currentMusic = loader.getMusicInOrder();
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        //Invoked when there has been an error during an asynchronous operation
        switch (i) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + i1);
                break;
        }
        return false;
    }


    // Basic functions for media player controller
    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        playMedia();
        Log.i(getClass().getName(), "Media prepared.");
        if(serviceCallbacks != null) {
            prepareController();
        }
    }



    private void playMedia(){
        if(!mediaPlayer.isPlaying()){
            mediaPlayer.setVolume(currentVolume, currentVolume);
            mediaPlayer.start();
        }
    }

    private void stopMedia(){
        if(mediaProgress!=null)
            mediaProgressThread.interrupt();
        Log.i("Here", "IAM");

        if(mediaPlayer==null)
            return;
        if(mediaPlayer.isPlaying())
            mediaPlayer.stop();
    }

    private void pauseMedia(){
        if(mediaPlayer==null) return;
        if(mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            pausePosition = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia(){
        if(mediaPlayer==null) return;
        if(!mediaPlayer.isPlaying()){
            mediaPlayer.seekTo(pausePosition);
            mediaPlayer.setVolume(currentVolume, currentVolume);
            mediaPlayer.start();
        }
    }


    // Phone call handler functions
    private void registerBecomingNoisyReceiver(){
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    private void phoneCallStateListener(){
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state){
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if(mediaPlayer!=null){
                            transportControls.pause();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if(mediaPlayer!=null){
                            transportControls.play();
                            ongoingCall = false;
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }


    // Methods for binding service
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    public class LocalBinder extends Binder {
        public MusicPlayerService getService(){
            return MusicPlayerService.this;
        }
    }

    public void setCallbacks(ServiceCallbacks callbacks){
        serviceCallbacks = callbacks;
        initMediaProgressBar();
        Log.i(getClass().getName(), "Callbacks set.");
    }

    public MediaControllerCompat.TransportControls getTransportControls() {
        return transportControls;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
}
