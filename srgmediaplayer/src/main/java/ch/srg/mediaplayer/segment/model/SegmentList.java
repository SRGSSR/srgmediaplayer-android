package ch.srg.mediaplayer.segment.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SegmentList implements Iterable<Segment> {
    private static final long SEGMENT_HYSTERESIS_MS = 5000;

    private List<Segment> segments;
    @Nullable
    private Segment currentSegment = null;
    private int currentSegmentIndex = -1;
    private Set<Listener> listeners = new HashSet<>();


    public interface Listener {
        /**
         * @param segment null when switching to a non current segment
         */
        void onCurrentSegmentChanged(@Nullable Segment segment);
    }

    private SegmentList(List<Segment> list) {
        segments = list;
    }

    public SegmentList() {
        this(new ArrayList<Segment>());
    }

    public boolean addListener(Listener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(Listener o) {
        return listeners.remove(o);
    }

    @Nullable
    public Segment getCurrentSegment() {
        return currentSegment;
    }

    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }

    public void setCurrentSegment(@Nullable Segment segment) {
        Log.d("SegmentList", "CurrentSegment = " + segment);
        if (segment != currentSegment) {
            this.currentSegment = segment;
            this.currentSegmentIndex = currentSegment != null ? indexOf(segment) : -1;
            for (Listener listener : listeners) {
                listener.onCurrentSegmentChanged(segment);
            }
        }
    }

    @Nullable
    public Segment peekNextSegment() {
        int count = size();
        if (currentSegmentIndex + 1 < count) {
            int index = currentSegmentIndex + 1;
            return segments.get(index);
        } else {
            return null;
        }
    }

    @Nullable
    public Segment peekPreviousSegment() {
        if (currentSegmentIndex - 1 >= 0) {
            int index = currentSegmentIndex - 1;
            return segments.get(index);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public Iterator<Segment> iterator() {
        return segments.iterator();
    }

    public int size() {
        return segments.size();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public boolean contains(Segment o) {
        return segments.contains(o);
    }

    public void clear() {
        segments.clear();
    }

    public @Nullable
    Segment getSegment(long time) {
        if (currentSegment != null && segments.contains(currentSegment)
                && time >= currentSegment.getMarkIn() - SEGMENT_HYSTERESIS_MS
                && time < currentSegment.getMarkOut()) {
            return currentSegment;
        }
        for (Segment segment : segments) {
            if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
                return segment;
            }
        }
        return null;
    }

    @Nullable
    private Segment getBlockedSegment(long time) {
        for (Segment segment : segments) {
            if (!TextUtils.isEmpty(segment.getBlockingReason())) {
                if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
                    return segment;
                }
            }
        }
        return null;
    }

    /**
     * @param segmentList
     * @return true if something changed
     */
    public boolean setSegmentList(List<Segment> segmentList) {
        if (!segments.equals(segmentList)) {
            segments.clear();
            this.currentSegmentIndex = -1;
            this.currentSegment = null;
            if (segmentList != null) {
                segments.addAll(segmentList);
            }
            return true;
        } else {
            return false;
        }
    }

    public Segment get(int index) {
        return segments.get(index);
    }

    public int indexOf(Segment o) {
        return segments.indexOf(o);
    }

    public int lastIndexOf(Segment o) {
        return segments.lastIndexOf(o);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public List<Segment> getSegments() {
        return segments;
    }
}
