package ch.srg.mediaplayer.demo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import ch.srg.mediaplayer.service.MediaPlayerService;

public class AudioListActivity extends Activity implements View.OnClickListener {

    private static final String[] URLS = {
            "aac:http://stream.srg-ssr.ch/m/drs1/aacp_96",
            "aac:http://stream.srg-ssr.ch/m/drs2/aacp_96",
            "aac:http://stream.srg-ssr.ch/m/drs3/aacp_96"
    };

    private Button buttonPlay;
    private Button buttonStopAndPlay;
    private Button buttonToggle;
    private Button buttonStop;

    private Button buttonSeekTo1Minute;
    private ListView audioList;
    private String selectedAudioUrl;
    private Button buttonStartLoop;
    private boolean startLoopRunning;

    private ArrayList<String> eventLogList;
    private ArrayAdapter<String> eventListAdapter;
    private ListView eventList;
    private AudioStatusReceiver audioStatusReceiver;
    private DateFormat logDateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_list);

        logDateFormat = DateFormat.getTimeInstance();

        buttonPlay = (Button) findViewById(R.id.play);
        buttonPlay.setOnClickListener(this);

        buttonStopAndPlay = (Button) findViewById(R.id.stop_and_play);
        buttonStopAndPlay.setOnClickListener(this);

        buttonToggle = (Button) findViewById(R.id.toggle);
        buttonToggle.setOnClickListener(this);

        buttonStop = (Button) findViewById(R.id.stop);
        buttonStop.setOnClickListener(this);

        buttonSeekTo1Minute = (Button) findViewById(R.id.seekTo1minute);
        buttonSeekTo1Minute.setOnClickListener(this);

        buttonStartLoop = (Button) findViewById(R.id.start_loop);
        buttonStartLoop.setOnClickListener(this);

        audioList = (ListView) findViewById(R.id.audio_list);
        audioList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, URLS));
        audioList.setItemChecked(0, true);
        selectedAudioUrl = URLS[0];

        audioList.setSelector(R.drawable.list_item_background);
        audioList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectedAudioUrl = URLS[i];
            }
        });

        eventList = (ListView) findViewById(R.id.event_list);
        eventListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        eventList.setAdapter(eventListAdapter);

        MediaPlayerService.setDataProvider(DemoApplication.multiDataProvider);
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
        if (v == buttonPlay) {
            sendPlayAction(selectedAudioUrl);
        } else if (v == buttonStopAndPlay) {
            sendStopAction();
            sendPlayAction(selectedAudioUrl);
        } else if (v == buttonToggle) {
            sendToggleAction();
        } else if (v == buttonStop) {
            sendStopAction();
        } else if (v == buttonStartLoop) {
            if (!startLoopRunning) {
                startStartLoop();
                buttonStartLoop.setBackgroundResource(android.R.color.holo_purple);
            } else {
                stopStartLoop();
                buttonStartLoop.setBackground(null);
            }
        } else if (v == buttonSeekTo1Minute) {
            Intent intent = new Intent(this, MediaPlayerService.class);
            intent.setAction(MediaPlayerService.ACTION_SEEK);
            intent.putExtra(MediaPlayerService.ARG_POSITION, 60000);
            startService(intent);
        }
    }

    private void stopStartLoop() {
        startLoopRunning = false;
    }

    private void startStartLoop() {
        new Thread() {
            public void run() {
                startLoopRunning = true;
                int idx = 0;
                Random random = new Random();
                while (startLoopRunning) {
                    Intent intentStart = new Intent(AudioListActivity.this, MediaPlayerService.class);
                    intentStart.setAction(MediaPlayerService.ACTION_PLAY);
                    intentStart.putExtra(MediaPlayerService.ARG_MEDIA_IDENTIFIER, URLS[idx]);
                    startService(intentStart);

                    try {
                        Thread.sleep(100 * Math.max(0, random.nextInt(40)));
                    } catch (InterruptedException e) {
                    }

                    ++idx;
                    idx %= URLS.length;
                }
            }
        }.start();
    }

    private void sendToggleAction() {
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(MediaPlayerService.ACTION_TOGGLE_PLAYBACK);
        startService(intent);
    }

    private void sendPlayAction(String audioUrl) {
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(MediaPlayerService.ACTION_PLAY);

        intent.putExtra(MediaPlayerService.ARG_MEDIA_IDENTIFIER, audioUrl);
        startService(intent);
    }

    private void sendStopAction() {
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(MediaPlayerService.ACTION_STOP);
        startService(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        audioStatusReceiver.unregister(this);
        stopStartLoop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        audioStatusReceiver = new AudioStatusReceiver();
        audioStatusReceiver.register(this);
        audioStatusReceiver.requestBroadcastStatus(this);
    }


    private class AudioStatusReceiver extends BroadcastReceiver {
        public void register(Context context) {
            Log.d("Demo", "registering broadcast receiver");
            LocalBroadcastManager.getInstance(context).registerReceiver(this, new IntentFilter(MediaPlayerService.ACTION_BROADCAST_STATUS));
        }

        public void unregister(Context context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra(MediaPlayerService.ACTION_BROADCAST_STATUS_BUNDLE);
            if (bundle != null) {
                logAudioBundle(bundle);
            }
        }

        public void requestBroadcastStatus(Context context) {
            Intent intent = new Intent(context, MediaPlayerService.class);
            intent.setAction(MediaPlayerService.ACTION_BROADCAST_STATUS);
            context.startService(intent);
        }
    }

    private void logAudioBundle(Bundle bundle) {
        String state = bundle.getString(MediaPlayerService.KEY_STATE);
        int flags = bundle.getInt(MediaPlayerService.KEY_FLAGS);
        String uri = bundle.getString(MediaPlayerService.KEY_MEDIA_IDENTIFIER);
        long position = bundle.getLong(MediaPlayerService.KEY_POSITION, -1);

        String log = String.format("%s %s %d %d %s", logDateFormat.format(new Date()), state, flags, position, uri);

        eventListAdapter.insert(log, 0);
    }
}
