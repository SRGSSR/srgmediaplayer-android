package ch.srg.mediaplayer;

import android.support.annotation.Nullable;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface PlayerViewDelegate {
    void attachToController(SRGMediaPlayerController playerController);

    void detachFromController(SRGMediaPlayerController srgMediaPlayerController);

    void update();

    void setHideCentralButton(boolean loading);
}
