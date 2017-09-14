package ch.srg.mediaplayer.segment.adapter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public abstract class BaseSegmentAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    @Nullable
    private SRGMediaPlayerController controller;
    @Nullable
    public SegmentClickListener listener;
    @NonNull
    protected ArrayList<Segment> segments = new ArrayList<>();

    private final ArrayList<SegmentChangeListener> segmentChangeListeners = new ArrayList<>();

    private int currentSegmentIndex;
    private long currentTime;

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

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public String getSegmentIdentifier(int position) {
        return segments.get(position).getIdentifier();
    }

    private int getSegmentIndex(long time) {
        if (controller != null) {
            Segment segment = controller.getSegment(time);
            return segments.indexOf(segment);
        }
        return -1;
    }

    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }

    protected long getCurrentTime() {
        return currentTime;
    }

    public boolean updateProgressSegments(long time) {
        boolean segmentChange = false;
        if (time != currentTime) {
            currentTime = time;
            if (currentSegmentIndex != -1) {
                notifyItemChanged(currentSegmentIndex);
            }
            int newSegmentIndex = segments.indexOf(controller != null ? controller.getSegment(time) : null);
            segmentChange = newSegmentIndex != currentSegmentIndex;
            if (segmentChange) {
                int start = Math.max(0, Math.min(currentSegmentIndex, newSegmentIndex));
                int count = Math.abs(currentSegmentIndex - newSegmentIndex) + 1;
                currentSegmentIndex = newSegmentIndex;
                notifyItemRangeChanged(start, count);
                for (SegmentChangeListener l : segmentChangeListeners) {
                    l.onSegmentHighlighted(currentSegmentIndex >= 0 ? segments.get(currentSegmentIndex) : null);
                }
            }
        }
        return segmentChange;
    }

    public void updateWithMediaPlayerController(@Nullable SRGMediaPlayerController controller) {
        this.controller = controller;
        if (controller != null) {
            filterSegmentList(controller.getSegments());
        } else {
            segments = new ArrayList<>();
        }
    }
}
