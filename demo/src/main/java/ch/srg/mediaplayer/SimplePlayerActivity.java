package ch.srg.mediaplayer;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import ch.srg.mediaplayer.demo.DemoApplication;
import ch.srg.mediaplayer.demo.R;
import ch.srg.mediaplayer.extras.fullscreen.helper.SystemUiHelper;
import ch.srg.mediaplayer.extras.overlay.control.SimplePlayerControlView;

public class SimplePlayerActivity extends AppCompatActivity implements
        SRGMediaPlayerController.Listener, View.OnClickListener {
    public static final String ARG_URN = "urn";
    private static final String FRAGMENT_TAG = "mediaFragment";
    private static final String PLAYER_TAG = "simplePlayer";
    private static final String TAG = "SimplePlayerActivity";

    private SRGMediaPlayerController srgMediaPlayer;

    private SRGMediaPlayerView playerView;

    @Nullable
    private SimplePlayerControlView playerControlView;

    private SystemUiHelper uiHelper;

    private MediaPlayerFragment mediaPlayerFragment;
    private int orientation;
    private SRGMediaPlayerDataProvider dataProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simple_media_player);
        setTitle("DEMO Live");

        playerView = (SRGMediaPlayerView) findViewById(R.id.demo_video_container);

        View mediaControl = findViewById(R.id.media_control);
        playerControlView = (SimplePlayerControlView) mediaControl;

        dataProvider = DemoApplication.multiDataProvider;

        mediaPlayerFragment = (MediaPlayerFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (mediaPlayerFragment == null) {
            createPlayerController();

            mediaPlayerFragment = new MediaPlayerFragment();
            mediaPlayerFragment.mediaPlayer = srgMediaPlayer;
            getFragmentManager().beginTransaction().add(mediaPlayerFragment, FRAGMENT_TAG).commit();
        } else {
            if (mediaPlayerFragment.mediaPlayer == null) {
                createPlayerController();
                mediaPlayerFragment.mediaPlayer = srgMediaPlayer;
            } else {
                srgMediaPlayer = mediaPlayerFragment.mediaPlayer;
            }
        }

        srgMediaPlayer.bindToMediaPlayerView(playerView);

        srgMediaPlayer.registerEventListener(this);

        if (playerControlView != null) {
            playerControlView.attachToController(srgMediaPlayer);
        }

        orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            //code for portrait mode
            uiHelper = null;
        } else {
            //code for landscape mode
            uiHelper = new SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY);
        }
    }

    private void createPlayerController() {
        srgMediaPlayer = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
        srgMediaPlayer.setDebugMode(true);
        srgMediaPlayer.setPlayerDelegateFactory(((DemoApplication) getApplication()).getPlayerDelegateFactory());
    }

    public void playTestIdentifier(String identifier) {
        try {
            String SEEK_DELIMITER = "@seek:";
            if (identifier.contains(SEEK_DELIMITER)) {
                Long time = Long.parseLong(identifier.substring(identifier.indexOf(SEEK_DELIMITER) + SEEK_DELIMITER.length())) * 1000;
                srgMediaPlayer.play(identifier, time);
            } else {
                srgMediaPlayer.play(identifier);
            }
        } catch (SRGMediaPlayerException e) {
            Log.e(TAG, "play " + identifier, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.demo_segment_media_player_activity, menu);
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        }
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        switch (event.type) {
            case MEDIA_READY_TO_PLAY:
                break;
            case EXTERNAL_EVENT:
                break;

            case OVERLAY_CONTROL_DISPLAYED:
                if (uiHelper != null) {
                    uiHelper.show();
                }
                break;

            case OVERLAY_CONTROL_HIDDEN:
                if (uiHelper != null) {
                    uiHelper.hide();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        srgMediaPlayer.bindToMediaPlayerView(playerView);
        playTestIdentifier(getIntent().getStringExtra(ARG_URN));
    }

    @Override
    protected void onPause() {
        srgMediaPlayer.unbindFromMediaPlayerView(playerView);

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public static class MediaPlayerFragment extends Fragment {

        public SRGMediaPlayerController mediaPlayer;

        public MediaPlayerFragment() {
            this.setRetainInstance(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mediaPlayer.release();
        }
    }

    public SRGMediaPlayerController getSrgMediaPlayer() {
        return srgMediaPlayer;
    }

    public SRGMediaPlayerView getPlayerView() {
        return playerView;
    }
}
