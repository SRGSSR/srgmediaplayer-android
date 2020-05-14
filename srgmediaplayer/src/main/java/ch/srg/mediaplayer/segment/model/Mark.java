package ch.srg.mediaplayer.segment.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class Mark implements Comparable<Mark> {
    private long position;
    @Nullable
    private Date date;

    public Mark(long position, @Nullable Date date) {
        this.position = position;
        this.date = date;
    }

    public Mark(long position) {
        this(position, null);
    }

    public Mark(@NonNull Date date) {
        this(0, date);
    }

    public long getPosition() {
        return position;
    }

    @Nullable
    public Date getDate() {
        return date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Mark mark = (Mark) o;

        if (position != mark.position) return false;
        return date != null ? date.equals(mark.date) : mark.date == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (position ^ (position >>> 32));
        result = 31 * result + (date != null ? date.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Mark{" +
                "position=" + position +
                ", date=" + date +
                '}';
    }

    @Override
    public int compareTo(@NonNull Mark another) {
        return (date != null && another.getDate() != null) ? date.compareTo(another.getDate()) : (int) (position - another.position);
    }
}
