package ch.srg.mediaplayer.segment.adapter;

import android.support.annotation.Nullable;

import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface SegmentChangeListener {
    void onSegmentHighlighted(@Nullable Segment segment);
}
