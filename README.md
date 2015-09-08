SRG Media Player
================

Building
--------
* install Android Studio from http://developer.android.com/sdk/installing/studio.html (stable build)
* checkout the project from github
* plug a device in ADB debug mode
* select Run/Run 'demo'
* select your device

Using
-----
To use SRG Media Player in your project, add this in your build.gradle:

    repositories {
    	maven {
    		url "http://maven.ecetest.rts.ch/content/repositories/releases/"
    	}
    	maven {
    		url "http://maven.ecetest.rts.ch/content/repositories/snapshots/"
    	}
    }

Then use:

    compile 'ch.srg.mediaplayer:srgmediaplayer:1.0.+@aar'

in your dependencies.

Device requirements
-------------------
* Android 4.0.3 or higher (API 15+)

Features
--------
* has multiple selectable player engines: Android internal, Exoplayer and more
* audio and video
* basic controls: play/pause/stop/seek
* fluid rotation (stream continuity)
* HLS VOD
* LIVE streams
* custom overlays
* offer management of segments for a video. Display as smart overlays, control of streams through segments (segment-overlay module)
* support for URL rewriting

Roadmap
----------------
* Offer a full featured and customizable Activity or a lightweight Fragment to used in others app

* Offer a full featured playlist management. Display, navigation, business logic.

* Handle multiple formats: HLS, DASH, ...

* UI can be easily customized with Theme or full custom layouts

* Offer timeshift by streams with DVR.

Implementation
-----------------
* The fist step for integrate the SRG Media Player in your project is to configure your own dataProvider for bind your model to the API. In the demo application we have build a DummyDataProvider for handle data from web and specific videos. The class implements basic interface for dataProvider, and segments.

```
#!java
  private static Map<String, String> data = new HashMap<String, String>() {
	    {
		    put("SPECIMEN", "http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
		    put("ODK", "http://stream-i.rts.ch/i/oreki/2015/OREKI_20150225_full_f_861302-,101,701,1201,k.mp4.csmil/master.m3u8");
		    put("BIDOUM", "http://stream-i.rts.ch/i/bidbi/2008/bidbi_01042008-,450,k.mp4.csmil/master.m3u8");
		    put("MULTI1", "https://srgssruni9ch-lh.akamaihd.net/i/enc9uni_ch@191320/master.m3u8");
		    put("MULTI2", "https://srgssruni10ch-lh.akamaihd.net/i/enc10uni_ch@191367/master.m3u8");
		    put("MULTI3", "https://srgssruni7ch-lh.akamaihd.net/i/enc7uni_ch@191283/master.m3u8");
		    put("MULTI4", "https://srgssruni11ch-lh.akamaihd.net/i/enc11uni_ch@191455/master.m3u8");
		    put("ERROR", "http://invalid.stream/");
    	};
  };

  @Override
  public Uri getUri(String mediaIdentifier) {
      if (mediaIdentifier.contains("@")){
          mediaIdentifier = mediaIdentifier.substring(0,mediaIdentifier.indexOf('@'));
      }
      if (data.containsKey(mediaIdentifier)) {
          return Uri.parse(data.get(mediaIdentifier));
      }
      return null;
   }
```

* In this basic implementation if we ask to the player to play the Media with the identifier SPECIMEN the dataProvider will send back the correct Uri to the player.

* If you want to specify the media type you will play and set a delegate for it. You need to provide a PlayerDelegateFactory and set it to the player controller.

```
#!java
    playerDelegateFactory = new PlayerDelegateFactory() {
      @Override
      public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
        switch (multiDataProvider.getPrefix(mediaIdentifier)) {
          case "aac":
            return new ExoPlayerDelegate(DemoApplication.this,srgMediaPlayer, ExoPlayerDelegate.SourceType.EXTRACTOR);
          case "native":
            return new NativePlayerDelegate(srgMediaPlayer);
        }
        return new NativePlayerDelegate(srgMediaPlayer);
      }
    };
    srgMediaPlayerController.setPlayerDelegateFactory(playerDelegateFactory);
```

* In this example we configure a new PlayerDelegateFactory and specify the player delegate to use for each kind of prefix in our model. ExoPlayerDelegate for AAC and Native for other content.

* A default PlayerDelegateFactory is already implemented, we use ExoPlayer for JellyBean and HLS content and NativePlayer for versions before JellyBean.

* We have currently two delegates implemented, one for ExoPlayer and one for NativePlayer. You can create your custom delegate if you need to use a different kind of media player. Be sure to implement PlayerDelegate interface for handle communication between the player and the component.

Next step is to add the SRGMediaPlayerView in your layout.

```
#!xml
<ch.srg.mediaplayer.SRGMediaPlayerView
    android:id="@+id/demo_video_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:animateLayoutChanges="true"
    app:containerAspectRatio="auto"
    app:videoScale="center_inside">

    <!-- Loading Progress -->
    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        app:overlay_mode="loading" />

    <!--Controls-->
    <ch.srg.view.LivePlayerControlView
        android:id="@+id/media_control"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_above="@+id/bottomAnchor"
        android:layout_centerHorizontal="true"
        app:overlay_mode="control" />

    <!--Some text over the controls that stay-->
    <ch.srg.mediaplayer.extras.overlay.error.SimpleErrorMessage
        android:id="@+id/error_message"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:overlay_mode="unmanaged" />

</ch.srg.mediaplayer.SRGMediaPlayerView>
```

The SRGMediaPlayerView is basically a RelativeLayout, you can add children view and use property

    app:overlay_mode="control|always_shown|loading|unmanaged"

for synchronize this view visibility with player events.

* control: Will hide and show view when "OVERLAY_CONTROL_DISPLAYED" and "OVERLAY_CONTROL_HIDDEN" triggred
* always_shown: Never hide the view
* loading: Show the view only when player is loading video
* unmanaged: You need to manage manually the visibility of this view

In your Activity (or fragment) you have to coordinate all the view with the player controller. Your activity or fragment should implement SRGMediaPlayerController.Listener if you want to listen player events.

```
#!java
    private SRGMediaPlayerController srgMediaPlayerController;
    private SRGMediaPlayerView videoContainer;
    private MultiDataProvider dataProvider;
    private LivePlayerControlView playerControlView;

    ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {

      ...

      videoContainer = (SRGMediaPlayerView) findViewById(R.id.demo_video_container);
      playerControlView = (LivePlayerControlView) findViewById(R.id.media_control);

      srgMediaPlayerController = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
      srgMediaPlayerController.bindToMediaPlayerView(videoContainer);
      playerControlView.attachToController(srgMediaPlayer);
      srgMediaPlayerController.registerEventListener(this);
      ...
    }

    @Override
    protected void onResume() {
      super.onResume();
      srgMediaPlayerController.bindToMediaPlayerView(videoContainer);
      srgMediaPlayerController.keepScreenOn(true);
    }

    @Override
    protected void onPause() {
      srgMediaPlayer.unbindFromMediaPlayerView();
      super.onPause();
    }

    @Override
    protected void onStop() {
      super.onStop();
      srgMediaPlayer.keepScreenOn(false);
    }
```
