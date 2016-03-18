package ch.srg.mediaplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.View;

/**
 * Created by Axel on 04/03/2015.
 *
 * This is the generic interface to implement if you need to create a custom delegate for playing
 * audio or video content with the player.
 *
 */
public interface PlayerDelegate {
	long UNKNOWN_TIME = -1;

	interface OnPlayerDelegateListener {

		void onPlayerDelegatePreparing(PlayerDelegate delegate);

		void onPlayerDelegateReady(PlayerDelegate delegate);

		void onPlayerDelegatePlayWhenReadyCommited(PlayerDelegate delegate);

		void onPlayerDelegateBuffering(PlayerDelegate delegate);

		void onPlayerDelegateCompleted(PlayerDelegate delegate);

		void onPlayerDelegateError(PlayerDelegate delegate, SRGMediaPlayerException e);

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
	 * @throws SRGMediaPlayerException
	 */
	void prepare(Uri videoUri) throws SRGMediaPlayerException;

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

	int getVideoSourceHeight();

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
}
