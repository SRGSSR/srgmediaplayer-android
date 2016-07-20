package ch.srg.mediaplayer.service;

import ch.srg.mediaplayer.SRGMediaPlayerMetaDataProvider;

/**
 * Created by seb on 07/07/15.
 */
public interface SRGMediaPlayerServiceMetaDataProvider extends SRGMediaPlayerMetaDataProvider {
    void getNotificationData(String mediaIdentifier, GetNotificationDataCallback getNotificationDataCallback);

    interface GetNotificationDataCallback {
        void onNotificationDataLoaded(NotificationData notificationData);
        void onImageLoaded(NotificationData notificationData);
        void onDataNotAvailable();
    }
}
