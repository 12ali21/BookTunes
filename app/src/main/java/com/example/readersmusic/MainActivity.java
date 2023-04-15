package com.example.readersmusic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/*
TODO: WORRY category
TODO: Better music and type compatibility
TODO: Controller animation
TODO: a new activity that shows the list of musics
TODO: maybe compressing?
 */

public class MainActivity extends AppCompatActivity implements ServiceCallbacks{

    RecyclerView recyclerView;
    TextView controllerMusicName;
    TextView controllerMusicType;
    CardView playerCard;
    ImageButton controllerPlayButton;
    ImageButton controllerNextButton;
    ImageButton controllerPreviousButton;
    ImageView controllerCoverImage;
    ProgressBar controllerProgressBar;
    SharedPreferences sharedPreferences;
//
//    ImageButton volumeButton;
//    SeekBar volumeSeekBar;

    ConstraintLayout controllerLayout;

    MediaProgress mediaProgress;
    Thread mediaProgressObserver;

    public static final String BROADCAST_PLAY_NEW_AUDIO = "com.example.readersmusic.audioplayer.playnew";
    public static final String VOLUME_SHARED_PREFERENCES = "com.example.readersmusic.audioplayer.volumeVar";

    boolean serviceBound;
    private ServiceConnection serviceConnection;
    private MusicPlayerService playerService;

    private static boolean playing = false;
    private float currentVolume;
    View toastView;
    private Toast volumeToast;
    private ProgressBar volumeProgressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("BookTunes", MODE_PRIVATE);

        toastView =  getLayoutInflater().inflate(R.layout.toast_layout, (ViewGroup) findViewById(R.id.toast_layout));
        volumeToast = new Toast(this);
        volumeToast.setView(toastView);
        volumeProgressBar = toastView.findViewById(R.id.toast_progress);
        volumeToast.setGravity(Gravity.TOP, 0, 120);
        
        createNotificationChannel();
        // Find views by Id
        recyclerView = findViewById(R.id.cards_recyclerview);
        playerCard = findViewById(R.id.player_card);

        controllerLayout = findViewById(R.id.controller_layout);
        controllerMusicName = findViewById(R.id.controller_title);
        controllerMusicType = findViewById(R.id.controller_type);
        controllerPlayButton = findViewById(R.id.controller_play_btn);
        controllerNextButton = findViewById(R.id.controller_next_btn);
        controllerPreviousButton = findViewById(R.id.controller_previous_btn);
        controllerCoverImage = findViewById(R.id.controller_cover_img);
        controllerProgressBar = findViewById(R.id.media_progressbar);

//        volumeButton = findViewById(R.id.volume_btn);
//        volumeSeekBar = findViewById(R.id.volume_seekbar);

        currentVolume = sharedPreferences.getFloat(VOLUME_SHARED_PREFERENCES, 0f);

        playerCard.setVisibility(View.INVISIBLE);
        // Create cards details
        ArrayList<CardModel> cardModels = createCards();


        // recycler view adapter
        CardAdapter adapter = new CardAdapter(this, cardModels);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(adapter);


        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) iBinder;
                playerService = binder.getService();
                serviceBound = true;
                playerService.setCallbacks(MainActivity.this);
                playerService.prepareController();
                playerService.changeVolume(currentVolume);
