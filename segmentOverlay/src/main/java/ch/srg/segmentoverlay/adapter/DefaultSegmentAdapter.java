package ch.srg.segmentoverlay.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

import ch.srg.segmentoverlay.R;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public class DefaultSegmentAdapter extends BaseSegmentAdapter<DefaultSegmentAdapter.ViewHolder> {
    private int currentSegment;
    private long currentTime;

    public DefaultSegmentAdapter(Context context, List<Segment> segmentsList) {
        super(context, segmentsList);
    }

    @Override
    public boolean updateProgressSegments(String mediaIdentifier, long time) {
        boolean segmentChange = false;
        if (time != currentTime) {
            currentTime = time;
            if (currentSegment != -1) {
                notifyItemChanged(currentSegment);
            }
            int newSegment = getSegmentIndex(mediaIdentifier, time);
            segmentChange = newSegment != currentSegment;
            if (segmentChange) {
                int start = Math.max(0, Math.min(currentSegment, newSegment));
                int count = Math.abs(currentSegment - newSegment) + 1;
                notifyItemRangeChanged(start, count);
                currentSegment = newSegment;
            }
        }
        return segmentChange;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_segment, parent, false);
        return new ViewHolder(v, clickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Segment segment = segments.get(position);
        Glide.with(context)
                .load(segment.getImageUrl())
                .crossFade()
                .into(holder.thumbnail);
        holder.title.setText(segment.getTitle());
        holder.title.append("\n");
        holder.title.append(segment.getDescription());
        holder.duration.setText("" + segment.getDuration());
        long timeInSegment = currentTime - segment.getMarkIn();
        int duration = (int) segment.getDuration();
        holder.progressBar.setMax(duration);
        holder.progressBar.setProgress(Math.min(duration, Math.max(0, (int) timeInSegment)));
        holder.segment = segment;
        holder.darkOverlay.setVisibility(segment.isBlocked() ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnTouchListener {
        private Segment segment;
        private final SegmentClickListener clickListener;
        private ImageView thumbnail;
        private ProgressBar progressBar;
        private TextView duration;
        private TextView title;
        private View darkOverlay;

        public ViewHolder(View itemView, SegmentClickListener clickListener) {
            super(itemView);
            thumbnail = (ImageView) itemView.findViewById(R.id.item_segment_thumbnail);
            progressBar = (ProgressBar) itemView.findViewById(R.id.item_segment_progress);
            duration = (TextView) itemView.findViewById(R.id.item_segment_duration);
            title = (TextView) itemView.findViewById(R.id.item_segment_title);
            darkOverlay = itemView.findViewById(R.id.item_segment_darkener);
            itemView.setOnTouchListener(this);
            this.clickListener = clickListener;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.i("DefaultSegmentAdapter", "OnClick :" + getAdapterPosition());
            if (event.getAction() == MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_MOVE) {
                clickListener.onClick(v, segment, event.getX(), event.getY());
            }
            return true;
        }
    }

    @Override
    public void setSegmentList(List<Segment> segmentList) {
        super.setSegmentList(segmentList);
        currentTime = -1;
        currentSegment = -1;
    }
}
