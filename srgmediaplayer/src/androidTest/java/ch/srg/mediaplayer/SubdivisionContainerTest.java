package ch.srg.mediaplayer;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import ch.srg.mediaplayer.segment.model.Segment;
import ch.srg.mediaplayer.segment.model.SubDivisionContainer;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class SubdivisionContainerTest {

    @Test
    public void testContains() {
        List<Segment> listSegments = new ArrayList<>();
        listSegments.add(new Segment("s1", "s1", 100, 4000, 3900, true, null));
        listSegments.add(new Segment("s2", "s2", 5000, 5800, 800, true, "blocked"));
        listSegments.add(new Segment("s3", "s3", 6000, 10000, 4000, false, "blocked"));
        SubDivisionContainer subDivisionContainer = new SubDivisionContainer(listSegments);

        Segment s1 = new Segment("s1", "s1", 100, 4000, 3900, true, null);
        Assert.assertTrue(listSegments.contains(s1));
        Assert.assertTrue(subDivisionContainer.contains(s1));
        Assert.assertEquals(s1, listSegments.get(0));


    }

    @Test
    public void testFilter() {
        List<Segment> listSegments = new ArrayList<>();
        listSegments.add(new Segment("s1", "s1", 100, 4000, 3900, true, null));
        listSegments.add(new Segment("s2", "s2", 5000, 5800, 800, true, "blocked"));
        listSegments.add(new Segment("s3", "s3", 6000, 10000, 4000, false, "blocked"));

        List<Segment> blockedSegment = new ArrayList<>();
        blockedSegment.add(listSegments.get(1));
        blockedSegment.add(listSegments.get(2));
        List<Segment> displayedSegment = new ArrayList<>();
        displayedSegment.add(listSegments.get(0));
        displayedSegment.add(listSegments.get(1));


        SubDivisionContainer subDivisionContainer = new SubDivisionContainer(listSegments);
        Assert.assertEquals(3, subDivisionContainer.size());
        Assert.assertEquals(2, subDivisionContainer.getBlockedSegments().size());
        Assert.assertEquals(2, subDivisionContainer.getDisplayableSegments().size());
        Assert.assertEquals(blockedSegment, subDivisionContainer.getBlockedSegments());
        Assert.assertEquals(displayedSegment, subDivisionContainer.getDisplayableSegments());
        Assert.assertEquals(listSegments, subDivisionContainer.getSegments());

        for (int i = 0; i < subDivisionContainer.getBlockedSegments().size(); i++) {
            Assert.assertEquals(blockedSegment.get(i), subDivisionContainer.getBlockedSegment(i));
        }
        for (int i = 0; i < subDivisionContainer.getDisplayableSegments().size(); i++) {
            Assert.assertEquals(displayedSegment.get(i), subDivisionContainer.getDisplayedSegment(i));
        }

        for (int i = 0; i < subDivisionContainer.size(); i++) {
            Assert.assertEquals(listSegments.get(i), subDivisionContainer.getSegment(i));
        }

    }

    @Test
    public void testFindById() {
        List<Segment> listSegments = new ArrayList<>();
        listSegments.add(new Segment("s1", "s1", 100, 4000, 3900, true, null));
        listSegments.add(new Segment("s2", "s2", 5000, 5800, 800, true, "blocked"));
        listSegments.add(new Segment("s3", "s3", 6000, 10000, 4000, false, "blocked"));

        SubDivisionContainer subDivisionContainer = new SubDivisionContainer(listSegments);
        String[] ids = new String[]{"s2", "s3", "s1"};
        for (String id : ids) {
            Segment segment = subDivisionContainer.findSegmentById(id);
            Assert.assertNotNull(segment);
            Assert.assertEquals(id, segment.getIdentifier());
            if (segment.isDisplayable()) {
                segment = subDivisionContainer.findDisplayableSegmentById(id);
                Assert.assertNotNull(segment);
                Assert.assertEquals(id, segment.getIdentifier());
            }
            if (segment.isBlocked()) {
                segment = subDivisionContainer.findBlockedSegmentById(id);
                Assert.assertNotNull(segment);
                Assert.assertEquals(id, segment.getIdentifier());
            }
        }
        Assert.assertNull(subDivisionContainer.findSegmentById("Unknown"));
        Assert.assertNull(subDivisionContainer.findBlockedSegmentById("Unknown"));
        Assert.assertNull(subDivisionContainer.findDisplayableSegmentById("Unknown"));
    }

    @Test
    public void testFindAtPosition() {
        List<Segment> listSegments = new ArrayList<>();
        listSegments.add(new Segment("s1", "s1", 100, 4000, 3900, true, null));
        listSegments.add(new Segment("s2", "s2", 5000, 5800, 800, true, "blocked"));
        listSegments.add(new Segment("s3", "s3", 6000, 10000, 4000, false, "blocked"));

        SubDivisionContainer subDivisionContainer = new SubDivisionContainer(listSegments);


        Assert.assertNull(subDivisionContainer.findSegmentAtPosition(90));
        Segment s1 = subDivisionContainer.findSegmentAtPosition(100);
        Assert.assertNotNull(s1);
        Assert.assertEquals(s1.getIdentifier(), "s1");

        s1 = subDivisionContainer.findSegmentAtPosition(3999);
        Assert.assertNotNull(s1);
        Assert.assertEquals(s1.getIdentifier(), "s1");

        Assert.assertNull(subDivisionContainer.findSegmentAtPosition(4200));
        Assert.assertNull(subDivisionContainer.findBlockedSegmentAtPosition(4200));
        Assert.assertNull(subDivisionContainer.findDisplayedSegmentAtPosition(4200));
    }

}
