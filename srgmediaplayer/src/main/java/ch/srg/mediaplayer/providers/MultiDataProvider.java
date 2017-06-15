package ch.srg.mediaplayer.providers;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayer.segment.data.SegmentDataProvider;
import ch.srg.mediaplayer.segment.model.Segment;

/**
 * Created by seb on 27/05/15.
 */
public class MultiDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {
    protected HashMap<String, SRGMediaPlayerDataProvider> dataProviders = new HashMap<>();

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
    public void getUri(final String originalMediaIdentifier, int playerType, final GetUriCallback getUriCallback) {
        final String originalPrefix = getPrefix(originalMediaIdentifier);
        getProvider(originalMediaIdentifier).getUri(getIdentifier(originalMediaIdentifier), playerType, new GetUriCallback() {
            @Override
            public void onUriLoaded(String mediaIdentifier, Uri uri, String realMediaIdentifier, Long position, int mediaType, int streamType) {
                getUriCallback.onUriLoaded(originalMediaIdentifier, uri, originalPrefix + ":" + realMediaIdentifier, position, mediaType, streamType);
            }

            @Override
            public void onUriLoadFailed(String mediaIdentifier, SRGMediaPlayerException exception) {
                getUriCallback.onUriLoadFailed(originalMediaIdentifier, exception);
            }
        });
    }

    @Override
    public void getSegmentList(String mediaIdentifier, final GetSegmentListCallback callback) {
        SRGMediaPlayerDataProvider provider = getProvider(mediaIdentifier);
        if (provider instanceof SegmentDataProvider) {
            final String prefix = getPrefix(mediaIdentifier);
            String identifier = getIdentifier(mediaIdentifier);
            ((SegmentDataProvider) provider).getSegmentList(identifier, new GetSegmentListCallback() {
                @Override
                public void onSegmentListLoaded(List<Segment> baseList) {
                    ArrayList<Segment> prefixedSegments = new ArrayList<>(baseList.size());
                    for (Segment s : baseList) {
                        Segment prefixedS = new Segment(prefix + ":" + s.getMediaIdentifier(), s.getIdentifier(), s.getTitle(), s.getDescription(), s.getImageUrl(), s.getBlockingReason(), s.getMarkIn(), s.getMarkOut(), s.getDuration(), s.getPublishedTimestamp(), s.isDisplayable(), s.isFullLength());
                        prefixedSegments.add(prefixedS);
                    }
                    callback.onSegmentListLoaded(prefixedSegments);
                }

                @Override
                public void onDataNotAvailable() {
                    callback.onDataNotAvailable();
                }
            });
        }
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
