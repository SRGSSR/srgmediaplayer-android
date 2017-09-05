package ch.srg.mediaplayer.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.demo.DemoApplication;
import ch.srg.mediaplayer.demo.R;

public class LayoutFragment extends android.support.v4.app.Fragment implements
        SRGMediaPlayerController.Listener {
    public static final String ARG_LAYOUT_ID = "layoutId";

    private static final String PLAYER_TAG = "main";
    private static final String URN = "dash:http://rdmedia.bbc.co.uk/dash/ondemand/testcard/1/client_manifest-events.mpd";
    private static final String TAG = "DemoSegment";

    private SRGMediaPlayerController mediaPlayer;

    private SRGMediaPlayerView playerView;

    public LayoutFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        int layoutId = getArguments().getInt(ARG_LAYOUT_ID);
        ViewGroup rootView = (ViewGroup) inflater.inflate(layoutId, container, false);
        playerView = (SRGMediaPlayerView) rootView.findViewById(R.id.demo_video_container);
        mediaPlayer.bindToMediaPlayerView(playerView);
        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mediaPlayer = createPlayer(context);
        try {
            mediaPlayer.play(URN);
        } catch (SRGMediaPlayerException e) {
            Toast.makeText(context, "Player error " + e, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDetach() {
        mediaPlayer.release();
        super.onDetach();
    }

    private SRGMediaPlayerController createPlayer(Context context) {
        SRGMediaPlayerController mp = new SRGMediaPlayerController(context, DemoApplication.multiDataProvider, PLAYER_TAG);
        mp.setDebugMode(true);
        return mp;
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        switch (event.type) {
            case FATAL_ERROR:
            case TRANSIENT_ERROR:
                Context context = getContext();
                if (context != null) {
                    Toast toast = Toast.makeText(context, "error " + event.exception, Toast.LENGTH_LONG);
                    toast.show();
                }
                break;
        }
    }
}
