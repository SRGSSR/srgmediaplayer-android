package ch.srg.mediaplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import com.akamai.android.analytics.AkamaiMediaAnalytics;
import com.akamai.android.analytics.EndReasonCodes;
import com.akamai.android.analytics.PluginCallBacks;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer2.drm.DefaultDrmSessionEventListener;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.spherical.SphericalSurfaceView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.video.VideoListener;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import ch.srg.mediaplayer.segment.model.Mark;
import ch.srg.mediaplayer.segment.model.MediaPlayerTimeLine;
import ch.srg.mediaplayer.segment.model.Segment;
import ch.srg.mediaplayer.segment.model.SegmentList;
import ch.srg.mediaplayer.utils.FileLicenseStore;
import ch.srg.mediaplayer.utils.LicenseStoreDelegate;
import ch.srg.mediaplayer.utils.MonitorTransferListener;

/**
 * Handles the playback of media.
 * <li> Encapsulate exoplayer
 * <li> Handles "logical segments"
 * <li> Handles DRM Configuration
 * <li> Audio focus
 * <li> Supports TextureView and SurfaceView
 * <li> Supports 360 playback with ExoPlayer view
 *
 * <p>
 * Threading: all calls to public method must be made from main thread. An exception will be
 * thrown in debug mode.
 */
