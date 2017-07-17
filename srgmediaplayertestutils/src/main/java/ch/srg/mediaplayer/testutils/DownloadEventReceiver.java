package ch.srg.mediaplayer.testutils;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

public class DownloadEventReceiver extends BroadcastReceiver {

    public interface CheckDownloadCompleteCallback {
        void onDownloadComplete();
    }

    private final CheckDownloadCompleteCallback callback;

    public DownloadEventReceiver(CheckDownloadCompleteCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub

        String action = intent.getAction();
        if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0));
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor cursor = manager.query(query);
            if (cursor.moveToFirst()) {
                if (cursor.getCount() > 0) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            callback.onDownloadComplete();
                    }
                }
            }
            cursor.close();
        }
    }
}