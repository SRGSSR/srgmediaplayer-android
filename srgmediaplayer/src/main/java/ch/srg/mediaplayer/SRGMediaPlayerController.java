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
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.internal.exoplayer.ExoPlayerDelegate;
import ch.srg.mediaplayer.internal.nativeplayer.NativePlayerDelegate;

/**
 * Handle the playback of media.
 * if used in conjonction with a SRGMediaPlayerView can handle Video playback base on delegation on
 * actual players, like android.MediaPlayer or ExoPlayer
 */
public class SRGMediaPlayerController implements PlayerDelegate.OnPlayerDelegateListener, Handler.Callback {

    public static final String TAG = "SRGMediaPlayer";
    public static final String NAME = "SRGMediaPlayer";
    public static final String VERSION = "0.0.2";
    private static final long[] EMPTY_TIME_RANGE = new long[2];
    private static final long UPDATE_PERIOD = 100;

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

    public static String getName() {
        return NAME;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static final long UNKNOWN_TIME = PlayerDelegate.UNKNOWN_TIME;

    public static final int AUDIO_FOCUS_FLAG_MUTE = 1;

    public static final int AUDIO_FOCUS_FLAG_PAUSE = 2;
    private static final int MSG_PREPARE_FOR_MEDIA_IDENTIFIER = 3;
    public static final int AUDIO_FOCUS_FLAG_DUCK = 4;
    public static final int AUDIO_FOCUS_FLAG_AUTO_RESTART = 8;

    private static final int MSG_PREPARE_FOR_URI = 4;
    private static final int MSG_SET_PLAY_WHEN_READY = 5;
    private static final int MSG_SEEK_TO = 6;
    private static final int MSG_SET_MUTE = 7;
    private static final int MSG_APPLY_STATE = 8;
    private static final int MSG_RELEASE = 9;
    /*package*/ static final int MSG_DELEGATE_EXCEPTION = 12;
    private static final int MSG_REGISTER_EVENT_LISTENER = 13;
    private static final int MSG_UNREGISTER_EVENT_LISTENER = 14;
    private static final int MSG_SWAP_PLAYER_DELEGATE = 15;
    private static final int MSG_PLAYER_DELEGATE_PREPARING = 16;
    private static final int MSG_PLAYER_DELEGATE_READY = 17;
    private static final int MSG_PLAYER_DELEGATE_BUFFERING = 18;
    private static final int MSG_PLAYER_DELEGATE_COMPLETED = 19;
    private static final int MSG_DATA_PROVIDER_EXCEPTION = 20;
    private static final int MSG_PERIODIC_UPDATE = 21;
    private static final int MSG_FIRE_EVENT = 22;

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

            EXTERNAL_EVENT;
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

        public static Event buildEvent(SRGMediaPlayerController controller, Type eventType) {
            return new Event(eventType, controller, null);
        }

        public static Event buildErrorEvent(SRGMediaPlayerController controller, boolean fatalError, SRGMediaPlayerException exception) {
            return new Event(fatalError ? Type.FATAL_ERROR : Type.TRANSIENT_ERROR, controller, exception);
        }

        public static Event buildStateEvent(SRGMediaPlayerController controller) {
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
            final StringBuilder sb = new StringBuilder("Event{");
            sb.append("type=").append(type);
            sb.append(", state=").append(state);
            sb.append(", mediaIdentifier='").append(mediaIdentifier).append('\'');
            sb.append(", mediaPosition=").append(mediaPosition);
            sb.append(", mediaDuration=").append(mediaDuration);
            sb.append(", mediaPlaying=").append(mediaPlaying);
            sb.append(", videoViewDimension='").append(videoViewDimension).append('\'');
            sb.append(", tag='").append(tag).append('\'');
            sb.append(", exception=").append(exception);
            sb.append(", mediasession=").append(mediaSessionId);
            sb.append('}');
            return sb.toString();
        }
    }

