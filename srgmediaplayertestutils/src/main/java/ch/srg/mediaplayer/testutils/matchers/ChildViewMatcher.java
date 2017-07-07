package ch.srg.mediaplayer.testutils.matchers;

import android.content.res.Resources;
import android.view.View;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ChildViewMatcher {

    private final int viewId;

    public static ChildViewMatcher withView(final int textViewId) {
        return new ChildViewMatcher(textViewId);
    }

    public ChildViewMatcher(int viewId) {
        this.viewId = viewId;
    }

    public Matcher<View> viewMatches(final Matcher<View> matcher) {

        return new TypeSafeMatcher<View>() {
            Resources resources = null;

            @Override
            public boolean matchesSafely(View view) {
                this.resources = view.getResources();

                View v = view.getRootView().findViewById(viewId);
                return v != null && matcher.matches(v);
            }

            @Override
            public void describeTo(Description description) {
                String idDescription = Integer.toString(viewId);
                if (this.resources != null) {
                    try {
                        idDescription = this.resources.getResourceName(viewId);
                    } catch (Resources.NotFoundException e) {
                        idDescription = String.format("%s (resource name not found)", viewId);
                    }
                }

                description.appendText("View with id: " + idDescription);
            }
        };
    }
}