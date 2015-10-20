package ch.srg.segmentoverlay.controller;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.segmentoverlay.adapter.SegmentClickListener;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 26.05.15.
 */
public class SegmentController implements SegmentClickListener, SRGMediaPlayerController.Listener,  Runnable {
	private static final long PERIODIC_UPDATE_DELAY = 250;
	private static final long SEGMENT_HYSTERESIS_MS = 500;

	private Context context;

    private SRGMediaPlayerController playerController;

	private ArrayList<Segment> segments = new ArrayList<>();

	private Set<Listener> listeners = new HashSet<>(); // TODO Weak hash set ?
	private boolean userChangingProgress;
	private final Handler handler;
	private Segment segmentBeingSkipped;
	private Segment currentSegment;

	public static class Event extends SRGMediaPlayerController.Event {
		public Segment segment;
		public String blockingReason;
		public Type segmentEventType;

		public enum Type {
			/**
			 *  An identified segment (visible or not) is being started, while not being inside a segment before.
			 */
			SEGMENT_START,
			/**
			 *  An identified segment (visible or not) is being ended, without another one to start.
			 */
			SEGMENT_END,
			/**
			 *  An identified segment (visible or not) is being started, while being inside another segment before.
			 */
			SEGMENT_SWITCH,
			/**
			 * The user has selected a visible segment.
			 */
			SEGMENT_SELECTED,
			/**
			 *  The playback is being seek to a later value, because it reached a blocked segment.
			 */
			SEGMENT_SKIPPED_BLOCKED,
			/**
			 *  The user has tried to seek to a blocked segment, seek has been denied.
			 */
			SEGMENT_USER_SEEK_BLOCKED
		}


		public Event(SRGMediaPlayerController controller, Type type, Segment segment, String blockingReason) {
			super(controller, null);
			this.segmentEventType = type;
			this.blockingReason = blockingReason;
			this.segment = segment;
		}

		public Event(SRGMediaPlayerController controller, Type type, Segment segment) {
			this(controller, type, segment, null);
		}

		@Override
		public String toString() {
			return "Event{" +
					"segment=" + segment +
					", blockingReason='" + blockingReason + '\'' +
					", segmentEventType=" + segmentEventType +
					'}';
		}
	}

	public SegmentController(Context context, @NonNull SRGMediaPlayerController playerController) {
        this.context = context;
		this.playerController = playerController;

		handler = new Handler();
	}

    @Override
    public void onClick(View v, Segment segment, float x, float y) {
        Log.i("SegmentController", "OnClick :" + segment);
	    if (!segment.isBlocked()) {
		    segmentBeingSkipped = null;

			playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_SELECTED, segment));
			String mediaIdentifier = playerController.getMediaIdentifier();
			if (!TextUtils.isEmpty(mediaIdentifier) && mediaIdentifier.equals(segment.getMediaIdentifier())) {
				playerController.seekTo(segment.getMarkIn());
			} else {
				try {
					playerController.play(segment.getMediaIdentifier());
				} catch (SRGMediaPlayerException e) {
					e.printStackTrace();
				}
			}
			currentSegment = segment;
	    } else {
			playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_USER_SEEK_BLOCKED, segment, segment.getBlockingReason()));
		}
    }

	private void notifyPositionChange(String mediaIdentifier, long time) {
		for (Listener l : listeners) {
			l.onPositionChange(mediaIdentifier, time);
		}
	}

	public void sendUserTrackedProgress(long time) {
		// TODO handle if progress time is valid with segment
		userChangingProgress = true;

		notifyPositionChange(playerController.getMediaIdentifier(), time);
	}

	public void stopUserTrackingProgress() {
		userChangingProgress = false;
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void setSegmentList(List<Segment> segments) {
		this.segments.clear();
		if (segments != null) {
			this.segments.addAll(segments);
		}
		for (Listener l : listeners) {
			l.onSegmentListChanged(segments);
		}
	}

	public boolean isUserChangingProgress() {
		return userChangingProgress;
	}

	public boolean seekTo(long mediaPosition) {
		Segment segment = getSegmentForTime(mediaPosition);
		if (segment != null && segment.isBlocked()) {
			seekTo(segment.getMarkOut());
			return false;
		} else {
			playerController.seekTo(mediaPosition);
			return true;
		}
	}

	public interface Listener {
		void onPositionChange(@Nullable String mediaIdentifier, long position);

		void onSegmentListChanged(List<Segment> segments);
	}

	@Override
	public void run() {
		updateIfNotUserTracked();
		handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
	}

	private void updateIfNotUserTracked() {
		if (!isUserChangingProgress()) {
			update();
		}
	}

	private void update() {
		if (playerController == null || playerController.isReleased() || playerController.isSeekPending()) {
			return;
		}
		long mediaPosition = playerController.getMediaPosition();
		Segment newSegment = getSegmentForTime(mediaPosition);

		if (currentSegment != newSegment) {
			if (currentSegment == null) {
				playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_START, newSegment));
			} else if (newSegment == null) {
				playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_END, newSegment));
			} else {
				playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_SWITCH, newSegment));
			}
			if (newSegment != null && newSegment.isBlocked()) {
				if (newSegment != segmentBeingSkipped) {
					Log.v("SegmentTest", "Skipping over " + newSegment.getIdentifier());
					segmentBeingSkipped = newSegment;
					playerController.postEvent(new Event(playerController, Event.Type.SEGMENT_SKIPPED_BLOCKED, newSegment, newSegment.getBlockingReason()));
					seekTo(newSegment.getMarkOut());
				}
			} else {
				segmentBeingSkipped = null;
			}
			currentSegment = newSegment;
		}
		notifyPositionChange(playerController.getMediaIdentifier(), mediaPosition);
	}

	@Nullable
	public Segment getSegmentForTime(long time) {
		for (Segment segment : segments) {
			if (time >= segment.getMarkIn() - SEGMENT_HYSTERESIS_MS
					&& time < segment.getMarkOut()
					&& segment == currentSegment) {
				return segment;
			}
			if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
				return segment;
			}
		}
		return null;
	}

	@Override
	public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
		if (mp == playerController) {
			if (event.type == SRGMediaPlayerController.Event.Type.STATE_CHANGE
					&& event.state == SRGMediaPlayerController.State.RELEASED) {
				// Update one last time UI to reflect released state
				notifyPositionChange(mp.getMediaIdentifier(), 0);
			} else {
				updateIfNotUserTracked();
			}
		} else {
			throw new IllegalArgumentException("Unexpected player");
		}
	}

	public void startListening() {
		playerController.registerEventListener(this);
		handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
	}

	public void stopListening() {
		playerController.unregisterEventListener(this);
		handler.removeCallbacks(this);
	}
}
