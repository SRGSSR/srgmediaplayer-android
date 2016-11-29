package ch.srg.mediaplayer;

public class SubtitleTrack {
    public int index;
    public String trackId;
    public String language;

    public SubtitleTrack(int index, String trackId, String language) {
        this.index = index;
        this.trackId = trackId;
        this.language = language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubtitleTrack that = (SubtitleTrack) o;

        if (index != that.index) return false;
        if (trackId != null ? !trackId.equals(that.trackId) : that.trackId != null) return false;
        return language != null ? language.equals(that.language) : that.language == null;

    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + (trackId != null ? trackId.hashCode() : 0);
        result = 31 * result + (language != null ? language.hashCode() : 0);
        return result;
    }
}