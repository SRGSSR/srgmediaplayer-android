package ch.srg.mediaplayer.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;
import com.google.android.exoplayer2.drm.DrmInitData;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class FileLicenseStore implements LicenseStoreDelegate, Serializable {
    private static final String TAG = "FileLicenseStore";
    private HashMap<Integer, byte[]> map;
    private Context context;

    public FileLicenseStore(Context context) {
        this.context = context;
    }

    @WorkerThread
    @Override
    public byte[] fetch(DrmInitData drmInitData) {
        if (map == null) {
            readFile();
        }
        return map.get(drmInitData.hashCode());
    }

    @WorkerThread
    @Override
    public void store(DrmInitData drmInitData, byte[] keySet) {
        if (!Arrays.equals(keySet, map.get(drmInitData.hashCode()))) {
            map.put(drmInitData.hashCode(), keySet);
            writeFile();
        }
    }

    @NonNull
    private File getFile() {
        return new File(context.getCacheDir(), getClass().getName());
    }

    @SuppressLint("UseSparseArrays")
    @WorkerThread
    private void readFile() {
        try {
            File file = getFile();
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            //noinspection unchecked
            map = (HashMap<Integer, byte[]>) ois.readObject();
            ois.close();
            fis.close();
            Log.v(TAG, "Read license file");
        } catch (IOException | ClassNotFoundException e) {
            Log.v(TAG, "read", e);
            map = new HashMap<>();
        }
    }

    @WorkerThread
    private void writeFile() {
        try {
            File file = getFile();
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(map);
            oos.close();
            fos.close();
            Log.v(TAG, "Wrote license file");
        } catch (IOException e) {
            Log.v(TAG, "write", e);
        }
    }

}
