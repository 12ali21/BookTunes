package com.example.readersmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readersmusic.MusicCard.CardAdapter
import com.example.readersmusic.MusicCard.CardModel
import com.example.readersmusic.service.MusicPlayerService
import com.example.readersmusic.service.MusicPlayerService.LocalBinder
import com.example.readersmusic.service.PlaybackStatus

/*
TODO: Better music and type compatibility
TODO: Controller animation
TODO: a new activity that shows the list of musics
TODO: maybe compressing?
 */
class MainActivity : AppCompatActivity(), ServiceCallbacks {
    var playerService: MusicPlayerService? = null

    lateinit var recyclerView: RecyclerView
    lateinit var controllerMusicName: TextView
    lateinit var controllerMusicType: TextView
    lateinit var playerCard: CardView
    lateinit var controllerPlayButton: ImageButton
    lateinit var controllerNextButton: ImageButton
    lateinit var controllerPreviousButton: ImageButton
    lateinit var controllerCoverImage: ImageView
    lateinit var controllerProgressBar: ProgressBar
    lateinit var sharedPreferences: SharedPreferences

    //
    //    ImageButton volumeButton;
    //    SeekBar volumeSeekBar;



    var controllerLayout: ConstraintLayout? = null
    var mediaProgress: MediaProgress? = null
    var mediaProgressObserver: Thread? = null
    var serviceBound = false
    private var serviceConnection: ServiceConnection? = null
    private var currentVolume = 0f
    var toastView: View? = null
    private var volumeToast: Toast? = null
    private var volumeProgressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load shared preferences
        sharedPreferences = getSharedPreferences(SHARED_PREFERENCES, MODE_PRIVATE)

        // View bindings
        recyclerView = findViewById(R.id.cards_recyclerview)
        playerCard = findViewById(R.id.player_card)
        controllerLayout = findViewById(R.id.controller_layout)
        controllerMusicName = findViewById(R.id.controller_title)
        controllerMusicType = findViewById(R.id.controller_type)
        controllerPlayButton = findViewById(R.id.controller_play_btn)
        controllerNextButton = findViewById(R.id.controller_next_btn)
        controllerPreviousButton = findViewById(R.id.controller_previous_btn)
        controllerCoverImage = findViewById(R.id.controller_cover_img)
        controllerProgressBar = findViewById(R.id.media_progressbar)

        // Hide player
        playerCard.visibility = View.INVISIBLE

