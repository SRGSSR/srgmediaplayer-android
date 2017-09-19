package ch.srg.mediaplayer.segment.adapter;

import android.view.View;

import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public interface SegmentClickListener {

    void onSegmentClick(View v, Segment segment);

    void onSegmentLongClick(View v, Segment segment, int position);

}
