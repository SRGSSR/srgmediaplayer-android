package ch.srg.mediaplayer.service;

import ch.srg.mediaplayer.SRGMediaPlayerMetaDataProvider;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface SRGMediaPlayerServiceMetaDataProvider extends SRGMediaPlayerMetaDataProvider {
    void getNotificationData(String mediaIdentifier, GetNotificationDataCallback getNotificationDataCallback);

    interface GetNotificationDataCallback {
        void onNotificationDataLoaded(NotificationData notificationData);
        void onImageLoaded(NotificationData notificationData);
        void onDataNotAvailable();
    }
}
