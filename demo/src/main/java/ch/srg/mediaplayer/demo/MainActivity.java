package ch.srg.mediaplayer.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

import ch.srg.mediaplayer.DemoMediaPlayerActivity;

public class MainActivity extends Activity implements View.OnClickListener {
	private Button buttonAudio;
	private Button buttonDemoSegmentMediaPlayer;
	private Button buttonDemoLiveMediaPlayer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		buttonAudio = (Button) findViewById(R.id.audio);
		buttonAudio.setOnClickListener(this);

		buttonDemoSegmentMediaPlayer = (Button) findViewById(R.id.demo_segment_media_player);
		buttonDemoSegmentMediaPlayer.setOnClickListener(this);
		buttonDemoLiveMediaPlayer = (Button) findViewById(R.id.demo_live_media_player);
		buttonDemoLiveMediaPlayer.setOnClickListener(this);

		checkForUpdates();
	}

	@Override
	protected void onPause() {
		super.onPause();
		UpdateManager.unregister();
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkForCrashes();
	}

	private void checkForCrashes() {
		CrashManager.register(this, "314785ad94ebbae30a802a8a1eadf3df");
	}

	private void checkForUpdates() {
		// Remove this for store / production builds!
		UpdateManager.register(this, "314785ad94ebbae30a802a8a1eadf3df");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		if (v == buttonAudio) {
			Intent intent = new Intent(this, AudioListActivity.class);
			startActivity(intent);
		} else if (v == buttonDemoSegmentMediaPlayer) {
			Intent intent = new Intent(this, DemoMediaPlayerActivity.class);
			startActivity(intent);
		} else if (v == buttonDemoLiveMediaPlayer) {
			Intent intent = new Intent(this, DemoMediaPlayerActivity.class);
			intent.putExtra(DemoMediaPlayerActivity.ARG_LIVE, true);
			startActivity(intent);
		}
	}
}
