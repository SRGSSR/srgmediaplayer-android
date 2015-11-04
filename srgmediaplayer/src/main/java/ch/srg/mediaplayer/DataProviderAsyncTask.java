package ch.srg.mediaplayer;

import android.net.Uri;
import android.os.AsyncTask;

/**
 * Created by Axel on 31/03/2015.
 */
public class DataProviderAsyncTask extends AsyncTask<String, Integer, Void> {

	private final SRGMediaPlayerDataProvider dataProvider;

	private final SRGMediaPlayerController controller;
	private final PlayerDelegate playerDelegate;

	public DataProviderAsyncTask(SRGMediaPlayerController controller,
								 SRGMediaPlayerDataProvider dataProvider,
								 PlayerDelegate playerDelegate) {
		this.controller = controller;
		this.dataProvider = dataProvider;
		this.playerDelegate = playerDelegate;
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
				controller.onUriLoaded(uri, playerDelegate);
			} else {
				controller.onDataProviderException(new SRGMediaPlayerException("Null uri for mediaIdentifier " + mediaIdentifier));
			}
		} catch (SRGMediaPlayerException e) {
			controller.onDataProviderException(e);
		}
		return null;
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

	public PlayerDelegate getPlayerDelegate() {
		return playerDelegate;
	}
}
