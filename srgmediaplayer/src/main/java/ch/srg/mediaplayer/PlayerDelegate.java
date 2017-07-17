package ch.srg.mediaplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.android.exoplayer2.text.Cue;

import java.util.List;

/**
 * Created by Axel on 04/03/2015.
 *
 * This is the generic interface to implement if you need to create a custom delegate for playing
 * audio or video content with the player.
 *
 */
public interface PlayerDelegate {
	long UNKNOWN_TIME = -1;

	@Nullable
	SubtitleTrack getSubtitleTrack();

	boolean hasVideoTrack();

	interface OnPlayerDelegateListener {

		void onPlayerDelegatePreparing(PlayerDelegate delegate);

		void onPlayerDelegateReady(PlayerDelegate delegate);

		void onPlayerDelegatePlayWhenReadyCommited(PlayerDelegate delegate);

		void onPlayerDelegateBuffering(PlayerDelegate delegate);

		void onPlayerDelegateCompleted(PlayerDelegate delegate);

		void onPlayerDelegateError(PlayerDelegate delegate, SRGMediaPlayerException e);

		void onPlayerDelegateSubtitleCues(PlayerDelegate delegate, List<Cue> cues);

		void onPlayerDelegateVideoSizeChanged(PlayerDelegate delegate, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);

		SRGMediaPlayerView getMediaPlayerView();

		Handler getMainHandler();

		Context getContext();
    }

	/**
	 * Check if the view passed to the player is capable to render video.
	 *
	 * @param view
	 * @return
	 */
	boolean canRenderInView(View view);

	/**
	 * Create a rendering view for the player
	 *
	 * @param parentContext
	 * @return
	 */
	View createRenderingView(Context parentContext);

	/**
	 * Connect delegate to compatible rendering view, or throw exception
	 *
	 * @param mediaPlayerView
	 * @throws SRGMediaPlayerException
	 */
	void bindRenderingViewInUiThread(SRGMediaPlayerView mediaPlayerView) throws SRGMediaPlayerException;

	void unbindRenderingView();

	/**
	 * Setup the current uri in delegate and prepare the delegate for playing video. You can choose
	 * to throw an exception if the videoUri is null or if the developer try to play the same video.
	 *
	 * @param videoUri
	 * @param streamType
	 * @throws SRGMediaPlayerException
	 */
	void prepare(Uri videoUri, int streamType) throws SRGMediaPlayerException;

	/**
	 * Delegate don't need to keep the playing intention state
	 * controller will handle it and send correctly playing message.
	 * This method can be called at any state.
	 *
	 * @param playIfReady
	 * @throws IllegalStateException
	 */
	void playIfReady(boolean playIfReady) throws IllegalStateException;

	void seekTo(long positionInMillis) throws IllegalStateException;

	boolean isPlaying();

	void setMuted(boolean muted);

	/**
	 * @return position in millis or {@link #UNKNOWN_TIME} for live
	 */
	long getCurrentPosition();

	/**
	 * @return duration in millis or {@link #UNKNOWN_TIME} for live
	 */
	long getDuration();

	int getBufferPercentage();

	long getBufferPosition();

	void release() throws IllegalStateException;

	boolean isLive();

	long getPlaylistStartTime();

	/**
	 * Video and audio are displayed remotely instead of locally on the device.
	 * @return true for chromecast and such, false for local players
	 */
	boolean isRemote();

	SRGMediaPlayerController.Event.ScreenType getScreenType();

	/**
	 * Force usage of a specific quality (when supported). Represented by bandwidth.
	 * @param quality quality in bits/sec or null to disable
	 */
	void setQualityOverride(Long quality);

	/**
	 * Force usage of a specific quality (when supported). Represented by bandwidth.
	 * @param quality quality in bits/sec or null to disable
	 */
	void setQualityDefault(Long quality);

	/**
	 * Get current bandwidth estimate (when supported).
	 *
	 * @return bandwidth estimate in bits/sec or null if not available.
     */
	Long getBandwidthEstimate();

	/**
	 * Get Total bandwidth of currently playing stream.
	 *
	 * @return current bandwidth in bits/seconds or null if not available
	 */
	Long getCurrentBandwidth();

	long getPlaylistReferenceTime();

	@NonNull
	List<SubtitleTrack> getSubtitleTrackList();

	void setSubtitleTrack(SubtitleTrack track);
}
