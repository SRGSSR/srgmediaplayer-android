package ch.srg.mediaplayer.segment.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class SegmentList extends ArrayList<Segment> {

    public SegmentList(int initialCapacity) {
        super(initialCapacity);
    }

    public SegmentList() {
    }

    public SegmentList(@NonNull Collection<? extends Segment> c) {
        super(c);
    }

    /**
     * Get the first Block segment at position
     */
    @Nullable
    public Segment findBlockedSegmentAtPosition(long position) {
        for (Segment segment : this) {
            if (segment.isBlocked() && segment.getMarkRange().isInRange(position)) {
                return segment;
            }
        }
        return null;
    }

    /**
     * Get the first segment at position
     *
     * @param position
     * @return
     */
    @Nullable
    public Segment findSegmentAtPosition(@NonNull long position) {
        for (Segment segment : this) {
            if (segment.getMarkRange().isInRange(position)) {
                return segment;
            }
        }
        return null;
    }

    @Nullable
    public Segment findSegmentById(@NonNull String id) {
        for (Segment segment : this) {
            if (TextUtils.equals(id, segment.getIdentifier())) {
                return segment;
            }
        }
        return null;
    }


}
