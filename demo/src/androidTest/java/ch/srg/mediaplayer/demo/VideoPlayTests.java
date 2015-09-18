package ch.srg.mediaplayer.demo;

import android.support.test.runner.AndroidJUnit4;
import android.test.FlakyTest;
import android.util.Log;

import com.robotium.solo.Condition;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

import ch.srg.mediaplayer.DemoMediaPlayerActivity;

/**
 * Created by seb on 24/07/15.
 */
@RunWith(AndroidJUnit4.class)
public class VideoPlayTests extends AbstractPlayerTests {
    public static final String TAG = "VODTest";

    public VideoPlayTests() {
        super(DemoMediaPlayerActivity.class);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void simplePlay() throws InterruptedException {
        solo.waitForCondition(new Condition() {
            @Override
            public boolean isSatisfied() {
                return getActivity().isTestListLoaded();
            }
        }, NETWORK_TIMEOUT);

        playItem(getActivity().getTestListItem(0));
    }

    @FlakyTest(tolerance=10)
    @Test
    public void playCheckTime() throws InterruptedException {
        playItem(TEST_1);
        long setupPlayTime = 1000;
        waitForPlayerPositionWithoutError(setupPlayTime);
        final long playTime = 5000;
        Log.v(TAG, "Starting wait");
        Thread.sleep(playTime - setupPlayTime);
        Log.v(TAG, "End of wait");
        assertPlayerPosition(playTime);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void playCheckSeekForwardAfterSettlingDown() throws InterruptedException {
        playItem(TEST_1);

        waitForPlayerSettledDown();

        long seekPosition = 180000;
        getMediaPlayer().seekTo(seekPosition);

        waitForPlayerPositionWithoutError(seekPosition);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void playCheckSeekForwardWithoutDelay() throws InterruptedException {
        playItem(TEST_1);

        long seekPosition = 180000;
        getMediaPlayer().seekTo(seekPosition);

        waitForPlayerPositionWithoutError(seekPosition);
    }

    @FlakyTest(tolerance=10)
    @Test
    public void playSeekNearEnd() throws InterruptedException {
        playItem(TEST_1);

        long seekPosition = DURATION_TEST_1 - 1000;
        getMediaPlayer().seekTo(seekPosition);
        Assert.assertFalse(mediaCompletedReceived);

        waitForMediaCompleted();
        waitForPlayerNotPlaying();
    }

    @FlakyTest(tolerance=10)
    @Test
    public void playSeekPastEnd() throws InterruptedException {
        playItem(TEST_1);

        long seekPosition = DURATION_TEST_1 + 1000;
        getMediaPlayer().seekTo(seekPosition);

        waitForPlayerNotPlaying();
        waitForMediaCompleted();
    }

    // TODO Sadly the following does not work: solo is not able to include hardware layer in screenshots
    // Although "adb shell screencap -p" does work ...
    //@Test
    public void playTestScreenContent() throws InterruptedException {
        playItem(TEST_1);

        waitForPlayerSettledDown();

        solo.takeScreenshot();
    }
}
