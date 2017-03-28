package ch.srg.mediaplayer.demo;

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

import java.util.Arrays;
import java.util.List;

import ch.srg.mediaplayer.segment.adapter.BaseSegmentAdapter;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Created by npietri on 21.05.15.
 */
public class DemoSegmentAdapter extends BaseSegmentAdapter<DemoSegmentAdapter.ViewHolder> {

    private static final List<String> URL_PREFIX_WITHOUT_SCALING_SUPPORT =
            Arrays.asList("http://www.srfcdn.ch/", "http://buygapinsurance.co.uk");

    private int currentSegment;
    private long currentTime;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_demo_segment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Segment segment = segments.get(position);

        // Try Glide implementation
        String finalUrl = segment.getImageUrl();
        if (isScalingSupported(finalUrl)) {
            finalUrl += "/scale/width/" + holder.thumbnail1.getMeasuredWidth();
        }
        Glide.with(holder.itemView.getContext()).load(finalUrl).centerCrop().crossFade().into(holder.thumbnail1);

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
        if (currentSegment == position) {
            holder.itemView.setBackgroundResource(R.color.red);
        } else {
            holder.itemView.setBackgroundResource(R.color.grey);
        }
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnTouchListener {
        private Segment segment;
        private ImageView thumbnail1;
        private ProgressBar progressBar;
        private TextView duration;
        private TextView title;
        private View darkOverlay;

        public ViewHolder(View itemView) {
            super(itemView);
            thumbnail1 = (ImageView) itemView.findViewById(R.id.item_segment_thumbnail1);
            progressBar = (ProgressBar) itemView.findViewById(R.id.item_segment_progress);
            duration = (TextView) itemView.findViewById(R.id.item_segment_duration);
            title = (TextView) itemView.findViewById(R.id.item_segment_title);
            darkOverlay = itemView.findViewById(R.id.item_segment_darkener);
            itemView.setOnTouchListener(this);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.i("DefaultSegmentAdapter", "OnClick :" + getAdapterPosition());
            if (event.getAction() == MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_MOVE) {
                listener.onSegmentClick(v, segment);
            }
            return true;
        }
    }

    private boolean isScalingSupported(String url) {
        for (String prefix : URL_PREFIX_WITHOUT_SCALING_SUPPORT) {
            if (url.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getCurrentSegment() {
        return currentSegment;
    }
}
