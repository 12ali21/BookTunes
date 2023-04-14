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
            try {
                if(mediaPlayer.isPlaying())
                    progressBar.setProgress(mediaPlayer.getCurrentPosition());
            } catch (IllegalStateException e){
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.i("Media Progress", "Thread Stopped");
    }
}
