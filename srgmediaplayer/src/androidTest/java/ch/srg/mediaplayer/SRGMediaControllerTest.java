package ch.srg.mediaplayer;

import android.app.Instrumentation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.tests.ConditionWatcher;
import ch.srg.mediaplayer.tests.EventInstruction;
import ch.srg.mediaplayer.utils.MockDataProvider;
import ch.srg.mediaplayer.utils.SRGMediaPlayerControllerQueueListener;

/**
 * Created by npietri on 12.06.15.
 * <p>
 * These tests work with a mock delegate and data provider, they do not do any playing or url decoding.
 * The goal is to test the player controller, its contract and robustness.
 */
@RunWith(AndroidJUnit4.class)
public class SRGMediaControllerTest extends InstrumentationTestCase {

    public static final int TIMEOUT_STATE_CHANGE = 10000;
    public static final String VIDEO_ON_DEMAND_IDENTIFIER = "SPECIMEN";
    public static final String NON_STREAMED_VIDEO_IDENTIFIER = "BIG-BUCK-NON-STREAMED";
    public static final String VIDEO_LIVESTREAM_IDENTIFIER = "NDR";
    public static final String VIDEO_DVR_LIVESTREAM_IDENTIFIER = "NDR-DVR";
    public static final String AUDIO_ON_DEMAND_IDENTIFIER = "C-EST-PAS-TROP-TOT";
    public static final String HTTP_403_IDENTIFIER = "HTTP_403";
    public static final String HTTP_404_IDENTIFIER = "HTTP_404";
    public static final String AUDIO_DVR_LIVESTREAM_IDENTIFIER = "DRS1";

    private SRGMediaPlayerController controller;

    private SRGMediaPlayerControllerQueueListener queue;

    private SRGMediaPlayerException lastError;
    private boolean mediaCompletedReceived;
    private MockDataProvider provider;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        injectInstrumentation(instrumentation);

