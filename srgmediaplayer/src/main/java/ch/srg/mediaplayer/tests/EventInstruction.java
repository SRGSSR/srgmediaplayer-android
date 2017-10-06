package ch.srg.mediaplayer.tests;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public abstract class EventInstruction extends Instruction implements SRGMediaPlayerController.Listener {

    private SRGMediaPlayerController.Event lastEvent;
    private boolean fulfilled;

    public EventInstruction() {
        SRGMediaPlayerController.registerGlobalEventListener(this);
    }

    @Override
    public String getDescription() {
        return fulfilled ? "Fulfilled with event: " + lastEvent : "Not fulfilled";
    }

    @Override
    public final boolean checkCondition() {
        return fulfilled;
    }

    public abstract boolean checkCondition(SRGMediaPlayerController.Event event);

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        lastEvent = event;

        fulfilled |= checkCondition(event);
        if (fulfilled) {
            SRGMediaPlayerController.unregisterGlobalEventListener(this);
        }
    }
}