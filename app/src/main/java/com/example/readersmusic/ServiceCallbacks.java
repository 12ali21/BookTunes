package com.example.readersmusic;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.widget.ProgressBar;

public interface ServiceCallbacks {
    void changePlayBtnState(MusicPlayerService.PlaybackStatus status);
    void changeControllerVisibility(boolean visibility);
    void setControllerTitle(String title);
    void setControllerType(MusicType type);
    void changeControllerBackgroundFromCover(Bitmap musicCover);
    ProgressBar getMediaProgressBar();
}
