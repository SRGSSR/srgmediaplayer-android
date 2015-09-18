package ch.srg.mediaplayer.demo;

import android.support.test.InstrumentationRegistry;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.robotium.solo.Condition;
import com.robotium.solo.Solo;

import junit.framework.Assert;

import org.junit.Before;

import ch.srg.mediaplayer.DemoMediaPlayerActivity;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.SRGMediaPlayerView;
import ch.srg.segmentoverlay.controller.SegmentController;

/**
 * Created by seb on 11/08/15.
 */
public class AbstractPlayerTests extends ActivityInstrumentationTestCase2<DemoMediaPlayerActivity> {
    public static final String TEST_1 = "dummy:SPECIMEN";
    public static final int DURATION_TEST_1 = 3568000;
    protected static final int NETWORK_TIMEOUT = 10000;
    private static final long PLAYER_TIME_TOLERANCE = 1000;
    protected static final int PLAYER_TIMEOUT = NETWORK_TIMEOUT;
    public static final String TAG = "PlayerTest";
    protected SRGMediaPlayerException lastError;
    protected boolean mediaCompletedReceived;
    protected Solo solo;
    private SRGMediaPlayerController.Listener playerEventListener;
    private DemoMediaPlayerActivity activity;

    public AbstractPlayerTests(Class<DemoMediaPlayerActivity> activityClass) {
        super(activityClass);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        activity = getActivity();
        solo = new Solo(getInstrumentation(), activity);
        lastError = null;
        mediaCompletedReceived = false;
        Log.d("PlayerTest", "Listening to player " + getMediaPlayer().getControllerId());
        playerEventListener = new SRGMediaPlayerController.Listener() {
            @Override
            public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
                Log.d(TAG, "Received event " + event);
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
        };
        getMediaPlayer().registerEventListener(playerEventListener);
    }

    protected void playItem(String identifier) throws InterruptedException {
        getActivity().playTestIdentifier(identifier);
        waitForPlayerStartWithoutError();
    }

    protected void assertPlayerPosition(long expectedPosition) {
        long mediaPosition = getMediaPlayer().getMediaPosition();
        Assert.assertEquals(expectedPosition, mediaPosition, PLAYER_TIME_TOLERANCE);
        Log.v(TAG, "Player position: " + mediaPosition + ", expected " + expectedPosition);
    }

    protected void waitForPlayerStartWithoutError() {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return getMediaPlayer().isPlaying();
            }
        }, PLAYER_TIMEOUT));
        checkNoError();
    }

    protected void waitForPlayerNotPlaying() {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return !getMediaPlayer().isPlaying();
            }
        }, PLAYER_TIMEOUT));
    }

    protected void waitForPlayerSeekingStart() {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return getMediaPlayer().isSeekPending();
            }
        }, PLAYER_TIMEOUT));
    }

    protected void waitForPlayerSeekingDoneWithoutError() {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return !getMediaPlayer().isSeekPending();
            }
        }, PLAYER_TIMEOUT));
        checkNoError();
    }

    protected void waitForMediaCompleted() {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                Log.v(TAG, "mediaCompletedReceived = " + mediaCompletedReceived);
                return mediaCompletedReceived;
            }
        }, PLAYER_TIMEOUT));
        checkNoError();
    }

    protected void waitForPlayerPositionWithoutError(final long position) {
        Assert.assertTrue(solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return getMediaPlayer().getMediaPosition() >= position;
            }
        }, PLAYER_TIMEOUT));
        checkNoError();
    }

    private void checkNoError() {
        Assert.assertNull("lastError", lastError);
    }

    protected SRGMediaPlayerController getMediaPlayer() {
        return activity.getSrgMediaPlayer();
    }

    protected SRGMediaPlayerView getPlayerView() {
        return getActivity().getPlayerView();
    }

    protected SegmentController getSegmentController() {
        return activity.getSegmentController();
    }

    protected void waitForPlayerSettledDown() {
        long setupPlayTime = 1000;
        waitForPlayerPositionWithoutError(setupPlayTime);
    }
}
