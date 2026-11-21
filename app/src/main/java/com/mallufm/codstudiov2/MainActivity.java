package com.mallufm.codstudiov2;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_MIC_PERM = 102;
    private static final int REQ_VOICE_FALLBACK = 103;
    private static final String PREFS_NAME = "cod_studio_prefs";
    private static final String KEY_API = "gemini_api_key";

    private static final String BG = "#0D1117";
    private static final String PANEL = "#161B22";
    private static final String BORDER = "#30363D";
    private static final String ACCENT = "#58A6FF";
    private static final String GREEN = "#238636";
    private static final String RED = "#DA3633";
    private static final String PURPLE = "#8957E5";
    private static final String TEXT_DIM = "#8B949E";

    private EditText filePathInput;
    private EditText apiKeyInput;
    private EditText codeEditor;
    private TextView statusText;
    private Button btnLang;
    private Button btnMic;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean useMalayalam = true;

    private SpeechRecognizer speechRecognizer;
    private boolean useFallbackIntentMode = false;
    private boolean continuousMicActive = false;
    private boolean isListeningNow = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> chatHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildEditorUi();
            tts = new TextToSpeech(this, this);
            initSpeechRecognizer();
        } catch (Exception e) {
            Toast.makeText(this, "Init error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveApiKeyToPrefs() {
        if (apiKeyInput == null) return;
        String k = apiKeyInput.getText().toString().trim();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_API, k).apply();
    }

    private String getSavedApiKey() {
        if (apiKeyInput != null) {
            String typed = apiKeyInput.getText().toString().trim();
            if (!typed.isEmpty()) return typed;
        }
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return sp.getString(KEY_API, "").trim();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            useFallbackIntentMode = true;
            if (statusText != null) {
                statusText.setText("Using fallback voice loop • Tap Mic");
            }
            return;
        }
        useFallbackIntentMode = false;
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListeningNow = true;
                    if (statusText != null) {
                        statusText.setText(useMalayalam
                                ? "മൈക്ക് ഓൺ • സംസാരിക്കൂ (നിർത്താൻ Mic അമർത്തുക)"
                                : "Live Mic ON • Speak anytime (Tap Mic to stop)");
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (statusText != null) {
                        statusText.setText(useMalayalam ? "കേൾക്കുന്നു..." : "Listening...");
                    }
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListeningNow = false;
                }

                @Override
                public void onError(int error) {
                    isListeningNow = false;
                    if (!continuousMicActive) return;
                    mainHandler.postDelayed(() -> {
                        if (continuousMicActive) {
                            triggerSingleListenCycle();
                        }
                    }, 450);
                }

                @Override
                public void onResults(Bundle results) {
                    isListeningNow = false;
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            handleRecognizedText(matches.get(0));
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            useFallbackIntentMode = true;
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String localFallbackReply(String heard) {
        String lower = heard.toLowerCase(Locale.ROOT).trim();
        String nowTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        String nowDate = new SimpleDateFormat("dd MMM yyyy, EEEE", Locale.getDefault()).format(new Date());

        if (containsAny(lower, "നിർത്തു", "ഓഫ്", "മതി", "stop", "turn off", "mic off")) {
            stopContinuousMic(false);
            return useMalayalam ? "ശരി, മൈക്ക് ഓഫ് ചെയ്തു." : "Okay, mic turned off.";
        }
        if (containsAny(lower, "സേവ്", "save file", "save")) {
            saveFile();
            return useMalayalam ? "ഫയൽ സേവ് ചെയ്തു." : "File saved.";
        }
        if (containsAny(lower, "കോപ്പി", "copy")) {
            copyAllToClipboard();
            return useMalayalam ? "കോപ്പി ചെയ്തു." : "Copied to clipboard.";
        }
        if (containsAny(lower, "സമയം", "ടൈം", "എത്ര മണി", "time")) {
            return useMalayalam ? "ഇപ്പോൾ സമയം " + nowTime + " ആണ്." : "Current time is " + nowTime + ".";
        }
        if (containsAny(lower, "തീയതി", "ദിവസം", "ഇന്ന്", "date", "today")) {
            return useMalayalam ? "ഇന്ന് " + nowDate + " ആണ്." : "Today is " + nowDate + ".";
        }
        if (containsAny(lower, "നമസ്കാരം", "ഹലോ", "ഹായ്", "hello", "hi", "hey")) {
            return useMalayalam ? "നമസ്കാരം! പറയൂ, എന്താണ് വിശേഷം?" : "Hey! What's up?";
        }
        if (containsAny(lower, "സുഖമാണോ", "എങ്ങനെയുണ്ട്", "how are you")) {
            return useMalayalam ? "എനിക്ക് നല്ല സുഖം! നിങ്ങൾക്ക് എങ്ങനെയുണ്ട്?" : "I'm doing great! How about you?";
        }
        return useMalayalam
                ? "സ്മാർട്ട് മറുപടികൾക്കായി മുകളിൽ Gemini API Key നൽകുക. ഇപ്പോൾ കേട്ടത്: " + heard
                : "Add your Gemini API Key above for smart AI replies. Heard: " + heard;
    }

    private String callGeminiApi(String apiKey, String userMessage) {
        HttpURLConnection conn = null;
        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String langInstruction = useMalayalam
                    ? "Reply naturally, warmly, and clearly in Malayalam (matching the user's conversational tone). Keep spoken answers clear and concise without markdown symbols like asterisks or code fences."
                    : "Reply naturally, warmly, and clearly in English (matching the user's conversational tone). Keep spoken answers clear and concise without markdown symbols.";

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append(langInstruction).append("\n\n");
            int startIdx = Math.max(0, chatHistory.size() - 6);
            for (int i = startIdx; i < chatHistory.size(); i++) {
                contextBuilder.append(chatHistory.get(i)).append("\n");
            }
            contextBuilder.append("User: ").append(userMessage).append("\nAssistant:");

            JSONObject part = new JSONObject();
            part.put("text", contextBuilder.toString());

            JSONObject content = new JSONObject();
            content.put("parts", new JSONArray().put(part));

            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));

            byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outBytes);
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }

            if (code < 200 || code >= 300) {
                return null;
            }

            JSONObject respJson = new JSONObject(sb.toString());
            JSONArray candidates = respJson.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject c0 = candidates.getJSONObject(0);
                JSONObject cContent = c0.optJSONObject("content");
                if (cContent != null) {
                    JSONArray parts = cContent.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        String reply = parts.getJSONObject(0).optString("text", "").trim();
                        if (!reply.isEmpty()) {
                            return reply.replace("*", "").replace("#", "").trim();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void handleRecognizedText(String rawText) {
        if (rawText == null) return;
        String heard = rawText.trim();
        if (heard.isEmpty()) return;

        if (!codeEditor.getText().toString().isEmpty()) {
            codeEditor.append("\n");
        }
        codeEditor.append(heard);

        String lower = heard.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "നിർത്തു", "ഓഫ്", "മതി", "stop", "turn off", "mic off")) {
            String stopReply = localFallbackReply(heard);
            statusText.setText(stopReply);
            speakText(stopReply);
            return;
        }

        String apiKey = getSavedApiKey();
        if (apiKey.isEmpty()) {
            String fallback = localFallbackReply(heard);
            statusText.setText(fallback);
            speakTextAndResume(fallback);
            return;
        }

        saveApiKeyToPrefs();
        if (statusText != null) {
            statusText.setText(useMalayalam ? "മറുപടി തയ്യാറാക്കുന്നു..." : "Thinking...");
        }

        bgExecutor.execute(() -> {
            String aiReply = callGeminiApi(apiKey, heard);
            final String finalReply = (aiReply != null && !aiReply.isEmpty())
                    ? aiReply
                    : localFallbackReply(heard);

            chatHistory.add("User: " + heard);
            chatHistory.add("Assistant: " + finalReply);

            mainHandler.post(() -> {
                if (statusText != null) {
                    statusText.setText(finalReply);
                }
                speakTextAndResume(finalReply);
            });
        });
    }

    private void speakTextAndResume(String text) {
        speakText(text);
        if (continuousMicActive) {
            long delayMs = Math.min(4500L, Math.max(1500L, text.length() * 65L));
            mainHandler.postDelayed(() -> {
                if (continuousMicActive && !isListeningNow) {
                    triggerSingleListenCycle();
                }
            }, delayMs);
        }
    }

    private void toggleContinuousMic() {
        if (continuousMicActive) {
            stopContinuousMic(true);
            return;
        }

        saveApiKeyToPrefs();

        if (!useFallbackIntentMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERM);
                return;
            }
        }

        if (!useFallbackIntentMode && speechRecognizer == null) {
            initSpeechRecognizer();
        }

        continuousMicActive = true;
        updateMicButtonState();
        String startMsg = useMalayalam ? "ശരി, ഞാൻ കേൾക്കുന്നുണ്ട്. സംസാരിക്കൂ" : "I am listening, go ahead";
        speakText(startMsg);
        mainHandler.postDelayed(this::triggerSingleListenCycle, useFallbackIntentMode ? 1100 : 850);
    }

    private void stopContinuousMic(boolean announce) {
        continuousMicActive = false;
        isListeningNow = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
        updateMicButtonState();
        String stopMsg = useMalayalam ? "മൈക്ക് ഓഫ് ചെയ്തു" : "Mic turned off";
        if (statusText != null) {
            statusText.setText(stopMsg);
        }
        if (announce) {
            speakText(stopMsg);
        }
    }

    private Intent buildSpeechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String langTag = useMalayalam ? "ml-IN" : "en-US";
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                useMalayalam ? "സംസാരിക്കൂ... (നിർത്താൻ Back അമർത്തുക)" : "Speak now... (Press Back to stop)");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return intent;
    }

    private void triggerSingleListenCycle() {
        if (!continuousMicActive || isListeningNow) return;

        if (useFallbackIntentMode || speechRecognizer == null) {
            try {
                isListeningNow = true;
                startActivityForResult(buildSpeechIntent(), REQ_VOICE_FALLBACK);
            } catch (Exception e) {
                isListeningNow = false;
                continuousMicActive = false;
                updateMicButtonState();
                String msg = useMalayalam ? "സ്പീച്ച് എഞ്ചിൻ ലഭ്യമല്ല" : "Speech engine unavailable";
                statusText.setText(msg);
            }
            return;
        }

        try {
            isListeningNow = true;
            speechRecognizer.startListening(buildSpeechIntent());
        } catch (Exception e) {
            isListeningNow = false;
            useFallbackIntentMode = true;
            triggerSingleListenCycle();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_FALLBACK) {
            isListeningNow = false;
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    handleRecognizedText(matches.get(0));
                }
            } else if (resultCode == RESULT_CANCELED) {
                continuousMicActive = false;
                updateMicButtonState();
                statusText.setText(useMalayalam ? "മൈക്ക് ഓഫ് ചെയ്തു" : "Mic stopped");
            }
        }
    }

    private void updateMicButtonState() {
        if (btnMic == null) return;
        btnMic.setText(continuousMicActive ? "Mic ON" : "Mic");
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(continuousMicActive ? RED : GREEN));
        gd.setStroke(1, Color.parseColor(BORDER));
        gd.setCornerRadius(14f);
        btnMic.setBackground(gd);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            applyVoiceLanguage(false);
            if (statusText != null && !continuousMicActive) {
                statusText.setText(useMalayalam ? "തയ്യാറാണ് • Mic അമർത്തി സംസാരിക്കൂ" : "Ready • Tap Mic to talk");
            }
        } else {
            ttsReady = false;
        }
    }

    private void applyVoiceLanguage(boolean speakNotice) {
        if (!ttsReady || tts == null) return;
        try {
            if (useMalayalam) {
                int mlRes = tts.setLanguage(new Locale("ml", "IN"));
                if (mlRes == TextToSpeech.LANG_MISSING_DATA || mlRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                    if (speakNotice) {
                        tts.speak("Malayalam voice not installed. Using English voice.", TextToSpeech.QUEUE_FLUSH, null, "ml_fb");
                    }
                }
            } else {
                tts.setLanguage(Locale.US);
            }
        } catch (Exception ignored) {}
    }

    private void speakText(String text) {
        String say = (text == null || text.trim().isEmpty())
                ? (useMalayalam ? "നമസ്കാരം" : "Hello")
                : text.trim();

        if (!ttsReady || tts == null) return;
        applyVoiceLanguage(false);
        String toSay = say.length() > 450 ? say.substring(0, 450) : say;
        tts.speak(toSay, TextToSpeech.QUEUE_FLUSH, null, "cod_studio_utterance");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleContinuousMic();
            } else {
                useFallbackIntentMode = true;
                toggleContinuousMic();
            }
        }
    }

    private void buildEditorUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(BG));
        root.setPadding(22, 30, 22, 22);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topRow);

        TextView header = new TextView(this);
        header.setText("COD STUDIO");
        header.setTextColor(Color.parseColor(ACCENT));
        header.setTextSize(20f);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        topRow.addView(header, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnLang = makeBtn("Lang: ML", PURPLE, Color.WHITE);
        btnLang.setOnClickListener(v -> {
            useMalayalam = !useMalayalam;
            btnLang.setText(useMalayalam ? "Lang: ML" : "Lang: EN");
            applyVoiceLanguage(true);
            speakText(useMalayalam ? "മലയാളം സജീവമാക്കി" : "English activated");
        });
        topRow.addView(btnLang);

        btnMic = makeBtn("Mic", GREEN, Color.WHITE);
        btnMic.setOnClickListener(v -> toggleContinuousMic());
        topRow.addView(btnMic);

        Button btnSpeak = makeBtn("Speak", "#1F6FEB", Color.WHITE);
        btnSpeak.setOnClickListener(v -> speakText(statusText.getText().toString()));
        topRow.addView(btnSpeak);

        statusText = new TextView(this);
        statusText.setText("Starting Voice Engine...");
        statusText.setTextColor(Color.parseColor("#E6EDF3"));
        statusText.setTextSize(13f);
        statusText.setPadding(0, 10, 0, 10);
        root.addView(statusText);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedKey = sp.getString(KEY_API, "");

        LinearLayout apiRow = new LinearLayout(this);
        apiRow.setOrientation(LinearLayout.HORIZONTAL);
        apiRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(apiRow);

        apiKeyInput = new EditText(this);
        apiKeyInput.setHint("Paste Gemini API Key here (AIza...)");
        apiKeyInput.setHintTextColor(Color.parseColor(TEXT_DIM));
        apiKeyInput.setText(savedKey);
        apiKeyInput.setTextColor(Color.WHITE);
        apiKeyInput.setTextSize(12f);
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setTypeface(Typeface.MONOSPACE);
        GradientDrawable apiBg = new GradientDrawable();
        apiBg.setColor(Color.parseColor(PANEL));
        apiBg.setStroke(2, Color.parseColor(BORDER));
        apiBg.setCornerRadius(14f);
        apiKeyInput.setBackground(apiBg);
        apiKeyInput.setPadding(18, 12, 18, 12);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        aLp.setMargins(0, 0, 10, 0);
        apiRow.addView(apiKeyInput, aLp);

        Button btnSaveKey = makeBtn("Save Key", PURPLE, Color.WHITE);
        btnSaveKey.setOnClickListener(v -> {
            saveApiKeyToPrefs();
            String msg = useMalayalam ? "API Key സേവ് ചെയ്തു" : "API Key saved";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            statusText.setText(msg);
        });
        apiRow.addView(btnSaveKey);

        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prLp.setMargins(0, 10, 0, 0);
        root.addView(pathRow, prLp);

        String defaultFile = new File(Environment.getExternalStorageDirectory(), "Download/snippet.py").getAbsolutePath();
        filePathInput = new EditText(this);
        filePathInput.setText(defaultFile);
        filePathInput.setTextColor(Color.WHITE);
        filePathInput.setTextSize(12f);
        filePathInput.setSingleLine(true);
        filePathInput.setTypeface(Typeface.MONOSPACE);
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor(PANEL));
        pathBg.setStroke(2, Color.parseColor(BORDER));
        pathBg.setCornerRadius(14f);
        filePathInput.setBackground(pathBg);
        filePathInput.setPadding(18, 12, 18, 12);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pLp.setMargins(0, 0, 10, 0);
        pathRow.addView(filePathInput, pLp);

        Button btnLoad = makeBtn("Load", PANEL, Color.WHITE);
        Button btnSave = makeBtn("Save", GREEN, Color.WHITE);
        pathRow.addView(btnLoad);
        pathRow.addView(btnSave);

        btnLoad.setOnClickListener(v -> loadFile());
        btnSave.setOnClickListener(v -> saveFile());

        HorizontalScrollView snipScroll = new HorizontalScrollView(this);
        snipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, 12, 0, 10);
        root.addView(snipScroll, sLp);

        LinearLayout snipRow = new LinearLayout(this);
        snipRow.setOrientation(LinearLayout.HORIZONTAL);
        snipScroll.addView(snipRow);

        Button bPy = makeBtn("Python", PANEL, Color.parseColor(ACCENT));
        Button bGit = makeBtn("Git Push", PANEL, Color.parseColor(ACCENT));
        Button bHtml = makeBtn("HTML", PANEL, Color.parseColor(ACCENT));
        Button bCopy = makeBtn("Copy All", "#1F6FEB", Color.WHITE);
        Button bTermux = makeBtn("Open Termux", PANEL, Color.WHITE);

        snipRow.addView(bPy);
        snipRow.addView(bGit);
        snipRow.addView(bHtml);
        snipRow.addView(bCopy);
        snipRow.addView(bTermux);

        bPy.setOnClickListener(v -> insertSnippet("#!/usr/bin/env python3\n\ndef main():\n    print(\"Hello from Cod Studio\")\n\nif __name__ == \"__main__\":\n    main()\n"));
        bGit.setOnClickListener(v -> insertSnippet("git status && git add -A && git commit -m \"Update code\" && git push\n"));
        bHtml.setOnClickListener(v -> insertSnippet("<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Cod Studio</title></head>\n<body><h1>Hello Cod Studio</h1></body>\n</html>\n"));
        bCopy.setOnClickListener(v -> copyAllToClipboard());
        bTermux.setOnClickListener(v -> launchTermux());

        codeEditor = new EditText(this);
        codeEditor.setHint("# Transcript / code editor...");
        codeEditor.setHintTextColor(Color.parseColor(TEXT_DIM));
        codeEditor.setTextColor(Color.parseColor("#E6EDF3"));
        codeEditor.setTextSize(14f);
        codeEditor.setGravity(Gravity.TOP | Gravity.START);
        codeEditor.setTypeface(Typeface.MONOSPACE);
        codeEditor.setHorizontallyScrolling(true);
        GradientDrawable edBg = new GradientDrawable();
        edBg.setColor(Color.parseColor(PANEL));
        edBg.setStroke(2, Color.parseColor(BORDER));
        edBg.setCornerRadius(16f);
        codeEditor.setBackground(edBg);
        codeEditor.setPadding(20, 20, 20, 20);

        root.addView(codeEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Button makeBtn(String label, String bgHex, int fgColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(fgColor);
        b.setAllCaps(false);
        b.setTextSize(12f);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setStroke(1, Color.parseColor(BORDER));
        gd.setCornerRadius(14f);
        b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 8, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void insertSnippet(String snippet) {
        int start = Math.max(0, codeEditor.getSelectionStart());
        codeEditor.getText().insert(start, snippet);
    }

    private void copyAllToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("CodStudio", codeEditor.getText().toString());
            cm.setPrimaryClip(clip);
        }
    }

    private void launchTermux() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (launch != null) {
                startActivity(launch);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://termux.dev")));
            }
        } catch (Exception ignored) {}
    }

    private void loadFile() {
        String p = filePathInput.getText().toString().trim();
        File f = new File(p);
        if (!f.exists() || !f.isFile()) return;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            codeEditor.setText(sb.toString());
        } catch (Exception ignored) {}
    }

    private void saveFile() {
        String p = filePathInput.getText().toString().trim();
        File f = new File(p);
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(f);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(codeEditor.getText().toString());
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        saveApiKeyToPrefs();
        stopContinuousMic(false);
        bgExecutor.shutdownNow();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
