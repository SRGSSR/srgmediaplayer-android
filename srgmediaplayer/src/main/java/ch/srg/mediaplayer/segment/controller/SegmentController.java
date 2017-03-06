package ch.srg.mediaplayer.segment.controller;

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
import java.util.WeakHashMap;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.segment.adapter.SegmentClickListener;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Created by npietri on 26.05.15.
 */
public class SegmentController implements SegmentClickListener, SRGMediaPlayerController.Listener,  Runnable {
	private static final long PERIODIC_UPDATE_DELAY = 250;
	private static final long SEGMENT_HYSTERESIS_MS = 5000;
	private static final String TAG = "SegmentController";

	private SRGMediaPlayerController playerController;

	private ArrayList<Segment> segments = new ArrayList<>();

	private Set<Listener> listeners = new HashSet<>(); // TODO Weak hash set ?

	private boolean userChangingProgress;
	@Nullable
	private Handler handler;
	private Segment segmentBeingSkipped;
	private Segment currentSegment;

	private SegmentClickDelegate segmentClickDelegate = new SegmentClickDelegate() {
		@Override
		public void onSegmentClick(Segment segment) {
			String mediaIdentifier = playerController.getMediaIdentifier();
			currentSegment = segment;
			if (!TextUtils.isEmpty(mediaIdentifier) && mediaIdentifier.equals(segment.getMediaIdentifier())) {
				postEvent(Event.Type.SEGMENT_SELECTED, segment);
				playerController.seekTo(segment.getMarkIn());
				playerController.start();
			} else {
				try {
					playerController.play(segment.getMediaIdentifier(), segment.getMarkIn());
				} catch (SRGMediaPlayerException e) {
					e.printStackTrace();
				}
			}
		}
	};
	private static WeakHashMap<SRGMediaPlayerController, SegmentController> controllers = new WeakHashMap<>();

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

	public static SegmentController attachOrCreate(@NonNull SRGMediaPlayerController playerController) {
		// TODO Temporary measure until we integrate SegmentController in SRGMediaPlayerController
		SegmentController segmentController = controllers.get(playerController);
		if (segmentController == null) {
			segmentController = new SegmentController(playerController);
			controllers.put(playerController, segmentController);
		}
		return segmentController;
	}

	private SegmentController(@NonNull SRGMediaPlayerController playerController) {
		this.playerController = playerController;
	}

    @Override
    public void onSegmentClick(View v, Segment segment) {
        Log.i("SegmentController", "OnClick :" + segment);
	    if (!segment.isBlocked()) {
		    segmentBeingSkipped = null;

			segmentClickDelegate.onSegmentClick(segment);
	    } else {
			postBlockedSegmentEvent(segment, Event.Type.SEGMENT_USER_SEEK_BLOCKED);
		}
    }

	@Override
	public void onLongClick(View v, Segment segment, int position) {

	}

	public void postEvent(Event.Type type, Segment segment) {
		playerController.broadcastEvent(new Event(playerController, type, segment));
	}

	public void postBlockedSegmentEvent(Segment segment, Event.Type type) {
		playerController.broadcastEvent(new Event(playerController, type, segment, segment.getBlockingReason()));
	}

	private void notifyPositionChange(String mediaIdentifier, long time, boolean seeking) {
		for (Listener l : listeners) {
			l.onPositionChange(mediaIdentifier, time, seeking);
		}
	}

	public void sendUserTrackedProgress(long time) {
		// TODO handle if progress time is valid with segment
		userChangingProgress = true;

		notifyPositionChange(playerController.getMediaIdentifier(), time, true);
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
		if (!this.segments.equals(segments)) {
			this.segments.clear();
			if (segments != null) {
				this.segments.addAll(segments);
			}
			for (Listener l : listeners) {
				l.onSegmentListChanged(segments);
			}
		}
	}

	public boolean isUserChangingProgress() {
		return userChangingProgress;
	}

	public boolean seekTo(String mediaIdentifier, long mediaPosition) {
		Segment segment = getSegment(mediaIdentifier, mediaPosition);
		if (segment != null && segment.isBlocked()) {
			postBlockedSegmentEvent(segment, Event.Type.SEGMENT_SKIPPED_BLOCKED);
			seekTo(mediaIdentifier, segment.getMarkOut());
			return false;
		} else {
			playerController.seekTo(mediaPosition);
			playerController.start();
			return true;
		}
	}

	public interface Listener {
		void onPositionChange(@Nullable String mediaIdentifier, long position, boolean seeking);

		void onSegmentListChanged(List<Segment> segments);
	}

	public interface SegmentClickDelegate {
		void onSegmentClick(Segment segment);
	}

	@Override
	public void run() {
		updateIfNotUserTracked();
		if (handler != null) {
			handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
		}
	}

	private void updateIfNotUserTracked() {
		if (!isUserChangingProgress()) {
			update();
		}
	}

	private void update() {
		if (playerController == null || playerController.isReleased()) {
			return;
		}
		long mediaPosition = playerController.getMediaPosition();
		if (!playerController.isSeekPending()) {
			String mediaIdentifier = playerController.getMediaIdentifier();
			Segment newSegment = getSegment(mediaIdentifier, mediaPosition);

			if (currentSegment != newSegment) {
				if (currentSegment == null) {
					postEvent(Event.Type.SEGMENT_START, newSegment);
				} else if (newSegment == null) {
					postEvent(Event.Type.SEGMENT_END, null);
				} else {
					postEvent(Event.Type.SEGMENT_SWITCH, newSegment);
				}
				if (newSegment != null && newSegment.isBlocked()) {
					if (newSegment != segmentBeingSkipped) {
						Log.v("SegmentTest", "Skipping over " + newSegment.getIdentifier());
						segmentBeingSkipped = newSegment;
						postBlockedSegmentEvent(newSegment, Event.Type.SEGMENT_SKIPPED_BLOCKED);
						seekTo(mediaIdentifier, newSegment.getMarkOut());
					}
				} else {
					segmentBeingSkipped = null;
				}
				currentSegment = newSegment;
			}
		}
		notifyPositionChange(playerController.getMediaIdentifier(), mediaPosition, false);
	}

	@Nullable
	public Segment getSegment(String mediaIdentifier, long time) {
		if (currentSegment != null && segments.contains(currentSegment)
				&& time >= currentSegment.getMarkIn() - SEGMENT_HYSTERESIS_MS
				&& time < currentSegment.getMarkOut()) {
			return currentSegment;
		}
		for (Segment segment : segments) {
			if (TextUtils.equals(segment.getMediaIdentifier(), mediaIdentifier)
					&& !segment.isFullLength()) {
				if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
					return segment;
				}
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
				notifyPositionChange(mp.getMediaIdentifier(), 0, false);
			} else {
				updateIfNotUserTracked();
			}
		} else {
			Log.e(TAG, "Unexpected player");
		}
	}

	public void startListening() {
		if (handler == null) {
			playerController.registerEventListener(this);
			handler = new Handler();
			handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
		}
	}

	public void stopListening() {
		if (handler != null) {
			playerController.unregisterEventListener(this);
			handler.removeCallbacksAndMessages(null);
			handler = null;
		}
	}

	public Segment getCurrentSegment() {
		return currentSegment;
	}

	public void setSegmentClickDelegate(SegmentClickDelegate segmentClickDelegate) {
		this.segmentClickDelegate = segmentClickDelegate;
	}
}
