package ch.srg.mediaplayer.utils;

import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */

@RequiresApi(api = 18)
public final class DrmUtils {
    private static final String WIDEVINE_SECURITY_LEVEL = "securityLevel";
    private static final String WIDEVINE_SECURITY_L1 = "L1";
    private static final String WIDEVINE_SECURITY_L3 = "L3";

    /**
     * <pre>
     * @param hdcpLevel
     * @see {@link MediaDrm#HDCP_V1}
     * @see {@link MediaDrm#HDCP_V2}
     * @see {@link MediaDrm#HDCP_V2_1}
     * @see {@link MediaDrm#HDCP_V2_2}
     * @see {@link MediaDrm#HDCP_V2_3}
     * </pre>
     */
    @RequiresApi(api = 28)
    public static boolean isHdcpCompatible(int hdcpLevel) {
        try {
            MediaDrm drm = new MediaDrm(C.WIDEVINE_UUID);
            int maxHDCPLevel = drm.getMaxHdcpLevel();
            return hdcpLevel <= maxHDCPLevel;
        } catch (UnsupportedSchemeException e) {
            return false;
        }
    }

    public static boolean isWidevineL1(@NonNull FrameworkMediaDrm mediaDrm) {
        return TextUtils.equals(WIDEVINE_SECURITY_L1, getSecurityLevel(mediaDrm));
    }

    public static boolean isWidevineL3(@NonNull FrameworkMediaDrm mediaDrm) {
        return TextUtils.equals(WIDEVINE_SECURITY_L3, getSecurityLevel(mediaDrm));
    }

    private static String getSecurityLevel(@NonNull FrameworkMediaDrm mediaDrm) {
        return mediaDrm.getPropertyString(WIDEVINE_SECURITY_LEVEL);
    }
}
