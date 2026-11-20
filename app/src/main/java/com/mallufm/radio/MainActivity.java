package com.mallufm.radio;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String CHANNELS_JSON_URL =
            "https://raw.githubusercontent.com/archanaammu1556-pixel/shopzy-local-apk-/main/channels.json";

    private static class Channel {
        String name;
        String url;
        Channel(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private final List<Channel> channels = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private LinearLayout channelListLayout;
    private int currentIndex = -1;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);
        root.setBackgroundColor(Color.parseColor("#10141C"));

        TextView title = new TextView(this);
        title.setText("Mallu FM");
        title.setTextSize(26f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("Loading channels...");
        statusText.setTextSize(15f);
        statusText.setTextColor(Color.parseColor("#A0AEC0"));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 16, 0, 24);
        root.addView(statusText);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button playPauseBtn = new Button(this);
        playPauseBtn.setText("Play / Stop");
        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    stopPlayback();
                } else if (currentIndex >= 0 && currentIndex < channels.size()) {
                    playChannel(currentIndex);
                } else if (!channels.isEmpty()) {
                    playChannel(0);
                } else {
                    fetchChannelsAsync(true);
                }
            }
        });
        controls.addView(playPauseBtn);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("Refresh Links");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchChannelsAsync(false);
            }
        });
        controls.addView(refreshBtn);

        root.addView(controls);

        ScrollView scrollView = new ScrollView(this);
        channelListLayout = new LinearLayout(this);
        channelListLayout.setOrientation(LinearLayout.VERTICAL);
        channelListLayout.setPadding(0, 24, 0, 0);
        scrollView.addView(channelListLayout);
        root.addView(scrollView);

        setContentView(root);
        loadDefaultChannels();
        renderChannelButtons();
        fetchChannelsAsync(false);
    }

    private void loadDefaultChannels() {
        channels.clear();
        channels.add(new Channel("Sample MP3 Stream 1", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service"));
        channels.add(new Channel("Sample Stream 2", "https://ice1.somafm.com/groovesalad-128-mp3"));
    }

    private void fetchChannelsAsync(final boolean autoPlayFirst) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cacheBuster = CHANNELS_JSON_URL + (CHANNELS_JSON_URL.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                    HttpURLConnection conn = (HttpURLConnection) new URL(cacheBuster).openConnection();
                    conn.setConnectTimeout(7000);
                    conn.setReadTimeout(7000);
                    conn.setRequestProperty("Cache-Control", "no-cache");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    conn.disconnect();

                    JSONObject obj = new JSONObject(sb.toString());
                    JSONArray arr = obj.getJSONArray("channels");
                    final List<Channel> fetched = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String name = item.optString("name", "Channel " + (i + 1));
                        String url = item.optString("url", "");
                        if (!url.isEmpty()) {
                            fetched.add(new Channel(name, url));
                        }
                    }

                    if (!fetched.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                channels.clear();
                                channels.addAll(fetched);
                                renderChannelButtons();
                                statusText.setText("Channels updated (" + channels.size() + ")");
                                if (autoPlayFirst) {
                                    playChannel(0);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Using saved/default list");
                            if (autoPlayFirst && !channels.isEmpty()) {
                                playChannel(0);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void renderChannelButtons() {
        channelListLayout.removeAllViews();
        for (int i = 0; i < channels.size(); i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setText(channels.get(i).name);
            btn.setAllCaps(false);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playChannel(idx);
                }
            });
            channelListLayout.addView(btn);
        }
    }

    private void playChannel(int index) {
        if (index < 0 || index >= channels.size()) return;
        currentIndex = index;
        final Channel ch = channels.get(index);
        statusText.setText("Buffering: " + ch.name);
        stopPlayback();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(ch.url);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    isPlaying = true;
                    statusText.setText("Playing: " + ch.name);
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    statusText.setText("Stream error, refreshing list...");
                    stopPlayback();
                    fetchChannelsAsync(false);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            statusText.setText("Error playing " + ch.name + ", refreshing...");
            stopPlayback();
            fetchChannelsAsync(false);
        }
    }

    private void stopPlayback() {
        isPlaying = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        if (currentIndex >= 0 && currentIndex < channels.size()) {
            statusText.setText("Stopped: " + channels.get(currentIndex).name);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }
}
