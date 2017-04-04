package ch.srg.mediaplayer.segment.adapter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import ch.srg.mediaplayer.segment.controller.SegmentController;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public abstract class BaseSegmentAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    @Nullable
    private SegmentController segmentController;
    @Nullable
    public SegmentClickListener listener;
    @NonNull
    protected ArrayList<Segment> segments = new ArrayList<>();

    private final ArrayList<SegmentChangeListener> segmentChangeListeners = new ArrayList<>();

    private int currentSegmentIndex;
    private long currentTime;
    private String currentMediaIdentifier;

    protected BaseSegmentAdapter() {
        setHasStableIds(true);
    }

    public void setSegmentClickListener(SegmentClickListener clickListener) {
        listener = clickListener;
    }

    public void removeSegmentClickListener(SegmentClickListener clickListener) {
        if (listener != clickListener) {
            listener = null;
        }
    }

    public void addSegmentChangeListener(SegmentChangeListener listener) {
        segmentChangeListeners.add(listener);
    }

    public void removeSegmentChangeListener(SegmentChangeListener listeners) {
        segmentChangeListeners.remove(listeners);
    }

    private void filterSegmentList(@Nullable List<Segment> segmentList) {
        segments.clear();
        if (segmentList != null) {
            for (Segment s : segmentList) {
                if (s.isDisplayable()) {
                    segments.add(s);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return segments.get(position).getIdentifier().hashCode();
    }

    public String getSegmentIdentifier(int position) {
        return segments.get(position).getIdentifier();
    }

    private int getSegmentIndex(@NonNull String mediaIdentifier, long time) {
        int segmentIndex;
        segmentIndex = findLogicalSegment(mediaIdentifier, time);
        if (segmentIndex == -1) {
            segmentIndex = findPhysicalSegment(mediaIdentifier);
        }
        return segmentIndex;
    }

    private int getSegmentIndex(@NonNull String mediaIdentifier, String segmentIdentifier) {
        int segmentIndex;
        segmentIndex = findLogicalSegment(mediaIdentifier, segmentIdentifier);
        if (segmentIndex == -1) {
            segmentIndex = findPhysicalSegment(mediaIdentifier);
        }
        return segmentIndex;
    }

    private int findPhysicalSegment(@NonNull String mediaIdentifier) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if ((mediaIdentifier.equals(segment.getMediaIdentifier())
                    && segment.getMarkIn() == 0 && segment.getMarkOut() == 0)
                    || mediaIdentifier.equals(segment.getIdentifier())) {
                return i;
            }
        }
        return -1;
    }

    private int findLogicalSegment(@NonNull String mediaIdentifier, long time) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (mediaIdentifier.equals(segment.getMediaIdentifier())
                    && time >= segment.getMarkIn() && time <= segment.getMarkOut()) {
                return i;
            }
        }
        return -1;
    }

    private int findLogicalSegment(@NonNull String mediaIdentifier, String segmentIdentifier) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (mediaIdentifier.equals(segment.getMediaIdentifier())
                    && TextUtils.equals(segment.getIdentifier(), segmentIdentifier)) {
                return i;
            }
        }
        return -1;
    }

    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }

    protected String getCurrentMediaIdentifier() {
        return currentMediaIdentifier;
    }

    protected long getCurrentTime() {
        return currentTime;
    }

    public boolean updateProgressSegments(@NonNull String mediaIdentifier, long time) {
        boolean segmentChange = false;
        this.currentMediaIdentifier = mediaIdentifier;
        if (time != currentTime) {
            currentTime = time;
            if (currentSegmentIndex != -1) {
                notifyItemChanged(currentSegmentIndex);
            }
            int newSegmentIndex = segments.indexOf(segmentController != null ? segmentController.getSegment(mediaIdentifier, time) : null);
            segmentChange = newSegmentIndex != currentSegmentIndex;
            if (segmentChange) {
                int start = Math.max(0, Math.min(currentSegmentIndex, newSegmentIndex));
                int count = Math.abs(currentSegmentIndex - newSegmentIndex) + 1;
                currentSegmentIndex = newSegmentIndex;
                notifyItemRangeChanged(start, count);
                if (currentSegmentIndex >= 0) {
                    for (SegmentChangeListener l : segmentChangeListeners) {
                        l.onSegmentHighlighted(segments.get(currentSegmentIndex));
                    }
                }
            }
        }
        return segmentChange;
    }

    public void updateWithSegmentController(@Nullable SegmentController segmentController) {
        this.segmentController = segmentController;
        if (segmentController != null) {
            filterSegmentList(segmentController.getSegments());
        } else {
            segments = new ArrayList<>();
        }
    }
}
