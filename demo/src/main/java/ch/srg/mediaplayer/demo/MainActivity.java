package ch.srg.mediaplayer.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

public class MainActivity extends Activity implements View.OnClickListener {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.demo_audio).setOnClickListener(this);
		findViewById(R.id.demo_simple_media_player).setOnClickListener(this);
		findViewById(R.id.demo_segment_media_player).setOnClickListener(this);
		findViewById(R.id.demo_live_media_player).setOnClickListener(this);
		findViewById(R.id.demo_layouts).setOnClickListener(this);
		findViewById(R.id.demo_multi_live).setOnClickListener(this);

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
		if (!BuildConfig.DEBUG)
		{
			UpdateManager.register(this, getString(R.string.hockeyapp_app_identifier));
		}
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
		int id = v.getId();
		switch (id) {
			case R.id.demo_audio: {
				Intent intent = new Intent(this, AudioListActivity.class);
				startActivity(intent);
				break;
			}
			case R.id.demo_simple_media_player: {
				Intent intent = new Intent(this, DemoMediaPlayerActivity.class);
				startActivity(intent);
				break;
			}
			case R.id.demo_segment_media_player: {
				Intent intent = new Intent(this, DemoMediaPlayerActivity.class);
				intent.putExtra(DemoMediaPlayerActivity.ARG_LIVE, true);
				startActivity(intent);
				break;
			}
			case R.id.demo_live_media_player: {
				Intent intent = new Intent(this, SimplePlayerActivity.class);
				intent.putExtra(SimplePlayerActivity.ARG_URN, "directVideo:http://stream-i.rts.ch/i/tj/2015/tj_20150528_full_f_858979-,101,701,1201,k.mp4.csmil/master.m3u8");
				startActivity(intent);
				break;
			}
			case R.id.demo_layouts: {
				Intent intent = new Intent(this, LayoutsActivity.class);
				startActivity(intent);
				break;
			}
			case R.id.demo_multi_live: {
				Intent intent = new Intent(this, MultiDemoMediaPlayerActivity.class);
				startActivity(intent);
				break;
			}

		}
	}
}
