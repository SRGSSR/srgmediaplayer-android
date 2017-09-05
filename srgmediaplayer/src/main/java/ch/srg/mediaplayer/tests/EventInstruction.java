package ch.srg.mediaplayer.tests;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class EventInstruction extends Instruction implements SRGMediaPlayerController.Listener {

    private boolean received;

    public EventInstruction() {
        SRGMediaPlayerController.registerGlobalEventListener(this);
    }

    @Override
    public String getDescription() {
        return "Event Instruction for: " + event;
    }

    @Override
    public boolean checkCondition() {
        return false;
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        received = checkCondition();
        if (received) {
            SRGMediaPlayerController.unregisterGlobalEventListener(this);
        }
    }
}