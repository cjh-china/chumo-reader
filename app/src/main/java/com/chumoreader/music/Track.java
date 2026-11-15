package com.chumoreader.music;

/** 一首本地音乐。 */
public class Track {
    public long id;
    public String title = "";
    public String artist = "";
    public String album = "";
    public String path = "";
    public long duration;
    public long albumId;

    public String display() {
        if (title == null || title.length() == 0) {
            int i = path == null ? -1 : path.lastIndexOf('/');
            return i >= 0 ? path.substring(i + 1) : "未知曲目";
        }
        return title;
    }

    public String artistOrUnknown() {
        if (artist == null || artist.length() == 0 || "<unknown>".equals(artist)) {
            return "未知歌手";
        }
        return artist;
    }

    public String durationText() {
        long s = duration / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
