package ch.srg.mediaplayer;

import android.net.Uri;

/**
 * Created by Axel on 27/02/2015.
 */
public interface SRGMediaPlayerDataProvider {
    int MEDIA_TYPE_AUDIO = 1;
    int MEDIA_TYPE_VIDEO = 2;

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
        void onUriLoaded(String mediaIdentifier, Uri uri, String realMediaIdentifier, Long position, int mediaType, int streamType);

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
