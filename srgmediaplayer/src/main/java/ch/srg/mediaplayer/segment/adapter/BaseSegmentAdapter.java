package ch.srg.mediaplayer.segment.adapter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public abstract class BaseSegmentAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    public SegmentClickListener listener;

    public abstract boolean updateProgressSegments(@NonNull String mediaIdentifier, long time);

    protected ArrayList<Segment> segments;

    protected ArrayList<SegmentChangeListener> segmentChangeListeners = new ArrayList<>();

    protected BaseSegmentAdapter(List<Segment> segmentsList) {
        segments = new ArrayList<>(segmentsList);
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

    public void setSegmentList(@Nullable List<Segment> segmentList) {
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

    public int getDisplayableSegmentsListSize() {
        return segments.size();
    }

    @Override
    public long getItemId(int position) {
        return segments.get(position).getIdentifier().hashCode();
    }

    public String getSegmentIdentifier(int position) {
        return segments.get(position).getIdentifier();
    }

    public int getSegmentIndex(@NonNull String mediaIdentifier, long time) {
        int segmentIndex;
        segmentIndex = findLogicalSegment(mediaIdentifier, time);
        if (segmentIndex == -1) {
            segmentIndex = findPhysicalSegment(mediaIdentifier);
        }
        return segmentIndex;
    }

    public int getSegmentIndex(@NonNull String mediaIdentifier, String segmentIdentifier) {
        int segmentIndex;
        segmentIndex = findLogicalSegment(mediaIdentifier, segmentIdentifier);
        if (segmentIndex == -1) {
            segmentIndex = findPhysicalSegment(mediaIdentifier);
        }
        return segmentIndex;
    }

    public int findPhysicalSegment(@NonNull String mediaIdentifier) {
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

    public int findLogicalSegment(@NonNull String mediaIdentifier, long time) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (mediaIdentifier.equals(segment.getMediaIdentifier())
                    && time >= segment.getMarkIn() && time <= segment.getMarkOut()) {
                return i;
            }
        }
        return -1;
    }

    public int findLogicalSegment(@NonNull String mediaIdentifier, String segmentIdentifier) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (mediaIdentifier.equals(segment.getMediaIdentifier())
                    && TextUtils.equals(segment.getIdentifier(), segmentIdentifier)) {
                return i;
            }
        }
        return -1;
    }

    public ArrayList<Segment> getSegmentsList() {
        return segments;
    }

    public abstract int getCurrentSegment();
}
