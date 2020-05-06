package ch.srg.mediaplayer.demo;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerView;

public class MainActivity extends AppCompatActivity implements SRGMediaPlayerController.Listener {

    private SRGMediaPlayerView mediaPlayerView;
    private SRGMediaPlayerController mediaPlayerController;
    private static final String URL = "https://srgplayerswivod-vh.akamaihd.net/i/44089658/,video,.mp4.csmil/master.m3u8?start=0.0&end=113.0";
    private static final @SRGMediaPlayerController.SRGStreamType
    int streamType = SRGMediaPlayerController.STREAM_HLS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaPlayerView = findViewById(R.id.media_player_view);

        mediaPlayerController = new SRGMediaPlayerController(this, "demo_player");
        mediaPlayerView.setAspectRatioListener(new SRGMediaPlayerView.AspectRatioListener() {
            @Override
            public void onAspectRatioChanged(float videoAspectRatio, float containerAspectRatio, boolean autoContainerAspectMode) {
                Log.d("Demo", "videoRatio = " + videoAspectRatio + " containerRatio = " + containerAspectRatio + " auto = " + autoContainerAspectMode);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediaPlayerController.registerEventListener(this);
        mediaPlayerController.bindToMediaPlayerView(mediaPlayerView);
        mediaPlayerController.play(Uri.parse(URL), streamType);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mediaPlayerController.unbindFromMediaPlayerView(mediaPlayerView);
        mediaPlayerController.release();
        mediaPlayerController.unregisterEventListener(this);
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        Log.d("MediaPlayerEvent", " " + event.type + " " + event.state);
        if (event.type == SRGMediaPlayerController.Event.Type.STREAM_TIMELINE_CHANGED) {
            Log.d("COUCOU", "" + mp.getPlayerTimeLine());
        }
        Log.d("COUCOU", "player position = " + mp.getMediaPosition());
        Log.d("COUCOU", "buffer position = " + mp.getBufferPosition());
        Log.d("COUCOU", "buffer % = " + mp.getBufferPercentage());
    }
}
