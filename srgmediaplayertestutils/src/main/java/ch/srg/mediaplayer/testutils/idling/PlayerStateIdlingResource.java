package ch.srg.mediaplayer.testutils.idling;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.espresso.IdlingResource;
import android.text.TextUtils;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */

public class PlayerStateIdlingResource implements IdlingResource, SRGMediaPlayerController.Listener {

    private final SRGMediaPlayerController.Event.Type eventToWait;
    private String urn;
    @Nullable
    private ResourceCallback callback;

    private boolean idleNow = false;

    public PlayerStateIdlingResource(@NonNull SRGMediaPlayerController.Event.Type eventToWait) {
        this.eventToWait = eventToWait;
        SRGMediaPlayerController.registerGlobalEventListener(this);
    }

    public PlayerStateIdlingResource(String urn, @NonNull SRGMediaPlayerController.Event.Type eventToWait) {
        this.urn = urn;
        this.eventToWait = eventToWait;
        SRGMediaPlayerController.registerGlobalEventListener(this);
    }

    @Override
    public String getName() {
        return PlayerStateIdlingResource.class.getName() + ":" + urn + ":" + eventToWait;
    }

    @Override
    public boolean isIdleNow() {
        return idleNow;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (mp != null) {
            if (urn == null) {
                if (eventToWait.equals(event.type)) {
                    SRGMediaPlayerController.unregisterGlobalEventListener(this);
                    idleNow = true;
                    if (callback != null) {
                        callback.onTransitionToIdle();
                    }
                }
            }
        }
    }
}
