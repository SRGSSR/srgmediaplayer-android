package ch.srg.mediaplayer;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Scanner;

import ch.srg.networking.HTTPConnectionFactory;

/**
 * Created by seb on 22/07/15.
 */
public class RawHttpDataProvider implements SRGMediaPlayerDataProvider {
    private final HTTPConnectionFactory httpConnectionFactory;
    private int mediaType;

    public RawHttpDataProvider(HTTPConnectionFactory httpConnectionFactory, int mediaType) {
        this.httpConnectionFactory = httpConnectionFactory;
        this.mediaType = mediaType;
    }

    @Override
    public Uri getUri(String mediaIdentifier) throws SRGMediaPlayerException {
        return httpConnectionFactory.fetch(mediaIdentifier, new HTTPConnectionFactory.URLConnectionProcessor<Uri>() {
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
}
