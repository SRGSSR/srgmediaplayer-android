package ch.srg.mediaplayer.internal.cast.exceptions;

import java.io.IOException;

/**
 * Created by npietri on 03.11.15.
 */
public class NoConnectionException extends IOException {

    public NoConnectionException() {
    }

    public NoConnectionException(Throwable throwable) {
        super(throwable);
    }

    public NoConnectionException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
