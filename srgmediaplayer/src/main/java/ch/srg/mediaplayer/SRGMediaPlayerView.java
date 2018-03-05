package ch.srg.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
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

import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.Util;

import java.util.List;

/**
 * This class is a placeholder for some video.
 * Place it in your layout, or create it programmatically and bind it to a SRGMediaPlayerController to play video
 */
public class SRGMediaPlayerView extends RelativeLayout implements ControlTouchListener, Handler.Callback {

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

    public interface ControlVisibilityChangeListener {
        void onControlVisibilityChanged(boolean visible);
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
    public static final int DEFAULT_AUTO_HIDE_DELAY = 3000;
    private static int overlayAutoHideDelay = DEFAULT_AUTO_HIDE_DELAY;
    private static final int MSG_HIDE_CONTROLS = 1;

    private boolean onTop;
    private boolean adjustToParentScrollView;
    private boolean debugMode;
    private boolean subtitleViewConfigured;

    private boolean showingControlOverlays = true;

    private final Handler handler = new Handler(this);

    private ControlVisibilityChangeListener controlVisibilityChangeListener;


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

    public void setVideoAspectMode(int aspectMode) {
        updateAspectRatio(aspectMode);
    }

    public void setVideoRenderingView(View newVideoRenderingView) {
        Log.v(SRGMediaPlayerController.TAG, "setVideoRenderingView");
        if (videoRenderingView == newVideoRenderingView) {
            return;
        }
        if (videoRenderingView != null) {
            discardVideoRenderingView();
        }
        if (newVideoRenderingView != null) {
            videoRenderingView = newVideoRenderingView;
            updateOnTopInternal(onTop);
            videoRenderingViewWidth = -1;
            videoRenderingViewHeight = -1;
            addView(videoRenderingView, 0);
        }

    }

    public View getVideoRenderingView() {
        return videoRenderingView;
    }

    public String getVideoRenderingViewSizeString() {
        if (videoRenderingView != null) {
            return videoRenderingView.getWidth() + "x" + videoRenderingView.getHeight();
        } else {
            return UNKNOWN_DIMENSION;
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

    public boolean isDebugMode() {
        return debugMode;
    }

    private void updateOnTopInternal(boolean onTop) {
        if (videoRenderingView != null && videoRenderingView instanceof SurfaceView) {
            //((SurfaceView) videoRenderingView).setZOrderMediaOverlay(onTop);
            ((SurfaceView) videoRenderingView).setZOrderOnTop(onTop);
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

    public void setControlTouchListener(ControlTouchListener controlTouchListener) {
        this.touchListener = controlTouchListener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.v(SRGMediaPlayerController.TAG, "dispatchTouchEvent " + event.getAction());

        //This will trigger the onTouch attached to the videorenderingview if it's the case.
        boolean handled = super.dispatchTouchEvent(event);

        if (touchListener != null) {
            touchListener.onMediaControlTouched();
        }
        return handled;
    }

    protected void onVideoRenderViewClicked() {

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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

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

    /**
     * Returns a set of layout parameters with default AUTO_HIDE visibility.
     */
    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, LayoutParams.OVERLAY_UNMANAGED);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof SRGMediaPlayerView.LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public RelativeLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new SRGMediaPlayerView.LayoutParams(getContext(), attrs);
    }

    public void setAdjustToParentScrollView(boolean adjustToParentScrollView) {
        this.adjustToParentScrollView = adjustToParentScrollView;
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

    public void setCues(List<Cue> cues) {
        if (!subtitleViewConfigured) {
            configureSubtitleView();
        }
        if (subtitleView != null) {
            subtitleView.setCues(cues);
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

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_HIDE_CONTROLS:
                hide();
                return true;
            default:
                return false;
        }
    }

    private void updateChildrenVisibility(boolean show) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            ViewGroup.LayoutParams vlp = child.getLayoutParams();
            if (vlp instanceof SRGMediaPlayerView.LayoutParams) {
                switch (((SRGMediaPlayerView.LayoutParams) vlp).overlayMode) {
                    case LayoutParams.OVERLAY_ALWAYS_SHOWN:
                        child.setVisibility(VISIBLE);
                        break;
                    case LayoutParams.OVERLAY_CONTROL:
                        child.setVisibility(show ? VISIBLE : GONE);
                        break;
                    case LayoutParams.OVERLAY_LOADING:
                    case LayoutParams.OVERLAY_UNMANAGED:
                    default:
                        // Do nothing
                        break;
                }
            }
        }
    }

    public void hide() {
        if (showingControlOverlays) {
            updateChildrenVisibility(false);
            showingControlOverlays = false;
            if (controlVisibilityChangeListener != null) {
                controlVisibilityChangeListener.onControlVisibilityChanged(false);
            }
        }
    }

    public void show() {
        cancel();
        if (!showingControlOverlays) {
            updateChildrenVisibility(true);
            showingControlOverlays = true;
            if (controlVisibilityChangeListener != null) {
                controlVisibilityChangeListener.onControlVisibilityChanged(true);
            }
        }
    }

    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }

    public void showTemporarly() {
        show();
        postponeControlsHiding();
    }

    public void ensureHiding() {
        if (showingControlOverlays && !handler.hasMessages(MSG_HIDE_CONTROLS)) {
            postponeControlsHiding();
        }
    }

    private void postponeControlsHiding() {
        handler.removeMessages(MSG_HIDE_CONTROLS);
        handler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLS, overlayAutoHideDelay);
    }

    public boolean isShowingControlOverlays() {
        return this.showingControlOverlays;
    }

    public void setControlVisibilityChangeListener(ControlVisibilityChangeListener controlVisibilityChangeListener) {
        this.controlVisibilityChangeListener = controlVisibilityChangeListener;
        if (controlVisibilityChangeListener != null) {
            controlVisibilityChangeListener.onControlVisibilityChanged(isShowingControlOverlays());
        }
    }

    public static void setOverlayAutoHideDelay(int overlayAutoHideDelay) {
        SRGMediaPlayerView.overlayAutoHideDelay = overlayAutoHideDelay;
    }

    public static int getOverlayAutoHideDelay() {
        return SRGMediaPlayerView.overlayAutoHideDelay;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
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

        public LayoutParams(int width, int height, int overlayMode) {
            super(width, height);
            this.overlayMode = overlayMode;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.VideoContainer_Layout);
            this.overlayMode = a.getInt(R.styleable.VideoContainer_Layout_overlay_mode, OVERLAY_UNMANAGED);
            a.recycle();
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
}

