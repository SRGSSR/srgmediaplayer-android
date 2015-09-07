package ch.srg.segmentoverlay.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public abstract class BaseSegmentAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    public SegmentClickListener clickListener;

	public abstract boolean updateProgressSegments(long time);

	protected Context context;

	protected ArrayList<Segment> segments;

	protected ArrayList<SegmentChangeListeners> segmentChangeListeners = new ArrayList<>();

	protected BaseSegmentAdapter(Context context, List<Segment> segmentsList) {
		this.context = context;
		segments = new ArrayList<>(segmentsList);
		setHasStableIds(true);
	}

	public void setSegmentListListener(SegmentClickListener clickListener){
        this.clickListener = clickListener;
    }

	public void addSegmentChangeListener(SegmentChangeListeners listener){
		segmentChangeListeners.add(listener);
	}

	public void setSegmentList(@Nullable List<Segment> segmentList) {
		segments.clear();
		if (segmentList != null) {
			for (Segment s : segmentList) {
				if (s.isDisplayable()) {
					segments.add(s);
				}
			}
		}
		notifyDataSetChanged();
	}

	@Override
	public long getItemId(int position) {
		return segments.get(position).getIdentifier().hashCode();
	}

	public String getSegmentIdentifier(int position) {
		return segments.get(position).getIdentifier();
	}

	public int getSegmentIndexForTime(long time) {
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			if (time >= segment.getMarkIn() && time <= segment.getMarkOut()) {
				return i;
			}
		}
		return -1;
	}

	public ArrayList<Segment> getSegmentsList() {
		return segments;
	}
}
