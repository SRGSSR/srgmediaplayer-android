package ch.srg.mediaplayer;

import android.test.InstrumentationTestCase;

import ch.srg.mediaplayer.tests.ConditionWatcher;
import ch.srg.mediaplayer.tests.EventInstruction;

/**
 * Abstract base class for media player tests.
 */
public abstract class MediaPlayerTest extends InstrumentationTestCase {

    // Wait until an event of the specified type is received. Fails if no event is received for a given
    // timeout period.
    protected void waitForEvent(final SRGMediaPlayerController.Event.Type eventType, final int timeoutSeconds) throws Exception {
        ConditionWatcher watcher = ConditionWatcher.getInstance();
        watcher.setTimeoutLimit(timeoutSeconds * 1000);
        watcher.waitForCondition(new EventInstruction() {
            @Override
            public boolean checkCondition(SRGMediaPlayerController.Event event) {
                return event.type == eventType;
            }
        });
    }

    protected void waitForEvent(final SRGMediaPlayerController.Event.Type eventType) throws Exception {
        waitForEvent(eventType, 20);
    }

    // Wait until a given state is reached. Fails if the state is not reached within a given timeout period.
    protected void waitForState(final SRGMediaPlayerController.State state, final int timeoutSeconds) throws Exception {
        ConditionWatcher watcher = ConditionWatcher.getInstance();
        watcher.setTimeoutLimit(timeoutSeconds * 1000);
        watcher.waitForCondition(new EventInstruction() {
            @Override
            public boolean checkCondition(SRGMediaPlayerController.Event event) {
                return event.state == state;
            }
        });
    }

    protected void waitForState(final SRGMediaPlayerController.State state) throws Exception {
        waitForState(state, 20);
    }
}
