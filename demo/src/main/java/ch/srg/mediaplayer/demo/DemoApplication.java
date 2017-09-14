package ch.srg.mediaplayer.demo;

import android.app.Application;
import android.content.Context;
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
    }
}
