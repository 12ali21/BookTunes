package com.example.readersmusic;

import android.media.MediaPlayer;
import android.util.Log;
import android.widget.ProgressBar;

import java.util.concurrent.atomic.AtomicBoolean;

public class MediaProgress implements Runnable {
    private final MediaPlayer mediaPlayer;
    private final ProgressBar progressBar;
    private final AtomicBoolean stop = new AtomicBoolean(false);


    public MediaProgress(MediaPlayer mediaPlayer, ProgressBar progressBar) {
        this.mediaPlayer = mediaPlayer;
        this.progressBar = progressBar;
        this.progressBar.setMax(mediaPlayer.getDuration());
    }

    void stop(){
        stop.set(true);
    }

    @Override
    public void run() {
        while(!stop.get()){
            if(mediaPlayer.isPlaying())
                progressBar.setProgress(mediaPlayer.getCurrentPosition());

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.i("Media Progress", "Stopped");
    }
}
