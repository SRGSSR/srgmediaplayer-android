package ch.srg.mediaplayer.utils;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;

/**
 * Created by npietri on 12.06.15.
 */
public class MockDelegate implements PlayerDelegate {

    public enum State {
        IDLE,
        PREPARING,
        READY,
        PLAYING,
        STOPPED,
        ERROR,
        RELEASED
    }

    private State state;

    private OnPlayerDelegateListener controller;

    private String videoSourceUrl;

    public MockDelegate(OnPlayerDelegateListener controller) {
        this.controller = controller;
        state = State.IDLE;
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
        try {
            String videoSourceUrl = videoUri.toString();
            if (videoSourceUrl.equalsIgnoreCase(this.videoSourceUrl)) {
                return;
            }
            if (TextUtils.isEmpty(videoSourceUrl)) {
                state = State.ERROR;
                throw new SRGMediaPlayerException("VideoSource is empty");
            }
            state = State.PREPARING;
            controller.onPlayerDelegatePreparing(this);
            this.videoSourceUrl = videoSourceUrl;
            state = State.READY;
            controller.onPlayerDelegateReady(this);
        } catch (Exception e) {
            release();
            throw new SRGMediaPlayerException(e);
        }
    }

    @Override
    public void playIfReady(boolean playIfReady) throws IllegalStateException {
        Log.v(SRGMediaPlayerController.TAG, this + " playIfReady: " + playIfReady);
        if (state == State.READY) {
            if (!isPlaying() && playIfReady) {
                state = State.PLAYING;
            }
        }
        if (!playIfReady && (state == State.PREPARING || state == State.READY || state == State.PLAYING)) {
            state = State.STOPPED;
        }
        controller.onPlayerDelegatePlayWhenReadyCommited(this);
    }

    @Override
    public void seekTo(long positionInMillis) throws IllegalStateException {

    }

    @Override
    public boolean isPlaying() {
        return State.PLAYING == state;
    }

    @Override
    public void setMuted(boolean muted) {

    }

    @Override
    public long getCurrentPosition() {
        return 0;
    }

    @Override
    public long getDuration() {
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
        state = State.RELEASED;
    }

    public String getVideoSourceUrl() {
        return videoSourceUrl;
    }

    public void setVideoSourceUrl(String videoSourceUrl) {
        this.videoSourceUrl = videoSourceUrl;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void notifyError(SRGMediaPlayerException e) {
        controller.onPlayerDelegateError(this, e);
    }

}
