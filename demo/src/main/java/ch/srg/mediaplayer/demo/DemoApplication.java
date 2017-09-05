package ch.srg.mediaplayer.demo;

import android.app.Application;
import android.content.Context;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.providers.DirectMappingDataProvider;
import ch.srg.mediaplayer.providers.MultiDataProvider;
import ch.srg.mediaplayer.service.MediaPlayerService;
import ch.srg.mediaplayer.service.SRGMediaPlayerServiceMetaDataProvider;
import ch.srg.mediaplayer.service.session.MediaSessionManager;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class DemoApplication extends Application {

    private static Context sContext;
    private static Application sApplication;

    public static DummyDataProvider dummyDataProvider;
    public static MultiDataProvider multiDataProvider;

    public DemoApplication() {
    }

    public static Context getContext() {
        return sContext;
    }

    public static Application getApplication() {
        return sApplication;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;
        sContext = getApplicationContext();

        MediaSessionManager.initialize(sContext);

        dummyDataProvider = new DummyDataProvider();
        multiDataProvider = new MultiDataProvider();
        multiDataProvider.addDataProvider("dummy", dummyDataProvider);
        multiDataProvider.addDataProvider("directVideo", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));
        multiDataProvider.addDataProvider("aac", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_AUDIO));
        multiDataProvider.addDataProvider("dash", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));
        multiDataProvider.addDataProvider("native", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_AUDIO));
        multiDataProvider.addDataProvider("rawHttp", new RawHttpDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));

        MediaPlayerService.setServiceDataProvider(new SRGMediaPlayerServiceMetaDataProvider() {
            @Override
            public void getNotificationData(String mediaIdentifier, GetNotificationDataCallback getNotificationDataCallback) {
                getNotificationDataCallback.onDataNotAvailable();
            }

            @Override
            public void getMediaMetadata(String mediaIdentifier, GetMediaMetadataCallback callback) {
                callback.onDataNotAvailable();
            }
        });
    }
}
