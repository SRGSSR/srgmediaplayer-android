package ch.srg.mediaplayer;

import java.util.UUID;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class DrmConfig {
    private String licenceUrl;
    private UUID drmType;

    public DrmConfig(String licenceUrl, UUID drmType) {
        this.licenceUrl = licenceUrl;
        this.drmType = drmType;
    }

    public String getLicenceUrl() {
        return licenceUrl;
    }

    public UUID getDrmType() {
        return drmType;
    }
}
