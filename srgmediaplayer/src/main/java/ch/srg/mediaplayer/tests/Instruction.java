package ch.srg.mediaplayer.tests;

import android.os.Bundle;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public abstract class Instruction {

    private Bundle dataContainer = new Bundle();

    public final void setData(Bundle dataContainer) {
        this.dataContainer = dataContainer;
    }

    public final Bundle getDataContainer() {
        return dataContainer;
    }

    public abstract String getDescription();

    public abstract boolean checkCondition();
}
