package ch.srg.mediaplayer.internal.cast;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.internal.cast.exceptions.NoConnectionException;

/**
 * Created by npietri on 19.10.15.
 */
public class ChromeCastDelegate implements PlayerDelegate {

    private static final String TAG = "ChromeCastDelegate";
    private final OnPlayerDelegateListener controller;
    private final ChromeCastManager chromeCastManager;

    private MediaInfo mediaInfo;

    private boolean delegateReady;
    private boolean playIfReady;
    private long pendingSeekTo;
    private String title;
    private String subTitle;
    private String mediaThumbnailUrl;

    public ChromeCastDelegate(OnPlayerDelegateListener controller) {
        this.chromeCastManager = ChromeCastManager.getInstance();
        this.controller = controller;
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
        try {
            chromeCastManager.loadMedia(mediaInfo, true, pendingSeekTo);
        } catch (NoConnectionException e) {
            e.printStackTrace();
            throw new SRGMediaPlayerException(e);
        }
        Log.d(TAG, "onPlayerDelegatePlayWhenReadyCommited");
        delegateReady = true;
        controller.onPlayerDelegateReady(ChromeCastDelegate.this);
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        Log.d(TAG, "PlayIfReady: " + playIfReady);

        controller.onPlayerDelegatePlayWhenReadyCommited(ChromeCastDelegate.this);

        if (mediaInfo != null && this.playIfReady != playIfReady) {
            if (playIfReady) {
                if (chromeCastManager.getMediaStatus() == MediaStatus.PLAYER_STATE_PAUSED) {
                    Log.d(TAG, "remoteMediaPlayer.play");
                    try {
                        chromeCastManager.play();
                    } catch (NoConnectionException e) {
                        e.printStackTrace();
                        throw new IllegalStateException(e);
                    }
                }
            } else {
                Log.d(TAG, "remoteMediaPlayer.pause");
                try {
                    chromeCastManager.pause();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                    throw new IllegalStateException(e);
                }
            }
        }
        controller.onPlayerDelegatePlayWhenReadyCommited(ChromeCastDelegate.this);
        this.playIfReady = playIfReady;
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        Log.d(TAG, "seekTo: " + positionInMillis);
        if (delegateReady) {
            controller.onPlayerDelegateBuffering(this);
            Log.d(TAG, "remoteMediaPlayer.seek");
            try {
                chromeCastManager.seek(positionInMillis);
                controller.onPlayerDelegateReady(this);
            } catch (NoConnectionException e) {
                e.printStackTrace();
                throw new IllegalStateException(e);
            }
        } else {
            pendingSeekTo = positionInMillis;
        }
    }

    @Override
    public boolean isPlaying() {
        return chromeCastManager.getMediaStatus() == MediaStatus.PLAYER_STATE_PLAYING;
    }

    @Override
    public void setMuted(boolean muted) {
        try {
            chromeCastManager.setMuted(muted);
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getCurrentPosition() {
        try {
            return chromeCastManager.getMediaPosition();
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        try {
            return chromeCastManager.getMediaDuration();
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
        return 0;
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
