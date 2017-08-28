package ch.srg.mediaplayer.internal;

import android.net.Uri;

import ch.srg.mediaplayer.PlayerDelegate;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface PlayerDelegateFactory {
    PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier);
}
