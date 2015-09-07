package ch.srg.mediaplayer;

/**
 * Created by Axel on 02/03/2015.
 */
public class SRGMediaPlayerException extends Exception {

	public SRGMediaPlayerException(String detailMessage) {
		super(detailMessage);
	}

	public SRGMediaPlayerException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public SRGMediaPlayerException(Throwable throwable) {
		super(throwable);
	}
}
