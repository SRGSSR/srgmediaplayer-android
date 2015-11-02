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
import com.google.android.gms.common.images.WebImage;

import java.io.IOException;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.service.cast.ChromeCastManager;

/**
 * Created by npietri on 19.10.15.
 */
public class GoogleCastDelegate implements PlayerDelegate, GoogleApiClient.ConnectionCallbacks, RemoteMediaPlayer.OnStatusUpdatedListener {

    private static final String TAG = "GoogleCastDelegate";
    private final OnPlayerDelegateListener controller;
    private final ChromeCastManager chromeCastManager;

    private MediaInfo mediaInfo;

    private int internalStatus;
    private boolean delegateReady;
    private boolean playIfReady;
    private long pendingSeekTo;
    private String title;
    private String subTitle;
    private String mediaThumbnailUrl;

    private boolean connected;

    public GoogleCastDelegate(ChromeCastManager chromeCastManager, OnPlayerDelegateListener srgMediaPlayer) {
        this.chromeCastManager = chromeCastManager;
        apiClient.registerConnectionCallbacks(this);
        if (!apiClient.isConnected()) {
            throw new SRGMediaPlayerException("api client not connected");
        }
        this.controller = controller;
        this.remoteMediaPlayer = remoteMediaPlayer;
        try {
            Cast.CastApi.setMessageReceivedCallbacks(apiClient,
                    this.remoteMediaPlayer.getNamespace(), this.remoteMediaPlayer);
            this.remoteMediaPlayer.setOnStatusUpdatedListener(this);
            connected = true;
        } catch (IOException e) {
            throw new SRGMediaPlayerException("Exception while creating media channel", e);
        }

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
        remoteMediaPlayer.requestStatus(apiClient);
        Log.d(TAG, "Prepare: " + videoUri);
        controller.onPlayerDelegatePreparing(this);

        String metadataTitle;
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);

        if (title != null) {
            metadataTitle = title;
        } else {
            Context context = controller.getContext();
            metadataTitle = context.getString(context.getApplicationInfo().labelRes);
        }
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, metadataTitle);
        if (subTitle != null) {
            mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, subTitle);
        }

        if (mediaThumbnailUrl != null) {
            mediaMetadata.addImage(new WebImage(Uri.parse(mediaThumbnailUrl)));
        }

        mediaInfo = new MediaInfo.Builder(String.valueOf(videoUri))
                .setContentType("application/x-mpegurl")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        remoteMediaPlayer
                .load(apiClient, mediaInfo, this.playIfReady, pendingSeekTo)
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

        remoteMediaPlayer.requestStatus(apiClient);
        controller.onPlayerDelegatePlayWhenReadyCommited(GoogleCastDelegate.this);

        if (connected && mediaInfo != null && this.playIfReady != playIfReady) {
            if (playIfReady) {
                if (internalStatus == MediaStatus.PLAYER_STATE_PAUSED) {
                    Log.d(TAG, "remoteMediaPlayer.play");
                    remoteMediaPlayer.play(apiClient);
                }
            } else {
                Log.d(TAG, "remoteMediaPlayer.pause");
                remoteMediaPlayer.pause(apiClient);
            }
        }
        controller.onPlayerDelegatePlayWhenReadyCommited(GoogleCastDelegate.this);
        this.playIfReady = playIfReady;
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        Log.d(TAG, "seekTo: " + positionInMillis);
        if (connected && delegateReady && sessionId != null && apiClient != null) {
            controller.onPlayerDelegateBuffering(this);
            Log.d(TAG, "remoteMediaPlayer.seek");
            remoteMediaPlayer.seek(apiClient, positionInMillis).setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
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
        return connected && internalStatus == MediaStatus.PLAYER_STATE_PLAYING;
    }

    @Override
    public void setMuted(boolean muted) {
        if (connected) {
            remoteMediaPlayer.setStreamMute(apiClient, muted);
        }
    }

    @Override
    public long getCurrentPosition() {
        return connected ? remoteMediaPlayer.getApproximateStreamPosition() : 0;
    }

    @Override
    public long getDuration() {
        return connected ? remoteMediaPlayer.getStreamDuration() : 0;
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

    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public long getPlaylistStartTime() {
        return 0;
    }

    @Override
    public void onStatusUpdated() {
        MediaStatus status = remoteMediaPlayer.getMediaStatus();
        if (status != null) {
            internalStatus = status.getPlayerState();
        }
    }

    public void setMediaTitle(String title) {
        this.title = title;
    }

    public void setMediaSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public void setMediaThumbnailUrl(String mediaThumbnailUrl) {
        this.mediaThumbnailUrl = mediaThumbnailUrl;
    }

    @Override
    public boolean isRemote() {
        return true;
    }
}
