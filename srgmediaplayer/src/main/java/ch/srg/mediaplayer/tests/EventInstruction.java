package ch.srg.mediaplayer.tests;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class EventInstruction extends Instruction implements SRGMediaPlayerController.Listener {

    private SRGMediaPlayerController.Event event;

    private boolean eventReceived;

    public EventInstruction(SRGMediaPlayerController.Event event) {
        this.event = event;
        SRGMediaPlayerController.registerGlobalEventListener(this);
    }

    @Override
    public String getDescription() {
        return "Event Instruction for: " + event;
    }

    @Override
    public boolean checkCondition() {
        if (eventReceived) {
            SRGMediaPlayerController.unregisterGlobalEventListener(this);
        }
        return eventReceived;
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (!eventReceived) {
            eventReceived = this.event.equals(event);
        }
    }
}