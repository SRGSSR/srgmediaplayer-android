package ch.srg.mediaplayer.segment.model;

import androidx.annotation.NonNull;

import java.util.Date;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class MarkRange implements Comparable<MarkRange> {
    private Mark markIn;
    private Mark markOut;

    public MarkRange(Mark markIn, Mark markOut) {
        this.markIn = markIn;
        this.markOut = markOut;
    }

    public MarkRange(@NonNull Date dateIn, @NonNull Date dateOut) {
        this(new Mark(dateIn), new Mark(dateOut));
    }

    public MarkRange(long markIn, long markOut) {
        this(new Mark(markIn), new Mark(markOut));
    }

    public Mark getMarkIn() {
        return markIn;
    }

    public Mark getMarkOut() {
        return markOut;
    }

    @Override
    public int compareTo(@NonNull MarkRange o) {
        return markIn.compareTo(o.markIn);
    }

    public boolean isInRange(long position) {
        return position >= markIn.getPosition() && position < markOut.getPosition();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MarkRange markRange = (MarkRange) o;

        if (markIn != null ? !markIn.equals(markRange.markIn) : markRange.markIn != null)
            return false;
        return markOut != null ? markOut.equals(markRange.markOut) : markRange.markOut == null;
    }

    @Override
    public int hashCode() {
        int result = markIn != null ? markIn.hashCode() : 0;
        result = 31 * result + (markOut != null ? markOut.hashCode() : 0);
        return result;
    }
}
