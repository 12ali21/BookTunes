package com.example.readersmusic

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.example.readersmusic.service.MusicPlayerService
import com.example.readersmusic.service.PlaybackStatus

class MediaNotification(private val context: Context, private val sessionToken: MediaSessionCompat.Token){

    fun buildNotification(status: PlaybackStatus, currentMusic: Music): Notification {
        var playPauseActionIcon = android.R.drawable.ic_media_play
        var playPauseAction: PendingIntent? = null
        if (status == PlaybackStatus.PLAYING) {
            playPauseActionIcon = android.R.drawable.ic_media_pause
            playPauseAction = playbackAction(1)
        } else if (status == PlaybackStatus.PAUSED) {
            playPauseActionIcon = android.R.drawable.ic_media_play
            playPauseAction = playbackAction(0)
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setShowWhen(false)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setLargeIcon(currentMusic.cover)
            .setSmallIcon(R.drawable.audio_book_icon)
            .setContentTitle(currentMusic.name)
            .setContentText(currentMusic.artist)
            .addAction(android.R.drawable.ic_media_previous, "Previous", playbackAction(3))
            .addAction(playPauseActionIcon, "Pause", playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "Next", playbackAction(4))
            .addAction(android.R.drawable.ic_delete, "Stop", playbackAction(2))
        val notification = notificationBuilder.build()
        (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID,
            notification
        )

        return notification
    }

    // Cancel media session notification
    fun removeNotification() {
        (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager).cancel(
            NOTIFICATION_ID
        )
    }

    // Callback actions for media session buttons
    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackIntent = Intent(context, MusicPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackIntent.action = MusicPlayerService.ACTION_PLAY
                return PendingIntent.getService(
                    context,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            1 -> {
                // Pause
                playbackIntent.action = MusicPlayerService.ACTION_PAUSE
                return PendingIntent.getService(
                    context,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            2 -> {
                // Stop
                playbackIntent.action = MusicPlayerService.ACTION_STOP
                return PendingIntent.getService(
                    context,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            3 -> {
                // Previous track
                playbackIntent.action = MusicPlayerService.ACTION_PREVIOUS
                return PendingIntent.getService(
                    context,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            4 -> {
                // Next track
                playbackIntent.action = MusicPlayerService.ACTION_NEXT
                return PendingIntent.getService(
                    context,
                    actionNumber,
                    playbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            else -> {}
        }
        return null
    }
    companion object{
        // Notification variables
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "200"
    }
}