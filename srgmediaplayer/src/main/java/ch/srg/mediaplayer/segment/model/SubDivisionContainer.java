package ch.srg.mediaplayer.segment.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class SubDivisionContainer {
    private final List<Segment> segments = new ArrayList<>();
    private final List<Segment> blockedSegments = new ArrayList<>();
    private final List<Segment> displayableSegments = new ArrayList<>();

    public SubDivisionContainer() {
        // Nothing
    }

    public SubDivisionContainer(@Nullable List<Segment> list) {
        setSegments(list);
    }

    public SubDivisionContainer(@NonNull SubDivisionContainer source) {
        this();
        this.segments.addAll(source.segments);
        this.blockedSegments.addAll(source.blockedSegments);
        this.displayableSegments.addAll(source.displayableSegments);
    }

    public void clear() {
        segments.clear();
        blockedSegments.clear();
        displayableSegments.clear();
    }

    public boolean setSegments(@Nullable List<Segment> list) {
        if (list == null || list.isEmpty()) {
            clear();
            return true;
        }
        if (!segments.equals(list)) {
            clear();
            segments.addAll(list);
            for (Segment segment : list) {
                if (segment.isBlocked()) {
                    blockedSegments.add(segment);
                }
                if (segment.isDisplayable()) {
                    displayableSegments.add(segment);
                }
            }
            return true;
        }
        return false;
    }

    @Nullable
    public static Segment findSegmentById(@NonNull String id, List<Segment> list) {
        for (Segment segment : list) {
            if (TextUtils.equals(id, segment.getIdentifier())) {
                return segment;
            }
        }
        return null;
    }

    @Nullable
    public static Segment findSegmentAtPosition(@NonNull long position, List<Segment> list) {
        for (Segment segment : list) {
            if (position >= segment.getMarkIn() && position < segment.getMarkOut()) {
                return segment;
            }
        }
        return null;
    }

    @Nullable
    public Segment findSegmentById(@NonNull String id) {
        return findSegmentById(id, segments);
    }

    @Nullable
    public Segment findDisplayableSegmentById(@NonNull String id) {
        return findSegmentById(id, displayableSegments);
    }

    @Nullable
    public Segment findBlockedSegmentById(@NonNull String id) {
        return findSegmentById(id, blockedSegments);
    }

    @Nullable
    public Segment findSegmentAtPosition(long position) {
        return findSegmentAtPosition(position, segments);
    }

    @Nullable
    public Segment findDisplayedSegmentAtPosition(long position) {
        return findSegmentAtPosition(position, displayableSegments);
    }

    @Nullable
    public Segment findBlockedSegmentAtPosition(long position) {
        return findSegmentAtPosition(position, blockedSegments);
    }


    @NonNull
    public List<Segment> getSegments() {
        return segments;
    }

    @NonNull
    public List<Segment> getDisplayableSegments() {
        return displayableSegments;
    }

    @NonNull
    public List<Segment> getBlockedSegments() {
        return blockedSegments;
    }

    @NonNull
    public Segment getSegment(int index) {
        return segments.get(index);
    }

    @NonNull
    public Segment getDisplayedSegment(int index) {
        return displayableSegments.get(index);
    }

    @NonNull
    public Segment getBlockedSegment(int index) {
        return blockedSegments.get(index);
    }

    public int size() {
        return segments.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubDivisionContainer segments1 = (SubDivisionContainer) o;

        return segments.equals(segments1.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    public boolean contains(@Nullable Segment o) {
        return segments.contains(o);
    }

    public boolean containsAll(@NonNull Collection<Segment> c) {
        return segments.containsAll(c);
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
