package ch.srg.mediaplayer.segment.model;

import com.google.android.exoplayer2.C;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class MediaPlayerTimeLine {
    public static final long LIVE_EDGE_DURATION = 45000;
    private long startTimeMs;
    private long durationMs;
    private boolean dynamicWindow;
    private boolean isSeekable;

    public MediaPlayerTimeLine(long startTimeMs, long durationMs, boolean dynamicWindow) {
        update(startTimeMs, durationMs, dynamicWindow);
    }

    public MediaPlayerTimeLine() {
        this(C.TIME_UNSET, C.TIME_UNSET, false);
    }

    public MediaPlayerTimeLine(MediaPlayerTimeLine source) {
        this.startTimeMs = source.startTimeMs;
        this.dynamicWindow = source.dynamicWindow;
        this.durationMs = source.durationMs;
        this.isSeekable = source.isSeekable;
    }

    /**
     * @param startTimeMs
     * @param durationMs
     * @param dynamicWindow
     * @return true is something has changed
     */
    public boolean update(long startTimeMs, long durationMs, boolean dynamicWindow) {
        if (!dynamicWindow) {
            startTimeMs = 0;
        } else if (startTimeMs == C.TIME_UNSET && durationMs != C.TIME_UNSET) {
            startTimeMs = System.currentTimeMillis() - durationMs;
        }
        boolean changed = false;
        if (startTimeMs != this.startTimeMs) {
            this.startTimeMs = startTimeMs;
            changed = true;
        }
        if (durationMs != this.durationMs) {
            this.durationMs = durationMs;
            changed = true;
        }
        if (dynamicWindow != this.dynamicWindow) {
            this.dynamicWindow = dynamicWindow;
            changed = true;
        }
        this.isSeekable = durationMs != C.TIME_UNSET && !(durationMs <= LIVE_EDGE_DURATION && dynamicWindow);
        return changed;
    }

    public boolean isAtLivePosition(long position) {
        return dynamicWindow && durationMs != C.TIME_UNSET && position >= durationMs - LIVE_EDGE_DURATION;
    }

    /**
     * @param time
     * @return relative position in the window [0,durationMs[
     */
    public long getPosition(long time) {
        return time == C.TIME_UNSET ? time : Math.min(Math.max(time - startTimeMs, 0), durationMs-1);
    }

    public long getTime(long position) {
        return startTimeMs == C.TIME_UNSET ? startTimeMs : startTimeMs + position;
    }

    public boolean isSeekable() {
        return isSeekable;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isDynamicWindow() {
        return dynamicWindow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaPlayerTimeLine that = (MediaPlayerTimeLine) o;

        if (startTimeMs != that.startTimeMs) return false;
        if (durationMs != that.durationMs) return false;
        if (dynamicWindow != that.dynamicWindow) return false;
        return isSeekable == that.isSeekable;
    }

    @Override
    public int hashCode() {
        int result = (int) (startTimeMs ^ (startTimeMs >>> 32));
        result = 31 * result + (int) (durationMs ^ (durationMs >>> 32));
        result = 31 * result + (dynamicWindow ? 1 : 0);
        result = 31 * result + (isSeekable ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MediaPlayerTimeLine{");
        sb.append("startTimeMs=").append(startTimeMs);
        sb.append(", durationMs=").append(durationMs);
        sb.append(", dynamicWindow=").append(dynamicWindow);
        sb.append(", isSeekable=").append(isSeekable);
        sb.append('}');
        return sb.toString();
    }
}
