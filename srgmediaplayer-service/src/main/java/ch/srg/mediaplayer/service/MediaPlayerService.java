/*
 * Created by David Gerber
 *
 * Copyright (c) 2012 Radio Télévision Suisse
 * All Rights Reserved
 */
package ch.srg.mediaplayer.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerFactory;
import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.internal.cast.ChromeCastManager;
import ch.srg.mediaplayer.internal.session.MediaSessionManager;

/**
 * MediaPlayerService plays using the SRGMediaPlayerController. The communication works
 * using the following intents:<br>
 * - <b>ACTION_PLAY:</b> resume a stream. <i>ARG_MEDIA_IDENTIFIER:</i> media identifier of the stream.
 * - <b>ACTION_PAUSE:</b> pause the stream<br>
 * - <b>ACTION_STOP:</b> stop the stream<br>
 * - <b>ACTION_TOGGLE_PLAYBACK:</b> toggle between pause and play depending on the current state<br>
 * - <b>ACTION_SEEK:</b> seek into the stream. <i>ARG_POSITION:</i> position within the stream in milliseconds<br>
 * - <b>ACTION_SHOW_NOTIFICATION:</b> show the notification. Typically called when the playing activity is going away<br>
 * - <b>ACTION_HIDE_NOTIFICATION:</b> hide the notification. Typically called when the playing activity is coming back<br>
 * - <b>ACTION_BROADCAST_STATUS:</b> called to request an immediate status update of the player
 * <p>The player reports back its status using the following broadcast:
 * - <b>ACTION_BROADCAST_STATUS_BUNDLE:</b> with <i>KEY_STATE:</i> player status; <i>KEY_POSITION:</i> position within the stream in milliseconds;
 * <i>KEY_DURATION:</i> duration of the stream in milliseconds;
 */
public class MediaPlayerService extends Service implements SRGMediaPlayerController.Listener, ChromeCastManager.Listener, MediaSessionManager.Listener, SRGMediaPlayerFactory {
    public static final String TAG = "MediaPlayerService";

    private static final String PREFIX = "ch.srg.mediaplayer.service";
    public static final String ACTION_PLAY = PREFIX + ".action.PLAY";
    public static final String ACTION_PREPARE = PREFIX + ".action.PREPARE";
    public static final String ACTION_RESUME = PREFIX + ".action.RESUME";
    public static final String ACTION_PAUSE = PREFIX + ".action.PAUSE";
    public static final String ACTION_STOP = PREFIX + ".action.STOP";
    public static final String ACTION_BROADCAST_STATUS = PREFIX + ".action.BROADCAST_STATUS";
    public static final String ACTION_SEEK = PREFIX + ".action.SEEK";
    public static final String ACTION_TOGGLE_PLAYBACK = PREFIX + ".action.TOGGLE_PLAYBACK";
    public static final String ACTION_SHOW_NOTIFICATION = PREFIX + ".action.SHOW_NOTIFICATION";

    public static final String ACTION_HIDE_NOTIFICATION = PREFIX + ".action.HIDE_NOTIFICATION";

    public static final String ACTION_BROADCAST_STATUS_BUNDLE = PREFIX + ".broadcast.STATUS_BUNDLE";

    public static final String ARG_MEDIA_IDENTIFIER = "mediaIdentifier";
    public static final String ARG_POSITION = "position"; /** Long **/
    public static final String ARG_POSITION_INCREMENENT = "positionIncrement";
    public static final String ARG_FROM_NOTIFICATION = "fromNotification";

    public static final String KEY_STATE = "state";
    public static final String KEY_MEDIA_IDENTIFIER = "mediaIdentifier";
    public static final String KEY_POSITION = "position";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_FLAGS = "flags";
    public static final String KEY_PLAYING = "playing";

    private static final int NOTIFICATION_ID = 1;
    /**
     * Forbid seeking too close to the end when using relative seek (POSITION_INCREMENT).
     */
    private static final long SEEK_END_SAFETY_MARGIN_MS = 10000;
    // Do not seek if player is already playing and no further than this to the required position
    private static final long START_SEEK_THRESHOLD = 2000;
    private static SRGMediaPlayerDataProvider dataProvider;

