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
import android.speech.RecognizerIntent;
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
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_SPEECH = 101;
    private static final int REQ_MIC_PERM = 102;

    private static final String BG = "#0D1117";
    private static final String PANEL = "#161B22";
    private static final String BORDER = "#30363D";
    private static final String ACCENT = "#58A6FF";
    private static final String GREEN = "#238636";
    private static final String PURPLE = "#8957E5";
    private static final String TEXT_DIM = "#8B949E";

    private EditText filePathInput;
    private EditText codeEditor;
    private TextView statusText;
    private Button btnLang;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean useMalayalam = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildEditorUi();
            tts = new TextToSpeech(this, this);
        } catch (Exception e) {
            Toast.makeText(this, "Init error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            applyVoiceLanguage(false);
            if (statusText != null) {
                statusText.setText("TTS Ready • Tap Mic or Speak");
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
        if (statusText != null) {
            statusText.setText("Speaking: " + toSay);
        }
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERM);
                return;
            }
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            String langTag = useMalayalam ? "ml-IN" : "en-US";
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    useMalayalam ? "മലയാളത്തിൽ പറയൂ..." : "Speak now...");
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            String msg = useMalayalam ? "വോയിസ് ഇൻപുട്ട് ലഭ്യമല്ല" : "Speech recognition not available";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            if (statusText != null) {
                statusText.setText(msg);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                String msg = useMalayalam ? "മൈക്രോഫോൺ അനുമതി ആവശ്യമാണ്" : "Microphone permission required";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String heard = matches.get(0);
                if (!codeEditor.getText().toString().isEmpty()) {
                    codeEditor.append("\n");
                }
                codeEditor.append(heard);
                String reply = useMalayalam ? "നിങ്ങൾ പറഞ്ഞത്: " + heard : "You said: " + heard;
                statusText.setText(reply);
                speakText(reply);
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

        Button btnMic = makeBtn("Mic", GREEN, Color.WHITE);
        btnMic.setOnClickListener(v -> startListening());
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
        codeEditor.setHint("# Tap Mic to speak, or write code here...");
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
