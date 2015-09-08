package ch.srg.mediaplayer;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Created by seb on 22/07/15.
 */
public class RawHttpDataProvider implements SRGMediaPlayerDataProvider {
    private static final String TAG = "rawHttp";
    private int mediaType;

    public RawHttpDataProvider(int mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public Uri getUri(String mediaIdentifier) throws SRGMediaPlayerException {
        return fetch(mediaIdentifier, new URLConnectionProcessor<Uri>() {
            @Override
            public void onSetupHttpURLConnection(HttpURLConnection urlConnection) throws IOException {
            }

            @Override
            public Uri onHttpURLConnectionSuccess(InputStreamReader in) throws IOException {
                Scanner s = new Scanner(in).useDelimiter("\\A");
                if (s.hasNext()) {
                    return Uri.parse(s.next());
                }
                else {
                    return null;
                }
            }

            @Override
            public Uri onHttpURLConnectionError(int httpCode, Exception e) {
                return null;
            }
        });
    }

    @Override
    public int getMediaType(String mediaIdentifier) throws SRGMediaPlayerException {
        return mediaType;
    }

    public interface URLConnectionProcessor<T> {
        /**
         * Called with a valid HttpURLConnection before the getResponseCode call.
         *
         * @param urlConnection
         */
        void onSetupHttpURLConnection(HttpURLConnection urlConnection) throws IOException;

        T onHttpURLConnectionSuccess(InputStreamReader in) throws IOException;

        T onHttpURLConnectionError(int httpCode, Exception e);

    }

    private
    @Nullable
    <T> T fetch(String url,
                @NonNull URLConnectionProcessor<T> processor) {
        HttpURLConnection httpURLConnection = null;

        try
        {
            httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

            processor.onSetupHttpURLConnection(httpURLConnection);
            Log.d(TAG, "Fetching " + url);

            int responseCode = httpURLConnection.getResponseCode();

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_NOT_AUTHORITATIVE:
                    InputStream inputStream = httpURLConnection.getInputStream();

                    InputStreamReader in = new InputStreamReader(inputStream, "UTF-8");
                    return processor.onHttpURLConnectionSuccess(in);
                default:
                    Log.e(TAG, String.format("%d HTTP error: %d for %s",
                            httpURLConnection.hashCode(), responseCode, url));
                    return processor.onHttpURLConnectionError(responseCode, null);
            }
        }
        catch (IOException e)
        {
            Log.e(TAG, "IO error for " + url + " " + e.toString());
            return processor.onHttpURLConnectionError(0, e);
        }
        finally
        {
            if (httpURLConnection != null)
            {
                httpURLConnection.disconnect();
            }
        }
    }

}
