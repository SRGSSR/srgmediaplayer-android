package ch.srg.mediaplayer.extras.dataproviders;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.segmentoverlay.data.SegmentDataProvider;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by seb on 27/05/15.
 */
public class MultiDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider  {
	HashMap<String, SRGMediaPlayerDataProvider> dataProviders = new HashMap<>();

	public void addDataProvider(String prefix, SRGMediaPlayerDataProvider dataProvider) {
		dataProviders.put(prefix, dataProvider);
	}

	public void removeDataProvider(String prefix) {
		dataProviders.remove(prefix);
	}

	public SRGMediaPlayerDataProvider getProvider(String mediaIdentifier) {
		String prefix = getPrefix(mediaIdentifier);
		SRGMediaPlayerDataProvider provider = dataProviders.get(prefix);
		if (provider == null) {
			throw new IllegalArgumentException("No provider found for prefix: " + prefix);
		}
		return provider;
	}

	public static String getPrefix(String mediaIdentifier) {
		return splitMediaIdentifier(mediaIdentifier)[0];
	}

	public static String getIdentifier(String mediaIdentifier) {
		return splitMediaIdentifier(mediaIdentifier)[1];
	}

	private static String[] splitMediaIdentifier(String mediaIdentifier) {
		String[] strings = new String[2];
		int index = mediaIdentifier.indexOf(':');
		if (index == -1 || index == 0 || index >= mediaIdentifier.length() - 1) {
			throw new IllegalArgumentException("Media identifier (" + mediaIdentifier + ") must follow <prefix>:<identifier> syntax");
		}
		strings[0] = mediaIdentifier.substring(0, index);
		strings[1] = mediaIdentifier.substring(index + 1);

		return strings;
	}

	@Override
	public Uri getUri(String mediaIdentifier) throws SRGMediaPlayerException {
		return getProvider(mediaIdentifier).
				getUri(getIdentifier(mediaIdentifier));
	}

	@Override
	public List<Segment> getSegments(String mediaIdentifier) {
		SRGMediaPlayerDataProvider provider = getProvider(mediaIdentifier);
		String prefix = getPrefix(mediaIdentifier);
		if (provider instanceof SegmentDataProvider) {
			List<Segment> baseList = ((SegmentDataProvider) provider).
					getSegments(getIdentifier(mediaIdentifier));
			ArrayList<Segment> prefixedSegments = new ArrayList<>(baseList.size());
			for (Segment s: baseList) {
				Segment prefixedS = new Segment(prefix + ":" + s.getMediaIdentifier(), s.getIdentifier(), s.getTitle(), s.getDescription(), s.getImageUrl(), s.getBlockingReason(), s.getMarkIn(), s.getMarkOut(), s.getDuration(), s.getPublishedTimestamp(), s.isDisplayable(), s.isFullLength());
				prefixedSegments.add(prefixedS);
			}
			return prefixedSegments;
		} else {
			return null;
		}
	}


	@Override
	public int getMediaType(String mediaIdentifier) throws SRGMediaPlayerException {
		return getProvider(mediaIdentifier).getMediaType(getIdentifier(mediaIdentifier));
	}

	public boolean isSupported(String mediaIdentifier) {
		try {
			getProvider(mediaIdentifier);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
