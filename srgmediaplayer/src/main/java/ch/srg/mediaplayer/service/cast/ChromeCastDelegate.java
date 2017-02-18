package ch.srg.mediaplayer.service.cast;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.util.Collections;
import java.util.List;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaMetadata;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.mediaplayer.SubtitleTrack;


/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public class ChromeCastDelegate implements PlayerDelegate {

    private static final String TAG = "ChromeCastDelegate";
    private final OnPlayerDelegateListener controller;

    private boolean delegateReady;
    private boolean playIfReady;
    private long pendingSeekTo;
    private String contentType;
    private boolean live;

    private Uri videoUri;
    @Nullable
    private RemoteMediaClient remoteMediaClient;
    private SRGMediaMetadata srgMediaMetadata;
    private MediaInfo mediaInfo;

    public ChromeCastDelegate(OnPlayerDelegateListener controller) {
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
        Log.d(TAG, "Prepare: " + videoUri + " type: " + contentType + " title: " + srgMediaMetadata == null ? "" : srgMediaMetadata.getTitle());
        this.videoUri = videoUri;
        controller.onPlayerDelegatePreparing(this);

        remoteMediaClient = ChromeCastManager.getRemoteMediaClient();
        mediaInfo = buildMediaInfo();
        if (remoteMediaClient != null) {
            remoteMediaClient.load(mediaInfo, playIfReady, pendingSeekTo);
        }

        Log.d(TAG, "onPlayerDelegatePlayWhenReadyCommited");
        delegateReady = true;
        controller.onPlayerDelegateReady(ChromeCastDelegate.this);
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        Log.d(TAG, "PlayIfReady: " + playIfReady);

        if (mediaInfo != null && this.playIfReady != playIfReady) {
            if (playIfReady) {
                if (remoteMediaClient != null && remoteMediaClient.getMediaStatus().getPlayerState() == MediaStatus.PLAYER_STATE_PAUSED) {
                    Log.d(TAG, "remoteMediaPlayer.play");
                    remoteMediaClient.play();
                }
            } else {
                Log.d(TAG, "remoteMediaPlayer.pause");
                if (remoteMediaClient != null) {
                    remoteMediaClient.pause();
                }
            }
        }

        this.playIfReady = playIfReady;
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {
        Log.d(TAG, "seekTo: " + positionInMillis);
        if (delegateReady) {
            if (remoteMediaClient != null) {
                if (isActive() && remoteMediaClient.isPlaying()) {
                    controller.onPlayerDelegateBuffering(this);
                    Log.d(TAG, "remoteMediaPlayer.seek");
                    remoteMediaClient.seek(positionInMillis);
                    controller.onPlayerDelegateReady(this);
                } else {
                    remoteMediaClient.load(mediaInfo, playIfReady, positionInMillis);
                }
            }
        } else {
            pendingSeekTo = positionInMillis;
        }
    }

    @Override
    public boolean isPlaying() {
        if (remoteMediaClient != null) {
            return remoteMediaClient.isPlaying();
        }
        return false;
    }

    @Override
    public void setMuted(boolean muted) {
        if (remoteMediaClient != null) {
            remoteMediaClient.setStreamMute(muted);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (remoteMediaClient != null) {
            return remoteMediaClient.getApproximateStreamPosition();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        return srgMediaMetadata == null ? 0 : srgMediaMetadata.getDuration();
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
    public boolean hasVideoTrack() {
        return false;
    }

    @Override
    public void release() throws IllegalStateException {
        remoteMediaClient.stop();
    }

    private boolean isActive() {
        MediaInfo remoteMediaInformation = null;
        if (remoteMediaClient != null) {
            remoteMediaInformation = remoteMediaClient.getMediaInfo();
        }
        return remoteMediaInformation != null && mediaInfo != null
                && TextUtils.equals(remoteMediaInformation.getContentId(), mediaInfo.getContentId());
    }

    @Override
    public boolean isLive() {
        return live;
    }

    @Override
    public long getPlaylistStartTime() {
        return 0;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public SRGMediaPlayerController.Event.ScreenType getScreenType() {
        return SRGMediaPlayerController.Event.ScreenType.CHROMECAST;
    }

    @Override
    public void setQualityOverride(Long quality) {
    }

    @Override
    public void setQualityDefault(Long quality) {

    }

    @Override
    public Long getBandwidthEstimate() {
        return null;
    }

    public long getPlaylistReferenceTime() {
        return System.currentTimeMillis();
    }

    @Override
    public List<SubtitleTrack> getSubtitleTrackList() {
        return Collections.emptyList();
    }

    @Override
    public void setSubtitleTrack(SubtitleTrack track) {
    }

    @Nullable
    @Override
    public SubtitleTrack getSubtitleTrack() {
        return null;
    }

    public void setSRGMediaMetadata(SRGMediaMetadata srgMediaMetadata) {
        this.srgMediaMetadata = srgMediaMetadata;
    }

    private MediaInfo buildMediaInfo() {
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

        movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, srgMediaMetadata.getDescription());
        movieMetadata.putString(MediaMetadata.KEY_TITLE, srgMediaMetadata.getTitle());
        movieMetadata.addImage(new WebImage(Uri.parse(srgMediaMetadata.getImageUrl())));

        return new MediaInfo.Builder(videoUri.toString())
                .setStreamType(live ? MediaInfo.STREAM_TYPE_LIVE : MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(movieMetadata)
                .setStreamDuration(srgMediaMetadata.getDuration())
                .build();
    }
}
