package ch.srg.mediaplayer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Axel on 09/03/2015.
 * <p>
 * TODO OverlayController should handle visibility directly instead of messing with child's visibility.
 * Problematic use cases:
 * SRGMediaPlayerView.applyOverlayMode(playerControlView, SRGMediaPlayerView.LayoutParams.OVERLAY_CONTROL);
 * playerControlView.setVisibility(View.VISIBLE);
 * -> the second visibility should not have any impact. the overlay mode itself should be applied directly
 * <p>
 * The potential race condition is when the app does:
 * A) play.showControls()
 * B) SRGMediaPlayerView.applyOverlayMode(playerControlView, SRGMediaPlayerView.LayoutParams.OVERLAY_CONTROL);
 * The order of execution of A / B will have an impact on whether the player controls are displayed or not, this is bad.
 */
/*package*/ class OverlayController implements ControlTouchListener, SRGMediaPlayerController.Listener {

    private static final String TAG = SRGMediaPlayerController.TAG;

    public static int overlayAutoHideDelay = 3000;

    private SRGMediaPlayerView videoContainer;
    private SRGMediaPlayerController playerController;

    private boolean showingControlOverlays = true;
    private boolean showingLoadings = true;

    private boolean forceShowingOverlays = false;

    private long lastOverlayPostponingTime = 0L;

    private Handler handler = new Handler();

    private Runnable hideOverlaysRunnable = new Runnable() {
        @Override
        public void run() {
            if (!forceShowingOverlays) {
                hideControlOverlays();
            }
        }
    };

    public OverlayController(SRGMediaPlayerController playerController, Handler handler) {
        this.handler = handler;
        this.playerController = playerController;
        showControlOverlays();
        updateWithPlayer(false);
    }

    public void setForceShowingControlOverlays(boolean forceShowingControlOverlays) {
        this.forceShowingOverlays = forceShowingControlOverlays;
        if (forceShowingControlOverlays) {
            showControlOverlays();
        }
    }

    @Override
    public void onMediaControlTouched() {
        if (!showingControlOverlays) {
            showControlOverlays();
        } else {
            postponeOverlayHiding();
        }
    }

    /**
     * Bind the SRGMediaPlayerView touch event to the overlayController for handle touch and switch
     * visibility of views if necessary.
     *
     * @param videoContainer video container to bind to
     */
    public void bindToVideoContainer(SRGMediaPlayerView videoContainer) {
        if (videoContainer == this.videoContainer) {
            return;
        }
        if (this.videoContainer != null) {
            this.videoContainer.setControlTouchListener(null);
        }
        this.videoContainer = videoContainer;
        if (this.videoContainer != null) {
            this.videoContainer.setControlTouchListener(this);
        }
        propagateControlVisibility();
        updateLoadings(true);
    }

    public void updateLoadings(boolean forceUpdate) {
        updateLoadings(forceUpdate,
                playerController.getState() == SRGMediaPlayerController.State.PREPARING
                        || playerController.getState() == SRGMediaPlayerController.State.BUFFERING
                        || playerController.isSeekPending());
    }

    public boolean isOverlayVisible() {
        return showingControlOverlays;
    }

    private void setLoadingsVisibility(boolean forceUpdate, boolean visible) {
        if (forceUpdate || showingLoadings != visible) {
            showingLoadings = visible;
            handleSpecificVisibility(SRGMediaPlayerView.LayoutParams.OVERLAY_LOADING, visible);
        }
    }

    public void showControlOverlays() {
        playerController.broadcastEvent(SRGMediaPlayerController.Event.Type.OVERLAY_CONTROL_DISPLAYED);
        showingControlOverlays = true;
        propagateControlVisibility();
        postponeOverlayHiding();
    }

    public void hideControlOverlays() {
        if (!forceShowingOverlays) {
            playerController.broadcastEvent(SRGMediaPlayerController.Event.Type.OVERLAY_CONTROL_HIDDEN);
            handler.removeCallbacks(hideOverlaysRunnable);
            showingControlOverlays = false;
            propagateControlVisibility();
        }
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Invalid thread");
        }
        if (mp != playerController) {
            throw new IllegalArgumentException("Unexpected controller");
        }
        switch (event.type) {
            case STATE_CHANGE:
            case PLAYING_STATE_CHANGE:
            case WILL_SEEK:
            case DID_SEEK:
                updateWithPlayer(false);
                break;
            case MEDIA_COMPLETED:
                setForceShowingControlOverlays(true);
                break;
        }

    }

    public void forceUpdate() {
        updateWithPlayer(true);
    }

    private void updateWithPlayer(boolean forceUpdate) {
        SRGMediaPlayerController.State state = playerController.getState();
        switch (state) {
            case PREPARING:
            case BUFFERING:
                break;
            case IDLE:
                setForceShowingControlOverlays(false);
                hideControlOverlays();
                break;
            case RELEASED:
                setForceShowingControlOverlays(true);
                break;
            case READY:
                if (playerController.isPlaying() && playerController.hasVideoTrack() && !playerController.isRemote()) {
                    postponeOverlayHiding();
                    setForceShowingControlOverlays(false);
                } else {
                    setForceShowingControlOverlays(true);
                }
                break;
            default:
                throw new IllegalArgumentException("Unhandled state: " + state);
        }
        updateLoadings(forceUpdate);
    }

    private void updateLoadings(boolean forceUpdate, boolean loading) {
        setLoadingsVisibility(forceUpdate, loading);
    }

    private void postponeOverlayHiding() {
        long now = System.currentTimeMillis();
        if (now > lastOverlayPostponingTime + 250) {
            lastOverlayPostponingTime = now;
            handler.removeCallbacks(hideOverlaysRunnable);
            handler.postDelayed(hideOverlaysRunnable, overlayAutoHideDelay);
        }
    }

    public void propagateControlVisibility() {
        Log.v(TAG, "control visibility: " + showingControlOverlays);
        if (videoContainer != null) {
            int childCount = videoContainer.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = videoContainer.getChildAt(i);
                ViewGroup.LayoutParams vlp = child.getLayoutParams();

                if (vlp instanceof SRGMediaPlayerView.LayoutParams) {
                    SRGMediaPlayerView.LayoutParams lp = (SRGMediaPlayerView.LayoutParams) vlp;
                    switch (lp.overlayMode) {
                        case SRGMediaPlayerView.LayoutParams.OVERLAY_CONTROL:
                            child.setVisibility(showingControlOverlays ? View.VISIBLE : View.GONE);
                            break;
                        case SRGMediaPlayerView.LayoutParams.OVERLAY_ALWAYS_SHOWN:
                            child.setVisibility(View.VISIBLE);
                            break;
                        case SRGMediaPlayerView.LayoutParams.OVERLAY_UNMANAGED:
                        case SRGMediaPlayerView.LayoutParams.OVERLAY_LOADING:
                        default:
                            // Do nothing.
                            break;
                    }
                }
            }
        }
    }

    private void handleSpecificVisibility(int type, boolean visible) {
        if (videoContainer != null && videoContainer.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < videoContainer.getChildCount(); i++) {
                View child = videoContainer.getChildAt(i);
                ViewGroup.LayoutParams vlp = child.getLayoutParams();
                if (vlp != null && vlp instanceof SRGMediaPlayerView.LayoutParams) {
                    SRGMediaPlayerView.LayoutParams lp = (SRGMediaPlayerView.LayoutParams) vlp;
                    if (lp.overlayMode == type) {
                        child.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                }
            }
        }
    }

    public boolean isShowingControlOverlays() {
        return showingControlOverlays;
    }

    /**
     * Configure auto hide delay (delay to change visibility for overlay of OVERLAY_CONTROL type)
     *
     * @param overlayAutoHideDelay auto hide delay in ms
     */
    public static void setOverlayAutoHideDelay(int overlayAutoHideDelay) {
        OverlayController.overlayAutoHideDelay = overlayAutoHideDelay;
    }
}
