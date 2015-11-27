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
	 * Uri to play the media identifier. Must be constant for a single media identifier. This method
	 * may do a network or database request.
	 *
	 * @param mediaIdentifier media identifier
	 * @return Uri to play the media
	 * @throws SRGMediaPlayerException network or parsing error encapsulated in an SRGMediaPlayerExcpetion
	 */
	Uri getUri(String mediaIdentifier) throws SRGMediaPlayerException;

	/**
	 * Item type for a media identifier. Must be constant for a single media identifier. This method
	 * may do a network or database request.
	 * @param mediaIdentifier media identifier
	 * @return {@link #TYPE_AUDIO} or {@link #TYPE_VIDEO}
	 * @throws SRGMediaPlayerException network or parsing error encapsulated in an SRGMediaPlayerExcpetion
	 */
	int getMediaType(@NonNull String mediaIdentifier) throws SRGMediaPlayerException;
}
