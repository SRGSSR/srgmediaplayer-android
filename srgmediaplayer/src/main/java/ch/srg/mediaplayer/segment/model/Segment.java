package ch.srg.mediaplayer.segment.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Date;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class Segment implements Comparable<Segment> {
    private String identifier;
    private String title;
    private String description;
    private String imageUrl;
    private String blockingReason;
    private MarkRange markRange;
    private long duration;
    private boolean displayable;
    private boolean isLive;
    private boolean is360;

    public Segment(String identifier, String title, String description, String imageUrl, String blockingReason, MarkRange markRange, long duration, boolean displayable, boolean isLive, boolean is360) {
        this.identifier = identifier;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.blockingReason = blockingReason;
        this.markRange = markRange;
        this.duration = duration;
        this.displayable = displayable;
        this.isLive = isLive;
        this.is360 = is360;
    }

    public Segment(String identifier, String title, String description, String imageUrl,
                   String blockingReason, long markIn, long markOut, long duration,
                   boolean displayable, boolean isLive, boolean is360, long referenceDate) {
        this(identifier, title, description, imageUrl, blockingReason,
                new MarkRange(new Date(referenceDate + markIn), new Date(referenceDate + markOut)), duration,
                displayable, isLive, is360);
    }

    public Segment(String identifier, String title, String description, String imageUrl,
                   String blockingReason, long markIn, long markOut, long duration,
                   boolean displayable, boolean isLive, boolean is360) {
        this(identifier, title, description, imageUrl, blockingReason, new MarkRange(markIn, markOut), duration, displayable, isLive, is360);
    }

    public Segment(String identifier, String title, long markIn, long marOut, long duration, boolean displayable, String blockingReason) {
        this(identifier, title, null, null, blockingReason, markIn, marOut, duration, displayable, false, false, 0);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public MarkRange getMarkRange() {
        return markRange;
    }

    public Mark getMarkIn() {
        return markRange.getMarkIn();
    }

    public Mark getMarkOut() {
        return markRange.getMarkOut();
    }

    public long getDuration() {
        return duration;
    }

    public String getBlockingReason() {
        return blockingReason;
    }

    public boolean isBlocked() {
        return !TextUtils.isEmpty(blockingReason);
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isDisplayable() {
        return displayable;
    }

    public boolean isLive() {
        return isLive;
    }

    public boolean is360() {
        return is360;
    }

    @Override
    public int compareTo(@NonNull Segment another) {
        return markRange.compareTo(another.markRange);
    }

    @Override
    public String toString() {
        return "Segment{" +
                "identifier='" + identifier + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", blockingReason='" + blockingReason + '\'' +
                ", markRange=" + markRange +
                ", duration=" + duration +
                ", displayable=" + displayable +
                ", isLive=" + isLive +
                ", is360=" + is360 +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Segment segment = (Segment) o;

        if (duration != segment.duration) return false;
        if (displayable != segment.displayable) return false;
        if (isLive != segment.isLive) return false;
        if (is360 != segment.is360) return false;
        if (identifier != null ? !identifier.equals(segment.identifier) : segment.identifier != null)
            return false;
        if (title != null ? !title.equals(segment.title) : segment.title != null) return false;
        if (description != null ? !description.equals(segment.description) : segment.description != null)
            return false;
        if (imageUrl != null ? !imageUrl.equals(segment.imageUrl) : segment.imageUrl != null)
            return false;
        if (blockingReason != null ? !blockingReason.equals(segment.blockingReason) : segment.blockingReason != null)
            return false;
        return markRange != null ? markRange.equals(segment.markRange) : segment.markRange == null;
    }

    @Override
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (imageUrl != null ? imageUrl.hashCode() : 0);
        result = 31 * result + (blockingReason != null ? blockingReason.hashCode() : 0);
        result = 31 * result + (markRange != null ? markRange.hashCode() : 0);
        result = 31 * result + (int) (duration ^ (duration >>> 32));
        result = 31 * result + (displayable ? 1 : 0);
        result = 31 * result + (isLive ? 1 : 0);
        result = 31 * result + (is360 ? 1 : 0);
        return result;
    }
}
