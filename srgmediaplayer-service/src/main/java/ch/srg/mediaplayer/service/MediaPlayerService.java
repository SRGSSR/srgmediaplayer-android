/*
 * Created by David Gerber
 *
 * Copyright (c) 2012 Radio Télévision Suisse
 * All Rights Reserved
 */
package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
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
public class MediaPlayerService extends Service implements SRGMediaPlayerController.Listener, ChromeCastManager.Listener, MediaSessionManager.Listener {
    public static final String TAG = "MediaPlayerService";

    private static final String PREFIX = "ch.srg.mediaplayer.service";
    public static final String ACTION_PLAY = PREFIX + ".action.PLAY";
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
    public static final String ARG_POSITION = "position";
    public static final String ARG_FLAGS = "flags";
    public static final String ARG_FROM_NOTIFICATION = "fromNotification";

    public static final String KEY_STATE = "state";
    public static final String KEY_MEDIA_IDENTIFIER = "mediaIdentifier";
    public static final String KEY_POSITION = "position";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_FLAGS = "flags";
    public static final String KEY_PLAYING = "playing";

    private static final int NOTIFICATION_ID = 1;
    public static final int FLAG_LIVE = 1;
    private static final long AUTORELEASE_DELAY_MS = 10000;
    private static SRGMediaPlayerDataProvider dataProvider;
    private static SRGMediaPlayerServiceMetaDataProvider serviceDataProvider;

    private SRGMediaPlayerController player;
    private boolean isForeground;

    // Media Session implementation
    private MediaSessionCompat mediaSessionCompat;

    private int flags;
    private final Handler handler = new Handler();

    private SRGMediaPlayerController.State currentState;

    private Bitmap mediaArtBitmap;

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

    private Runnable autoRelease = new Runnable() {
        @Override
        public void run() {
            if (player != null && !player.isPlaying()) {
                if (player.isBoundToMediaPlayerView()) {
                    handler.postDelayed(autoRelease, AUTORELEASE_DELAY_MS);
                } else {
                    player.release();
                    player = null;
                    mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
                }
            }
        }
    };