//                playerService.changeVolume((float)volumeSeekBar.getProgress()/20);


                Log.i(getClass().getName(), "Service Connected.");
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                serviceBound = false;
            }
        };

        // Click listeners
        adapter.setOnClickListener(new CardAdapter.OnClickListener() {
            @Override
            public void onClick(int position, CardModel model) {
                MusicType type = MusicType.CALM;
                switch(position){
                    case 0:
                        type = MusicType.FIGHT;
                        break;
                    case 1:
                        type = MusicType.SORROW;
                        break;
                    case 2:
                        type = MusicType.CALM;
                        break;
                    case 3:
                        type = MusicType.FEAR;
                }

                playMusic(type);
            }
        });
        controllerPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(playing) {
                    playerService.controllerHandleIncomingActions(MusicPlayerService.ACTION_PAUSE);
                    playing = false;
                    changePlayBtnState(MusicPlayerService.PlaybackStatus.PAUSED);
                }
                else {
                    playerService.controllerHandleIncomingActions(MusicPlayerService.ACTION_PLAY);
                    playing = true;
                    changePlayBtnState(MusicPlayerService.PlaybackStatus.PLAYING);

                }
            }
        });

        controllerNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerService.getTransportControls().skipToNext();
            }
        });

        controllerPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerService.getTransportControls().skipToPrevious();
            }
        });

    }


    public void changePlayBtnState(MusicPlayerService.PlaybackStatus status){
        if(status == MusicPlayerService.PlaybackStatus.PLAYING){
            controllerPlayButton.setImageResource(android.R.drawable.ic_media_pause);
            playing = true;
        }
        else {
            controllerPlayButton.setImageResource(android.R.drawable.ic_media_play);
            playing = false;
        }
    }

    @Override
    public void changeControllerVisibility(boolean visible) {
        if(visible)
            playerCard.setVisibility(View.VISIBLE);
        else
            playerCard.setVisibility(View.INVISIBLE);
    }

    @Override
    public void setControllerTitle(String title) {
        controllerMusicName.setText(title);
    }

    @Override
    public void setControllerType(MusicType type) {
        controllerMusicType.setText(type.getType(this));
    }

    @Override
    public void changeControllerBackgroundFromCover(Bitmap musicCover) {
/*        Palette.from(musicCover).generate(new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getVibrantSwatch();
                if (swatch == null) swatch = palette.getMutedSwatch(); // Sometimes vibrant swatch is not available
                if (swatch != null) {
                    // Set the background color of the player bar based on the swatch color
                    controllerLayout.setBackgroundColor(swatch.getRgb());
                    controllerPlayButton.setBackgroundColor(swatch.getRgb());
                    controllerNextButton.setBackgroundColor(swatch.getRgb());
                    controllerPreviousButton.setBackgroundColor(swatch.getRgb());
                    // Update the track's title with the proper title text color
                    controllerMusicName.setTextColor(swatch.getTitleTextColor());

                    // Update the artist name with the proper body text color
                    controllerMusicType.setTextColor(swatch.getBodyTextColor());
                }
            }
        });*/
        controllerCoverImage.setImageBitmap(musicCover);
    }

    @Override
    public ProgressBar getMediaProgressBar() {
        return controllerProgressBar;
    }



    private ArrayList<CardModel> createCards() {
        ArrayList<CardModel> cardModels = new ArrayList<>();
        cardModels.add(new CardModel(R.drawable.fight, getString(R.string.type_battle)));
        cardModels.add(new CardModel(R.drawable.sorrow, getString(R.string.type_sorrow)));
        cardModels.add(new CardModel(R.drawable.peaceful, getString(R.string.type_calm)));
        cardModels.add(new CardModel(R.drawable.thunderstorm, getString(R.string.type_fear)));
        return cardModels;
    }

    private void createNotificationChannel(){
        NotificationChannel notificationChannel = new NotificationChannel(MusicPlayerService.CHANNEL_ID, "Media Player", NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(notificationChannel);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("serviceBound", serviceBound);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        serviceBound=savedInstanceState.getBoolean("serviceBound");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        if(serviceBound){
            playerService.setCallbacks(null);
            unbindService(serviceConnection);
            playerService.stopSelf();
        }

        sharedPreferences.edit()
                .putFloat(VOLUME_SHARED_PREFERENCES, currentVolume)
                .apply();

        super.onDestroy();
    }

    void playMusic(MusicType type){
        if(!serviceBound){
            Intent intent = new Intent(MainActivity.this, MusicPlayerService.class);
            intent.putExtra("type", type);
            startForegroundService(intent);
            bindService(intent, serviceConnection, 0);
        } else {
            Intent intent = new Intent(BROADCAST_PLAY_NEW_AUDIO);
            intent.putExtra("type", type);
            sendBroadcast(intent);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        float volumeStep = 1f/volumeProgressBar.getMax();

        boolean myKey = false;

        if(playerService != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_DOWN) {
                myKey = true;
                currentVolume+=volumeStep;
                if(currentVolume>1)
                    currentVolume = 1;

                playerService.changeVolume(currentVolume);

            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_DOWN) {
                myKey = true;
                currentVolume-= volumeStep;
                if(currentVolume < 0)
                    currentVolume = 0;
                playerService.changeVolume(currentVolume);

            }
            if(myKey) {
                volumeToast.show();
                Log.i(MainActivity.this.getClass().getName(), "" + currentVolume);
                volumeProgressBar.setProgress((int) (currentVolume * volumeProgressBar.getMax()));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}


