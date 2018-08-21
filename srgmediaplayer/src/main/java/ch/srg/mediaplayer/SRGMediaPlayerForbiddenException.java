package ch.srg.mediaplayer;

/**
 * Created by Axel on 02/03/2015.
 */
public class SRGMediaPlayerForbiddenException extends SRGMediaPlayerException {

	public SRGMediaPlayerForbiddenException(String detailMessage) {
		super(detailMessage);
	}

	public SRGMediaPlayerForbiddenException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public SRGMediaPlayerForbiddenException(Throwable throwable) {
		super(throwable);
	}
}
