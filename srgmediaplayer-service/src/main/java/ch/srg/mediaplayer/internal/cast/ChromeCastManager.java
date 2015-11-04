package ch.srg.mediaplayer.internal.cast;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import ch.srg.mediaplayer.internal.cast.exceptions.NoConnectionException;
import ch.srg.mediaplayer.service.MediaPlayerService;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "ChromeCastManager";

    public static final int DISCONNECT_REASON_OTHER = 0;
    public static final int DISCONNECT_REASON_CONNECTIVITY = 1;
    public static final int DISCONNECT_REASON_APP_NOT_RUNNING = 2;
    public static final int DISCONNECT_REASON_EXPLICIT = 3;

    public static final int NO_APPLICATION_ERROR = 0;
    private static final double VOLUME_INCREMENT = 1.0;

    private static ChromeCastManager instance;
    private RemoteMediaPlayer remoteMediaPlayer;
    private MediaSessionCompat mediaSessionCompat;
    private int state = MediaStatus.PLAYER_STATE_IDLE;
    private int idleReason;

    protected Context context;
    protected MediaRouter mediaRouter;
    protected MediaRouteSelector mediaRouteSelector;
    protected CastMediaRouterCallback mediaRouterCallback;
    protected CastDevice selectedCastDevice;
    private Set<Listener> listeners = new HashSet<>();

    protected String deviceName;

    protected GoogleApiClient apiClient;
    protected String sessionId;
    private MediaRouter.RouteInfo routeInfo;
    private ComponentName mediaEventReceiver;
    private MediaStatus mediaStatus;

    private boolean connectionSuspended;
    protected int applicationErrorCode = NO_APPLICATION_ERROR;

    protected ChromeCastManager(Context context) {
        this.context = context.getApplicationContext();

        this.mediaRouter = MediaRouter.getInstance(context);
        this.mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();

        mediaRouterCallback = new CastMediaRouterCallback(this);
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        Log.d(TAG, "VideoCastManager is instantiated");
    }

    public static synchronized ChromeCastManager initialize(Context context) {
        if (instance == null) {
            Log.d(TAG, "New instance of VideoCastManager is created");
            if (ConnectionResult.SUCCESS != GooglePlayServicesUtil
                    .isGooglePlayServicesAvailable(context)) {
                String msg = "Couldn't find the appropriate version of Google Play Services";
                Log.e(TAG, msg);
            }
            instance = new ChromeCastManager(context);
        }
        return instance;
    }

    /**
     * Returns a (singleton) instance of this class. Clients should call this method in order to
     * get a hold of this singleton instance, only after it is initialized. If it is not initialized
     * yet, an {@link IllegalStateException} will be thrown.
     */
    public static ChromeCastManager getInstance() {
        if (instance == null) {
            String msg = "No VideoCastManager instance was found, did you forget to initialize it?";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
        return instance;
    }

    private void onApplicationDisconnected(int errorCode) {
        Log.d(TAG, "onApplicationDisconnected() reached with error code: " + errorCode);
        applicationErrorCode = errorCode;
        updateMediaSession(false);
        if (mediaSessionCompat != null) {
            mediaRouter.setMediaSessionCompat(null);
        }
        if (mediaRouter != null) {
            Log.d(TAG, "onApplicationDisconnected(): Cached RouteInfo: " + getRouteInfo());
            Log.d(TAG, "onApplicationDisconnected(): Selected RouteInfo: "
                    + mediaRouter.getSelectedRoute());
            if (getRouteInfo() == null || mediaRouter.getSelectedRoute().equals(getRouteInfo())) {
                Log.d(TAG, "onApplicationDisconnected(): Setting route to default");
                mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
            }
        }
        onDeviceSelected(null);
    }

    protected void onApplicationConnected(ApplicationMetadata appMetadata,
                                          String applicationStatus, String sessionId, boolean wasLaunched) {
        Log.d(TAG, "onApplicationConnected() reached with sessionId: " + sessionId);
        applicationErrorCode = NO_APPLICATION_ERROR;
        try {
            attachMediaChannel();
            this.sessionId = sessionId;
            remoteMediaPlayer.requestStatus(apiClient).
                    setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "Impossible to get request status: " + result.getStatus().getStatusCode());
                            }

                        }
                    });
            for (Listener listener : listeners){
                listener.onApplicationConnected();
            }
        } catch (NoConnectionException e) {
            Log.e(TAG, "Failed to attach media/data channel due to network issues", e);
        }
    }

    public void onApplicationConnectionFailed(int errorCode) {
        Log.d(TAG, "onApplicationConnectionFailed() reached with errorCode: " + errorCode);
        applicationErrorCode = errorCode;
        onDeviceSelected(null);
        if (mediaRouter != null) {
            Log.d(TAG, "onApplicationConnectionFailed(): Setting route to default");
            mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
        }
    }

    public final void startCastDiscovery() {
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    public final void stopCastDiscovery() {
        mediaRouter.removeCallback(mediaRouterCallback);
    }

    /*
     * Updates the playback status of the Media Session
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateMediaSession(boolean playing) {
        if (!isConnected()) {
            return;
        }
        try {
            if ((mediaSessionCompat == null) && playing) {
                setUpMediaSession(getRemoteMediaInformation());
            }
            if (mediaSessionCompat != null) {
                int playState = isRemoteStreamLive() ? PlaybackStateCompat.STATE_BUFFERING
                        : PlaybackStateCompat.STATE_PLAYING;
                int state = playing ? playState : PlaybackStateCompat.STATE_PAUSED;

                mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(state, 0, 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build());
            }
        } catch (NoConnectionException e) {
            Log.e(TAG, "Failed to set up MediaSessionCompat due to network issues", e);
        }
    }

    private void setUpMediaSession(final MediaInfo info) {
        if (mediaSessionCompat == null) {
            mediaEventReceiver = new ComponentName(context, MediaPlayerService.class.getName());
            mediaSessionCompat = new MediaSessionCompat(context, "TAG", mediaEventReceiver,
                    null);
            mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSessionCompat.setActive(true);
            mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    KeyEvent keyEvent = mediaButtonIntent
                            .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE
                            || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY)) {
                        toggle();
                    }
                    return true;
                }

                @Override
                public void onPlay() {
                    toggle();
                }

                @Override
                public void onPause() {
                    toggle();
                }

                private void toggle() {
                    try {
                        togglePlayback();
                    } catch (NoConnectionException e) {
                        Log.e(TAG, "MediaSessionCompat.Callback(): Failed to toggle playback", e);
                    }
                }
            });
        }

        mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build());

        // Update the media session's image
        //updateLockScreenImage(info);

        // update the media session's metadata
        //updateMediaSessionMetadata();

        mediaRouter.setMediaSessionCompat(mediaSessionCompat);
    }

    public void clearMediaSession() {
        Log.d(TAG, "clearMediaSession()");
        if (mediaSessionCompat != null) {
            mediaSessionCompat.setActive(false);
            mediaSessionCompat.release();
            mediaSessionCompat = null;
        }
    }

    /**
     * Toggles the playback of the movie.
     *
     * @throws NoConnectionException
     */
    public void togglePlayback() throws NoConnectionException {
        checkConnectivity();
        boolean isPlaying = isRemoteMediaPlaying();
        if (isPlaying) {
            pause();
        } else {
            if (state == MediaStatus.PLAYER_STATE_IDLE
                    && idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                loadMedia(getRemoteMediaInformation(), true, 0);
            } else {
                play();
            }
        }
    }

    public void loadMedia(MediaInfo media, boolean autoPlay, long position) throws NoConnectionException {
        Log.d(TAG, "loadMedia");
        checkConnectivity();
        if (media == null) {
            return;
        }
        if (remoteMediaPlayer == null) {
            Log.e(TAG, "Trying to load a video with no active media session");
            throw new NoConnectionException();
        }

        remoteMediaPlayer.load(apiClient, media, autoPlay, position)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {

                    }
                });
    }


    public void play() throws NoConnectionException {
        Log.d(TAG, "play(customData)");
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        remoteMediaPlayer.play(apiClient)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Impossible to play remotePlayer: " + result.getStatus().getStatusCode());
                        }
                    }

                });
    }

    public void pause() throws NoConnectionException {
        Log.d(TAG, "attempting to pause media");
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        remoteMediaPlayer.pause(apiClient)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Impossible to pause remotePlayer: " + result.getStatus().getStatusCode());
                        }
                    }

                });
    }

    public void seek(long position) throws NoConnectionException {
        Log.d(TAG, "attempting to seek media");
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        ResultCallback<RemoteMediaPlayer.MediaChannelResult> resultCallback =
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Impossible to pause remotePlayer: " + result.getStatus().getStatusCode());
                        }
                    }

                };
        remoteMediaPlayer.seek(apiClient,
                position,
                RemoteMediaPlayer.RESUME_STATE_PLAY).setResultCallback(resultCallback);
    }

    public void stop() throws NoConnectionException {
        Log.d(TAG, "stop()");
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        remoteMediaPlayer.stop(apiClient).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Impossible to stop remotePlayer: " + result.getStatus().getStatusCode());
                        }
                    }

                }
        );
    }

    public final void setDeviceVolume(double volume) throws NoConnectionException {
        checkConnectivity();
        try {
            Cast.CastApi.setVolume(apiClient, volume);
        } catch (IllegalStateException e) {
            throw new NoConnectionException("setDeviceVolume()", e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final double getDeviceVolume() throws NoConnectionException {
        checkConnectivity();
        try {
            return Cast.CastApi.getVolume(apiClient);
        } catch (IllegalStateException e) {
            throw new NoConnectionException("getDeviceVolume()", e);
        }
    }

    /**
     * Returns the {@link MediaInfo} for the current media
     *
     * @throws NoConnectionException If no connectivity to the device exists
     */
    public MediaInfo getRemoteMediaInformation() throws NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        return remoteMediaPlayer.getMediaInfo();
    }

    /**
     * Called when a {@link CastDevice} is extracted from the {@link MediaRouter.RouteInfo}. This is where all
     * the fun starts!
     */
    public final void onDeviceSelected(CastDevice device) {
        if (device == null) {
            disconnectDevice(true, false);
        } else {
            setDevice(device);
        }
    }

    private void launchApp() throws NoConnectionException {
        Log.d(TAG, "launchApp() is called");
        if (!isConnected()) {
            checkConnectivity();
        }
        Log.d(TAG, "Launching app");
        Cast.CastApi.launchApplication(apiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .setResultCallback(
                        new ResultCallback<Cast.ApplicationConnectionResult>() {

                            @Override
                            public void onResult(Cast.ApplicationConnectionResult result) {
                                if (result.getStatus().isSuccess()) {
                                    Log.d(TAG, "launchApplication() -> success result");
                                    onApplicationConnected(result.getApplicationMetadata(),
                                            result.getApplicationStatus(),
                                            result.getSessionId(),
                                            result.getWasLaunched());
                                } else {
                                    Log.d(TAG, "launchApplication() -> failure result");
                                    onApplicationConnectionFailed(
                                            result.getStatus().getStatusCode());
                                }
                            }
                        }
                );
    }

    /**
     * Stops the application on the receiver device.
     */
    public final void stopApplication() throws NoConnectionException {
        checkConnectivity();
        Cast.CastApi.stopApplication(apiClient, sessionId).setResultCallback(
                new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status result) {
                        if (!result.isSuccess()) {
                            Log.d(TAG, "stopApplication -> onResult: stopping "
                                    + "application failed");
                        } else {
                            Log.d(TAG, "stopApplication -> onResult Stopped application "
                                    + "successfully");
                        }
                    }
                });
    }

    private void attachMediaChannel() throws NoConnectionException {
        Log.d(TAG, "attachMediaChannel()");
        checkConnectivity();
        if (remoteMediaPlayer == null) {
            remoteMediaPlayer = new RemoteMediaPlayer();

            remoteMediaPlayer.setOnStatusUpdatedListener(
                    new RemoteMediaPlayer.OnStatusUpdatedListener() {

                        @Override
                        public void onStatusUpdated() {
                            Log.d(TAG, "RemoteMediaPlayer::onStatusUpdated() is reached");
                            ChromeCastManager.this.onRemoteMediaPlayerStatusUpdated();
                        }
                    }
            );

            remoteMediaPlayer.setOnPreloadStatusUpdatedListener(
                    new RemoteMediaPlayer.OnPreloadStatusUpdatedListener() {

                        @Override
                        public void onPreloadStatusUpdated() {
                            Log.d(TAG,
                                    "RemoteMediaPlayer::onPreloadStatusUpdated() is reached");
                            //VideoCastManager.this.onRemoteMediaPreloadStatusUpdated();
                        }
                    });


            remoteMediaPlayer.setOnMetadataUpdatedListener(
                    new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                        @Override
                        public void onMetadataUpdated() {
                            Log.d(TAG, "RemoteMediaPlayer::onMetadataUpdated() is reached");
                            //VideoCastManager.this.onRemoteMediaPlayerMetadataUpdated();
                        }
                    }
            );
        }
        try {
            Log.d(TAG, "Registering MediaChannel namespace");
            Cast.CastApi.setMessageReceivedCallbacks(apiClient, remoteMediaPlayer.getNamespace(),
                    remoteMediaPlayer);
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "attachMediaChannel()", e);
        }
    }

    private void onRemoteMediaPlayerStatusUpdated() {
        Log.d(TAG, "onRemoteMediaPlayerStatusUpdated() reached");
        if (apiClient == null || remoteMediaPlayer == null
                || remoteMediaPlayer.getMediaStatus() == null) {
            Log.d(TAG, "mApiClient or remoteMediaPlayer is null, so will not proceed");
            return;
        }
        mediaStatus = remoteMediaPlayer.getMediaStatus();
        state = mediaStatus.getPlayerState();
        idleReason = mediaStatus.getIdleReason();

        if (state == MediaStatus.PLAYER_STATE_PLAYING) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = playing");
            updateMediaSession(true);
        } else if (state == MediaStatus.PLAYER_STATE_PAUSED) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = paused");
            updateMediaSession(false);
        } else if (state == MediaStatus.PLAYER_STATE_IDLE) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = idle");
            updateMediaSession(false);
            switch (idleReason) {
                case MediaStatus.IDLE_REASON_FINISHED:
                    clearMediaSession();
                    break;
                case MediaStatus.IDLE_REASON_ERROR:
                    // something bad happened on the cast device
                    Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = ERROR");
                    clearMediaSession();
                    break;
                case MediaStatus.IDLE_REASON_CANCELED:
                    Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = CANCELLED");
                    break;
                case MediaStatus.IDLE_REASON_INTERRUPTED:
                    if (mediaStatus.getLoadingItemId() == MediaQueueItem.INVALID_ITEM_ID) {
                        // we have reached the end of queue
                        clearMediaSession();
                    }
                    break;
                default:
                    Log.e(TAG, "onRemoteMediaPlayerStatusUpdated(): Unexpected Idle Reason "
                            + idleReason);
            }
        } else if (state == MediaStatus.PLAYER_STATE_BUFFERING) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = buffering");
        } else {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = unknown");
        }
        /*for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onRemoteMediaPlayerStatusUpdated();
            consumer.onVolumeChanged(volume, isMute);
        }*/
    }

    public final void disconnect() {
        if (isConnected() || isConnecting()) {
            disconnectDevice(true, true);
        }
    }

    public final void disconnectDevice(boolean clearPersistedConnectionData,
                                       boolean setDefaultRoute) {
        Log.d(TAG, "disconnectDevice(" + clearPersistedConnectionData + "," + setDefaultRoute + ")");
        if (selectedCastDevice == null) {
            return;
        }
        selectedCastDevice = null;
        deviceName = null;

        String message = "disconnectDevice() Disconnect Reason: ";
        if (connectionSuspended) {
            message += "Connectivity lost";
        } else {
            switch (applicationErrorCode) {
                case CastStatusCodes.APPLICATION_NOT_RUNNING:
                    message += "App was taken over or not available anymore";
                    break;
                case NO_APPLICATION_ERROR:
                    message += "Intentional disconnect";
                    break;
                default:
                    message += "Other";
            }
        }
        Log.d(TAG, message);

        Log.d(TAG, "connectionSuspended: " + connectionSuspended);
        try {
            if ((isConnected() || isConnecting())) {
                Log.d(TAG, "Calling stopApplication");
                stopApplication();
            }
        } catch (NoConnectionException e) {
            Log.e(TAG, "Failed to stop the application after disconnecting route", e);
        }
        if (apiClient != null) {
            // the following check is currently required, without including a check for
            // isConnecting() due to a bug in the current play services library and will be removed
            // when that bug is addressed; calling disconnect() while we are in "connecting" state
            // will throw an exception
            if (apiClient.isConnected()) {
                Log.d(TAG, "Trying to disconnect");
                apiClient.disconnect();
            }
            if ((mediaRouter != null) && setDefaultRoute) {
                Log.d(TAG, "disconnectDevice(): Setting route to default");
                mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
            }
            apiClient = null;
        }
        sessionId = null;
        if (clearPersistedConnectionData && !connectionSuspended) {
            clearMediaSession();
        }
        state = MediaStatus.PLAYER_STATE_IDLE;
        
        for (Listener listener : listeners){
            listener.onApplicationDisconnected();
        }
    }

    /**
     * Adds and wires up the Media Router cast button. It returns a reference to the Media Router
     * menu item if the caller needs such reference. It is assumed that the enclosing
     * {@link android.app.Activity} inherits (directly or indirectly) from
     * {@link android.support.v7.app.AppCompatActivity}.
     *
     * @param menu           Menu reference
     * @param menuResourceId The resource id of the cast button in the xml menu descriptor file
     */
    public final MenuItem addMediaRouterButton(Menu menu, int menuResourceId) {
        MenuItem mediaRouteMenuItem = menu.findItem(menuResourceId);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider)
                MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
        return mediaRouteMenuItem;
    }

    /**
     * A simple method that throws an exception if there is no connectivity to the cast device.
     *
     * @throws NoConnectionException If no connectivity to the device exists
     */
    public final void checkConnectivity() throws
            NoConnectionException {
        if (!isConnected()) {
            throw new NoConnectionException();
        }
    }

    private void checkRemoteMediaPlayerAvailable() throws NoConnectionException {
        if (remoteMediaPlayer == null) {
            throw new NoConnectionException();
        }
    }

    private void setDevice(CastDevice device) {
        selectedCastDevice = device;
        deviceName = selectedCastDevice.getFriendlyName();

        if (apiClient == null) {
            Log.d(TAG, "acquiring a connection to Google Play services for " + selectedCastDevice);
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(selectedCastDevice, new CastListener());
            apiOptionsBuilder.setVerboseLoggingEnabled(true);
            apiClient = new GoogleApiClient.Builder(context)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            apiClient.connect();
        } else if (!apiClient.isConnected() && !apiClient.isConnecting()) {
            apiClient.connect();
        }
    }

    public boolean isRemoteMediaPlaying() throws NoConnectionException {
        checkConnectivity();
        return state == MediaStatus.PLAYER_STATE_BUFFERING
                || state == MediaStatus.PLAYER_STATE_PLAYING;
    }

    public final boolean isRemoteStreamLive() throws NoConnectionException {
        checkConnectivity();
        MediaInfo info = getRemoteMediaInformation();
        return (info != null) && (info.getStreamType() == MediaInfo.STREAM_TYPE_LIVE);
    }

    /**
     * can be used to find out if the application is connected to the service or not.
     *
     * @return <code>true</code> if connected, <code>false</code> otherwise.
     */
    public final boolean isConnected() {
        return (apiClient != null) && apiClient.isConnected();
    }

    /**
     * Returns <code>true</code> only if application is connecting to the Cast service.
     */
    public final boolean isConnecting() {
        return (apiClient != null) && apiClient.isConnecting();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected() reached with prior suspension: " + connectionSuspended);
        if (connectionSuspended || apiClient == null) {
            connectionSuspended = false;
            if (bundle != null && bundle.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                // the same app is not running any more
                Log.d(TAG, "onConnected(): App no longer running, so disconnecting");
                disconnect();
            }
            return;
        }
        try {
            Cast.CastApi.requestStatus(apiClient);
            launchApp();
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "requestStatus()", e);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        for (Listener listener : listeners) {
            listener.onApplicationDisconnected();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public MediaRouter.RouteInfo getRouteInfo() {
        return routeInfo;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public static boolean checkGooglePlayServices(final Activity activity) {
        final int googlePlayServicesCheck = GooglePlayServicesUtil.isGooglePlayServicesAvailable(
                activity);
        switch (googlePlayServicesCheck) {
            case ConnectionResult.SUCCESS:
                return true;
            default:
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(googlePlayServicesCheck,
                        activity, 0);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        activity.finish();
                    }
                });
                dialog.show();
        }
        return false;
    }

    public long getMediaDuration() throws NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        return remoteMediaPlayer.getStreamDuration();
    }

    public long getMediaPosition() throws NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        return remoteMediaPlayer.getApproximateStreamPosition();
    }

    public void setMuted(boolean muted) throws NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        remoteMediaPlayer.setStreamMute(apiClient, muted);
    }

    public int getMediaStatus() {
        return state;
    }

    public interface Listener {
        void onApplicationConnected();

        void onApplicationDisconnected();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    class CastListener extends Cast.Listener {

        /*
         * (non-Javadoc)
         * @see com.google.android.gms.cast.Cast.Listener#onApplicationDisconnected (int)
         */
        @Override
        public void onApplicationDisconnected(int statusCode) {
            ChromeCastManager.this.onApplicationDisconnected(statusCode);
        }

        /*
         * (non-Javadoc)
         * @see com.google.android.gms.cast.Cast.Listener#onApplicationStatusChanged ()
         */
        @Override
        public void onApplicationStatusChanged() {
        }

        @Override
        public void onVolumeChanged() {
        }
    }

    public static class CastMediaRouterCallback extends MediaRouter.Callback{
        private static final String TAG = "CastMediaRouterCallback";

        private final ChromeCastManager chromeCastManager;

        public CastMediaRouterCallback(ChromeCastManager chromeCastManager) {
            this.chromeCastManager = chromeCastManager;
        }

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteSelected: info=" + info);
            CastDevice device = CastDevice.getFromBundle(info.getExtras());
            chromeCastManager.onDeviceSelected(device);
            Log.d(TAG, "onRouteSelected: mSelectedDevice=" + device.getFriendlyName());
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.d(TAG, "onRouteUnselected: route=" + route);
            chromeCastManager.onDeviceSelected(null);
        }
    }

    public boolean incrementVolume() {
        return changeVolume(VOLUME_INCREMENT);
    }

    public boolean decrementVolume() {
        return changeVolume(- VOLUME_INCREMENT);
    }

    public boolean changeVolume(double volumeIncrement) {
        if (isConnected()) {
            try {
                double currentVolume = Cast.CastApi.getVolume(apiClient);
                Cast.CastApi.setVolume(apiClient,
                        Math.max(Math.min(currentVolume + volumeIncrement, 1), 0));
            } catch (Exception e) {
                Log.e(TAG, "unable to set volume", e);
            }
            return true;
        } else {
            return false;
        }
    }
}