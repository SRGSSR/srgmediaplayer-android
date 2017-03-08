package ch.srg.mediaplayer.segment.adapter;

import android.view.View;

import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public interface SegmentClickListener {

    void onSegmentClick(View v, Segment segment);

    void onSegmentLongClick(View v, Segment segment, int position);

}
