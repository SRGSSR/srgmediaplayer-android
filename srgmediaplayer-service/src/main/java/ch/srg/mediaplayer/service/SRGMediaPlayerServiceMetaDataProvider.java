package ch.srg.mediaplayer.service;

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
}
