package ch.srg.segmentoverlay.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.segmentoverlay.R;
import ch.srg.segmentoverlay.adapter.BaseSegmentAdapter;
import ch.srg.segmentoverlay.controller.SegmentController;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 20.05.15.
 */
public class SegmentView extends FrameLayout implements SegmentController.Listener {
    private SRGMediaPlayerController playerController;

    private RecyclerView recyclerView;
    private LinearLayoutManager linearLayoutManager;
    private SegmentController segmentController;

    @Nullable
    private BaseSegmentAdapter adapter;

    private Integer textColor;
    private Integer selectedTextColor;
    private Integer selectedBackground;

    public SegmentView(Context context) {
        this(context, null);
    }

    public SegmentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SegmentView, 0, 0);

        textColor = a.getColor(R.styleable.SegmentView_text_color, 0);
        selectedTextColor = a.getColor(R.styleable.SegmentView_text_color, 0);
        selectedBackground = a.getColor(R.styleable.SegmentView_text_color, 0);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.segment_view, this, true);

        recyclerView = (RecyclerView) findViewById(R.id.segment_recycler_view);
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(linearLayoutManager);

        // TODO Items change often during seek. Disabling animator fix the flashing, is there a better way to do this?
        recyclerView.setItemAnimator(null);
        a.recycle();
    }

    public void attachToController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
    }

    public void setBaseAdapter(@NonNull BaseSegmentAdapter adapter) {
        this.adapter = adapter;
        recyclerView.setAdapter(adapter);
        adapter.setSelectedTextColor(selectedTextColor);
        adapter.setSelectedBackgroundColor(selectedBackground);
        adapter.setTextColor(textColor);
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        if (adapter != null) {
            adapter.setTextColor(textColor);
        }
    }

    public void setSelectedTextColor(int selectedTextColor) {
        this.selectedTextColor = selectedTextColor;
        if (adapter != null) {
            adapter.setSelectedTextColor(selectedTextColor);
        }
    }

    public void setSelectedBackground(int selectedBackground) {
        this.selectedBackground = selectedBackground;
        if (adapter != null) {
            adapter.setSelectedBackgroundColor(selectedBackground);
        }
    }

    public void setSegmentController(@NonNull SegmentController segmentController) {
        if (this.segmentController != null) {
            this.segmentController.removeListener(this);
        }
        this.segmentController = segmentController;
        segmentController.addListener(this);
    }

    @Override
    public void onPositionChange(@Nullable String mediaIdentifier, long time, boolean seeking) {
        Segment currentSegment = segmentController.getCurrentSegment();
        String currentSegmentIdentifier = (!seeking && currentSegment != null) ? currentSegment.getIdentifier() : null;
        boolean change = mediaIdentifier != null && adapter.updateProgressSegments(mediaIdentifier, time, currentSegmentIdentifier);
        if (change) {
            // TODO Do not do this when using is scrolling the segment view
            recyclerView.scrollToPosition(adapter.getCurrentSegment());
        }
    }

    @Override
    public void onSegmentListChanged(List<Segment> segments) {
        setSegmentList(segments);
    }

    private void setSegmentList(@Nullable List<Segment> segments) {
        adapter.setSegmentList(segments);
        if (adapter.getSegmentsList().size() <= 1) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }
}
