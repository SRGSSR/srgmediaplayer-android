package ch.srg.mediaplayer.testutils.actions;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import android.view.View;

import org.hamcrest.Matcher;

public class ChildViewActions {

    public static ViewAction clickChildViewWithId(final int id) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specified id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                v.performClick();
            }
        };
    }

}