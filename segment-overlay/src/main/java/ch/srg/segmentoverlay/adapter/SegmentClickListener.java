package ch.srg.segmentoverlay.adapter;

import android.view.View;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public interface SegmentClickListener {

    void onSegmentClick(View v, Segment segment);

    void onLongClick(View v, Segment segment, int position);

}