        // Establish service connection
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                val binder = iBinder as LocalBinder
                playerService = binder.service
                serviceBound = true
                playerService?.setCallbacks(this@MainActivity)
                playerService?.prepareController()
                playerService?.changeVolume(currentVolume)
                Log.i(javaClass.name, "Service connection established.")
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                serviceBound = false
            }
        }

        createNotificationChannel()

        // FIXME: Remove this after implementing it in music activity
        // Creating volume interface
        /*toastView = findViewById(R.layout.toast_layout)
        volumeToast = Toast(this)
        volumeToast!!.view = toastView
        volumeProgressBar = toastView!!.findViewById(R.id.toast_progress)
        volumeToast!!.setGravity(Gravity.TOP, 0, 120)

        currentVolume = sharedPreferences.getFloat(VOLUME_SHARED_PREFERENCES, 0f)
        */

        // Create cards details
        val cardModels = createCards()

        // Connect recycler view adapter
        val adapter = CardAdapter(this, cardModels)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        // Recycler view cards listener
        adapter.setOnClickListener { position, model ->
            playMusic(model.type)
        }

        // controller buttons listeners
        controllerPlayButton.setOnClickListener(View.OnClickListener {
            if (playing) {
                playerService!!.controllerHandleIncomingActions(MusicPlayerService.ACTION_PAUSE)
                playing = false
                changePlayBtnState(PlaybackStatus.PAUSED)
            } else {
                playerService!!.controllerHandleIncomingActions(MusicPlayerService.ACTION_PLAY)
                playing = true
                changePlayBtnState(PlaybackStatus.PLAYING)
            }
        })
        controllerNextButton.setOnClickListener(View.OnClickListener { playerService!!.transportControls?.skipToNext() })
        controllerPreviousButton.setOnClickListener(View.OnClickListener { playerService!!.transportControls?.skipToPrevious() })
    }

    override fun changePlayBtnState(status: PlaybackStatus) {
        playing = if (status == PlaybackStatus.PLAYING) {
            controllerPlayButton.setImageResource(android.R.drawable.ic_media_pause)
            true
        } else {
            controllerPlayButton.setImageResource(android.R.drawable.ic_media_play)
            false
        }
    }

    override fun changeControllerVisibility(toVisible: Boolean) {
        if (toVisible) playerCard.visibility = View.VISIBLE else playerCard.visibility =
            View.INVISIBLE
    }

    override fun setControllerTitle(title: String, type: MusicType) {
        controllerMusicName.text = title
        controllerMusicType.text = type.getType(this)
    }


    override fun changeControllerBackgroundFromCover(musicCover: Bitmap) {
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
        controllerCoverImage.setImageBitmap(musicCover)
    }

    override fun getMediaProgressBar(): ProgressBar {
        return controllerProgressBar
    }

    private fun createCards(): ArrayList<CardModel> {
        val cardModels = ArrayList<CardModel>()
        cardModels.add(CardModel(R.drawable.fight,getString(R.string.type_battle), MusicType.Battle))
        cardModels.add(CardModel(R.drawable.sorrow, getString(R.string.type_sorrow), MusicType.SORROW))
        cardModels.add(CardModel(R.drawable.peaceful, getString(R.string.type_calm), MusicType.CALM))
        cardModels.add(CardModel(R.drawable.thunderstorm, getString(R.string.type_fear), MusicType.FEAR))
        return cardModels
    }

    private fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            MusicPlayerService.CHANNEL_ID,
            "Media Player",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationChannel.importance = NotificationManager.IMPORTANCE_LOW
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            notificationChannel
        )
    }

    // Retain serviceBound boolean on state change
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("serviceBound", serviceBound)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        serviceBound = savedInstanceState.getBoolean("serviceBound")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onDestroy() {
        // Stop and disconnect service
        if (serviceBound) {
            playerService?.setCallbacks(null)
            playerService?.stopSelf()
            unbindService(serviceConnection!!)
        }
        // Keep current volume as shared preferences
        sharedPreferences.edit()
            .putFloat(VOLUME_SHARED_PREFERENCES, currentVolume)
            .apply()
        super.onDestroy()
    }

    private fun playMusic(type: MusicType?) {
        // check service connection
        if (!serviceBound) { // create and connect service for the first time
            val intent = Intent(this@MainActivity, MusicPlayerService::class.java)
            intent.putExtra("type", type)
            startForegroundService(intent)
            bindService(intent, serviceConnection!!, 0)
        } else {
            //FIXME: find a replacement for broadcasting method
            val intent = Intent(BROADCAST_PLAY_NEW_AUDIO)
            intent.putExtra("type", type)
            sendBroadcast(intent)
        }
    }

    //FIXME: remove this code after implementing volume in music activity
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
//        val volumeStep = 1f / volumeProgressBar!!.max
        val volumeStep = 0.1f
        var myKey = false
        if (playerService != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_DOWN) {
                myKey = true
                currentVolume += volumeStep
                if (currentVolume > 1) currentVolume = 1f
                playerService!!.changeVolume(currentVolume)
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_DOWN) {
                myKey = true
                currentVolume -= volumeStep
                if (currentVolume < 0) currentVolume = 0f
                playerService!!.changeVolume(currentVolume)
            }
            if (myKey) {
                volumeToast?.show()
                Log.i(this@MainActivity.javaClass.name, "" + currentVolume)
                volumeProgressBar?.progress = (currentVolume * volumeProgressBar!!.max).toInt()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val BROADCAST_PLAY_NEW_AUDIO = "com.example.readersmusic.audioplayer.playnew"
        const val SHARED_PREFERENCES = "BookTunes"
        const val VOLUME_SHARED_PREFERENCES = "com.example.readersmusic.audioplayer.volumeVar"
        private var playing = false
    }
}