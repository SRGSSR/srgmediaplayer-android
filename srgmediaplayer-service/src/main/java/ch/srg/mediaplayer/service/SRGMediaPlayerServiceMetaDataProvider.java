package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;

/**
 * Created by seb on 07/07/15.
 */
public interface SRGMediaPlayerServiceMetaDataProvider {
    /**
     * Title to be displayed in notification
     * @param mediaIdentifier media identifier
     * @return user string
     */
    String getTitle(String mediaIdentifier);

    /**
     * Duration to be displayed in media session (displayed by chrome cast for instance)
     * @param mediaIdentifier media identifier
     * @return user string
     */
    Long getDuration(String mediaIdentifier);

    /**
     * Notification small resource Id.
     *
     * @param mediaIdentifier media identifier
     * @return drawable id to be displayed in service notification
     */
    @DrawableRes
    int getNotificationSmallIconResourceId(String mediaIdentifier);

    /**
     * Notification large resource Id.
     *
     * @param mediaIdentifier media identifier
     * @return bitmap to be displayed in service notification or null if not application (and small icon to be displayed)
     */
    Bitmap getNotificationLargeIconBitmap(String mediaIdentifier);

    /**
     * @param mediaIdentifier media identifier
     * @return true if notification should be in live mode
     */
    boolean isLive(String mediaIdentifier);

    /**
     * @param mediaIdentifier media identifier
     * @return pending intent to be called when user clicks on notification (or null)
     */
    @Nullable
    PendingIntent getNotificationPendingIntent(String mediaIdentifier);
}
