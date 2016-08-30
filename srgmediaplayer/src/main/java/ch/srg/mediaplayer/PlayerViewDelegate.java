package ch.srg.mediaplayer;

/**
 * Created by npietri on 24.05.16.
 */

public interface PlayerViewDelegate {
    int FULLSCREEN_BUTTON_INVISIBLE = 0;
    int FULLSCREEN_BUTTON_ON = 1;
    int FULLSCREEN_BUTTON_OFF = 2;

    void attachToController(SRGMediaPlayerController playerController);

    void detachFromController(SRGMediaPlayerController srgMediaPlayerController);

    void setFullScreenButtonState(int fullScreenButtonState);

    void update();
}
