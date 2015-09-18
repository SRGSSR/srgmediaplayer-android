package ch.srg.mediaplayer.demo;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.FlakyTest;
import android.util.Log;

import com.robotium.solo.Condition;
import com.robotium.solo.Solo;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ch.srg.mediaplayer.DemoMediaPlayerActivity;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.segmentoverlay.controller.SegmentController;

/**
 * Created by seb on 24/07/15.
 */
@RunWith(AndroidJUnit4.class)
public class SegmentsVideoTests extends AbstractPlayerTests {
    private static final long PLAYER_TIME_TOLERANCE = 1000;
    public static final String TEST_DUMMY = "dummy:SPECIMEN";
    public static final int TEST_RTS_1_DURATION = 1897000;
    public static final String TAG = "SegmentTest";

    // Timestamp
    private static final long TIMESTAMP_END_OF_LAST_SEGMENT = 239000;
    private static final long TIMESTAMP_BLOCKED_SEGMENT = 62500;

    Solo solo;
    private SRGMediaPlayerException lastError;
    private boolean mediaCompletedReceived;

    private SegmentController.Event currentExternalEvent;
    private SRGMediaPlayerController.Listener listener;

    public SegmentsVideoTests() {
        super(DemoMediaPlayerActivity.class);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        solo = new Solo(getInstrumentation(), getActivity());
        lastError = null;
        mediaCompletedReceived = false;
        listener = new SRGMediaPlayerController.Listener() {
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

                    case EXTERNAL_EVENT:
                        if (event instanceof SegmentController.Event) {
                            currentExternalEvent = (SegmentController.Event) event;
                            Log.i(TAG, currentExternalEvent.toString());
                            Log.i(TAG, "Time for event: " + String.valueOf(getMediaPlayer().getMediaPosition()));
                        }
                        break;
                }
            }
        };
        getMediaPlayer().registerEventListener(listener);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        Assert.assertNull(lastError);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void simplePlay() throws InterruptedException {
        playItem(TEST_DUMMY);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_START);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void segmentControllerEndSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        getSegmentController().seekTo(TIMESTAMP_END_OF_LAST_SEGMENT);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_END);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void segmentControllerBlockSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        getSegmentController().seekTo(TIMESTAMP_BLOCKED_SEGMENT);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_SKIPPED_BLOCKED);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void mediaPlayerEndSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        getMediaPlayer().seekTo(TIMESTAMP_END_OF_LAST_SEGMENT);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_END);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void mediaPlayerBlockSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        getMediaPlayer().seekTo(TIMESTAMP_BLOCKED_SEGMENT);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_SKIPPED_BLOCKED);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void userClickSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        solo.clickInRecyclerView(1);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_SELECTED);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void userSwitchSegment() throws InterruptedException {
        playItem(TEST_DUMMY);

        solo.clickInRecyclerView(0);
        solo.clickInRecyclerView(1);

        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_SWITCH);
    }

    // TODO Find method for user seeking
    //@Test
     public void userSeekToBlockedSegment() throws InterruptedException {
//        playItem(TEST_DUMMY);
//
//        solo.setProgressBar(1, (int) TIMESTAMP_BLOCKED_SEGMENT);
//
//        waitForSegmentEventSend(SegmentController.Event.Type.SEGMENT_USER_SEEK_BLOCKED);
    }

    private void waitForSegmentEventSend(final SegmentController.Event.Type type) {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return currentExternalEvent != null && type.equals(currentExternalEvent.segmentEventType);
            }
        }, PLAYER_TIMEOUT));
    }

}
