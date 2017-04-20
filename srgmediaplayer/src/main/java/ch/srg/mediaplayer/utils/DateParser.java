package ch.srg.mediaplayer.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by zapek on 30/01/14.
 */
public class DateParser {

    public static final int DATE_FORMAT_ISO_8601 = 1; /* used by Integration Layer */
    private static final String TAG = "localization";
    private final int format;
    private SimpleDateFormat sdf;

    public DateParser(int format) {
        this.format = format;
        switch (format) {
            case DATE_FORMAT_ISO_8601:
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
                break;

            default:
                sdf = null;
                break;
        }

        if (sdf == null) {
            throw new RuntimeException("wrong date parser format");
        }
    }

    public long parseTime(String s) {
        Date date = parseDate(s);
        return (date != null ? date.getTime() : 0);
    }

    @Nullable
    public Date parseDate(String s) {
        Date date = null;

        if (!TextUtils.isEmpty(s)) {
            if (format == DATE_FORMAT_ISO_8601) {
                s = fixZForSimpleDateFormat(s);
            }
            try {
                date = sdf.parse(s);
            } catch (ParseException e) {
                Log.e(TAG, "date parsing error: '" + s + "'", e);
            }
        }
        return date;
    }

    @NonNull
    public static String fixZForSimpleDateFormat(@NonNull String s) {
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1) + "+00:00";
        }
        return s;
    }
}