    private SRGMediaPlayerController player;
    private boolean isForeground;

    // Media Session implementation
    @Nullable
    private MediaSessionCompat mediaSessionCompat;

    private int flags;

    private SRGMediaPlayerController.State currentState;

    public boolean isDestroyed;

    private Runnable statusUpdater;
    private static PlayerDelegateFactory playerDelegateFactory;
    private static boolean debugMode;

    @Nullable
    private ChromeCastManager chromeCastManager;
    private MediaSessionManager mediaSessionManager;

    ServiceNotificationBuilder currentServiceNotification;
    private LocalBinder binder = new LocalBinder();

    private boolean videoInBackground;

    private String currentMediaIdentifier;
    @Nullable
    private NotificationData currentNotificationData;
    private boolean currentlyPlaying;
    private static SRGMediaPlayerServiceMetaDataProvider serviceDataProvider;
    private SRGMediaPlayerCreatedListener srgMediaPlayerCreatedListener;

    public SRGMediaPlayerController getMediaController() {
        return player;
    }

    @Override
    public void onChromeCastApplicationConnected() {
        Log.d(TAG, "onChromeCastApplicationConnected");
        if (player != null) {
            player.swapPlayerDelegate(null);
        }
    }

    @Override
    public void onChromeCastApplicationDisconnected() {
        Log.d(TAG, "onChromeCastApplicationDisconnected");
        if (player != null) {
            try {
                String mediaIdentifier = player.getMediaIdentifier();
                int mediaType = mediaIdentifier == null ? 0 : dataProvider.getMediaType(mediaIdentifier);
                if (player.isBoundToMediaPlayerView()
                        && (videoInBackground
                        || mediaType == SRGMediaPlayerDataProvider.TYPE_AUDIO)) {
                    player.swapPlayerDelegate(null);
                } else {
                    player.release();
                }
            } catch (SRGMediaPlayerException ignored) {
                player.release();
            }
        }
    }

    @Override
    public void onChromeCastPlayerStatusUpdated(int state, int idleReason) {
    }

    @Override
    public void onBitmapUpdate(Bitmap bitmap) {
        updateNotification();
    }

    @Override
    public void onResumeSession() {
        resume();
    }

    @Override
    public void onPauseSession() {
        pause();
    }

    @Override
    public void onStopSession() {
        stopPlayer();
    }

    @Override
    public void onMediaSessionUpdated() {

    }

    public void registerMediaPlayerListener(SRGMediaPlayerCreatedListener listener) {
        srgMediaPlayerCreatedListener = listener;
        if (srgMediaPlayerCreatedListener != null) {
            srgMediaPlayerCreatedListener.onServiceMediaPlayerChange(player);
        }
    }