    public static SRGMediaPlayerServiceMetaDataProvider getServiceDataProvider() {
        return serviceDataProvider;
    }

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
        mediaArtBitmap = bitmap;
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

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, this.toString() + " onCreate");

        //dummyArt = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_album_art);/* XXX: dummy, and should be done in a thread too */

		/*
         * Stop the Google Music player.
		 */
        MusicControl.pause(this); /* XXX: this is not the proper place for this, should be in the play part */

        if (ChromeCastManager.isInstantiated()) {
            chromeCastManager = ChromeCastManager.getInstance();
            chromeCastManager.addListener(this);
        }

        mediaSessionManager = MediaSessionManager.getInstance();
        mediaSessionManager.addListener(this);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, this.toString() + " onDestroy");
        isDestroyed = true;

        setForeground(false);

        if (chromeCastManager != null) {
            chromeCastManager.removeListener(this);
        }

        if (player != null) {
            player.release();
            player = null;
        }
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
                case ACTION_PLAY: {
                    setForeground(true);

                    String newMediaIdentifier = intent.getStringExtra(ARG_MEDIA_IDENTIFIER);

                    Long position;
                    if (intent.hasExtra(ARG_POSITION)) {
                        position = intent.getLongExtra(ARG_POSITION, 0);
                    } else {
                        position = null;
                    }
                    if (TextUtils.isEmpty(newMediaIdentifier)) {
                        Log.e(TAG, "ACTION_PLAY without mediaIdentifier, recovering");
                        newMediaIdentifier = getCurrentMediaIdentifier();
                    }
                    if (intent.hasExtra(ARG_FLAGS)) {
                        flags = intent.getIntExtra(ARG_FLAGS, 0);
                    }

                    //requestMediaSession(newMediaIdentifier);

                    try {
                        prepare(newMediaIdentifier, position, true);
                    } catch (SRGMediaPlayerException e) {
                        Log.e(TAG, "Player play " + newMediaIdentifier, e);
                    }
                }
                break;

                case ACTION_PAUSE:
                    pause();
                    break;

                case ACTION_STOP:
                    stopPlayer();
                    // Update notification so that if we are no longer synchronized, the user can
                    // at least force the notification removal with the stop button
                    updateNotification();
                    break;

                case ACTION_SEEK: {
                    long position = intent.getLongExtra(ARG_POSITION, -1);
                    if (position == -1) {
                        throw new IllegalArgumentException("Undefined position for seek");
                    } else {
                        if (isPlaying()) {
                            seekTo(position);
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

    private boolean hasNonDeadPlayer() {
        return player != null && !player.isReleased() && player.getMediaIdentifier() != null;
    }

    private String getCurrentMediaIdentifier() {
        return player != null ? player.getMediaIdentifier() : null;
    }

    private ServiceNotificationBuilder createNotificationBuilder() {
        String title;
        boolean live;
        PendingIntent pendingIntent;
        if (serviceDataProvider != null) {
            String mediaIdentifier = getCurrentMediaIdentifier();
            title = serviceDataProvider.getTitle(mediaIdentifier);
            live = serviceDataProvider.isLive(mediaIdentifier);
            pendingIntent = serviceDataProvider.getNotificationPendingIntent(mediaIdentifier);
        } else {
            title = null;
            live = false;
            pendingIntent = null;
        }
        return new ServiceNotificationBuilder(live, isPlaying(), title, pendingIntent, mediaArtBitmap);
    }

    private void startUpdates() {
        statusUpdater = new Runnable() {
            @Override
            public void run() {
                sendBroadcastStatus(false);
                if (!isDestroyed) {
                    handler.postDelayed(statusUpdater, 1000);
                }
            }
        };
        handler.post(statusUpdater);
    }

    private void prepare(String mediaIdentifier, Long startPosition) throws SRGMediaPlayerException {
        mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
        mediaArtBitmap = null;
        createPlayer();

        if (player.play(mediaIdentifier, startPosition)) {
            startUpdates();
        }
    }

    public void prepare(String mediaIdentifier, Long startPosition, boolean autoStart) throws SRGMediaPlayerException {
        if (player != null && !player.isReleased()) {
            if (mediaIdentifier.equals(player.getMediaIdentifier())) {
                if (autoStart) {
                    player.start();
                }
                if (startPosition != null) {
                    player.seekTo(startPosition);
                }
                return;
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
    }

    private void createPlayer() {
        if (dataProvider == null) {
            throw new IllegalArgumentException("No data provider defined");
        }
        player = new SRGMediaPlayerController(this, dataProvider, TAG);
        player.setDebugMode(debugMode);
        if (MediaPlayerService.playerDelegateFactory != null) {
            player.setPlayerDelegateFactory(MediaPlayerService.playerDelegateFactory);
        }
        player.registerEventListener(this);
        cancelAutoRelease();
    }

    private void cancelAutoRelease() {
        handler.removeCallbacks(autoRelease);
    }

    private void requestMediaSession(String mediaIdentifier) {
        mediaSessionCompat = mediaSessionManager.requestMediaSession(serviceDataProvider, mediaIdentifier);
    }

    private void updateNotification() {
        Log.v(TAG, "updateNotification");
        if (hasNonDeadPlayer()) {
            if (!isForeground) {
                setForeground(true);
            } else {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                ServiceNotificationBuilder builder = createNotificationBuilder();
                if (builder != currentServiceNotification) {
                    currentServiceNotification = builder;
                    notificationManager.notify(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat));
                }
            }
        } else {
            if (isForeground) {
                Log.v(TAG, "No player when updating notification. Going to background");
                setForeground(false);
            }
        }
    }

    private void setForeground(boolean foreground) {
        try {
            if (foreground != isForeground) {
                if (foreground) {
                    ServiceNotificationBuilder builder = createNotificationBuilder();
                    currentServiceNotification = builder;
                    startForeground(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat));
                } else {
                    stopForeground(true);
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
        if (player != null) {
            player.start();
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void stopPlayer() {
        if (player != null) {
            player.release();
            player = null;
            mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
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
        updateBundle.putInt(KEY_FLAGS, flags);

        Intent intent = new Intent(ACTION_BROADCAST_STATUS);
        intent.putExtra(ACTION_BROADCAST_STATUS_BUNDLE, updateBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    private long getPosition() {
        // Return position only for AOD when we have an active player.
        if (player != null) {
            return Math.max(0, player.getMediaPosition());
        } else {
            return 0;
        }
    }

    private long getDuration() {
        if (player != null) {
            return player.getMediaDuration();
        }

        return 0;
    }

    void seekTo(long msec) {
        player.seekTo(msec);
    }

    private SRGMediaPlayerController.State getState() {
        return player == null ? SRGMediaPlayerController.State.RELEASED : player.getState();
    }

    private void toggle() {
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
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
        return super.onUnbind(intent);
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        sendBroadcastStatus(false);
        // TODO Find a clean way to update notification only when necessary
        if (mediaSessionCompat != null) {
            updateNotification();
        }
        switch (event.type) {
            case MEDIA_READY_TO_PLAY:
                mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
                requestMediaSession(mp.getMediaIdentifier());
                cancelAutoRelease();
                break;
            case MEDIA_COMPLETED:
            case MEDIA_STOPPED:
                cancelAutoRelease();
                break;
            case PLAYING_STATE_CHANGE:
                cancelAutoRelease();
                handler.postDelayed(autoRelease, AUTORELEASE_DELAY_MS);
                break;
        }
    }

    public static void setDataProvider(SRGMediaPlayerDataProvider dataProvider) {
        MediaPlayerService.dataProvider = dataProvider;
    }

    public static void setServiceDataProvider(SRGMediaPlayerServiceMetaDataProvider serviceDataProvider) {
        MediaPlayerService.serviceDataProvider = serviceDataProvider;
    }

    public static void setPlayerDelegateFactory(PlayerDelegateFactory playerDelegateFactory) {
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
}