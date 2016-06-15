package ch.srg.mediaplayer;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ch.srg.mediaplayer.demo.DemoApplication;
import ch.srg.mediaplayer.demo.R;
import ch.srg.mediaplayer.extras.dataproviders.MultiDataProvider;
import ch.srg.mediaplayer.extras.fullscreen.helper.SystemUiHelper;
import ch.srg.mediaplayer.extras.overlay.error.SimpleErrorMessage;
import ch.srg.mediaplayer.internal.exoplayer.ExoPlayerDelegate;
import ch.srg.mediaplayer.internal.nativeplayer.NativePlayerDelegate;
import ch.srg.segmentoverlay.controller.SegmentController;
import ch.srg.segmentoverlay.model.Segment;
import ch.srg.segmentoverlay.view.PlayerControlView;
import ch.srg.segmentoverlay.view.SegmentView;
import ch.srg.view.LivePlayerControlView;

public class DemoMediaPlayerActivity extends AppCompatActivity implements
        SRGMediaPlayerController.Listener, View.OnClickListener {
    public static final String ARG_LIVE = "live";

    /**
     * This List URL can be changed on http://pastebin.com/UdDn1Jp2 with SpaTeam / spateam00 account.
     */
    public static final String LIST_URL = "http://pastebin.com/raw.php?i=UdDn1Jp2";
    public static final String FRAGMENT_TAG = "media";
    private static final String VIDEO_LIST = "ch.srg.mediaplayer.demo.video.list";

    public static final String PLAYER_TAG = "main";
    private static final String TAG = "DemoSegment";
    private static final double VOLUME_INCREMENT = 0.1;


    private SRGMediaPlayerController srgMediaPlayer;

    private SRGMediaPlayerView playerView;

    @Nullable
    private SegmentView segmentView;
    @Nullable
    private PlayerControlView segmentPlayerControlView;
    @Nullable
    private LivePlayerControlView livePlayerControlView;

    private DemoSegmentAdapter adapter;

    private Toolbar toolbar;

    private SegmentController segmentController;
    private TextView statusLabel;
    private SimpleErrorMessage errorMessage;
    private MultiDataProvider dataProvider;
    private List<Segment> segments;
    private ListView identifierListView;
    private List<String> identifierList;
    private List<String> commentedIdentifierList;

    private SystemUiHelper uiHelper;

    private MediaPlayerFragment mediaPlayerFragment;
    private int toastCount;
    private int orientation;

    private Button swapPlayerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            commentedIdentifierList = savedInstanceState.getStringArrayList(VIDEO_LIST);
        }

        if (getIntent().getBooleanExtra(ARG_LIVE, false)) {
            setContentView(R.layout.activity_demo_live_media_player);
            setTitle("DEMO Live");
        } else {
            setContentView(R.layout.activity_demo_segment_media_player);
            setTitle("DEMO Segment");
        }

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        playerView = (SRGMediaPlayerView) findViewById(R.id.demo_video_container);

        errorMessage = (SimpleErrorMessage) findViewById(R.id.error_message);
        statusLabel = (TextView) findViewById(R.id.status_label);
        swapPlayerButton = (Button) findViewById(R.id.button_swap_player);

        if (swapPlayerButton != null) {
            swapPlayerButton.setOnClickListener(this);
        }
        View mediaControl = findViewById(R.id.media_control);
        if (mediaControl instanceof PlayerControlView) {
            segmentPlayerControlView = (PlayerControlView) mediaControl;
        } else if (mediaControl instanceof LivePlayerControlView) {
            livePlayerControlView = (LivePlayerControlView) mediaControl;
        }

        int[] buttonIds = {
                R.id.button_seek_window_start,
                R.id.button_seek_one_hour,
                R.id.button_seek_half_hour,
                R.id.button_seek_one_minute,
                R.id.button_seek_live,
                R.id.button_quality_adptative,
                R.id.button_quality_hd,
                R.id.button_quality_sd
        };
        for (int buttonId : buttonIds) {
            View button = findViewById(buttonId);
            if (button != null) {
                button.setOnClickListener(this);
            }
        }

        dataProvider = DemoApplication.multiDataProvider;

        mediaPlayerFragment = (MediaPlayerFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (mediaPlayerFragment == null) {
            mediaPlayerFragment = new MediaPlayerFragment();
            mediaPlayerFragment.mediaPlayer = createPlayer();
            getFragmentManager().beginTransaction().add(mediaPlayerFragment, FRAGMENT_TAG).commit();
        } else {
            if (mediaPlayerFragment.mediaPlayer == null) {
                mediaPlayerFragment.mediaPlayer = createPlayer();
            } else {
                srgMediaPlayer = mediaPlayerFragment.mediaPlayer;
            }
        }

        if (livePlayerControlView != null) {
            livePlayerControlView.setPlayerController(srgMediaPlayer);
        }

        errorMessage.attachToController(srgMediaPlayer);
        srgMediaPlayer.registerEventListener(this);

        adapter = new DemoSegmentAdapter(this, new ArrayList<Segment>());

        segmentView = (SegmentView) findViewById(R.id.segment_demo_view);

        segmentController = new SegmentController(srgMediaPlayer);
        if (segmentView != null) {
            segmentView.setBaseAdapter(adapter);
            segmentView.setSegmentController(segmentController);
            segmentView.attachToController(srgMediaPlayer);
        }
        if (segmentPlayerControlView != null) {
            segmentPlayerControlView.attachToController(srgMediaPlayer);
            segmentPlayerControlView.setSegmentController(segmentController);
        }
        adapter.addSegmentClickListener(segmentController);

        if (segments != null && !segments.isEmpty()) {
            segmentController.setSegmentList(segments);
        }

        identifierListView = (ListView) findViewById(R.id.uri_list);
        identifierListView.setSelector(R.drawable.list_item_background);
        identifierListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                playTestIdentifier(getTestListItem(i));
            }
        });


        if (commentedIdentifierList == null || commentedIdentifierList.isEmpty()) {
            setIdentifierList(Arrays.asList("dummy:SPECIMEN"));
            loadIdentifiers();
        } else {
            setIdentifierList(commentedIdentifierList);
        }
        // TODO Add periodic listener (or equivalent)


        orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            //code for portrait mode
            uiHelper = null;
        } else {
            //code for landscape mode
            uiHelper = new SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY);
        }
    }

    private SRGMediaPlayerController createPlayer() {
        srgMediaPlayer = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
        srgMediaPlayer.setPlayerDelegateFactory(((DemoApplication) getApplication()).getPlayerDelegateFactory());
        srgMediaPlayer.setDebugMode(true);
        return srgMediaPlayer;
    }

    public String getTestListItem(int i) {
        return identifierList.get(i);
    }

    public void playTestIdentifier(String identifier) {
        try {
            String SEEK_DELIMITER = "@seek:";
            if (identifier.contains(SEEK_DELIMITER)) {
                Long time = Long.parseLong(identifier.substring(identifier.indexOf(SEEK_DELIMITER) + SEEK_DELIMITER.length())) * 1000;
                srgMediaPlayer.play(identifier, time);
            } else {
                srgMediaPlayer.play(identifier);
            }
        } catch (SRGMediaPlayerException e) {
            Log.e(TAG, "play " + identifier, e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        srgMediaPlayer.bindToMediaPlayerView(playerView);

        segmentController.startListening();
        if (livePlayerControlView != null) {
            livePlayerControlView.startListening();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.demo_segment_media_player_activity, menu);
        return true;
    }

    private void loadIdentifiers() {
        new AsyncTask<String, Void, List<String>>() {

            @Override
            protected List<String> doInBackground(String... params) {
                return fetch(params[0], new URLConnectionProcessor<List<String>>() {
                    @Override
                    public void onSetupHttpURLConnection(HttpURLConnection urlConnection) throws IOException {

                    }

                    @Override
                    public List<String> onHttpURLConnectionSuccess(InputStreamReader in) throws IOException {
                        List<String> urls = new ArrayList<String>();
                        BufferedReader reader = new BufferedReader(in);
                        for (; ; ) {
                            String line = reader.readLine();
                            if (line != null) {
                                if (dataProvider.isSupported(line.split(" ")[0])) {
                                    urls.add(line);
                                }
                            } else {
                                break;
                            }
                        }

                        return urls;
                    }

                    @Override
                    public List<String> onHttpURLConnectionError(int httpCode, Exception e) {
                        return null;
                    }
                });
            }

            @Override
            protected void onPostExecute(List<String> identifiers) {
                if (identifiers != null && identifiers.size() > 0) {
                    commentedIdentifierList = identifiers;
                    setIdentifierList(identifiers);
                }
            }
        }.execute(LIST_URL);
    }

    @Override
    public void onClick(View v) {
        long duration = srgMediaPlayer.getMediaDuration();
        switch (v.getId()) {
            case R.id.button_seek_window_start:
                srgMediaPlayer.seekTo(1);
                break;
            case R.id.button_seek_one_hour:
                srgMediaPlayer.seekTo(duration - 1000 * 3600);
                break;
            case R.id.button_seek_half_hour:
                srgMediaPlayer.seekTo(duration - 1000 * 1800);
                break;
            case R.id.button_seek_one_minute:
                srgMediaPlayer.seekTo(duration - 1000 * 60);
                break;
            case R.id.button_seek_live:
                srgMediaPlayer.seekTo(0);
                break;
            case R.id.button_swap_player:
                swapPlayer();
                break;
            case R.id.button_quality_adptative:
                srgMediaPlayer.setQualityOverride(null);
                break;
            case R.id.button_quality_hd:
                srgMediaPlayer.setQualityOverride(Long.MAX_VALUE);
                break;
            case R.id.button_quality_sd:
                srgMediaPlayer.setQualityOverride(0L);
                break;
        }
    }

    private void swapPlayer() {
        PlayerDelegate playerDelegate = srgMediaPlayer.getPlayerDelegate();
        PlayerDelegate newPlayerDelegate;
        String buttonText;
        if (playerDelegate instanceof ExoPlayerDelegate) {
            newPlayerDelegate = new NativePlayerDelegate(srgMediaPlayer);
            buttonText = "ExoPlayer";
        } else {
            newPlayerDelegate = DemoApplication.createExoPlayerDelegate(this, srgMediaPlayer, ExoPlayerDelegate.SourceType.HLS);
            buttonText = "NativePlayer";
        }
        srgMediaPlayer.swapPlayerDelegate(newPlayerDelegate);
        if (swapPlayerButton != null) {
            swapPlayerButton.setText(buttonText);
        }
    }

    public interface URLConnectionProcessor<T> {
        /**
         * Called with a valid HttpURLConnection before the getResponseCode call.
         *
         * @param urlConnection
         */
        void onSetupHttpURLConnection(HttpURLConnection urlConnection) throws IOException;

        T onHttpURLConnectionSuccess(InputStreamReader in) throws IOException;

        T onHttpURLConnectionError(int httpCode, Exception e);

    }

    private
    @Nullable
    <T> T fetch(String url,
                @NonNull URLConnectionProcessor<T> processor) {
        HttpURLConnection httpURLConnection = null;

        try {
            httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

            processor.onSetupHttpURLConnection(httpURLConnection);
            Log.d(TAG, "Fetching " + url);

            int responseCode = httpURLConnection.getResponseCode();

            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_NOT_AUTHORITATIVE:
                    InputStream inputStream = httpURLConnection.getInputStream();

                    InputStreamReader in = new InputStreamReader(inputStream, "UTF-8");
                    return processor.onHttpURLConnectionSuccess(in);
                default:
                    Log.e(TAG, String.format("%d HTTP error: %d for %s",
                            httpURLConnection.hashCode(), responseCode, url));
                    return processor.onHttpURLConnectionError(responseCode, null);
            }
        } catch (IOException e) {
            Log.e(TAG, "IO error for " + url + " " + e.toString());
            return processor.onHttpURLConnectionError(0, e);
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    private void setIdentifierList(List<String> commentedIdentifiers) {
        ArrayList<String> uncommentedIdentifiers = new ArrayList<>(commentedIdentifiers.size());
        for (String i : commentedIdentifiers) {
            uncommentedIdentifiers.add(i.split(" ")[0]);
        }
        this.identifierList = uncommentedIdentifiers;
        identifierListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, commentedIdentifiers));
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (statusLabel != null) {
            if (mp == null) {
                statusLabel.setText(String.format("Event %s", event));
            } else {
                statusLabel.setText(String.format("Event %s, %s/%s, definition=%dp", event, stringForTime(mp.getMediaPosition()), stringForTime(mp.getMediaDuration()), mp.getVideoSourceHeight()));
            }
        }
        switch (event.type) {
            case MEDIA_READY_TO_PLAY:
                segments = dataProvider.getSegments(mp.getMediaIdentifier());
                if (segments != null && !segments.isEmpty() && segmentView != null) {
                    segmentController.setSegmentList(segments);
                    segmentView.setVisibility(View.VISIBLE);
                } else if(segments != null && segmentView != null && segments.isEmpty()){
                    segmentView.setVisibility(View.GONE);
                }
                break;
            case EXTERNAL_EVENT:
                if (event instanceof SegmentController.Event) {
                    SegmentController.Event.Type segmentEventType =
                            ((SegmentController.Event) event).segmentEventType;
                    switch (segmentEventType) {
                        case SEGMENT_SKIPPED_BLOCKED:
                        case SEGMENT_USER_SEEK_BLOCKED:
                            toastCount++;
                            Toast toast = Toast.makeText(this, segmentEventType.toString() + " / " + ((SegmentController.Event) event).blockingReason + " / " + toastCount, Toast.LENGTH_SHORT);
                            toast.show();
                            break;
                    }
                }
                break;

            case OVERLAY_CONTROL_DISPLAYED:
                if (uiHelper != null) {
                    uiHelper.show();
                }
                break;

            case OVERLAY_CONTROL_HIDDEN:
                if (uiHelper != null) {
                    uiHelper.hide();
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        srgMediaPlayer.unbindFromMediaPlayerView(playerView);
        segmentController.stopListening();

        if (livePlayerControlView != null) {
            livePlayerControlView.stopListening();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        adapter.removeSegmentClickListener(segmentController);
        super.onStop();
    }

    protected String stringForTime(long millis) {
        int totalSeconds = (int) millis / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (commentedIdentifierList != null) {
            outState.putStringArrayList(VIDEO_LIST, new ArrayList<>(commentedIdentifierList));
        }
    }

    public boolean isTestListLoaded() {
        return commentedIdentifierList != null;
    }

    public static class MediaPlayerFragment extends Fragment {

        public SRGMediaPlayerController mediaPlayer;

        public MediaPlayerFragment() {
            this.setRetainInstance(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mediaPlayer.release();
        }
    }

    public SRGMediaPlayerController getSrgMediaPlayer() {
        return srgMediaPlayer;
    }

    public SRGMediaPlayerView getPlayerView() {
        return playerView;
    }

    public SegmentController getSegmentController() {
        return segmentController;
    }

    public MultiDataProvider getDataProvider() {
        return dataProvider;
    }

}
