package ch.srg.mediaplayer;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public class SRGMediaPlayerException extends Exception {
	private boolean fatal;

	public SRGMediaPlayerException(String detailMessage, boolean fatal) {
		super(detailMessage);
	}

	public SRGMediaPlayerException(String detailMessage, Throwable throwable, boolean fatal) {
		super(detailMessage, throwable);
	}

	public SRGMediaPlayerException(Throwable throwable) {
		super(throwable);
	}

	public boolean isFatal() {
		return fatal;
	}
}
