package ch.srg.mediaplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;

import ch.srg.mediaplayer.service.utils.AppUtils;

public class ServiceNotificationBuilder {
    private NotificationData notificationData;
private boolean playing;

    public ServiceNotificationBuilder(@NonNull NotificationData notificationData, boolean playing) {
        this.notificationData = notificationData;
        this.playing = playing;
    }

    public Notification buildNotification(Context context, @NonNull MediaSessionCompat mediaSessionCompat) {
        Intent pauseIntent = new Intent(context, MediaPlayerService.class);
        pauseIntent.setAction(playing ? MediaPlayerService.ACTION_PAUSE : MediaPlayerService.ACTION_RESUME);
        pauseIntent.putExtra(MediaPlayerService.ARG_FROM_NOTIFICATION, true);
        PendingIntent piPause = PendingIntent.getService(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(MediaPlayerService.ACTION_STOP, null, context, MediaPlayerService.class);
        PendingIntent piStop = PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String appName = AppUtils.getApplicationName(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        NotificationCompat.MediaStyle style = new NotificationCompat.MediaStyle();
        style.setMediaSession(mediaSessionCompat.getSessionToken());

        builder.setStyle(style);
        builder.setContentIntent(notificationData.pendingIntent);

        if (!notificationData.live) {
            if (playing) {
                builder.addAction(R.drawable.ic_pause_white_36dp, context.getString(R.string.service_notification_pause), piPause);
            } else {
                builder.addAction(R.drawable.ic_play_arrow_white_36dp, context.getString(R.string.service_notification_resume), piPause);
            }
            style.setShowActionsInCompactView(0, 1);
        } else {
            style.setShowActionsInCompactView(0);
        }

        builder.addAction(R.drawable.ic_stop_white_36dp, context.getString(R.string.service_notification_stop), piStop);
        if (!TextUtils.isEmpty(notificationData.title)) {
            builder.setContentTitle(notificationData.title);
        } else {
            builder.setContentTitle(appName);
        }

        if (!TextUtils.isEmpty(notificationData.text)) {
            builder.setContentText(notificationData.text);
        }

        builder.setSmallIcon(notificationData.notificationSmallIcon);
        if (notificationData.notificationLargeIcon != null) {
            builder.setLargeIcon(notificationData.notificationLargeIcon);
        }
        if (notificationData.mediaSessionBitmap != null) {
            MediaMetadataCompat currentMetadata = mediaSessionCompat.getController().getMetadata();
            MediaMetadataCompat.Builder newBuilder = currentMetadata == null
                    ? new MediaMetadataCompat.Builder()
                    : new MediaMetadataCompat.Builder(currentMetadata);
            MediaMetadataCompat metadata = newBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, notificationData.mediaSessionBitmap)
                    .build();
            mediaSessionCompat.setMetadata(metadata);
        }

        builder.setOngoing(true);

        return builder.build();
    }

}
