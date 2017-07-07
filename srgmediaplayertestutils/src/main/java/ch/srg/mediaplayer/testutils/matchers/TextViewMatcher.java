package ch.srg.mediaplayer.testutils.matchers;

import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.regex.Pattern;

public class TextViewMatcher {

    private final int textViewId;

    public static TextViewMatcher withTextView(final int textViewId) {
        return new TextViewMatcher(textViewId);
    }

    public TextViewMatcher(int textViewId) {
        this.textViewId = textViewId;
    }

    public Matcher<View> textMatches(final Matcher<String> matcher) {

        return new TypeSafeMatcher<View>() {
            Resources resources = null;

            @Override
            public boolean matchesSafely(View view) {
                this.resources = view.getResources();

                TextView textView = (TextView) view.getRootView().findViewById(textViewId);
                return textView != null && matcher.matches(textView.getText());
            }

            @Override
            public void describeTo(Description description) {
                String idDescription = Integer.toString(textViewId);
                if (this.resources != null) {
                    try {
                        idDescription = this.resources.getResourceName(textViewId);
                    } catch (Resources.NotFoundException e) {
                        idDescription = String.format("%s (resource name not found)", textViewId);
                    }
                }

                description.appendText("TextView with id: " + idDescription);
            }
        };
    }

    public Matcher<View> patternMatches(final String pattern){
        return new TypeSafeMatcher<View>() {
            Resources resources = null;

            @Override
            protected boolean matchesSafely(View view) {
                this.resources = view.getResources();

                TextView textView = (TextView) view.getRootView().findViewById(textViewId);
                return textView != null && Pattern.matches(pattern, textView.getText());
            }

            @Override
            public void describeTo(Description description) {
                String idDescription = Integer.toString(textViewId);
                if (this.resources != null) {
                    try {
                        idDescription = this.resources.getResourceName(textViewId);
                    } catch (Resources.NotFoundException e) {
                        idDescription = String.format("%s (resource name not found)", textViewId);
                    }
                }

                description.appendText("TextView with id: " + idDescription);
            }
        };
    }
}