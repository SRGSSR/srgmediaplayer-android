package ch.srg.mediaplayer;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

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
import ch.srg.mediaplayer.internal.PlayerDelegateFactory;
import ch.srg.mediaplayer.internal.cast.GoogleCastDelegate;
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

    //Cast objects
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MyMediaRouterCallback mMediaRouterCallback;
    private GoogleApiClient mApiClient;
    private boolean mWaitingForReconnect;
    private boolean mApplicationStarted;
    private CastDevice mSelectedDevice;
    private String mSessionId;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private RemoteMediaPlayer mRemoteMediaPlayer;

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

        initGoogleCast();

        playerView = (SRGMediaPlayerView) findViewById(R.id.demo_video_container);

        errorMessage = (SimpleErrorMessage) findViewById(R.id.error_message);
        statusLabel = (TextView) findViewById(R.id.status_label);

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
                R.id.button_seek_live
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
            srgMediaPlayer = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
            srgMediaPlayer.setDebugMode(true);

            mediaPlayerFragment = new MediaPlayerFragment();
            mediaPlayerFragment.mediaPlayer = srgMediaPlayer;
            getFragmentManager().beginTransaction().add(mediaPlayerFragment, FRAGMENT_TAG).commit();
        } else {
            if (mediaPlayerFragment.mediaPlayer == null) {
                srgMediaPlayer = new SRGMediaPlayerController(this, dataProvider, PLAYER_TAG);
                srgMediaPlayer.setDebugMode(true);
                mediaPlayerFragment.mediaPlayer = srgMediaPlayer;
            } else {
                srgMediaPlayer = mediaPlayerFragment.mediaPlayer;
            }
        }

        srgMediaPlayer.bindToMediaPlayerView(playerView);

        if (livePlayerControlView != null) {
            livePlayerControlView.setPlayerController(srgMediaPlayer);
        }

        errorMessage.attachToController(srgMediaPlayer);
        srgMediaPlayer.registerEventListener(this);

        adapter = new DemoSegmentAdapter(this, new ArrayList<Segment>());

        segmentView = (SegmentView) findViewById(R.id.segment_demo_view);

        segmentController = new SegmentController(this, srgMediaPlayer);
        if (segmentView != null) {
            segmentView.setBaseAdapter(adapter);
            segmentView.setSegmentController(segmentController);
            segmentView.attachToController(srgMediaPlayer);
        }
        if (segmentPlayerControlView != null) {
            segmentPlayerControlView.attachToController(srgMediaPlayer);
            segmentPlayerControlView.setSegmentController(segmentController);
        }
        adapter.setSegmentListListener(segmentController);

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

    private void initGoogleCast() {
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();

        mMediaRouterCallback = new MyMediaRouterCallback();
        mConnectionCallbacks = new ConnectionCallbacks();
        mConnectionFailedListener = new ConnectionFailedListener();
    }

    public String getTestListItem(int i) {
        return identifierList.get(i);
    }

    public void playTestIdentifier(String identifier) {
        if (mApiClient != null && mRemoteMediaPlayer != null && mApplicationStarted) {
            //playMediaOnGoogleCast(identifier, 0);

            Log.d(TAG, "Create new Google cast delegate");
            final GoogleCastDelegate googleCatDelegate = new GoogleCastDelegate(mSessionId, mApiClient, srgMediaPlayer);
            srgMediaPlayer.setPlayerDelegateFactory(new PlayerDelegateFactory() {
                @Override
                public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
                    return googleCatDelegate;
                }
            });
            segmentPlayerControlView.attachToController(srgMediaPlayer);
            try {
                srgMediaPlayer.play(identifier);
            } catch (SRGMediaPlayerException e) {
                e.printStackTrace();
            }
        } else {
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
    }

    private void playMediaOnGoogleCast(String identifier, long position) {
        String uri = null;
        try {
            uri = String.valueOf(dataProvider.getUri(identifier));
        } catch (SRGMediaPlayerException e) {
            e.printStackTrace();
        }
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, identifier);
        MediaInfo mediaInfo = new MediaInfo.Builder(
                uri)
                .setContentType("application/vnd.apple.mpegurl")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();

        if (!TextUtils.isEmpty(uri)) {
            try {
                mRemoteMediaPlayer.load(mApiClient, mediaInfo, true, position);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Problem occurred with media during loading", e);
            } catch (Exception e) {
                Log.e(TAG, "Problem opening media during loading", e);
            }
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
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
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
                srgMediaPlayer.seekTo(duration);
                break;
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
                segmentController.setSegmentList(segments);
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
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onPause() {
        srgMediaPlayer.unbindFromMediaPlayerView();
        segmentController.stopListening();

        if (livePlayerControlView != null) {
            livePlayerControlView.stopListening();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
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


    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());
            String routeId = info.getId();

            Log.d(TAG, routeId);

            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mSelectedDevice, mCastClientListener).setVerboseLoggingEnabled(true);

            mApiClient = new GoogleApiClient.Builder(DemoMediaPlayerActivity.this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            teardown();
            mSelectedDevice = null;
        }
    }

    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                Log.e(TAG, "Need to reconnect channels");
                //reconnectChannels();
            } else {
                try {
                    Cast.CastApi.launchApplication(mApiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata =
                                                        result.getApplicationMetadata();
                                                mSessionId = result.getSessionId();
                                                String applicationStatus = result.getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();

                                                Log.d(TAG, "applicationMetadata: " + String.valueOf(applicationMetadata));
                                                Log.d(TAG, "sessionId: " + mSessionId);
                                                Log.d(TAG, "applicationStatus: " + applicationStatus);
                                                Log.d(TAG, "wasLaunched: " + String.valueOf(wasLaunched));

                                                mApplicationStarted = true;

                                                mRemoteMediaPlayer = new RemoteMediaPlayer();

                                                String mediaIdentifier = srgMediaPlayer.getMediaIdentifier();
                                                long mediaPosition = srgMediaPlayer.getMediaPosition();

                                                if (srgMediaPlayer.isPlaying()) {
                                                    Log.d(TAG, "Create new Google cast delegate");
                                                    final GoogleCastDelegate googleCatDelegate = new GoogleCastDelegate(mSessionId, mApiClient, srgMediaPlayer);
                                                    //srgMediaPlayer.setPlayerDelegateFactory();
                                                    PlayerDelegateFactory playerDelegateFactory = new PlayerDelegateFactory() {
                                                        @Override
                                                        public PlayerDelegate getDelegateForMediaIdentifier(PlayerDelegate.OnPlayerDelegateListener srgMediaPlayer, String mediaIdentifier) {
                                                            return googleCatDelegate;
                                                        }
                                                    };
                                                    srgMediaPlayer.setPlayerDelegateFactory(playerDelegateFactory);
                                                    try {
                                                        srgMediaPlayer.play(mediaIdentifier, mediaPosition);
                                                    } catch (SRGMediaPlayerException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                            } else {
                                                teardown();
                                            }
                                        }
                                    }

                            );

                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch application", e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            mWaitingForReconnect = true;
        }
    }

    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            teardown();
        }
    }

    Cast.Listener mCastClientListener = new Cast.Listener() {
        @Override
        public void onApplicationStatusChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onApplicationStatusChanged: "
                        + Cast.CastApi.getApplicationStatus(mApiClient));
            }
        }

        @Override
        public void onVolumeChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int errorCode) {
            teardown();
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume < 1.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient,
                                        Math.min(currentVolume + VOLUME_INCREMENT, 1.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume up");
                        return super.dispatchKeyEvent(event);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume > 0.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient,
                                        Math.max(currentVolume - VOLUME_INCREMENT, 0.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume down");
                        return super.dispatchKeyEvent(event);
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }

}
