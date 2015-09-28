package ch.srg.mediaplayer.internal.nativeplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import java.util.HashMap;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

/**
 * Created by Axel on 02/03/2015.
 */
public class NativePlayerDelegate implements
		PlayerDelegate,
		MediaPlayer.OnPreparedListener,
		MediaPlayer.OnBufferingUpdateListener,
		MediaPlayer.OnSeekCompleteListener,
		MediaPlayer.OnVideoSizeChangedListener,
		MediaPlayer.OnInfoListener,
		MediaPlayer.OnErrorListener,
		MediaPlayer.OnCompletionListener {

	private enum State {
		IDLE,
		PREPARING,
		READY,
		ERROR,
		RELEASED
	}

	private MediaPlayer nativeMp;

	private String videoSourceUrl = null;
	private float videoSourceAspectRatio = SRGMediaPlayerView.DEFAULT_ASPECT_RATIO;
	private int videoSourceHeight = 0;

	private SRGMediaPlayerView mediaPlayerView;
	private SurfaceView surfaceView;

	private OnPlayerDelegateListener controller;

	// We have to maintain state as underlying player does not expose it
	private State state;

	public NativePlayerDelegate(OnPlayerDelegateListener controller) {
		this.controller = controller;
		nativeMp = new MediaPlayer();
		nativeMp.setOnPreparedListener(this);
		nativeMp.setOnBufferingUpdateListener(this);
		nativeMp.setOnSeekCompleteListener(this);
		nativeMp.setOnVideoSizeChangedListener(this);
		nativeMp.setOnInfoListener(this);
		nativeMp.setOnErrorListener(this);
		nativeMp.setOnCompletionListener(this);
		state = State.IDLE;
	}

	@Override
	public void prepare(Uri videoUri) throws SRGMediaPlayerException {
		Log.v(SRGMediaPlayerController.TAG, "Preparing " + videoUri);
		if (state != State.IDLE) {
			throw new IllegalStateException("Prepare can only be called once. prepare in " + state);
		}
		try {
			String videoSourceUrl = videoUri.toString();
			if (videoSourceUrl.equalsIgnoreCase(this.videoSourceUrl)) {
				Log.v(SRGMediaPlayerController.TAG, "Skipping same videoUrl");
				return;
			}
			controller.onPlayerDelegatePreparing(this);
			if (this.videoSourceUrl != null) {
				try {
					nativeMp.reset();
				} catch (IllegalStateException e) {
					Log.v(SRGMediaPlayerController.TAG, "Reset on play", e);
				}
			}
			this.videoSourceUrl = videoSourceUrl;

			HashMap<String, String> map = new HashMap<>();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			{
				// Fix bug where media play works once but fails on second time
				// https://jira.appcelerator.org/browse/TC-5053
				// Bug can be reproduced with m3u audio streams
				map.put("Cache-Control", "no-cache"); /* still happens in 5.0.1 */
			}
			nativeMp.setDataSource(controller.getContext(), videoUri, map);

			nativeMp.prepareAsync();

			state = State.PREPARING;
		} catch (Exception e) {
			release();
			throw new SRGMediaPlayerException(e);
		}
	}

	@Override
	public void playIfReady(boolean playIfReady) throws IllegalStateException {
		if (state == State.READY) {
			if (nativeMp.isPlaying() && !playIfReady) {
				nativeMp.pause();

			} else if (!nativeMp.isPlaying() && playIfReady) {
				nativeMp.start();
			}
			controller.onPlayerDelegatePlayWhenReadyCommited(this);
		}
	}

	@Override
	public void release() throws IllegalStateException {
		state = State.RELEASED;
		Thread endingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					nativeMp.setDisplay(null);
				} catch (Exception e) {
					Log.e(SRGMediaPlayerController.TAG, "NativePlayerDelegate Release: ", e);
				}
				nativeMp.setVolume(0f, 0f);
				nativeMp.stop();
				nativeMp.release();
			}
		});
		endingThread.start();
	}

	@Override
	public int getBufferPercentage() {
		return 0;
	}

	@Override
	public long getBufferPosition() {
		return 0;
	}

	@Override
	public void seekTo(long msec) throws IllegalStateException {
		controller.onPlayerDelegateBuffering(this);
		nativeMp.seekTo((int) msec);
	}

	@Override
	public boolean isPlaying() {
		if (state == State.READY) {
			return nativeMp.isPlaying();
		}
		return false;
	}

	@Override
	public long getCurrentPosition() {
		if (state == State.READY) {
			return nativeMp.getCurrentPosition();
		} else {
			return UNKNOWN_TIME;
		}
	}

	@Override
	public long getDuration() {
		if (state == State.READY) {
			return nativeMp.getDuration();
		} else {
			return UNKNOWN_TIME;
		}
	}

	@Override
	public int getVideoSourceHeight() {
		return videoSourceHeight;
	}


	@Override
	public boolean canRenderInView(View view) {
		return view != null && view instanceof SurfaceView;
	}

	@Override
	public View createRenderingView(Context parentContext) {
		return new SurfaceView(parentContext);
	}

	@Override
	public void bindRenderingViewInUiThread(SRGMediaPlayerView mediaPlayerView) throws SRGMediaPlayerException {
		if (mediaPlayerView == null || !canRenderInView(mediaPlayerView.getVideoRenderingView())) {
			throw new SRGMediaPlayerException("NativePlayerDelegate can render video in a " + mediaPlayerView);
		}
		surfaceView = (SurfaceView) mediaPlayerView.getVideoRenderingView();
		if (surfaceView != null && surfaceView.getHolder() != null) {
			nativeMp.setDisplay(surfaceView.getHolder());
		}
	}

	@Override
	public void unbindRenderingView() {
		nativeMp.setDisplay(null);
	}

	@Override
	public void setMuted(boolean muted) {
		if (muted) {
			nativeMp.setVolume(0f, 0f);
		} else {
			nativeMp.setVolume(1f, 1f);
		}
	}

	private void recomputeVideoContainerConstrains() {
		if (mediaPlayerView == null || surfaceView == null) {
			return; //nothing to do now.
		}
		if (Float.isNaN(videoSourceAspectRatio) || Float.isInfinite(videoSourceAspectRatio)) {
			Log.e(SRGMediaPlayerController.TAG, "Invalid aspect ratio: " + videoSourceAspectRatio);
			videoSourceAspectRatio = SRGMediaPlayerView.DEFAULT_ASPECT_RATIO;
		}
		mediaPlayerView.setVideoAspectRatio(videoSourceAspectRatio);

		controller.getMainHandler().post(new Runnable() {
			@Override
			public void run() {
				if (mediaPlayerView != null) {
					mediaPlayerView.invalidate();
				}
			}
		});


	}

	// ######################################################
	// ######################################################
	// ##  IMPLEMENTATION ON NATIVE MEDIAPLAYER CALLBACKS  ##
	// ######################################################
	// ######################################################

	@Override
	public void onPrepared(MediaPlayer mp) {
		if (state != State.PREPARING) {
			Log.e(SRGMediaPlayerController.TAG, "Unexpected onPrepared when in state: " + state);
		}

		state = State.READY;

		int h = mp.getVideoHeight();
		int w = mp.getVideoWidth();
		if (h != 0 && w != 0) {
			videoSourceHeight = h;
			videoSourceAspectRatio = w / (float) h;
			recomputeVideoContainerConstrains();
		}
		controller.onPlayerDelegateReady(this);
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		Log.d(SRGMediaPlayerController.TAG, "Buffering @ " + percent);
	}


	@Override
	public void onSeekComplete(MediaPlayer mp) {
		controller.onPlayerDelegateReady(this);
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		Log.d(SRGMediaPlayerController.TAG, "Size changed WxH:" + width + "x" + height);
		if (height != 0 && width != 0) {
			videoSourceHeight = height;
			videoSourceAspectRatio = width / (float) height;
			recomputeVideoContainerConstrains();
		}
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		switch (what) {
			case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
				Log.d(SRGMediaPlayerController.TAG, "Rendering started");
				break;
			case MediaPlayer.MEDIA_INFO_BUFFERING_START:
				Log.d(SRGMediaPlayerController.TAG, "Buffering " + videoSourceUrl);
				break;
			case MediaPlayer.MEDIA_INFO_BUFFERING_END:
				Log.d(SRGMediaPlayerController.TAG, "Buffering done");
				break;
			default:
				Log.d(SRGMediaPlayerController.TAG, "onInfo what=" + what + ",extra=" + extra);
				break;
		}
		return false;
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.d(SRGMediaPlayerController.TAG, "onError what=" + what + ",extra=" + extra);
		controller.onPlayerDelegateError(this, new NativePlayerDelegateException(what, extra));
		state = State.ERROR;
		return true;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		Log.d(SRGMediaPlayerController.TAG, "Playing completed");
		controller.onPlayerDelegateCompleted(this);
	}

	private static class NativePlayerDelegateException extends SRGMediaPlayerException {
		private final int what;
		private final int extra;

		public NativePlayerDelegateException(int what, int extra) {
			super(toMessage(what, extra));
			this.what = what;
			this.extra = extra;
		}

		private static String toMessage(int what, int extra) {
			return String.format("Native player error: %d / %d", what, extra);
		}
	}

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public long getPlaylistStartTime() {
		return 0;
	}
}
