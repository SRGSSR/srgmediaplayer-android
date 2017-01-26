package ch.srg.mediaplayer;

import android.net.Uri;

/**
 * Created by Axel on 27/02/2015.
 */
public interface SRGMediaPlayerDataProvider {
    int TYPE_AUDIO = 1;
    int TYPE_VIDEO = 2;

    interface GetUriCallback {
        /**
         * @param mediaIdentifier requested media identifier
         * @param uri             Uri for the media
         * @param mediaType       {@link #TYPE_AUDIO} or {@link #TYPE_VIDEO}
         */
        void onUriLoaded(String mediaIdentifier, Uri uri, int mediaType);

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
