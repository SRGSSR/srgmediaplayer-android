package ch.srg.mediaplayer.internal.cast;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat;
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
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import ch.srg.mediaplayer.internal.cast.exceptions.NoConnectionException;
import ch.srg.mediaplayer.internal.session.MediaSessionManager;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MediaSessionManager.Listener {
    private static final String TAG = "ChromeCastManager";

    public static final int DISCONNECT_REASON_OTHER = 0;
    public static final int DISCONNECT_REASON_CONNECTIVITY = 1;
    public static final int DISCONNECT_REASON_APP_NOT_RUNNING = 2;
    public static final int DISCONNECT_REASON_EXPLICIT = 3;

    public static final int NO_APPLICATION_ERROR = 0;
    private static final double VOLUME_INCREMENT = 0.075;

    private static ChromeCastManager instance;
    private final MediaSessionManager mediaSessionManager;
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
    private int stoppingCounter;
    private final Object stoppingLock = new Object();
    private boolean applicationConnected;
    private int castDiscoveryCounter;
    private boolean errorDialogShown;

    protected ChromeCastManager(Context context) {
        this.context = context.getApplicationContext();

        this.mediaRouter = MediaRouter.getInstance(context);
        this.mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();

        mediaRouterCallback = new CastMediaRouterCallback(this);
        mediaSessionManager = MediaSessionManager.getInstance();

        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

        Log.d(TAG, "VideoCastManager is instantiated");
    }

    public static synchronized ChromeCastManager initialize(Context context) {
        if (instance == null) {
            Log.d(TAG, "New instance of VideoCastManager is created");
            if (ConnectionResult.SUCCESS != GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)) {
                String msg = "Couldn't find the appropriate version of Google Play Services";
                Log.e(TAG, msg);
            }
            instance = new ChromeCastManager(context);
        }
        return instance;
    }

    public static boolean isInstantiated() {
        return instance != null;
    }

    /**
     * Returns a (singleton) instance of this class. Clients should call this method in order to
     * get a hold of this singleton instance, only after it is initialized. If it is not initialized
     * yet, an {@link IllegalStateException} will be thrown.
     */
    public static ChromeCastManager getInstance() {
        if (instance == null) {
            String msg = "No ChromeCastManager instance was found, did you forget to initialize it?";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
        return instance;
    }

    private void onApplicationDisconnected(int errorCode) {
        Log.d(TAG, "onApplicationDisconnected() reached with error code: " + errorCode);
        applicationErrorCode = errorCode;

        applicationConnected = false;

        mediaSessionManager.updateMediaSession(false);
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

        applicationConnected = true;

        try {
            attachMediaChannel();
            this.sessionId = sessionId;
            remoteMediaPlayer.requestStatus(apiClient).
                    setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "Application connected, remote media player status code: " + result.getStatus().getStatusCode());
                            }

                        }
                    });


            if (listeners.isEmpty()) {
                Log.d(TAG, "No listener found for application connected");
            } else {
                Log.d(TAG, listeners.size() + " listener(s) found for application connected");
            }
            HashSet<Listener> listeners = new HashSet<>(this.listeners);
            for (Listener listener : listeners) {
                listener.onChromeCastApplicationConnected();
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
        if (castDiscoveryCounter == 0) {
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        }
        castDiscoveryCounter++;
    }

    public final void stopCastDiscovery() {
        castDiscoveryCounter--;
        if (castDiscoveryCounter == 0) {
            mediaRouter.removeCallback(mediaRouterCallback);
        } else if (castDiscoveryCounter < 0) {
            throw new IllegalStateException("Mismatched calls to stopCastDiscovery / startCastDiscovery");
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
            loadIfNecessaryAndPlay();
        }
    }

    public void loadIfNecessaryAndPlay() throws NoConnectionException {
        if (state == MediaStatus.PLAYER_STATE_IDLE
                && idleReason == MediaStatus.IDLE_REASON_FINISHED) {
            MediaInfo remoteMediaInformation = getRemoteMediaInformation();
            if (remoteMediaInformation != null) {
                loadMedia(remoteMediaInformation, true, 0);
            }
        } else {
            play();
        }
    }

    public void loadMedia(@NonNull MediaInfo media, boolean autoPlay, long position) throws NoConnectionException {
        Log.d(TAG, "loadMedia " + media.getContentId());
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();

        waitForStopping();

        remoteMediaPlayer.load(apiClient, media, autoPlay, position)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {

                    }
                });
    }

    public void waitForStopping() {
        boolean waited = false;
        synchronized (stoppingLock) {
            while (stoppingCounter > 0) {
                waited = true;
                Log.d(TAG, "Waiting for stopped");
                try {
                    stoppingLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (waited) {
            Log.d(TAG, "Done waiting");
        }
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
        try {
            remoteMediaPlayer.seek(apiClient,
                    position,
                    RemoteMediaPlayer.RESUME_STATE_PLAY).setResultCallback(resultCallback);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when seeking to " + position, e);
            throw new NoConnectionException(e);
        }
    }

    public void stop() throws NoConnectionException {
        Log.d(TAG, "stop()");
        stoppingCounter++;
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        remoteMediaPlayer.stop(apiClient).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Impossible to stop remotePlayer: " + result.getStatus().getStatusCode());
                        }
                        synchronized (stoppingLock) {
                            Log.d(TAG, "Stopped " + stoppingCounter);
                            stoppingCounter--;
                            stoppingLock.notify();
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
        Log.d(TAG, "Launching app");
        if (apiClient == null || !apiClient.isConnected()) {
            throw new NoConnectionException();
        }

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

        boolean updateMediaRouter = true;
        mediaStatus = remoteMediaPlayer.getMediaStatus();
        state = mediaStatus.getPlayerState();
        idleReason = mediaStatus.getIdleReason();

        if (state == MediaStatus.PLAYER_STATE_PLAYING) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = playing");
            mediaSessionManager.updateMediaSession(true);
        } else if (state == MediaStatus.PLAYER_STATE_PAUSED) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = paused");
            mediaSessionManager.updateMediaSession(false);
        } else if (state == MediaStatus.PLAYER_STATE_IDLE) {
            Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = idle");
            mediaSessionManager.updateMediaSession(false);
            switch (idleReason) {
                case MediaStatus.IDLE_REASON_FINISHED:
                    mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
                    break;
                case MediaStatus.IDLE_REASON_ERROR:
                    // something bad happened on the cast device
                    Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = ERROR");
                    mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
                    break;
                case MediaStatus.IDLE_REASON_CANCELED:
                    Log.d(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = CANCELLED");
                    mediaRouter.setMediaSessionCompat(null);
                    updateMediaRouter = false;
                    break;
                case MediaStatus.IDLE_REASON_INTERRUPTED:
                    if (mediaStatus.getLoadingItemId() == MediaQueueItem.INVALID_ITEM_ID) {
                        // we have reached the end of queue
                        mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
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

        HashSet<Listener> listeners = new HashSet<>(this.listeners);
        for (Listener listener : listeners) {
            listener.onChromeCastPlayerStatusUpdated(state, idleReason);
        }

        if (updateMediaRouter) {
            try {
                mediaRouter.setMediaSessionCompat(mediaSessionManager.requestMediaSession(getRemoteMediaInformation()));
            } catch (NoConnectionException e) {
                e.printStackTrace();
            }
        }

    }

    public final void disconnect() {
        if (isApplicationConnected() || isConnecting()) {
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
            if ((isApplicationConnected() || isConnecting())) {
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
            mediaSessionManager.clearMediaSession(mediaSessionCompat != null ? mediaSessionCompat.getSessionToken() : null);
        }

        HashSet<Listener> listeners = new HashSet<>(this.listeners);
        if (listeners.isEmpty()) {
            Log.d(TAG, "No listener found for application disconnected");
        } else {
            Log.d(TAG, listeners.size() + " listener(s) found for application disconnected");
        }
        for (Listener listener : listeners) {
            listener.onChromeCastApplicationDisconnected();
        }

        state = MediaStatus.PLAYER_STATE_IDLE;
    }

    /**
     * Adds and wires up the Media Router cast button. It returns a reference to the Media Router
     * menu item if the caller needs such reference. It is assumed that the enclosing
     * {@link android.app.Activity} inherits (directly or indirectly) from
     * {@link android.support.v7.app.AppCompatActivity}.
     *
     * @param menu           Menu reference
     * @param menuResourceId The resource id of the cast button in the xml menu descriptor file
     * @return menu item or null if chrome cast not supported
     */
    public final MenuItem addMediaRouterButtonIfSupported(Activity activity, Menu menu, int menuResourceId) {
        if (checkGooglePlayServices(activity)) {
            MenuItem mediaRouteMenuItem = menu.findItem(menuResourceId);
            MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider)
                    MenuItemCompat.getActionProvider(mediaRouteMenuItem);
            mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
            return mediaRouteMenuItem;
        } else {
            return null;
        }
    }

    /**
     * A simple method that throws an exception if there is no connectivity to the cast device.
     *
     * @throws NoConnectionException If no connectivity to the device exists
     */
    public final void checkConnectivity() throws NoConnectionException {
        if (!isApplicationConnected()) {
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
    public final boolean isApplicationConnected() {
        return applicationConnected && apiClient != null && apiClient.isConnected();
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
        HashSet<Listener> listeners = new HashSet<>(this.listeners);
        for (Listener listener : listeners) {
            listener.onChromeCastApplicationDisconnected();
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

    public boolean checkGooglePlayServices(Activity activity) {
        final int googlePlayServicesCheck = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        switch (googlePlayServicesCheck) {
            case ConnectionResult.SUCCESS:
                return true;
            default:
                if (!errorDialogShown) {
                    try {
                        errorDialogShown = GoogleApiAvailability.getInstance().showErrorDialogFragment(activity, googlePlayServicesCheck, 1000);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Show error in check google play services", e);
                    }
                }
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

    public boolean handleKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (isApplicationConnected()) {
                    incrementVolume();
                    return true;
                } else {
                    return false;
                }
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (isApplicationConnected()) {
                    decrementVolume();
                    return true;
                } else {
                    return false;
                }
            default:
                return false;
        }
    }

    @Override
    public void onBitmapUpdate(Bitmap bitmap) {
    }

    @Override
    public void onResumeSession() {
    }

    @Override
    public void onPauseSession() {
    }

    @Override
    public void onStopSession() {
    }

    @Override
    public void onMediaSessionUpdated() {
        Log.d(TAG, "onMediaSessionUpdated from listener");
        try {
            mediaRouter.setMediaSessionCompat(mediaSessionManager.requestMediaSession(getRemoteMediaInformation()));
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
    }


    public interface Listener {
        void onChromeCastApplicationConnected();

        void onChromeCastApplicationDisconnected();

        void onChromeCastPlayerStatusUpdated(int state, int idleReason);
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

    public static class CastMediaRouterCallback extends MediaRouter.Callback {
        private final ChromeCastManager chromeCastManager;

        public CastMediaRouterCallback(ChromeCastManager chromeCastManager) {
            this.chromeCastManager = chromeCastManager;
        }

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteSelected: info=" + info);
            CastDevice device = CastDevice.getFromBundle(info.getExtras());
            if (device != null) {
                chromeCastManager.onDeviceSelected(device);
                Log.d(TAG, "onRouteSelected: mSelectedDevice=" + device.getFriendlyName());
            }
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
        return changeVolume(-VOLUME_INCREMENT);
    }

    public boolean changeVolume(double volumeIncrement) {
        if (isApplicationConnected()) {
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