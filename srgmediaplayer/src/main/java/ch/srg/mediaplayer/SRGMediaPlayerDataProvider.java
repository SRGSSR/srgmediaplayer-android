package ch.srg.mediaplayer;

import android.net.Uri;
import android.support.annotation.NonNull;

/**
 * Created by Axel on 27/02/2015.
 */
public interface SRGMediaPlayerDataProvider {
	int TYPE_AUDIO = 1;
	int TYPE_VIDEO = 2;

	/**
	 * Interface defined to return values for player.
	 */
	interface GetUriAndMediaTypeCallback {

		/**
		 * Return Uri to play the media identifier. Must be constant for a single media identifier. This method
		 * may do a network or database request. And Item type for a media identifier. Must be constant for a single media identifier. This method
		 * may do a network or database request.
		 * @param uri Uri to play the media
		 * @param mediaType {@link #TYPE_AUDIO} or {@link #TYPE_VIDEO}
         */
		void onDataLoaded(Uri uri, int mediaType);

		/**
		 *
		 */
		void onDataNotAvailable(SRGMediaPlayerException exception);
	}

	void getUriAndMediaType(@NonNull String mediaIdentifier, PlayerDelegate playerDelegate, GetUriAndMediaTypeCallback callback);

	void cancel(GetUriAndMediaTypeCallback callback);
}
