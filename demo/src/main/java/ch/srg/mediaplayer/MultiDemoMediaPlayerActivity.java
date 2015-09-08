package ch.srg.mediaplayer;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;

import ch.srg.mediaplayer.demo.R;

public class MultiDemoMediaPlayerActivity extends ActionBarActivity implements
		View.OnClickListener,
		SRGMediaPlayerView.VideoTouchListener,
		SRGMediaPlayerController.Listener {

	public static final String PLAYER_TAG = "main";
	private SRGMediaPlayerDataProvider dataProvider = new DummyDataProvider();

	private SRGMediaPlayerController rtsMediaPlayer1;
	private SRGMediaPlayerController rtsMediaPlayer2;
	private SRGMediaPlayerController rtsMediaPlayer3;

	private SRGMediaPlayerController rtsMediaPlayerBoundToTop;
	private SRGMediaPlayerController rtsMediaPlayerBoundToBotLeft;
	private SRGMediaPlayerController rtsMediaPlayerBoundToBotRight;

	private SRGMediaPlayerView SRGMediaPlayerViewTop;
	private SRGMediaPlayerView SRGMediaPlayerViewBottomLeft;
	private SRGMediaPlayerView SRGMediaPlayerViewBottomRight;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multi_demo_media_player);

		SRGMediaPlayerViewTop = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_top);
		SRGMediaPlayerViewBottomLeft = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_bottom_left);
		SRGMediaPlayerViewBottomRight = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_bottom_right);

		rtsMediaPlayer1 = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
		rtsMediaPlayer2 = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
		rtsMediaPlayer3 = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
		rtsMediaPlayer1.setDebugMode(true);
		rtsMediaPlayer2.setDebugMode(true);
		rtsMediaPlayer3.setDebugMode(true);

		try {
			rtsMediaPlayer1.play("MULTI1");
			rtsMediaPlayer2.play("MULTI2");
			rtsMediaPlayer2.mute();
			rtsMediaPlayer3.play("MULTI4");
			rtsMediaPlayer3.mute();
		} catch (SRGMediaPlayerException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		rtsMediaPlayer1.bindToMediaPlayerView(SRGMediaPlayerViewTop);
		rtsMediaPlayerBoundToTop = rtsMediaPlayer1;
		rtsMediaPlayer2.bindToMediaPlayerView(SRGMediaPlayerViewBottomLeft);
		rtsMediaPlayerBoundToBotLeft = rtsMediaPlayer2;
		rtsMediaPlayer3.bindToMediaPlayerView(SRGMediaPlayerViewBottomRight);
		rtsMediaPlayerBoundToBotRight = rtsMediaPlayer3;

		SRGMediaPlayerViewTop.setVideoTouchListener(this);
		SRGMediaPlayerViewBottomLeft.setVideoTouchListener(this);
		SRGMediaPlayerViewBottomRight.setVideoTouchListener(this);

	}

	@Override
	public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {

	}

	@Override
	public void onClick(View v) {

	}

	@Override
	public void onVideoRenderingViewTouched(SRGMediaPlayerView SRGMediaPlayerView) {
		if (SRGMediaPlayerView == SRGMediaPlayerViewBottomLeft) {

			String topId = rtsMediaPlayerBoundToTop.getMediaIdentifier();
			String botLeftId = rtsMediaPlayerBoundToBotLeft.getMediaIdentifier();
			try {
				rtsMediaPlayerBoundToTop.play(botLeftId);
				rtsMediaPlayerBoundToBotLeft.play(topId);
			} catch (SRGMediaPlayerException e) {
				e.printStackTrace();
			}

//			rtsMediaPlayerBoundToTop.mute();
//			SRGMediaPlayerController newBotLeft = rtsMediaPlayerBoundToTop;
//			SRGMediaPlayerController newTop = rtsMediaPlayerBoundToBotLeft;
//
//
//			newTop.unbindFromMediaPlayerView();
//			newBotLeft.unbindFromMediaPlayerView();
//
//			newTop.bindToMediaPlayerView(SRGMediaPlayerViewTop);
//
//			newBotLeft.bindToMediaPlayerView(SRGMediaPlayerViewBottomLeft);
//
//			rtsMediaPlayerBoundToBotLeft = newBotLeft;
//			rtsMediaPlayerBoundToTop = newTop;
//			rtsMediaPlayerBoundToTop.unmute();
		} else if (SRGMediaPlayerView == SRGMediaPlayerViewBottomRight) {

			String topId = rtsMediaPlayerBoundToTop.getMediaIdentifier();
			String boRightId = rtsMediaPlayerBoundToBotRight.getMediaIdentifier();
			try {
				rtsMediaPlayerBoundToTop.play(boRightId);
				rtsMediaPlayerBoundToBotRight.play(topId);
			} catch (SRGMediaPlayerException e) {
				e.printStackTrace();
			}


//			rtsMediaPlayerBoundToTop.mute();
//			SRGMediaPlayerController newBotRight = rtsMediaPlayerBoundToTop;
//			SRGMediaPlayerController newTop = rtsMediaPlayerBoundToBotRight;
//
//
//			newTop.unbindFromMediaPlayerView();
//			newBotRight.unbindFromMediaPlayerView();
//
//			newTop.bindToMediaPlayerView(SRGMediaPlayerViewTop);
//			newBotRight.bindToMediaPlayerView(SRGMediaPlayerViewBottomRight);
//
//			rtsMediaPlayerBoundToBotRight = newBotRight;
//			rtsMediaPlayerBoundToTop = newTop;
//			rtsMediaPlayerBoundToTop.unmute();
		}

		SRGMediaPlayerViewTop.setVideoTouchListener(this);
		SRGMediaPlayerViewBottomLeft.setVideoTouchListener(this);
		SRGMediaPlayerViewBottomRight.setVideoTouchListener(this);
	}

	@Override
	public void onVideoOverlayTouched(SRGMediaPlayerView SRGMediaPlayerView) {
		//Nothing special now
	}

	@Override
	protected void onPause() {
		rtsMediaPlayer1.unbindFromMediaPlayerView();
		rtsMediaPlayer2.unbindFromMediaPlayerView();
		rtsMediaPlayer3.unbindFromMediaPlayerView();
		super.onPause();
	}

	@Override
	protected void onStop() {
		rtsMediaPlayer1.release();
		rtsMediaPlayer1 = null;
		rtsMediaPlayer2.release();
		rtsMediaPlayer2 = null;
		rtsMediaPlayer3.release();
		rtsMediaPlayer3 = null;
		super.onStop();
	}

}
