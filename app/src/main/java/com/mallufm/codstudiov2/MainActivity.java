package com.mallufm.codstudiov2;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_MIC_PERM = 102;
    private static final int REQ_VOICE_FALLBACK = 103;

    private static final String BG = "#0D1117";
    private static final String PANEL = "#161B22";
    private static final String BORDER = "#30363D";
    private static final String ACCENT = "#58A6FF";
    private static final String GREEN = "#238636";
    private static final String RED = "#DA3633";
    private static final String PURPLE = "#8957E5";
    private static final String TEXT_DIM = "#8B949E";

    private EditText filePathInput;
    private EditText codeEditor;
    private TextView statusText;
    private Button btnLang;
    private Button btnMic;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean useMalayalam = false;

    private SpeechRecognizer speechRecognizer;
    private boolean useFallbackIntentMode = false;
    private boolean continuousMicActive = false;
    private boolean isListeningNow = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            useFallbackIntentMode = true;
            if (statusText != null) {
                statusText.setText("Using fallback voice loop • Tap Mic to start");
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
                                ? "മൈക്ക് ഓൺ ആണ് • സംസാരിക്കൂ (നിർത്താൻ Mic വീണ്ടും അമർത്തുക)"
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
                    if (continuousMicActive) {
                        mainHandler.postDelayed(() -> {
                            if (continuousMicActive) {
                                triggerSingleListenCycle();
                            }
                        }, 1200);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            useFallbackIntentMode = true;
            if (statusText != null) {
                statusText.setText("Fallback voice mode ready • Tap Mic");
            }
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String generateSmartReply(String heard) {
        String lower = heard.toLowerCase(Locale.ROOT).trim();
        String nowTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        String nowDate = new SimpleDateFormat("dd MMM yyyy, EEEE", Locale.getDefault()).format(new Date());

        if (containsAny(lower, "നിർത്തു", "ഓഫ്", "മതി", "stop", "turn off", "mic off", "close mic")) {
            stopContinuousMic(false);
            return useMalayalam ? "മൈക്ക് ഓഫ് ചെയ്തു." : "Mic turned off.";
        }

        if (containsAny(lower, "സേവ്", "save file", "save")) {
            saveFile();
            return useMalayalam ? "ഫയൽ സേവ് ചെയ്തു." : "File saved.";
        }

        if (containsAny(lower, "കോപ്പി", "copy")) {
            copyAllToClipboard();
            return useMalayalam ? "കോഡ് ക്ലിപ്പ്ബോർഡിലേക്ക് കോപ്പി ചെയ്തു." : "Copied code to clipboard.";
        }

        if (containsAny(lower, "സമയം", "ടൈം", "എത്ര മണി", "time")) {
            return useMalayalam ? "ഇപ്പോൾ സമയം " + nowTime + " ആണ്." : "Current time is " + nowTime + ".";
        }

        if (containsAny(lower, "തീയതി", "ദിവസം", "ഇന്ന്", "date", "today", "day")) {
            return useMalayalam ? "ഇന്ന് " + nowDate + " ആണ്." : "Today is " + nowDate + ".";
        }

        if (containsAny(lower, "നമസ്കാരം", "ഹലോ", "ഹായ്", "hello", "hi", "hey")) {
            return useMalayalam ? "നമസ്കാരം! പറയൂ, എന്താണ് വേണ്ടത്?" : "Hello! What can I do for you?";
        }

        if (containsAny(lower, "സുഖമാണോ", "എങ്ങനെയുണ്ട്", "സുഖം", "how are you", "how r u", "wassup")) {
            return useMalayalam ? "എനിക്ക് കുഴപ്പമില്ല, നന്നായിരിക്കുന്നു. നിങ്ങൾക്ക് എങ്ങനെയുണ്ട്?" : "I am doing good. How are you doing?";
        }

        if (containsAny(lower, "പേര്", "ആരാണ്", "നീ ആര്", "who are you", "your name", "name")) {
            return useMalayalam ? "ഞാൻ കോഡ് സ്റ്റുഡിയോ വോയിസ് അസിസ്റ്റന്റ് ആണ്." : "I am the Cod Studio voice assistant.";
        }

        if (containsAny(lower, "പൈത്തൺ", "python")) {
            insertSnippet("#!/usr/bin/env python3\n\ndef main():\n    print(\"Hello\")\n\nif __name__ == \"__main__\":\n    main()\n");
            return useMalayalam ? "പൈത്തൺ കോഡ് എഡിറ്ററിൽ ചേർത്തു." : "Inserted Python code into editor.";
        }

        if (containsAny(lower, "എച്ച് ടി എം എൽ", "html")) {
            insertSnippet("<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Page</title></head>\n<body><h1>Hello</h1></body>\n</html>\n");
            return useMalayalam ? "HTML കോഡ് എഡിറ്ററിൽ ചേർത്തു." : "Inserted HTML code into editor.";
        }

        if (containsAny(lower, "സഹായം", "എന്ത് ചെയ്യാം", "help", "what can you do")) {
            return useMalayalam
                    ? "സമയം, തീയതി ചോദിക്കാം; സേവ്, കോപ്പി, പൈത്തൺ, HTML അല്ലെങ്കിൽ നിർത്തു എന്ന് പറയാം."
                    : "You can ask time or date, or say save, copy, python, html, or stop.";
        }

        // Direct concise answer without repetitive filler prefixes
        if (useMalayalam) {
            return "കേട്ടു: " + heard + ". ഇതിന് എന്ത് മറുപടിയാണ് വേണ്ടതെന്ന് പറഞ്ഞാൽ ഞാൻ അപ്‌ഡേറ്റ് ചെയ്യാം.";
        } else {
            return "Heard: " + heard + ". Tell me the exact response you want for this and I will add it.";
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
        String reply = generateSmartReply(heard);
        statusText.setText(reply);
        speakText(reply);
    }

    private void toggleContinuousMic() {
        if (continuousMicActive) {
            stopContinuousMic(true);
            return;
        }

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
        String startMsg = useMalayalam ? "ലൈവ് മൈക്ക് ഓൺ ചെയ്തു, സംസാരിക്കൂ" : "Live mic started, keep speaking";
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
                String msg = useMalayalam ? "സ്പീച്ച് എഞ്ചിൻ ഈ ഫോണിൽ ലഭ്യമല്ല" : "Speech engine unavailable on device";
                statusText.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
                return;
            }

            if (continuousMicActive) {
                mainHandler.postDelayed(() -> {
                    if (continuousMicActive) {
                        triggerSingleListenCycle();
                    }
                }, 1300);
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
                statusText.setText("TTS Ready • Tap Mic for Voice");
            }
        } else {
            ttsReady = false;
            if (statusText != null) {
                statusText.setText("TTS Init failed (" + status + ")");
            }
        }
    }

    private void applyVoiceLanguage(boolean speakNotice) {
        if (!ttsReady || tts == null) return;
        try {
            if (useMalayalam) {
                int mlRes = tts.setLanguage(new Locale("ml", "IN"));
                if (mlRes == TextToSpeech.LANG_MISSING_DATA || mlRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                    if (statusText != null) {
                        statusText.setText("Malayalam TTS missing • Using English voice");
                    }
                    if (speakNotice) {
                        tts.speak("Malayalam voice data not found. Using English.", TextToSpeech.QUEUE_FLUSH, null, "ml_fallback");
                    }
                    return;
                }
                if (statusText != null) {
                    statusText.setText("Malayalam voice active");
                }
            } else {
                int enRes = tts.setLanguage(Locale.US);
                if (enRes == TextToSpeech.LANG_MISSING_DATA || enRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
                if (statusText != null) {
                    statusText.setText("English voice active");
                }
            }
        } catch (Exception e) {
            if (statusText != null) {
                statusText.setText("Voice error: " + e.getMessage());
            }
        }
    }

    private void speakText(String text) {
        String say = (text == null || text.trim().isEmpty())
                ? (useMalayalam ? "നമസ്കാരം, കോഡ് സ്റ്റുഡിയോ തയ്യാറാണ്" : "Hello, Cod Studio is ready")
                : text.trim();

        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Voice starting, try again", Toast.LENGTH_SHORT).show();
            return;
        }

        applyVoiceLanguage(false);
        String toSay = say.length() > 350 ? say.substring(0, 350) : say;
        tts.speak(toSay, TextToSpeech.QUEUE_FLUSH, null, "cod_studio_utterance");
        if (statusText != null && !continuousMicActive) {
            statusText.setText("Speaking: " + toSay);
        }
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

        btnLang = makeBtn("Lang: EN", PURPLE, Color.WHITE);
        btnLang.setOnClickListener(v -> {
            useMalayalam = !useMalayalam;
            btnLang.setText(useMalayalam ? "Lang: ML" : "Lang: EN");
            applyVoiceLanguage(true);
            speakText(useMalayalam ? "മലയാളം വോയിസ് സജീവമാക്കി" : "English voice activated");
        });
        topRow.addView(btnLang);

        btnMic = makeBtn("Mic", GREEN, Color.WHITE);
        btnMic.setOnClickListener(v -> toggleContinuousMic());
        topRow.addView(btnMic);

        Button btnSpeak = makeBtn("Speak", "#1F6FEB", Color.WHITE);
        btnSpeak.setOnClickListener(v -> speakText(codeEditor.getText().toString()));
        topRow.addView(btnSpeak);

        statusText = new TextView(this);
        statusText.setText("Starting Voice Engine...");
        statusText.setTextColor(Color.parseColor(TEXT_DIM));
        statusText.setTextSize(12f);
        statusText.setPadding(0, 8, 0, 12);
        root.addView(statusText);

        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(pathRow);

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
        filePathInput.setPadding(18, 14, 18, 14);
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
        sLp.setMargins(0, 14, 0, 12);
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

        bPy.setOnClickListener(v -> {
            insertSnippet("#!/usr/bin/env python3\n\ndef main():\n    print(\"Hello from Cod Studio\")\n\nif __name__ == \"__main__\":\n    main()\n");
            speakText(useMalayalam ? "പൈത്തൺ കോഡ് ചേർത്തു" : "Python snippet inserted");
        });
        bGit.setOnClickListener(v -> {
            insertSnippet("git status && git add -A && git commit -m \"Update code\" && git push\n");
            speakText(useMalayalam ? "ജിറ്റ് കമാൻഡ് ചേർത്തു" : "Git command inserted");
        });
        bHtml.setOnClickListener(v -> {
            insertSnippet("<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Cod Studio</title></head>\n<body><h1>Hello Cod Studio</h1></body>\n</html>\n");
            speakText(useMalayalam ? "എച്ച് ടി എം എൽ ടെംപ്ലേറ്റ് ചേർത്തു" : "HTML template inserted");
        });
        bCopy.setOnClickListener(v -> copyAllToClipboard());
        bTermux.setOnClickListener(v -> launchTermux());

        codeEditor = new EditText(this);
        codeEditor.setHint("# Tap Mic for live continuous voice, or write code here...");
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
        statusText.setText("Inserted snippet");
    }

    private void copyAllToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("CodStudio", codeEditor.getText().toString());
            cm.setPrimaryClip(clip);
            String msg = useMalayalam ? "കോഡ് കോപ്പി ചെയ്തു" : "Copied to clipboard";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            statusText.setText(msg);
            speakText(msg);
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
        } catch (Exception e) {
            statusText.setText("Could not launch Termux");
        }
    }

    private void loadFile() {
        String p = filePathInput.getText().toString().trim();
        File f = new File(p);
        if (!f.exists() || !f.isFile()) {
            String msg = useMalayalam ? "ഫയൽ കണ്ടെത്താനായില്ല" : "File not found";
            statusText.setText(msg + ": " + p);
            speakText(msg);
            return;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            codeEditor.setText(sb.toString());
            String msg = useMalayalam ? "ഫയൽ ലോഡ് ചെയ്തു" : "Loaded " + f.getName();
            statusText.setText(msg);
            speakText(msg);
        } catch (Exception e) {
            statusText.setText("Read error: " + e.getMessage());
        }
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
            String msg = useMalayalam ? "ഫയൽ സേവ് ചെയ്തു" : "Saved file";
            statusText.setText(msg + ": " + f.getAbsolutePath());
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            speakText(msg);
        } catch (Exception e) {
            statusText.setText("Save error: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        stopContinuousMic(false);
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
