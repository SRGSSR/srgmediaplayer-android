package ch.srg.mediaplayer;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import ch.srg.mediaplayer.demo.DemoApplication;
import ch.srg.mediaplayer.demo.R;

public class LayoutFragment extends android.support.v4.app.Fragment implements
        SRGMediaPlayerController.Listener {
    public static final String ARG_LAYOUT_ID = "layoutId";

    private static final String PLAYER_TAG = "main";
    private static final String URN = "dummy:SPECIMEN";
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

    private SRGMediaPlayerController createPlayer(Context context) {
        SRGMediaPlayerController mp = new SRGMediaPlayerController(context, DemoApplication.multiDataProvider, PLAYER_TAG);
        mp.setPlayerDelegateFactory(((DemoApplication) context.getApplicationContext()).getPlayerDelegateFactory());
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

    @Override
    public void onDetach() {
        super.onDetach();
        mediaPlayer.release();
    }
}
