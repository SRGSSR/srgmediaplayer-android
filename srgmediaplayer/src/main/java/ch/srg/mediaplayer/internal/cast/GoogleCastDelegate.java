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

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

/**
 * Created by npietri on 19.10.15.
 */
public class GoogleCastDelegate implements PlayerDelegate {

    private static final String TAG = "GoogleCastDelegate";
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private GoogleApiClient mApiClient;
    private boolean mApplicationStarted;
    private String mSessionId;
    private boolean isPlaying;

    private MediaInfo mediaInfo;

    public GoogleCastDelegate(String mSessionId, GoogleApiClient mApiClient) {
        this.mSessionId = mSessionId;
        this.mApiClient = mApiClient;
        mRemoteMediaPlayer = new RemoteMediaPlayer();
    }

    @Override
    public boolean canRenderInView(View view) {
        return false;
    }

    @Override
    public View createRenderingView(Context parentContext) {
        return null;
    }

    @Override
    public void bindRenderingViewInUiThread(SRGMediaPlayerView mediaPlayerView) throws SRGMediaPlayerException {

    }

    @Override
    public void unbindRenderingView() {

    }

    @Override
    public void prepare(Uri videoUri) throws SRGMediaPlayerException {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, "SRGPLAY");
        mediaInfo = new MediaInfo.Builder(String.valueOf(videoUri))
                .setContentType("application/vnd.apple.mpegurl")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        if (mRemoteMediaPlayer != null) {
            if (playIfReady) {
                try {
                    mRemoteMediaPlayer.load(mApiClient, mediaInfo, true);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Problem occurred with media during loading", e);
                } catch (Exception e) {
                    Log.e(TAG, "Problem opening media during loading", e);
                }
            } else if(isPlaying()){
                mRemoteMediaPlayer.pause(mApiClient);
            } else {
                mRemoteMediaPlayer.play(mApiClient);
            }
        }
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        if (mRemoteMediaPlayer != null) {
            if (!isPlaying()) {
                mRemoteMediaPlayer.seek(mApiClient, positionInMillis);
            } else {
                mRemoteMediaPlayer.load(mApiClient, mediaInfo, true, positionInMillis);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        return mRemoteMediaPlayer != null && mRemoteMediaPlayer.getMediaStatus().getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
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

    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSessionId = null;
    }

}
