package ch.srg.mediaplayer.providers;

import android.net.Uri;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.segment.data.SegmentDataProvider;
import ch.srg.srgmediaplayer.utils.Cancellable;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public class DirectMappingDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {

	@SRGMediaType
	private int mediaType;

	public DirectMappingDataProvider(@SRGMediaType int mediaType) {
		this.mediaType = mediaType;
	}

	@Override
	public Cancellable getUri(String mediaIdentifier, int playerType, GetUriCallback getUriCallback) {
		getUriCallback.onUriLoadedOrUpdated(mediaIdentifier, Uri.parse(mediaIdentifier), mediaIdentifier, null, STREAM_HLS);
		return Cancellable.NOT_CANCELLABLE;
	}

	@Override
	public Cancellable getSegmentList(String mediaIdentifier, GetSegmentListCallback callback) {
		callback.onDataNotAvailable();
		return Cancellable.NOT_CANCELLABLE;
	}
}
