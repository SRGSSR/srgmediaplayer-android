package ch.srg.mediaplayer.service.cast;

import android.support.v7.media.MediaRouter;

/**
 * Created by npietri on 02.11.15.
 */
public class CastMediaRouterCallback extends MediaRouter.Callback{

    private final ChromeCastManager chromeCastManager;

    public CastMediaRouterCallback(ChromeCastManager chromeCastManager) {
        this.chromeCastManager = chromeCastManager;
    }
}
