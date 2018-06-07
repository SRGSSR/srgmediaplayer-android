package ch.srg.mediaplayer.demo;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

public class MainActivity extends AppCompatActivity {

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediaPlayerController.bindToMediaPlayerView(mediaPlayerView);
        try {
            mediaPlayerController.play(Uri.parse(URL), streamType);
        } catch (SRGMediaPlayerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mediaPlayerController.unbindFromMediaPlayerView(mediaPlayerView);
        mediaPlayerController.release();
    }

}