    public void unregisterMediaPlayerCreatedListener(SRGMediaPlayerCreatedListener listener) {
        if (listener == srgMediaPlayerCreatedListener){
            srgMediaPlayerCreatedListener = null;
        }
    }

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, this.toString() + " onCreate");

		/*
         * Stop the Google Music player.
		 */
        MusicControl.pause(this); /* XXX: this is not the proper place for this, should be in the play part */

        if (ChromeCastManager.isInstantiated()) {
            chromeCastManager = ChromeCastManager.getInstance();
            chromeCastManager.addListener(this);
        }

        MediaSessionManager.initialize(this);
        mediaSessionManager = MediaSessionManager.getInstance();
        mediaSessionManager.addListener(this);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, this.toString() + " onDestroy");
        isDestroyed = true;

        if (chromeCastManager != null) {
            chromeCastManager.removeListener(this);
        }
        stopPlayer();
        NotificationManagerCompat.from(this).cancelAll();
    }

    @Override
    public int onStartCommand(Intent intent, int commandFlags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.v(TAG, this.toString() + " Action: " + action);

            switch (action) {
                case ACTION_RESUME:
                    setForeground(true);
                    resume();
                    break;
                case ACTION_PREPARE:
                case ACTION_PLAY: {
                    setForeground(true);

                    String newMediaIdentifier = intent.getStringExtra(ARG_MEDIA_IDENTIFIER);

                    Long position;
                    if (intent.hasExtra(ARG_POSITION)) {
                        position = intent.getLongExtra(ARG_POSITION, 0);
                    } else {
                        position = null;
                    }
                    play(newMediaIdentifier, position, TextUtils.equals(action, ACTION_PLAY));
                }
                break;

                case ACTION_PAUSE:
                    pause();
                    break;

                case ACTION_STOP:
                    stopPlayer();
                    // Update notification so that if we are no longer synchronized, the user can
                    // at least force the notification removal with the stop button
                    NotificationManagerCompat.from(this).cancelAll();
                    // We force the set foreground as the updateNotification could potentially
                    // be in the wrong state
                    try {
                        stopForeground(true);
                    } catch (NullPointerException ignored) {
                        // A null pointer exception is thrown by the system when using the ThreadServiceTestBase
                        // Catch necessary for tests
                    }
                    break;

                case ACTION_SEEK: {
                    long position = intent.getLongExtra(ARG_POSITION, -1);
                    Long positionIncrement = intent.hasExtra(ARG_POSITION_INCREMENENT) ? intent.getLongExtra(ARG_POSITION_INCREMENENT, 0) : null;
                    if (position == -1 && positionIncrement == null) {
                        throw new IllegalArgumentException("Undefined position or position increment for seek");
                    } else {
                        if (player != null) {
                            if (positionIncrement != null) {
                                seekTo(Math.min(getDuration() - SEEK_END_SAFETY_MARGIN_MS, getPosition() + positionIncrement));
                            } else {
                                seekTo(position);
                            }
                        }
                    }
                }
                break;

                case ACTION_TOGGLE_PLAYBACK:
                    toggle();
                    break;

                case ACTION_BROADCAST_STATUS:
                    sendBroadcastStatus(true);
                    break;

                case ACTION_SHOW_NOTIFICATION:
                case ACTION_HIDE_NOTIFICATION:
                    Log.e(TAG, action + " No longer supported");
                    break;

                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
        } else {
            /*
             * We don't want to be restarted by the system.
			 */
            stopSelf();
        }
        return (Service.START_NOT_STICKY); /* we don't handle sticky mode */
    }

    public SRGMediaPlayerController play(String newMediaIdentifier, Long position, boolean autoStart) {
        Log.d(TAG, "play " + newMediaIdentifier);
        if (TextUtils.isEmpty(newMediaIdentifier)) {
            Log.e(TAG, "ACTION_PLAY without mediaIdentifier, recovering");
            newMediaIdentifier = getCurrentMediaIdentifier();
        }

        currentMediaIdentifier = newMediaIdentifier;

        onMediaIdentifierChanged();

        try {
            return prepare(newMediaIdentifier, position, autoStart);
        } catch (SRGMediaPlayerException e) {
            Log.e(TAG, "Player play " + newMediaIdentifier, e);
            return null;
        }
    }

    private boolean hasNonDeadPlayer() {
        return player != null && !player.isReleased() && player.getMediaIdentifier() != null;
    }

    private String getCurrentMediaIdentifier() {
        return player != null ? player.getMediaIdentifier() : null;
    }

    private void startUpdates() {
        statusUpdater = new Runnable() {
            @Override
            public void run() {
                sendBroadcastStatus(false);
            }
        };
    }

    private void prepare(String mediaIdentifier, Long startPosition) throws SRGMediaPlayerException {
        Log.d(TAG, "prepare " + mediaIdentifier + " startPosition " + startPosition);
        mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
        createPlayer();

        if (player.play(mediaIdentifier, startPosition)) {
            startUpdates();
        }
    }

    public SRGMediaPlayerController prepare(String mediaIdentifier, @Nullable Long startPosition, boolean autoStart) throws SRGMediaPlayerException {
        Log.d(TAG, "prepare " + mediaIdentifier + " startPosition " + startPosition + " autoStart " + autoStart);
        if (player != null && !player.isReleased()) {
            if (TextUtils.equals(mediaIdentifier, player.getMediaIdentifier())) {
                if (autoStart) {
                    player.start();
                }
                if (startPosition != null) {
                    if (Math.abs(startPosition - player.getMediaPosition()) >= START_SEEK_THRESHOLD) {
                        player.seekTo(startPosition);
                    }
                }
                return player;
            } else {
                player.release();
            }
        }
        prepare(mediaIdentifier, startPosition);
        if (autoStart) {
            player.start();
        } else {
            player.pause();
        }
        return player;
    }

    private void createPlayer() {
        Log.d(TAG, "create player");
        if (dataProvider == null) {
            throw new IllegalArgumentException("No data provider defined");
        }
        player = new SRGMediaPlayerController(getApplicationContext(), dataProvider, TAG);
        player.setDebugMode(debugMode);
        if (MediaPlayerService.playerDelegateFactory != null) {
            player.setPlayerDelegateFactory(MediaPlayerService.playerDelegateFactory);
        }
        player.registerEventListener(this);
        if (srgMediaPlayerCreatedListener != null) {
            srgMediaPlayerCreatedListener.onServiceMediaPlayerChange(player);
        }
    }

    private void requestMediaSession(@NonNull NotificationData notificationData) {
        Log.d(TAG, "requestMediaSession: " + notificationData);
        mediaSessionCompat = mediaSessionManager.requestMediaSession(notificationData);
    }

    private void updateNotification() {
        Log.v(TAG, "updateNotification");
        if (hasNonDeadPlayer()) {
            if (!isForeground) {
                setForeground(true);
            } else {
                String newMediaIdentifier = getCurrentMediaIdentifier();
                if (!TextUtils.equals(newMediaIdentifier, currentMediaIdentifier)) {
                    this.currentMediaIdentifier = newMediaIdentifier;
                    onMediaIdentifierChanged();
                }
            }
        } else {
            if (isForeground) {
                Log.v(TAG, "No player when updating notification. Going to background");
                setForeground(false);
            }
        }
    }

    private void doNotify() {
        Log.d(TAG, "doNotify");
        if (currentNotificationData != null && mediaSessionCompat != null) {
            ServiceNotificationBuilder builder = new ServiceNotificationBuilder(currentNotificationData, isPlaying());
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            try {
                notificationManager.notify(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat));
            } catch (OutOfMemoryError e) {
                Log.d(TAG, "An out of memory occured when trying to build notification, image size?", e);
            }
        }
    }

    private void onMediaIdentifierChanged() {
        Log.d(TAG, "onMediaIdentifierChanged");
        currentNotificationData = null;
        final String requestedMediaIdentifier = this.currentMediaIdentifier;
        serviceDataProvider.getNotificationData(requestedMediaIdentifier, new SRGMediaPlayerServiceMetaDataProvider.GetNotificationDataCallback() {
            @Override
            public void onNotificationDataLoaded(NotificationData notificationData) {
                Log.d(TAG, "onNotificationDataLoaded: " + notificationData);
                if (requestedMediaIdentifier.equals(currentMediaIdentifier)) {
                    currentNotificationData = notificationData;
                    requestMediaSession(notificationData);
                    doNotify();
                }
            }

            @Override
            public void onImageLoaded(NotificationData notificationData) {
                Log.d(TAG, "onImageLoaded: " + notificationData);
                if (requestedMediaIdentifier.equals(currentMediaIdentifier)) {
                    currentNotificationData = notificationData;
                    doNotify();
                }
            }

            @Override
            public void onDataNotAvailable() {

            }
        });

    }

    private void setForeground(boolean foreground) {
        Log.d(TAG, "setForeground: " + foreground);
        try {
            if (foreground != isForeground) {
                if (foreground) {
                    if (currentNotificationData != null) {
                        ServiceNotificationBuilder builder = new ServiceNotificationBuilder(currentNotificationData, currentlyPlaying);
                        if (mediaSessionCompat != null) {
                            currentServiceNotification = builder;
                            startForeground(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat));
                        }
                    }
                } else {
                    stopForeground(true);
                    NotificationManagerCompat.from(this).cancelAll();
                    currentServiceNotification = null;
                }
                isForeground = foreground;
            }
        } catch (Throwable e) {
            // We ignore exception for tests (bug in ServiceTestCase that does not include a mock
            // activity manager). See http://code.google.com/p/android/issues/detail?id=12122
            Log.e(TAG, "setForeground", e);
        }
    }

    public void resume() {
        Log.d(TAG, "resume");
        if (player != null) {
            player.start();
        }
    }

    public void pause() {
        Log.d(TAG, "pause");
        if (player != null) {
            player.pause();
        }
    }

    public void stopPlayer() {
        Log.d(TAG, "stopPlayer");
        if (player != null) {
            mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
            mediaSessionCompat.setActive(false);
            mediaSessionCompat.release();
            mediaSessionCompat = null;
            player.release();
            player = null;
            setForeground(false);
        }
    }

    private void sendBroadcastStatus(boolean forced) {
        SRGMediaPlayerController.State state = getState();

        if (forced || state != currentState || state == SRGMediaPlayerController.State.READY) {
            broadcastStatusBundle(state);
            currentState = state;
        }

    }

    private void broadcastStatusBundle(SRGMediaPlayerController.State state) {
        Bundle updateBundle = new Bundle();
        updateBundle.putString(KEY_STATE, state.name());
        updateBundle.putBoolean(KEY_PLAYING, isPlaying());
        updateBundle.putLong(KEY_POSITION, getPosition());
        updateBundle.putLong(KEY_DURATION, getDuration());
        updateBundle.putString(KEY_MEDIA_IDENTIFIER, getCurrentMediaIdentifier());

        Intent intent = new Intent(ACTION_BROADCAST_STATUS);
        intent.putExtra(ACTION_BROADCAST_STATUS_BUNDLE, updateBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    /**
     * @return position in ms
     */
    private long getPosition() {
        if (player != null) {
            return Math.max(0, player.getMediaPosition());
        } else {
            return 0;
        }
    }

    /**
     * @return duration in ms
     */
    private long getDuration() {
        if (player != null) {
            return player.getMediaDuration();
        }

        return 0;
    }

    void seekTo(long msec) {
        Log.d(TAG, "seekTo: " + msec);
        player.seekTo(msec);
    }

    private SRGMediaPlayerController.State getState() {
        return player == null ? SRGMediaPlayerController.State.RELEASED : player.getState();
    }

    private void toggle() {
        Log.d(TAG, "toggle");
        if (player != null) {
            if (player.isPlaying()) {
                pause();
            } else if (!player.isReleased()) {
                resume();
                startUpdates();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        //Log.d(TAG, "onMediaPlayerEvent: " + event.toString());
        sendBroadcastStatus(false);
        if (currentlyPlaying != isPlaying()) {
            currentlyPlaying = isPlaying();
            doNotify();
        }
        switch (event.type) {
            case MEDIA_READY_TO_PLAY:
            case MEDIA_COMPLETED:
            case MEDIA_STOPPED:
                mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
                break;
            case PLAYING_STATE_CHANGE:
                break;
        }
    }

    public static void setDataProvider(SRGMediaPlayerDataProvider dataProvider) {
        Log.d(TAG, "setDataProvider: " + dataProvider);
        MediaPlayerService.dataProvider = dataProvider;
    }

    public static void setPlayerDelegateFactory(PlayerDelegateFactory playerDelegateFactory) {
        Log.d(TAG, "setPlayerDelegateFactory: " + playerDelegateFactory);
        MediaPlayerService.playerDelegateFactory = playerDelegateFactory;
    }

    public static void setDebugMode(boolean debugMode) {
        MediaPlayerService.debugMode = debugMode;
    }

    /**
     * Enable or disable support of video in background.
     *
     * @param videoInBackground true if video in background should be supported
     *                          (enables continuing play when chromecast is disconnected)
     */
    public void setVideoInBackground(boolean videoInBackground) {
        this.videoInBackground = videoInBackground;
    }

    public static void setServiceDataProvider(SRGMediaPlayerServiceMetaDataProvider serviceDataProvider) {
        MediaPlayerService.serviceDataProvider = serviceDataProvider;
    }

    public interface SRGMediaPlayerCreatedListener {
        void onServiceMediaPlayerChange(@Nullable SRGMediaPlayerController mediaPlayerController);
    }
}