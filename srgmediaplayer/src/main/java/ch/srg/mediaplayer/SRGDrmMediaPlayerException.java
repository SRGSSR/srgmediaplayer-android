package ch.srg.mediaplayer;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class SRGDrmMediaPlayerException extends SRGMediaPlayerException {
    public SRGDrmMediaPlayerException(String detailMessage) {
        super(detailMessage);
    }

    public SRGDrmMediaPlayerException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SRGDrmMediaPlayerException(Throwable throwable) {
        super(throwable);
    }
}
