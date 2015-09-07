package ch.srg.mediaplayer.internal;

import android.net.Uri;

import ch.srg.mediaplayer.PlayerDelegate;

/**
 * Created by npietri on 05.06.15.
 */
public interface PlayerDelegateFactory {
    PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier);
}
