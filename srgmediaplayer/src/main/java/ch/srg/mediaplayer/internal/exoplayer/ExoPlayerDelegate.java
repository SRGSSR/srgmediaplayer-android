
package ch.srg.mediaplayer.internal.exoplayer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.SubtitleTrack;


/**
 * Created by Axel on 02/03/2015.
 */

public class ExoPlayerDelegate implements
        PlayerDelegate,
        AudioCapabilitiesReceiver.Listener, ExoPlayer.EventListener {

    public static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private final EventLogger eventLogger;
    private final DefaultTrackSelector trackSelector;
    private final TrackSelectionHelper trackSelectionHelper;
    private Long qualityOverride;
    private Long qualityDefault;
    private long playlistReferenceTime;
    private Boolean playWhenReady;
    private Integer playbackState;

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    public enum SourceType {
        HLS,
        EXTRACTOR,
        DASH
    }

    public static final String TAG = SRGMediaPlayerController.TAG;

    private final Context context;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private AudioCapabilities audioCapabilities;

    private SimpleExoPlayer exoPlayer;
    private SourceType sourceType = SourceType.HLS;

    private String videoSourceUrl = null;
    private float videoSourceAspectRatio = 1.7777f;

    private View renderingView;

    private OnPlayerDelegateListener controller;

    private boolean live;

    private long playlistStartTimeMs;

    private ViewType viewType = ViewType.TYPE_SURFACEVIEW;

    private boolean muted;

    private Handler mainHandler;

    public ExoPlayerDelegate(Context context, OnPlayerDelegateListener controller, SourceType sourceType) {
        this.sourceType = sourceType;
        this.context = context;
        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(context, this);
        this.controller = controller;

        mainHandler = new Handler();

        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);

        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;

        boolean useExtensionRenderers = true;
        @SimpleExoPlayer.ExtensionRendererMode int extensionRendererMode = SimpleExoPlayer.EXTENSION_RENDERER_MODE_PREFER;

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        trackSelectionHelper = new TrackSelectionHelper(trackSelector, videoTrackSelectionFactory);
        eventLogger = new EventLogger(trackSelector);

        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector, new DefaultLoadControl(),
                drmSessionManager, extensionRendererMode);
        exoPlayer.addListener(this);
        exoPlayer.setAudioDebugListener(eventLogger);
        exoPlayer.setVideoDebugListener(eventLogger);
        exoPlayer.setMetadataOutput(eventLogger);
    }

    /* package */ Handler getMainHandler() {
        return controller.getMainHandler();
        //return mainHandler;
    }


    @Override
    public void prepare(Uri videoUri) throws SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri + " (" + sourceType + ")");
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

            String userAgent = "SRGLibrary/2.0alpha";

            DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(userAgent, BANDWIDTH_METER);
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, BANDWIDTH_METER, httpDataSourceFactory);

            MediaSource mediaSource;

            switch (sourceType) {
                case DASH:
                    mediaSource = new DashMediaSource(videoUri, dataSourceFactory,
                            new DefaultDashChunkSource.Factory(dataSourceFactory), mainHandler, eventLogger);
                    break;
                case HLS:
                    mediaSource = new HlsMediaSource(videoUri, dataSourceFactory, mainHandler, eventLogger);
                    break;
                case EXTRACTOR:
                default:
                    mediaSource = new ExtractorMediaSource(videoUri, dataSourceFactory, new DefaultExtractorsFactory(),
                            mainHandler, eventLogger);
                    break;
            }

            exoPlayer.prepare(mediaSource);

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
        long duration = exoPlayer.getDuration();
        if (duration != C.TIME_UNSET && exoPlayer.isCurrentWindowSeekable()) {
            long positionMs = positionInMillis == 0 ? C.TIME_UNSET : Math.min(positionInMillis, duration - 1);
            exoPlayer.seekTo(positionMs);
        } else {
            throw new IllegalStateException("Unknown duration when trying to seek to " + positionInMillis);
        }
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer.getPlaybackState() == ExoPlayer.STATE_READY && exoPlayer.getPlayWhenReady();
    }

    @Override
    public long getCurrentPosition() {
        return exoPlayer.getDuration() == C.TIME_UNSET ? UNKNOWN_TIME : (int) exoPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return exoPlayer.getDuration() == C.TIME_UNSET ? UNKNOWN_TIME : (int) exoPlayer.getDuration();
    }

    @Override
    public boolean hasVideoTrack() {
        TrackSelectionArray currentTrackSelections = exoPlayer.getCurrentTrackSelections();
        for (int i = 0; i < currentTrackSelections.length; i++) {
            if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                if (currentTrackSelections.get(i) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canRenderInView(View view) {
        return view instanceof SurfaceView || view instanceof TextureView;
    }

    @Override
    public View createRenderingView(Context parentContext) {
        if (viewType == ViewType.TYPE_SURFACEVIEW) {
            return new SurfaceView(parentContext);
        } else {
            return new TextureView(parentContext);
        }
    }

    @Override
    public void bindRenderingViewInUiThread(SRGMediaPlayerView mpv) throws SRGMediaPlayerException {
        if (mpv == null || !canRenderInView(mpv.getVideoRenderingView())) {
            throw new SRGMediaPlayerException("ExoPlayerDelegate cannot render video in a " + mpv);
        }
        renderingView = mpv.getVideoRenderingView();
        pushSurface(false);
    }

    @Override
    public void unbindRenderingView() {
        renderingView = null;
//        exoPlayer.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (renderingView instanceof SurfaceView) {
            exoPlayer.setVideoSurfaceView((SurfaceView) renderingView);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.setVideoTextureView((TextureView) renderingView);
        }
        recomputeVideoContainerConstrains();
    }


    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
        applyMuted();
    }

    private void applyMuted() {
        exoPlayer.setVolume(muted ? 0f : 1f);
    }

    private void recomputeVideoContainerConstrains() {
        SRGMediaPlayerView mediaPlayerView = controller.getMediaPlayerView();
        if (mediaPlayerView == null || renderingView == null) {
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
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.v(TAG, toString() + " exo state change: " + playWhenReady + " " + playbackState);
        if (this.playbackState == null || this.playbackState != playbackState) {
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    //controller.onPlayerDelegateStateChanged(this, SRGMediaPlayerController.State.IDLE);
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
            this.playbackState = playbackState;
        }
        if (this.playWhenReady == null || this.playWhenReady != playWhenReady) {
            controller.onPlayerDelegatePlayWhenReadyCommited(this);
            this.playWhenReady = playWhenReady;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException(e));
    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public long getPlaylistReferenceTime() {
        return getPlaylistStartTime();
    }

    @Override
    public boolean isLive() {
        return exoPlayer.isCurrentWindowDynamic();
    }

    @Override
    public long getPlaylistStartTime() {
        return isLive() ? System.currentTimeMillis() : 0;
    }

    private void checkStateForTrackActivation() {
        if (exoPlayer.getPlaybackState() != ExoPlayer.STATE_IDLE) {
            throw new IllegalStateException("track activation change after init not supported");
        }
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public SRGMediaPlayerController.Event.ScreenType getScreenType() {
        return renderingView != null ? SRGMediaPlayerController.Event.ScreenType.DEFAULT : SRGMediaPlayerController.Event.ScreenType.NONE;
    }

    public void setViewType(ViewType viewType) {
        this.viewType = viewType;
    }

    @Override
    public void setQualityOverride(Long quality) {
        this.qualityOverride = quality;
    }

    @Override
    public void setQualityDefault(Long quality) {
        this.qualityDefault = quality;
    }

    @Override
    public Long getBandwidthEstimate() {
        return null;
    }

    @NonNull
    @Override
    public List<SubtitleTrack> getSubtitleTrackList() {
        return new ArrayList<>();
    }

    @Nullable
    private SubtitleTrack getSubtitleTrackByTrackId(int i) {
        return null;
    }

    @Override
    public void setSubtitleTrack(SubtitleTrack track) {
    }

    @Override
    @Nullable
    public SubtitleTrack getSubtitleTrack() {
        return null;
    }


}

