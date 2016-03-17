package ch.srg.mediaplayer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

/**
 * This class is a placeholder for some video.
 * Place it in your layout, or create it programmatically and bind it to a SRGMediaPlayerController to play video
 */
public class SRGMediaPlayerView extends RelativeLayout implements View.OnTouchListener {

    // This code may be used to disallow multiple view on Nexus 5 for exemple
    //
    // private static final AtomicInteger surfaceViewCount = new AtomicInteger(0);
    //private static final List<String> restrictedLandscapeModel = Arrays.asList("hammerhead");
    //		if (restrictedLandscapeModel.contains(Build.DEVICE) && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
    //			if (surfaceViewCount.compareAndSet(0, 1)) {
    //				SurfaceView surfaceView = new SurfaceView(this.getContext());
    //				surfaceView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
    //					@Override
    //					public void onViewAttachedToWindow(View v) {}
    //
    //					@Override
    //					public void onViewDetachedFromWindow(View v) {
    //						surfaceViewCount.decrementAndGet();
    //					}
    //				});
    //				setVideoRenderingView(surfaceView);
    //				return surfaceView;
    //			} else {
    //				return null;
    //			}
    //		}

    public static final float DEFAULT_ASPECT_RATIO = 16 / 9f;
    public static final String UNKNOWN_DIMENSION = "0x0";
    private boolean onTop;
    private boolean adjustToParentScrollView;
    private boolean debugMode;

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * Interface definition for a callback to be invoked when touch event occurs.
     */
    public interface VideoTouchListener {

        void onVideoRenderingViewTouched(SRGMediaPlayerView srgMediaPlayerView);

        void onVideoOverlayTouched(SRGMediaPlayerView srgMediaPlayerView);
    }

    public enum ScaleMode {
        CENTER_INSIDE,
        TOP_INSIDE,
        CENTER_CROP,
        FIT
    }

    private boolean autoAspect = true;
    private float aspectRatio = DEFAULT_ASPECT_RATIO;
    private float actualVideoAspectRatio = DEFAULT_ASPECT_RATIO;

    private ScaleMode scaleMode = null;

