package ch.srg.mediaplayer.internal.cast;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import java.io.IOException;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

/**
 * Created by npietri on 19.10.15.
 */
public class GoogleCastDelegate implements PlayerDelegate {

    private static final String TAG = "GoogleCastDelegate";
    private final OnPlayerDelegateListener controller;

    private RemoteMediaPlayer mRemoteMediaPlayer;
    private GoogleApiClient mApiClient;
    private String mSessionId;

    private boolean mApplicationStarted;

    private MediaInfo mediaInfo;

    private int internalStatus;
    private boolean delegateReady;
    private boolean playIfReady;
    private long pendingSeekTo;

    public GoogleCastDelegate(String mSessionId, GoogleApiClient mApiClient, OnPlayerDelegateListener controller) {
        this.mSessionId = mSessionId;
        this.mApiClient = mApiClient;
        this.controller = controller;
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                    mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
            mRemoteMediaPlayer.setOnStatusUpdatedListener(listener);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }
    }

    @Override
    public boolean canRenderInView(View view) {
        return true;
    }

    @Override
    public View createRenderingView(Context parentContext) {
        return new View(parentContext);
    }

    @Override
    public void bindRenderingViewInUiThread(SRGMediaPlayerView mediaPlayerView) throws SRGMediaPlayerException {

    }

    @Override
    public void unbindRenderingView() {

    }


    @Override
    public void prepare(Uri videoUri) throws SRGMediaPlayerException {
        mRemoteMediaPlayer.requestStatus(mApiClient);
        Log.d(TAG, "Prepare: " + videoUri);
        controller.onPlayerDelegatePreparing(this);
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, "SRGPLAY");
        mediaInfo = new MediaInfo.Builder(String.valueOf(videoUri))
                .setContentType("application/vnd.apple.mpegurl")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        mRemoteMediaPlayer
                .load(mApiClient, mediaInfo, this.playIfReady, pendingSeekTo)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
            @Override
            public void onResult(RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                Log.d(TAG, "onPlayerDelegatePlayWhenReadyCommited");
                delegateReady = true;
                controller.onPlayerDelegateReady(GoogleCastDelegate.this);
            }
        });
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        Log.d(TAG, "PlayIfReady: " + playIfReady);

        mRemoteMediaPlayer.requestStatus(mApiClient);
        controller.onPlayerDelegatePlayWhenReadyCommited(GoogleCastDelegate.this);

        if (mRemoteMediaPlayer != null && mediaInfo != null && this.playIfReady != playIfReady) {
            if (playIfReady && internalStatus == MediaStatus.PLAYER_STATE_PAUSED) {
                Log.d(TAG, "remoteMediaPlayer.play");
                mRemoteMediaPlayer.play(mApiClient);
            } else {
                Log.d(TAG, "remoteMediaPlayer.pause");
                mRemoteMediaPlayer.pause(mApiClient);
            }
        }
        this.playIfReady = playIfReady;
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        Log.d(TAG, "seekTo: " + positionInMillis);
        if (delegateReady && mRemoteMediaPlayer != null && mSessionId != null && mApiClient != null) {
            controller.onPlayerDelegateBuffering(this);
            Log.d(TAG, "remoteMediaPlayer.seek");
            mRemoteMediaPlayer.seek(mApiClient, positionInMillis).setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                @Override
                public void onResult(RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                    controller.onPlayerDelegateReady(GoogleCastDelegate.this);
                }
            });
        } else {
            pendingSeekTo = positionInMillis;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mRemoteMediaPlayer != null) {
            return internalStatus == MediaStatus.PLAYER_STATE_PLAYING;
        }
        return false;
    }

    @Override
    public void setMuted(boolean muted) {
        if (mRemoteMediaPlayer != null) {
            mRemoteMediaPlayer.setStreamMute(mApiClient, muted);
        }
    }

    @Override
    public long getCurrentPosition() {
        return mRemoteMediaPlayer != null ? mRemoteMediaPlayer.getApproximateStreamPosition() : 0;
    }

    @Override
    public long getDuration() {
        return mRemoteMediaPlayer != null ? mRemoteMediaPlayer.getStreamDuration() : 0;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public long getBufferPosition() {
        return 0;
    }

    @Override
    public int getVideoSourceHeight() {
        return 0;
    }

    @Override
    public void release() throws IllegalStateException {
        teardown();
        mRemoteMediaPlayer = null;
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public long getPlaylistStartTime() {
        return 0;
    }

    RemoteMediaPlayer.OnStatusUpdatedListener listener = new RemoteMediaPlayer.OnStatusUpdatedListener() {
        @Override
        public void onStatusUpdated() {
            if (mRemoteMediaPlayer != null) {
                MediaStatus status = mRemoteMediaPlayer.getMediaStatus();
                if (status != null) {
                    internalStatus = status.getPlayerState();
                }
            }
        }
    };

    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
                delegateReady = false;
            }
            mApiClient = null;
        }
        mSessionId = null;
    }

}
