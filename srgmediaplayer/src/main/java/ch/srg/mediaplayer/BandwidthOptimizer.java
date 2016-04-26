package ch.srg.mediaplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Created by seb on 20/04/16.
 */
public class BandwidthOptimizer implements SRGMediaPlayerController.Listener {
    private static final String TAG = SRGMediaPlayerController.TAG;
    Long lastBandwidthEstimate;
    int lastNetworkEstimate;
    private ConnectivityManager connectivityManager;
    private Long wifiDefaultEstimate;
    private Long mobileDefaultEstimate;


    public void start(Context context) {
        SRGMediaPlayerController.registerGlobalEventListener(this);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void stop() {
        SRGMediaPlayerController.unregisterGlobalEventListener(this);
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        Long mpBandwidthEstimate = mp.getBandwidthEstimate();
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        int networkType = networkInfo.getType();
        if (lastNetworkEstimate != networkType) {
            lastNetworkEstimate = networkType;
            switch (networkType) {
                case ConnectivityManager.TYPE_WIFI:
                case ConnectivityManager.TYPE_WIMAX:
                case ConnectivityManager.TYPE_ETHERNET:
                    lastBandwidthEstimate = wifiDefaultEstimate;
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    lastBandwidthEstimate = mobileDefaultEstimate;
                    break;
                default:
                    lastBandwidthEstimate = null;
            }
        }
        if (mpBandwidthEstimate != null) {
            lastBandwidthEstimate = mpBandwidthEstimate;
        } else {
            if (event.type == SRGMediaPlayerController.Event.Type.MEDIA_READY_TO_PLAY) {
                if (lastBandwidthEstimate != null) {
                    Log.d(TAG, "Using bandwidth: " + lastBandwidthEstimate);
                    mp.setQualityDefault(lastBandwidthEstimate);
                }
            }
        }
    }

    public void setWifiDefaultEstimate(Long wifiDefaultEstimate) {
        this.wifiDefaultEstimate = wifiDefaultEstimate;
    }

    public void setMobileDefaultEstimate(Long mobileDefaultEstimate) {
        this.mobileDefaultEstimate = mobileDefaultEstimate;
    }
}
