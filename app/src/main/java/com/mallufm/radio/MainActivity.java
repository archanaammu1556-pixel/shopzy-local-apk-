package com.mallufm.codstudiov2;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

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
        tts = new TextToSpeech(this, this);
        buildEditorUi();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            applyVoiceLanguage();
        } else {
            statusText.setText("Voice engine not available on this device");
        }
    }

    private void applyVoiceLanguage() {
        if (!ttsReady || tts == null) return;
        Locale target = useMalayalam ? new Locale("ml", "IN") : Locale.US;
        int res = tts.setLanguage(target);
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (useMalayalam) {
                statusText.setText("Malayalam TTS data missing. Install Malayalam voice in TTS settings.");
            } else {
                tts.setLanguage(Locale.ENGLISH);
            }
        }
    }

    private void speakText(String text) {
        if (text == null || text.trim().isEmpty()) {
            String emptyMsg = useMalayalam ? "വായിക്കാൻ ടെക്സ്റ്റ് ഒന്നുമില്ല" : "Nothing to speak";
            statusText.setText(emptyMsg);
            return;
        }
        if (!ttsReady || tts == null) {
            statusText.setText("Voice is starting...");
            return;
        }
        applyVoiceLanguage();
        String toSay = text.length() > 400 ? text.substring(0, 400) : text;
        tts.speak(toSay, TextToSpeech.QUEUE_FLUSH, null, "cod_studio_utterance");
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
            applyVoiceLanguage();
            String reply = useMalayalam
                    ? "മലയാളം വോയിസ് സജീവമാക്കി"
                    : "English voice activated";
            statusText.setText(reply);
            speakText(reply);
        });
        topRow.addView(btnLang);

        Button btnSpeak = makeBtn("Speak", "#1F6FEB", Color.WHITE);
        btnSpeak.setOnClickListener(v -> {
            String selectedOrAll = codeEditor.getText().toString().trim();
            if (selectedOrAll.isEmpty()) {
                selectedOrAll = useMalayalam
                        ? "നമസ്കാരം, കോഡ് സ്റ്റുഡിയോ തയ്യാറാണ്"
                        : "Hello, Cod Studio is ready";
            }
            speakText(selectedOrAll);
        });
        topRow.addView(btnSpeak);

        statusText = new TextView(this);
        statusText.setText("Ready • Tap EN/ML for voice & Speak to talk back");
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
        codeEditor.setHint("# Write code or notes here, then tap Speak...");
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
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            String msg = useMalayalam ? "കോഡ് കോപ്പി ചെയ്തു" : "Copied to clipboard";
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
