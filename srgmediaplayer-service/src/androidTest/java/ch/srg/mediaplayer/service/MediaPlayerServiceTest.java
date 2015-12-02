package ch.srg.mediaplayer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import junit.framework.Assert;

import java.util.concurrent.TimeUnit;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.internal.session.MediaSessionManager;
import ch.srg.mediaplayer.utils.ThreadServiceTestBase;

/**
 * Created by seb on 31/10/14.
 */
public class MediaPlayerServiceTest extends ThreadServiceTestBase<MediaPlayerService> {

    private static final long TIMEOUT = 10000;
    private static final int SEEK_POSITION_1 = 3000;
    private MediaStatusReceiver serviceStatus;
    private MediaPlayerService mediaPlayerService;

    public MediaPlayerServiceTest() {
        super(MediaPlayerService.class);
        MediaPlayerService.setDataProvider(new ch.srg.mediaplayer.extras.dataproviders.DirectMappingDataProvider(SRGMediaPlayerDataProvider.TYPE_AUDIO));
    }

    public void testHlsPlayStopWithDelay() {
        startHlsPlay();

        waitForState(SRGMediaPlayerController.State.READY);

        assertPlayerPositionChanging(true);

        delay(3000);

        sendAction(MediaPlayerService.ACTION_STOP);

        waitForState(SRGMediaPlayerController.State.RELEASED);

        assertPlayerPositionChanging(false);
    }

    public void testHlsPlayStopWithoutDelay() {
        startHlsPlay();

        waitForState(SRGMediaPlayerController.State.READY);

        assertPlayerPositionChanging(true);

        sendAction(MediaPlayerService.ACTION_STOP);

        waitForState(SRGMediaPlayerController.State.RELEASED);

        assertPlayerPositionChanging(false);
    }

    public void testHlsSeekWithAction() {
        startHlsPlay();

        waitForState(SRGMediaPlayerController.State.READY);

        sendAction(MediaPlayerService.ACTION_SEEK, MediaPlayerService.ARG_POSITION, SEEK_POSITION_1);

        waitForPlayerTimeMoreThan(SEEK_POSITION_1);

        sendAction(MediaPlayerService.ACTION_SEEK, MediaPlayerService.ARG_POSITION, 0);

        waitForPlayerTimeLessThan(SEEK_POSITION_1);
    }

    public void testHlsSeekMethodCall() {
        startHlsPlay();

        waitForState(SRGMediaPlayerController.State.READY);

        mediaPlayerService.seekTo(SEEK_POSITION_1);

        waitForPlayerTimeMoreThan(SEEK_POSITION_1);

        mediaPlayerService.seekTo(0);

        waitForPlayerTimeLessThan(SEEK_POSITION_1);
    }

