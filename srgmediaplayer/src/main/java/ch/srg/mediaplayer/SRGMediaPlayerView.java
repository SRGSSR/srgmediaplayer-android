package ch.srg.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
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
public class SRGMediaPlayerView extends ViewGroup {

    private Matrix layoutTransformMatrix = new Matrix();
    private int childWidth;
    private int childHeight;
    private int childLeft;
    private int childTop;
    private int containerWidth;
    private int containerHeight;

    public interface ScaleModeListener {
        void onScaleModeChanged(SRGMediaPlayerView mediaPlayerView, ScaleMode scaleMode);
    }

    public ScaleMode getScaleMode() {
        return scaleMode;
    }

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

    private boolean onTop;
    private boolean adjustToParentScrollView;
    private boolean debugMode;
    private boolean subtitleViewConfigured;

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

    @Nullable
    private View videoRenderingView;

    @Nullable
    private ScaleModeListener scaleModeListener;

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
        if (scaleModeListener != null) {
            scaleModeListener.onScaleModeChanged(this, scaleMode);
        }
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
            videoRenderingView = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        containerWidth = right - left;
        containerHeight = bottom - top;
        calculateChildPosition();

        //for surfaceView ensure setFixedSize. May be unnecessary
        if (videoRenderingView instanceof SurfaceView) {
            switch (scaleMode) {
                case CENTER_INSIDE:
                case TOP_INSIDE:
                    videoRenderingView.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                    ((SurfaceView) videoRenderingView).getHolder().setFixedSize(childWidth, childHeight);
                    break;
                case CENTER_CROP:
                case FIT:
                    videoRenderingView.layout(0, 0, containerWidth, containerHeight);
                    ((SurfaceView) videoRenderingView).getHolder().setFixedSize(containerWidth, containerHeight);
                    break;
            }

        } else if (videoRenderingView instanceof TextureView) {
            videoRenderingView.layout(0, 0, containerWidth, containerHeight);
            layoutTransformMatrix.reset();
            switch (scaleMode) {
                case CENTER_INSIDE:
                    layoutTransformMatrix.postScale(
                            childWidth / (float) containerWidth,
                            childHeight / (float) containerHeight,
                            containerWidth / 2,
                            containerHeight / 2);
                    break;
                case TOP_INSIDE:
                    layoutTransformMatrix.postScale(
                            childWidth / (float) containerWidth,
                            childHeight / (float) containerHeight,
                            0,
                            0);
                    break;
                case CENTER_CROP: {
                    int videoWidth = containerWidth;
                    int videoHeight = containerHeight;
                    float videoContainerAspectRatio = containerWidth / (float) containerHeight;
                    if (actualVideoAspectRatio < videoContainerAspectRatio) {
                        videoHeight = (int) Math.ceil(videoWidth / actualVideoAspectRatio);
                    } else if (actualVideoAspectRatio > videoContainerAspectRatio) {
                        videoWidth = (int) Math.ceil(videoHeight * actualVideoAspectRatio);
                    }
                    layoutTransformMatrix.postScale(
                            videoWidth / (float) containerWidth,
                            videoHeight / (float) containerHeight,
                            containerWidth / 2,
                            containerHeight / 2);
                }
                break;
                case FIT:
                    // TODO Not implemented
                    break;
            }
            ((TextureView) videoRenderingView).setTransform(layoutTransformMatrix);
        }
        if (isDebugMode()) {
            Log.d(TAG, "onLayout: Update videoRenderingView size " +
                    "videoRenderingViewWidth=" + childWidth +
                    " videoRenderingViewHeight=" + childHeight);
        }
        if (isDebugMode()) {
            Log.d(TAG, "onLayout: l=" + childLeft + " t=" + childTop
                    + " surfaceWidth=" + childWidth + " surfaceHeight=" + childHeight
                    + " containerWidth=" + containerWidth + " videoContainerHeight=" + containerHeight);
        }
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            if (v != videoRenderingView) {
                v.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            }
        }
    }

    private void calculateChildPosition() {
        childLeft = 0;
        childTop = 0;
        childWidth = containerWidth;
        childHeight = containerHeight;
        //Then we do some math to force actual size of the videoRenderingView
        float videoContainerAspectRatio = containerWidth / (float) containerHeight;
        switch (scaleMode) {
            case CENTER_CROP:
            case CENTER_INSIDE:
            case TOP_INSIDE:
                if (actualVideoAspectRatio > videoContainerAspectRatio) {
                    childHeight = (int) Math.ceil(childWidth / actualVideoAspectRatio);
                    if (scaleMode != ScaleMode.TOP_INSIDE) {
                        childTop = (containerHeight - childHeight) / 2;
                    }
                } else if (actualVideoAspectRatio < videoContainerAspectRatio) {
                    childWidth = (int) Math.ceil(childHeight * actualVideoAspectRatio);
                    if (scaleMode != ScaleMode.TOP_INSIDE) {
                        childLeft = (containerWidth - childWidth) / 2;
                    }
                }
                break;
            case FIT:
            default:
                throw new IllegalStateException("Unsupported scale mode: " + scaleMode);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int specWidthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int specHeight = MeasureSpec.getSize(heightMeasureSpec);
        final int specHeightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = specWidth;
        int height = specHeight;

        if (specWidthMode == MeasureSpec.EXACTLY && specHeightMode == MeasureSpec.EXACTLY) {
            if (!autoAspect) {
                Log.w(SRGMediaPlayerController.TAG, "Aspect ratio cannot be supported with these layout constraints");
            }
        } else if (specWidthMode == MeasureSpec.EXACTLY) {
            height = (int) (width / containerAspectRatio);

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
            width = (int) (height * containerAspectRatio);

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
        if (width != containerWidth || height != containerHeight) {
            containerWidth = width;
            containerHeight = height;
            calculateChildPosition();
        }

        int childSpecWidth = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
        int childSpecHeight = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            if (v != videoRenderingView) {
                v.measure(childSpecWidth, childSpecHeight);
            }
        }
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
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
    protected LayoutParams generateDefaultLayoutParams() {
        return new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
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

    @TargetApi(19)
    private CaptioningManager getCaptioningManager() {
        return (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void setScaleModeListener(@Nullable ScaleModeListener scaleModeListener) {
        this.scaleModeListener = scaleModeListener;
        if (scaleModeListener != null) {
            scaleModeListener.onScaleModeChanged(this, getScaleMode());
        }
    }
}