    private View videoRenderingView;
    private int videoRenderingViewWidth = -1;
    private int videoRenderingViewHeight = -1;
    private VideoTouchListener touchListener;

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
        switch (aspectVal) {
            case 1: // square
                aspectRatio = 1f;
                autoAspect = false;
                break;
            case 2: // standard_4_3
                aspectRatio = 4 / 3f;
                autoAspect = false;
                break;
            case 3: // wide_16_10
                aspectRatio = 16 / 10f;
                autoAspect = false;
                break;
            case 4: // hdvideo_16_9
                aspectRatio = 16 / 9f;
                autoAspect = false;
                break;
            case 5: // movie_21_9
                aspectRatio = 21 / 9f;
                autoAspect = false;
                break;
            case 0:
            default:
                autoAspect = true;
                break;
        }
        adjustToParentScrollView = a.getBoolean(R.styleable.SRGMediaPlayerView_adjustToParentScrollView, true);
        a.recycle();
    }

    /**
     * Set the desired aspect ratio for the ViceoContainer
     *
     * @param desiredAspect the desired aspect ratio (width/height)
     * @return true if the VideoContainer accept the ratio (auto mode), false if the ratio is already fixed
     */
    public boolean setVideoAspectRatio(float desiredAspect) {
        actualVideoAspectRatio = desiredAspect;
        if (autoAspect) {
            this.aspectRatio = desiredAspect;
            if (aspectRatio != desiredAspect) {
                requestLayout();
            }
            return true;
        }
        return false;
    }

    public void setVideoRenderingView(View newVideoRenderingView) {
        if (videoRenderingView == newVideoRenderingView) {
            return;
        }
        if (videoRenderingView != null) {
            discardVideoRenderingView();
        }
        if (newVideoRenderingView != null) {
            videoRenderingView = newVideoRenderingView;
            updateOnTopInternal(onTop);
            videoRenderingView.setOnTouchListener(this);
            videoRenderingViewWidth = -1;
            videoRenderingViewHeight = -1;
            RelativeLayout.LayoutParams surfaceParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            videoRenderingView.setLayoutParams(surfaceParams);
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
    }

    public void setOnTop(boolean onTop) {
        this.onTop = onTop;
        updateOnTopInternal(onTop);
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

    public void setVideoTouchListener(VideoTouchListener videoTouchListener) {
        this.touchListener = videoTouchListener;
    }

    /**
     * This flag is used to track videoRenderView touch event.
     * It is set by the onTouch attached to the videoRenderView and reset by the dispatchTouchEvent.
     * This flag prevents the dispatchTouchEvent to send back the event to the VideoTouchListener.
     */
    private boolean videoRenderViewTrackingTouch = false;
    private boolean videoRenderViewHandledTouch = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.v(SRGMediaPlayerController.TAG, "dispatchTouchEvent " + event.getAction());

        //This will trigger the onTouch attached to the videorenderingview if it's the case.
        boolean handled = super.dispatchTouchEvent(event);
        if (videoRenderViewTrackingTouch) {
            return handled;
        }
        if (videoRenderViewHandledTouch) {
            videoRenderViewHandledTouch = false;
            return handled;
        }

        if (touchListener != null) {
            boolean controlHit = isControlHit((int) event.getX(), (int) event.getY());
            if (controlHit) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        touchListener.onVideoOverlayTouched(this);
                        break;
                    case MotionEvent.ACTION_UP:
                        touchListener.onVideoOverlayTouched(this);
                        break;
                    case MotionEvent.ACTION_DOWN:
                    default:
                        break;
                }
            }
        }
        return handled;
    }

    public boolean isControlHit(int x, int y) {
        boolean controlHit = false;
        for(int i = getChildCount(); i >= 0; --i)
        {
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

    /**
     * Only used for the videorenderingview
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.v(SRGMediaPlayerController.TAG, "onTouch videoview " + event.getAction());
        if (v == videoRenderingView) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                videoRenderViewTrackingTouch = true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (touchListener != null) {
                    touchListener.onVideoRenderingViewTouched(this);
                }
                videoRenderViewTrackingTouch = false;
                videoRenderViewHandledTouch = true;
            }
        }
        return true;
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // We do the layout first, the onMeasure will compute the actual size correctly based on resizeMode and aspectRatio
        super.onLayout(changed, left, top, right, bottom);

        //Then we do some math to force actual size of the videoRenderingView
        if (videoRenderingView != null) {
            int videoContainerWidth = right - left;
            int videoContainerHeight = bottom - top;
            int surfaceWidth = videoContainerWidth;
            int surfaceHeight = videoContainerHeight;
            float videoContainerAspectRatio = videoContainerWidth / (float) videoContainerHeight;
            if (actualVideoAspectRatio > videoContainerAspectRatio) {
                surfaceHeight = (int) Math.ceil(surfaceWidth / actualVideoAspectRatio);
            } else if (actualVideoAspectRatio < videoContainerAspectRatio) {
                surfaceWidth = (int) Math.ceil(surfaceHeight * actualVideoAspectRatio);
//            } else {
                //Nothing values already set above
            }

            //check against last set values
            if (videoRenderingViewWidth != surfaceWidth || videoRenderingViewHeight != surfaceHeight) {
                videoRenderingViewWidth = surfaceWidth;
                videoRenderingViewHeight = surfaceHeight;
                //for surfaceView ensure setFixedSize. May be unnecessary
                if (videoRenderingView instanceof SurfaceView) {
                    ((SurfaceView) videoRenderingView).getHolder().setFixedSize(surfaceWidth, surfaceHeight);
                }
                RelativeLayout.LayoutParams surfaceParams = new RelativeLayout.LayoutParams(videoRenderingViewWidth, videoRenderingViewHeight);
                if (scaleMode == ScaleMode.TOP_INSIDE) {
                    surfaceParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                    surfaceParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                } else if (scaleMode == ScaleMode.CENTER_INSIDE) {
                    surfaceParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                } else {
                    throw new IllegalArgumentException("Scale mode not supported " + scaleMode);
                }

                videoRenderingView.setLayoutParams(surfaceParams);
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
            Log.w(SRGMediaPlayerController.TAG, "Aspect ratio cannot be supported with these layout constraints");
        } else if (specWidthMode == MeasureSpec.EXACTLY) {
            int width = specWidth;
            int height = (int) (width / aspectRatio);

            int maxHeight;
            maxHeight = specHeightMode == MeasureSpec.AT_MOST ? specHeight : MEASURED_SIZE_MASK;
            if (adjustToParentScrollView) {
                maxHeight = Math.min(maxHeight, getParentScrollViewHeight());
            }

            if (height > maxHeight) {
                height = maxHeight;
                width = (int) (height * aspectRatio);
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            }
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        } else if (specHeightMode == MeasureSpec.EXACTLY) {
            int height = specHeight;
            int width = (int) (height * aspectRatio);

            int maxWidth;
            maxWidth = specWidthMode == MeasureSpec.AT_MOST ? specWidth : MEASURED_SIZE_MASK;
            if (adjustToParentScrollView) {
                maxWidth = Math.min(maxWidth, getParentScrollViewWidth());
            }

            if (width > maxWidth) {
                width = maxWidth;
                height = (int) (width / aspectRatio);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            }
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        }
        if (isDebugMode()) {
            Log.v(SRGMediaPlayerController.TAG, String.format("onMeasure W:%d/%s, H:%d/%s -> %d,%d",
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
        sb.append(", aspectRatio=").append(aspectRatio);
        sb.append(", onTop=").append(onTop);
        sb.append(", autoAspect=").append(autoAspect);
        sb.append(", actualVideoAspectRatio=").append(actualVideoAspectRatio);
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

    /**
     * Apply a specific overlayMode. Make sure to call {@link SRGMediaPlayerController#updateOverlayVisibilities}
     * to force visibilty update after this.
     *
     * @param targetView the view on which the mode will be applied
     * @param targetMode the mode to apply
     * @return true if the mode is applied, false otherwise
     */
    public static final boolean applyOverlayMode(View targetView, int targetMode) {
        if (targetView != null && targetView.getLayoutParams() != null && targetView.getLayoutParams() instanceof SRGMediaPlayerView.LayoutParams) {
            ((SRGMediaPlayerView.LayoutParams) targetView.getLayoutParams()).overlayMode = targetMode;
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
    }

    public void setAdjustToParentScrollView(boolean adjustToParentScrollView) {
        this.adjustToParentScrollView = adjustToParentScrollView;
    }
}
