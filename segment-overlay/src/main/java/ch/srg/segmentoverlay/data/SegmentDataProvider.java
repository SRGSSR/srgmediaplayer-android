package ch.srg.segmentoverlay.data;

import java.util.List;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public interface SegmentDataProvider {
    List<Segment> getSegments(String mediaIdentifier);
}
