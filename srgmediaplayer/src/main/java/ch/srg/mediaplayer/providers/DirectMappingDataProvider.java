package ch.srg.mediaplayer.providers;

import android.net.Uri;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.segment.data.SegmentDataProvider;

/**
 * Created by Axel on 02/03/2015.
 */
public class DirectMappingDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {

	@SRGMediaType
	private int mediaType;

	public DirectMappingDataProvider(@SRGMediaType int mediaType) {
		this.mediaType = mediaType;
	}

	@Override
	public void getUri(String mediaIdentifier, PlayerDelegate playerDelegate, GetUriCallback getUriCallback) {
		getUriCallback.onUriLoaded(mediaIdentifier, Uri.parse(mediaIdentifier), mediaIdentifier, null, mediaType, STREAM_HLS);
	}

	@Override
	public void getSegmentList(String mediaIdentifier, GetSegmentListCallback callback) {
		callback.onDataNotAvailable();
	}
}
