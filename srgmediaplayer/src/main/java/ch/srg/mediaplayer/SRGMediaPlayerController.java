package ch.srg.mediaplayer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.akamai.android.exoplayer2loader.AkamaiExoPlayerLoader;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
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
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;

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

import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Handle the playback of media.
 * if used in conjonction with a SRGMediaPlayerView can handle Video playback base on delegation on
 * actual players, like android.MediaPlayer or ExoPlayer
 */
@SuppressWarnings({"unused", "unchecked", "UnusedReturnValue", "WeakerAccess", "PointlessBitwiseExpression"})
public class SRGMediaPlayerController implements Handler.Callback,
        Player.EventListener,
        DefaultDrmSessionManager.EventListener,
        SimpleExoPlayer.VideoListener,
        AudioCapabilitiesReceiver.Listener,
        TextRenderer.Output {
    public static final String TAG = "SRGMediaPlayer";
    public static final String VERSION = BuildConfig.VERSION_NAME;

    private static final long[] EMPTY_TIME_RANGE = new long[2];
    private static final long UPDATE_PERIOD = 100;
    private static final long SEGMENT_HYSTERESIS_MS = 5000;
    private Long userTrackingProgress;
    private static final String NAME = "SRGMediaPlayer";
    private boolean currentViewKeepScreenOn;

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    /**
     * True when audio focus has been requested, does not reflect current focus (LOSS / DUCKED).
     */
    private boolean audioFocusGranted;

    @Nullable
    private Long currentSeekTarget;
    private boolean debugMode;
    private boolean pausedBecauseTransientFocusLoss;
    private boolean duckedBecauseTransientFocusLoss;
    private boolean pausedBecauseFocusLoss;
    private boolean mutedBecauseFocusLoss;
    //TODO Use this in exoplayer 2 (or delete)
    private Long qualityOverride;
    private Long qualityDefault;
    private Throwable fatalError;
    private long controllerId;
    private static long controllerIdCounter;

    private boolean firstFrameRendered;

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static final long UNKNOWN_TIME = -1;

    public static final long TIME_LIVE = C.TIME_UNSET;

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
    private static final int MSG_EXCEPTION = 12;
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
            STATE_CHANGE,
            FATAL_ERROR,
            TRANSIENT_ERROR, /* To be removed ? */
            DRM_ERROR,

            MEDIA_READY_TO_PLAY,
            MEDIA_COMPLETED,
            MEDIA_STOPPED,

            PLAYING_STATE_CHANGE,
            WILL_SEEK, // SEEK_STARTED
            DID_SEEK, // SEEK_STOPPED

            EXTERNAL_EVENT,

            DID_BIND_TO_PLAYER_VIEW,
            DID_UNBIND_FROM_PLAYER_VIEW,

            SUBTITLE_DID_CHANGE,
            AUDIO_TRACK_DID_CHANGE,

            FIRST_FRAME_RENDERED,

            POSITION_DISCONTINUITY,

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

            UNSUPPORTED_DRM
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
        public final long mediaPlaylistStartTime;
        public final boolean mediaLive;
        public final ScreenType screenType;
        public final State state;

        public Segment segment;
        public String blockingReason;
        public Event.Type segmentEventType;

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

        private Event(SRGMediaPlayerController controller, Type eventType, SRGMediaPlayerException eventException, Segment segment, String blockingReason) {
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
            mediaPlaylistStartTime = controller.getPlaylistStartTime();
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
                    ", mediaPlaylistStartTime=" + mediaPlaylistStartTime +
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

    public interface Listener {

        /**
         * Called on specific player event (see {@link Event.Type} )
         *
         * @param mp    the SRGMediaPlayer that triggers the event
         * @param event corresponding event
         */
        void onMediaPlayerEvent(SRGMediaPlayerController mp, Event event);

    }

    private Context context;

    private Handler mainHandler;

    private Handler commandHandler;
    private HandlerThread commandHandlerThread;
    private final AudioManager audioManager;

    private State state = State.IDLE;

    private boolean exoPlayerCurrentPlayWhenReady;

    private Long seekToWhenReady = null;

    //Used to force keepscreen on even when not playing
    private boolean externalWakeLock = false;

    private boolean muted = false;

    @NonNull
    private final SimpleExoPlayer exoPlayer;
    private final AudioCapabilitiesReceiver audioCapabilitiesReceiver;

    private DefaultTrackSelector trackSelector;
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private AudioCapabilities audioCapabilities;
    private EventLogger eventLogger;
    private ViewType viewType;
    private View renderingView;
    private Integer playbackState;
    private List<Segment> segments = new ArrayList<>();

    private Segment segmentBeingSkipped;
    @Nullable
    private Segment currentSegment = null;
    private boolean userChangingProgress;
    @Nullable
    private SRGMediaPlayerView mediaPlayerView;

    private Uri currentMediaUri = null;

    private String tag;
    //Main player property to handle multiple player view
    private boolean mainPlayer = true;

    private int audioFocusBehaviorFlag = AUDIO_FOCUS_FLAG_PAUSE;

    private OnAudioFocusChangeListener audioFocusChangeListener;

    /**
     * Listeners registered to this player
     */
    private Set<Listener> eventListeners = Collections.newSetFromMap(new WeakHashMap<Listener, Boolean>());

    private static Set<Listener> globalEventListeners = Collections.newSetFromMap(new WeakHashMap<Listener, Boolean>());

    @Nullable
    private AkamaiExoPlayerLoader akamaiExoPlayerLoader;

    @Nullable
    private AkamaiMediaAnalyticsConfiguration akamaiMediaAnalyticsConfiguration;
    // FIXME : why userAgent letterbox is set here?
    private static final String userAgent = "curl/Letterbox_2.0"; // temporarily using curl/ user agent to force subtitles with Akamai beta
    @Nullable
    private DrmConfig drmConfig;

    /**
     * Create a new SRGMediaPlayerController with no DRM support with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context context
     * @param tag     tag to identify this controller
     */
    public SRGMediaPlayerController(Context context, String tag) {
        this(context, tag, null);
    }

    /**
     * Create a new SRGMediaPlayerController with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context   context
     * @param tag       tag to identify this controller
     * @param drmConfig drm configuration
     */
    public SRGMediaPlayerController(Context context, String tag, @Nullable DrmConfig drmConfig) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper(), this);
        this.tag = tag;

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        controllerId = ++controllerIdCounter;

        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this.context, this);
        audioCapabilitiesReceiver.register();

        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        eventLogger = new EventLogger(trackSelector);
        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
        UnsupportedDrmException unsupportedDrm = null;
        if (drmConfig != null) {
            try {
                UUID drmType = drmConfig.getDrmType();
                HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(drmConfig.getLicenceUrl(), new DefaultHttpDataSourceFactory(userAgent));
                drmSessionManager = new DefaultDrmSessionManager<>(drmType,
                        FrameworkMediaDrm.newInstance(drmType),
                        drmCallback, null, mainHandler, this);
                setViewType(ViewType.TYPE_SURFACEVIEW);
            } catch (UnsupportedDrmException e) {
                fatalError = e;
            }
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this.context, drmSessionManager, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, new DefaultLoadControl());
        exoPlayer.addListener(this);
        exoPlayer.setVideoListener(this);
        exoPlayer.setTextOutput(this);
        exoPlayer.setAudioDebugListener(eventLogger);
        exoPlayer.setVideoDebugListener(eventLogger);
        exoPlayer.setMetadataOutput(eventLogger);
        exoPlayerCurrentPlayWhenReady = exoPlayer.getPlayWhenReady();

        audioFocusChangeListener = new OnAudioFocusChangeListener(new WeakReference<>(this));
        audioFocusGranted = false;
        if (fatalError != null) {
            Event event = new Event(this, Event.Type.UNSUPPORTED_DRM, (SRGMediaPlayerException) fatalError);
        }
    }

    private synchronized void startBackgroundThreadIfNecessary() {
        // Synchronization seems necessary here to prevent two startBackgroundThread back to back.
        if (commandHandler == null || !commandHandlerThread.isAlive()) {
            startBackgroundThread();
        }
    }

    private synchronized void startBackgroundThread() {
        stopBackgroundThread();
        commandHandlerThread = new HandlerThread(getClass().getSimpleName() + ":Handler", Process.THREAD_PRIORITY_DEFAULT);
        commandHandlerThread.start();
        logV("Started command thread: " + commandHandlerThread);
        commandHandler = new Handler(commandHandlerThread.getLooper(), this);
    }

    private synchronized void stopBackgroundThread() {
        logV("Stopping command thread: " + commandHandlerThread);
        if (commandHandler != null) {
            commandHandler.removeCallbacksAndMessages(null);
            commandHandler = null;
        }
        if (commandHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                commandHandlerThread.quitSafely();
            } else {
                commandHandlerThread.quit();
            }
        }
    }

    private void assertCommandHandlerThread() {
        if (Thread.currentThread() != commandHandlerThread) {
            throw new IllegalStateException("Invalid thread: " + Thread.currentThread());
        }
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

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
     * @throws SRGMediaPlayerException player exception
     */
    public boolean play(@NonNull Uri uri, int streamType) throws SRGMediaPlayerException {
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
     * @throws SRGMediaPlayerException player exception
     */
    public boolean play(@NonNull Uri uri, Long startPositionMs, @SRGStreamType int streamType) throws SRGMediaPlayerException {
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
     * @param startPositionMs start position in milliseconds or null to prevent seek
     * @param streamType      {@link SRGMediaPlayerController#STREAM_DASH}, {@link SRGMediaPlayerController#STREAM_HLS}, {@link SRGMediaPlayerController#STREAM_HTTP_PROGRESSIVE} or {@link SRGMediaPlayerController#STREAM_LOCAL_FILE}
     * @throws SRGMediaPlayerException player exception
     */
    @SuppressWarnings("ConstantConditions")
    public void prepare(@NonNull Uri uri,
                        Long startPositionMs,
                        @SRGStreamType int streamType,
                        List<Segment> segments) throws SRGMediaPlayerException {
        if (uri == null) {
            throw new IllegalArgumentException("Invalid argument: null uri");
        }
        PrepareUriData data = new PrepareUriData(uri, startPositionMs, streamType, segments);
        sendMessage(MSG_PREPARE_FOR_URI, data);
    }

    public void keepScreenOn(boolean lock) {
        externalWakeLock = lock;
        manageKeepScreenOnInternal();
    }

    class PrepareUriData {
        Uri uri;
        Long position;
        int streamType;
        private List<Segment> segments;

        PrepareUriData(Uri uri, Long position, int streamType, List<Segment> segments) {
            this.uri = uri;
            this.position = position;
            this.streamType = streamType;
            this.segments = segments;
        }

        @Override
        public String toString() {
            return uri.toString();
        }
    }

    /**
     * Resume playing after a pause call or make the controller start immediately after the preparation phase.
     *
     * @return true if focus audio granted
     */
    public boolean start() {
        if (requestAudioFocus()) {
            sendMessage(MSG_SET_PLAY_WHEN_READY, true);
            return true;
        } else {
            Log.v(TAG, "Audio focus request failed");
            return false;
        }
    }

    /**
     * Pause the current media or prevent it from starting immediately if controller in preparation phase.
     */
    public void pause() {
        resetAudioFocusResume();
        abandonAudioFocus();
        sendMessage(MSG_SET_PLAY_WHEN_READY, false);
    }

    /**
     * <p>
     * Try to seek to the provided position, if this position is not reachable
     * will throw an exception.
     * Seek position is stored when the player is preparing and the stream will start at the last seekTo value.
     * </p>
     * <h2>Live stream</h2>
     * <p>
     * When playing a live stream, a value of 0 represents the live most position.
     * A value of 1..duration represents the relative position in the live stream.
     * </p>
     *
     * @param positionMs position in ms
     * @throws IllegalStateException player error
     */
    public void seekTo(long positionMs) throws IllegalStateException {
        Segment blockedSegment = getBlockedSegment(positionMs);
        if (blockedSegment != null) {
            seekEndOfBlockedSegment(blockedSegment);
        } else {
            currentSeekTarget = positionMs;
            sendMessage(MSG_SEEK_TO, positionMs);
        }
    }

    public void mute() {
        sendMessage(MSG_SET_MUTE, true);
    }

    public void unmute() {
        sendMessage(MSG_SET_MUTE, false);
    }

    private void sendMessage(int what) {
        sendMessage(what, null);
    }

    private void sendMessage(int what, Object param) {
        logV("Sending message: " + what + " " + String.valueOf(param));
        if (!isReleased()) {
            startBackgroundThreadIfNecessary();
            if (commandHandler != null) {
                commandHandler.obtainMessage(what, param).sendToTarget();
            } else {
                logV("Released while sending message, " + what + " ignored");
            }
        } else {
            // Use case for this is to ignore asynchronous messages received from player for instance
            logV("Ignoring message in release state: " + what);
        }
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

    @Override
    public boolean handleMessage(final Message msg) {
        if (isReleased()) {
            logE("handleMessage when released: skipping " + msg);
            return true;
        }
        if (msg.what != MSG_PERIODIC_UPDATE) {
            logV("handleMessage: " + msg);
        }
        final SRGMediaPlayerView mediaPlayerView = this.mediaPlayerView;
        switch (msg.what) {
            case MSG_PREPARE_FOR_URI:
                setStateInternal(State.PREPARING);
                PrepareUriData data = (PrepareUriData) msg.obj;
                Uri uri = data.uri;
                this.segments.clear();
                seekToWhenReady = data.position;
                currentSegment = null;
                if (data.segments != null) {
                    segments.addAll(data.segments);
                }
                postEventInternal(Event.Type.SEGMENT_LIST_CHANGE);
                postEventInternal(Event.Type.MEDIA_READY_TO_PLAY);
                try {
                    if (mediaPlayerView != null) {
                        internalUpdateMediaPlayerViewBound();
                    }
                    prepareInternal(uri, data.streamType);
                } catch (SRGMediaPlayerException e) {
                    logE("onUriLoaded", e);
                    handleFatalExceptionInternal(e);
                }
                return true;

            case MSG_SET_PLAY_WHEN_READY:
                boolean playWhenReady = (Boolean) msg.obj;
                exoPlayer.setPlayWhenReady(playWhenReady);
                return true;

            case MSG_SEEK_TO:
                Long positionMs = (Long) msg.obj;
                if (positionMs == null) {
                    throw new IllegalArgumentException("Missing position for seek to");
                } else {
                    if (state != State.PREPARING) {
                        postEventInternal(Event.Type.WILL_SEEK);
                        broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
                        seekToWhenReady = positionMs;
                        try {
                            exoPlayer.seekTo(seekToWhenReady);
                            seekToWhenReady = null;
                        } catch (IllegalStateException ignored) {
                        }
                    } else {
                        seekToWhenReady = positionMs;
                    }
                }
                return true;

            case MSG_SET_MUTE:
                if (this.muted != (Boolean) msg.obj) {
                    this.muted = (Boolean) msg.obj;
                    muteInternal(muted);
                }
                return true;

            case MSG_APPLY_STATE:
                applyStateInternal();
                return true;

            case MSG_RELEASE:
                releaseInternal();
                return true;

            case MSG_EXCEPTION:
                handleFatalExceptionInternal((SRGMediaPlayerException) msg.obj);
                return true;

            case MSG_REGISTER_EVENT_LISTENER:
                Listener listenerToRegister = ((WeakReference<Listener>) msg.obj).get();
                if (listenerToRegister != null) {
                    eventListeners.add(listenerToRegister);
                }
                return true;

            case MSG_UNREGISTER_EVENT_LISTENER:
                Listener listenerToUnregister = ((WeakReference<Listener>) msg.obj).get();
                if (listenerToUnregister != null) {
                    eventListeners.remove(listenerToUnregister);
                }
                return true;

            case MSG_PLAYER_PREPARING:
                setStateInternal(State.PREPARING);
                return true;

            case MSG_PLAYER_READY:
                setStateInternal(State.READY);
                applyStateInternal();
                return true;

            case MSG_PLAYER_BUFFERING:
                setStateInternal(State.BUFFERING);
                return true;

            case MSG_PLAYER_COMPLETED:
                setStateInternal(State.READY);
                postEventInternal(Event.Type.MEDIA_COMPLETED);
                releaseInternal();
                return true;

            case MSG_PLAYER_PLAY_WHEN_READY_COMMITED:
                postEventInternal(Event.Type.PLAYING_STATE_CHANGE);
                return true;

            case MSG_PLAYER_SUBTITLE_CUES:
                final List<Cue> cueList = (List<Cue>) msg.obj;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaPlayerView != null) {
                            mediaPlayerView.setCues(cueList);
                        }
                    }
                });
                return true;

            case MSG_PLAYER_VIDEO_ASPECT_RATIO:
                final float aspectRatio = (Float) msg.obj;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaPlayerView != null) {
                            mediaPlayerView.setVideoAspectRatio(aspectRatio);
                        }
                    }
                });
                return true;

            case MSG_PERIODIC_UPDATE:
                periodicUpdateInternal();
                schedulePeriodUpdate();
                return true;

            case MSG_FIRE_EVENT:
                postEventInternal((Event) msg.obj);
                return true;

            default:
                String message = "Unknown message: " + msg.what + " / " + msg.obj;
                if (isDebugMode()) {
                    throw new IllegalArgumentException(message);
                } else {
                    logE(message);
                    return false;
                }

        }
    }

    private void prepareInternal(@NonNull Uri videoUri, int streamType) throws SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri + " (" + streamType + ")");
        setupAkamaiQos(videoUri);
        try {
            if (this.currentMediaUri != null && this.currentMediaUri.equals(videoUri)) {
                return;
            }
            sendMessage(MSG_PLAYER_PREPARING);
            this.currentMediaUri = videoUri;


            DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                    userAgent,
                    BANDWIDTH_METER,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true);

            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, BANDWIDTH_METER, httpDataSourceFactory);

            MediaSource mediaSource;

            eventLogger.setChainedListener(akamaiExoPlayerLoader);

            switch (streamType) {
                case STREAM_DASH:
                    mediaSource = new DashMediaSource(videoUri, new DefaultHttpDataSourceFactory(userAgent),
                            new DefaultDashChunkSource.Factory(dataSourceFactory), mainHandler, eventLogger);
                    break;
                case STREAM_HLS:
                    mediaSource = new HlsMediaSource(videoUri, dataSourceFactory, mainHandler, eventLogger);
                    break;
                case STREAM_HTTP_PROGRESSIVE:
                    mediaSource = new ExtractorMediaSource(videoUri, dataSourceFactory, new DefaultExtractorsFactory(),
                            mainHandler, eventLogger);
                    break;
                case STREAM_LOCAL_FILE:
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

    private void setupAkamaiQos(@NonNull Uri videoUri) {
        if (akamaiMediaAnalyticsConfiguration != null) {
            akamaiExoPlayerLoader = new AkamaiExoPlayerLoader(getContext(), akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsConfigUrl(), isDebugMode());
            akamaiExoPlayerLoader.setViewerId(akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsViewerId());
            for (Pair<String, String> keyValue : akamaiMediaAnalyticsConfiguration.getAkamaiMediaAnalyticsDataSet()) {
                akamaiExoPlayerLoader.setData(keyValue.first, keyValue.second);
            }
            akamaiExoPlayerLoader.initializeLoader(exoPlayer, videoUri.toString());
            exoPlayer.setVideoDebugListener(akamaiExoPlayerLoader);
        }
    }

    private void muteInternal(boolean muted) {
        exoPlayer.setVolume(muted ? 0f : 1f);
    }

    private void periodicUpdateInternal() {
        long currentPosition = exoPlayer.getCurrentPosition();
        if (currentSeekTarget != null) {
            if (currentPosition != UNKNOWN_TIME
                    && currentPosition != currentSeekTarget
                    || !exoPlayerCurrentPlayWhenReady) {
                currentSeekTarget = null;
                postEventInternal(Event.Type.DID_SEEK);
                postEventInternal(Event.Type.LOADING_STATE_CHANGED);
            }
        }
        if (!segments.isEmpty() && !userChangingProgress) {
            checkSegmentChange(currentPosition);
        }
    }

    private void checkSegmentChange(long mediaPosition) {
        if (isReleased() && mediaPosition != UNKNOWN_TIME) {
            return;
        }
        if (!isSeekPending() && mediaPosition != -1) {
            Segment blockedSegment = getBlockedSegment(mediaPosition);
            Segment newSegment = getSegment(mediaPosition);

            if (blockedSegment != null) {
                if (blockedSegment != segmentBeingSkipped) {
                    Log.v("SegmentTest", "Skipping over " + blockedSegment.getIdentifier());
                    segmentBeingSkipped = blockedSegment;
                    seekEndOfBlockedSegment(blockedSegment);
                }
            } else {
                segmentBeingSkipped = null;
                if (currentSegment != newSegment) {
                    if (currentSegment == null) {
                        postSegmentEvent(Event.Type.SEGMENT_START, newSegment);
                    } else if (newSegment == null) {
                        postSegmentEvent(Event.Type.SEGMENT_END, null);
                    } else {
                        postSegmentEvent(Event.Type.SEGMENT_SWITCH, newSegment);
                    }
                    currentSegment = newSegment;
                }
            }
        }
    }

    @NonNull
    public List<Segment> getSegments() {
        return segments;
    }

    private void seekEndOfBlockedSegment(Segment segment) {
        postBlockedSegmentEvent(Event.Type.SEGMENT_SKIPPED_BLOCKED, segment);
        seekTo(segment.getMarkOut());
    }

    @Nullable
    public Segment getSegment(long time) {
        if (currentSegment != null && segments.contains(currentSegment)
                && time >= currentSegment.getMarkIn() - SEGMENT_HYSTERESIS_MS
                && time < currentSegment.getMarkOut()) {
            return currentSegment;
        }
        for (Segment segment : segments) {
            if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
                return segment;
            }
        }
        return null;
    }

    @Nullable
    private Segment getBlockedSegment(long time) {
        for (Segment segment : segments) {
            if (!TextUtils.isEmpty(segment.getBlockingReason())) {
                if (time >= segment.getMarkIn() && time < segment.getMarkOut()) {
                    return segment;
                }
            }
        }
        return null;
    }

    public void setSegmentList(List<Segment> segmentList) {
        segments.clear();
        if (segmentList != null) {
            segments.addAll(segmentList);
        }
        checkSegmentChange(getMediaPosition());
        broadcastEvent(Event.Type.SEGMENT_LIST_CHANGE);
    }

    private void postSegmentEvent(Event.Type type, Segment segment) {
        broadcastEvent(new Event(this, type, null, segment));
    }

    private void postBlockedSegmentEvent(Event.Type type, Segment segment) {
        broadcastEvent(new Event(this, type, null, segment, segment.getBlockingReason()));
    }

    private void switchToSegment(Segment segment) {
        postSegmentEvent(Event.Type.SEGMENT_SELECTED, segment);
        seekTo(segment.getMarkIn());
    }

    /**
     * Start playing a segment in the segment list. This is considered a user request.
     * This will seek to the beginning of the segment whether or not we are currently in this
     * segment.
     * This does not affect playing state.
     *
     * @param identifier segment identifier
     * @return true if segment found and switch occured
     */
    public boolean switchToSegment(String identifier) {
        for (Segment segment : segments) {
            if (TextUtils.equals(segment.getIdentifier(), identifier)) {
                switchToSegment(segment);
                return true;
            }
        }
        return false;
    }

    private void schedulePeriodUpdate() {
        commandHandler.removeMessages(MSG_PERIODIC_UPDATE);
        commandHandler.sendMessageDelayed(
                commandHandler.obtainMessage(MSG_PERIODIC_UPDATE), UPDATE_PERIOD);
    }

    private void applyStateInternal() {
        muteInternal(muted);
        Long seekTarget = this.seekToWhenReady;
        if (seekTarget != null) {
            postEventInternal(Event.Type.WILL_SEEK);
            broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
            Log.v(TAG, "Apply state / Seeking to " + seekTarget);
            try {
                exoPlayer.seekTo(seekTarget);
                currentSeekTarget = seekTarget;
                this.seekToWhenReady = null;
            } catch (IllegalStateException ignored) {
            }
        }
        if (exoPlayerCurrentPlayWhenReady) {
            schedulePeriodUpdate();
        }
    }

    private void manageKeepScreenOnInternal() {
        int playbackState = exoPlayer.getPlaybackState();
        boolean playWhenReady = exoPlayer.getPlayWhenReady();
        final boolean lock = externalWakeLock ||
                ((playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) && playWhenReady);
        logV("Scheduling change keepScreenOn currently attached mediaPlayerView to " + lock + state + " " + playbackState + " " + playWhenReady);
        if (currentViewKeepScreenOn != lock) {
            currentViewKeepScreenOn = lock;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayerView != null) {
                        mediaPlayerView.setKeepScreenOn(lock);
                        logV("Changing keepScreenOn for currently attached mediaPlayerView to " + lock + "[" + mediaPlayerView + "]");
                    } else {
                        logV("Cannot change keepScreenOn, no mediaPlayerView attached");
                    }
                }
            });
        }
    }

    private void logV(String msg) {
        if (isDebugMode()) {
            Log.v(TAG, getControllerId() + " " + msg);
        }
    }

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

    private void releaseDelegateInternal() {
        if (debugMode) {
            assertCommandHandlerThread();
        }

        postEventInternal(Event.Type.MEDIA_STOPPED);
        if (akamaiExoPlayerLoader != null) {
            akamaiExoPlayerLoader.releaseLoader();
        }
        exoPlayer.stop();
        exoPlayer.release();
        currentMediaUri = null;
        seekToWhenReady = null;
        audioCapabilitiesReceiver.unregister();
    }

    public State getState() {
        return state;
    }

    private void handleFatalExceptionInternal(SRGMediaPlayerException e) {
        logE("exception occurred", e);
        postFatalErrorInternal(e);
        releaseInternal();
    }

    /**
     * Release the current player. Once the player is released you have to create a new player
     * if you want to play a new video.
     * <p>
     * Remark: The player does not immediately reach the released state.
     */
    public void release() {
        if (mediaPlayerView != null) {
            unbindFromMediaPlayerView(mediaPlayerView);
        }
        sendMessage(MSG_RELEASE);
    }

    private void releaseInternal() {
        currentSeekTarget = null;
        setStateInternal(State.RELEASED);
        abandonAudioFocus();
        releaseDelegateInternal();
        stopBackgroundThread();
        unregisterAllEventListenersInternal();
    }

    public boolean isPlaying() {
        return state == State.READY && exoPlayer.getPlaybackState() == Player.STATE_READY && exoPlayer.getPlayWhenReady();
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

    /**
     * @return media position
     */
    public long getMediaPosition() {
        Long seekToWhenReady = this.seekToWhenReady;
        if (seekToWhenReady != null) {
            return seekToWhenReady;
        } else {
            return exoPlayer.getCurrentPosition();
        }
    }

    /**
     * @return Media duration relative to 0.
     */
    public long getMediaDuration() {
        return exoPlayer.getDuration();
    }

    /**
     * Live time, (time of the last playlist load).
     * <pre>
     *     getPosition() - getDuration() + getLiveTime() = wall clock time
     * </pre>
     *
     * @return reference wall clock time in ms
     */
    public long getLiveTime() {
        return getPlaylistStartTime();
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
        newView.setCues(Collections.<Cue>emptyList());
        internalUpdateMediaPlayerViewBound();
        manageKeepScreenOnInternal();
    }

    @Nullable
    public SRGMediaPlayerView getMediaPlayerView() {
        return mediaPlayerView;
    }

    private void internalUpdateMediaPlayerViewBound() {
        final SRGMediaPlayerView mediaPlayerView = this.mediaPlayerView;
        if (mediaPlayerView != null) {
            if (!canRenderInView(mediaPlayerView.getVideoRenderingView())) {
                // We need to create a new rendering view.
                createRenderingViewInMainThread(mediaPlayerView.getContext());
                Log.v(TAG, renderingView + "binding, creating rendering view" + mediaPlayerView);
            } else {
                renderingView = mediaPlayerView.getVideoRenderingView();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.v(TAG, "binding, bindRenderingViewInUiThread " + mediaPlayerView);
                            bindRenderingViewInUiThread();
                        } catch (SRGMediaPlayerException e) {
                            Log.d(TAG, "Error binding view", e);
                        }
                    }
                });
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
                    + mediaPlayerView);
        }
        currentViewKeepScreenOn = mediaPlayerView.getKeepScreenOn();
        pushSurface();
        broadcastEvent(Event.Type.DID_BIND_TO_PLAYER_VIEW);
    }

    private void pushSurface() {
        if (renderingView instanceof SurfaceView) {
            exoPlayer.setVideoSurfaceView((SurfaceView) renderingView);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.setVideoTextureView((TextureView) renderingView);
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
            mediaPlayerView = null;
            broadcastEvent(Event.Type.DID_UNBIND_FROM_PLAYER_VIEW);
        }
    }

    private boolean canRenderInView(View view) {
        return view instanceof SurfaceView || view instanceof TextureView;
    }

    private void createRenderingViewInMainThread(final Context parentContext) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (viewType == ViewType.TYPE_SURFACEVIEW) {
                    renderingView = new SurfaceView(parentContext);
                } else {
                    renderingView = new TextureView(parentContext);
                }
                if (mediaPlayerView != null) {
                    Log.v(TAG, "binding, setVideoRenderingView " + mediaPlayerView);
                    mediaPlayerView.setVideoRenderingView(renderingView);
                }
                if (renderingView instanceof SurfaceView) {
                    ((SurfaceView) renderingView).getHolder().addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            Log.v(TAG, renderingView + "binding, surfaceCreated" + mediaPlayerView);
                            try {
                                if (((SurfaceView) renderingView).getHolder() == holder) {
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
                            //TODO if a delegate is bound to this surface, we need tu unbind it
                        }
                    });
                } else if (renderingView instanceof TextureView) {
                    TextureView textureView = (TextureView) renderingView;
                    textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                        @SuppressWarnings("ConstantConditions")
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
                            // TODO
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
            }
        });
    }

    public void setViewType(ViewType viewType) {
        this.viewType = viewType;
    }

    private void unbindRenderingView() {
        if (renderingView instanceof SurfaceView) {
            exoPlayer.clearVideoSurfaceView((SurfaceView) renderingView);
        } else if (renderingView instanceof TextureView) {
            exoPlayer.clearVideoTextureView((TextureView) renderingView);
        }
        renderingView = null;
    }

    private void setStateInternal(State state) {
        if (debugMode) {
            assertCommandHandlerThread();
        }
        if (this.state != state) {
            this.state = state;
            postEventInternal(Event.buildStateEvent(this));
        }
    }

    /**
     * Register a listener on events fired by this SRGMediaPlayerController. WARNING, The listener
     * is stored in a Weak set. If you use a dedicated object, make sure to keep a reference.
     *
     * @param listener the listener.
     */
    public void registerEventListener(Listener listener) {
        sendMessage(MSG_REGISTER_EVENT_LISTENER, new WeakReference<>(listener));
    }

    /**
     * Unregister a listener from this SRGMediaPlayerController.
     *
     * @param listener the listener.
     */
    public void unregisterEventListener(Listener listener) {
        sendMessage(MSG_UNREGISTER_EVENT_LISTENER, new WeakReference<>(listener));
    }

    private void unregisterAllEventListenersInternal() {
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

    private void broadcastEvent(Event.Type eventType) {
        broadcastEvent(Event.buildEvent(this, eventType));
    }

    private void broadcastEvent(Event event) {
        sendMessage(MSG_FIRE_EVENT, event);
    }

    private void postFatalErrorInternal(SRGMediaPlayerException e) {
        // Don't update fatal error if a Drm exception occurs
        if (fatalError instanceof SRGDrmMediaPlayerException || fatalError instanceof UnsupportedDrmException) {
            return;
        }
        this.fatalError = e;
        postEventInternal(Event.buildErrorEvent(this, true, e));
    }

    private void postDrmErrorInternal(SRGDrmMediaPlayerException e) {
        this.fatalError = e;
        postEventInternal(new Event(this, Event.Type.DRM_ERROR, e));
    }

    private void postEventInternal(Event.Type eventType) {
        postEventInternal(Event.buildEvent(this, eventType));
    }

    private void postEventInternal(final Event event) {
        if (debugMode) {
            assertCommandHandlerThread();
        }
        int count = SRGMediaPlayerController.globalEventListeners.size() + this.eventListeners.size();
        final Set<Listener> eventListeners = new HashSet<>(count);
        Log.d(TAG, "Posting event: " + event + " to " + count);
        eventListeners.addAll(globalEventListeners);
        eventListeners.addAll(this.eventListeners);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                doPostEventInternal(event, eventListeners);
            }
        });
    }

    private void doPostEventInternal(Event event, Set<Listener> eventListeners) {
        for (Listener listener : eventListeners) {
            listener.onMediaPlayerEvent(this, event);
        }
    }

    public Context getContext() {
        return context;
    }

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
            sendMessage(MSG_SET_PLAY_WHEN_READY, true);
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
            sendMessage(MSG_SET_PLAY_WHEN_READY, false);
        } else if ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_MUTE) != 0) {
            if (!muted) {
                mutedBecauseFocusLoss = playing;
                mute();
            }
        }
    }

    private boolean requestAudioFocus() {
        if (audioFocusBehaviorFlag == 0 || audioFocusGranted) {
            return true;
        } else {
            Log.d(TAG, "Request audio focus");
            //final WeakReference<SRGMediaPlayerController> selfReference = new WeakReference<>(this);
            int result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);

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
        if (audioFocusBehaviorFlag != 0) {
            Log.d(TAG, "Abandon audio focus");

            audioManager.abandonAudioFocus(audioFocusChangeListener);
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
            Log.d(TAG, "audio focus changed: " + focusChange);

            SRGMediaPlayerController player = playerReference.get();
            if (player != null) {
                player.handleAudioFocusChange(focusChange);
            }
        }
    }

    public void setAudioFocusBehaviorFlag(int audioFocusBehaviorFlag) {
        this.audioFocusBehaviorFlag = audioFocusBehaviorFlag;
    }

    /**
     * Method used to handle a bug with the ExoPlayer, the current seek behaviour of this player
     * doesn't ensure the precision of the seek.
     *
     * @return true if current media position match to seek target
     */
    public boolean isSeekPending() {
        Long currentSeekTarget = this.currentSeekTarget;
        return currentSeekTarget != null && getMediaPosition() == currentSeekTarget;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /*package*/
    static Event createTestEvent(Event.Type eventType, SRGMediaPlayerController controller, SRGMediaPlayerException eventException) {
        return new Event(controller, eventType, eventException);
    }

    public String getControllerId() {
        return String.valueOf(controllerId);
    }

    public String getMediaSessionId() {
        return getControllerId();
    }

    private long getPlaylistStartTime() {
        long res = UNKNOWN_TIME;
        if (isLive()) {
            res = System.currentTimeMillis();
        }
        return res;
    }

    public boolean isLive() {
        return exoPlayer.isCurrentWindowDynamic();
    }

    public boolean isMainPlayer() {
        return mainPlayer;
    }

    public void setMainPlayer(boolean mainPlayer) {
        if (this.mainPlayer != mainPlayer) {
            this.mainPlayer = mainPlayer;
            forceBroadcastStateChange();
        }
    }

    public boolean isRemote() {
        return false;
    }

    private void forceBroadcastStateChange() {
        broadcastEvent(Event.buildStateEvent(this));
    }

    /**
     * Force use specific quality (when supported). Represented by bandwidth.
     * Can be 0 to force lowest quality or Integer.MAX for highest for instance.
     *
     * @param quality bandwidth quality in bits/sec or null to disable
     */
    public void setQualityOverride(Long quality) {
        qualityOverride = quality;
        sendMessage(MSG_APPLY_STATE);
    }

    /**
     * Use a specific quality when an estimate is not available (when supported).
     * Represented by bandwidth. Typically used to force a better quality during startup.
     * Can be 0 to force lowest quality or Integer.MAX for highest for instance.
     *
     * @param qualityDefault bandwidth quality in bits/sec or null to disable
     */
    public void setQualityDefault(Long qualityDefault) {
        this.qualityDefault = qualityDefault;
        sendMessage(MSG_APPLY_STATE);
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
                    }
                }
            }
        }
        if (debugMode && (result.isEmpty())) {
            return Arrays.asList(
                    new AudioTrack(0, 0, "English", null),
                    new AudioTrack(0, 1, "French", null),
                    new AudioTrack(0, 2, "عربي", null),
                    new AudioTrack(0, 3, "中文", null));
        } else {
            return result;
        }
    }

    /**
     * If track is null, no sound is playing during playback.
     *
     * @param track
     */
    public void setAudioTrack(@Nullable AudioTrack track) {
        int rendererIndex = getAudioTrackRendererId();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (rendererIndex != -1 && trackInfo != null) {
            TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
            trackSelector.setRendererDisabled(rendererIndex, track == null);
            if (track != null) {
                TrackSelection.Factory factory = new FixedTrackSelection.Factory();
                MappingTrackSelector.SelectionOverride override = new MappingTrackSelector.SelectionOverride(factory, track.groupIndex, track.trackIndex);
                trackSelector.setSelectionOverride(rendererIndex, trackGroups, override);
            } else {
                trackSelector.clearSelectionOverride(rendererIndex, trackGroups);
            }
        }
        broadcastEvent(Event.Type.AUDIO_TRACK_DID_CHANGE);
    }

    @Nullable
    public AudioTrack getCurrentAudioTrack() {
        int rendererIndex = getAudioTrackRendererId();

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null && rendererIndex != -1) {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

            MappingTrackSelector.SelectionOverride override = trackSelector.getSelectionOverride(rendererIndex, trackGroups);
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
                    }
                }
            }
        }
        if (debugMode && (result.isEmpty())) {
            return Arrays.asList(
                    new SubtitleTrack(0, "English", null),
                    new SubtitleTrack(0, "French", null),
                    new SubtitleTrack(0, "عربي", null),
                    new SubtitleTrack(0, "中文", null));
        } else {
            return result;
        }
    }

    public void setSubtitleTrack(@Nullable SubtitleTrack track) {
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
        broadcastEvent(Event.Type.SUBTITLE_DID_CHANGE);
    }

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

    private SubtitleTrack getSubtitleTrack(TrackGroup trackGroup, int i, int j) {
        Format format = trackGroup.getFormat(j);
        if (format.id != null && format.language != null) {
            return new SubtitleTrack(new Pair<>(i, j), format.id, format.language);
        } else {
            return null;
        }
    }

    private SubtitleTrack getSubtitleTrackByTrackId(int i, int j) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(getSubtitleRendererId());
        TrackGroup trackGroup = trackGroups.get(i);
        return getSubtitleTrack(trackGroup, i, j);
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
            for (int i = 0; i < mappedTrackInfo.length; i++) {
                if (mappedTrackInfo.getTrackGroups(i).length > 0
                        && exoPlayer.getRendererType(i) == trackType) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * @return loading state (preparing, buffering or seek pending)
     */
    public boolean isLoading() {
        return getState() == State.PREPARING || getState() == State.BUFFERING || isSeekPending();
    }


    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        boolean audioCapabilitiesChanged = !audioCapabilities.equals(this.audioCapabilities);
        if (audioCapabilitiesChanged) {
            this.audioCapabilities = audioCapabilities;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        // Ignore
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
        Log.v(TAG, toString() + " exo state change: " + playWhenReady + " " + playbackState);
        if (this.playbackState == null || this.playbackState != playbackState) {
            switch (playbackState) {
                case Player.STATE_IDLE:
                    break;
                case Player.STATE_BUFFERING:
                    sendMessage(MSG_PLAYER_BUFFERING);
                    break;
                case Player.STATE_READY:
                    manageKeepScreenOnInternal();
                    sendMessage(MSG_PLAYER_READY);
                    break;
                case Player.STATE_ENDED:
                    manageKeepScreenOnInternal();
                    sendMessage(MSG_PLAYER_COMPLETED);
                    break;
            }
            this.playbackState = playbackState;
            broadcastEvent(Event.Type.LOADING_STATE_CHANGED);
        }
        if (this.exoPlayerCurrentPlayWhenReady != playWhenReady) {
            sendMessage(MSG_PLAYER_PLAY_WHEN_READY_COMMITED);
            this.exoPlayerCurrentPlayWhenReady = playWhenReady;
        }
        manageKeepScreenOnInternal();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Ignore
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        manageKeepScreenOnInternal();
        sendMessage(MSG_EXCEPTION, new SRGMediaPlayerException(error));
    }

    @Override
    public void onPositionDiscontinuity() {
        broadcastEvent(Event.Type.POSITION_DISCONTINUITY);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Ignore
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        float aspectRatio = ((float) width / (float) height) * pixelWidthHeightRatio;
        if ((aspectRatio / 90) % 2 == 1) {
            aspectRatio = 1 / aspectRatio;
        }
        sendMessage(MSG_PLAYER_VIDEO_ASPECT_RATIO, aspectRatio);
    }

    @Override
    public void onRenderedFirstFrame() {
        firstFrameRendered = true;
        broadcastEvent(Event.Type.FIRST_FRAME_RENDERED);
    }

    public boolean isFirstFrameRendered() {
        return firstFrameRendered;
    }

    @Override
    public void onCues(List<Cue> cues) {
        sendMessage(MSG_PLAYER_SUBTITLE_CUES, cues);
    }

    @Override
    public void onDrmKeysLoaded() {
        eventLogger.onDrmKeysLoaded();
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        eventLogger.onDrmSessionManagerError(e);
        postFatalErrorInternal(new SRGDrmMediaPlayerException(e));
        if (akamaiExoPlayerLoader != null) {
            akamaiExoPlayerLoader.onDrmSessionManagerError(e);
        }
    }

    @Override
    public void onDrmKeysRestored() {
        eventLogger.onDrmKeysRestored();
    }

    @Override
    public void onDrmKeysRemoved() {
        eventLogger.onDrmKeysRemoved();
    }


    /**
     * Provide Akamai QOS Configuration.
     *
     * @param akamaiMediaAnalyticsConfiguration akamai qos configuration to enable QOS monitoring. null to disable
     *                                          akamai qos.
     */
    public void setAkamaiMediaAnalyticsConfiguration(@Nullable AkamaiMediaAnalyticsConfiguration akamaiMediaAnalyticsConfiguration) {
        this.akamaiMediaAnalyticsConfiguration = akamaiMediaAnalyticsConfiguration;
    }
}