package ch.srg.mediaplayer.service;

import android.os.Bundle;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;

import ch.srg.mediaplayer.SRGMediaPlayerException;

/**
 * Created by seb on 27/10/15.
 */
public class ChromeCastManager {
    private RemoteMediaPlayer remoteMediaPlayer;
    private GoogleApiClient apiClient;
    private String sessionId;

    private MediaRouter mediaRouter;
    private boolean connected;


    public boolean isConnected() {
        return remoteMediaPlayer != null && apiClient != null && sessionId != null && apiClient.isConnected();
    }

    public void onConnected(Bundle bundle) {
        connected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {
        controller.onPlayerDelegateError(this, new SRGMediaPlayerException("Google cast disconnected"));
        connected = false;
    }

}
