package ch.srg.mediaplayer;

import com.google.android.exoplayer2.C;

import org.junit.Assert;
import org.junit.Test;

import ch.srg.mediaplayer.segment.model.MediaPlayerTimeLine;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class MediaPlayerTimeLineTest {

    @Test
    public void testEmpty() {
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertEquals(C.TIME_UNSET, mediaPlayerTimeLine.getDurationMs());
        Assert.assertEquals(0L, mediaPlayerTimeLine.getStartTimeMs());
        Assert.assertFalse(mediaPlayerTimeLine.isDynamicWindow());
        Assert.assertFalse(mediaPlayerTimeLine.isSeekable());
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(985));
    }

    @Test
    public void testUpdate() {
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertFalse(mediaPlayerTimeLine.update(System.currentTimeMillis(), C.TIME_UNSET, false));

        mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertTrue(mediaPlayerTimeLine.update(C.TIME_UNSET, 56797L, false));

        mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertTrue(mediaPlayerTimeLine.update(System.currentTimeMillis(), 56000L, false));

        mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertTrue(mediaPlayerTimeLine.update(C.TIME_UNSET, C.TIME_UNSET, true));
        Assert.assertTrue(mediaPlayerTimeLine.update(System.currentTimeMillis(), 80000L, true));

        mediaPlayerTimeLine = new MediaPlayerTimeLine();
        Assert.assertFalse(mediaPlayerTimeLine.update(0L, C.TIME_UNSET, false));
    }

    @Test
    public void testVOD() {
        long startTimeMs = 0L;
        long durationMs = 60000L;
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine(startTimeMs, durationMs, false, 0);

        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(durationMs - 1));
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(durationMs / 2));
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(0));
        Assert.assertTrue(mediaPlayerTimeLine.isSeekable());

        for (int position = 0; position < durationMs; position++) {
            Assert.assertEquals(position, mediaPlayerTimeLine.getPosition(position));
            Assert.assertEquals(position, mediaPlayerTimeLine.getTime(position));
        }
        Assert.assertEquals(C.TIME_UNSET, mediaPlayerTimeLine.getPosition(C.TIME_UNSET));
        Assert.assertEquals(C.TIME_UNSET, mediaPlayerTimeLine.getTime(C.TIME_UNSET));
    }

    @Test
    public void testLiveOnly() {
        long startTimeMs = System.currentTimeMillis();
        long durationMs = 30000L; //Live only have little duration
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine(startTimeMs, durationMs, true, 0);

        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(0));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - 1));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - MediaPlayerTimeLine.LIVE_EDGE_DURATION));
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(durationMs - MediaPlayerTimeLine.LIVE_EDGE_DURATION - 1));
        Assert.assertFalse(mediaPlayerTimeLine.isSeekable());


        for (int position = 0; position < durationMs; position++) {
            long time = startTimeMs + position;
            Assert.assertEquals(position, mediaPlayerTimeLine.getPosition(time));
            Assert.assertEquals(time, mediaPlayerTimeLine.getTime(position));
        }
    }

    @Test
    public void testSimpleLiveDvr() {
        long startTimeMs = System.currentTimeMillis();
        long durationMs = 120000L;
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine(startTimeMs, durationMs, true, 0);

        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(0));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - 1));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - MediaPlayerTimeLine.LIVE_EDGE_DURATION));
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(durationMs - MediaPlayerTimeLine.LIVE_EDGE_DURATION - 1));
        Assert.assertTrue(mediaPlayerTimeLine.isSeekable());


        for (int position = 0; position < durationMs; position++) {
            long time = startTimeMs + position;
            Assert.assertEquals(position, mediaPlayerTimeLine.getPosition(time));
            Assert.assertEquals(time, mediaPlayerTimeLine.getTime(position));
        }
    }

    @Test
    public void testLiveDvrWithOffset() {
        long startTimeMs = C.TIME_UNSET;
        long durationMs = 120000L;
        long offset = 26000L;
        MediaPlayerTimeLine mediaPlayerTimeLine = new MediaPlayerTimeLine(startTimeMs, durationMs, true, offset);
        Assert.assertNotEquals(startTimeMs, mediaPlayerTimeLine.getStartTimeMs());
        Assert.assertFalse(mediaPlayerTimeLine.isAtLivePosition(0));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - 1));
        Assert.assertTrue(mediaPlayerTimeLine.isAtLivePosition(durationMs - MediaPlayerTimeLine.LIVE_EDGE_DURATION));
        Assert.assertTrue(mediaPlayerTimeLine.isSeekable());


        long startTime = mediaPlayerTimeLine.getStartTimeMs();
        for (int position = 0; position < durationMs; position++) {
            long time = startTime + position;
            Assert.assertEquals(position, mediaPlayerTimeLine.getPosition(time));
            Assert.assertEquals(time, mediaPlayerTimeLine.getTime(position));
        }

        Assert.assertEquals(C.TIME_UNSET, mediaPlayerTimeLine.getPosition(C.TIME_UNSET));
    }
}
