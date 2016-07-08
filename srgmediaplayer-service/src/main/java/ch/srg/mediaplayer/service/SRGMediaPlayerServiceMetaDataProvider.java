package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;

/**
 * Created by seb on 07/07/15.
 */
public interface SRGMediaPlayerServiceMetaDataProvider {
    int TYPE_AUDIO = SRGMediaPlayerDataProvider.TYPE_AUDIO;
    int TYPE_VIDEO = SRGMediaPlayerDataProvider.TYPE_VIDEO;

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
     * Notification large resource bitmap.
     *
     * @param mediaIdentifier media identifier
     * @return bitmap to be displayed in service notification or null if not application (and small icon to be displayed)
     */
    Bitmap getNotificationLargeIconBitmap(String mediaIdentifier);

    /**
     * Media session large resource url. Can be used for a lock screen for instance.
     *
     * @param mediaIdentifier media identifier
     * @return bitmap to be displayed in service notification or null if not application (and small icon to be displayed)
     */
    Bitmap getMediaSessionBitmap(String mediaIdentifier);

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

    /**
     * Text to be displayed in notification
     * @param mediaIdentifier media identifier
     * @return user string
     */
    String getText(String mediaIdentifier);

    /**
     * Item type for a media identifier. Must be constant for a single media identifier. This method
     * may do a network or database request.
     * @param mediaIdentifier media identifier
     * @return {@link #TYPE_AUDIO} or {@link #TYPE_VIDEO}
     * @throws SRGMediaPlayerException network or parsing error encapsulated in an SRGMediaPlayerExcpetion
     */
    int getMediaType(@NonNull String mediaIdentifier) throws SRGMediaPlayerException;
}
