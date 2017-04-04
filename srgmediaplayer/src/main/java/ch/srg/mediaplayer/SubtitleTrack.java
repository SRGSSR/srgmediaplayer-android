package ch.srg.mediaplayer;

public class SubtitleTrack {
    public Object tag;
    public String trackId;
    public String language;

    public SubtitleTrack(Object tag, String trackId, String language) {
        this.tag = tag;
        this.trackId = trackId;
        this.language = language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubtitleTrack that = (SubtitleTrack) o;

        if (tag != null ? !tag.equals(that.tag) : that.tag != null) return false;
        if (trackId != null ? !trackId.equals(that.trackId) : that.trackId != null) return false;
        return language != null ? language.equals(that.language) : that.language == null;

    }

    @Override
    public int hashCode() {
        int result = tag != null ? tag.hashCode() : 0;
        result = 31 * result + (trackId != null ? trackId.hashCode() : 0);
        result = 31 * result + (language != null ? language.hashCode() : 0);
        return result;
    }
}