@SuppressWarnings({"unused", "unchecked", "UnusedReturnValue", "WeakerAccess", "PointlessBitwiseExpression"})
@MainThread
public class SRGMediaPlayerController implements Handler.Callback,
        Player.EventListener,
        DefaultDrmSessionEventListener,
        VideoListener,
        AudioCapabilitiesReceiver.Listener,
        TextOutput {
    public static final String TAG = "SRGMediaPlayer";
    public static final String VERSION = BuildConfig.VERSION_NAME;

    private static final long[] EMPTY_TIME_RANGE = new long[2];
    private static final long UPDATE_PERIOD = 100;
    private static final long SEGMENT_HYSTERESIS_MS = 5000;
    private static final int MINIMUM_DRM_LICENSE_DURATION_SECONDS = 2 * 60;
    // Bandwidth meter uses application context which is fine
    @SuppressLint("StaticFieldLeak")
    private static DefaultBandwidthMeter singletonBandwidthMeter;
    private static byte[] offlineLicenseKeySetId;
    private Long userTrackingProgress;
    private static final String NAME = "SRGMediaPlayer";
    private boolean currentViewKeepScreenOn;
    @SuppressWarnings("FieldCanBeLocal")
    private MonitoringDrmCallback monitoringDrmCallback;
    /**
     * Set to true first time player goes to READY.
     */
    private boolean playingOrBuffering;
    @Nullable
    private DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
    @Nullable
    OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper;
    private DefaultHttpDataSourceFactory httpDataSourceFactory;

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    public enum SurfaceType {
        FLAT,
        SPHERICAL
    }

    /**
     * True when audio focus has been requested, does not reflect current focus (LOSS / DUCKED).
     */
    private boolean audioFocusGranted;
    private boolean debugMode;
    private boolean pausedBecauseTransientFocusLoss;
    private boolean duckedBecauseTransientFocusLoss;
    private boolean pausedBecauseFocusLoss;
    private boolean mutedBecauseFocusLoss;
    private Throwable fatalError;
    private long controllerId;
    private static long controllerIdCounter;

    private boolean firstFrameRendered;
    private boolean playbackActuallyStarted;

    public static final long UNKNOWN_TIME = -1;

    /**
     * Disable audio focus handling. Always play audio.
     */
    public static final int AUDIO_FOCUS_FLAG_DISABLED = 0;
    /**
     * Mute when losing audio focus.
     */
    public static final int AUDIO_FOCUS_FLAG_MUTE = 1 << 0;
    /**
     * Pause stream when losing audio focus. Do not auto restart unless AUDIO_FOCUS_FLAG_AUTO_RESTART is also set.
     */
    public static final int AUDIO_FOCUS_FLAG_PAUSE = 1 << 1;
    /**
     * Duck volume when losing audio focus.
     */
    public static final int AUDIO_FOCUS_FLAG_DUCK = 1 << 2;
    /**
     * If set, stream auto restart after gaining audio focus, must be used with AUDIO_FOCUS_FLAG_PAUSE to pause.
     * This concerns only non transient focus loss, in case of a transient focus lost the stream will always restart.
     */
    public static final int AUDIO_FOCUS_FLAG_AUTO_RESTART = 1 << 3;

    private static final int MSG_PREPARE_FOR_URI = 4;
    private static final int MSG_SET_PLAY_WHEN_READY = 5;
    private static final int MSG_SEEK_TO = 6;
    private static final int MSG_SET_MUTE = 7;
    private static final int MSG_APPLY_STATE = 8;
    private static final int MSG_RELEASE = 9;
    private static final int MSG_PLAYER_EXCEPTION = 12;
    private static final int MSG_REGISTER_EVENT_LISTENER = 13;
    private static final int MSG_UNREGISTER_EVENT_LISTENER = 14;
    private static final int MSG_PLAYER_PREPARING = 101;
    private static final int MSG_PLAYER_READY = 102;
    private static final int MSG_PLAYER_BUFFERING = 103;
    private static final int MSG_PLAYER_COMPLETED = 104;
    private static final int MSG_PLAYER_PLAY_WHEN_READY_COMMITED = 105;
    private static final int MSG_PLAYER_SUBTITLE_CUES = 106;
    private static final int MSG_PLAYER_VIDEO_ASPECT_RATIO = 107;
    private static final int MSG_PERIODIC_UPDATE = 300;
    private static final int MSG_FIRE_EVENT = 400;

    public enum State {
        /**
         * Player is not in the process of doing anything.
         */
        IDLE,
        /**
         * Player is trying to become usable.
         */
        PREPARING,
        /**
         * Player is capable to play.
         */
        READY,
        /**
         * Player is buffering.
         */
        BUFFERING,
        /**
         * Player released (end state).
         */
        RELEASED
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SRGMediaPlayerController.STREAM_HLS, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE, SRGMediaPlayerController.STREAM_DASH, SRGMediaPlayerController.STREAM_LOCAL_FILE})
    public @interface SRGStreamType {
    }

    public static final int STREAM_HLS = 1;
    public static final int STREAM_HTTP_PROGRESSIVE = 2;
    public static final int STREAM_DASH = 3;
    public static final int STREAM_LOCAL_FILE = 4;

    //region Event

    /**
     * Interface definition for a callback to be invoked when the status changes or is periodically emitted.
     */
    @SuppressWarnings({"WeakerAccess", "unused", "SameParameterValue"})
    public static class Event {

        public enum ScreenType {
            NONE,
            DEFAULT,
            CHROMECAST
        }

        public enum Type {
            /**
             * Player state has changed. New state in {@link #state}.
             */
            STATE_CHANGE,
            /**
             * Fatal error.
             */
            FATAL_ERROR,
            /**
             * When a non fatal error occured. Never sent currently.
             */
            TRANSIENT_ERROR,
            /**
             * Media will either play or will now be able to be played as soon as {@link #start()} is called.
             */
            MEDIA_READY_TO_PLAY,
            /**
             * Sent when media has reached end of stream. Media stopped will not be sent.
             */
            MEDIA_COMPLETED,
            /**
             * Sent when media has been stopped externally. This player will then be released.
             */
            MEDIA_STOPPED,
            /**
             * Exoplayer ready to play state changed.
             */
            PLAYING_STATE_CHANGE,
            /**
             * Start seeking.
             */
            WILL_SEEK,
            /**
             * Seeking is done.
             */
            DID_SEEK,
            /**
             * External plugin called. No longer used.
             */
            EXTERNAL_EVENT,
            /**
             * A player view is connected to this player.
             */
            DID_BIND_TO_PLAYER_VIEW,
            /**
             * The player view has been disconnected to this player.
             */
            DID_UNBIND_FROM_PLAYER_VIEW,
            /**
             * Subtitle track has changed.
             */
            SUBTITLE_DID_CHANGE,
            /**
             * Audio track has changed.
             */
            AUDIO_TRACK_DID_CHANGE,
            /**
             * The first video frame has been rendered. See also {@link #PLAYBACK_ACTUALLY_STARTED}.
             */
            FIRST_FRAME_RENDERED,
            /**
             * A position jump has occurred in the stream. Can happen because of network or stream error.
             */
            POSITION_DISCONTINUITY,

            /**
             * The value of {@link #isLoading()} has changed. Any player buffering start / end will trigger this event.
             */
            LOADING_STATE_CHANGED,

            /**
             * An identified segment (visible or not) is being started, while not being inside a segment before.
             */
            SEGMENT_START,
            /**
             * An identified segment (visible or not) is being ended, without another one to start.
             */
            SEGMENT_END,
            /**
             * An identified segment (visible or not) is being started, while being inside another segment before.
             */
            SEGMENT_SWITCH,
            /**
             * The user has selected a visible segment.
             */
            SEGMENT_SELECTED,
            /**
             * The playback is being seek to a later value, because it reached a blocked segment.
             */
            SEGMENT_SKIPPED_BLOCKED,
            /**
             * The user has tried to seek to a blocked segment, seek has been denied.
             */
            SEGMENT_USER_SEEK_BLOCKED,
            /**
             * The Segment list has changed.
             */
            SEGMENT_LIST_CHANGE,
            /**
             * DRM Keys have been received. Can be called multiple times during stream playback.
             */
            DRM_KEYS_LOADED,
            /**
             * Stream timeline (DASH Manifest) has been updated. (Warning: This is not related to segments).
             */
            STREAM_TIMELINE_CHANGED,
            /**
             * Playback actually started: media stream position is changing after playback. This event is also sent
             * for audio only media. It Can be used to monitor "perceived" performance.
             */
            PLAYBACK_ACTUALLY_STARTED
        }

        public final Type type;

        public final Uri mediaUri;

        public final String mediaSessionId;
        public final long mediaPosition;
        public final long mediaDuration;
        public final boolean mediaPlaying;
        public final boolean mediaMuted;
        public final String videoViewDimension;
        public final String tag;
        public final boolean mediaLive;
        public final ScreenType screenType;
        public final State state;

        @Nullable
        public Segment segment;
        @Nullable
        public String blockingReason;
        public Event.Type segmentEventType;

        @Nullable
        public final SRGMediaPlayerException exception;

        private static Event buildTestEvent(SRGMediaPlayerController controller) {
            return new Event(controller, Type.EXTERNAL_EVENT, null);
        }

        private static Event buildEvent(SRGMediaPlayerController controller, Type eventType) {
            return new Event(controller, eventType, null);
        }

        private static Event buildErrorEvent(SRGMediaPlayerController controller, boolean fatalError, SRGMediaPlayerException exception) {
            return new Event(controller, fatalError ? Type.FATAL_ERROR : Type.TRANSIENT_ERROR, exception);
        }

        private static Event buildStateEvent(SRGMediaPlayerController controller) {
            return new Event(controller, Type.STATE_CHANGE, null);
        }

        private Event(SRGMediaPlayerController controller, Type eventType, @Nullable SRGMediaPlayerException eventException, @Nullable Segment segment, @Nullable String blockingReason) {
            type = eventType;
            tag = controller.tag;
            state = controller.state;
            exception = eventException;
            mediaSessionId = controller.getMediaSessionId();
            mediaUri = controller.currentMediaUri;
            mediaPosition = controller.getMediaPosition();
            mediaDuration = controller.getMediaDuration();
            mediaPlaying = controller.isPlaying();
            mediaMuted = controller.muted;
            mediaLive = controller.isLive();
            SRGMediaPlayerView mediaPlayerView = controller.mediaPlayerView;
            videoViewDimension = mediaPlayerView != null ? mediaPlayerView.getVideoRenderingViewSizeString() : SRGMediaPlayerView.UNKNOWN_DIMENSION;
            screenType = controller.getScreenType();
            this.segment = segment;
            this.blockingReason = blockingReason;
        }

        private Event(SRGMediaPlayerController controller, Type eventType, SRGMediaPlayerException eventException, Segment segment) {
            this(controller, eventType, eventException, segment, null);
        }

        private Event(SRGMediaPlayerController controller, Type eventType, SRGMediaPlayerException eventException) {
            this(controller, eventType, eventException, null, null);
        }

        protected Event(SRGMediaPlayerController controller, SRGMediaPlayerException eventException) {
            this(controller, Type.EXTERNAL_EVENT, eventException);
        }

        public boolean hasException() {
            return type == Type.FATAL_ERROR || type == Type.TRANSIENT_ERROR || exception != null;
        }

        @NonNull
        @Override
        public String toString() {
            return "Event{" +
                    "type=" + type +
                    ", mediaUrl='" + mediaUri + '\'' +
                    ", mediaSessionId='" + mediaSessionId + '\'' +
                    ", mediaPosition=" + mediaPosition +
                    ", mediaDuration=" + mediaDuration +
                    ", mediaPlaying=" + mediaPlaying +
                    ", mediaMuted=" + mediaMuted +
                    ", videoViewDimension='" + videoViewDimension + '\'' +
                    ", tag='" + tag + '\'' +
                    ", mediaLive=" + mediaLive +
                    ", screenType=" + screenType +
                    ", state=" + state +
                    ", exception=" + exception +
                    ", segment=" + segment +
                    ", blockingReason='" + blockingReason + '\'' +
                    ", segmentEventType=" + segmentEventType +
                    '}';
        }
    }

    public Event buildTestEvent() {
        return Event.buildTestEvent(this);
    }

    private Event.ScreenType getScreenType() {
        return Event.ScreenType.DEFAULT;
    }
    //endregion

    public interface Listener {

        /**
         * Called on specific player event (see {@link Event.Type} )
         *
         * @param mp    the SRGMediaPlayer that triggers the event
         * @param event corresponding event
         */
        void onMediaPlayerEvent(SRGMediaPlayerController mp, Event event);


    }


    private LicenseStoreDelegate licenseStoreDelegate;

    private Context context;

    private Handler mainHandler;

    private final AudioManager audioManager;

    private State state = State.IDLE;

    private boolean exoPlayerCurrentPlayWhenReady;

    //Used to force keepscreen on even when not playing
    private boolean externalWakeLock = false;

    private boolean muted = false;

    @NonNull
    private final SimpleExoPlayer exoPlayer;
    private final AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private final DefaultTrackSelector trackSelector;

    @Nullable
    private MediaSessionConnector mediaSessionConnector;
    private AudioCapabilities audioCapabilities;
    @NonNull
    private ViewType viewType = ViewType.TYPE_TEXTUREVIEW;
    private SurfaceType surfaceType = SurfaceType.FLAT;
    private View renderingView;
    private Integer playbackState;
    private SegmentList userSegmentList = new SegmentList();
    private Segment segmentBeingSkipped;
    @Nullable
    private Segment currentSegment = null;
    @Nullable
    private SRGMediaPlayerView mediaPlayerView;

    private Uri currentMediaUri = null;

    private String tag;

    private int audioFocusBehaviorFlag = AUDIO_FOCUS_FLAG_PAUSE;

    private OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequestCompat audioFocusRequest;

    /**
     * Listeners registered to this player
     */
    private Set<Listener> eventListeners = Collections.newSetFromMap(new WeakHashMap<>());

    private static Set<Listener> globalEventListeners = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Akamai analytics class.
     */
    @Nullable
    private AkamaiMediaAnalytics akamaiMediaAnalytics;

    @Nullable
    private AkamaiMediaAnalyticsConfiguration akamaiMediaAnalyticsConfiguration;
    private static final String userAgent = "curl/Letterbox_2.0"; // temporarily using curl/ user agent to force subtitles with Akamai beta
    @Nullable
    private DrmConfig drmConfig;
    private int drmRequestDuration;
    private boolean drmRequestOffline;
    private @SRGStreamType
    int currentStreamType;
    private int numberOfDrmRetry = 0;
    private final Timeline.Window window;
    @NonNull
    private final MediaPlayerTimeLine playerTimeLine;

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        return VERSION;
    }

    /**
     * Create a new SRGMediaPlayerController with no DRM support with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context context
     * @param tag     tag to identify this controller
     */
    public SRGMediaPlayerController(Context context, String tag) {
        this(context, tag, null, null, 0L);
    }

    /**
     * Create a new SRGMediaPlayerController with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context   context
     * @param tag       tag to identify this controller
     * @param drmConfig drm configuration null for no DRM support
     */
    public SRGMediaPlayerController(Context context, String tag, @Nullable DrmConfig drmConfig) {
        this(context, tag, drmConfig, null, 0L);
    }

    /**
     * Create a new SRGMediaPlayerController with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context      context
     * @param tag          tag to identify this controller
     * @param drmConfig    drm configuration null for no DRM support
     * @param mediaSession optional mediaSession When set, the caller is responsible for the mediasession lifecycle, when
     *                     null, a media session will be created and connected to the exoplayer
     */
    public SRGMediaPlayerController(Context context, String tag, @Nullable DrmConfig drmConfig, @Nullable MediaSessionCompat mediaSession, long windowOffsetMs) {
        this.context = context;
        this.window = new Timeline.Window();
        this.playerTimeLine = new MediaPlayerTimeLine(windowOffsetMs);
        Looper looper = Looper.myLooper();
        if (looper == null || looper != Looper.getMainLooper()) {
            throw new IllegalStateException("Constructor must be run in main thread");
        }
        this.mainHandler = new Handler(looper, this);
        this.tag = tag;

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        controllerId = ++controllerIdCounter;

        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this.context, this);
        audioCapabilitiesReceiver.register();

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();

        TransferListener DEFAULT_BANDWIDTHMETER = getDefaultBandwidthMeter(context);
        TransferListener listener = debugMode ? new MonitorTransferListener(DEFAULT_BANDWIDTHMETER) : DEFAULT_BANDWIDTHMETER;
        httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                userAgent,
                listener,
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                true);

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        EventLogger eventLogger = new EventLogger(trackSelector);
        drmSessionManager = null;
        UnsupportedDrmException unsupportedDrm = null;
        if (drmConfig != null) {
            this.drmConfig = drmConfig;
            try {
                UUID drmType = drmConfig.getDrmType();
                monitoringDrmCallback = new MonitoringDrmCallback(new HttpMediaDrmCallback(drmConfig.getLicenceUrl(), httpDataSourceFactory));
                drmSessionManager = new DefaultDrmSessionManager<>(drmType,
                        FrameworkMediaDrm.newInstance(drmType),
                        monitoringDrmCallback, null, true);
                drmSessionManager.addListener(mainHandler, this);
                offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(this.drmConfig.getLicenceUrl(),
                        httpDataSourceFactory);
            } catch (UnsupportedDrmException e) {
                fatalError = new SRGMediaPlayerException(null, e, SRGMediaPlayerException.Reason.DRM);
            }
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this.context);
        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl(), drmSessionManager, mainHandler.getLooper());
        exoPlayer.addListener(this);
        exoPlayer.addVideoListener(this);
        exoPlayer.addTextOutput(this);
        exoPlayer.addAnalyticsListener(eventLogger);
        exoPlayer.addMetadataOutput(eventLogger);
        exoPlayerCurrentPlayWhenReady = exoPlayer.getPlayWhenReady();
        audioFocusChangeListener = new OnAudioFocusChangeListener(new WeakReference<>(this));
        audioFocusGranted = false;

        if (mediaSession != null) {
            mediaSessionConnector = null;
        } else {
            try {
                mediaSession = new MediaSessionCompat(context, context.getPackageName());
                mediaSessionConnector = new MediaSessionConnector(mediaSession);
                mediaSessionConnector.setPlayer(exoPlayer);
                mediaSession.setActive(true);
            } catch (Throwable exception) {
                Log.d(TAG, "Unable to create MediaSession", exception);
                // Seems to happen on older devices (Old Google Play Service version?)
                // See https://github.com/SRGSSR/SRGMediaPlayer-Android/issues/25
            }
        }

        licenseStoreDelegate = new FileLicenseStore(context);
    }

    private void applyOfflineLicense(byte[] offlineLicenseKeySetId) {
        if (drmSessionManager != null && offlineLicenseKeySetId != null) {
            drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, offlineLicenseKeySetId);
            if (debugMode) {
                debugPrintLicenseDurationRemaining(offlineLicenseKeySetId);
            }
        }
    }

    private boolean isOfflineLicenseExpired(@NonNull byte[] offlineLicenseKeySetId) {
        if (offlineLicenseHelper != null) {
            try {
                Pair<Long, Long> validity = offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);
                return validity.first <= MINIMUM_DRM_LICENSE_DURATION_SECONDS;
            } catch (DrmSession.DrmSessionException e) {
                Log.e(TAG, "offline license test", e);
                return true;
            }
        }
        return true;
    }

    private void debugPrintLicenseDurationRemaining(byte[] offlineLicenseKeySetId) {
        if (drmConfig != null && offlineLicenseHelper != null) {
            try {
                Pair<Long, Long> validity = offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);
                Log.v(TAG, "DRM validity: license=" + validity.first + "s, playback=" + validity.second + " s");
            } catch (DrmSession.DrmSessionException e) {
                Log.v(TAG, "DRM validity: error", e);
            }
        }
    }

    //region playback control

    /**
     * Try to play a video with a url, you can't replay the current playing video.
     * will throw an exception if you haven't setup a data provider or if the media is not present
     * in the provider.
     * <p/>
     * The corresponding events are triggered when the video loading start and is ready.
     *
     * @param uri        uri of the media
     * @param streamType {@link SRGMediaPlayerController#STREAM_DASH}, {@link SRGMediaPlayerController#STREAM_HLS}, {@link SRGMediaPlayerController#STREAM_HTTP_PROGRESSIVE} or {@link SRGMediaPlayerController#STREAM_LOCAL_FILE}
     * @return true when media is preparing and in the process of being started
     * @throws IllegalArgumentException if segment is not in segment list or uri is null
     */
    public boolean play(@NonNull Uri uri, int streamType) {
        return play(uri, null, streamType);
    }

    /**
     * Try to play a video with a url, you can't replay the current playing video.
     * will throw an exception if you haven't setup a data provider or if the media is not present
     * in the provider.
     * <p/>
     * The corresponding events are triggered when the video loading start and is ready.
     *
     * @param uri             uri of the media
     * @param startPositionMs start position in milliseconds or null to prevent seek
     * @param streamType      {@link SRGMediaPlayerController#STREAM_DASH}, {@link SRGMediaPlayerController#STREAM_HLS}, {@link SRGMediaPlayerController#STREAM_HTTP_PROGRESSIVE} or {@link SRGMediaPlayerController#STREAM_LOCAL_FILE}
     * @return true when media is preparing and in the process of being started
     * @throws IllegalArgumentException if segment is not in segment list or uri is null
     */
    public boolean play(@NonNull Uri uri, Long startPositionMs, @SRGStreamType int streamType) {
        prepare(uri, startPositionMs, streamType, null);
        return start();
    }

    /**
     * Try to play a video with a url and corresponding segments, you can't replay the current playing video.
     * will throw an exception if you haven't setup a data provider or if the media is not present
     * in the provider.
     * <p/>
     * The corresponding events are triggered when the video loading start and is ready.
     *
     * @param uri             uri of the media
     * @param startPositionMs start position in milliseconds relative to uri or segment when given or null to prevent seek
     * @param streamType      {@link SRGMediaPlayerController#STREAM_DASH}, {@link SRGMediaPlayerController#STREAM_HLS}, {@link SRGMediaPlayerController#STREAM_HTTP_PROGRESSIVE} or {@link SRGMediaPlayerController#STREAM_LOCAL_FILE}
     * @param segments        logical segment list
     * @param segment         segment to play, must be in segments list. This is considered a user selected segment (SEGMENT_SELECTED is sent)
     * @throws IllegalArgumentException if segment is not in segment list or uri is null
     */
    public void prepare(@NonNull Uri uri,
                        Long startPositionMs,
                        @SRGStreamType int streamType,
                        List<Segment> segments,
                        Segment segment) {
        // TODO prepare need to be called when player is in idle or release state only?
        //noinspection ConstantConditions
        if (uri == null) {
            throw new IllegalArgumentException("Invalid argument: null uri");
        }

        this.userSegmentList.clear();
        playerTimeLine.update(C.TIME_UNSET, C.TIME_UNSET, false, 0L);
        if (segments != null) {
            this.userSegmentList.addAll(segments);
        }
        if (segment != null && this.userSegmentList.findSegmentById(segment.getIdentifier()) == null) {
            throw new IllegalArgumentException("Unknown segment: " + segment);
        }
        setState(State.PREPARING);

        Long playbackStartPosition = startPositionMs;
        this.currentSegment = null;
        currentStreamType = streamType;
        if (segment != null && segment.getMarkIn().getDate() == null) {
            broadcastEvent(new Event(this, Event.Type.SEGMENT_SELECTED, null, segment));
            playbackStartPosition = (startPositionMs != null ? startPositionMs : 0) + segment.getMarkIn().getPosition();
        }


        Long finalPlaybackStartPosition = playbackStartPosition;
        Runnable prepareViewAndPlayer = () -> prepareViewAndPlayer(uri, streamType, finalPlaybackStartPosition);
        if (drmConfig != null && licenseStoreDelegate != null) {
            downloadOrApplyOfflineLicense(uri, prepareViewAndPlayer, drmConfig);
        } else {
            prepareViewAndPlayer.run();
        }
        broadcastEvent(Event.Type.SEGMENT_LIST_CHANGE);
    }

    private void downloadOrApplyOfflineLicense(@NonNull Uri uri, @NonNull Runnable prepareViewAndPlayer, @NonNull DrmConfig drmConfig) {
        AsyncTask.execute(() -> {
            try {
                DataSource dataSource = httpDataSourceFactory.createDataSource();
                DashManifest dashManifest = DashUtil.loadManifest(dataSource, uri);
                DrmInitData drmInitData = DashUtil.loadDrmInitData(dataSource, dashManifest.getPeriod(0));
                byte[] offlineLicenseKeySetId = licenseStoreDelegate.fetch(drmInitData);
                if (offlineLicenseKeySetId != null && !isOfflineLicenseExpired(offlineLicenseKeySetId)) {
                    Log.v(TAG, "DRM Restored");
                    applyOfflineLicense(offlineLicenseKeySetId);
                    drmRequestOffline = true;
                } else {
                    Log.v(TAG, "Downloading DRM");
                    drmRequestOffline = false;
                    long start = SystemClock.elapsedRealtime();
                    if (offlineLicenseHelper == null) {
                        throw new IllegalStateException("No license helper when trying to download license");
                    } else {
                        byte[] keySet = offlineLicenseHelper.downloadLicense(drmInitData);
                        licenseStoreDelegate.store(drmInitData, keySet);
                        applyOfflineLicense(keySet);
                        drmRequestDuration += SystemClock.elapsedRealtime() - start;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "License Download", e);
            } finally {
                mainHandler.post(prepareViewAndPlayer);
            }
        });
    }

    private void prepareViewAndPlayer(@NonNull Uri uri, @SRGStreamType int streamType, Long playbackStartPosition) {
        try {
            if (mediaPlayerView != null) {
                updateMediaPlayerViewBound();
            }
            prepareExoplayer(uri, playbackStartPosition, streamType);
        } catch (SRGMediaPlayerException e) {
            logE("onUriLoaded", e);
            handlePlayerException(e);
        }
    }

    /**
     * Try to play a video with a url and corresponding segments, you can't replay the current playing video.
     * will throw an exception if you haven't setup a data provider or if the media is not present
     * in the provider.
     * <p/>
     * The corresponding events are triggered when the video loading start and is ready.
     *
     * @param uri             uri of the media
     * @param startPositionMs start position in milliseconds or null to prevent seek
     * @param streamType      {@link SRGMediaPlayerController#STREAM_DASH}, {@link SRGMediaPlayerController#STREAM_HLS}, {@link SRGMediaPlayerController#STREAM_HTTP_PROGRESSIVE} or {@link SRGMediaPlayerController#STREAM_LOCAL_FILE}
     * @param segments        logical segment list
     * @throws IllegalArgumentException if segment is not in segment list or uri is null
     * @ player exception
     */
    public void prepare(@NonNull Uri uri,
                        Long startPositionMs,
                        @SRGStreamType int streamType,
                        List<Segment> segments) {
        prepare(uri, startPositionMs, streamType, segments, null);
    }

    /**
     * Resume playing after a pause call or make the controller start immediately after the preparation phase.
     *
     * @return true if focus audio granted
     */
    public boolean start() {
        if (requestAudioFocus()) {
            exoPlayer.setPlayWhenReady(true);
            return true;
        } else {
            logV("Audio focus audioFocusRequest failed");
            return false;
        }
    }

    /**
     * Pause the current media or prevent it from starting immediately if controller in preparation phase.
     */
    public void pause() {
        resetAudioFocusResume();
        abandonAudioFocus();
        exoPlayer.setPlayWhenReady(false);
    }

    /**
     * <pre>
     * Try to seek to the provided Mark.
     *
     * If mark has a non null date, it would try to seek at the date time position.
     * The date time position would be more precise if the stream has a start time value.
     *
     * Otherwise it would seek to the mark position.
     *  </pre>
     *
     * @param mark with a date or position to seek to
     */
    public void seekTo(@NonNull Mark mark) {
        long position = mark.getPosition();
        if (mark.getDate() != null) {
            // Convert to a player position
            position = playerTimeLine.getPosition(mark.getDate().getTime());
        }
        seekTo(position);
    }

    /**
     * <p>
     * Try to seek to the provided position, if this position is not reachable
     * will throw an exception.
     * Use it after calling play or prepare, otherwise it will be erased with the position given in play or prepare methods.
     * </p>
     * <h2>Live stream</h2>
     * <p>
     * When playing a live stream, to seek at live position use {@link SRGMediaPlayerController#seekToDefaultPosition()}
     * A value of 0..duration represents the relative position in the live stream.
     * </p>
     *
     * @param positionMs
     */
    public void seekTo(long positionMs) {
        Segment blockedSegment = getBlockedSegment(positionMs);
        if (blockedSegment != null) {
            seekEndOfBlockedSegment(blockedSegment);
        } else {
            broadcastEvent(Event.Type.WILL_SEEK);
            exoPlayer.seekTo(positionMs);
        }
    }

    /**
     * Seek to the live edge if a live stream, otherwise at the start of the stream
     */
    public void seekToDefaultPosition() {
        exoPlayer.seekToDefaultPosition(exoPlayer.getCurrentWindowIndex());
    }

    //endregion

    //region initialisation

    private void prepareExoplayer(@NonNull Uri videoUri, @Nullable Long playbackStartPosition, int streamType) throws
            SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri + " (" + streamType + ")");
        setupAkamaiQos(videoUri);
        try {
            if (this.currentMediaUri != null && this.currentMediaUri.equals(videoUri)) {
                return;
            }
            this.currentMediaUri = videoUri;
            this.currentStreamType = streamType;

            DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                    userAgent,
                    getDefaultBandwidthMeter(context),
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true);

            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, getDefaultBandwidthMeter(context), httpDataSourceFactory);

            MediaSource mediaSource;

            switch (streamType) {
                case STREAM_DASH:
                    // Use DefaultDashChunkSource with workaround that don't crash the application if problem during manifest parsing
                    // https://github.com/google/ExoPlayer/issues/2795
                    mediaSource = new DashMediaSource.Factory(new ch.srg.mediaplayer.DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                            .createMediaSource(videoUri);
                    break;
                case STREAM_HLS:
                    mediaSource = new HlsMediaSource.Factory(new DefaultHlsDataSourceFactory(dataSourceFactory))
                            .createMediaSource(videoUri);
                    break;
                case STREAM_HTTP_PROGRESSIVE:
                    mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(videoUri);
                    break;
                case STREAM_LOCAL_FILE:
                    FileDataSourceFactory fileDataSourceFactory = new FileDataSourceFactory();
                    mediaSource = new ProgressiveMediaSource.Factory(fileDataSourceFactory)
                            .createMediaSource(videoUri);
                    break;
                default:
                    throw new IllegalStateException("Invalid source type: " + streamType);
            }
            exoPlayer.prepare(mediaSource);
            if (playbackStartPosition != null) {
                try {
                    seekTo(playbackStartPosition);
                    checkSegmentChange(playbackStartPosition);
                } catch (IllegalStateException exception) {
                    Log.w(TAG, "Invalid initial playback position", exception);
                }
            }
            lastPeriodicUpdate = null;
            playbackActuallyStarted = false;
        } catch (Exception e) {
            release();
            throw new SRGMediaPlayerException(null, e, SRGMediaPlayerException.Reason.EXOPLAYER);
        }
    }

    private void setupAkamaiQos(@NonNull Uri videoUri) {
        if (akamaiMediaAnalyticsConfiguration != null) {
            akamaiMediaAnalytics = new AkamaiMediaAnalytics(context, akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsConfigUrl());
            akamaiMediaAnalytics.disableLocationSupport();
            akamaiMediaAnalytics.setStreamURL(videoUri.toString(), true);
            Iterable<? extends Pair<String, String>> akamaiMediaAnalyticsDataSet = akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsDataSet();
            for (Pair<String, String> dataPair : akamaiMediaAnalyticsDataSet) {
                akamaiMediaAnalytics.setData(dataPair.first, dataPair.second);
            }
            akamaiMediaAnalytics.setData("viewerid", akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsViewerId());
            if (debugMode) {
                akamaiMediaAnalytics.enableDebugLogging();
            }
            akamaiMediaAnalytics.handleSessionInit(new PluginCallBacks() {
                @Override
                public float streamHeadPosition() {
                    // Use last known position to workaround threading issues
                    return lastPeriodicUpdate != null ? lastPeriodicUpdate / 1000f : 0;
                }

                @Override
                public long bytesLoaded() {
                    return 0;
                }

                @Override
                public int droppedFrames() {
                    return 0;
                }
            });
        }
    }

    //endregion

    //region release

    /**
     * Release the current player. Once the player is released you have to create a new player
     * if you want to play a new video.
     * <p>
     * Remark: The player come in RELEASED state, the resources may not be released immediately (managed by exoplayer)
     */
    public void release() {
        broadcastEvent(Event.Type.MEDIA_STOPPED);
        doAkamaiAnalytics((ma) -> {
            ma.handleVisit();
            ma.handleSessionCleanup();
        });
        doRelease();
    }

    private interface AnalyticsRunner {
        void run(AkamaiMediaAnalytics ma);
    }

    private void doAkamaiAnalytics(AnalyticsRunner runner) {
        if (akamaiMediaAnalytics != null) {
            try {
                runner.run(akamaiMediaAnalytics);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Called by user release or when the media is at end
     */
    private void doRelease() {
        if (this.state != State.RELEASED) {
            numberOfDrmRetry = 0;
            if (mediaPlayerView != null) {
                unbindFromMediaPlayerView(mediaPlayerView);
            }
            setState(State.RELEASED);
            abandonAudioFocus();
            releaseExoplayer();
            unregisterAllEventListeners();
            stopPeriodicUpdate();
            if (offlineLicenseHelper != null) {
                offlineLicenseHelper.release();
            }
        }
    }

    private void releaseExoplayer() {
        exoPlayer.stop();
        // Done after stop to be sure that no event listener are called.
        if (mediaSessionConnector != null) {
            // Sets the player to be connected to the media session. Must be called on the same thread that is used to access the player.
            mediaSessionConnector.setPlayer(null);
            mediaSessionConnector.mediaSession.setActive(false);
            mediaSessionConnector.mediaSession.release();
        }

        exoPlayer.release();
        currentMediaUri = null;
        audioCapabilitiesReceiver.unregister();
    }

    //endregion

    //region volume control

    public void mute() {
        setMute(true);
    }

    public void unmute() {
        setMute(false);
    }

    public void setMute(boolean muted) {
        if (this.muted != muted) {
            this.muted = muted;
            setVolume(muted ? 0f : 1f);
        }
    }

    public void setVolume(float volume) {
        exoPlayer.setVolume(volume);
    }

    //endregion

    //region period update
    @Nullable
    private Long lastPeriodicUpdate;

    private void startPeriodicUpdateThreadIfNecessary() {
        schedulePeriodUpdate();
    }

    private void stopPeriodicUpdate() {
        logV("Stopping periodic update thread: " + mainHandler);
        if (mainHandler != null) {
            mainHandler.removeMessages(MSG_PERIODIC_UPDATE);
        }
    }

    private void schedulePeriodUpdate() {
        Handler handler = this.mainHandler;
        if (handler != null) {
            handler.removeMessages(MSG_PERIODIC_UPDATE);
            handler.sendMessageDelayed(handler.obtainMessage(MSG_PERIODIC_UPDATE), UPDATE_PERIOD);
        }
    }

    @Override
    public boolean handleMessage(final Message msg) {
        if (!isReleased()) {
            periodicUpdate();
            schedulePeriodUpdate();
        }
        return false;
    }

    private void periodicUpdate() {
        long currentPosition = exoPlayer.getCurrentPosition();
        long mediaDuration = getMediaDuration();
        if (lastPeriodicUpdate == null || currentPosition != lastPeriodicUpdate) {
            if (!isLive() && mediaDuration != C.TIME_UNSET && currentPosition > mediaDuration) {
                Log.w(TAG,
                        "Force EoF due to longer subtitle or audio track. position (" + currentPosition + " ms) > duration (" + mediaDuration + " ms).");
                onPlayerStateChanged(false, ExoPlayer.STATE_ENDED);
            }
            if (lastPeriodicUpdate != null && !playbackActuallyStarted && exoPlayer.getPlayWhenReady()) {
                playbackActuallyStarted = true;
                broadcastEvent(Event.Type.PLAYBACK_ACTUALLY_STARTED);
            }
            if (!userSegmentList.isEmpty()) {
                checkSegmentChange(currentPosition);
            }
            lastPeriodicUpdate = currentPosition;
        }
    }
    //endregion

    //region segments

    /**
     * CurrentSegment and  block segment MarkRange will change during playback
     *
     * @param mediaPosition
     */
    private void checkSegmentChange(long mediaPosition) {
        if (isReleased() && mediaPosition != UNKNOWN_TIME) {
            return;
        }

        if (mediaPosition != UNKNOWN_TIME) {
            // Player relative segment
            Segment blockedSegment = getBlockedSegment(mediaPosition);
            Segment newSegment = getSegment(mediaPosition);

            if (blockedSegment != null) {
                if (!Segment.equalIdentifier(blockedSegment, segmentBeingSkipped)) {
                    Log.v("SegmentTest", "Skipping over " + blockedSegment.getIdentifier());
                    segmentBeingSkipped = blockedSegment;
                    seekEndOfBlockedSegment(blockedSegment);
                }
            } else {
                segmentBeingSkipped = null;
                if (!Segment.equalIdentifier(currentSegment, newSegment)) {
                    if (currentSegment == null) {
                        broadcastSegmentEvent(Event.Type.SEGMENT_START, newSegment);
                    } else if (newSegment == null) {
                        broadcastSegmentEvent(Event.Type.SEGMENT_END, null);
                    } else {
                        broadcastSegmentEvent(Event.Type.SEGMENT_SWITCH, newSegment);
                    }
                    currentSegment = newSegment;
                }
            }
        }
    }

    @NonNull
    public SegmentList getSegments() {
        return getPlayerSegmentList();
    }

    private void seekEndOfBlockedSegment(Segment segment) {
        broadcastBlockedSegmentEvent(Event.Type.SEGMENT_SKIPPED_BLOCKED, segment);
        seekTo(segment.getMarkOut());
    }

    @Nullable
    public Segment getSegment(long time) {
        SegmentList localSubDivision = getPlayerSegmentList();
        if (currentSegment != null && localSubDivision.findSegmentById(currentSegment.getIdentifier()) != null
                && time >= currentSegment.getMarkIn().getPosition() - SEGMENT_HYSTERESIS_MS
                && time < currentSegment.getMarkOut().getPosition()) {
            return currentSegment;
        }
        return localSubDivision.findSegmentAtPosition(time);
    }

    @Nullable
    public Segment getCurrentSegment() {
        return currentSegment;
    }

    @Nullable
    private Segment getBlockedSegment(long time) {
        SegmentList localSubDivision = getPlayerSegmentList();
        return localSubDivision.findBlockedSegmentAtPosition(time);
    }

    public void setSegmentList(@NonNull List<Segment> segmentList) {
        userSegmentList.clear();
        userSegmentList.addAll(segmentList);
        checkSegmentChange(getMediaPosition());
        broadcastEvent(Event.Type.SEGMENT_LIST_CHANGE);
    }

    /**
     * Convert user definition segment to a Player time reference segment markIn and markOut
     */
    private SegmentList getPlayerSegmentList() {
        SegmentList playerTimeSegmentList = new SegmentList(userSegmentList.size());
        for (Segment segment : userSegmentList) {
            if (segment.getMarkIn().getDate() != null && segment.getMarkOut().getDate() != null) {
                long markIn = playerTimeLine.getPosition(segment.getMarkIn().getDate().getTime());
                long markOut = playerTimeLine.getPosition(segment.getMarkOut().getDate().getTime());
                Segment segmentPlayer = new Segment(
                        segment.getIdentifier(),
                        segment.getTitle(), segment.getDescription(), segment.getImageUrl(), segment.getBlockingReason(), markIn, markOut, segment.getDuration(), segment.isDisplayable(), segment.isLive(), segment.is360());
                playerTimeSegmentList.add(segmentPlayer);
            } else {
                playerTimeSegmentList.add(segment);
            }
        }
        return playerTimeSegmentList;
    }

    private void broadcastSegmentEvent(Event.Type type, Segment segment) {
        broadcastEvent(new Event(this, type, null, segment));
    }

    @SuppressWarnings("SameParameterValue")
    private void broadcastBlockedSegmentEvent(Event.Type type, Segment segment) {
        broadcastEvent(new Event(this, type, null, segment, segment.getBlockingReason()));
    }

    /**
     * @param segment a player segment {@link SRGMediaPlayerController#getPlayerSegmentList()}
     * @return true if it changed segment
     */
    private boolean switchToSegment(Segment segment) {
        if (segment.getMarkIn().getDate() != null) {
            long markInTime = segment.getMarkIn().getDate().getTime();
            if (markInTime > playerTimeLine.getStartTimeMs() + playerTimeLine.getDurationMs())
                return false;
        }
        broadcastSegmentEvent(Event.Type.SEGMENT_SELECTED, segment);
        seekTo(segment.getMarkIn());
        return true;
    }

    /**
     * Start playing a segment in the segment list. This is considered a user request.
     * This will seek to the beginning of the segment whether or not we are currently in this
     * segment.
     * This does not affect playing state.
     *
     * @param identifier segment identifier
     * @return true if segment found and switch occurred
     */
    public boolean switchToSegment(String identifier) {
        Segment segment = getPlayerSegmentList().findSegmentById(identifier);
        if (segment != null) {
            return switchToSegment(segment);
        }
        return false;
    }

    //endregion

    //region focus management
    public void keepScreenOn(boolean lock) {
        externalWakeLock = lock;
        manageKeepScreenOn();
    }


    private void manageKeepScreenOn() {
        int playbackState = exoPlayer.getPlaybackState();
        boolean playWhenReady = exoPlayer.getPlayWhenReady();
        final boolean lock = externalWakeLock ||
                ((playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) && playWhenReady);
        logV("Scheduling change keepScreenOn currently attached mediaPlayerView to " + lock + state + " " + playbackState + " " + playWhenReady);
        if (currentViewKeepScreenOn != lock) {
            currentViewKeepScreenOn = lock;
            if (mediaPlayerView != null) {
                mediaPlayerView.setKeepScreenOn(lock);
                logV("Changing keepScreenOn for currently attached mediaPlayerView to " + lock + "[" + mediaPlayerView + "]");
            } else {
                logV("Cannot change keepScreenOn, no mediaPlayerView attached");
            }
        }
    }

    //endregion

    //region logs
    private void logV(String msg) {
        if (isDebugMode()) {
            Log.v(TAG, getControllerId() + " " + msg);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void logE(String msg) {
        if (isDebugMode()) {
            Log.e(TAG, getControllerId() + " " + msg);
        }
    }

    private void logE(String msg, Exception e) {
        if (isDebugMode()) {
            Log.e(TAG, getControllerId() + " " + msg, e);
        }
    }
    //endregion

    private void handlePlayerException(SRGMediaPlayerException e) {
        logE("exception occurred", e);
        broadcastFatalError(e, false);
        doRelease();
    }

    @NonNull
    public SimpleExoPlayer getExoPlayer() {
        return exoPlayer;
    }

    @Nullable
    public MediaSessionCompat.Token getMediaSessionToken() {
        if (mediaSessionConnector != null) {
            return mediaSessionConnector.mediaSession.getSessionToken();
        }
        return null;
    }

    @Nullable
    @Deprecated
    public MediaSessionCompat getMediaSession() {
        return mediaSessionConnector != null ? mediaSessionConnector.mediaSession : null;
    }


    /**
     * Attach a MediaPlayerView to the controller.
     * Also ink the overlayController to the MediaPlayerView.
     *
     * @param newView player view
     * @throws IllegalStateException if a player view is already attached to this controller
     */
    public void bindToMediaPlayerView(@NonNull SRGMediaPlayerView newView) {
        if (mediaPlayerView != null) {
            unbindFromMediaPlayerView(mediaPlayerView);
        }
        mediaPlayerView = newView;
        newView.setCues(Collections.emptyList());
        updateMediaPlayerViewBound();
        manageKeepScreenOn();
    }

    @Nullable
    public SRGMediaPlayerView getMediaPlayerView() {
        return mediaPlayerView;
    }

    private void updateMediaPlayerViewBound() {
        final SRGMediaPlayerView mediaPlayerView = this.mediaPlayerView;
        if (mediaPlayerView != null) {
            if (!canRenderInView(mediaPlayerView.getVideoRenderingView())) {
                // We need to create a new rendering view.
                createRenderingView(mediaPlayerView.getContext());
                logV(renderingView + "binding, creating rendering view" + mediaPlayerView);
            } else {
                renderingView = mediaPlayerView.getVideoRenderingView();
                try {
                    logV("binding, bindRenderingViewInUiThread " + mediaPlayerView);
                    bindRenderingViewInUiThread();
                } catch (SRGMediaPlayerException e) {
                    logE("Error binding view", e);
                }
            }
        } else {
            // mediaPlayerView null, just unbind delegate
            unbindRenderingView();
        }
    }

    private void bindRenderingViewInUiThread() throws SRGMediaPlayerException {
        SRGMediaPlayerView mediaPlayerView = this.mediaPlayerView;
        if (mediaPlayerView == null ||
                !canRenderInView(mediaPlayerView.getVideoRenderingView())) {
            throw new SRGMediaPlayerException("ExoPlayer cannot render video in a "
                    + mediaPlayerView, null, SRGMediaPlayerException.Reason.VIEW);
        }
        currentViewKeepScreenOn = mediaPlayerView.getKeepScreenOn();
        if (renderingView instanceof SphericalSurfaceView) {
            SphericalSurfaceView sphericalSurfaceView = (SphericalSurfaceView) renderingView;
            sphericalSurfaceView.setVideoComponent(exoPlayer.getVideoComponent());
        } else if (renderingView instanceof SurfaceView) {
            exoPlayer.setVideoSurfaceView((SurfaceView) renderingView);
            mediaPlayerView.setScaleModeListener(this::onScaleModeChanged);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.setVideoTextureView((TextureView) renderingView);
        }
        broadcastEvent(Event.Type.DID_BIND_TO_PLAYER_VIEW);
    }

    public void onScaleModeChanged(SRGMediaPlayerView mediaPlayerView, SRGMediaPlayerView.ScaleMode scaleMode) {
        switch (scaleMode) {
            case CENTER_INSIDE:
            case TOP_INSIDE:
                exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                break;
            case CENTER_CROP:
            case FIT:
                exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                break;
        }
    }


    /**
     * Clear the current mediaPlayer, unbind the delegate and the overlayController
     *
     * @param playerView video container to unbind from.
     */
    public void unbindFromMediaPlayerView(SRGMediaPlayerView playerView) {
        if (mediaPlayerView == playerView) {
            unbindRenderingView();
            if (mediaPlayerView != null) {
                playerView.setScaleModeListener(null);
            }
            mediaPlayerView = null;
            broadcastEvent(Event.Type.DID_UNBIND_FROM_PLAYER_VIEW);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean canRenderInView(View view) {
        switch (viewType) {
            case TYPE_TEXTUREVIEW:
                return view instanceof TextureView;
            case TYPE_SURFACEVIEW:
                return surfaceType == SurfaceType.SPHERICAL ?
                        view instanceof SphericalSurfaceView :
                        !(view instanceof SphericalSurfaceView) && view instanceof SurfaceView;
            default:
                return false;
        }
    }

    private void createSurfaceView(final Context parentContext) {
        switch (surfaceType) {
            case FLAT:
                createFlatSurfaceView(parentContext);
                break;
            case SPHERICAL:
                createSphericalSurfaceView(parentContext);
                break;
            default:
                throw new IllegalStateException("Unsupported surface type: " + surfaceType);
        }
    }

    private void createFlatSurfaceView(final Context parentContext) {
        SurfaceView surfaceView = new SurfaceView(parentContext);
        renderingView = surfaceView;
        if (mediaPlayerView != null) {
            mediaPlayerView.setVideoRenderingView(surfaceView);
        }
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            // It is very important to check renderingView type as it may have changed (do not listen to lint here!)
            private boolean isCurrent(SurfaceHolder holder) {
                return renderingView instanceof SurfaceView && ((SurfaceView) renderingView).getHolder() == holder;
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.v(TAG, renderingView + "binding, surfaceCreated" + mediaPlayerView);
                try {
                    if (isCurrent(holder)) {
                        bindRenderingViewInUiThread();
                    } else {
                        Log.d(TAG, "Surface created, but media player delegate retired");
                    }
                } catch (SRGMediaPlayerException e) {
                    Log.d(TAG, "Error binding view", e);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.v(TAG, renderingView + "binding, surfaceChanged" + mediaPlayerView);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.v(TAG, renderingView + "binding, surfaceDestroyed" + mediaPlayerView);
            }
        });
    }

    private void createSphericalSurfaceView(final Context parentContext) {
        SphericalSurfaceView sphericalSurfaceView = new SphericalSurfaceView(parentContext);
        sphericalSurfaceView.setDefaultStereoMode(C.STEREO_MODE_MONO);
        renderingView = sphericalSurfaceView;
        if (mediaPlayerView != null) {
            Log.v(TAG, "binding, setVideoRenderingView " + mediaPlayerView);
            mediaPlayerView.setVideoRenderingView(sphericalSurfaceView);
        }
        try {
            bindRenderingViewInUiThread();
        } catch (SRGMediaPlayerException e) {
            Log.e(TAG, "SphericalSurfaceView binding", e);
        }
    }

    private void createTextureView(final Context parentContext) {
        TextureView textureView = new TextureView(parentContext);
        renderingView = textureView;
        if (mediaPlayerView != null) {
            Log.v(TAG, "binding, setVideoRenderingView " + mediaPlayerView);
            mediaPlayerView.setVideoRenderingView(textureView);
        }

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            // It is very important to check renderingView type as it may have changed (do not listen to lint here!)
            boolean isCurrent(SurfaceTexture surfaceTexture) {
                return renderingView instanceof TextureView && ((TextureView) renderingView).getSurfaceTexture() == surfaceTexture;
            }

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Log.v(TAG, renderingView + "binding, surfaceTextureAvailable" + mediaPlayerView);
                if (isCurrent(surfaceTexture)) {
                    try {
                        bindRenderingViewInUiThread();
                    } catch (SRGMediaPlayerException e) {
                        Log.d(TAG, "Error binding view", e);
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
    }

    private void createRenderingView(final Context parentContext) {
        Log.d(TAG, "createRenderingView " + viewType + " " + surfaceType);
        switch (viewType) {
            case TYPE_TEXTUREVIEW:
                createTextureView(parentContext);
                break;
            case TYPE_SURFACEVIEW:
                createSurfaceView(parentContext);
                break;
            default:
                throw new IllegalStateException("Unsupported view type: " + viewType);
        }
    }

    private void unbindRenderingView() {
        if (renderingView instanceof SphericalSurfaceView) {
            SphericalSurfaceView surfaceView = (SphericalSurfaceView) renderingView;
            surfaceView.setVideoComponent(null);
        } else if (renderingView instanceof SurfaceView) {
            exoPlayer.clearVideoSurfaceView((SurfaceView) renderingView);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.clearVideoTextureView((TextureView) renderingView);
        }
        renderingView = null;
    }

    /**
     * Warning texture view not supported to play DRM content.
     *
     * @param viewType view type
     */
    public void setViewType(@NonNull ViewType viewType) {
        if (debugMode && drmConfig != null && viewType == ViewType.TYPE_TEXTUREVIEW) {
            Log.w(TAG, "Texture view does not support DRM");
        }
        this.viewType = viewType;
    }

    /**
     * Change surface type to SPHERICAL to play 360° content or FLAT for regular content
     *
     * @param surfaceType surface type
     */
    public void setSurfaceType(@NonNull SurfaceType surfaceType) {
        if (debugMode && viewType == ViewType.TYPE_TEXTUREVIEW) {
            Log.w(TAG, "Surface type is only available for Surface view");
        }
        this.surfaceType = surfaceType;
    }

    private void setState(State state) {
        if (this.state != state) {
            this.state = state;
            broadcastEvent(Event.buildStateEvent(this));
        }
    }

    /**
     * Register a listener on events fired by this SRGMediaPlayerController. WARNING, The listener
     * is stored in a Weak set. If you use a dedicated object, make sure to keep a reference.
     *
     * @param listener the listener.
     */
    public void registerEventListener(Listener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    /**
     * Unregister a listener from this SRGMediaPlayerController.
     *
     * @param listener the listener.
     */
    public void unregisterEventListener(Listener listener) {
        if (listener != null) {
            eventListeners.remove(listener);
        }
    }

    private void unregisterAllEventListeners() {
        eventListeners.clear();
    }

    /**
     * Register a global listener on events fired by all (current and future) SRGMediaPlayerControllers.
     *
     * @param listener the global listener.
     * @return true if the listener was registered.
     */
    public static boolean registerGlobalEventListener(Listener listener) {
        return globalEventListeners.add(listener);
    }

    /**
     * Unregister a global listener from all (current and future) SRGMediaPlayerControllers.
     *
     * @param listener the global listener.
     * @return true if the listener was previously registered and successfully unregistered.
     */
    public static boolean unregisterGlobalEventListener(Listener listener) {
        return globalEventListeners.remove(listener);
    }

    private void broadcastFatalError(SRGMediaPlayerException e, boolean override) {
        if (override || fatalError == null) {
            this.fatalError = e;
            broadcastEvent(Event.buildErrorEvent(this, true, e));
        }
    }

    private void broadcastEvent(Event.Type eventType) {
        broadcastEvent(Event.buildEvent(this, eventType));
    }

    private void broadcastEvent(final Event event) {
        int count = SRGMediaPlayerController.globalEventListeners.size() + this.eventListeners.size();
        final Set<Listener> eventListeners = new HashSet<>(count);
        Log.d(TAG, "Posting event: " + event + " to " + count);
        eventListeners.addAll(globalEventListeners);
        eventListeners.addAll(this.eventListeners);

        if (isDebugMode() && Looper.getMainLooper() != Looper.myLooper()) {
            throw new IllegalStateException("expected main thread");
        }
        for (Listener listener : eventListeners) {
            listener.onMediaPlayerEvent(SRGMediaPlayerController.this, event);
        }
    }

    public Context getContext() {
        return context;
    }

    //region audio focus management

    void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                handleAudioFocusLoss(false, false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                handleAudioFocusLoss(true, false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                handleAudioFocusLoss(true, true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                handleAudioFocusGain();
                break;
        }
    }

    private void handleAudioFocusGain() {
        audioFocusGranted = true;
        if (duckedBecauseTransientFocusLoss) {
            unmute();
        }
        if (pausedBecauseFocusLoss && ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_AUTO_RESTART) != 0
                || pausedBecauseTransientFocusLoss)) {
            exoPlayer.setPlayWhenReady(true);
        }
        if (mutedBecauseFocusLoss) {
            unmute();
        }
        resetAudioFocusResume();
    }

    private void resetAudioFocusResume() {
        pausedBecauseFocusLoss = false;
        pausedBecauseTransientFocusLoss = false;
        duckedBecauseTransientFocusLoss = false;
    }

    private boolean hasLostAudioFocus() {
        return pausedBecauseFocusLoss ||
                pausedBecauseTransientFocusLoss ||
                duckedBecauseTransientFocusLoss;
    }

    private void handleAudioFocusLoss(boolean transientFocus, boolean mayDuck) {
        audioFocusGranted = false;
        boolean playing = isPlaying();
        if (mayDuck && (audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_DUCK) != 0) {
            if (!muted) {
                this.duckedBecauseTransientFocusLoss = playing;
                // We could also actually duck. But this is fine for our usage afaics.
                mute();
            }
        } else if ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_PAUSE) != 0) {
            pausedBecauseFocusLoss = playing;
            pausedBecauseTransientFocusLoss = playing && transientFocus;
            exoPlayer.setPlayWhenReady(false);
        } else if ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_MUTE) != 0) {
            if (!muted) {
                mutedBecauseFocusLoss = playing;
                mute();
            }
        }
    }


    /**
     * From Android O, if another app request audio focus with AUDIOFOCUS_GAIN, the audio focus never gain automatically.
     * If the focus is transient lost, for example by a phone call, the audio focus can be gain automatically at the end of the call.
     */
    private boolean requestAudioFocus() {
        if (audioFocusBehaviorFlag == 0 || audioFocusGranted) {
            return true;
        } else {
            logV("Request audio focus");
            if (audioFocusRequest == null) {
                AudioAttributesCompat audioAttributes = new AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build();
                audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
            }
            int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusGranted = true;
                return true;
            } else {
                logE("Could not get audio focus granted...");
                return false;
            }
        }
    }

    private void abandonAudioFocus() {
        if (audioFocusBehaviorFlag != 0 && audioFocusRequest != null) {
            logV("Abandon audio focus");
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
            audioFocusGranted = false;
        }
    }

    private static class OnAudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
        private final WeakReference<SRGMediaPlayerController> playerReference;

        OnAudioFocusChangeListener(WeakReference<SRGMediaPlayerController> playerReference) {
            this.playerReference = playerReference;
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            SRGMediaPlayerController player = playerReference.get();
            if (player != null) {
                player.logV("audio focus changed: " + focusChange);
                player.handleAudioFocusChange(focusChange);
            }
        }
    }

    public void setAudioFocusBehaviorFlag(int audioFocusBehaviorFlag) {
        this.audioFocusBehaviorFlag = audioFocusBehaviorFlag;
    }

    //endregion

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /*package*/
    static Event createTestEvent(Event.Type eventType, SRGMediaPlayerController controller, SRGMediaPlayerException
            eventException) {
        return new Event(controller, eventType, eventException);
    }

    public String getControllerId() {
        return String.valueOf(controllerId);
    }

    public String getMediaSessionId() {
        return getControllerId();
    }

    @NonNull
    public MediaPlayerTimeLine getPlayerTimeLine() {
        return playerTimeLine;
    }

    //region player values

    /**
     * Method used to handle a bug with the ExoPlayer, the current seek behaviour of this player
     * doesn't ensure the precision of the seek.
     *
     * @return true if current media position match to seek target
     */
    @Deprecated
    public boolean isSeekPending() {
        return true;
    }


    /**
     * Check if the player is released, this method can help you to determine if you need to
     * create a new player instance.
     *
     * @return true when player is released and cannot be reused
     */
    public boolean isReleased() {
        return state == State.RELEASED;
    }

    public boolean isLive() {
        return exoPlayer.isCurrentWindowDynamic();
    }

    public State getState() {
        return state;
    }

    public boolean isPlaying() {
        return state == State.READY && exoPlayer.getPlayWhenReady();
    }

    public boolean getPlayWhenReady() {
        return exoPlayer.getPlayWhenReady();
    }

    /**
     * Return the current Url played.
     */
    @Nullable
    public Uri getMediaUri() {
        return currentMediaUri;
    }


    public long getMediaPosition() {
        return exoPlayer.getCurrentPosition();
    }

    /**
     * Media time position
     * Live stream [startTime,startTime+duration]
     * Stream [0,duration]
     *
     * @return media time position.
     */
    public long getCurrentTimePosition() {
        return playerTimeLine.getTime(exoPlayer.getCurrentPosition());
    }

    /**
     * @return Media duration in milliseconds, {@link C#TIME_UNSET} if unknown
     */
    public long getMediaDuration() {
        return playerTimeLine.getDurationMs();
    }

    /**
     * @return null if no DrmConfig was set
     */
    @Nullable
    public DrmConfig getDrmConfig() {
        return drmConfig;
    }

    public long getBufferPosition() {
        return exoPlayer.getBufferedPosition();
    }

    public int getBufferPercentage() {
        return exoPlayer.getBufferedPercentage();
    }

    public boolean isBoundToMediaPlayerView() {
        return mediaPlayerView != null;
    }

    public boolean isRemote() {
        return false;
    }

    /**
     * @return loading state (preparing, buffering or seek pending)
     */
    public boolean isLoading() {
        return getState() == State.PREPARING || getState() == State.BUFFERING;
    }

    public boolean isFirstFrameRendered() {
        return firstFrameRendered;
    }

    public boolean isPlaybackActuallyStarted() {
        return playbackActuallyStarted;
    }

    public static boolean isDrmSupported() {
        return true;
    }

    public int getDrmRequestDuration() {
        return drmRequestDuration;
    }

    public boolean isDrmRequestOffline() {
        return drmRequestOffline;
    }

    /**
     * Get Total bandwidth of currently playing stream.
     *
     * @return current bandwidth in bits/seconds or null if not available
     */
    public Long getCurrentBandwidth() {
        Format videoFormat = exoPlayer.getVideoFormat();
        Format audioFormat = exoPlayer.getAudioFormat();
        int videoBandwidth = videoFormat != null && videoFormat.bitrate != Format.NO_VALUE ? videoFormat.bitrate : 0;
        int audioBandwidth = audioFormat != null && audioFormat.bitrate != Format.NO_VALUE ? audioFormat.bitrate : 0;
        long bandwidth = videoBandwidth + audioBandwidth;
        return bandwidth > 0 ? bandwidth : null;
    }

    //endregion

    //region setter

    /**
     * Force use specific quality (when supported). Represented by bandwidth.
     * Can be 0 to force lowest quality or Integer.MAX for highest for instance.
     * <p>
     * Currently only supported partially to limit the maximum quality.
     * The player will be adapt between 0 and the quality selected.
     *
     * @param quality bandwidth quality in bits/sec or null to disable
     */
    public void setQualityOverride(Long quality) {
        DefaultTrackSelector.ParametersBuilder parameters = trackSelector.buildUponParameters();
        if (quality == null) {
            trackSelector.setParameters(parameters.setMaxVideoBitrate(Integer.MAX_VALUE).setForceLowestBitrate(false));
        } else {
            if (quality == 0) {
                trackSelector.setParameters(parameters.setForceLowestBitrate(true));
            } else {
                trackSelector.setParameters(parameters.setMaxVideoBitrate(quality.intValue()).setForceLowestBitrate(false));
            }
        }
    }

    /**
     * Use a specific quality when an estimate is not available (when supported).
     * Represented by bandwidth. Typically used to force a better quality during startup.
     * Can be 0 to force lowest quality or Integer.MAX for highest for instance.
     * <p>
     * WARNING: Not supported at the moment.
     *
     * @param qualityDefault bandwidth quality in bits/sec or null to disable
     */
    public void setQualityDefault(Long qualityDefault) {
    }

    //endregion

    //region audio/subtitle track management

    public boolean hasVideoTrack() {
        return hasTrackOfType(C.TRACK_TYPE_VIDEO);
    }

    public boolean hasAudioTrack() {
        return hasTrackOfType(C.TRACK_TYPE_AUDIO);
    }

    private boolean hasTrackOfType(int trackType) {
        TrackSelectionArray currentTrackSelections = exoPlayer.getCurrentTrackSelections();
        for (int i = 0; i < currentTrackSelections.length; i++) {
            if (exoPlayer.getRendererType(i) == trackType) {
                if (currentTrackSelections.get(i) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @AnyThread
    public Throwable getFatalError() {
        return fatalError;
    }

    @NonNull
    public List<AudioTrack> getAudioTrackList() {
        List<AudioTrack> result = new ArrayList<>();
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        int audioTrackRendererId = getAudioTrackRendererId();
        if (mappedTrackInfo != null && audioTrackRendererId != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(audioTrackRendererId);
            for (int trackGroupIndex = 0; trackGroupIndex < trackGroups.length; trackGroupIndex++) {
                TrackGroup trackGroup = trackGroups.get(trackGroupIndex);
                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                    AudioTrack audioTrack = AudioTrack.createFrom(trackGroup, trackGroupIndex, trackIndex);
                    if (audioTrack != null) {
                        result.add(audioTrack);
                        break;
                    }
                }
            }
        }
        if (debugMode && (result.isEmpty())) {
            return Arrays.asList(
                    new AudioTrack(0, 0, "en", null, "English"),
                    new AudioTrack(0, 1, "fr", null, "French"),
                    new AudioTrack(0, 2, "ar", null, "عربي"),
                    new AudioTrack(0, 3, "ch", null, "中文"));
        } else {
            return result;
        }
    }

    /**
     * If track is null, no sound is playing during playback.
     *
     * @param track one track returned by {@link #getSubtitleTrackList()}
     */
    public void setAudioTrack(@Nullable AudioTrack track) {
        int rendererIndex = getAudioTrackRendererId();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (rendererIndex != -1 && trackInfo != null) {
            TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
            DefaultTrackSelector.ParametersBuilder builder = trackSelector.buildUponParameters();
            builder.setRendererDisabled(rendererIndex, track == null);
            if (track != null) {
                DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(track.groupIndex, track.trackIndex);
                builder.setSelectionOverride(rendererIndex, trackGroups, override);
            } else {
                builder.clearSelectionOverride(rendererIndex, trackGroups);
            }
            trackSelector.setParameters(builder);
        }
        broadcastEvent(Event.Type.AUDIO_TRACK_DID_CHANGE);
    }

    @Nullable
    public AudioTrack getCurrentAudioTrack() {
        int rendererIndex = getAudioTrackRendererId();

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null && rendererIndex != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            DefaultTrackSelector.SelectionOverride override = trackSelector.getParameters().getSelectionOverride(rendererIndex, trackGroups);
            if (override != null) {
                int[] tracks = override.tracks;
                if (tracks.length != 0) {
                    return AudioTrack.createFrom(trackGroups.get(override.groupIndex), override.groupIndex, tracks[0]);
                }
            }
        }
        return null;
    }

    @NonNull
    public List<SubtitleTrack> getSubtitleTrackList() {
        List<SubtitleTrack> result = new ArrayList<>();
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        int subtitleRendererId = getSubtitleRendererId();
        if (mappedTrackInfo != null && subtitleRendererId != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(subtitleRendererId);
            for (int i = 0; i < trackGroups.length; i++) {
                TrackGroup trackGroup = trackGroups.get(i);
                for (int j = 0; j < trackGroup.length; j++) {
                    SubtitleTrack subtitleTrack = getSubtitleTrack(trackGroup, i, j);
                    if (subtitleTrack != null) {
                        result.add(subtitleTrack);
                        break;
                    }
                }
            }
        }
        if (debugMode && (result.isEmpty())) {
            return Arrays.asList(
                    new SubtitleTrack(0, "en", null, "English"),
                    new SubtitleTrack(0, "fr", null, "French"),
                    new SubtitleTrack(0, "ar", null, "عربي"),
                    new SubtitleTrack(0, "ch", null, "中文"));
        } else {
            return result;
        }
    }

    public void setSubtitleTrack(@Nullable SubtitleTrack track) {
        int rendererIndex = getSubtitleRendererId();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (rendererIndex != -1 && trackInfo != null) {
            TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
            DefaultTrackSelector.ParametersBuilder builder = trackSelector.buildUponParameters();
            builder.setRendererDisabled(rendererIndex, track == null);
            if (track != null) {
                Pair<Integer, Integer> integerPair = (Pair<Integer, Integer>) track.tag;
                int groupIndex = integerPair.first;
                int trackIndex = integerPair.second;
                DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
                builder.setSelectionOverride(rendererIndex, trackGroups, override);
            } else {
                builder.clearSelectionOverride(rendererIndex, trackGroups);
            }
            trackSelector.setParameters(builder);
        }
        broadcastEvent(Event.Type.SUBTITLE_DID_CHANGE);
    }

    @Nullable
    public SubtitleTrack getSubtitleTrack() {
        int rendererIndex = getSubtitleRendererId();

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null && rendererIndex != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            DefaultTrackSelector.SelectionOverride override = trackSelector.getParameters().getSelectionOverride(rendererIndex, trackGroups);
            if (override != null) {
                int[] tracks = override.tracks;
                if (tracks.length != 0) {
                    return getSubtitleTrackByTrackId(override.groupIndex, tracks[0]);
                }
            }
        }
        return null;
    }

    private SubtitleTrack getSubtitleTrack(TrackGroup trackGroup, int i, int j) {
        Format format = trackGroup.getFormat(j);
        if (format.id != null && format.language != null) {
            String label = format.label != null ? format.label : format.language;
            return new SubtitleTrack(new Pair<>(i, j), format.id, format.language, label);
        } else {
            return null;
        }
    }

    private SubtitleTrack getSubtitleTrackByTrackId(int i, int j) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(getSubtitleRendererId());
            TrackGroup trackGroup = trackGroups.get(i);
            return getSubtitleTrack(trackGroup, i, j);
        } else {
            return null;
        }
    }

    private int getSubtitleRendererId() {
        return getTrackRendererIdOfType(C.TRACK_TYPE_TEXT);
    }

    private int getAudioTrackRendererId() {
        return getTrackRendererIdOfType(C.TRACK_TYPE_AUDIO);
    }

    private int getTrackRendererIdOfType(int trackType) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo != null) {
            for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
                if (mappedTrackInfo.getTrackGroups(i).length > 0
                        && exoPlayer.getRendererType(i) == trackType) {
                    return i;
                }
            }
        }
        return -1;
    }

    //endregion

    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        boolean audioCapabilitiesChanged = !audioCapabilities.equals(this.audioCapabilities);
        if (audioCapabilitiesChanged) {
            this.audioCapabilities = audioCapabilities;
        }
    }

    //region Exoplayer listeners

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        timeline.getWindow(exoPlayer.getCurrentWindowIndex(), this.window);
        long liveEdgeDuration = 0L;
        if (window.isDynamic && manifest instanceof HlsManifest) {
            liveEdgeDuration = 30000;
        }
        playerTimeLine.update(window.windowStartTimeMs, window.getDurationMs(), window.isDynamic, liveEdgeDuration);
        broadcastEvent(Event.Type.STREAM_TIMELINE_CHANGED);
    }


    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        // Ignore
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.v(TAG, toString() + " Exoplayer state change: " + playWhenReady + " " + playbackState);
        if (this.playbackState == null || this.playbackState != playbackState) {
            switch (playbackState) {
                case Player.STATE_IDLE:
                    break;
                case Player.STATE_BUFFERING:
                    setState(State.BUFFERING);
                    doAkamaiAnalytics((ma) -> {
                        if (this.playbackState == Player.STATE_READY) {
                            ma.handleBufferStart();
                        }
                    });
                    break;
                case Player.STATE_READY:
                    numberOfDrmRetry = 0;
                    if (!playingOrBuffering) {
                        broadcastEvent(Event.Type.MEDIA_READY_TO_PLAY);
                        playingOrBuffering = true;
                    }
                    setState(State.READY);
                    startPeriodicUpdateThreadIfNecessary();
                    doAkamaiAnalytics((ma) -> {
                        if (this.playbackState == Player.STATE_BUFFERING) {
                            ma.handleBufferEnd();
                        } else {
                            ma.handlePlaying();
                        }
                    });
                    break;
                case Player.STATE_ENDED:
                    setState(State.READY);
                    broadcastEvent(Event.Type.MEDIA_COMPLETED);
                    doRelease(); // Business decision, but we could set position to default and not releasing exoplayer.
                    doAkamaiAnalytics((ma) -> ma.handlePlayEnd(EndReasonCodes.Play_End_Detected.toString()));
                    break;
            }
            this.playbackState = playbackState;
            broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
        }
        if (this.exoPlayerCurrentPlayWhenReady != playWhenReady) {
            broadcastEvent(Event.Type.PLAYING_STATE_CHANGE);
            this.exoPlayerCurrentPlayWhenReady = playWhenReady;
        }
        manageKeepScreenOn();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Ignore
    }

    /**
     * Retry exoplayer playback after an error.
     */
    public void retry() {
        Runnable retry = exoPlayer::retry;
        if (drmConfig != null && licenseStoreDelegate != null) {
            downloadOrApplyOfflineLicense(currentMediaUri, retry, drmConfig);
        } else {
            retry.run();
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        manageKeepScreenOn();
        Throwable cause = error.getCause();
        SRGMediaPlayerException.Reason reason = SRGMediaPlayerException.Reason.EXOPLAYER;
        if (cause instanceof MediaCodec.CryptoException) {
            MediaCodec.CryptoException cryptoException = (MediaCodec.CryptoException) cause;
            if (cryptoException.getErrorCode() == MediaCodec.CryptoException.ERROR_KEY_EXPIRED
                    || cryptoException.getErrorCode() == MediaCodec.CryptoException.ERROR_NO_KEY) {
                if (currentMediaUri != null && numberOfDrmRetry < 1) {
                    Log.w(TAG, "DRM expired key during playback, retrying");
                    numberOfDrmRetry++;
                    retry();
                    return;
                } else {
                    Log.w(TAG, "DRM expired key during playback. Failing (retry count: " + numberOfDrmRetry + ")");
                    reason = SRGMediaPlayerException.Reason.DRM_KEY_EXPIRED;
                }
            } else {
                reason = SRGMediaPlayerException.Reason.DRM;
            }
        } else if (error.type == ExoPlaybackException.TYPE_RENDERER) {
            reason = SRGMediaPlayerException.Reason.RENDERER;
        } else if (cause instanceof IOException) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException
                    && ((HttpDataSource.InvalidResponseCodeException) cause).responseCode == 403) {
                reason = SRGMediaPlayerException.Reason.FORBIDDEN;
            } else {
                reason = SRGMediaPlayerException.Reason.NETWORK;
            }
        }
        SRGMediaPlayerException exception = new SRGMediaPlayerException(null, error, reason);
        doAkamaiAnalytics((ma) -> ma.handleError("PLAYER.ERROR"));

        handlePlayerException(exception);
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        broadcastEvent(Event.Type.POSITION_DISCONTINUITY);
        Log.w(TAG, "Position discontinuity " + reason);
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        // Ignore
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Ignore
    }

    @Override
    public void onSeekProcessed() {
        broadcastEvent(Event.Type.DID_SEEK);
        broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                   float pixelWidthHeightRatio) {
        float aspectRatio = ((float) width / (float) height) * pixelWidthHeightRatio;
        if ((aspectRatio / 90) % 2 == 1) {
            aspectRatio = 1 / aspectRatio;
        }
        final float ration = aspectRatio;
        if (mediaPlayerView != null) {
            mediaPlayerView.setVideoAspectRatio(ration);
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        firstFrameRendered = true;
        broadcastEvent(Event.Type.FIRST_FRAME_RENDERED);
    }


    @Override
    public void onSurfaceSizeChanged(int width, int height) {
        //Nothing
    }

    @Override
    public void onCues(final List<Cue> cues) {
        if (mediaPlayerView != null) {
            mediaPlayerView.setCues(cues);
        }
    }

    @Override
    public void onDrmSessionAcquired() {
        // already handled by eventLogger
    }

    @Override
    public void onDrmSessionReleased() {
        // already handled by eventLogger
    }

    @Override
    public void onDrmKeysLoaded() {
        broadcastEvent(Event.Type.DRM_KEYS_LOADED);
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        broadcastFatalError(new SRGMediaPlayerException(null, e, SRGMediaPlayerException.Reason.DRM), true);
    }

    @Override
    public void onDrmKeysRestored() {
        // already handled by eventLogger
    }

    @Override
    public void onDrmKeysRemoved() {
        // already handled by eventLogger
    }

    //endregion


    /**
     * Provide Akamai QOS Configuration.
     *
     * @param akamaiMediaAnalyticsConfiguration akamai qos configuration to enable QOS monitoring. null to disable
     *                                          akamai qos.
     */
    public void setAkamaiMediaAnalyticsConfiguration(@Nullable AkamaiMediaAnalyticsConfiguration
                                                             akamaiMediaAnalyticsConfiguration) {
        this.akamaiMediaAnalyticsConfiguration = akamaiMediaAnalyticsConfiguration;
    }

    private class MonitoringDrmCallback implements MediaDrmCallback {
        private final MediaDrmCallback callback;

        public MonitoringDrmCallback(MediaDrmCallback mediaDrmCallback) {
            this.callback = mediaDrmCallback;
        }

        @Override
        public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request) throws Exception {
            Log.v(TAG, "DRM: executeProvisionRequest");
            long now = SystemClock.elapsedRealtime();
            byte[] result = callback.executeProvisionRequest(uuid, request);
            drmRequestDuration += SystemClock.elapsedRealtime() - now;
            drmRequestOffline = false;
            return result;
        }

        @Override
        public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request) throws Exception {
            Log.v(TAG, "DRM: executeKeyRequest");
            long now = SystemClock.elapsedRealtime();
            byte[] result = callback.executeKeyRequest(uuid, request);
            drmRequestDuration += SystemClock.elapsedRealtime() - now;
            drmRequestOffline = false;
            return result;
        }
    }

    public void setLicenseStoreDelegate(LicenseStoreDelegate licenseStoreDelegate) {
        this.licenseStoreDelegate = licenseStoreDelegate;
    }

    private static synchronized DefaultBandwidthMeter getDefaultBandwidthMeter(Context context) {
        if (singletonBandwidthMeter == null) {
            singletonBandwidthMeter = new DefaultBandwidthMeter.Builder(context.getApplicationContext()).build();
        }
        return singletonBandwidthMeter;
    }
}