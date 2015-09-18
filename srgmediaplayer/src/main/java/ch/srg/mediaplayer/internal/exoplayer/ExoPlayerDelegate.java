
package ch.srg.mediaplayer.internal.exoplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;

import java.io.IOException;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;


/**
 * Created by Axel on 02/03/2015.
 */

public class ExoPlayerDelegate implements
        PlayerDelegate,
        ExoPlayer.Listener,
        HlsSampleSource.EventListener,
        MediaCodecVideoTrackRenderer.EventListener,
        MediaCodecAudioTrackRenderer.EventListener,
        AudioCapabilitiesReceiver.Listener,
        RendererBuilderCallback {

    public enum SourceType {
        HLS,
        EXTRACTOR
    }

    public static final int RENDERER_COUNT = 2;
    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;
    public static final String TAG = SRGMediaPlayerController.TAG;

    private final Context context;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private AudioCapabilities audioCapabilities;

    private ExoPlayer exoPlayer;
    private SourceType sourceType = SourceType.HLS;
    private RendererBuilder rendererBuilder;
    private TrackRenderer videoRenderer;
    private TrackRenderer audioRenderer;

    private String videoSourceUrl = null;
    private float videoSourceAspectRatio = 1.7777f;
    private int videoSourceHeight = 0;

    private SurfaceView surfaceView;

    private OnPlayerDelegateListener controller;
    private boolean audioTrack = true;
    private boolean videoTrack = true;

    public ExoPlayerDelegate(Context context, OnPlayerDelegateListener controller) {
        this(context, controller, SourceType.HLS);
    }

    public ExoPlayerDelegate(Context context, OnPlayerDelegateListener controller, SourceType sourceType) {
        this.sourceType = sourceType;
        this.context = context;
        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(context, this);
        this.controller = controller;
        exoPlayer = ExoPlayer.Factory.newInstance(5, 1000, 5000);
        exoPlayer.addListener(this);
    }

    /* package */ Handler getMainHandler() {
        return controller.getMainHandler();
        //return mainHandler;
    }


    @Override
    public void prepare(Uri videoUri) throws SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri);
        try {
            String videoSourceUrl = videoUri.toString();
            if (videoSourceUrl.equalsIgnoreCase(this.videoSourceUrl)) {
                return;
            }
            controller.onPlayerDelegatePreparing(this);
            if (this.videoSourceUrl != null) {
                try {
                    //exoPlayer.reset();
                } catch (IllegalStateException e) {
                    Log.v(TAG, "Reset on play", e);
                }
            }
            this.videoSourceUrl = videoSourceUrl;

            switch (sourceType) {
                case HLS:
                    rendererBuilder = new HlsRendererBuilder(context, "android", this.videoSourceUrl, audioCapabilities);
                    break;
                case EXTRACTOR:
                default:
                    rendererBuilder = new ExtractorRendererBuilder(context, "android", Uri.parse(this.videoSourceUrl));
                    break;
            }

            rendererBuilder.buildRenderers(this, this);
        } catch (Exception e) {
            release();
            throw new SRGMediaPlayerException(e);
        }
    }

    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        boolean audioCapabilitiesChanged = !audioCapabilities.equals(this.audioCapabilities);
        if (audioCapabilitiesChanged) {
            this.audioCapabilities = audioCapabilities;
//			releasePlayer();
//			preparePlayer();
        }
    }

    @Override
    public void onRenderers(TrackRenderer[] renderers, BandwidthMeter bandwidthMeter) {
        if (!audioTrack) {
            renderers[TYPE_AUDIO] = null;
        }
        if (!videoTrack) {
            renderers[TYPE_VIDEO] = null;
        }
        for (int i = 0; i < RENDERER_COUNT; i++) {
            if (renderers[i] == null) {
                // Convert a null renderer to a dummy renderer.
                renderers[i] = new DummyTrackRenderer();
            }
        }
        this.videoRenderer = renderers[TYPE_VIDEO];
        this.audioRenderer = renderers[TYPE_AUDIO];
        Log.v(TAG,
                "Using renderers: video:" + videoRenderer + " audio:" + audioRenderer);
        pushSurface(false);
        exoPlayer.setSelectedTrack(TYPE_AUDIO, ExoPlayer.TRACK_DEFAULT);
        exoPlayer.setSelectedTrack(TYPE_VIDEO, ExoPlayer.TRACK_DEFAULT);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.prepare(renderers);
    }

    @Override
    public void onRenderersError(Exception e) {
        Log.d(TAG, "onRenderersError ", e);
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException(e));
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        exoPlayer.setPlayWhenReady(playIfReady);
    }

    @Override
    public void release() throws IllegalStateException {
        exoPlayer.stop();
        exoPlayer.release();
    }

    @Override
    public int getBufferPercentage() {
        return exoPlayer.getBufferedPercentage();
    }

    @Override
    public long getBufferPosition() {
        return exoPlayer.getBufferedPosition();
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        long seekPosition = exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
                : Math.min(Math.max(0, positionInMillis), getDuration());
        exoPlayer.seekTo(seekPosition);
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer.getPlaybackState() == ExoPlayer.STATE_READY && exoPlayer.getPlayWhenReady();
    }

    @Override
    public long getCurrentPosition() {
        return exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? UNKNOWN_TIME : (int) exoPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? UNKNOWN_TIME : (int) exoPlayer.getDuration();
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
    public void bindRenderingViewInUiThread(SRGMediaPlayerView mpv) throws SRGMediaPlayerException {
        if (mpv == null || !canRenderInView(mpv.getVideoRenderingView())) {
            throw new SRGMediaPlayerException("ExoPlayerDelegate cannot render video in a " + mpv);
        }
        surfaceView = (SurfaceView) mpv.getVideoRenderingView();
        if (surfaceView != null && surfaceView.getHolder() != null && surfaceView.getHolder().getSurface() != null) {
            pushSurface(true);
        }
    }

    @Override
    public void unbindRenderingView() {
        surfaceView = null;
        exoPlayer.blockingSendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (videoRenderer == null) {
            Log.d(TAG, "Exoplayer push surface w/o videoRenderer");
            return;
        }
        Surface surface = null;
        if (surfaceView != null && surfaceView.getHolder() != null) {
            surface = surfaceView.getHolder().getSurface();
        }
        if (blockForSurfacePush) {
            exoPlayer.blockingSendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        } else {
            exoPlayer.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        }
        recomputeVideoContainerConstrains();
    }


    @Override
    public void setMuted(boolean muted) {
        exoPlayer.sendMessage(audioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, muted ? 0f : 1f);
    }

    private void recomputeVideoContainerConstrains() {
        SRGMediaPlayerView mediaPlayerView = controller.getMediaPlayerView();
        if (mediaPlayerView == null || surfaceView == null) {
            return; //nothing to do now.
        }
        if (Float.isNaN(videoSourceAspectRatio) || Float.isInfinite(videoSourceAspectRatio)) {
            videoSourceAspectRatio = 16f / 10;
        }
        mediaPlayerView.setVideoAspectRatio(videoSourceAspectRatio);
        mediaPlayerView.invalidate();
    }

    // #############################################
    // #############################################
    // ##  IMPLEMENTATION ON EXOPLAYER CALLBACKS  ##
    // #############################################
    // #############################################

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.v(TAG, toString() + " exo state change: " + playWhenReady + " " + playbackState);
        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                //controller.onPlayerDelegateStateChanged(this, SRGMediaPlayerController.State.IDLE);
                break;
            case ExoPlayer.STATE_PREPARING:
                controller.onPlayerDelegatePreparing(this);
                break;
            case ExoPlayer.STATE_BUFFERING:
                controller.onPlayerDelegateBuffering(this);
                break;
            case ExoPlayer.STATE_READY:
                controller.onPlayerDelegateReady(this);
                break;
            case ExoPlayer.STATE_ENDED:
                controller.onPlayerDelegateCompleted(this);
                break;
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {
        controller.onPlayerDelegatePlayWhenReadyCommited(this);
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException(e));
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        Log.v(TAG, "onDroppedFrames: " + count + "frames, " + elapsed + "ms");
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.v(TAG, "video size changed: " + width + "x" + height + " pixelRatio:" + pixelWidthHeightRatio);
        videoSourceHeight = height;
        videoSourceAspectRatio = width / (float) height; // TODO Shouldn't we take into account pixelWidthHeightRatio?
        recomputeVideoContainerConstrains();
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
    }


    @Override
    public void onDecoderInitialized(String s, long l, long l2) {
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        Log.e(TAG, "onDecoderInitializationError: " + e);
    }

    @Override
    public void onDecoderError(IllegalStateException e) {
        Log.e(TAG, "onDecoderError: " + e);
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        Log.e(TAG, "onCryptoError: " + e);
    }

    @Override
    public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format, int mediaStartTimeMs, int mediaEndTimeMs) {

    }

    @Override
    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format, int mediaStartTimeMs, int mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
    }

    @Override
    public void onLoadCanceled(int sourceId, long bytesLoaded) {
        // Do nothing.
    }

    @Override
    public void onUpstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs) {
        // Do nothing.
    }

    @Override
    public void onLoadError(int i, IOException e) {
        Log.e(TAG, "onLoadError: ", e);
    }

    @Override
    public void onDownstreamFormatChanged(int sourceId, Format format, int trigger, int mediaTimeMs) {
        Log.e(TAG, "downstream format changed: " + sourceId + " format: " + format + " trigger:" + trigger + " mediaTimeMs:" + mediaTimeMs);
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException(e));
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException(e));
    }

    private void checkStateForTrackActivation() {
        if (exoPlayer.getPlaybackState() != ExoPlayer.STATE_IDLE) {
            throw new IllegalStateException("track activation change after init not supported");
        }
    }

    public void setAudioTrack(boolean audioTrack) {
        checkStateForTrackActivation();
        this.audioTrack = audioTrack;
    }

    public void setVideoTrack(boolean videoTrack) {
        checkStateForTrackActivation();
        this.videoTrack = videoTrack;
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }
}