    private Event.ScreenType getScreenType() {
        PlayerDelegate delegate = this.currentMediaPlayerDelegate;
        return delegate == null ? Event.ScreenType.NONE : delegate.getScreenType();
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
    private HandlerThread commandHandlerThread;

    private Handler commandHandler;
    private final AudioManager audioManager;

    private SRGMediaPlayerDataProvider mediaPlayerDataProvider;

    private DataProviderAsyncTask dataProviderAsyncTask;
    private State state = State.IDLE;

    private boolean playWhenReady = true;

    private Long seekToWhenReady = null;

    //Used to force keepscreen on even when not playing
    private boolean externalWakeLock = false;

    private boolean muted = false;
    private PlayerDelegateFactory playerDelegateFactory;

    @Nullable
    private PlayerDelegate currentMediaPlayerDelegate;
    private SRGMediaPlayerView mediaPlayerView;

    private OverlayController overlayController;

    private String currentMediaIdentifier = null;

    private String currentMediaUrl = null;
    private String tag;

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

        overlayController = new OverlayController(this, mainHandler);
        registerEventListener(overlayController);

        playerDelegateFactory = new PlayerDelegateFactory() {
            @Override
            public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
                if (ExoPlayerDelegate.isSupported()) {
                    return new ExoPlayerDelegate(SRGMediaPlayerController.this.context,
                            SRGMediaPlayerController.this, ExoPlayerDelegate.SourceType.HLS);
                } else {
                    return new NativePlayerDelegate(SRGMediaPlayerController.this);
                }
            }
        };

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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

    @Override
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
        PlayerDelegate playerDelegate;

        public PrepareUriData(Uri uri, PlayerDelegate playerDelegate) {
            this.uri = uri;
            this.playerDelegate = playerDelegate;
        }

