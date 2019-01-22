package ch.srg.mediaplayer;

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RetryTestRule implements TestRule {
    private int tryCount;

    public RetryTestRule(int retryCount) {
        this.tryCount = retryCount;
    }

    public Statement apply(Statement base, Description description) {
        return statement(base, description);
    }

    private Statement statement(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable caughtThrowable = null;

                // implement retry logic here
                for (int i = 0; i < tryCount; i++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (Throwable t) {
                        caughtThrowable = t;
                        Log.e("RetryTestRule", (description.getDisplayName() + ": run " + (i + 1) + " failed"));
                    }
                }
                Log.e("RetryTestRule", description.getDisplayName() + ": giving up after " + tryCount + " failures");
                throw caughtThrowable;
            }
        };
    }
}
