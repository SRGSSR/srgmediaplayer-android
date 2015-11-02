package ch.srg.mediaplayer.service.cast;

import android.content.Context;
import android.media.AudioManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager {
    private static final String TAG = "ChromeCastManager";

    private static ChromeCastManager instance;
    private AudioManager audioManager;
    private RemoteMediaPlayer remoteMediaPlayer;
    private MediaSessionCompat mediaSessionCompat;
    private String applicationId;
    private int state = MediaStatus.PLAYER_STATE_IDLE;
    private int idleReason;
    private String dataNamespace;
    private Cast.MessageReceivedCallback dataChannel;

    protected Context context;
    protected MediaRouter mediaRouter;
    protected MediaRouteSelector mediaRouteSelector;
    protected CastMediaRouterCallback mediaRouterCallback;
    protected CastDevice selectedCastDevice;
    protected String deviceName;

    protected GoogleApiClient apiClient;
    protected String sessionId;
    private MediaRouter.RouteInfo routeInfo;

    protected ChromeCastManager(Context context, String applicationId, String dataNamespace) {
        this.context = context.getApplicationContext();
        this.applicationId = applicationId;

        this.mediaRouter = MediaRouter.getInstance(context);
        this.mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(
                CastMediaControlIntent.categoryForCast(applicationId)).build();

        mediaRouterCallback = new CastMediaRouterCallback(this);
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        Log.d(TAG, "VideoCastManager is instantiated");
        this.dataNamespace = dataNamespace;

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Returns a (singleton) instance of this class. Clients should call this method in order to
     * get a hold of this singleton instance, only after it is initialized. If it is not initialized
     * yet, an {@link IllegalStateException} will be thrown.
     *
     */
    public static ChromeCastManager getInstance() {
        if (instance == null) {
            String msg = "No VideoCastManager instance was found, did you forget to initialize it?";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
        return instance;
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
     * Stops the application on the receiver device.
     */
    public final void stopApplication() {
        if (isConnected()) {
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
    }

    /**
     * can be used to find out if the application is connected to the service or not.
     *
     * @return <code>true</code> if connected, <code>false</code> otherwise.
     */
    public final boolean isConnected() {
        return (apiClient != null) && apiClient.isConnected();
    }

}
