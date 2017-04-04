package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
import android.graphics.Bitmap;

/**
 * Created by seb on 20/07/16.
 */
public class NotificationData {
    // TODO Remove live from here and handle pause capability in another way

    public String title;
    public String text;
    public PendingIntent pendingIntent;
    public Bitmap notificationLargeIcon;
    public Bitmap mediaSessionBitmap;
    public int notificationSmallIcon;
    public long duration;

    public NotificationData(String title, String text, PendingIntent pendingIntent, Bitmap notificationLargeIcon, Bitmap mediaSessionBitmap, int notificationSmallIcon, long duration) {
        this.title = title;
        this.text = text;
        this.pendingIntent = pendingIntent;
        this.notificationLargeIcon = notificationLargeIcon;
        this.mediaSessionBitmap = mediaSessionBitmap;
        this.notificationSmallIcon = notificationSmallIcon;
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotificationData that = (NotificationData) o;

        if (notificationSmallIcon != that.notificationSmallIcon) return false;
        if (duration != that.duration) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        if (text != null ? !text.equals(that.text) : that.text != null) return false;
        if (pendingIntent != null ? !pendingIntent.equals(that.pendingIntent) : that.pendingIntent != null)
            return false;
        if (notificationLargeIcon != null ? !notificationLargeIcon.equals(that.notificationLargeIcon) : that.notificationLargeIcon != null)
            return false;
        return mediaSessionBitmap != null ? mediaSessionBitmap.equals(that.mediaSessionBitmap) : that.mediaSessionBitmap == null;

    }

    @Override
    public int hashCode() {
        int result = (title != null ? title.hashCode() : 0);
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (pendingIntent != null ? pendingIntent.hashCode() : 0);
        result = 31 * result + (notificationLargeIcon != null ? notificationLargeIcon.hashCode() : 0);
        result = 31 * result + (mediaSessionBitmap != null ? mediaSessionBitmap.hashCode() : 0);
        result = 31 * result + notificationSmallIcon;
        result = 31 * result + (int) (duration ^ (duration >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "NotificationData{" +
                ", title='" + title + '\'' +
                ", text='" + text + '\'' +
                ", pendingIntent=" + pendingIntent +
                ", notificationLargeIcon=" + notificationLargeIcon +
                ", mediaSessionBitmap=" + mediaSessionBitmap +
                ", notificationSmallIcon=" + notificationSmallIcon +
                ", duration=" + duration +
                '}';
    }
}
