package ch.srg.segmentoverlay.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public abstract class BaseSegmentAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    public HashSet<SegmentClickListener> listeners = new HashSet<>();

	public abstract boolean updateProgressSegments(@NonNull String mediaIdentifier, long time);

	protected Context context;

	protected ArrayList<Segment> segments;

	protected ArrayList<SegmentChangeListeners> segmentChangeListeners = new ArrayList<>();

	protected BaseSegmentAdapter(Context context, List<Segment> segmentsList) {
		this.context = context;
		segments = new ArrayList<>(segmentsList);
		setHasStableIds(true);
	}

	public void addSegmentClickListener(SegmentClickListener clickListener) {
		listeners.add(clickListener);
	}

	public void removeSegmentClickListener(SegmentClickListener clickListener) {
		listeners.remove(clickListener);
	}

	public void addSegmentChangeListener(SegmentChangeListeners listener) {
		segmentChangeListeners.add(listener);
	}

	public void removeSegmentChangeListener(SegmentChangeListeners listeners) {
		segmentChangeListeners.remove(listeners);
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

	public int getDisplayableSegmentsListSize(){
		return segments.size();
	}

	@Override
	public long getItemId(int position) {
		return segments.get(position).getIdentifier().hashCode();
	}

	public String getSegmentIdentifier(int position) {
		return segments.get(position).getIdentifier();
	}

	public int getSegmentIndex(@NonNull String mediaIdentifier, long time) {
		int segmentIndex;
		segmentIndex = findLogicalSegment(mediaIdentifier, time);
		if (segmentIndex == -1) {
			segmentIndex = findPhysicalSegment(mediaIdentifier);
		}
		return segmentIndex;
	}

	public int findPhysicalSegment(@NonNull String mediaIdentifier) {
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			if ((mediaIdentifier.equals(segment.getMediaIdentifier())
					&& segment.getMarkIn() == 0 && segment.getMarkOut() == 0)
					|| mediaIdentifier.equals(segment.getIdentifier())) {
				return i;
			}
		}
		return -1;
	}

	public int findLogicalSegment(@NonNull String mediaIdentifier, long time) {
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			if (mediaIdentifier.equals(segment.getMediaIdentifier())
					&& time >= segment.getMarkIn() && time <= segment.getMarkOut()) {
				return i;
			}
		}
		return -1;
	}

	public ArrayList<Segment> getSegmentsList() {
		return segments;
	}
}
