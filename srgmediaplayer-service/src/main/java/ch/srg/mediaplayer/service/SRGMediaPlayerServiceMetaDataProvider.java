package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
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
     * Notification resource Id.
     *
     * @param mediaIdentifier media identifier
     * @return drawable id to be displayed in service notification
     */
    int getNotificationIconResourceId(String mediaIdentifier);

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
