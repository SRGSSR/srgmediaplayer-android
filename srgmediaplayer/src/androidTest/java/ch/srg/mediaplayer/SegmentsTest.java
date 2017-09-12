package ch.srg.mediaplayer;

import android.app.Instrumentation;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.srg.mediaplayer.segment.data.SegmentDataProvider;
import ch.srg.mediaplayer.segment.model.Segment;
import ch.srg.mediaplayer.tests.ConditionWatcher;
import ch.srg.mediaplayer.tests.EventInstruction;

class SegmentsTestDataProvider implements SRGMediaPlayerDataProvider, SegmentDataProvider {

    public static final String ON_DEMAND_IDENTIFIER = "ON_DEMAND";

    private static Map<String, String> uriStrings = new HashMap<String, String>() {
        {
            put(ON_DEMAND_IDENTIFIER, "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8");
        }
    };

    private List<Segment> segmentList;

    public SegmentsTestDataProvider(List<Segment> segmentList) {
        this.segmentList = segmentList;
    }

    @Override
    public void getUri(String mediaIdentifier, int playerType, GetUriCallback callback) {
        String uriString = uriStrings.get(mediaIdentifier);
        if (uriString != null) {
            Uri uri = Uri.parse("https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8");
            callback.onUriLoaded(mediaIdentifier, uri, mediaIdentifier, null, STREAM_HLS);
        }
        else {
            callback.onUriLoadFailed(mediaIdentifier, new SRGMediaPlayerException("Unknown URI"));
        }
    }

    @Override
    public void getSegmentList(String mediaIdentifier, GetSegmentListCallback callback) {
        callback.onSegmentListLoaded(segmentList);
    }
}

@RunWith(AndroidJUnit4.class)
public class SegmentsTest extends InstrumentationTestCase {

    private SRGMediaPlayerController controller;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        injectInstrumentation(instrumentation);
    }

    @After
    public void release() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
    }

    @Test
    public void testSegmentPlaythrough() throws Exception {
        List<Segment> segmentList = new ArrayList<>();
        segmentList.add(createSegment("segment", 2000, 3000, false));

        controller = createController(segmentList);
        controller.play(SegmentsTestDataProvider.ON_DEMAND_IDENTIFIER);

        // TODO: Wait
    }

    private SRGMediaPlayerController createController(final List<Segment> segmentList) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                SegmentsTestDataProvider dataProvider = new SegmentsTestDataProvider(segmentList);
                controller = new SRGMediaPlayerController(getInstrumentation().getContext(), dataProvider, "test");
            }
        });
        return controller;
    }

    private static Segment createSegment(String identifier, long markIn, long markOut, boolean blocked) {
        return new Segment(identifier, "urn:" + identifier, null, null, null, blocked ? "blocked" : null, markIn, markOut, markOut - markIn, 0, true, false);
    }
}
