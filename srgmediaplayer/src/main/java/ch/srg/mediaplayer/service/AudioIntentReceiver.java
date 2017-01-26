/*
 * Created by David Gerber
 * 
 * Copyright (c) 2013 Radio Télévision Suisse
 * All Rights Reserved
 */
package ch.srg.mediaplayer.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

public class AudioIntentReceiver extends BroadcastReceiver {

	private static final String TAG = MediaPlayerService.TAG;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
		{
			Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY");

			sendAction(context, MediaPlayerService.ACTION_PAUSE);
		}
		else if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON))
		{
			KeyEvent event = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
			if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
				int keyCode = event.getKeyCode();
				Log.d(TAG, "keyCode: " + keyCode);

				switch (keyCode) {
					case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
						sendAction(context, MediaPlayerService.ACTION_TOGGLE_PLAYBACK);
						break;

					case KeyEvent.KEYCODE_MEDIA_PLAY:
						sendAction(context, MediaPlayerService.ACTION_PLAY);
						break;

					case KeyEvent.KEYCODE_MEDIA_PAUSE:
						sendAction(context, MediaPlayerService.ACTION_PAUSE);
						break;

					case KeyEvent.KEYCODE_HEADSETHOOK:
					case KeyEvent.KEYCODE_MEDIA_STOP:
						sendAction(context, MediaPlayerService.ACTION_STOP);
						break;

					case KeyEvent.KEYCODE_MEDIA_NEXT:
						Log.d(TAG, "KEYCODE_MEDIA_NEXT not supported yet"); /* XXX */
						break;

					case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
						Log.d(TAG, "KEYCODE_MEDIA_PREVIOUS not supported yet"); /* XXX */
						break;
				}
			}
		}
	}

	private ComponentName sendAction(Context context, String action) {
		return context.startService(new Intent(action, null, context, MediaPlayerService.class));
	}

}
