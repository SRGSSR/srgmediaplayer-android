package ch.srg.mediaplayer.extras.dataproviders;

import android.net.Uri;
import android.support.annotation.NonNull;

import java.util.List;

import ch.srg.mediaplayer.PlayerDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.segmentoverlay.data.SegmentDataProvider;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by Axel on 02/03/2015.
 */
public class DirectMappingDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {
	private int mediaType;

	public DirectMappingDataProvider(int mediaType) {
		this.mediaType = mediaType;
	}

	@Override
	public Uri getUri(String mediaIdentifier, PlayerDelegate playerDelegate) {
		return Uri.parse(mediaIdentifier);
	}

	@Override
	public List<Segment> getSegments(String mediaIdentifier) {
		return null;
	}

	@Override
	public int getMediaType(@NonNull String mediaIdentifier) throws SRGMediaPlayerException {
		return mediaType;
	}
}
