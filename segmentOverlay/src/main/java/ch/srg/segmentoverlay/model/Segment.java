package ch.srg.segmentoverlay.model;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * Created by zapek on 2014-05-07.
 */
public class Segment implements Comparable<Segment>, Parcelable {

	/** URN is the target urn of the media to be played. */
	private String urn;
	/** Unique identifier for this particular segment, not used for playing. */
	private String identifier;
	private String title;
	private String description;
	private String imageUrl;
	private String blockingReason;
	private long markIn;
	private long markOut;
	private int progress;
	private boolean isCurrent;
	private long publishedTimestamp;
	private boolean displayable;

	public Segment(String identifier, String title, String description, String imageUrl,
	               long markIn, long markOut, String blocking, long publishedTimestamp,
	               boolean displayable, String urn) {
		this.identifier = identifier;
		this.title = title;
		this.description = description;
		this.imageUrl = imageUrl;
		this.markIn = markIn;
		this.markOut = markOut;
		this.publishedTimestamp = publishedTimestamp;
		this.displayable = displayable;
		this.urn = urn;
		blockingReason = blocking;
	}

	public Segment(Parcel in) {
		readFromParcel(in);
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(identifier);
		dest.writeString(title);
		dest.writeString(description);
		dest.writeString(imageUrl);
		dest.writeString(blockingReason);
		dest.writeLong(markIn);
		dest.writeLong(markOut);
		dest.writeLong(publishedTimestamp);
		dest.writeString(urn);
		dest.writeByte((byte) (displayable ? 1 : 0));
	}

	private void readFromParcel(Parcel in) {
		identifier = in.readString();
		title = in.readString();
		description = in.readString();
		imageUrl = in.readString();
		blockingReason = in.readString();
		markIn = in.readLong();
		markOut = in.readLong();
		publishedTimestamp = in.readLong();
		urn = in.readString();
		displayable = in.readByte() != 0;
	}

	public String getTitle() {
		return (title);
	}

	public String getDescription() {
		return (description);
	}

	public String getImageUrl() {
		return (imageUrl);
	}

	public long getMarkIn() {
		return (markIn);
	}

	public long getMarkOut() {
		return (markOut);
	}

	public long getDuration() {
		return (markOut - markIn);
	}

	public void setProgress(int value) {
		progress = value;
	}

	public int getProgress() {
		return (progress);
	}

	public void setIsCurrent(boolean value) {
		isCurrent = value;
	}

	public boolean isCurrent() {
		return (isCurrent);
	}

	public long getPublishedTimestamp() {
		return publishedTimestamp;
	}

	public String getBlockingReason() {
		return (blockingReason);
	}

	public boolean isBlocked() {
		return blockingReason != null;
	}

	public String getIdentifier() {
		return identifier;
	}

	public boolean isDisplayable() {
		return displayable;
	}

	public String getUrn() {
		return urn;
	}

	@Override
	public int compareTo(Segment another) {
		return ((int) (markIn - another.getMarkIn()));
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Creator<Segment> CREATOR = new Creator<Segment>() {
		@Override
		public Segment createFromParcel(Parcel in) {
			return new Segment(in);
		}

		@Override
		public Segment[] newArray(int size) {
			return new Segment[size];
		}
	};

	@Override
	public String toString() {
		return "Segment{" +
				"urn='" + urn + '\'' +
				", identifier='" + identifier + '\'' +
				", title='" + title + '\'' +
				", description='" + description + '\'' +
				", imageUrl='" + imageUrl + '\'' +
				", blockingReason='" + blockingReason + '\'' +
				", markIn=" + markIn +
				", markOut=" + markOut +
				", progress=" + progress +
				", isCurrent=" + isCurrent +
				", publishedTimestamp=" + publishedTimestamp +
				", displayable=" + displayable +
				'}';
	}
}
