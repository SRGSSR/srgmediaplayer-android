package ch.srg.mediaplayer;

import android.net.Uri;
import android.os.AsyncTask;

/**
 * Created by Axel on 31/03/2015.
 */
public class DataProviderAsyncTask extends AsyncTask<String, Integer, Void> {

	private final SRGMediaPlayerDataProvider dataProvider;

	private final SRGMediaPlayerController controller;

	public DataProviderAsyncTask(SRGMediaPlayerController controller, SRGMediaPlayerDataProvider dataProvider) {
		this.controller = controller;
		this.dataProvider = dataProvider;
	}

	@Override
	protected Void doInBackground(String... params) {
		if (params == null || params.length < 1 || isCancelled()) {
			return null;
		}
		try {
			String mediaIdentifier = params[0];
			Uri uri = dataProvider.getUri(mediaIdentifier);
			if (uri != null) {
				controller.sendMessage(SRGMediaPlayerController.MSG_DATA_PROVIDER_URI_LOADED, uri);
			} else {
				sendException(new SRGMediaPlayerException("Null uri for mediaIdentifier " + mediaIdentifier));
			}
		} catch (SRGMediaPlayerException e) {
			sendException(e);
		}
		return null;
	}

	private void sendException(SRGMediaPlayerException e) {
		controller.sendMessage(SRGMediaPlayerController.MSG_DATA_PROVIDER_EXCEPTION, e);
	}

	@Override
	protected void onPostExecute(Void nothing) {

	}

	@Override
	protected void onCancelled(Void nothing) {
		super.onCancelled(nothing);
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
	}

}
