package ch.srg.mediaplayer;

import ch.srg.srgmediaplayer.utils.Cancellable;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 *
 * License information is available from the LICENSE file.
 */
public interface SRGMediaPlayerMetaDataProvider {
	Cancellable getMediaMetadata(String mediaIdentifier, GetMediaMetadataCallback callback);

	interface GetMediaMetadataCallback {
		void onMediaMetadataLoaded(SRGMediaMetadata srgMediaMetadata);

		void onDataNotAvailable();
	}
}
