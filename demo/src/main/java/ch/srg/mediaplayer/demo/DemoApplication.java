package ch.srg.mediaplayer.demo;

import android.app.Application;
import android.content.Context;

import ch.srg.mediaplayer.DummyDataProvider;
import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.RawHttpDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.extras.dataproviders.DirectMappingDataProvider;
import ch.srg.mediaplayer.extras.dataproviders.MultiDataProvider;
import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.internal.cast.ChromeCastManager;
import ch.srg.mediaplayer.internal.exoplayer.ExoPlayerDelegate;
import ch.srg.mediaplayer.internal.nativeplayer.NativePlayerDelegate;
import ch.srg.mediaplayer.internal.session.MediaSessionManager;
import ch.srg.mediaplayer.service.MediaPlayerService;

/**
 * Created by seb on 07/04/15.
 */
public class DemoApplication extends Application {

	private static Context sContext;
	private static Application sApplication;

	public static DummyDataProvider dummyDataProvider;
	public static MultiDataProvider multiDataProvider;

	private PlayerDelegateFactory playerDelegateFactory;

	private ChromeCastManager chromeCastManager;

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
		chromeCastManager = ChromeCastManager.initialize(this);

		dummyDataProvider = new DummyDataProvider();
		multiDataProvider = new MultiDataProvider();
		multiDataProvider.addDataProvider("dummy", dummyDataProvider);
		multiDataProvider.addDataProvider("directVideo", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.TYPE_VIDEO));
		multiDataProvider.addDataProvider("aac", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.TYPE_AUDIO));
		multiDataProvider.addDataProvider("dash", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.TYPE_VIDEO));
		multiDataProvider.addDataProvider("native", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.TYPE_AUDIO));
		multiDataProvider.addDataProvider("rawHttp", new RawHttpDataProvider(SRGMediaPlayerDataProvider.TYPE_VIDEO));

		playerDelegateFactory = new PlayerDelegateFactory() {
			@Override
			public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
				switch (multiDataProvider.getPrefix(mediaIdentifier)) {
					case "aac":
						return new ExoPlayerDelegate(DemoApplication.this,srgMediaPlayer, ExoPlayerDelegate.SourceType.EXTRACTOR);
					case "dash":
						return new ExoPlayerDelegate(DemoApplication.this,srgMediaPlayer, ExoPlayerDelegate.SourceType.DASH);
					case "il":
					case "native":
						return new NativePlayerDelegate(srgMediaPlayer);
				}
				return new NativePlayerDelegate(srgMediaPlayer);
			}
		};
		MediaPlayerService.setPlayerDelegateFactory(playerDelegateFactory);
	}

	public PlayerDelegateFactory getPlayerDelegateFactory() {
		return playerDelegateFactory;
	}
}
