package ch.srg.mediaplayer;

/**
 * Created by Axel on 02/03/2015.
 */
public class SRGMediaPlayerException extends Exception {
    private Reason reason;

    public enum Reason {
        NETWORK,
        DRM,
        FORBIDDEN,
        VIEW,
        RENDERER,
        DATASOURCE,
        EXOPLAYER,
        TOKEN
    }

    public SRGMediaPlayerException(String detailMessage, Throwable throwable, Reason reason) {
        super(detailMessage, throwable);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
