/*
 * Created by David Gerber
 * 
 * Copyright (c) 2013 Radio Télévision Suisse
 * All Rights Reserved
 */
package ch.srg.mediaplayer.service;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;

public class MusicControl {
	/* copied from MediaPlaybackService in the Music Player app */
	private static final String SERVICECMD = "com.android.music.musicservicecommand";
	private static final String CMDNAME = "command";
	private static final String CMDPAUSE = "pause";

	static public void pause(Activity activity) {
		Intent i = new Intent(SERVICECMD);
		i.putExtra(CMDNAME, CMDPAUSE);
		activity.sendBroadcast(i);
	}

	static public void pause(Service service) {
		Intent i = new Intent(SERVICECMD);
		i.putExtra(CMDNAME, CMDPAUSE);
		service.sendBroadcast(i);
	}
}
