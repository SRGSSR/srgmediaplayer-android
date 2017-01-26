package ch.srg.mediaplayer;

/**
 * Created by seb on 30/08/16.
 */

public interface SRGMediaPlayerFactory {
    SRGMediaPlayerController play(String newMediaIdentifier, Long position, boolean autoStart);
}
