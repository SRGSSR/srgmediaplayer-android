package ch.srg.mediaplayer;

/**
 * Created by npietri on 24.05.16.
 */

public interface PlayerViewDelegate {

    void attachToController(SRGMediaPlayerController playerController);

    void detachFromController(SRGMediaPlayerController srgMediaPlayerController);

    void update();
}
