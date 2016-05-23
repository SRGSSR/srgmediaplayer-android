package ch.srg.mediaplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;

import ch.srg.mediaplayer.service.utils.AppUtils;

public class ServiceNotificationBuilder {
    private boolean live;
    private boolean playing;
    private String title;
    private String text;
    private PendingIntent pendingIntent;
    private Bitmap notificationLargeIcon;
    private Bitmap mediaSessionBitmap;
    private int notificationSmallIcon;

    public ServiceNotificationBuilder(boolean live, boolean playing, String title, String text, PendingIntent pendingIntent, @DrawableRes int notificationSmallIcon, Bitmap notificationBitmap, Bitmap mediaSessionBitmap) {
        this.live = live;
        this.playing = playing;
        this.title = title;
        this.text = text;
        this.pendingIntent = pendingIntent;
        this.notificationSmallIcon = notificationSmallIcon;
        this.notificationLargeIcon = notificationBitmap;
        this.mediaSessionBitmap = mediaSessionBitmap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceNotificationBuilder that = (ServiceNotificationBuilder) o;

        if (live != that.live) return false;
        if (playing != that.playing) return false;
        if (!TextUtils.equals(title, that.title)) return false;
        if (!TextUtils.equals(text, that.text)) return false;
        if (notificationSmallIcon != that.notificationSmallIcon) return false;
        if (notificationLargeIcon != that.notificationLargeIcon) return false;
        if (mediaSessionBitmap != that.mediaSessionBitmap) return false;
        if (pendingIntent != null ? !pendingIntent.equals(that.pendingIntent) : that.pendingIntent != null)
            return false;

        return true;

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
        builder.setContentIntent(pendingIntent);

        if (!live) {
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
        if (!TextUtils.isEmpty(title)) {
            builder.setContentTitle(title);
        } else {
            builder.setContentTitle(appName);
        }

        if (!TextUtils.isEmpty(text)) {
            builder.setContentText(text);
        }

        builder.setSmallIcon(notificationSmallIcon);
        if (notificationLargeIcon != null) {
            builder.setLargeIcon(notificationLargeIcon);
        }
        if (mediaSessionBitmap != null) {
            MediaMetadataCompat currentMetadata = mediaSessionCompat.getController().getMetadata();
            MediaMetadataCompat.Builder newBuilder = currentMetadata == null
                    ? new MediaMetadataCompat.Builder()
                    : new MediaMetadataCompat.Builder(currentMetadata);
            MediaMetadataCompat metadata = newBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaSessionBitmap)
                    .build();
            mediaSessionCompat.setMetadata(metadata);
        }

        builder.setOngoing(true);

        return builder.build();
    }

}
