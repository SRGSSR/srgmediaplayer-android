package ch.srg.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.CaptioningManager;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.Util;

import java.util.List;

/**
 * Created by StahliJ on 05.01.2018.
 */

public class SRGMediaPlayerView extends RelativeLayout implements
        Player.EventListener, SimpleExoPlayer.VideoListener, ControlTouchListener, TextRenderer.Output, SRGMediaPlayerController.Listener {


    public enum ScaleMode {
        CENTER_INSIDE,
        TOP_INSIDE,
        /**
         * Not currently supported.
         */
        CENTER_CROP,
        /**
         * Not currently supported.
         */
        FIT
    }

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    public static final String TAG = "SRGMediaPlayerView";
    public static final float DEFAULT_ASPECT_RATIO = 16 / 9f;
    public static final String UNKNOWN_DIMENSION = "0x0";
    public static final int ASPECT_RATIO_SQUARE = 1;
    public static final int ASPECT_RATIO_4_3 = 2;
    public static final int ASPECT_RATIO_16_10 = 3;
    public static final int ASPECT_RATIO_16_9 = 4;
    public static final int ASPECT_RATIO_21_9 = 5;
    public static final int ASPECT_RATIO_AUTO = 0;
    private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    // Attributs

    @Nullable
    private SRGMediaPlayerController mediaPlayerController;
    private boolean onTop;
    private boolean adjustToParentScrollView;
    private boolean debugMode;
    private boolean subtitleViewConfigured;
    private boolean currentViewKeepScreenOn;
    //Used to force keepscreen on even when not playing
    boolean externalWakeLock = true;

    @Nullable
    private SubtitleView subtitleView;

    private boolean autoAspect = true;
    /**
     * Aspect ratio of the entire container.
     */
    private float containerAspectRatio = DEFAULT_ASPECT_RATIO;
    /**
     * Aspect ratio of the video being played.
     */
    private float actualVideoAspectRatio = DEFAULT_ASPECT_RATIO;
    private ScaleMode scaleMode = null;
    private View videoRenderingView;
    private int videoRenderingViewWidth = -1;
    private int videoRenderingViewHeight = -1;
    @Nullable
    private ControlTouchListener touchListener;
    private boolean videoRenderViewTrackingTouch = false;
    private ViewType viewType = ViewType.TYPE_TEXTUREVIEW;

    public SRGMediaPlayerView(Context context) {
        this(context, null, 0);
    }

    public SRGMediaPlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SRGMediaPlayerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SRGMediaPlayerView, 0, 0);

        int scaleVal = a.getInteger(R.styleable.SRGMediaPlayerView_videoScale, 0);
        switch (scaleVal) {
            case 1:
                scaleMode = ScaleMode.TOP_INSIDE;
                break;
            case 2:
                scaleMode = ScaleMode.CENTER_CROP;
                break;
            case 3:
                scaleMode = ScaleMode.FIT;
                break;
            case 0:
            default:
                scaleMode = ScaleMode.CENTER_INSIDE;
                break;
        }
        int aspectVal = a.getInteger(R.styleable.SRGMediaPlayerView_containerAspectRatio, 0);
        updateAspectRatio(aspectVal);
        adjustToParentScrollView = a.getBoolean(R.styleable.SRGMediaPlayerView_adjustToParentScrollView, true);
        a.recycle();
        currentViewKeepScreenOn = getKeepScreenOn();
    }

    public void bindController(@NonNull SRGMediaPlayerController newMediaPlayer) {
        if (mediaPlayerController != newMediaPlayer) {
            if (mediaPlayerController != null) {
                unbindController();
            }
            mediaPlayerController = newMediaPlayer;
            mediaPlayerController.onBindFromMediaPlayerView(this);
            manageKeepScreenOnInternal();
            bindOrCreateVideoContainerView();
        } else {
            Log.d(TAG, "SRGMediaPlayerController already binded to this view!");
        }
    }

    public void unbindController() {
        if (mediaPlayerController != null) {
            if (videoRenderingView != null) {
                unBindVideoContainerViewToControler();
            }
            manageKeepScreenOnInternal();
            this.mediaPlayerController.onUnbindFromMediaPlayerView(this);
        }
        this.mediaPlayerController = null;
    }

    protected void onVideoRenderViewClicked() {
        // Nothing by default
    }


    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        // TODO : what to do?
    }

    // Exoplayer Listener

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        // nothing
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        // nothing
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // nothing
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.v(TAG, toString() + " exo state change: " + playWhenReady + " " + playbackState);
        switch (playbackState) {
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                break;
            case Player.STATE_READY:
                manageKeepScreenOnInternal();
                break;
            case Player.STATE_ENDED:
                manageKeepScreenOnInternal();
                break;
        }

        manageKeepScreenOnInternal();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // nothing
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        manageKeepScreenOnInternal();
    }

    @Override
    public void onPositionDiscontinuity() {
        // nothing
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    } // nothing

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        float aspectRatio = ((float) width / (float) height) * pixelWidthHeightRatio;
        if ((aspectRatio / 90) % 2 == 1) {
            aspectRatio = 1 / aspectRatio;
        }
        setVideoAspectRatio(aspectRatio);
    }

    @Override
    public void onRenderedFirstFrame() {
        if (mediaPlayerController != null) {
            mediaPlayerController.setRenderedFirstFrame();
        }
    }

    @Override
    public void onCues(List<Cue> cues) {
        updateCues(cues);
    }

    // ControlTouchListener

    @Override
    public void onMediaControlTouched() {
        if (touchListener != null) {
            touchListener.onMediaControlTouched();
        }
    }

    @Override
    public void onMediaControlBackgroundTouched() {
        if (touchListener != null) {
            touchListener.onMediaControlBackgroundTouched();
        }
    }

    // Setters

    public void setVideoAspectMode(int aspectMode) {
        updateAspectRatio(aspectMode);
    }

    /**
     * Set the video aspect ratio for the video being played by the container
     *
     * @param videoAspect the video aspect ratio
     */
    public void setVideoAspectRatio(float videoAspect) {
        if (Math.abs(actualVideoAspectRatio - videoAspect) > ASPECT_RATIO_TOLERANCE) {
            actualVideoAspectRatio = videoAspect;
            if (autoAspect) {
                this.containerAspectRatio = videoAspect;
            }
            requestLayout();
        }
    }

    public void setViewType(@NonNull ViewType viewType) {
        if (this.viewType != viewType) {
            if (mediaPlayerController != null) {
                switchToViewType(viewType);
            }
        }
    }

    public void setScaleMode(ScaleMode scaleMode) {
        this.scaleMode = scaleMode;
        requestLayout();
    }

    public void setOnTop(boolean onTop) {
        this.onTop = onTop;
        updateOnTopInternal(onTop);
    }

    public void forceKeepScreenOn(boolean lock) {
        externalWakeLock = lock;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void setControlTouchListener(ControlTouchListener controlTouchListener) {
        this.touchListener = controlTouchListener;
    }

    public void setAdjustToParentScrollView(boolean adjustToParentScrollView) {
        this.adjustToParentScrollView = adjustToParentScrollView;
    }

    // Getters

    public @Nullable
    View getVideoRenderingView() {
        return videoRenderingView;
    }

    public String getVideoRenderingViewSizeString() {
        if (videoRenderingView != null) {
            return videoRenderingView.getWidth() + "x" + videoRenderingView.getHeight();
        } else {
            return UNKNOWN_DIMENSION;
        }
    }

    public ScaleMode getScaleMode() {
        return scaleMode;
    }

    public boolean isBoundToController() {
        return mediaPlayerController != null;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isAdjustToParentScrollView() {
        return adjustToParentScrollView;
    }

    public boolean isForceKeepOnScreen() {
        return externalWakeLock;
    }

    // View layouts

    /**
     * Returns a set of layout parameters with default AUTO_HIDE visibility.
     */
    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new SRGMediaPlayerView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, LayoutParams.OVERLAY_UNMANAGED);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public RelativeLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    /**
     * Apply a specific overlayMode. Make sure to call {@link SRGMediaPlayerController#updateOverlayVisibilities}
     * to force visibilty update after this.
     *
     * @param targetView the view on which the mode will be applied
     * @param targetMode the mode to apply
     * @return true if the mode is applied, false otherwise
     */
    public static final boolean applyOverlayMode(View targetView, int targetMode) {
        if (targetView != null && targetView.getLayoutParams() != null && targetView.getLayoutParams() instanceof LayoutParams) {
            ((LayoutParams) targetView.getLayoutParams()).overlayMode = targetMode;
            return true;
        }
        return false;
    }

    /**
     * Per-child layout information associated with VideoContainer.
     */
    public static class LayoutParams extends RelativeLayout.LayoutParams {
        /**
         * Indicate that the child must be leave unchanged
         */
        public static final int OVERLAY_UNMANAGED = -1;

        /**
         * Indicate that the child can be auto hidden by the component
         */
        public static final int OVERLAY_CONTROL = -2;

        /**
         * Indicate that the child is always shown
         */
        public static final int OVERLAY_ALWAYS_SHOWN = -3;

        /**
         * Indicate that the child is displayed when player is loading
         */
        public static final int OVERLAY_LOADING = -4;

        /**
         * Information about how is handle the component visibility. Can be one of the
         * constants AUTO_HIDE or ALWAYS_SHOW.
         */
        @ViewDebug.ExportedProperty(category = "layout", mapping = {
                @ViewDebug.IntToString(from = OVERLAY_UNMANAGED, to = "UNMANAGED"),
                @ViewDebug.IntToString(from = OVERLAY_CONTROL, to = "CONTROL"),
                @ViewDebug.IntToString(from = OVERLAY_ALWAYS_SHOWN, to = "ALWAYS_SHOWN"),
                @ViewDebug.IntToString(from = OVERLAY_LOADING, to = "LOADING")
        })
        public int overlayMode;

        public LayoutParams(int width, int height, int showMode) {
            super(width, height);
            resolveShowMode(showMode);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.VideoContainer_Layout);
            resolveShowMode(a.getInt(R.styleable.VideoContainer_Layout_overlay_mode, OVERLAY_UNMANAGED));
            a.recycle();
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        private void resolveShowMode(int value) {
            // TODO Why this ?, why not: overlayMode = value; AssertValidOverlayMode(value); ?
            switch (value) {
                case OVERLAY_CONTROL:
                    this.overlayMode = OVERLAY_CONTROL;
                    break;
                case OVERLAY_ALWAYS_SHOWN:
                    this.overlayMode = OVERLAY_ALWAYS_SHOWN;
                    break;
                case OVERLAY_LOADING:
                    this.overlayMode = OVERLAY_LOADING;
                    break;
                case OVERLAY_UNMANAGED:
                default:
                    this.overlayMode = OVERLAY_UNMANAGED;
                    break;
            }
        }
    } //End LayoutParams

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int specWidthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int specHeight = MeasureSpec.getSize(heightMeasureSpec);
        final int specHeightMode = MeasureSpec.getMode(heightMeasureSpec);

        if (specWidthMode == MeasureSpec.EXACTLY && specHeightMode == MeasureSpec.EXACTLY) {
            if (!autoAspect) {
                Log.w(SRGMediaPlayerController.TAG, "Aspect ratio cannot be supported with these layout constraints");
            }
        } else if (specWidthMode == MeasureSpec.EXACTLY) {
            int width = specWidth;
            int height = (int) (width / containerAspectRatio);

            int maxHeight;
            maxHeight = specHeightMode == MeasureSpec.AT_MOST ? specHeight : MEASURED_SIZE_MASK;
            if (adjustToParentScrollView) {
                maxHeight = Math.min(maxHeight, getParentScrollViewHeight());
            }

            if (height > maxHeight) {
                height = maxHeight;
                width = (int) (height * containerAspectRatio);
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            }
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        } else if (specHeightMode == MeasureSpec.EXACTLY) {
            int height = specHeight;
            int width = (int) (height * containerAspectRatio);

            int maxWidth;
            maxWidth = specWidthMode == MeasureSpec.AT_MOST ? specWidth : MEASURED_SIZE_MASK;
            if (adjustToParentScrollView) {
                maxWidth = Math.min(maxWidth, getParentScrollViewWidth());
            }

            if (width > maxWidth) {
                width = maxWidth;
                height = (int) (width / containerAspectRatio);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            }
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        }
        if (isDebugMode()) {
            Log.v(TAG, String.format("onMeasure W:%d/%s, H:%d/%s -> %d,%d",
                    specWidth, modeName(specWidthMode), specHeight, modeName(specHeightMode),
                    MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec)));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        //Then we do some math to force actual size of the videoRenderingView
        if (videoRenderingView != null) {
            int l = 0, t = 0;
            int videoContainerWidth = right - left;
            int videoContainerHeight = bottom - top;
            int surfaceWidth = videoContainerWidth;
            int surfaceHeight = videoContainerHeight;
            float videoContainerAspectRatio = videoContainerWidth / (float) videoContainerHeight;
            switch (scaleMode) {
                case CENTER_INSIDE:
                case TOP_INSIDE:
                    if (actualVideoAspectRatio > videoContainerAspectRatio) {
                        surfaceHeight = (int) Math.ceil(surfaceWidth / actualVideoAspectRatio);
                        if (scaleMode == ScaleMode.CENTER_INSIDE) {
                            t = (videoContainerHeight - surfaceHeight) / 2;
                        }
                    } else if (actualVideoAspectRatio < videoContainerAspectRatio) {
                        surfaceWidth = (int) Math.ceil(surfaceHeight * actualVideoAspectRatio);
                        if (scaleMode == ScaleMode.CENTER_INSIDE) {
                            l = (videoContainerWidth - surfaceWidth) / 2;
                        }
                    }
                    break;
                case CENTER_CROP:
                case FIT:
                default:
                    throw new IllegalStateException("Unsupported scale mode: " + scaleMode);
            }

            videoRenderingView.setY(t);
            videoRenderingView.setX(l);
            //check against last set values
            if (videoRenderingViewWidth != surfaceWidth || videoRenderingViewHeight != surfaceHeight) {
                videoRenderingViewWidth = surfaceWidth;
                videoRenderingViewHeight = surfaceHeight;
                //for surfaceView ensure setFixedSize. May be unnecessary
                if (videoRenderingView instanceof SurfaceView) {
                    ((SurfaceView) videoRenderingView).getHolder().setFixedSize(surfaceWidth, surfaceHeight);
                } else if (videoRenderingView instanceof TextureView) {
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) videoRenderingView.getLayoutParams();
                    lp.width = surfaceWidth;
                    lp.height = surfaceHeight;
                    videoRenderingView.setLayoutParams(lp);
                }
                if (isDebugMode()) {
                    Log.d(TAG, "onLayout: Update videoRenderingView size " +
                            "videoRenderingViewWidth=" + videoRenderingViewWidth +
                            " videoRenderingViewHeight=" + videoRenderingViewHeight);
                }
            }
            if (isDebugMode()) {
                Log.d(TAG, "onLayout: l=" + l + " t=" + t
                        + " surfaceWidth=" + surfaceWidth + " surfaceHeight=" + surfaceHeight
                        + " videoContainerWidth=" + videoContainerWidth + " videoContainerHeight=" + videoContainerHeight);
            }
        }
    }


    // View

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.v(SRGMediaPlayerController.TAG, "dispatchTouchEvent " + event.getAction());

        //This will trigger the onTouch attached to the videorenderingview if it's the case.
        boolean handled = super.dispatchTouchEvent(event);

        if (touchListener != null) {
            boolean controlHandled = isControlHit((int) event.getX(), (int) event.getY()) && handled;
            if (controlHandled) {
                touchListener.onMediaControlTouched();
            } else {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    videoRenderViewTrackingTouch = true;
                }
                if (videoRenderViewTrackingTouch) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        onVideoRenderViewClicked();
                        videoRenderViewTrackingTouch = false;
                        touchListener.onMediaControlBackgroundTouched();
                    }
                }
                handled = true;
            }
        }
        return handled;
    }

    protected boolean isControlHit(int x, int y) {
        boolean controlHit = false;
        for (int i = getChildCount(); i >= 0; --i) {
            View child = getChildAt(i);
            if (child != null) {
                ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                if (child.getVisibility() == VISIBLE && layoutParams instanceof LayoutParams && ((LayoutParams) layoutParams).overlayMode == LayoutParams.OVERLAY_CONTROL) {
                    Rect bounds = new Rect();
                    child.getHitRect(bounds);
                    if (bounds.contains(x, y)) {
                        controlHit = true;
                        break;
                    }
                }
            }
        }
        return controlHit;
    }

    private void updateAspectRatio(int aspectMode) {
        switch (aspectMode) {
            case ASPECT_RATIO_SQUARE: // square
                containerAspectRatio = 1f;
                autoAspect = false;
                break;
            case ASPECT_RATIO_4_3: // standard_4_3
                containerAspectRatio = 4 / 3f;
                autoAspect = false;
                break;
            case ASPECT_RATIO_16_10: // wide_16_10
                containerAspectRatio = 16 / 10f;
                autoAspect = false;
                break;
            case ASPECT_RATIO_16_9: // hdvideo_16_9
                containerAspectRatio = 16 / 9f;
                autoAspect = false;
                break;
            case ASPECT_RATIO_21_9: // movie_21_9
                containerAspectRatio = 21 / 9f;
                autoAspect = false;
                break;
            case ASPECT_RATIO_AUTO:
            default:
                autoAspect = true;
                break;
        }
        requestLayout();
    }

    private void updateCues(List<Cue> cues) {
        if (!subtitleViewConfigured) {
            configureSubtitleView();
        }
        if (subtitleView != null) {
            subtitleView.setCues(cues);
        }
    }

    private void configureSubtitleView() {
        if (subtitleView == null) {
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i) instanceof SubtitleView) {
                    subtitleView = (SubtitleView) getChildAt(i);
                    break;
                }
            }
        }
        if (subtitleView != null) {
            CaptionStyleCompat style;
            float fontScale;
            if (Util.SDK_INT >= 19) {
                style = getUserCaptionStyleV19();
                fontScale = getUserCaptionFontScaleV19();
            } else {
                style = CaptionStyleCompat.DEFAULT;
                fontScale = 1.0f;
            }
            subtitleView.setStyle(style);
            subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
        }
        subtitleViewConfigured = true;
    }

    private void updateOnTopInternal(boolean onTop) {
        if (videoRenderingView != null && videoRenderingView instanceof SurfaceView) {
            //((SurfaceView) videoRenderingView).setZOrderMediaOverlay(onTop);
            ((SurfaceView) videoRenderingView).setZOrderOnTop(onTop);
        }
    }

    @TargetApi(19)
    private float getUserCaptionFontScaleV19() {
        CaptioningManager captioningManager = getCaptioningManager();
        return captioningManager.getFontScale();
    }

    @TargetApi(19)
    private CaptionStyleCompat getUserCaptionStyleV19() {
        CaptioningManager captioningManager = getCaptioningManager();
        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

    private CaptioningManager getCaptioningManager() {
        return (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SRGMediaPlayerView{");
        sb.append(", width=").append(getWidth());
        sb.append(", height=").append(getHeight());
        sb.append(", videoRenderingView=").append(videoRenderingView);
        sb.append(", videoRenderingViewWidth=").append(videoRenderingViewWidth);
        sb.append(", videoRenderingViewHeight=").append(videoRenderingViewHeight);
        sb.append(", scaleMode=").append(scaleMode);
        sb.append(", containerAspectRatio=").append(containerAspectRatio);
        sb.append(", onTop=").append(onTop);
        sb.append(", autoAspect=").append(autoAspect);
        sb.append(", actualVideoAspectRatio=").append(actualVideoAspectRatio);
        sb.append(", context=").append(getContext());
        sb.append('}');
        return sb.toString();
    }

    private int getParentScrollViewHeight() {
        ViewParent parent = getParent();
        do {
            if (parent != null) {
                if (parent instanceof ScrollView) {
                    return ((ScrollView) parent).getMeasuredHeight();
                }
                parent = parent.getParent();
            }
        } while (parent != null);

        return Integer.MAX_VALUE;
    }

    private int getParentScrollViewWidth() {
        ViewParent parent = getParent();
        do {
            if (parent != null) {
                if (parent instanceof HorizontalScrollView) {
                    return ((HorizontalScrollView) parent).getMeasuredWidth();
                }
                parent = parent.getParent();
            }
        } while (parent != null);

        return Integer.MAX_VALUE;
    }

    private String modeName(int mode) {
        switch (mode) {
            case MeasureSpec.UNSPECIFIED:
                return "unspecified";
            case MeasureSpec.AT_MOST:
                return "at_most";
            case MeasureSpec.EXACTLY:
                return "exactly";
            default:
                return "???";
        }
    }

    private void manageKeepScreenOnInternal() {
        if (mediaPlayerController != null) {
            final boolean lock = externalWakeLock || mediaPlayerController.isPlaying();
            logV("Scheduling change keepScreenOn currently attached mediaPlayerView to " + lock + " isPlaying " + mediaPlayerController.isPlaying());
            if (currentViewKeepScreenOn != lock) {
                currentViewKeepScreenOn = lock;
                setKeepScreenOn(lock);
            } else {
                setKeepScreenOn(false);
            }
        }
    }

    private void logV(String msg) {
        if (isDebugMode()) {
            Log.v(TAG, msg);
        }
    }

    private void logE(String msg) {
        if (isDebugMode()) {
            Log.e(TAG, msg);
        }
    }

    private void logE(String msg, Exception e) {
        if (isDebugMode()) {
            Log.e(TAG, msg, e);
        }
    }

    private void bindVideoContainerViewToControler() {
        if (mediaPlayerController != null) {
            try {
                mediaPlayerController.bindVideoContainerView(videoRenderingView);
            } catch (SRGMediaPlayerException e) {
                logE("Can't bind videoRenderingView ", e);
            }
        }
    }

    private void unBindVideoContainerViewToControler() {
        if (mediaPlayerController != null) {
            try {
                mediaPlayerController.unbindVideoContainerView(videoRenderingView);
                discardVideoRenderingView();
            } catch (SRGMediaPlayerException e) {
                logE("Can't unbind videoRenderingView ", e);
            }
        }
    }

    private void bindOrCreateVideoContainerView() {
        if (videoRenderingView == null) {
            createRenderingContainerView();
        } else {
            bindVideoContainerViewToControler();
        }

    }

    private void createRenderingContainerView() {
        switch (viewType) {
            case TYPE_SURFACEVIEW:
                this.videoRenderingView = createSurfaceView();
                break;
            case TYPE_TEXTUREVIEW:
            default:
                this.videoRenderingView = createTextureView();
                break;
        }
        setVideoRenderingView();
    }

    private void setVideoRenderingView() {
        Log.v(SRGMediaPlayerController.TAG, "addViewContainer");
        if (videoRenderingView != null && videoRenderingView.getParent() == null) {
            updateOnTopInternal(onTop);
            videoRenderingViewWidth = -1;
            videoRenderingViewHeight = -1;
            addView(videoRenderingView, 0);
        }
    }

    public void discardVideoRenderingView() {
        if (videoRenderingView != null) {
            removeView(videoRenderingView);
            videoRenderingView.setOnTouchListener(null);
            videoRenderingView = null;
            videoRenderingViewWidth = -1;
            videoRenderingViewHeight = -1;
        }
    }


    private View createTextureView() {
        final TextureView textureView = new TextureView(getContext());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @SuppressWarnings("ConstantConditions")
                // It is very important to check renderingView type as it may have changed (do not listen to lint here!)
            boolean isCurrent(SurfaceTexture surfaceTexture) {
                return textureView.getSurfaceTexture() == surfaceTexture;
            }

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Log.v(TAG, textureView + "binding, surfaceTextureAvailable" + this);
                if (isCurrent(surfaceTexture)) {
                    try {
                        if (mediaPlayerController != null) {
                            mediaPlayerController.bindVideoContainerView(textureView);
                        }
                    } catch (SRGMediaPlayerException e) {
                        Log.d(TAG, "Error binding view", e);
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
                // TODO onSurfaceTextureSizeChanged
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                // TODO onSurfaceTextureUpdated
            }
        });

        return textureView;
    }

    private View createSurfaceView() {
        final SurfaceView surfaceView = new SurfaceView(getContext());
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                Log.v(TAG, surfaceView + "binding, surfaceCreated" + this);
                try {
                    if (surfaceView.getHolder() == surfaceHolder) {
                        if (mediaPlayerController != null) {
                            mediaPlayerController.bindVideoContainerView(surfaceView);
                        }
                    } else {
                        Log.d(TAG, "Surface created, but media player delegate retired");
                    }
                } catch (SRGMediaPlayerException e) {
                    Log.d(TAG, "Error binding view", e);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.v(TAG, surfaceView + "binding, surfaceChanged" + this);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.v(TAG, surfaceView + "binding, surfaceDestroyed" + this);
            }
        });
        return surfaceView;
    }

    private void switchToViewType(ViewType viewType) {
        this.viewType = viewType;
        if (videoRenderingView == null) {
            createRenderingContainerView();
        } else {
            discardVideoRenderingView();
            unBindVideoContainerViewToControler();
            createRenderingContainerView();
        }
    }
}
