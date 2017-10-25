package ch.srg.mediaplayer;

import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
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
    public static final String SPECIMEN_URL = "http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8";
    public static final Uri MEDIA_URI = Uri.parse(SPECIMEN_URL);
    private SRGMediaPlayerController controller;

    private SRGMediaPlayerControllerQueueListener queue;

    private SRGMediaPlayerException lastError;
    private boolean mediaCompletedReceived;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        injectInstrumentation(instrumentation);

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
    }

    @Test
    public void testPreparingState() throws Exception {
        controller.play(MEDIA_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
    }

    @Test
    public void testPlayReady() throws Exception {
        controller.play(MEDIA_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
    }

    @Test
    public void testNullUriError() throws Exception {
        try {
            controller.play(null, SRGMediaPlayerController.STREAM_HTTP_PROGRESSIVE);
            Assert.fail("Expected illegal argument exception");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testPlay() throws Exception {
        controller.play(MEDIA_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        Thread.sleep(100);
        Log.v("test", "isPlaying: " + controller.isPlaying());
        assertTrue("delegate.isPlaying()", controller.isPlaying());
    }

    @Test
    public void testPause() throws Exception {
        controller.play(MEDIA_URI, SRGMediaPlayerController.STREAM_HLS);
        waitForState(SRGMediaPlayerController.State.PREPARING);
        waitForState(SRGMediaPlayerController.State.BUFFERING);
        waitForState(SRGMediaPlayerController.State.READY);
        controller.pause();
        Thread.sleep(100);
        assertFalse(controller.isPlaying());
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
            controller.play(MEDIA_URI, SRGMediaPlayerController.STREAM_HLS);
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
