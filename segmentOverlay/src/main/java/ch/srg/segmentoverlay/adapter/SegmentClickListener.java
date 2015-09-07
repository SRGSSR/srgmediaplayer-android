package ch.srg.segmentoverlay.adapter;

import android.view.View;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public interface SegmentClickListener {

    /**
     * Callback method to be invoked when a item in a
     * RecyclerView is clicked
     * @param v The view within the RecyclerView.Adapter
     * @param position The position of the view in the adapter
     * @param x position of touch in item
     * @param y position of touch in item
     */
    void onClick(View v, Segment position, float x, float y);

}
