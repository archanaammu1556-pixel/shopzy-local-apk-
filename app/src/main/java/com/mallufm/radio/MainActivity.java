package com.mallufm.radio;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
        float freq;
        Channel(String name, String url, float freq) {
            this.name = name;
            this.url = url;
            this.freq = freq;
        }
    }

    private final List<Channel> channels = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private Equalizer equalizer;
    private TunerScaleView scaleView;
    private TunerKnobView knobView;
    private TunerKnobView bassKnob;
    private TunerKnobView midKnob;
    private TunerKnobView trebleKnob;
    private TextView freqText;
    private TextView channelNameText;
    private TextView statusText;
    private TextView volumeText;
    private TextView eqStatusText;
    private TextView bassValText;
    private TextView midValText;
    private TextView trebleValText;
    private LinearLayout presetLayout;
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private float volumeLevel = 0.8f;

    // Normalized -100 to +100 EQ controls
    private int bassLevel = 25;
    private int midLevel = 0;
    private int trebleLevel = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#05080D"));
        root.setWeightSum(1f);

        // Top Tuner Scale & Frequency Display
        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setBackgroundColor(Color.parseColor("#08131E"));
        topPanel.setPadding(16, 14, 16, 8);

        statusText = new TextView(this);
        statusText.setText("Loading Malayalam FM...");
        statusText.setTextSize(11f);
        statusText.setTextColor(Color.parseColor("#7DD3FC"));
        statusText.setGravity(Gravity.CENTER);
        topPanel.addView(statusText);

        scaleView = new TunerScaleView(this);
        LinearLayout.LayoutParams scaleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        scaleParams.topMargin = dp(3);
        topPanel.addView(scaleView, scaleParams);

        freqText = new TextView(this);
        freqText.setText("98.1 MHz");
        freqText.setTextSize(28f);
        freqText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        freqText.setTextColor(Color.parseColor("#E0F2FE"));
        freqText.setGravity(Gravity.CENTER);
        topPanel.addView(freqText);

        channelNameText = new TextView(this);
        channelNameText.setText("Mallu FM");
        channelNameText.setTextSize(14f);
        channelNameText.setTypeface(Typeface.DEFAULT_BOLD);
        channelNameText.setTextColor(Color.parseColor("#38BDF8"));
        channelNameText.setGravity(Gravity.CENTER);
        topPanel.addView(channelNameText);

        volumeText = new TextView(this);
        volumeText.setText("VOLUME: 80%  (Rotate Knob)");
        volumeText.setTextSize(11f);
        volumeText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        volumeText.setTextColor(Color.parseColor("#4ADE80"));
        volumeText.setGravity(Gravity.CENTER);
        volumeText.setPadding(0, dp(2), 0, 0);
        topPanel.addView(volumeText);

        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.30f);
        root.addView(topPanel, topParams);

        // Middle Rotary Volume Knob with Ambient Light & < > Tuning Buttons
        LinearLayout middlePanel = new LinearLayout(this);
        middlePanel.setOrientation(LinearLayout.VERTICAL);
        middlePanel.setGravity(Gravity.CENTER);
        middlePanel.setBackgroundColor(Color.parseColor("#040609"));
        middlePanel.setPadding(12, 4, 12, 4);

        LinearLayout dialRow = new LinearLayout(this);
        dialRow.setOrientation(LinearLayout.HORIZONTAL);
        dialRow.setGravity(Gravity.CENTER);

        Button prevBtn = createArrowButton("<");
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tuneStep(-1);
            }
        });
        dialRow.addView(prevBtn);

        knobView = new TunerKnobView(this, 8500L, 0f);
        LinearLayout.LayoutParams knobParams = new LinearLayout.LayoutParams(dp(146), dp(146));
        knobParams.leftMargin = dp(12);
        knobParams.rightMargin = dp(12);
        knobView.setOnRotateListener(new TunerKnobView.OnRotateListener() {
            @Override
            public void onRotateDelta(float deltaDegrees, float knobAngle) {
                float nextVol = volumeLevel + (deltaDegrees / 270f);
                setAppVolume(nextVol, knobAngle);
            }
        });
        dialRow.addView(knobView, knobParams);

        Button nextBtn = createArrowButton(">");
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tuneStep(1);
            }
        });
        dialRow.addView(nextBtn);

        middlePanel.addView(dialRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dp(3), 0, 0);

        Button playPauseBtn = createActionButton("PLAY / STOP");
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
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.rightMargin = dp(10);
        actionRow.addView(playPauseBtn, playLp);

        Button refreshBtn = createActionButton("REFRESH");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchChannelsAsync(false);
            }
        });
        actionRow.addView(refreshBtn);

        middlePanel.addView(actionRow);

        LinearLayout.LayoutParams midParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.27f);
        root.addView(middlePanel, midParams);

        // Equalizer Section with Small Ambient Color Knobs (Bass / Mid / Treble)
        LinearLayout eqPanel = new LinearLayout(this);
        eqPanel.setOrientation(LinearLayout.VERTICAL);
        eqPanel.setBackgroundColor(Color.parseColor("#070C14"));
        eqPanel.setPadding(dp(10), dp(5), dp(10), dp(5));

        LinearLayout eqHeader = new LinearLayout(this);
        eqHeader.setOrientation(LinearLayout.HORIZONTAL);
        eqHeader.setGravity(Gravity.CENTER_VERTICAL);

        eqStatusText = new TextView(this);
        eqStatusText.setText("EQ KNOBS");
        eqStatusText.setTextSize(10f);
        eqStatusText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        eqStatusText.setTextColor(Color.parseColor("#FBBF24"));
        LinearLayout.LayoutParams hdrLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        eqHeader.addView(eqStatusText, hdrLp);

        Button flatBtn = createEqPresetButton("FLAT");
        flatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyEqPreset(0, 0, 0, "FLAT");
            }
        });
        eqHeader.addView(flatBtn);

        Button bassBoostBtn = createEqPresetButton("BASS+");
        bassBoostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyEqPreset(75, 5, 20, "BASS+");
            }
        });
        eqHeader.addView(bassBoostBtn);

        Button vocalBtn = createEqPresetButton("VOCAL");
        vocalBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyEqPreset(-10, 60, 35, "VOCAL");
            }
        });
        eqHeader.addView(vocalBtn);

        eqPanel.addView(eqHeader);

        LinearLayout knobsRow = new LinearLayout(this);
        knobsRow.setOrientation(LinearLayout.HORIZONTAL);
        knobsRow.setGravity(Gravity.CENTER);
        knobsRow.setPadding(0, dp(2), 0, 0);

        bassValText = new TextView(this);
        bassKnob = new TunerKnobView(this, 6500L, 0f);
        bassKnob.setOnRotateListener(new TunerKnobView.OnRotateListener() {
            @Override
            public void onRotateDelta(float deltaDegrees, float knobAngle) {
                bassLevel = Math.max(-100, Math.min(100, bassLevel + Math.round(deltaDegrees / 1.35f)));
                syncEqKnobsAndLabels("CUSTOM");
                applyEqualizerSettings();
            }
        });
        knobsRow.addView(createSmallEqKnobColumn("BASS", bassKnob, bassValText));

        midValText = new TextView(this);
        midKnob = new TunerKnobView(this, 7500L, 120f);
        midKnob.setOnRotateListener(new TunerKnobView.OnRotateListener() {
            @Override
            public void onRotateDelta(float deltaDegrees, float knobAngle) {
                midLevel = Math.max(-100, Math.min(100, midLevel + Math.round(deltaDegrees / 1.35f)));
                syncEqKnobsAndLabels("CUSTOM");
                applyEqualizerSettings();
            }
        });
        knobsRow.addView(createSmallEqKnobColumn("MID", midKnob, midValText));

        trebleValText = new TextView(this);
        trebleKnob = new TunerKnobView(this, 9500L, 240f);
        trebleKnob.setOnRotateListener(new TunerKnobView.OnRotateListener() {
            @Override
            public void onRotateDelta(float deltaDegrees, float knobAngle) {
                trebleLevel = Math.max(-100, Math.min(100, trebleLevel + Math.round(deltaDegrees / 1.35f)));
                syncEqKnobsAndLabels("CUSTOM");
                applyEqualizerSettings();
            }
        });
        knobsRow.addView(createSmallEqKnobColumn("TREBLE", trebleKnob, trebleValText));

        eqPanel.addView(knobsRow);

        LinearLayout.LayoutParams eqParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.29f);
        root.addView(eqPanel, eqParams);

        // Bottom Channel Preset Bar
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setBackgroundColor(Color.parseColor("#0B0E14"));
        presetScroll.setFillViewport(true);

        presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setGravity(Gravity.CENTER_VERTICAL);
        presetLayout.setPadding(dp(8), dp(6), dp(8), dp(6));
        presetScroll.addView(presetLayout);

        LinearLayout.LayoutParams botParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.14f);
        root.addView(presetScroll, botParams);

        setContentView(root);
        syncEqKnobsAndLabels("BASS+");
        loadDefaultChannels();
        renderChannelButtons();
        updateTunerDisplay(-1);
        setAppVolume(volumeLevel, (volumeLevel - 0.5f) * 270f);
        fetchChannelsAsync(false);
    }

    private LinearLayout createSmallEqKnobColumn(String title, TunerKnobView knob, TextView valueView) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(colLp);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(9f);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#CBD5E1"));
        titleView.setGravity(Gravity.CENTER);
        col.addView(titleView);

        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        kLp.topMargin = dp(1);
        kLp.bottomMargin = dp(1);
        col.addView(knob, kLp);

        valueView.setTextSize(9f);
        valueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valueView.setTextColor(Color.parseColor("#93C5FD"));
        valueView.setGravity(Gravity.CENTER);
        col.addView(valueView);

        return col;
    }

    private Button createEqPresetButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(9f);
        b.setAllCaps(true);
        b.setTextColor(Color.parseColor("#E2E8F0"));
        b.setBackgroundColor(Color.parseColor("#182232"));
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private void applyEqPreset(int bass, int mid, int treble, String modeName) {
        bassLevel = bass;
        midLevel = mid;
        trebleLevel = treble;
        syncEqKnobsAndLabels(modeName);
        applyEqualizerSettings();
    }

    private void syncEqKnobsAndLabels(String modeName) {
        if (bassKnob != null) bassKnob.setAngle((bassLevel / 100f) * 135f);
        if (midKnob != null) midKnob.setAngle((midLevel / 100f) * 135f);
        if (trebleKnob != null) trebleKnob.setAngle((trebleLevel / 100f) * 135f);

        if (bassValText != null) bassValText.setText((bassLevel > 0 ? "+" : "") + bassLevel);
        if (midValText != null) midValText.setText((midLevel > 0 ? "+" : "") + midLevel);
        if (trebleValText != null) trebleValText.setText((trebleLevel > 0 ? "+" : "") + trebleLevel);

        if (eqStatusText != null) {
            eqStatusText.setText("EQ KNOBS [" + modeName + "]");
        }
    }

    private void initEqualizerForPlayer(MediaPlayer mp) {
        releaseEqualizer();
        if (mp == null) return;
        try {
            equalizer = new Equalizer(0, mp.getAudioSessionId());
            equalizer.setEnabled(true);
            applyEqualizerSettings();
        } catch (Exception ignored) {
            equalizer = null;
        }
    }

    private void applyEqualizerSettings() {
        if (equalizer == null) return;
        try {
            short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            if (bands <= 0 || range == null || range.length < 2) return;
            int minLevel = range[0];
            int maxLevel = range[1];

            for (short b = 0; b < bands; b++) {
                float pos;
                if (bands == 1) {
                    pos = bassLevel / 100f;
                } else {
                    float ratio = (float) b / (float) (bands - 1);
                    if (ratio < 0.35f) {
                        pos = bassLevel / 100f;
                    } else if (ratio <= 0.70f) {
                        pos = midLevel / 100f;
                    } else {
                        pos = trebleLevel / 100f;
                    }
                }
                pos = Math.max(-1f, Math.min(1f, pos));
                short targetLevel;
                if (pos >= 0f) {
                    targetLevel = (short) Math.round(pos * maxLevel);
                } else {
                    targetLevel = (short) Math.round((-pos) * minLevel);
                }
                equalizer.setBandLevel(b, targetLevel);
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseEqualizer() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Exception ignored) {
            }
            equalizer = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button createArrowButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(24f);
        b.setTextColor(Color.parseColor("#CBD5E1"));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinimumWidth(dp(44));
        b.setMinimumHeight(dp(56));
        return b;
    }

    private Button createActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10f);
        b.setAllCaps(true);
        b.setTextColor(Color.parseColor("#E2E8F0"));
        b.setBackgroundColor(Color.parseColor("#18202C"));
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        return b;
    }

    private void setAppVolume(float level, float knobAngle) {
        volumeLevel = Math.max(0f, Math.min(1f, level));
        int pct = Math.round(volumeLevel * 100f);
        if (volumeText != null) {
            volumeText.setText(pct == 0 ? "VOLUME: MUTED (0%)" : ("VOLUME: " + pct + "%"));
            volumeText.setTextColor(pct == 0 ? Color.parseColor("#F87171") : Color.parseColor("#4ADE80"));
        }
        if (knobView != null) {
            knobView.setAngle(knobAngle);
        }
        applyVolumeToPlayer();
    }

    private void applyVolumeToPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(volumeLevel, volumeLevel);
            } catch (Exception ignored) {
            }
        }
    }

    private float computeFrequency(int index, int total) {
        if (total <= 1) return 98.1f;
        float start = 90.0f;
        float end = 107.9f;
        float raw = start + ((end - start) * index) / (total - 1);
        return Math.round(raw * 10f) / 10f;
    }

    private void tuneStep(int delta) {
        if (channels.isEmpty()) return;
        int base = currentIndex >= 0 ? currentIndex : 0;
        int next = (base + delta + channels.size()) % channels.size();
        playChannel(next);
    }

    private void updateTunerDisplay(int index) {
        if (channels.isEmpty()) {
            freqText.setText("98.1 MHz");
            channelNameText.setText("No Channels");
            scaleView.setFrequency(98.1f);
            return;
        }
        int idx = (index >= 0 && index < channels.size()) ? index : 0;
        Channel ch = channels.get(idx);
        freqText.setText(String.format("%.1f MHz", ch.freq));
        channelNameText.setText(ch.name);
        scaleView.setFrequency(ch.freq);
        highlightPresets(idx);
    }

    private void highlightPresets(int activeIdx) {
        if (presetLayout == null) return;
        for (int i = 0; i < presetLayout.getChildCount(); i++) {
            View child = presetLayout.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                if (i == activeIdx) {
                    btn.setTextColor(Color.parseColor("#38BDF8"));
                    btn.setBackgroundColor(Color.parseColor("#132235"));
                } else {
                    btn.setTextColor(Color.parseColor("#D1D5DB"));
                    btn.setBackgroundColor(Color.parseColor("#111620"));
                }
            }
        }
    }

    private void loadDefaultChannels() {
        channels.clear();
        channels.add(new Channel("Ahalia FM", "https://cast1.my-control-panel.com/proxy/ahaliafm/stream", 90.4f));
        channels.add(new Channel("Radio Digital Malayali", "https://radio.digitalmalayali.in/listen/stream/radio.mp3", 98.1f));
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
                            fetched.add(new Channel(name, url, 0f));
                        }
                    }
                    for (int i = 0; i < fetched.size(); i++) {
                        fetched.get(i).freq = computeFrequency(i, fetched.size());
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
                                } else {
                                    updateTunerDisplay(currentIndex >= 0 ? currentIndex : 0);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Using saved/default list");
                            updateTunerDisplay(currentIndex >= 0 ? currentIndex : 0);
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
        presetLayout.removeAllViews();
        for (int i = 0; i < channels.size(); i++) {
            final int idx = i;
            Channel ch = channels.get(i);
            Button btn = new Button(this);
            btn.setText(String.format("%.1f\n%s", ch.freq, ch.name));
            btn.setTextSize(10f);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(12), dp(3), dp(12), dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            lp.rightMargin = dp(8);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playChannel(idx);
                }
            });
            presetLayout.addView(btn, lp);
        }
        highlightPresets(currentIndex);
    }

    private void playChannel(int index) {
        if (index < 0 || index >= channels.size()) return;
        currentIndex = index;
        final Channel ch = channels.get(index);
        updateTunerDisplay(index);
        statusText.setText("Buffering: " + ch.name);
        stopPlayback();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(ch.url);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    initEqualizerForPlayer(mp);
                    applyVolumeToPlayer();
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
        releaseEqualizer();
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
        if (knobView != null) knobView.stopAmbientAnimation();
        if (bassKnob != null) bassKnob.stopAmbientAnimation();
        if (midKnob != null) midKnob.stopAmbientAnimation();
        if (trebleKnob != null) trebleKnob.stopAmbientAnimation();
        stopPlayback();
    }

    private static class TunerScaleView extends View {
        private float frequency = 98.1f;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TunerScaleView(Context context) {
            super(context);
            linePaint.setColor(Color.parseColor("#475569"));
            linePaint.setStrokeWidth(2f);

            textPaint.setColor(Color.parseColor("#94A3B8"));
            textPaint.setTextSize(24f);
            textPaint.setTextAlign(Paint.Align.CENTER);

            needlePaint.setColor(Color.parseColor("#EF4444"));
            needlePaint.setStrokeWidth(4f);
        }

        void setFrequency(float freq) {
            this.frequency = freq;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float minFreq = 88.0f;
            float maxFreq = 108.0f;

            for (int f = 90; f <= 106; f += 4) {
                float frac = (f - minFreq) / (maxFreq - minFreq);
                float x = frac * w;
                canvas.drawText(String.valueOf(f), x, h * 0.35f, textPaint);
            }

            float tickTop = h * 0.55f;
            int totalTicks = 40;
            for (int i = 0; i <= totalTicks; i++) {
                float x = (i * w) / totalTicks;
                float tickLen = (i % 5 == 0) ? h * 0.25f : h * 0.14f;
                canvas.drawLine(x, tickTop, x, tickTop + tickLen, linePaint);
            }

            float clamped = Math.max(minFreq, Math.min(maxFreq, frequency));
            float nx = ((clamped - minFreq) / (maxFreq - minFreq)) * w;
            canvas.drawLine(nx, h * 0.15f, nx, h * 0.92f, needlePaint);
        }
    }

    private static class TunerKnobView extends View {
        interface OnRotateListener {
            void onRotateDelta(float deltaDegrees, float knobAngle);
        }

        private float angle = 0f;
        private float lastTouchAngle = 0f;
        private float hue;
        private final long cycleDurationMs;
        private ValueAnimator ambientAnimator;
        private OnRotateListener rotateListener;

        private final Paint ambientGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ambientRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TunerKnobView(Context context, long cycleDurationMs, float initialHue) {
            super(context);
            this.cycleDurationMs = cycleDurationMs;
            this.hue = initialHue % 360f;

            outerRingPaint.setColor(Color.parseColor("#1F2937"));
            facePaint.setColor(Color.parseColor("#9CA3AF"));
            ledPaint.setColor(Color.parseColor("#22C55E"));

            ambientRingPaint.setStyle(Paint.Style.STROKE);
            ambientRingPaint.setStrokeWidth(5.5f);

            startAmbientAnimation();
        }

        private void startAmbientAnimation() {
            if (ambientAnimator != null) return;
            ambientAnimator = ValueAnimator.ofFloat(hue, hue + 360f);
            ambientAnimator.setDuration(cycleDurationMs);
            ambientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            ambientAnimator.setRepeatMode(ValueAnimator.RESTART);
            ambientAnimator.setInterpolator(new LinearInterpolator());
            ambientAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    hue = ((Float) animation.getAnimatedValue()) % 360f;
                    invalidate();
                }
            });
            ambientAnimator.start();
        }

        void stopAmbientAnimation() {
            if (ambientAnimator != null) {
                ambientAnimator.cancel();
                ambientAnimator = null;
            }
        }

        void setOnRotateListener(OnRotateListener listener) {
            this.rotateListener = listener;
        }

        void setAngle(float angle) {
            this.angle = angle;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            float touchAngle = (float) Math.toDegrees(Math.atan2(dy, dx));

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastTouchAngle = touchAngle;
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float diff = touchAngle - lastTouchAngle;
                if (diff > 180f) diff -= 360f;
                if (diff < -180f) diff += 360f;
                angle += diff;
                lastTouchAngle = touchAngle;
                invalidate();
                if (rotateListener != null) {
                    rotateListener.onRotateDelta(diff, angle);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float maxR = Math.min(cx, cy);
            float knobRadius = maxR * 0.78f;

            int primaryColor = Color.HSVToColor(225, new float[]{hue, 0.95f, 1.0f});
            int secondaryColor = Color.HSVToColor(225, new float[]{(hue + 90f) % 360f, 0.95f, 1.0f});
            int glowCenter = Color.HSVToColor(120, new float[]{hue, 0.90f, 1.0f});
            int glowMid = Color.HSVToColor(45, new float[]{(hue + 45f) % 360f, 0.90f, 1.0f});

            ambientGlowPaint.setShader(new RadialGradient(
                    cx, cy, maxR * 0.98f,
                    new int[]{glowCenter, glowMid, Color.TRANSPARENT},
                    new float[]{0.66f, 0.86f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, maxR * 0.98f, ambientGlowPaint);

            ambientRingPaint.setShader(new LinearGradient(
                    cx - knobRadius, cy - knobRadius, cx + knobRadius, cy + knobRadius,
                    primaryColor, secondaryColor, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, knobRadius * 1.05f, ambientRingPaint);

            outerRingPaint.setShader(new LinearGradient(
                    0, 0, w, h,
                    Color.parseColor("#27272A"),
                    Color.parseColor("#09090B"),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, knobRadius, outerRingPaint);

            float innerR = knobRadius * 0.74f;
            facePaint.setShader(new RadialGradient(
                    cx, cy, innerR,
                    new int[]{Color.parseColor("#E4E4E7"), Color.parseColor("#71717A"), Color.parseColor("#3F3F46")},
                    new float[]{0.0f, 0.75f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, innerR, facePaint);

            canvas.save();
            canvas.rotate(angle, cx, cy);
            canvas.drawCircle(cx, cy - innerR * 0.65f, knobRadius * 0.055f, ledPaint);
            canvas.restore();
        }
    }
}
