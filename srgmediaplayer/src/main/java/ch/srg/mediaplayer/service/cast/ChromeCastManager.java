package ch.srg.mediaplayer.service.cast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.view.Menu;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.List;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager implements OptionsProvider {
    private static final String TAG = "ChromeCastManager";

    private static boolean isApplicationConnected;

    @Override
    public CastOptions getCastOptions(Context context) {
        CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
                .setNotificationOptions(null)
                .build();
        return new CastOptions.Builder()
                .setReceiverApplicationId("CC1AD845")
                .setCastMediaOptions(mediaOptions)
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }

    public static boolean isApplicationConnected(final Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                CastContext castContext = CastContext.getSharedInstance(context);
                if (castContext.getSessionManager() != null && castContext.getSessionManager().getCurrentCastSession() != null) {
                    isApplicationConnected = castContext.getSessionManager().getCurrentCastSession().isConnected();
                } else {
                    isApplicationConnected = false;
                }
            }
        });
        return isApplicationConnected;
    }

    @Nullable
    public static RemoteMediaClient getRemoteMediaClient(Context context) {
        return CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession().getRemoteMediaClient();
    }

    public static void addMediaRouterButtonIfSupported(Context context, Menu menu, @IdRes int media_route_menu_item) {
        CastButtonFactory.setUpMediaRouteButton(context, menu, media_route_menu_item);
    }

    public static void registerContext(Context context) {
        CastContext.getSharedInstance(context);
    }
}