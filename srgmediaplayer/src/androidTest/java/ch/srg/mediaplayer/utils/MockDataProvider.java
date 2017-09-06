package ch.srg.mediaplayer.utils;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

import ch.srg.mediaplayer.SRGMediaPlayerDataProvider;
import ch.srg.mediaplayer.SRGMediaPlayerException;

/**
 * Created by npietri on 12.06.15.
 */
public class MockDataProvider implements SRGMediaPlayerDataProvider {

    private static Map<String, String> data = new HashMap<String, String>() {
        {
            put("SPECIMEN", "http://stream-i.rts.ch/i/specm/2014/specm_20141203_full_f_817794-,101,701,1201,k.mp4.csmil/master.m3u8");
            put("ODK", "http://stream-i.rts.ch/i/oreki/2015/OREKI_20150225_full_f_861302-,101,701,1201,k.mp4.csmil/master.m3u8");
            put("BIDOUM", "http://stream-i.rts.ch/i/bidbi/2008/bidbi_01042008-,450,k.mp4.csmil/master.m3u8");
            put("MULTI1", "https://srgssruni9ch-lh.akamaihd.net/i/enc9uni_ch@191320/master.m3u8");
            put("MULTI2", "https://srgssruni10ch-lh.akamaihd.net/i/enc10uni_ch@191367/master.m3u8");
            put("MULTI3", "https://srgssruni7ch-lh.akamaihd.net/i/enc7uni_ch@191283/master.m3u8");
            put("MULTI4", "https://srgssruni11ch-lh.akamaihd.net/i/enc11uni_ch@191455/master.m3u8");
            put("NDR", "http://ndr_fs-lh.akamaihd.net/i/ndrfs_nds@119224/master.m3u8?dw=0");
            put("NDR-DVR", "http://ndr_fs-lh.akamaihd.net/i/ndrfs_nds@119224/master.m3u8");
            put("BIG-BUCK-NON-STREAMED", "http://www.sample-videos.com/video/mp4/720/big_buck_bunny_720p_1mb.mp4");
            put("C-EST-PAS-TROP-TOT", "https://rtsww-a-d.rts.ch/la-1ere/programmes/c-est-pas-trop-tot/2017/c-est-pas-trop-tot_20170628_full_c-est-pas-trop-tot_007d77e7-61fb-4aef-9491-5e6b07f7f931-128k.mp3");

            put("INVALID", "http://invalid.stream/");
            put("HTTP_403", "http://httpbin.org/status/403");
            put("HTTP_404", "http://httpbin.org/status/404");
            put("NULL", null);
        }
    };

    private int count;

    @Override
    public void getUri(String mediaIdentifier, int playerType, GetUriCallback callback) {
        count++;
        String uriString = data.get(mediaIdentifier);
        if (uriString == null) {
            callback.onUriLoadFailed(mediaIdentifier, new SRGMediaPlayerException("no uri"));
        } else {
            callback.onUriLoaded(mediaIdentifier, Uri.parse(uriString), mediaIdentifier, null, STREAM_HLS);
        }
    }

    public int getCount() {
        return count;
    }
}
