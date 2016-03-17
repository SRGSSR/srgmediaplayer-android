package ch.srg.mediaplayer;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.srg.segmentoverlay.data.SegmentDataProvider;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by Axel on 02/03/2015.
 */
public class DummyDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {


	private static Map<String, String> data = new HashMap<String, String>() {
		{
			put("SPECIMEN", "http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
			put("ODK", "http://stream-i.rts.ch/i/oreki/2015/OREKI_20150225_full_f_861302-,101,701,1201,k.mp4.csmil/master.m3u8");
			put("BIDOUM", "http://stream-i.rts.ch/i/bidbi/2008/bidbi_01042008-,450,k.mp4.csmil/master.m3u8");

			put("MULTI1", "http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
			put("MULTI2", "http://vevoplaylist-live.hls.adaptive.level3.net/vevo/ch1/appleman.m3u8");
			put("MULTI3", "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8");
//			put("MULTI4", "http://srv6.zoeweb.tv:1935/z330-live/stream/playlist.m3u8");

			put("ERROR", "http://invalid.stream/");
		}

		;
	};
	@Override
	public Uri getUri(String mediaIdentifier) {
		if (mediaIdentifier.contains("@")){
			mediaIdentifier = mediaIdentifier.substring(0,mediaIdentifier.indexOf('@'));
		}
		if (data.containsKey(mediaIdentifier)) {
			return Uri.parse(data.get(mediaIdentifier));
		}
		return null;
	}

	@Override
	public List<Segment> getSegments(String mediaIdentifier) {
		List<Segment> segments = new ArrayList<>();
		segments.add(createSegment("1", "Segment 1", 1000, 60000, false, true));
		segments.add(createSegment("HB2", "Hidden 2", 60000,  65000, true, false));
		segments.add(createSegment("2", "Segment 2", 115000, 120000, false, true));
		segments.add(createSegment("B2", "Blocked 2", 120000, 150000, true, true));
		segments.add(createSegment("3", "Segment 3", 150000, 180000, false, true));
		segments.add(createSegment("4", "Segment 4", 180000, 240000, false, true));
		return segments;
	}

	public static Segment createSegment(String id, String name, long markIn, long markOut, boolean blocked, boolean displayable) {
		String dummyImageUrl = "http://buygapinsurance.co.uk/wp-content/uploads/2011/08/crash_test_dummy.jpg";
		return new Segment(id, "urn:" + id, "Description " + id, name, dummyImageUrl, blocked ? "blocked" : null, markIn, markOut, markOut - markIn, 0, displayable, false);
	}

	@Override
	public int getMediaType(String mediaIdentifier) throws SRGMediaPlayerException {
		return TYPE_VIDEO;
	}
}
