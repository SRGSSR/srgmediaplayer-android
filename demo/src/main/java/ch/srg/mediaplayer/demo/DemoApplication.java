package ch.srg.mediaplayer.demo;

import android.app.Application;
import android.content.Context;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.internal.exoplayer.ExoPlayerDelegate;
import ch.srg.mediaplayer.internal.nativeplayer.NativePlayerDelegate;
import ch.srg.mediaplayer.providers.DirectMappingDataProvider;
import ch.srg.mediaplayer.providers.MultiDataProvider;
import ch.srg.mediaplayer.service.MediaPlayerService;
import ch.srg.mediaplayer.service.SRGMediaPlayerServiceMetaDataProvider;
import ch.srg.mediaplayer.service.cast.ChromeCastManager;
import ch.srg.mediaplayer.service.session.MediaSessionManager;

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
		multiDataProvider.addDataProvider("directVideo", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));
		multiDataProvider.addDataProvider("aac", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_AUDIO));
		multiDataProvider.addDataProvider("dash", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));
		multiDataProvider.addDataProvider("native", new DirectMappingDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_AUDIO));
		multiDataProvider.addDataProvider("rawHttp", new RawHttpDataProvider(SRGMediaPlayerDataProvider.MEDIA_TYPE_VIDEO));

		playerDelegateFactory = new PlayerDelegateFactory() {
			@Override
			public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
				switch (multiDataProvider.getPrefix(mediaIdentifier)) {
					case "aac":
						return createExoPlayerDelegate(DemoApplication.this, srgMediaPlayer);
					case "dash":
						return createExoPlayerDelegate(DemoApplication.this,srgMediaPlayer);
					case "directVideo":
					case "dummy":
						return createExoPlayerDelegate(DemoApplication.this,srgMediaPlayer);
					case "native":
						return new NativePlayerDelegate(srgMediaPlayer);
				}
				return new NativePlayerDelegate(srgMediaPlayer);
			}
		};
		MediaPlayerService.setPlayerDelegateFactory(playerDelegateFactory);
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

	public PlayerDelegateFactory getPlayerDelegateFactory() {
		return playerDelegateFactory;
	}

	public static ExoPlayerDelegate createExoPlayerDelegate(Context context, PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer) {
		ExoPlayerDelegate exoPlayerDelegate = new ExoPlayerDelegate(context, srgMediaPlayer);
		exoPlayerDelegate.setViewType(ExoPlayerDelegate.ViewType.TYPE_TEXTUREVIEW);
		return exoPlayerDelegate;
	}
}
