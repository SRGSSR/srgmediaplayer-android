package ch.srg.mediaplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import ch.srg.mediaplayer.service.utils.AppUtils;

public class ServiceNotificationBuilder {
    boolean live;
    boolean playing;
    String title;
    Bitmap notificationBitmap;

    public ServiceNotificationBuilder(boolean live, boolean playing, String title, Bitmap notificationBitmap) {
        this.live = live;
        this.playing = playing;
        this.title = title;
        this.notificationBitmap = notificationBitmap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceNotificationBuilder that = (ServiceNotificationBuilder) o;

        if (live != that.live) return false;
        if (playing != that.playing) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        return !(notificationBitmap != null ? !notificationBitmap.equals(that.notificationBitmap) : that.notificationBitmap != null);

    }

    public Notification buildNotification(Context context, PendingIntent notificationPendingIntent) {
        /* XXX: stackbuilder needed for back navigation */

        Intent pauseIntent = new Intent(context, MediaPlayerService.class);
        pauseIntent.setAction(playing ? MediaPlayerService.ACTION_PAUSE : MediaPlayerService.ACTION_RESUME);
        pauseIntent.putExtra(MediaPlayerService.ARG_FROM_NOTIFICATION, true);
        PendingIntent piPause = PendingIntent.getService(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(MediaPlayerService.ACTION_STOP, null, context, MediaPlayerService.class);
        PendingIntent piStop = PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String appName = AppUtils.getApplicationName(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentIntent(notificationPendingIntent);

        if (!live) {
            if (playing) {
                builder.addAction(R.drawable.av_pause_notif, context.getString(R.string.service_notification_pause), piPause);
            } else {
                builder.addAction(R.drawable.av_play_notif, context.getString(R.string.service_notification_resume), piPause);
            }
        }

        builder.addAction(R.drawable.av_stop_notif, context.getString(R.string.service_notification_stop), piStop);
        builder.setContentTitle(appName);

        if (!TextUtils.isEmpty(title)) {
            builder.setContentText(title);
        }

        builder.setSmallIcon(R.drawable.ic_media_play);
        if (notificationBitmap != null) {
            builder.setLargeIcon(notificationBitmap);
        }

        builder.setOngoing(true);

        return builder.build();
    }
}
