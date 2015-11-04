package ch.srg.mediaplayer.internal.cast;

import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.google.android.gms.cast.CastDevice;

/**
 * Created by npietri on 02.11.15.
 */
public class CastMediaRouterCallback extends MediaRouter.Callback{
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
