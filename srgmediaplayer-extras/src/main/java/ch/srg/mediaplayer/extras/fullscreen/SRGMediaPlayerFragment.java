package ch.srg.mediaplayer.extras.fullscreen;

import android.app.Fragment;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Class used to hold the SRGMediaPlayerOntroller during rotation
 */
public class SRGMediaPlayerFragment extends Fragment {

    public SRGMediaPlayerController mediaPlayer;

    public SRGMediaPlayerFragment() {
        this.setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
    }
}