        @Override
        public String toString() {
            return uri + "/" + playerDelegate;
        }
    }

    /*package*/ void onUriLoaded(Uri uri, PlayerDelegate playerDelegate) {
        sendMessage(MSG_PREPARE_FOR_URI, new PrepareUriData(uri, playerDelegate));
    }

    /*package*/ void onDataProviderException(SRGMediaPlayerException e) {
        sendMessage(MSG_DATA_PROVIDER_EXCEPTION, e);
    }

    public void start() {
        sendMessage(MSG_SET_PLAY_WHEN_READY, true);
    }

    public void pause() {
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
     *</p>
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
    public boolean handleMessage(Message msg) {
        if (isReleased()) {
            logE("handleMessage when released: skipping " + msg);
            return true;
        }
        logV("handleMessage: " + msg);
        switch (msg.what) {
            case MSG_PREPARE_FOR_MEDIA_IDENTIFIER: {
                String mediaIdentifier = (String) msg.obj;
                releaseDelegateInternal();
                prepareForIdentifierInternal(mediaIdentifier, null);
                seekToWhenReady = null;
                return true;
            }
            case MSG_PREPARE_FOR_URI: {
                PrepareUriData data = (PrepareUriData) msg.obj;
                Uri uri = data.uri;
                PlayerDelegate playerDelegate = data.playerDelegate;

                currentMediaUrl = String.valueOf(uri);
                postEventInternal(Event.Type.MEDIA_READY_TO_PLAY);
                try {
                    if (playerDelegate == null) {
                        createPlayerDelegateInternal(currentMediaIdentifier);
                    } else {
                        currentMediaPlayerDelegate = playerDelegate;
                    }
                    if (currentMediaPlayerDelegate != null) {
                        if (mediaPlayerView != null) {
                            internalUpdateMediaPlayerViewBound();
                        }
                        currentMediaPlayerDelegate.playIfReady(playWhenReady);
                        if (seekToWhenReady != null) {
                            try {
                                currentMediaPlayerDelegate.seekTo(seekToWhenReady);
                                seekToWhenReady = null;
                            } catch (IllegalStateException ignored) {
                            }
                        }
                        currentMediaPlayerDelegate.prepare(uri);
                    }
                } catch (SRGMediaPlayerException e) {
                    logE("onUriLoaded", e);
                    handleFatalExceptionInternal(e);
                }
                return true;
            }
            case MSG_SET_PLAY_WHEN_READY: {
                this.playWhenReady = (Boolean) msg.obj;
                if (currentMediaPlayerDelegate != null) {
                    currentMediaPlayerDelegate.playIfReady(playWhenReady);
                }
                return true;
            }
            case MSG_SEEK_TO: {
                Long positionMs = (Long) msg.obj;
                if (positionMs == null) {
                    throw new IllegalArgumentException("Missing position for seek to");
                } else {
                    if (state == State.PREPARING) {
                        seekToWhenReady = positionMs;
                    } else {
                        postEventInternal(Event.Type.WILL_SEEK);
                        seekToWhenReady = positionMs;
                        if (currentMediaPlayerDelegate != null) {
                            try {
                                currentMediaPlayerDelegate.seekTo(seekToWhenReady);
                                seekToWhenReady = null;
                            } catch (IllegalStateException ignored) {
                            }
                        }
                    }
                }
                return true;
            }
            case MSG_SET_MUTE: {
                if (this.muted != (Boolean) msg.obj) {
                    this.muted = (Boolean) msg.obj;
                    if (currentMediaPlayerDelegate != null) {
                        currentMediaPlayerDelegate.setMuted(muted);
                    }
                }
                return true;
            }
            case MSG_APPLY_STATE: {
                applyStateInternal();
                return true;
            }
            case MSG_RELEASE: {
                releaseInternal();
                return true;
            }
            case MSG_DELEGATE_EXCEPTION:
            case MSG_DATA_PROVIDER_EXCEPTION: {
                handleFatalExceptionInternal((SRGMediaPlayerException) msg.obj);
                return true;
            }
            case MSG_REGISTER_EVENT_LISTENER: {
                eventListeners.add((Listener) msg.obj);
                return true;
            }
            case MSG_UNREGISTER_EVENT_LISTENER: {
                eventListeners.remove((Listener) msg.obj);
                return true;
            }
            case MSG_SWAP_PLAYER_DELEGATE: {
                PlayerDelegate oldDelegate = currentMediaPlayerDelegate;
                PlayerDelegate newDelegate = (PlayerDelegate) msg.obj;
                if (oldDelegate != null) {
                    currentMediaPlayerDelegate = null;
                    if (seekToWhenReady == null) {
                        seekToWhenReady = oldDelegate.getCurrentPosition();
                    }
                    oldDelegate.unbindRenderingView();
                    try {
                        oldDelegate.release();
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Illegal state exception when releasing delegate");
                    }
                    prepareForIdentifierInternal(currentMediaIdentifier, newDelegate);
                }
                return true;
            }
            case MSG_PLAYER_DELEGATE_PREPARING:
                setStateInternal(State.PREPARING);
                return true;
            case MSG_PLAYER_DELEGATE_READY:
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
            case MSG_PERIODIC_UPDATE:
                periodicUpdateInteral();
                if (isPlaying()) {
                    schedulePeriodUpdate();
                }
                return true;
            case MSG_FIRE_EVENT:
                this.postEventInternal((Event) msg.obj);
                return true;
            default: {
                String message = "Unknown message: " + msg.what + " / " + msg.obj;
                if (isDebugMode()) {
                    throw new IllegalArgumentException(message);
                } else {
                    logE(message);
                    return false;
                }
            }
        }
    }

    private void periodicUpdateInteral() {
        if (currentSeekTarget != null) {
            if (currentMediaPlayerDelegate == null) {
                currentSeekTarget = null;
            } else {
                long currentPosition = currentMediaPlayerDelegate.getCurrentPosition();
                if (currentPosition != UNKNOWN_TIME && currentPosition != currentSeekTarget) {
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
        if (currentMediaPlayerDelegate != null) {
            currentMediaPlayerDelegate.setMuted(muted);
            currentMediaPlayerDelegate.playIfReady(playWhenReady);
            Long seekTarget = this.seekToWhenReady;
            if (seekTarget != null) {
                postEventInternal(Event.Type.WILL_SEEK);
                Log.v(TAG, "Apply state / Seeking to " + seekTarget);
                try {
                    currentMediaPlayerDelegate.seekTo(seekTarget);
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

    private void prepareForIdentifierInternal(String mediaIdentifier, PlayerDelegate playerDelegate) {
        setStateInternal(State.PREPARING);
        if (mediaIdentifier == null) {
            throw new IllegalArgumentException("Media identifier is null in prepare for identifier");
        }
        currentMediaIdentifier = mediaIdentifier;
        currentMediaUrl = null;
        if (dataProviderAsyncTask != null) {
            dataProviderAsyncTask.cancel(true);
        }
        dataProviderAsyncTask = new DataProviderAsyncTask(this, mediaPlayerDataProvider, playerDelegate);
        dataProviderAsyncTask.execute(mediaIdentifier);
    }

    private void createPlayerDelegateInternal(String mediaIdentifier) {
        releaseDelegateInternal();
        currentMediaPlayerDelegate = playerDelegateFactory.getDelegateForMediaIdentifier(this, mediaIdentifier);
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
        if (currentMediaPlayerDelegate != null) {
            postEventInternal(Event.Type.MEDIA_STOPPED);
            currentMediaPlayerDelegate.release();
            currentMediaPlayerDelegate = null;
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

    @Override
    public void onPlayerDelegatePreparing(PlayerDelegate delegate) {
        if (delegate == currentMediaPlayerDelegate) {
            sendMessage(MSG_PLAYER_DELEGATE_PREPARING);
        }
    }

    @Override
    public void onPlayerDelegateReady(PlayerDelegate delegate) {
        manageKeepScreenOnInternal();
        if (delegate == currentMediaPlayerDelegate) {
            sendMessage(MSG_PLAYER_DELEGATE_READY);
        }
    }

    @Override
    public void onPlayerDelegatePlayWhenReadyCommited(PlayerDelegate delegate) {
        manageKeepScreenOnInternal();
        if (delegate == currentMediaPlayerDelegate) {
            postEventInternal(Event.Type.PLAYING_STATE_CHANGE);
        }
    }

    @Override
    public void onPlayerDelegateBuffering(PlayerDelegate delegate) {
        if (delegate == currentMediaPlayerDelegate) {
            sendMessage(MSG_PLAYER_DELEGATE_BUFFERING);
        }
    }

    @Override
    public void onPlayerDelegateCompleted(PlayerDelegate delegate) {
        manageKeepScreenOnInternal();
        if (delegate == currentMediaPlayerDelegate) {
            sendMessage(MSG_PLAYER_DELEGATE_COMPLETED);
        }
    }

    @Override
    public void onPlayerDelegateError(PlayerDelegate delegate, SRGMediaPlayerException e) {
        manageKeepScreenOnInternal();
        if (delegate == currentMediaPlayerDelegate) {
            sendMessage(MSG_DELEGATE_EXCEPTION, e);
        }
    }

    /**
     * Release the current player. Once the player is released you have to create a new player
     * if you want to play a new video.
     */
    public void release() {
        sendMessage(MSG_RELEASE);
    }

    protected void releaseInternal() {
        setStateInternal(State.RELEASED);
        abandonAudioFocus();
        releaseDelegateInternal();
        stopBackgroundThread();
    }

    public boolean isPlaying() {
        return currentMediaPlayerDelegate != null && currentMediaPlayerDelegate.isPlaying() && seekToWhenReady == null;
    }

    /**
     * Return the current mediaIdentifier played.
     */
    @Nullable
    public String getMediaIdentifier() {
        return currentMediaIdentifier;
    }

    /**
     *
     * @return media position relative to MPST (see {@link #getMediaPlaylistStartTime} )
     */
    public long getMediaPosition() {
        if (currentMediaPlayerDelegate != null) {
            if (seekToWhenReady != null) {
                return seekToWhenReady;
            } else {
                return currentMediaPlayerDelegate.getCurrentPosition();
            }
        } else {
            return UNKNOWN_TIME;
        }
    }

    /**
     *
     * @return Media duration relative to 0.
     */
    public long getMediaDuration() {
        if (currentMediaPlayerDelegate != null) {
            return currentMediaPlayerDelegate.getDuration();
        } else {
            return UNKNOWN_TIME;
        }
    }

    /**
     * Media playlist start time (MPST) is a relative offset for the available seekable range,
     * used in sliding window live playlist.
     * The range [0..MPST] is not available for seeking.
     *
     * <pre>
     * 0 --------------- MPST --------- POSITION ------------------------------------- LIVE
     *                    \---------------------------DURATION---------------------------/
     * </pre>
     *
     * MPST stays constant with a value of 0 when playing a static video.
     *
     * @return MPST in ms
     */
    public long getMediaPlaylistStartTime() {
        if (currentMediaPlayerDelegate != null) {
            return currentMediaPlayerDelegate.getPlaylistStartTime();
        } else {
            return UNKNOWN_TIME;
        }
    }

    public long getBufferPosition() {
        if (currentMediaPlayerDelegate != null) {
            return currentMediaPlayerDelegate.getBufferPosition();
        } else {
            return UNKNOWN_TIME;
        }
    }

    public int getBufferPercentage() {
        if (currentMediaPlayerDelegate != null) {
            return currentMediaPlayerDelegate.getBufferPercentage();
        } else {
            return 0;
        }
    }

    public int getVideoSourceHeight() {
        if (currentMediaPlayerDelegate != null) {
            return currentMediaPlayerDelegate.getVideoSourceHeight();
        } else {
            return -1;
        }
    }

    public void showControlOverlays() {
        overlayController.showControlOverlays();
    }

    public void hideControlOverlays() {
        overlayController.hideControlOverlays();
    }

    public void toggleOverlay() {
        if (overlayController.isOverlayVisible()) {
            overlayController.hideControlOverlays();
        } else {
            overlayController.showControlOverlays();
        }
    }

    public boolean isBoundToMediaPlayerView() {
        return mediaPlayerView != null;
    }

    /**
     * Attach a MediaPlayerView to the controller.
     * Also ink the overlayController to the MediaPlayerView.
     *
     * @param newMediaPlayerView
     */
    public void bindToMediaPlayerView(SRGMediaPlayerView newMediaPlayerView) {
        if (mediaPlayerView != null && mediaPlayerView != newMediaPlayerView) {
            //TODO handle previous linkage
        }

        mediaPlayerView = newMediaPlayerView;
        internalUpdateMediaPlayerViewBound();
        overlayController.bindToVideoContainer(this.mediaPlayerView);
        manageKeepScreenOnInternal();
    }

    @Override
    public SRGMediaPlayerView getMediaPlayerView() {
        return mediaPlayerView;
    }

    private void internalUpdateMediaPlayerViewBound() {

        //Both not null
        if (mediaPlayerView != null && currentMediaPlayerDelegate != null) {

            if (!currentMediaPlayerDelegate.canRenderInView(mediaPlayerView.getVideoRenderingView())) {
                // We need to create a new rendering view.
                final View renderingView = currentMediaPlayerDelegate.createRenderingView(mediaPlayerView.getContext());
                Log.v(TAG, renderingView + "binding, creating rendering view" + mediaPlayerView);

                if (renderingView instanceof SurfaceView) {
                    ((SurfaceView) renderingView).getHolder().addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            Log.v(TAG, renderingView + "binding, surfaceCreated" + mediaPlayerView);
                            try {
                                if (currentMediaPlayerDelegate != null && ((SurfaceView) renderingView).getHolder() == holder) {
                                    currentMediaPlayerDelegate.bindRenderingViewInUiThread(mediaPlayerView);
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
                            public boolean isCurrent(SurfaceTexture surfaceTexture) {
                                return renderingView instanceof TextureView && ((TextureView) renderingView).getSurfaceTexture() == surfaceTexture;
                            }

                            @Override
                            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                                Log.v(TAG, renderingView + "binding, surfaceCreated" + mediaPlayerView);
                                if (currentMediaPlayerDelegate != null && isCurrent(surfaceTexture)) {
                                    try {
                                        currentMediaPlayerDelegate.bindRenderingViewInUiThread(mediaPlayerView);
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

                    } else {
                    Log.v(TAG, renderingView + "binding, attaching rendering view" + mediaPlayerView);

                    renderingView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            Log.v(TAG, renderingView + "binding, attached rendering view" + mediaPlayerView);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            //TODO if a delegate is bound to this surface, we need tu unbind it
                        }
                    });
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaPlayerView != null) {
                            Log.v(TAG, "binding, setVideoRenderingView " + mediaPlayerView);
                            mediaPlayerView.setVideoRenderingView(renderingView);
                        }
                    }
                });
            } else {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.v(TAG, "binding, bindRenderingViewInUiThread " + mediaPlayerView);
                            if (currentMediaPlayerDelegate != null) {
                                currentMediaPlayerDelegate.bindRenderingViewInUiThread(mediaPlayerView);
                            }
                        } catch (SRGMediaPlayerException e) {
                            Log.d(TAG, "Error binding view", e);
                        }
                    }
                });
            }

        } else
            // mediaPlayerView null, just unbind delegate
            if (mediaPlayerView == null) {
                if (currentMediaPlayerDelegate != null) {
                    currentMediaPlayerDelegate.unbindRenderingView();
                }
            }
        //Other cases are :
        // - both mediaPlayerView and delegate null, do nothing
        // - delegate null only, mediaPlayerView already stored as class attributes and will be set when needed
    }

    /**
     * Clear the current mediaPlayer, unbind the delegate and the overlayController
     *
     * @param playerView video container to unbind from.
     */
    public void unbindFromMediaPlayerView(SRGMediaPlayerView playerView) {
        if (mediaPlayerView == playerView) {
            overlayController.bindToVideoContainer(null);
            if (currentMediaPlayerDelegate != null) {
                currentMediaPlayerDelegate.unbindRenderingView();
            }
            mediaPlayerView = null;
        }
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
        sendMessage(MSG_REGISTER_EVENT_LISTENER, listener);
    }

    /**
     * Unregister a listener from this SRGMediaPlayerController.
     *
     * @param listener the listener.
     */
    public void unregisterEventListener(Listener listener) {
        sendMessage(MSG_UNREGISTER_EVENT_LISTENER, listener);
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
                doPostEvent(event, eventListeners);
            }
        });
    }

    private void doPostEvent(Event event, Set<Listener> eventListeners) {
        for (Listener listener : eventListeners) {
            listener.onMediaPlayerEvent(this, event);
        }
    }

    /**
     * Specify a custom PlayerDelegateFactory, you should use this if you need to use a different
     * delegate or if you want to use our ExoPlayerDelegate for AAC audio playing.
     *
     * @param playerDelegateFactory
     */
    public void setPlayerDelegateFactory(PlayerDelegateFactory playerDelegateFactory) {
        this.playerDelegateFactory = playerDelegateFactory;
    }

    public PlayerDelegateFactory getPlayerDelegateFactory() {
        return playerDelegateFactory;
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
            // TODO Handle ducked volume or something
            unmute();
        }
        if (pausedBecauseFocusLoss  && ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_AUTO_RESTART) != 0 || pausedBecauseTransientFocusLoss)) {
            start();
        }
        if (mutedBecauseFocusLoss) {
            unmute();
        }
        pausedBecauseFocusLoss = false;
        pausedBecauseTransientFocusLoss = false;
        duckedBecauseTransientFocusLoss = false;
    }

    private void handleAudioFocusLoss(boolean transientFocus, boolean mayDuck) {
        if (mayDuck && (audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_DUCK) != 0) {
            this.duckedBecauseTransientFocusLoss = true;
            // TODO Handle ducked volume or something
            mute();
        } else if ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_PAUSE) != 0) {
            pausedBecauseFocusLoss = true;
            pausedBecauseTransientFocusLoss = transientFocus;
            pause();
        } else if ((audioFocusBehaviorFlag & AUDIO_FOCUS_FLAG_MUTE) != 0) {
            mutedBecauseFocusLoss = true;
            mute();
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
        return String.valueOf(hashCode());
    }

    public String getMediaSessionId() {
        return getMediaIdentifier() + "@" + getControllerId();
    }

    public void updateOverlayVisibilities() {
        overlayController.propagateControlVisibility();
    }

    private long getPlaylistStartTime() {
        return currentMediaPlayerDelegate != null ? currentMediaPlayerDelegate.getPlaylistStartTime() : 0;
    }

    private boolean isLive() {
        return currentMediaPlayerDelegate != null && currentMediaPlayerDelegate.isLive();
    }

    public boolean isShowingControlOverlays() {
        if (overlayController != null) {
            return overlayController.isShowingControlOverlays();
        }
        return true;
    }

    /**
     * Change the player delegate for another one. The currently playing stream will be stopped on the old
     * player delegate and resumed with the new one.
     *
     * Warning this only impacts the current stream.
     *
     * @param playerDelegate player delegate or null to use the default one (using current delegate factory)
     */
    public void swapPlayerDelegate(PlayerDelegate playerDelegate) {
        if (playerDelegate == null && currentMediaIdentifier != null) {
            playerDelegate = playerDelegateFactory.getDelegateForMediaIdentifier(this, currentMediaIdentifier);
        }
        sendMessage(MSG_SWAP_PLAYER_DELEGATE, playerDelegate);
    }

    public PlayerDelegate getPlayerDelegate() {
        return currentMediaPlayerDelegate;
    }

    public boolean isRemote() {
        return currentMediaPlayerDelegate != null && currentMediaPlayerDelegate.isRemote();
    }
}