/*
 * Created by David Gerber
 * 
 * Copyright (c) 2012 Radio Télévision Suisse
 * All Rights Reserved
 */
package ch.srg.mediaplayer.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.media.MediaRouter;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.gms.common.api.GoogleApiClient;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.internal.PlayerDelegateFactory;

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
public class MediaPlayerService extends Service implements SRGMediaPlayerController.Listener {
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

    ServiceNotificationBuilder currentServiceNotification;

    private LocalBinder binder = new LocalBinder();

    private Runnable autoRelease = new Runnable() {
        @Override
        public void run() {
            if (player != null && !player.isPlaying()) {
                if (player.isBoundToMediaPlayerView()) {
                    handler.postDelayed(autoRelease, AUTORELEASE_DELAY_MS);
                } else {
                    player.release();
                    player = null;
                    clearMediaSession();
                }
            }
        }
    };

    private GoogleApiClient apiClient;
    private MediaRouter mediaRouter;
    private AudioManager audioManager;

    public SRGMediaPlayerController getMediaController() {
        return player;
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

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, this.toString() + " onDestroy");
        isDestroyed = true;

        setForeground(false);

        if (player != null) {
            player.release();
            player = null;
        }
        unregisterMediaButtonEvent();
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

                    clearMediaSession();

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

                    //setupMediaSession(newMediaIdentifier);

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
                    unregisterMediaButtonEvent();
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
        return player != null && !player.isReleased();
    }

    private String getCurrentMediaIdentifier() {
        return player != null ? player.getMediaIdentifier() : null;
    }

    private ServiceNotificationBuilder createNotificationBuilder() {
        String title;
        boolean live = false;
        PendingIntent pendingIntent = null;
        if (dataProvider instanceof SRGMediaPlayerServiceMetaDataProvider) {
            String mediaIdentifier = getCurrentMediaIdentifier();
            SRGMediaPlayerServiceMetaDataProvider serviceMetaDataProvider = (SRGMediaPlayerServiceMetaDataProvider) MediaPlayerService.dataProvider;
            title = serviceMetaDataProvider.getTitle(mediaIdentifier);
            live = serviceMetaDataProvider.isLive(mediaIdentifier);
            pendingIntent = serviceMetaDataProvider.getNotificationPendingIntent(mediaIdentifier);
        } else {
            title = null;
        }
        return new ServiceNotificationBuilder(live, isPlaying(), title, pendingIntent);
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
        createPlayer();

        if (player.play(mediaIdentifier, startPosition)) {
            startUpdates();
        }
    }

    private void setupMediaSession(String mediaIdentifier) {
        Log.d(TAG, "setupMediaSession");
        if (mediaSessionCompat == null) {
            boolean live = false;
            if (dataProvider instanceof SRGMediaPlayerServiceMetaDataProvider) {
                live = ((SRGMediaPlayerServiceMetaDataProvider) dataProvider).isLive(mediaIdentifier);
            }
            /*
            * Setup the media buttons for the lock screen component.
		    */
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.setComponent(new ComponentName(this, AudioIntentReceiver.class));

            mediaSessionCompat = new MediaSessionCompat(this, "MediaPlayerService", new ComponentName(this, AudioIntentReceiver.class), PendingIntent.getBroadcast(this, 0, intent, 0));
            mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSessionCompat.setActive(true);
            mediaSessionCompat.setCallback(new SRGMediaSessionCallback());

            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();
            if (dataProvider instanceof SRGMediaPlayerServiceMetaDataProvider) {
                String title = ((SRGMediaPlayerServiceMetaDataProvider) dataProvider).
                        getTitle(mediaIdentifier);

                meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
            }
            if (!live) {
                meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
            }

            mediaSessionCompat.setMetadata(meta.build());

            setupNotification();

            if (mediaRouter != null) {
                mediaRouter.setMediaSessionCompat(mediaSessionCompat);
            }
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
                    notificationManager.notify(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat, mediaArtBitmap));
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
                    startForeground(NOTIFICATION_ID, builder.buildNotification(this, mediaSessionCompat, mediaArtBitmap));
                } else {
                    stopForeground(true);
                    currentServiceNotification = null;
                }
                isForeground = foreground;
            }
        } catch (Throwable ignored) {
            // We ignore exception for tests (bug in ServiceTestCase that does not include a mock
            // activity manager). See http://code.google.com/p/android/issues/detail?id=12122
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
            clearMediaSession();
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
        clearMediaSession();
        return super.onUnbind(intent);
    }

    private void registerMediaButtonEvent() {
        //audioManager.registerMediaButtonEventReceiver(new ComponentName(this, AudioIntentReceiver.class.getName()));
    }

    private void unregisterMediaButtonEvent() {
        //audioManager.unregisterMediaButtonEventReceiver(new ComponentName(this, AudioIntentReceiver.class.getName()));
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
                clearMediaSession();
                setupMediaSession(mp.getMediaIdentifier());
                registerMediaButtonEvent();
                cancelAutoRelease();
                break;
            case MEDIA_COMPLETED:
            case MEDIA_STOPPED:
                unregisterMediaButtonEvent();
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

    public static void setPlayerDelegateFactory(PlayerDelegateFactory playerDelegateFactory) {
        MediaPlayerService.playerDelegateFactory = playerDelegateFactory;
    }

    public static void setDebugMode(boolean debugMode) {
        MediaPlayerService.debugMode = debugMode;
    }

    private class SRGMediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent keyEvent = mediaButtonIntent
                    .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                pause();
            } else if (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY) {
                resume();
            }
            return true;
        }

        @Override
        public void onPlay() {
            super.onPlay();
            resume();
        }

        @Override
        public void onPause() {
            super.onPause();
            pause();
        }

        @Override
        public void onStop() {
            super.onStop();
            stopPlayer();
        }
    }

    public void setupCastApi(MediaRouter mediaRouter, GoogleApiClient apiClient) {
        this.mediaRouter = mediaRouter;
        this.apiClient = apiClient;
    }

    private void setupNotification() {
        int notificationIconId;
        if (dataProvider instanceof SRGMediaPlayerServiceMetaDataProvider) {
            notificationIconId = ((SRGMediaPlayerServiceMetaDataProvider) dataProvider).getNotificationIconResourceId(getCurrentMediaIdentifier());
        } else {
            notificationIconId = 0;
        }

        updateLockScreenImage(notificationIconId);
    }

    /*
     * Updates lock screen image
     */
    private void updateLockScreenImage(int notificationIconId) {
        mediaArtBitmap = BitmapFactory.decodeResource(this.getResources(), notificationIconId);
        MediaMetadataCompat currentMetadata = mediaSessionCompat.getController().getMetadata();
        MediaMetadataCompat.Builder newBuilder = currentMetadata == null
                ? new MediaMetadataCompat.Builder()
                : new MediaMetadataCompat.Builder(currentMetadata);
        mediaSessionCompat.setMetadata(newBuilder
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaArtBitmap)
                .build());
        updateNotification();
    }

    /*
     * Clears Media Session
     */
    public void clearMediaSession() {
        Log.d(TAG, "clearMediaSession()");
        audioManager.abandonAudioFocus(null);
        if (mediaSessionCompat != null) {
            mediaSessionCompat.setActive(false);
            mediaSessionCompat.release();
            mediaSessionCompat = null;
        }
    }
}