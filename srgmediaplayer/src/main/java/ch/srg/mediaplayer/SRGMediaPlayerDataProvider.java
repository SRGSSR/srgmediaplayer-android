package ch.srg.mediaplayer;

import android.net.Uri;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by Axel on 27/02/2015.
 */
public interface SRGMediaPlayerDataProvider {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MEDIA_TYPE_UNKNOWN, MEDIA_TYPE_AUDIO, MEDIA_TYPE_VIDEO})
    public @interface SRGMediaType {
    }
    int MEDIA_TYPE_UNKNOWN = 0;
    int MEDIA_TYPE_AUDIO = 1;
    int MEDIA_TYPE_VIDEO = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STREAM_HLS, STREAM_HTTP_PROGRESSIVE, STREAM_DASH, STREAM_LOCAL_FILE})
    public @interface SRGStreamType {
    }
    int STREAM_HLS = 1;
    int STREAM_HTTP_PROGRESSIVE = 2;
    int STREAM_DASH = 3;
    int STREAM_LOCAL_FILE = 4;

    interface GetUriCallback {
        /**
         * @param mediaIdentifier     requested media identifier
         * @param uri                 Uri for the media
         * @param realMediaIdentifier the real mediaIdentifier played, in case of asking to play a logical segment
         * @param position            position in the stream
         * @param mediaType           {@link #MEDIA_TYPE_AUDIO} or {@link #MEDIA_TYPE_VIDEO}
         * @param streamType          {@link #STREAM_DASH}, {@link #STREAM_HLS}, {@link #STREAM_HTTP_PROGRESSIVE}, {@link #STREAM_LOCAL_FILE}
         */
        void onUriLoaded(String mediaIdentifier, Uri uri, String realMediaIdentifier, Long position, @SRGMediaType int mediaType, @SRGStreamType int streamType);

        /**
         * @param mediaIdentifier requested media identifier
         * @param exception       associated exception
         */
        void onUriLoadFailed(String mediaIdentifier, SRGMediaPlayerException exception);
    }

    /**
     * Uri to play the media identifier. Must be constant for a single media identifier.
     *
     * @param mediaIdentifier media identifier
     * @param playerDelegate  player delegate
     * @param getUriCallback  callback
     * @throws SRGMediaPlayerException network or parsing error encapsulated in an SRGMediaPlayerExcpetion
     */
    void getUri(String mediaIdentifier, PlayerDelegate playerDelegate, GetUriCallback getUriCallback);
}
