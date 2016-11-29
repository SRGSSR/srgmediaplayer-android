package ch.srg.mediaplayer;

/**
 * Created by npietri on 24.05.16.
 */

public interface PlayerViewDelegate {
    int BUTTON_STATE_INVISIBLE = 0;
    int BUTTON_STATE_ON = 1;
    int BUTTON_STATE_OFF = 2;

    void attachToController(SRGMediaPlayerController playerController);

    void detachFromController(SRGMediaPlayerController srgMediaPlayerController);

    void setFullScreenButtonState(int buttonState);

    void setSubtitleButtonState(int buttonState);

    void update();
}
