package ch.srg.mediaplayer;

import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import ch.srg.mediaplayer.utils.SRGMediaPlayerControllerQueueListener;

/**
 * Created by npietri on 12.06.15.
 * <p>
 * These tests work with a mock delegate and data provider, they do not do any playing or url decoding.
 * The goal is to test the player controller, its contract and robustness.
 */
@RunWith(AndroidJUnit4.class)
public class PlaybackTest extends MediaPlayerTest {
    private static final Uri VIDEO_ON_DEMAND_URI = Uri.parse("http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
    private static final Uri NON_STREAMED_VIDEO_URI = Uri.parse("http://www.sample-videos.com/video/mp4/720/big_buck_bunny_720p_1mb.mp4");
    private static final Uri VIDEO_LIVESTREAM_URI = Uri.parse("http://ndr_fs-lh.akamaihd.net/i/ndrfs_nds@119224/master.m3u8?dw=0");
    private static final Uri VIDEO_DVR_LIVESTREAM_URI = Uri.parse("http://ndr_fs-lh.akamaihd.net/i/ndrfs_nds@119224/master.m3u8");
    private static final Uri AUDIO_ON_DEMAND_URI = Uri.parse("https://rtsww-a-d.rts.ch/la-1ere/programmes/c-est-pas-trop-tot/2017/c-est-pas-trop-tot_20170628_full_c-est-pas-trop-tot_007d77e7-61fb-4aef-9491-5e6b07f7f931-128k.mp3");
    private static final Uri HTTP_403_URI = Uri.parse("http://httpbin.org/status/403");
    private static final Uri HTTP_404_URI = Uri.parse("http://httpbin.org/status/404");
    private static final Uri AUDIO_DVR_LIVESTREAM_URI = Uri.parse("http://lsaplus.swisstxt.ch/audio/drs1_96.stream/playlist.m3u8");

    private SRGMediaPlayerController controller;

    private SRGMediaPlayerControllerQueueListener queue;

    private SRGMediaPlayerException lastError;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        injectInstrumentation(InstrumentationRegistry.getInstrumentation());

        // Init variables
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                controller = new SRGMediaPlayerController(getInstrumentation().getContext(), "test");
                controller.setDebugMode(true);
            }
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
        controller.release();
    }

    @Test
    public void testIdleState() throws Exception {
        assertEquals(SRGMediaPlayerController.State.IDLE, controller.getState());
        assertFalse(controller.isReleased());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isLoading());
        assertFalse(controller.isLive());
        assertEquals(SRGMediaPlayerController.UNKNOWN_TIME, controller.getLiveTime());
        assertEquals(SRGMediaPlayerController.UNKNOWN_TIME, controller.getMediaDuration());
        assertFalse(controller.hasVideoTrack());
    }

    @Test
    public void testPreparingState() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testReadyState() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testPlayAudioOverHTTP() throws Exception {
        controller.play(NON_STREAMED_VIDEO_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testHTTP403HLS() throws Exception {
        controller.play(HTTP_403_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testHTTP403Progressive() throws Exception {
        controller.play(HTTP_403_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void TestHTTP404() throws Exception {
        controller.play(HTTP_404_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void TestHTTP404Progressive() throws Exception {
        controller.play(HTTP_404_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testNullUriError() throws Exception {
        controller.play(null, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testNullUriProgressiveError() throws Exception {
        controller.play(null, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testPlay() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testOnDemandVideoPlayback() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertFalse(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testVideoLivestreamPlayback() throws Exception {
        controller.play(VIDEO_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testDVRVideoLivestreamPlayback() throws Exception {
        controller.play(VIDEO_DVR_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testOnDemandVideoPlaythrough() throws Exception {
        // Start near the end of the stream
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        controller.seekTo(3566768);
        waitForState(SRGMediaPlayerController.State.READY);
        waitForEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testNonStreamedMediaPlaythrough() throws Exception {
        controller.play(NON_STREAMED_VIDEO_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testOnDemandAudioPlayback() throws Exception {
        controller.play(AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
    }

    @Test
    public void testDVRAudioLivestreamPlayback() throws Exception {
        controller.play(AUDIO_DVR_LIVESTREAM_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testOnDemandAudioPlaythrough() throws Exception {
        // Start near the end of the stream
        controller.play(AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        controller.seekTo((long) 3230783);
        waitForState(SRGMediaPlayerController.State.READY);
        waitForEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testPlayAtPosition() throws Exception {
        controller.play(AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        controller.seekTo((long) 30000);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 30);
    }

    @Test
    public void testPlayAfterStreamEnd() throws Exception {
        controller.play(AUDIO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
        controller.seekTo((long) 9900000);
        waitForState(SRGMediaPlayerController.State.READY);
        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testPause() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        controller.pause();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testSeek() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testMultipleSeeks() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        controller.seekTo(70 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 70);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testMultipleSeeksDuringBuffering() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        controller.seekTo(70 * 1000);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 70);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhilePreparing() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        assertFalse(controller.isPlaying());

        controller.seekTo(60 * 1000);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhileBuffering() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        assertFalse(controller.isPlaying());

        controller.seekTo(60 * 1000);
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhilePaused() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        controller.pause();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        // TODO: No BUFFERING?
        waitForState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testSeekWhileReleasing() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.isReleased());

        // Trigger a release. The controller is not immediately reaching the released state.
        controller.release();
        assertFalse(controller.isReleased());
        controller.seekTo(60 * 1000);

        waitForState(SRGMediaPlayerController.State.RELEASED);
        assertTrue(controller.isReleased());
    }

    @Test
    public void testSeekWhileReleased() throws Exception {

    }

    @Test
    public void testRelease() throws Exception {
        controller.play(VIDEO_ON_DEMAND_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.isReleased());

        // Trigger a release. The controller is not immediately reaching the released state.
        controller.release();
        assertFalse(controller.isReleased());

        waitForState(SRGMediaPlayerController.State.RELEASED);
        assertTrue(controller.isReleased());
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

    private interface EventCondition {
        boolean check(SRGMediaPlayerController.Event event);
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
