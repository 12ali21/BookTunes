package com.example.readersmusic;

import android.graphics.Bitmap;
import android.widget.ProgressBar;

import com.example.readersmusic.service.PlaybackStatus;

public interface ServiceCallbacks {
    void changePlayBtnState(PlaybackStatus status);
    void changeControllerVisibility(boolean visibility);
    void setControllerTitle(String title, MusicType type);
    void changeControllerBackgroundFromCover(Bitmap musicCover);
    ProgressBar getMediaProgressBar();
}
