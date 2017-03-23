package ch.srg.mediaplayer.segment.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.List;

import ch.srg.mediaplayer.ControlTouchListener;
import ch.srg.mediaplayer.R;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.segment.adapter.BaseSegmentAdapter;
import ch.srg.mediaplayer.segment.controller.SegmentController;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Created by npietri on 20.05.15.
 */
public class SegmentView extends RecyclerView implements SegmentController.Listener {
    private LinearLayoutManager linearLayoutManager;
    private SegmentController segmentController;

    @Nullable
    private ControlTouchListener controlTouchListener;

    @Nullable
    private BaseSegmentAdapter adapter;

    public SegmentView(Context context) {
        this(context, null);
    }

    public SegmentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SegmentView, 0, 0);

        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        setLayoutManager(linearLayoutManager);

        // TODO Items change often during seek. Disabling animator fix the flashing, is there a better way to do this?
        setItemAnimator(null);
        a.recycle();
    }

    public void setBaseAdapter(@NonNull BaseSegmentAdapter adapter) {
        this.adapter = adapter;
        setAdapter(adapter);
        adapter.updateWithSegmentController(segmentController);
    }

    public void setSegmentController(@NonNull SegmentController segmentController) {
        if (this.segmentController != null) {
            this.segmentController.removeListener(this);
        }
        this.segmentController = segmentController;
        segmentController.addListener(this);
        if (adapter != null) {
            adapter.updateWithSegmentController(segmentController);
        }
    }

    @Override
    public void onPositionChange(@Nullable String mediaIdentifier, long time, boolean seeking) {
        boolean change =
                mediaIdentifier != null
                && adapter != null
                && adapter.updateProgressSegments(mediaIdentifier, time);
        if (change) {
            scrollToPosition(adapter.getCurrentSegment());
        }
    }

    @Override
    public void onSegmentListChanged(List<Segment> segments) {
        if (adapter != null) {
            adapter.updateWithSegmentController(segmentController);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = super.onTouchEvent(e);
        if (handled && controlTouchListener != null) {
            controlTouchListener.onMediaControlTouched();
        }
        return handled;
    }

    public void setControlTouchListener(ControlTouchListener controlTouchListener) {
        this.controlTouchListener = controlTouchListener;
    }
}
