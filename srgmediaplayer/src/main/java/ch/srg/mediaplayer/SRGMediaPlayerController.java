package ch.srg.mediaplayer;

import android.content.Context;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import ch.srg.mediaplayer.service.AudioIntentReceiver;

/**
 * Handle the playback of media.
 * if used in conjonction with a SRGMediaPlayerView can handle Video playback base on delegation on
 * actual players, like android.MediaPlayer or ExoPlayer
 */
public class SRGMediaPlayerController implements Handler.Callback,
        Player.EventListener,
        SimpleExoPlayer.VideoListener,
        AudioCapabilitiesReceiver.Listener,
        TextRenderer.Output {
    public static final String TAG = "SRGMediaPlayer";
    public static final String NAME = "SRGMediaPlayer";
    public static final String VERSION = BuildConfig.VERSION_NAME;
    public static final int DEFAULT_AUTO_HIDE_DELAY = OverlayController.DEFAULT_AUTO_HIDE_DELAY;

    private static final long[] EMPTY_TIME_RANGE = new long[2];
    private static final long UPDATE_PERIOD = 100;

    public enum ViewType {
        TYPE_SURFACEVIEW,
        TYPE_TEXTUREVIEW
    }

    /**
     * True when audio focus has been requested, does not reflect current focus (LOSS / DUCKED).
     */
    private boolean audioFocusRequested;
    @Nullable
    private Long currentSeekTarget;
    private boolean debugMode;
    private boolean pausedBecauseTransientFocusLoss;
    private boolean duckedBecauseTransientFocusLoss;
    private boolean pausedBecauseFocusLoss;
    private boolean mutedBecauseFocusLoss;
    private Long qualityOverride;
    private Long qualityDefault;
    private Throwable fatalError;
    private long controllerId;
    private static long controllerIdCounter;

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static final long UNKNOWN_TIME = -1;

    /**
     * Disable audio focus handling. Always play audio.
     */
    public static final int AUDIO_FOCUS_FLAG_DISABLED = 0;
    /**
     * Mute when losing audio focus.
     */
    public static final int AUDIO_FOCUS_FLAG_MUTE = 1;
    /**
     * Pause stream when losing audio focus. Do not auto restart unless AUDIO_FOCUS_FLAG_AUTO_RESTART is also set.
     */
    public static final int AUDIO_FOCUS_FLAG_PAUSE = 2;
    /**
     * Duck volume when losing audio focus.
     */
    public static final int AUDIO_FOCUS_FLAG_DUCK = 4;
    /**
     * If set, stream auto restart after gaining audio focus, must be used with AUDIO_FOCUS_FLAG_PAUSE to pause.
     */
    public static final int AUDIO_FOCUS_FLAG_AUTO_RESTART = 8;

    private static final int MSG_PREPARE_FOR_MEDIA_IDENTIFIER = 3;

    private static final int MSG_PREPARE_FOR_URI = 4;
    private static final int MSG_SET_PLAY_WHEN_READY = 5;
    private static final int MSG_SEEK_TO = 6;
    private static final int MSG_SET_MUTE = 7;
    private static final int MSG_APPLY_STATE = 8;
    private static final int MSG_RELEASE = 9;
    /*package*/ static final int MSG_DELEGATE_EXCEPTION = 12;
    private static final int MSG_REGISTER_EVENT_LISTENER = 13;
    private static final int MSG_UNREGISTER_EVENT_LISTENER = 14;
    private static final int MSG_PLAYER_DELEGATE_PREPARING = 101;
    private static final int MSG_PLAYER_DELEGATE_READY = 102;
    private static final int MSG_PLAYER_DELEGATE_BUFFERING = 103;
    private static final int MSG_PLAYER_DELEGATE_COMPLETED = 104;
    private static final int MSG_PLAYER_DELEGATE_PLAY_WHEN_READY_COMMITED = 105;
    private static final int MSG_PLAYER_DELEGATE_SUBTITLE_CUES = 106;
    private static final int MSG_PLAYER_DELEGATE_VIDEO_ASPECT_RATIO = 107;
    private static final int MSG_DATA_PROVIDER_EXCEPTION = 200;
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
        RELEASED,
    }

    /**
     * Interface definition for a callback to be invoked when the status changes or is periodically emitted.
     */
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

            MEDIA_READY_TO_PLAY,
            MEDIA_COMPLETED,
            MEDIA_STOPPED,

            OVERLAY_CONTROL_DISPLAYED,
            OVERLAY_CONTROL_HIDDEN,
            PLAYING_STATE_CHANGE,
            WILL_SEEK, // SEEK_STARTED
            DID_SEEK, // SEEK_STOPPED

            EXTERNAL_EVENT,

            DID_BIND_TO_PLAYER_VIEW,
            DID_UNBIND_FROM_PLAYER_VIEW,

            SUBTITLE_DID_CHANGE
        }

        public final Type type;

        public final String mediaIdentifier;

        public final String mediaUrl;
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
        public final SRGMediaPlayerException exception;

        private static Event buildTestEvent(SRGMediaPlayerController controller) {
            return new Event(Type.EXTERNAL_EVENT, controller, null);
        }

        private static Event buildEvent(SRGMediaPlayerController controller, Type eventType) {
            return new Event(eventType, controller, null);
        }

        private static Event buildErrorEvent(SRGMediaPlayerController controller, boolean fatalError, SRGMediaPlayerException exception) {
            return new Event(fatalError ? Type.FATAL_ERROR : Type.TRANSIENT_ERROR, controller, exception);
        }

        private static Event buildStateEvent(SRGMediaPlayerController controller) {
            return new Event(Type.STATE_CHANGE, controller, null);
        }

        private Event(Type eventType, SRGMediaPlayerController controller, SRGMediaPlayerException eventException) {
            type = eventType;
            tag = controller.tag;
            state = controller.state;
            exception = eventException;
            mediaIdentifier = controller.currentMediaIdentifier;
            mediaSessionId = controller.getMediaSessionId();
            mediaUrl = controller.currentMediaUrl;
            mediaPosition = controller.getMediaPosition();
            mediaDuration = controller.getMediaDuration();
            mediaPlaying = controller.isPlaying();
            mediaMuted = controller.muted;
            mediaLive = controller.isLive();
            mediaPlaylistStartTime = controller.getPlaylistStartTime();
            videoViewDimension = controller.mediaPlayerView != null ? controller.mediaPlayerView.getVideoRenderingViewSizeString() : SRGMediaPlayerView.UNKNOWN_DIMENSION;
            screenType = controller.getScreenType();
        }

        protected Event(SRGMediaPlayerController controller, SRGMediaPlayerException eventException) {
            this(Type.EXTERNAL_EVENT, controller, eventException);
        }

        public boolean hasException() {
            return type == Type.FATAL_ERROR || type == Type.TRANSIENT_ERROR || exception != null;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "type=" + type +
                    ", mediaIdentifier='" + mediaIdentifier + '\'' +
                    ", mediaUrl='" + mediaUrl + '\'' +
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

    private SRGMediaPlayerDataProvider mediaPlayerDataProvider;

    private State state = State.IDLE;

    private Boolean playWhenReady = true;

    private Long seekToWhenReady = null;

    //Used to force keepscreen on even when not playing
    private boolean externalWakeLock = false;

    private boolean muted = false;

    @Nullable
    private SimpleExoPlayer exoPlayer;
    private AudioIntentReceiver becomingNoisyReceiver;
    private DefaultTrackSelector trackSelector;
    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private AudioCapabilities audioCapabilities;
    private EventLogger eventLogger;
    private DefaultRenderersFactory renderersFactory;
    private ViewType viewType;
    private View renderingView;
    private Integer playbackState;

    @Nullable
    private SRGMediaPlayerView mediaPlayerView;

    private OverlayController overlayController;

    private String currentMediaIdentifier = null;

    private String currentMediaUrl = null;
    private String tag;

    //Main player property to handle multiple player view
    private boolean mainPlayer = true;

    private int audioFocusBehaviorFlag = AUDIO_FOCUS_FLAG_PAUSE;

    /**
     * Listeners registered to this player
     */
    private Set<Listener> eventListeners = Collections.newSetFromMap(new WeakHashMap<Listener, Boolean>());

    private static Set<Listener> globalEventListeners = Collections.newSetFromMap(new WeakHashMap<Listener, Boolean>());

    /**
     * Create a new SRGMediaPlayerController with the current context, a mediaPlayerDataProvider, and a TAG
     * if you need to retrieve a controller
     *
     * @param context
     * @param mediaPlayerDataProvider
     * @param tag
     */
    public SRGMediaPlayerController(Context context, SRGMediaPlayerDataProvider mediaPlayerDataProvider, String tag) {
        this.context = context;
        this.mediaPlayerDataProvider = mediaPlayerDataProvider;
        this.mainHandler = new Handler(Looper.getMainLooper(), this);
        this.tag = tag;

        overlayController = new OverlayController(this);
        registerEventListener(overlayController);

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        controllerId = ++controllerIdCounter;
    }

    private void createNewExoPlayerInstance() {
        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(context, this);

        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);

        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        eventLogger = new EventLogger(trackSelector);
        renderersFactory = new DefaultRenderersFactory(context, drmSessionManager, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, new DefaultLoadControl());
        exoPlayer.addListener(this);
        exoPlayer.setVideoListener(this);
        exoPlayer.setTextOutput(this);
        exoPlayer.setAudioDebugListener(eventLogger);
        exoPlayer.setVideoDebugListener(eventLogger);
        exoPlayer.setMetadataOutput(eventLogger);
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
     * Start a video from beginning
     *
     * @see #play(String, Long)
     */
    public boolean play(String mediaIdentifier) throws SRGMediaPlayerException {
        return play(mediaIdentifier, null);
    }

    /**
     * Try to play a video with a mediaIdentifier, you can't replay the current playing video.
     * will throw an exception if you haven't setup a data provider or if the media is not present
     * in the provider.
     * <p/>
     * The corresponding events are triggered when the video loading start and is ready.
     *
     * @param mediaIdentifier identifier
     * @param startPositionMs start position in milliseconds or null to prevent seek
     * @return true when media is preparing and in the process of being started
     * @throws SRGMediaPlayerException
     */
    public boolean play(String mediaIdentifier, Long startPositionMs) throws SRGMediaPlayerException {
        if (mediaPlayerDataProvider == null) {
            throw new IllegalStateException("No data provider set before play");
        }
        if (requestAudioFocus()) {
            if (!TextUtils.equals(currentMediaIdentifier, mediaIdentifier)) {
                sendMessage(MSG_PREPARE_FOR_MEDIA_IDENTIFIER, mediaIdentifier);
                start();
            }
            if (startPositionMs != null) {
                seekTo(startPositionMs);
            }
            return true;
        } else {
            Log.v(TAG, "Audio focus request failed");
            return false;
        }
    }

    public void keepScreenOn(boolean lock) {
        externalWakeLock = lock;
        manageKeepScreenOnInternal();
    }

    class PrepareUriData {
        Uri uri;
        Long position;
        String mediaIdentifier;
        int streamType;

        public PrepareUriData(Uri uri, String mediaIdentifier, Long position, int streamType) {
            this.uri = uri;
            this.mediaIdentifier = mediaIdentifier;
            this.position = position;
            this.streamType = streamType;
        }

        @Override
        public String toString() {
            return uri.toString();
        }
    }

    /**
     * Resume playing after a pause call or make the controller start immediately after the preparation phase.
     */
    public void start() {
        if (!hasLostAudioFocus()) {
            sendMessage(MSG_SET_PLAY_WHEN_READY, true);
        }
    }

    /**
     * Pause the current media or prevent it from starting immediately if controller in preparation phase.
     */
    public void pause() {
        resetAudioFocusResume();
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
        currentSeekTarget = positionMs;
        sendMessage(MSG_SEEK_TO, positionMs);
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

    /*package*/ void sendMessage(int what, Object param) {
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
     * @return
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
        switch (msg.what) {
            case MSG_PREPARE_FOR_MEDIA_IDENTIFIER:
                String mediaIdentifier = (String) msg.obj;
                releaseDelegateInternal();
                prepareForIdentifierInternal(mediaIdentifier);
                seekToWhenReady = null;
                return true;

            case MSG_PREPARE_FOR_URI:
                createPlayerInternal();
                PrepareUriData data = (PrepareUriData) msg.obj;
                Uri uri = data.uri;
                if (seekToWhenReady == null) {
                    // TODO
                    // Here we have an issue: we handle restore to position only when the dataprovider
                    // does not give us a position to seek to (segment mark in in IL case).
                    // When the dataprovider does give a position to seek to, we don't know which
                    // position to take as the seekto could have occurred before or
                    // after the seek. And we don't know the segment range either...
                    seekToWhenReady = data.position;
                }

                currentMediaIdentifier = data.mediaIdentifier;
                postEventInternal(Event.Type.MEDIA_READY_TO_PLAY);
                try {
                    if (mediaPlayerView != null) {
                        internalUpdateMediaPlayerViewBound();
                    }
                    exoPlayer.setPlayWhenReady(playWhenReady);
                    if (seekToWhenReady != null) {
                        try {
                            exoPlayer.seekTo(seekToWhenReady);
                            seekToWhenReady = null;
                        } catch (IllegalStateException ignored) {
                        }
                    }
                    prepareInternal(uri, data.streamType);
                } catch (SRGMediaPlayerException e) {
                    logE("onUriLoaded", e);
                    handleFatalExceptionInternal(e);
                }
                return true;

            case MSG_SET_PLAY_WHEN_READY:
                this.playWhenReady = (Boolean) msg.obj;
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(playWhenReady);
                }
                return true;

            case MSG_SEEK_TO:
                Long positionMs = (Long) msg.obj;
                if (positionMs == null) {
                    throw new IllegalArgumentException("Missing position for seek to");
                } else {
                    if (state != State.PREPARING) {
                        postEventInternal(Event.Type.WILL_SEEK);
                        seekToWhenReady = positionMs;
                        if (exoPlayer != null) {
                            try {
                                exoPlayer.seekTo(seekToWhenReady);
                                seekToWhenReady = null;
                            } catch (IllegalStateException ignored) {
                            }
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

            case MSG_DELEGATE_EXCEPTION:
            case MSG_DATA_PROVIDER_EXCEPTION:
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

            case MSG_PLAYER_DELEGATE_PREPARING:
                setStateInternal(State.PREPARING);
                return true;

            case MSG_PLAYER_DELEGATE_READY:
                startBecomingNoisyReceiver();
                setStateInternal(State.READY);
                applyStateInternal();
                return true;

            case MSG_PLAYER_DELEGATE_BUFFERING:
                setStateInternal(State.BUFFERING);
                return true;

            case MSG_PLAYER_DELEGATE_COMPLETED:
                setStateInternal(State.READY);
                postEventInternal(Event.Type.MEDIA_COMPLETED);
                releaseInternal();
                return true;

            case MSG_PLAYER_DELEGATE_PLAY_WHEN_READY_COMMITED:
                postEventInternal(Event.Type.PLAYING_STATE_CHANGE);
                return true;

            case MSG_PLAYER_DELEGATE_SUBTITLE_CUES:
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

            case MSG_PLAYER_DELEGATE_VIDEO_ASPECT_RATIO:
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
                periodicUpdateInteral();
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

    private void prepareInternal(Uri videoUri, int streamType) throws SRGMediaPlayerException {
        Log.v(TAG, "Preparing " + videoUri + " (" + streamType + ")");
        try {
            String videoSourceUrl = videoUri.toString();
            if (this.currentMediaUrl != null && this.currentMediaUrl.equalsIgnoreCase(videoSourceUrl)) {
                return;
            }
            sendMessage(MSG_PLAYER_DELEGATE_PREPARING);
            this.currentMediaUrl = videoSourceUrl;

            String userAgent = "curl/Letterbox_2.0"; // temporarily using curl/ user agent to force subtitles with Akamai beta

            DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(userAgent, BANDWIDTH_METER);
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, BANDWIDTH_METER, httpDataSourceFactory);

            MediaSource mediaSource;

            switch (streamType) {
                case SRGMediaPlayerDataProvider.STREAM_DASH:
                    mediaSource = new DashMediaSource(videoUri, dataSourceFactory,
                            new DefaultDashChunkSource.Factory(dataSourceFactory), mainHandler, eventLogger);
                    break;
                case SRGMediaPlayerDataProvider.STREAM_HLS:
                    mediaSource = new HlsMediaSource(videoUri, dataSourceFactory, mainHandler, eventLogger);
                    break;
                case SRGMediaPlayerDataProvider.STREAM_HTTP_PROGRESSIVE:
                    mediaSource = new ExtractorMediaSource(videoUri, dataSourceFactory, new DefaultExtractorsFactory(),
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

            if (exoPlayer != null) {
                exoPlayer.prepare(mediaSource);
            }

        } catch (Exception e) {
            release();
            throw new SRGMediaPlayerException(e);
        }
    }

    private void muteInternal(boolean muted) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(muted ? 0f : 1f);
        }
    }

    private void startBecomingNoisyReceiver() {
        becomingNoisyReceiver = new AudioIntentReceiver(this);
        context.registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    private void periodicUpdateInteral() {
        if (exoPlayer == null) {
            currentSeekTarget = null;
        } else {
            if (currentSeekTarget != null) {
                long currentPosition = exoPlayer.getCurrentPosition();
                if (currentPosition != UNKNOWN_TIME
                        && currentPosition != currentSeekTarget
                        || !playWhenReady) {
                    currentSeekTarget = null;
                    postEventInternal(Event.Type.DID_SEEK);
                }
            }
        }
    }

    public void schedulePeriodUpdate() {
        commandHandler.removeMessages(MSG_PERIODIC_UPDATE);
        commandHandler.sendMessageDelayed(
                commandHandler.obtainMessage(MSG_PERIODIC_UPDATE), UPDATE_PERIOD);
    }

    private void applyStateInternal() {
        if (exoPlayer != null) {
            muteInternal(muted);
            exoPlayer.setPlayWhenReady(playWhenReady);
            Long seekTarget = this.seekToWhenReady;
            if (seekTarget != null) {
                postEventInternal(Event.Type.WILL_SEEK);
                Log.v(TAG, "Apply state / Seeking to " + seekTarget);
                try {
                    exoPlayer.seekTo(seekTarget);
                    currentSeekTarget = seekTarget;
                    this.seekToWhenReady = null;
                } catch (IllegalStateException ignored) {
                }
            }
            if (playWhenReady) {
                schedulePeriodUpdate();
            }
        }
    }

    private void prepareForIdentifierInternal(String mediaIdentifier) {
        setStateInternal(State.PREPARING);
        if (mediaIdentifier == null) {
            throw new IllegalArgumentException("Media identifier is null in prepare for identifier");
        }
        currentMediaIdentifier = mediaIdentifier;
        currentMediaUrl = null;

        mediaPlayerDataProvider.getUri(mediaIdentifier, SRGMediaPlayerDataProvider.PLAYER_TYPE_EXOPLAYER, new SRGMediaPlayerDataProvider.GetUriCallback() {
            @Override
            public void onUriLoaded(String mediaIdentifier, Uri uri, String realMediaIdentifier, Long position, int streamType) {
                if (realMediaIdentifier == null) {
                    throw new IllegalArgumentException("realMediaIdentifier may not be null");
                }
                sendMessage(MSG_PREPARE_FOR_URI, new PrepareUriData(uri, realMediaIdentifier, position, streamType));
            }

            @Override
            public void onUriLoadFailed(String mediaIdentifier, SRGMediaPlayerException exception) {
                sendMessage(MSG_DATA_PROVIDER_EXCEPTION, exception);
            }
        });
    }

    private void createPlayerInternal() {
        releaseDelegateInternal();
        createNewExoPlayerInstance();
    }

    private void manageKeepScreenOnInternal() {
        final boolean lock = externalWakeLock || (playWhenReady && isPlaying());
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
        if (exoPlayer != null) {
            postEventInternal(Event.Type.MEDIA_STOPPED);
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
            currentMediaIdentifier = null;
            currentMediaUrl = null;
            seekToWhenReady = null;
        }
    }

    public State getState() {
        return state;
    }

    /*package*/ void handleFatalExceptionInternal(SRGMediaPlayerException e) {
        logE("exception occurred", e);
        postErrorEventInternal(true, e);
        releaseInternal();
    }

    /**
     * Release the current player. Once the player is released you have to create a new player
     * if you want to play a new video.
     */
    public void release() {
        if (mediaPlayerView != null) {
            showControlOverlays();
            unbindFromMediaPlayerView(mediaPlayerView);
        }
        if (becomingNoisyReceiver != null) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver);
            } catch (IllegalArgumentException ignored) {
                // Prevent crash if race condition during receiver unregistering
                Log.e(TAG, "Becoming noisy receiver was not registered");
            }
            becomingNoisyReceiver = null;
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
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_READY && exoPlayer.getPlayWhenReady();
    }

    /**
     * Return the current mediaIdentifier played.
     */
    @Nullable
    public String getMediaIdentifier() {
        return currentMediaIdentifier;
    }

    /**
     * @return media position relative to MPST (see {@link #getMediaPlaylistStartTime} )
     */
    public long getMediaPosition() {
        if (exoPlayer != null) {
            Long seekToWhenReady = this.seekToWhenReady;
            if (seekToWhenReady != null) {
                return seekToWhenReady;
            } else {
                return exoPlayer.getCurrentPosition();
            }
        } else {
            return UNKNOWN_TIME;
        }
    }

    /**
     * @return Media duration relative to 0.
     */
    public long getMediaDuration() {
        if (exoPlayer != null) {
            return exoPlayer.getDuration();
        } else {
            return UNKNOWN_TIME;
        }
    }

    /**
     * Media playlist start time (MPST) is a relative offset for the available seekable range,
     * used in sliding window live playlist.
     * The range [0..MPST] is not available for seeking.
     * <p/>
     * <pre>
     * 0 --------------- MPST --------- POSITION ------------------------------------- LIVE
     *                    \---------------------------DURATION---------------------------/
     * </pre>
     * <p/>
     * MPST stays constant with a value of 0 when playing a static video.
     *
     * @return MPST in ms
     */
    @Deprecated
    public long getMediaPlaylistStartTime() {
        return 0;
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
        if (exoPlayer != null) {
            return getPlaylistStartTime();
        } else {
            return UNKNOWN_TIME;
        }
    }

    public long getBufferPosition() {
        if (exoPlayer != null) {
            return exoPlayer.getBufferedPosition();
        } else {
            return UNKNOWN_TIME;
        }
    }

    public int getBufferPercentage() {
        if (exoPlayer != null) {
            return exoPlayer.getBufferedPercentage();
        } else {
            return 0;
        }
    }

    public void showControlOverlays() {
        overlayController.showControlOverlays();
    }

    public void hideControlOverlays() {
        overlayController.hideControlOverlaysImmediately();
    }

    public void toggleOverlay() {
        if (overlayController.isControlsVisible()) {
            overlayController.hideControlOverlaysImmediately();
        } else {
            overlayController.showControlOverlays();
        }
    }

    public void setForceLoaders(Boolean forceLoaders) {
        overlayController.setForceLoaders(forceLoaders);
    }

    public void setForceControls(Boolean forceControls) {
        overlayController.setForceControls(forceControls);
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
        overlayController.bindToVideoContainer(this.mediaPlayerView);
        manageKeepScreenOnInternal();
    }

    @Nullable
    public SRGMediaPlayerView getMediaPlayerView() {
        return mediaPlayerView;
    }

    private void internalUpdateMediaPlayerViewBound() {
        //Both not null
        if (mediaPlayerView != null && exoPlayer != null) {

            if (!canRenderInView(mediaPlayerView.getVideoRenderingView())) {
                // We need to create a new rendering view.
                createRenderingViewInMainThread(mediaPlayerView.getContext());
                Log.v(TAG, renderingView + "binding, creating rendering view" + mediaPlayerView);
            } else {
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

        } else
            // mediaPlayerView null, just unbind delegate
            if (mediaPlayerView == null) {
                unbindRenderingView();
            }
        //Other cases are :
        // - both mediaPlayerView and delegate null, do nothing
        // - delegate null only, mediaPlayerView already stored as class attributes and will be set when needed
    }

    private void bindRenderingViewInUiThread() throws SRGMediaPlayerException {
        if (mediaPlayerView == null ||
                !canRenderInView(mediaPlayerView.getVideoRenderingView())) {
            throw new SRGMediaPlayerException("ExoPlayerDelegate cannot render video in a "
                    + mediaPlayerView);
        }
        pushSurface(false);
        broadcastEvent(Event.Type.DID_BIND_TO_PLAYER_VIEW);
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (exoPlayer != null) {
            if (renderingView instanceof SurfaceView) {
                exoPlayer.setVideoSurfaceView((SurfaceView) renderingView);
            } else if (renderingView instanceof TextureView) {
                exoPlayer.setVideoTextureView((TextureView) renderingView);
            }
        }
    }

    /**
     * Clear the current mediaPlayer, unbind the delegate and the overlayController
     *
     * @param playerView video container to unbind from.
     */
    public void unbindFromMediaPlayerView(SRGMediaPlayerView playerView) {
        if (mediaPlayerView == playerView) {
            overlayController.bindToVideoContainer(null);
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
                                if (exoPlayer != null && ((SurfaceView) renderingView).getHolder() == holder) {
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
                        public boolean isCurrent(SurfaceTexture surfaceTexture) {
                            return renderingView instanceof TextureView && ((TextureView) renderingView).getSurfaceTexture() == surfaceTexture;
                        }

                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                            Log.v(TAG, renderingView + "binding, surfaceTextureAvailable" + mediaPlayerView);
                            if (exoPlayer != null && isCurrent(surfaceTexture)) {
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
        if (exoPlayer != null) {
            exoPlayer.clearVideoSurface();
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

    public void broadcastEvent(Event.Type eventType) {
        broadcastEvent(Event.buildEvent(this, eventType));
    }

    public void broadcastEvent(Event event) {
        sendMessage(MSG_FIRE_EVENT, event);
    }

    private void postErrorEventInternal(boolean fatalError, SRGMediaPlayerException e) {
        if (fatalError) {
            this.fatalError = e;
        }
        postEventInternal(Event.buildErrorEvent(this, fatalError, e));
    }

    private void postEventInternal(Event.Type eventType) {
        postEventInternal(Event.buildEvent(this, eventType));
    }

    public void postEventInternal(final Event event) {
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

    public void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                handleAudioFocusLoss(false, false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                handleAudioFocusLoss(true, focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                handleAudioFocusGain();
                break;
        }
    }

    private void handleAudioFocusGain() {
        if (duckedBecauseTransientFocusLoss) {
            unmute();
        }
        if (pausedBecauseFocusLoss && ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_AUTO_RESTART) != 0 || pausedBecauseTransientFocusLoss)) {
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
        if (audioFocusBehaviorFlag == 0 || audioFocusRequested) {
            return true;
        } else {
            Log.d(TAG, "Request audio focus");
            final WeakReference<SRGMediaPlayerController> selfReference = new WeakReference<>(this);

            int result = audioManager.requestAudioFocus(
                    new OnAudioFocusChangeListener(selfReference),
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusRequested = true;
                return true;
            } else {
                logE("Could not get audio focus granted...");
                return false;
            }
        }
    }

    private void abandonAudioFocus() {
        if (audioFocusRequested && audioFocusBehaviorFlag != 0) {
            Log.d(TAG, "Abandon audio focus");

            audioManager.abandonAudioFocus(null);
            audioFocusRequested = false;
        }
    }

    private static class OnAudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
        private final WeakReference<SRGMediaPlayerController> playerReference;

        public OnAudioFocusChangeListener(WeakReference<SRGMediaPlayerController> playerReference) {
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
        return new Event(eventType, controller, eventException);
    }

    public String getControllerId() {
        return String.valueOf(controllerId);
    }

    public String getMediaSessionId() {
        return getControllerId();
    }

    public void updateOverlayVisibilities() {
        overlayController.propagateOverlayVisibility();
    }

    private long getPlaylistStartTime() {
        long res = 0;
        if (exoPlayer != null && isLive()) {
            res = System.currentTimeMillis();
        }
        return res;
    }

    public boolean isLive() {
        return exoPlayer != null && exoPlayer.isCurrentWindowDynamic();
    }

    public boolean isShowingControlOverlays() {
        if (overlayController != null) {
            return overlayController.isShowingControlOverlays();
        }
        return true;
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

    public void forceBroadcastStateChange() {
        broadcastEvent(Event.buildStateEvent(this));
    }

    /**
     * Configure auto hide delay (delay to change visibility for overlay of OVERLAY_CONTROL type)
     *
     * @param overlayAutoHideDelay auto hide delay in ms
     */
    public static void setOverlayAutoHideDelay(int overlayAutoHideDelay) {
        OverlayController.setOverlayAutoHideDelay(overlayAutoHideDelay);
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
        if (exoPlayer != null) {
            Format videoFormat = exoPlayer.getVideoFormat();
            Format audioFormat = exoPlayer.getAudioFormat();
            int videoBandwidth = videoFormat != null && videoFormat.bitrate != Format.NO_VALUE ? videoFormat.bitrate : 0;
            int audioBandwidth = audioFormat != null && audioFormat.bitrate != Format.NO_VALUE ? audioFormat.bitrate : 0;
            long bandwidth = videoBandwidth + audioBandwidth;
            return bandwidth > 0 ? bandwidth : null;
        } else {
            return null;
        }
    }

    public boolean hasVideoTrack() {
        if (exoPlayer != null) {
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
        return false;
    }

    public Throwable getFatalError() {
        return fatalError;
    }

    @NonNull
    public List<SubtitleTrack> getSubtitleTrackList() {
        List<SubtitleTrack> result = new ArrayList<>();
        if (exoPlayer != null) {
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
        } else {
            result = Collections.emptyList();
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
        if (exoPlayer != null) {
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
    }

    @Nullable
    public SubtitleTrack getSubtitleTrack() {
        if (exoPlayer != null) {
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
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo != null) {
            for (int i = 0; i < mappedTrackInfo.length; i++) {
                if (exoPlayer != null
                        && mappedTrackInfo.getTrackGroups(i).length > 0
                        && exoPlayer.getRendererType(i) == C.TRACK_TYPE_TEXT) {
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
        return getState() == SRGMediaPlayerController.State.PREPARING
                || getState() == SRGMediaPlayerController.State.BUFFERING
                || isSeekPending();
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
        // TODO Should we really ignore this ?
        // Ignore
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.v(TAG, toString() + " exo state change: " + playWhenReady + " " + playbackState);
        if (this.playbackState == null || this.playbackState != playbackState) {
            switch (playbackState) {
                case Player.STATE_IDLE:
                    // TODO Why ?
                    //controller.onPlayerDelegateStateChanged(this, SRGMediaPlayerController.State.IDLE);
                    break;
                case Player.STATE_BUFFERING:
                    sendMessage(MSG_PLAYER_DELEGATE_BUFFERING);
                    break;
                case Player.STATE_READY:
                    manageKeepScreenOnInternal();
                    sendMessage(MSG_PLAYER_DELEGATE_READY);
                    break;
                case Player.STATE_ENDED:
                    manageKeepScreenOnInternal();
                    sendMessage(MSG_PLAYER_DELEGATE_COMPLETED);
                    break;
            }
            this.playbackState = playbackState;
        }
        if (this.playWhenReady == null || this.playWhenReady != playWhenReady) {
            manageKeepScreenOnInternal();
            sendMessage(MSG_PLAYER_DELEGATE_PLAY_WHEN_READY_COMMITED);
            this.playWhenReady = playWhenReady;
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Ignore
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        manageKeepScreenOnInternal();
        sendMessage(MSG_DELEGATE_EXCEPTION, error);
    }

    @Override
    public void onPositionDiscontinuity() {
        // Ignore
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Ignore
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (exoPlayer != null) {
            float aspectRatio = ((float) width / (float) height) * pixelWidthHeightRatio;
            if ((aspectRatio / 90) % 2 == 1) {
                aspectRatio = 1 / aspectRatio;
            }
            sendMessage(MSG_PLAYER_DELEGATE_VIDEO_ASPECT_RATIO, aspectRatio);
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        // TODO Should we ignore this ?
        // Ignore
    }

    @Override
    public void onCues(List<Cue> cues) {
        sendMessage(MSG_PLAYER_DELEGATE_SUBTITLE_CUES, cues);
    }
}