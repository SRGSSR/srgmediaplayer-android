package ch.srg.mediaplayer.segment.data;

import java.util.List;

import ch.srg.mediaplayer.segment.model.Segment;
import ch.srg.srgmediaplayer.utils.Cancellable;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface SegmentDataProvider {
    Cancellable getSegmentList(String mediaIdentifier, GetSegmentListCallback callback);


    interface GetSegmentListCallback {
        void onSegmentListLoaded(List<Segment> srgMediaMetadata);

        void onDataNotAvailable();
    }

}
