
package ch.srg.mediaplayer.internal.exoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;

import java.util.ArrayList;
import java.util.List;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.SubtitleTrack;


/**
 * Created by Axel on 02/03/2015.
 */

public class ExoPlayerDelegate implements
        PlayerDelegate,
        AudioCapabilitiesReceiver.Listener,
        ExoPlayer.EventListener,
        TextRenderer.Output {

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private final EventLogger eventLogger;
    private final DefaultTrackSelector trackSelector;
    private final DefaultRenderersFactory renderersFactory;
    private long playlistReferenceTime;
    private Boolean playWhenReady;
    private Integer playbackState;

    @Override
    public void onCues(List<Cue> cues) {
        controller.onPlayerDelegateSubtitleCues(this, cues);
    }

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    public static final String TAG = SRGMediaPlayerController.TAG;

    private final Context context;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private AudioCapabilities audioCapabilities;

    private SimpleExoPlayer exoPlayer;

    private String videoSourceUrl = null;

    private View renderingView;

    private OnPlayerDelegateListener controller;

    private boolean live;

    private ViewType viewType = ViewType.TYPE_SURFACEVIEW;

    private boolean muted;

    private Handler mainHandler;

    public ExoPlayerDelegate(Context context, OnPlayerDelegateListener controller) {
        this.context = context;
        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(context, this);
        this.controller = controller;

        mainHandler = new Handler();

        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);

        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        eventLogger = new EventLogger(trackSelector);
        renderersFactory = new DefaultRenderersFactory(context, drmSessionManager, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, new DefaultLoadControl());
        exoPlayer.addListener(this);
        exoPlayer.setTextOutput(this);
        exoPlayer.setAudioDebugListener(eventLogger);
        exoPlayer.setVideoDebugListener(eventLogger);
        exoPlayer.setVideoListener(new SimpleExoPlayer.VideoListener() {
            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                ExoPlayerDelegate.this.controller.onPlayerDelegateVideoSizeChanged(ExoPlayerDelegate.this, width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
            }

            @Override
            public void onRenderedFirstFrame() {

            }
        });
        exoPlayer.setMetadataOutput(eventLogger);
    }

    /* package */ Handler getMainHandler() {
        return controller.getMainHandler();
        //return mainHandler;
    }


    @Override
    public void prepare(Uri videoUri, int streamType) throws SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri + " (" + streamType + ")");
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

            DefaultDataSourceFactory httpDataSourceFactory = getHttpDataSourceFactory();

            MediaSource mediaSource;

            switch (streamType) {
                case SRGMediaPlayerDataProvider.STREAM_DASH:
                    mediaSource = new DashMediaSource(videoUri, httpDataSourceFactory,
                            new DefaultDashChunkSource.Factory(httpDataSourceFactory), mainHandler, eventLogger);
                    break;
                case SRGMediaPlayerDataProvider.STREAM_HLS:
                    mediaSource = new HlsMediaSource(videoUri, httpDataSourceFactory, mainHandler, eventLogger);
                    break;
                case SRGMediaPlayerDataProvider.STREAM_HTTP_PROGRESSIVE:
                    mediaSource = new ExtractorMediaSource(videoUri, httpDataSourceFactory, new DefaultExtractorsFactory(),
                            mainHandler, eventLogger);
                    break;
                case SRGMediaPlayerDataProvider.STREAM_LOCAL_FILE:
                    FileDataSourceFactory fileDataSourceFactory = new FileDataSourceFactory();
                    mediaSource = new ExtractorMediaSource(videoUri, fileDataSourceFactory, new DefaultExtractorsFactory(),
                            mainHandler, eventLogger);
                    break;
                default:
                    throw new IllegalStateException("Invalid source type: " + streamType);
            }

            exoPlayer.prepare(mediaSource);

        } catch (Exception e) {
            release();
            throw new SRGMediaPlayerException(e);
        }
    }

    @NonNull
    private DefaultDataSourceFactory getHttpDataSourceFactory() {
        String userAgent = "curl/Letterbox_2.0"; // temporarily using curl/ user agent to force subtitles with Akamai beta

        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(userAgent, BANDWIDTH_METER);
        return new DefaultDataSourceFactory(context, BANDWIDTH_METER, httpDataSourceFactory);
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
        exoPlayer.clearVideoSurface();
        renderingView = null;
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (renderingView instanceof SurfaceView) {
            exoPlayer.setVideoSurfaceView((SurfaceView) renderingView);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.setVideoTextureView((TextureView) renderingView);
        }
    }


    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
        applyMuted();
    }

    private void applyMuted() {
        exoPlayer.setVolume(muted ? 0f : 1f);
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
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

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

    }

    @Override
    public void setQualityDefault(Long quality) {

    }

    @Override
    public Long getBandwidthEstimate() {
        return null;
    }

    @NonNull
    @Override
    public List<SubtitleTrack> getSubtitleTrackList() {
        ArrayList<SubtitleTrack> subtitleTracks = new ArrayList<>();

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        int subtitleRendererId = getSubtitleRendererId();
        if (mappedTrackInfo != null && subtitleRendererId != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(subtitleRendererId);
            for (int i = 0; i < trackGroups.length; i++) {
                TrackGroup trackGroup = trackGroups.get(i);
                for (int j = 0; j < trackGroup.length; j++) {
                    SubtitleTrack subtitleTrack = getSubtitleTrack(trackGroup, i, j);
                    if (subtitleTrack != null) {
                        subtitleTracks.add(subtitleTrack);
                    }
                }
            }
        }
        return subtitleTracks;
    }

    @Nullable
    private SubtitleTrack getSubtitleTrack(TrackGroup trackGroup, int i, int j) {
        Format format = trackGroup.getFormat(j);
        if (format.id != null || format.language != null) {
            return new SubtitleTrack(new Pair<>(i, j), format.id, format.language);
        } else {
            return null;
        }
    }

    @Nullable
    private SubtitleTrack getSubtitleTrackByTrackId(int i, int j) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(getSubtitleRendererId());
        TrackGroup trackGroup = trackGroups.get(i);
        return getSubtitleTrack(trackGroup, i, j);
    }

    private int getSubtitleRendererId() {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo != null) {
            for (int i = 0; i < mappedTrackInfo.length; i++) {
                if (mappedTrackInfo.getTrackGroups(i).length > 0
                        && exoPlayer.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void setSubtitleTrack(SubtitleTrack track) {
        int rendererIndex = getSubtitleRendererId();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (rendererIndex != -1 && trackInfo != null) {
            TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
            trackSelector.setRendererDisabled(rendererIndex, track == null);
            if (track != null) {
                TrackSelection.Factory factory = new FixedTrackSelection.Factory();
                Pair<Integer, Integer> integerPair = (Pair<Integer, Integer>) track.tag;
                int groupIndex = integerPair.first;
                int trackIndex = integerPair.second;
                MappingTrackSelector.SelectionOverride override = new MappingTrackSelector.SelectionOverride(factory, groupIndex, trackIndex);
                trackSelector.setSelectionOverride(rendererIndex, trackGroups, override);
            } else {
                trackSelector.clearSelectionOverride(rendererIndex, trackGroups);
            }
        }
    }

    @Override
    @Nullable
    public SubtitleTrack getSubtitleTrack() {
        int rendererIndex = getSubtitleRendererId();

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null && rendererIndex != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

            MappingTrackSelector.SelectionOverride override = trackSelector.getSelectionOverride(rendererIndex, trackGroups);
            if (override != null) {
                int[] tracks = override.tracks;
                if (tracks.length != 0) {
                    return getSubtitleTrackByTrackId(override.groupIndex, tracks[0]);
                }
            }
        }
        return null;
    }
}

