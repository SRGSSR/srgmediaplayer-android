package ch.srg.mediaplayer;

/**
 * Interface definition for a callback to be invoked when touch event occurs.
 */
public interface ControlTouchListener {
    void onMediaControlTouched();

    void onMediaControlBackgroundTouched();
}
