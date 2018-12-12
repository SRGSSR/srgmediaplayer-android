package ch.srg.mediaplayer;

import android.content.Context;
import android.net.Uri;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ch.srg.mediaplayer.segment.model.Segment;
import ch.srg.mediaplayer.utils.SRGMediaPlayerControllerQueueListener;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by npietri on 12.06.15.
 * <p>
 * These tests work with a mock delegate and data provider, they do not do any playing or url decoding.
 * The goal is to test the player controller, its contract and robustness.
 */
@RunWith(AndroidJUnit4.class)
public class PlaybackTest extends MediaPlayerTest {
    private static final Uri VIDEO_ON_DEMAND_URI = Uri.parse("http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
    private static final Uri NON_STREAMED_VIDEO_URI = Uri.parse("http://amssamples.streaming.mediaservices.windows.net/2e91931e-0d29-482b-a42b-9aadc93eb825/AzurePromo.mp4");
    private static final Uri VIDEO_LIVESTREAM_URI = Uri.parse("http://tagesschau-lh.akamaihd.net/i/tagesschau_1@119231/master.m3u8?dw=0");
    private static final Uri VIDEO_DVR_LIVESTREAM_URI = Uri.parse("http://tagesschau-lh.akamaihd.net/i/tagesschau_1@119231/master.m3u8");
    private static final Uri AUDIO_ON_DEMAND_URI = Uri.parse("https://rtsww-a-d.rts.ch/la-1ere/programmes/c-est-pas-trop-tot/2017/c-est-pas-trop-tot_20170628_full_c-est-pas-trop-tot_007d77e7-61fb-4aef-9491-5e6b07f7f931-128k.mp3");
    private static final Uri HTTP_403_URI = Uri.parse("http://httpbin.org/status/403");
    private static final Uri HTTP_404_URI = Uri.parse("http://httpbin.org/status/404");
    private static final Uri AUDIO_DVR_LIVESTREAM_URI = Uri.parse("http://lsaplus.swisstxt.ch/audio/drs1_96.stream/playlist.m3u8");

    private SRGMediaPlayerController controller;

    private SRGMediaPlayerControllerQueueListener queue;

    private SRGMediaPlayerException lastError;

    @Before
    public void setUp() {
        // Init variables
        getInstrumentation().runOnMainSync(() -> {
            controller = new SRGMediaPlayerController(getInstrumentation().getContext(), "test");
            controller.setDebugMode(true);
        });
        controller.setDebugMode(true);

        lastError = null;
        controller.registerEventListener(new SRGMediaPlayerController.Listener() {
            @Override
            public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
                switch (event.type) {
                    case FATAL_ERROR:
                    case TRANSIENT_ERROR:
                        lastError = event.exception;
                        break;
                }
            }
        });

        queue = new SRGMediaPlayerControllerQueueListener();
        controller.registerEventListener(queue);

        assertEquals(SRGMediaPlayerController.State.IDLE, controller.getState());
    }

