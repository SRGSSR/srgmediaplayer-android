package ch.srg.mediaplayer.service.cast;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.view.Menu;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.List;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager implements OptionsProvider {
    private static final String TAG = "ChromeCastManager";

    private static Context context;

    @Override
    public CastOptions getCastOptions(Context context) {
        ChromeCastManager.context = context;
        CastOptions options = new CastOptions.Builder()
                .setReceiverApplicationId()
                .build();
        return options;
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }

    public static boolean isApplicationConnected() {
        return CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession().isConnected();
    }

    @Nullable
    public static RemoteMediaClient getRemoteMediaClient() {
        return CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession().getRemoteMediaClient();
    }

    public static void addMediaRouterButtonIfSupported(Context context, Menu menu, @IdRes int media_route_menu_item) {
        CastContext.getSharedInstance(context);
        CastButtonFactory.setUpMediaRouteButton(context, menu, media_route_menu_item);
    }
}