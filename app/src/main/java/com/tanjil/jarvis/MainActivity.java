package com.tanjil.jarvis;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQ_AUDIO = 1001;

    private TextView statusText, userText, answerText;
    private EditText apiUrlInput;
    private Button talkButton;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        userText = findViewById(R.id.userText);
        answerText = findViewById(R.id.answerText);
        apiUrlInput = findViewById(R.id.apiUrlInput);
        talkButton = findViewById(R.id.talkButton);
        Button saveApiButton = findViewById(R.id.saveApiButton);

        prefs = getSharedPreferences("jarvis", MODE_PRIVATE);
        String saved = prefs.getString("api_url", BuildConfig.DEFAULT_API_URL);
        apiUrlInput.setText(saved);

        tts = new TextToSpeech(this, this);

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    statusText.setText("Listening…");
                }

                @Override public void onBeginningOfSpeech() {
                    statusText.setText("Listening…");
                }

                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    statusText.setText("Processing…");
                }

                @Override public void onError(int error) {
                    statusText.setText("Ready");
                    answerText.setText("I couldn't hear that clearly. Please try again.");
                }

                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        userText.setText(text);
                        handleCommand(text);
                    }
                }

                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            statusText.setText("Speech recognition unavailable");
        }

        talkButton.setOnClickListener(v -> ensureAudioPermissionAndListen());

        saveApiButton.setOnClickListener(v -> {
            String url = apiUrlInput.getText().toString().trim();
            prefs.edit().putString("api_url", url).apply();
            Toast.makeText(this, "API URL saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void ensureAudioPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO
            );
            return;
        }
        startListening();
    }

    private void startListening() {
        if (speechRecognizer == null) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS");

        speechRecognizer.startListening(intent);
    }

    private void handleCommand(String raw) {
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        // Cheap local/offline-first intent router.
        if (containsAny(lower, "সময়", "time", "কটা বাজে", "কয়টা বাজে")) {
            String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            reply("এখন সময় " + time);
            return;
        }

        if (containsAny(lower, "তারিখ", "date", "আজ কত তারিখ", "আজকের তারিখ")) {
            String date = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
            reply("আজ " + date);
            return;
        }

        if (containsAny(lower, "হ্যালো", "hello", "hi", "হাই", "jarvis", "জারভিস")) {
            reply("জি, আমি শুনছি।");
            return;
        }

        if (containsAny(lower, "ধন্যবাদ", "thank you", "thanks")) {
            reply("স্বাগতম।");
            return;
        }

        if (containsAny(lower, "চুপ", "stop speaking", "stop talking")) {
            if (tts != null) tts.stop();
            answerText.setText("ঠিক আছে।");
            statusText.setText("Ready");
            return;
        }

        callAiFallback(text);
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private void callAiFallback(String text) {
        String apiUrl = prefs.getString("api_url", BuildConfig.DEFAULT_API_URL).trim();

        if (apiUrl.isEmpty()) {
            reply("এই প্রশ্নের জন্য AI প্রয়োজন। Settings-এ AI API URL দিন।");
            return;
        }

        statusText.setText("Asking AI…");
        talkButton.setEnabled(false);

        new Thread(() -> {
            String response;
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("message", text);
                body.put("input", text);

                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8
                ));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);

                response = parseAiResponse(sb.toString());
                if (response.isEmpty()) {
                    response = "AI server থেকে ব্যবহারযোগ্য উত্তর পাওয়া যায়নি।";
                }
            } catch (Exception e) {
                response = "AI server-এ সংযোগ করা যায়নি: " + e.getMessage();
            }

            final String finalResponse = response;
            runOnUiThread(() -> {
                talkButton.setEnabled(true);
                reply(finalResponse);
            });
        }).start();
    }

    private String parseAiResponse(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            String[] keys = {"reply", "response", "message", "text", "answer", "output"};
            for (String key : keys) {
                if (obj.has(key) && !obj.isNull(key)) {
                    Object v = obj.get(key);
                    if (v instanceof String) return ((String) v).trim();
                    if (v instanceof JSONObject) {
                        JSONObject nested = (JSONObject) v;
                        for (String nestedKey : keys) {
                            if (nested.has(nestedKey) && nested.get(nestedKey) instanceof String) {
                                return nested.getString(nestedKey).trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // If server returns plain text instead of JSON.
        }
        String plain = raw == null ? "" : raw.trim();
        if (plain.startsWith("<")) return "";
        return plain;
    }

    private void reply(String text) {
        statusText.setText("Ready");
        answerText.setText(text);
        speak(text);
    }

    private void speak(String text) {
        if (tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("bn", "BD"));
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            tts.setSpeechRate(1.0f);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_AUDIO &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    @Override
    protected void onDestroy() {
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
