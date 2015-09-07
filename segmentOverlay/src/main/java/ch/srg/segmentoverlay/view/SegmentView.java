package ch.srg.segmentoverlay.view;

import android.content.Context;
import android.graphics.Color;
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

    private int themeColor = Color.RED;
    private BaseSegmentAdapter adapter;

    public SegmentView(Context context) {
        this(context, null);
    }

    public SegmentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.segment_view, this, true);

        recyclerView = (RecyclerView) findViewById(R.id.segment_recycler_view);
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(linearLayoutManager);

        // TODO Items change often during seek. Disabling animator fix the flashing, is there a better way to do this?
        recyclerView.setItemAnimator(null);
    }

    public void attachToController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
    }

    public void setBaseAdapter(@NonNull BaseSegmentAdapter adapter) {
        this.adapter = adapter;
        recyclerView.setAdapter(adapter);
    }

    public void setSegmentController(@NonNull SegmentController segmentController) {
        if (this.segmentController != null) {
            this.segmentController.removeListener(this);
        }
        this.segmentController = segmentController;
        segmentController.addListener(this);
    }

    private void update() {
        if (playerController != null) {
            long time = playerController.getMediaPosition();

            if (segmentController != null) {
                segmentController.sendUserTrackedProgress(time);
            }
        }
    }

    @Override
    public void onPositionChange(long time) {
        if (adapter.updateProgressSegments(time)) {
            // TODO Do not do this when using is scrolling the segment view
            recyclerView.scrollToPosition(adapter.getSegmentIndexForTime(time));
        }
    }

    @Override
    public void onSegmentListChanged(List<Segment> segments) {
        setSegmentList(segments);
    }

    public void setSegmentList(@Nullable List<Segment> segments) {
        adapter.setSegmentList(segments);
        if (adapter.getSegmentsList().size() <= 1) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }
}