        // Init variables
        provider = new MockDataProvider();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                controller = new SRGMediaPlayerController(getInstrumentation().getContext(), provider, "test");
                controller.setDebugMode(true);
            }
        });
        controller.setDebugMode(true);

        lastError = null;
        mediaCompletedReceived = false;
        controller.registerEventListener(new SRGMediaPlayerController.Listener() {
            @Override
            public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
                switch (event.type) {
                    case FATAL_ERROR:
                    case TRANSIENT_ERROR:
                        lastError = event.exception;
                        break;
                    case MEDIA_COMPLETED:
                        mediaCompletedReceived = true;
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
        assertNull(controller.getMediaIdentifier());
        assertFalse(controller.isReleased());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isLoading());
        assertFalse(controller.isLive());
        assertEquals(SRGMediaPlayerController.UNKNOWN_TIME, controller.getLiveTime());
        assertEquals(SRGMediaPlayerController.UNKNOWN_TIME, controller.getMediaDuration());
        assertFalse(controller.hasVideoTrack());
    }

    // TODO: Fix. Either the test is wrong and the documentation needs to be updated, or the test is
    //       correct and the implementation needs to be fixed
    @Test
    public void testMediaIdentifier() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        assertEquals(VIDEO_ON_DEMAND_IDENTIFIER, controller.getMediaIdentifier());
    }

    @Test
    public void testPreparingState() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.PREPARING);
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testReadyState() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testPlayAudioOverHTTP() throws Exception {
        controller.play(NON_STREAMED_VIDEO_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testHTTP403() throws Exception {
        controller.play(HTTP_403_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void TestHTTP404() throws Exception {
        controller.play(HTTP_404_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testNullUriError() throws Exception {
        controller.play("NULL");
        waitForState(SRGMediaPlayerController.State.PREPARING);
        waitForState(SRGMediaPlayerController.State.RELEASED);
        Assert.assertTrue(controller.isReleased());
        Assert.assertNotNull(lastError);
    }

    @Test
    public void testPlaybackStateInitialSequence() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testOnDemandVideoPlayback() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertFalse(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testVideoLivestreamPlayback() throws Exception {
        controller.play(VIDEO_LIVESTREAM_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testDVRVideoLivestreamPlayback() throws Exception {
        controller.play(VIDEO_DVR_LIVESTREAM_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testOnDemandVideoPlaythrough() throws Exception {
        // Start near the end of the stream
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER, (long) 3566768);
        waitUntilState(SRGMediaPlayerController.State.READY);
        waitUntilEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testNonStreamedMediaPlaythrough() throws Exception {
        controller.play(NON_STREAMED_VIDEO_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        waitUntilState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testOnDemandAudioPlayback() throws Exception {
        controller.play(AUDIO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
    }

    @Test
    public void testDVRAudioLivestreamPlayback() throws Exception {
        controller.play(AUDIO_DVR_LIVESTREAM_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.hasVideoTrack());
        assertTrue(controller.isLive());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getMediaDuration());
        assertTrue(SRGMediaPlayerController.UNKNOWN_TIME != controller.getLiveTime());
    }

    @Test
    public void testOnDemandAudioPlaythrough() throws Exception {
        // Start near the end of the stream
        controller.play(AUDIO_ON_DEMAND_IDENTIFIER, (long) 3230783);
        waitUntilState(SRGMediaPlayerController.State.READY);
        waitUntilEvent(SRGMediaPlayerController.Event.Type.MEDIA_COMPLETED);
    }

    @Test
    public void testPlayAtPosition() throws Exception {
        controller.play(AUDIO_ON_DEMAND_IDENTIFIER, (long) 30000);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 30);
    }

    @Test
    public void testPlayAfterStreamEnd() throws Exception {
        controller.play(AUDIO_ON_DEMAND_IDENTIFIER, (long) 9900000);
        waitUntilState(SRGMediaPlayerController.State.READY);
        waitForState(SRGMediaPlayerController.State.RELEASED);
    }

    @Test
    public void testPause() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        controller.pause();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testSeek() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        waitUntilState(SRGMediaPlayerController.State.BUFFERING);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testMultipleSeeks() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        controller.seekTo(70 * 1000);
        waitUntilState(SRGMediaPlayerController.State.BUFFERING);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 70);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhileNotPlaying() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.PREPARING);
        assertFalse(controller.isPlaying());

        controller.seekTo(60 * 1000);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertTrue(controller.isPlaying());
    }

    @Test
    public void testSeekWhilePaused() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertTrue(controller.isPlaying());
        controller.pause();
        Thread.sleep(100); // Need to wait
        assertFalse(controller.isPlaying());
        assertEquals(controller.getMediaPosition() / 1000, 0);

        controller.seekTo(60 * 1000);
        // TODO: No BUFFERING?
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertEquals(controller.getMediaPosition() / 1000, 60);
        assertFalse(controller.isPlaying());
    }

    @Test
    public void testSeekWhileReleasing() throws Exception {

    }

    @Test
    public void testSeekWhileReleased() throws Exception {

    }

    @Test
    public void testRelease() throws Exception {
        controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
        waitUntilState(SRGMediaPlayerController.State.READY);
        assertFalse(controller.isReleased());

        // Trigger a release. The controller is not immediately reaching the released state.
        controller.release();
        assertFalse(controller.isReleased());

        waitUntilState(SRGMediaPlayerController.State.RELEASED);
        assertTrue(controller.isReleased());
        assertNull(controller.getMediaIdentifier());
    }

    @Test
    public void playReleaseRobustness() {
        final Context context = getInstrumentation().getContext();
        int testCount = 100;

        for (int i = 0; i < testCount; i++) {
            Log.v("MediaCtrlerTest", "create/play/release " + i + " / " + testCount);
            Runnable runnable = new CreatePlayRelease(context, provider);
            getInstrumentation().runOnMainSync(runnable);
        }
    }

    // Wait for an error, expecting it to occur right afterwards. Will fail if this is not the case.
    private void waitForError(final String message) {
        Log.i(getClass().getName(), "Wait for error");
        Assert.assertTrue("Timeout waiting for error: " + message,
                waitForCondition(TIMEOUT_STATE_CHANGE, new EventCondition() {
                    @Override
                    public boolean check(SRGMediaPlayerController.Event event) {
                        return SRGMediaPlayerController.Event.Type.FATAL_ERROR == event.type
                                && TextUtils.equals(event.exception.getMessage(), message);
                    }
                }));
    }

    // Wait for the specified state, expecting it to occur right afterwards. Will fail if this is not the case.
    private void waitForState(final SRGMediaPlayerController.State state) {
        Log.i(getClass().getName(), "Wait for state: " + state);
        Assert.assertTrue("Timeout waiting for player state: " + state,
                waitForCondition(TIMEOUT_STATE_CHANGE, new EventCondition() {
                    @Override
                    public boolean check(SRGMediaPlayerController.Event event) {
                        if (SRGMediaPlayerController.Event.Type.STATE_CHANGE == event.type) {
                            assertEquals(state, event.state);
                            Log.i(getClass().getName(), "State: " + state + " find.");
                            return true;
                        } else {
                            return false;
                        }
                    }
                }));
    }

    // Wait for the specified condition, expecting it to occur right afterwards. Will fail if this is not the case.
    private boolean waitForCondition(long timeout, EventCondition condition) {
        SRGMediaPlayerController.Event event = null;
        long startTime = System.nanoTime();
        long timeoutNs = TimeUnit.NANOSECONDS.convert(timeout, TimeUnit.MILLISECONDS);
        while (startTime + timeoutNs > System.nanoTime()) {
            try {
                event = queue.getEventInBlockingQueue();
                Log.i(getClass().getName(), "Pool event: " + String.valueOf(event));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (event != null) {
                if (condition.check(event)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Wait until an event of the specified type is received. Fails if no event is received for a given
    // timeout period.
    private void waitUntilEvent(final SRGMediaPlayerController.Event.Type eventType, final int timeoutSeconds) throws Exception {
        ConditionWatcher watcher = ConditionWatcher.getInstance();
        watcher.setTimeoutLimit(timeoutSeconds * 1000);
        watcher.waitForCondition(new EventInstruction() {
            @Override
            public boolean checkCondition(SRGMediaPlayerController.Event event) {
                return event.type == eventType;
            }
        });
    }

    private void waitUntilEvent(final SRGMediaPlayerController.Event.Type eventType) throws Exception {
        waitUntilEvent(eventType, 20);
    }

    // Wait until a given state is reached. Fails if the state is not reached within a given timeout period.
    private void waitUntilState(final SRGMediaPlayerController.State state, final int timeoutSeconds) throws Exception {
        ConditionWatcher watcher = ConditionWatcher.getInstance();
        watcher.setTimeoutLimit(timeoutSeconds * 1000);
        watcher.waitForCondition(new EventInstruction() {
            @Override
            public boolean checkCondition(SRGMediaPlayerController.Event event) {
                return event.state == state;
            }
        });
    }

    private void waitUntilState(final SRGMediaPlayerController.State state) throws Exception {
        waitUntilState(state, 20);
    }

    private interface EventCondition {
        boolean check(SRGMediaPlayerController.Event event);
    }

    private static class CreatePlayRelease implements Runnable {
        SRGMediaPlayerController controller;
        private Context context;
        private SRGMediaPlayerDataProvider provider;
        private Random r = new Random();

        public CreatePlayRelease(Context context, SRGMediaPlayerDataProvider provider) {
            this.context = context;
            this.provider = provider;
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
            controller = new SRGMediaPlayerController(context, provider, "test");
            controller.setDebugMode(true);
        }

        private void test() throws SRGMediaPlayerException, InterruptedException {
            controller.play(VIDEO_ON_DEMAND_IDENTIFIER);
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
