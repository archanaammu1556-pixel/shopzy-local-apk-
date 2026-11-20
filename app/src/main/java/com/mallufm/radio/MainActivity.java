package com.mallufm.radio;

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
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
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
    private TunerScaleView scaleView;
    private TunerKnobView knobView;
    private TextView freqText;
    private TextView channelNameText;
    private TextView statusText;
    private LinearLayout presetLayout;
    private int currentIndex = -1;
    private boolean isPlaying = false;

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
        topPanel.setPadding(16, 24, 16, 20);

        statusText = new TextView(this);
        statusText.setText("Loading Malayalam FM...");
        statusText.setTextSize(12f);
        statusText.setTextColor(Color.parseColor("#7DD3FC"));
        statusText.setGravity(Gravity.CENTER);
        topPanel.addView(statusText);

        scaleView = new TunerScaleView(this);
        LinearLayout.LayoutParams scaleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        scaleParams.topMargin = dp(8);
        topPanel.addView(scaleView, scaleParams);

        freqText = new TextView(this);
        freqText.setText("98.1 MHz");
        freqText.setTextSize(38f);
        freqText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        freqText.setTextColor(Color.parseColor("#E0F2FE"));
        freqText.setGravity(Gravity.CENTER);
        freqText.setPadding(0, dp(4), 0, 0);
        topPanel.addView(freqText);

        channelNameText = new TextView(this);
        channelNameText.setText("Mallu FM");
        channelNameText.setTextSize(16f);
        channelNameText.setTypeface(Typeface.DEFAULT_BOLD);
        channelNameText.setTextColor(Color.parseColor("#38BDF8"));
        channelNameText.setGravity(Gravity.CENTER);
        channelNameText.setPadding(0, dp(2), 0, 0);
        topPanel.addView(channelNameText);

        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.44f);
        root.addView(topPanel, topParams);

        // Middle Rotary Knob with < and > Tuning Buttons
        LinearLayout middlePanel = new LinearLayout(this);
        middlePanel.setOrientation(LinearLayout.VERTICAL);
        middlePanel.setGravity(Gravity.CENTER);
        middlePanel.setBackgroundColor(Color.parseColor("#040609"));
        middlePanel.setPadding(16, 12, 16, 12);

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

        knobView = new TunerKnobView(this);
        LinearLayout.LayoutParams knobParams = new LinearLayout.LayoutParams(dp(185), dp(185));
        knobParams.leftMargin = dp(18);
        knobParams.rightMargin = dp(18);
        knobView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!channels.isEmpty()) {
                    int next = (currentIndex + 1) % channels.size();
                    playChannel(next);
                }
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

        // Compact Play/Stop & Refresh row below knob
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dp(10), 0, 0);

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
        playLp.rightMargin = dp(12);
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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.42f);
        root.addView(middlePanel, midParams);

        // Bottom Preset Bar
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setBackgroundColor(Color.parseColor("#0B0E14"));
        presetScroll.setFillViewport(true);

        presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setGravity(Gravity.CENTER_VERTICAL);
        presetLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
        presetScroll.addView(presetLayout);

        LinearLayout.LayoutParams botParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.14f);
        root.addView(presetScroll, botParams);

        setContentView(root);
        loadDefaultChannels();
        renderChannelButtons();
        updateTunerDisplay(-1);
        fetchChannelsAsync(false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button createArrowButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(26f);
        b.setTextColor(Color.parseColor("#CBD5E1"));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinimumWidth(dp(48));
        b.setMinimumHeight(dp(64));
        return b;
    }

    private Button createActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11f);
        b.setAllCaps(true);
        b.setTextColor(Color.parseColor("#E2E8F0"));
        b.setBackgroundColor(Color.parseColor("#18202C"));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        return b;
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
            knobView.setAngle(0f);
            return;
        }
        int idx = (index >= 0 && index < channels.size()) ? index : 0;
        Channel ch = channels.get(idx);
        freqText.setText(String.format("%.1f MHz", ch.freq));
        channelNameText.setText(ch.name);
        scaleView.setFrequency(ch.freq);
        float angle = (idx * 360f) / Math.max(1, channels.size());
        knobView.setAngle(angle);
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
            btn.setTextSize(11f);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(14), dp(4), dp(14), dp(4));
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
            textPaint.setTextSize(28f);
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

            // Major labels every 4 MHz
            for (int f = 90; f <= 106; f += 4) {
                float frac = (f - minFreq) / (maxFreq - minFreq);
                float x = frac * w;
                canvas.drawText(String.valueOf(f), x, h * 0.35f, textPaint);
            }

            // Ruler ticks
            float tickTop = h * 0.55f;
            int totalTicks = 40;
            for (int i = 0; i <= totalTicks; i++) {
                float x = (i * w) / totalTicks;
                float tickLen = (i % 5 == 0) ? h * 0.25f : h * 0.14f;
                canvas.drawLine(x, tickTop, x, tickTop + tickLen, linePaint);
            }

            // Red indicator line
            float clamped = Math.max(minFreq, Math.min(maxFreq, frequency));
            float nx = ((clamped - minFreq) / (maxFreq - minFreq)) * w;
            canvas.drawLine(nx, h * 0.15f, nx, h * 0.92f, needlePaint);
        }
    }

    private static class TunerKnobView extends View {
        private float angle = 0f;
        private float lastTouchAngle = 0f;
        private float accumulatedDelta = 0f;
        private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TunerKnobView(Context context) {
            super(context);
            outerRingPaint.setColor(Color.parseColor("#1F2937"));
            facePaint.setColor(Color.parseColor("#9CA3AF"));
            ledPaint.setColor(Color.parseColor("#22C55E"));
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
                accumulatedDelta = 0f;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float diff = touchAngle - lastTouchAngle;
                if (diff > 180f) diff -= 360f;
                if (diff < -180f) diff += 360f;
                angle += diff;
                accumulatedDelta += diff;
                lastTouchAngle = touchAngle;
                invalidate();
                if (Math.abs(accumulatedDelta) >= 45f) {
                    accumulatedDelta = 0f;
                    performClick();
                }
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
            float radius = Math.min(cx, cy) * 0.92f;

            outerRingPaint.setShader(new LinearGradient(
                    0, 0, w, h,
                    Color.parseColor("#27272A"),
                    Color.parseColor("#09090B"),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, outerRingPaint);

            float innerR = radius * 0.72f;
            facePaint.setShader(new RadialGradient(
                    cx, cy, innerR,
                    new int[]{Color.parseColor("#E4E4E7"), Color.parseColor("#71717A"), Color.parseColor("#3F3F46")},
                    new float[]{0.0f, 0.75f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, innerR, facePaint);

            canvas.save();
            canvas.rotate(angle, cx, cy);
            canvas.drawCircle(cx, cy - innerR * 0.65f, radius * 0.045f, ledPaint);
            canvas.restore();
        }
    }
}
