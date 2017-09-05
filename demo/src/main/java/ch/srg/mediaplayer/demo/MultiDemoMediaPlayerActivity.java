package ch.srg.mediaplayer.demo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

public class MultiDemoMediaPlayerActivity extends Activity implements
        SRGMediaPlayerController.Listener {

	public static final String PLAYER_TAG = "main";

	private SRGMediaPlayerController mediaPlayers[];

	private SRGMediaPlayerView views[];
	private ViewGroup parentView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multi_demo_media_player);

		views = new SRGMediaPlayerView[3];
		views[0] = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_top);
		views[1] = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_bottom_left);
		views[2] = (SRGMediaPlayerView) findViewById(R.id.multi_video_view_bottom_right);

		parentView = (ViewGroup) findViewById(R.id.parent);

		findViewById(R.id.button_1).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				selectPlayer(0);
			}
		});
		findViewById(R.id.button_2).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				selectPlayer(1);
			}
		});
		findViewById(R.id.button_3).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				selectPlayer(2);
			}
		});
		mediaPlayers = new SRGMediaPlayerController[3];
		for (int i = 0; i < mediaPlayers.length; i++) {
			mediaPlayers[i] = createPlayerController();
		}

		startAll();
	}

	private void startAll() {
		for (int i = 0; i < mediaPlayers.length; i++) {
			startPlayer(i);
		}
	}

	private void startPlayer(int i) {
		try {
			mediaPlayers[i].play("dummy:MULTI" + (i + 1));
		} catch (SRGMediaPlayerException e) {
			throw new RuntimeException(e);
		}
	}

	@NonNull
	private SRGMediaPlayerController createPlayerController() {
		SRGMediaPlayerController mp = new SRGMediaPlayerController(this, DemoApplication.multiDataProvider, PLAYER_TAG);
		mp.setDebugMode(true);
		mp.setAudioFocusBehaviorFlag(SRGMediaPlayerController.AUDIO_FOCUS_FLAG_DISABLED);

		return mp;
	}

	@Override
	protected void onResume() {
		super.onResume();
		int top = 0;
		bindPlayers(top);
		selectPlayer(0);
	}

	private void bindPlayers(int top) {
		int viewIndex = 0;
		mediaPlayers[top].bindToMediaPlayerView(views[viewIndex++]);
		mediaPlayers[top].unmute();
		for (int i = 0; i < mediaPlayers.length; i++) {
			if (i != top) {
				mediaPlayers[i].bindToMediaPlayerView(views[viewIndex++]);
//				mediaPlayers[i].mute();
			}
		}

	}

	@Override
	public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {

	}

	private void selectPlayer(int newTop) {
		SRGMediaPlayerController topPlayer = mediaPlayers[newTop];
		if (!topPlayer.isPlaying()) {
			startPlayer(newTop);
			return;
		}
		for (int i = 0; i < mediaPlayers.length; i++) {
			if (i == newTop) {
				mediaPlayers[i].unmute();
			} else {
				mediaPlayers[i].mute();
			}
		}

		SRGMediaPlayerView topView = topPlayer.getMediaPlayerView();
		swapLayoutParams(views[0], topView);

		for (SRGMediaPlayerView view : views) {
			if (view != topView) {
				view.bringToFront();
			}
		}
	}

	private void swapLayoutParams(View a, View b) {
			ViewGroup.LayoutParams lpA = a.getLayoutParams();
			ViewGroup.LayoutParams lpB = b.getLayoutParams();
			a.setLayoutParams(lpB);
			b.setLayoutParams(lpA);
	}

	@Override
	protected void onPause() {
		unbindAll();
		super.onPause();
	}

	private void unbindAll() {
		for (SRGMediaPlayerController controller : mediaPlayers) {
			controller.unbindFromMediaPlayerView(controller.getMediaPlayerView());
		}
	}

	@Override
	protected void onStop() {
		for (int i = 0; i < mediaPlayers.length; i++) {
			mediaPlayers[i].release();
			mediaPlayers[i] = null;
		}
		super.onStop();
	}

}