    @After
    public void release() {
        controller.unregisterEventListener(queue);
        queue.clear();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                controller.release();
            }
        });
    }

    @Test
    public void testIdleState() throws Exception {
        assertEquals(SRGMediaPlayerController.State.IDLE, controller.getState());
        assertFalse(controller.isReleased());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isLoading());
        assertFalse(controller.isLive());
        assertEquals(SRGMediaPlayerController.UNKNOWN_TIME, controller.getLiveTime());
        assertEquals(SRGMediaPlayerController.TIME_LIVE, controller.getMediaDuration());
        assertFalse(controller.hasVideoTrack());
    }

    @Test
    public void testPreparingState() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        assertEquals(SRGMediaPlayerController.State.BUFFERING, controller.getState());
        assertFalse(controller.isPlaying());
    }

    private static void playMainThread(SRGMediaPlayerController controller, Uri uri, int streamType, final List<Segment> segmentList, final Segment segment) {
        getInstrumentation().runOnMainSync(() -> {
            controller.prepare(uri, 0L, streamType, segmentList, segment);
            controller.start();
        });
    }

    private static void playMainThread(SRGMediaPlayerController controller, Uri uri, int streamType) {
        playMainThread(controller, uri, streamType, null, null);
    }

    private void pauseMainThread() {
        getInstrumentation().runOnMainSync(() -> {
            controller.pause();
        });
    }


    private void seekToMainThread(int positionMs) {
        getInstrumentation().runOnMainSync(() -> {
            controller.seekTo(positionMs);
        });
    }


    @Test
    public void testReadyState() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testPlayAudioOverHTTP() throws Exception {
        playMainThread(controller, NON_STREAMED_VIDEO_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testHTTP403HLS() throws Exception {
        playMainThread(controller, HTTP_403_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForEvent(SRGMediaPlayerController.Event.Type.FATAL_ERROR);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
//        Assert.assertNotNull(lastError);
    }

    @Test
    public void testHTTP403Progressive() throws Exception {
        playMainThread(controller, HTTP_403_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void TestHTTP404() throws Exception {
        playMainThread(controller, HTTP_404_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void TestHTTP404Progressive() throws Exception {
        playMainThread(controller, HTTP_404_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testPlay() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testOnDemandVideoPlayback() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertFalse(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.TIME_LIVE != controller.getLiveTime());
    }

    @Test
    public void testVideoLivestreamPlayback() throws Exception {
        playMainThread(controller, VIDEO_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.TIME_LIVE != controller.getLiveTime());
    }

    @Test
    public void testDVRVideoLivestreamPlayback() throws Exception {
        playMainThread(controller, VIDEO_DVR_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.TIME_LIVE != controller.getLiveTime());
    }

    @Test
    public void testOnDemandVideoPlaythrough() throws Exception {
        // Start near the end of the stream
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        seekToMainThread(3566768);
        waitForEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testNonStreamedMediaPlaythrough() throws Exception {
        playMainThread(controller, NON_STREAMED_VIDEO_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testOnDemandAudioPlayback() throws Exception {
        playMainThread(controller, AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
    }

    @Test
    public void testDVRAudioLivestreamPlayback() throws Exception {
        playMainThread(controller, AUDIO_DVR_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testOnDemandAudioPlaythrough() throws Exception {
        // Start near the end of the stream
        playMainThread(controller, AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        getInstrumentation().runOnMainSync(() -> {
            controller.seekTo((long) 3230783);
        });
        waitForEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testPlayAndSeekToPosition() throws Exception {
        playMainThread(controller, AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue("is playing", controller.isPlaying() || controller.isLoading());
        getInstrumentation().runOnMainSync(() -> {
            controller.seekTo((long) 30000);
        });
        assertTrue("still playing after seek", controller.isPlaying() || controller.isLoading());
        waitForEvent(SRGMediaPlayerController.Event.Type.DID_SEEK);
        assertEquals(30, controller.getMediaPosition() / 1000);
    }

    @Test
    public void testPlayAtStartingPosition() throws Exception {
        Long position = 3000L;
        getInstrumentation().runOnMainSync(() -> {
            controller.play(AUDIO_ON_DEMAND_URI, position, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        });
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(3, controller.getMediaPosition() / 1000);
    }

    @Test
    public void testPlayAtStartingPositionNull() throws Exception {
        Long position = null;
        getInstrumentation().runOnMainSync(() -> {
            controller.play(AUDIO_ON_DEMAND_URI, position, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        });
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(0, controller.getMediaPosition() / 1000);
    }

    @Test
    public void testPlayAtStartingPositionWitSegment() throws Exception {
        Segment segment0 = new Segment("segmentId0", "Segment0", null, null, null,
                3000L, 10000L, 7000L, true, false, false);
        Segment segment1 = new Segment("segmentId1", "Segment1", null, null, null,
                12000L, 15000L, 3000L, true, false, false);
        List<Segment> segmentList = Arrays.asList(segment0, segment1);

        playMainThread(controller, AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE, segmentList, segment1);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(12, controller.getMediaPosition() / 1000);
    }

    @Test
    public void testPlayAfterStreamEnd() throws Exception {
        playMainThread(controller, AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        seekToMainThread(9900000);
        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testPause() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        pauseMainThread();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testSeek() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(0, controller.getMediaPosition() / 1000);

        seekToMainThread(60 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(60, controller.getMediaPosition() / 1000);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testMultipleSeeks() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(0, controller.getMediaPosition() / 1000);
        seekToMainThread(60 * 1000);
        seekToMainThread(70 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(70, controller.getMediaPosition() / 1000);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testMultipleSeeksDuringBuffering() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(0, controller.getMediaPosition() / 1000);

        seekToMainThread(60 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        seekToMainThread(70 * 1000);
        waitForEvent(SRGMediaPlayerController.Event.Type.DID_SEEK);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(70, controller.getMediaPosition() / 1000);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhilePreparing() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        assertFalse(controller.isPlaying());

        seekToMainThread(60 * 1000);
        assertTrue(controller.isLoading() || controller.isPlaying());
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(60, controller.getMediaPosition() / 1000);
        while (!controller.isPlaying()) {
            Thread.sleep(100);
        }
    }

    @Test
    public void testSeekWhileBuffering() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        assertFalse(controller.isPlaying());

        seekToMainThread(60 * 1000);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(60, controller.getMediaPosition() / 1000);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhilePaused() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        pauseMainThread();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
        assertEquals(0, controller.getMediaPosition() / 1000);

        seekToMainThread(60 * 1000);
        // TODO: No BUFFERING?
        waitForState(SRGMediaPlayerController.State.READY);
        waitForEvent(SRGMediaPlayerController.Event.Type.DID_SEEK);
        assertEquals(60, controller.getMediaPosition() / 1000);
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testPauseStartPositionKept() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        seekToMainThread(60 * 1000);
        waitForEvent(SRGMediaPlayerController.Event.Type.DID_SEEK);
        pauseMainThread();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
        assertEquals(60, controller.getMediaPosition() / 1000);

        getInstrumentation().runOnMainSync(() -> {
            controller.start();
        });
        waitForState(SRGMediaPlayerController.State.READY); // pause or play, the player is ready
        assertTrue(controller.isPlaying());

        assertEquals(60, controller.getMediaPosition() / 1000);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhileReleasing() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.isReleased());

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Trigger a release. The controller immediately set to released but the exoplayer release operation is asynchronous
                controller.release();
                assertTrue(controller.isReleased());
                controller.seekTo(60 * 1000);

                try {
                    waitForState(SRGMediaPlayerController.State.RELEASED);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                assertTrue(controller.isReleased());
            }
        });
    }

    @Test
    public void testSeekWhileReleased() throws Exception {

    }

    @Test
    public void testRelease() throws Exception {
        playMainThread(controller, VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.isReleased());

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                controller.release();
                assertTrue(controller.isReleased());
                try {
                    waitForEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
                    waitForState(SRGMediaPlayerController.State.RELEASED);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                assertTrue(controller.isReleased());
            }
        });
    }

    @Test
    public void playReleaseRobustness() {
        final Context context = getInstrumentation().getContext();
        int testCount = 100;

        for (int i = 0; i < testCount; i++) {
            Log.v("MediaCtrlerTest", "create/play/release " + i + " / " + testCount);
            Runnable runnable = new CreatePlayRelease(context);
            getInstrumentation().runOnMainSync(runnable);
        }
    }

    protected void waitForState(SRGMediaPlayerController.State state) throws Exception {
        if (controller.getState() != state) {
            super.waitForState(state);
        }
    }

    private static class CreatePlayRelease implements Runnable {
        SRGMediaPlayerController controller;
        private Context context;
        private Random r = new Random();

        public CreatePlayRelease(Context context) {
            this.context = context;
        }

        public void run() {
            setup();

            try {
                test();
            } catch (SRGMediaPlayerException e) {
                Assert.fail("SRGMediaPlayerException" + e.getMessage());
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }

        private void setup() {
            controller = new SRGMediaPlayerController(context, "test");
            controller.setDebugMode(true);
        }

        private void test() throws SRGMediaPlayerException, InterruptedException {
            controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
            potentialSleep();
            controller.release();
            potentialSleep();
            controller.pause();
        }

        private void potentialSleep() throws InterruptedException {
            if (r.nextBoolean()) {
                Thread.sleep(100);
            }
        }
    }
}
