### SRG Media Player ###
================

WARNING : This repository is no more maintain, please use SRGLetterbox (https://github.com/SRGSSR/srgletterbox-android).

Features
--------
* audio and video
* basic controls: play/pause/stop/seek
* fluid rotation (stream continuity)
* HLS VOD
* LIVE and DVR streams in HLS
* custom overlays
* offer management of segments for a video. Display as smart overlays, control of streams through segments (segment-overlay module)
* support for URL rewriting

Implementation
-----------------
```xml
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

```java
    private SRGMediaPlayerController srgMediaPlayerController;
    private SRGMediaPlayerView videoContainer;
    private LivePlayerControlView playerControlView;

    ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {

      ...

      videoContainer = (SRGMediaPlayerView) findViewById(R.id.demo_video_container);
      playerControlView = (LivePlayerControlView) findViewById(R.id.media_control);

      srgMediaPlayerController = new SRGMediaPlayerController(this, PLAYER_TAG);
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
