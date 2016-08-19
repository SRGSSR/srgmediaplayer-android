package ch.srg.segmentoverlay.data;

import java.util.List;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public interface SegmentDataProvider {
    void getSegmentList(String mediaIdentifier, GetSegmentListCallback callback);


    interface GetSegmentListCallback {
        void onSegmentListLoaded(List<Segment> srgMediaMetadata);

        void onDataNotAvailable();
    }

}
