package ch.srg.mediaplayer.testutils;

import android.content.Context;
import android.support.test.espresso.IdlingResource;

import java.util.ArrayList;
import java.util.List;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.registerIdlingResources;
import static android.support.test.espresso.Espresso.unregisterIdlingResources;

/**
 * Created by npietri on 13.10.16.
 */

public abstract class BaseTestClass {

    private List<IdlingResource> idlingResources = new ArrayList<>();

    protected void addAndRegisterIdlingResource(IdlingResource resource) {
        idlingResources.add(resource);
        registerIdlingResources(resource);
    }

    protected void removeAndUnregisterIdlingResource(IdlingResource resource) {
        idlingResources.remove(resource);
        unregisterIdlingResources(resource);
    }

    public void teardownOkHttpIdlingResource() {
        for (IdlingResource resource : idlingResources) {
            unregisterIdlingResources(resource);
        }
    }

    public static Context getTestContext() {
        return getInstrumentation().getTargetContext();
    }

}