    public void testLivePlayStopWithDelay() {
        if (true)
            return;
        startSrf1Live();

        waitForState(SRGMediaPlayerController.State.READY);

        delay(4000);

        sendAction(MediaPlayerService.ACTION_STOP);

        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    public void testLivePlayStopWithoutDelay() {
        if (true)
            return;
        startSrf1Live();

        waitForState(SRGMediaPlayerController.State.READY);

        sendAction(MediaPlayerService.ACTION_STOP);

        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    public void testPlay404Stream() {
        start404Stream();
        waitForState(SRGMediaPlayerController.State.IDLE);
    }

    // We ignore this test as one device fails
    public void ignoredTestPlayUnknownHostStream() {
        startUnknownHostStream();
        waitForState(SRGMediaPlayerController.State.IDLE);
    }

    public static void delay(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void waitForState(SRGMediaPlayerController.State statePlaying) {
        serviceStatus.waitForState(statePlaying, TIMEOUT);
    }

    public void startSrf1Live() {
        playStream("http://stream.srg-ssr.ch/m/drs1/aacp_96");
    }

    public void startHlsPlay() {
        playStream("http://srfaodorigin-vh.akamaihd.net/i/world/nachrichten4/2aba5a10-bc0e-4679-8949-28639c2ef269.,q10,q20,.mp4.csmil/index_1_a.m3u8?null=");
    }

    public void startUnknownHostStream() {
        playStream("http://unknownHost654654654654654.com/zestream");
    }

    public void start404Stream() {
        playStream("http://streaming.swisstxt.ch/unknownStream");
    }

    public void playStream(String mediaIdentifier) {
        Intent audioPlayerIntent = new Intent(getContext(), MediaPlayerService.class);
        audioPlayerIntent.setAction(MediaPlayerService.ACTION_PLAY);
        audioPlayerIntent.putExtra(MediaPlayerService.ARG_MEDIA_IDENTIFIER, mediaIdentifier);
        mediaPlayerService = startService(false, null, audioPlayerIntent);
    }

    public void sendAction(String action) {
        sendAction(action, null, 0);
    }

    private void sendAction(String action, String key, long value) {
        Intent audioPlayerIntent = new Intent(getContext(), MediaPlayerService.class);
        audioPlayerIntent.setAction(action);
        if (key != null) {
            audioPlayerIntent.putExtra(key, value);
        }
        startService(audioPlayerIntent);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        serviceStatus = new MediaStatusReceiver();
        Context context = getContext();

        serviceStatus.register(context);
        MediaSessionManager.initialize(context);
    }

    @Override
    protected void tearDown() throws Exception {
        serviceStatus.unregister(getContext());
        super.tearDown();
    }

    private class MediaStatusReceiver extends BroadcastReceiver {
        private SRGMediaPlayerController.State state;
        private long position;
        private long duration;

        public void register(Context context) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this, new IntentFilter(MediaPlayerService.ACTION_BROADCAST_STATUS));
        }

        public void unregister(Context context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        }

        public void requestBroadcast() {
            sendAction(MediaPlayerService.ACTION_BROADCAST_STATUS);
        }

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra(MediaPlayerService.ACTION_BROADCAST_STATUS_BUNDLE);
            if (bundle != null) {
                state = SRGMediaPlayerController.State.valueOf(bundle.getString(MediaPlayerService.KEY_STATE));
                position = bundle.getLong(MediaPlayerService.KEY_POSITION);
                duration = bundle.getLong(MediaPlayerService.KEY_DURATION);
                Log.d("APST", "current State: " + state);
                notifyAll();
            } else {
                throw new IllegalArgumentException("Player broadcast without status");
            }
        }

        public void waitForState(SRGMediaPlayerController.State state, long timeout) {
            serviceStatus.requestBroadcast();

            Log.d("APST", "waitForState: " + state);
            long remainingTime = timeout;
            while (state != this.state && remainingTime > 0) {
                long waitStartTime = System.nanoTime();
                Log.d("APST", "waitForState: " + state + " current: " + this.state + " remainingTime: " + remainingTime);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                remainingTime -= TimeUnit.MILLISECONDS.convert(System.nanoTime() - waitStartTime, TimeUnit.NANOSECONDS);
            }
            Log.d("APST", "waitForState: " + state + "  remainingTime: " + remainingTime);

            if (remainingTime <= 0) {
                Assert.fail("Timeout waiting for player state=" + state + " current state: " + this.state);
            }
        }
    }

    public void assertPlayerPositionChanging(boolean expectChange) {
        Log.d("APST", "assertPlayerPositionChanging " + expectChange);
        long startPosition = serviceStatus.position;
        if (expectChange) {
            int timeout = 100;
            while (startPosition == serviceStatus.position && --timeout > 0) {
                delay(100);
            }
            if (timeout <= 0) {
                Assert.fail("Player position change timeout");
            }
        } else {
            int timeout = 30;
            while (--timeout > 0) {
                Assert.assertFalse("Player position change",
                        startPosition != serviceStatus.position);
                delay(100);
            }
        }
    }


    private void waitForPlayerTimeMoreThan(final long time) {
        Log.d("APST", "waitForPlayerTimeMoreThan " + time);
        int timeout = 100;
        while (serviceStatus.position < time && --timeout > 0) {
            delay(100);
        }
        if (timeout <= 0) {
            Assert.fail("Player position more than " + time + " timeout");
        }
    }

    private void waitForPlayerTimeLessThan(final long time) {
        Log.d("APST", "waitForPlayerTimeLessThan " + time);
        int timeout = 100;
        while (serviceStatus.position > time && --timeout > 0) {
            delay(100);
            Log.d("APST", "time: " + serviceStatus.position + " timeout: " + timeout);
        }
        if (timeout <= 0) {
            Assert.fail("Player position more than " + time + " timeout");
        }
    }
}
