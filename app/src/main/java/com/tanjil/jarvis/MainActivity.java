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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
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
    private static final String PREF_API_URL = "api_url";

    private TextView statusText, userText, answerText;
    private EditText apiUrlInput;
    private Button talkButton, testConnectionButton;

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
        testConnectionButton = findViewById(R.id.testConnectionButton);
        Button saveApiButton = findViewById(R.id.saveApiButton);

        prefs = getSharedPreferences("jarvis", MODE_PRIVATE);
        String saved = prefs.getString(PREF_API_URL, BuildConfig.DEFAULT_API_URL);
        apiUrlInput.setText(normalizeBaseUrl(saved));

        tts = new TextToSpeech(this, this);
        setupSpeechRecognizer();

        talkButton.setOnClickListener(v -> ensureAudioPermissionAndListen());

        saveApiButton.setOnClickListener(v -> {
            String url = normalizeBaseUrl(apiUrlInput.getText().toString());
            if (url.isEmpty()) {
                Toast.makeText(this, "Backend URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            apiUrlInput.setText(url);
            prefs.edit().putString(PREF_API_URL, url).apply();
            Toast.makeText(this, "Backend URL saved", Toast.LENGTH_SHORT).show();
        });

        testConnectionButton.setOnClickListener(v -> testLiveConnection());
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("Speech recognition unavailable");
            return;
        }

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

    private String getBaseUrl() {
        String value = prefs.getString(PREF_API_URL, BuildConfig.DEFAULT_API_URL);
        return normalizeBaseUrl(value);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String url = value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String endpoint(String baseUrl, String path) {
        String base = normalizeBaseUrl(baseUrl);
        if (base.endsWith(path)) return base;
        return base + path;
    }

    private void testLiveConnection() {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty()) {
            answerText.setText("Backend URL সেট করা নেই।");
            return;
        }

        statusText.setText("Testing server…");
        testConnectionButton.setEnabled(false);

        new Thread(() -> {
            String result;
            try {
                HttpURLConnection conn = openConnection(endpoint(baseUrl, "/health"), "GET");
                int code = conn.getResponseCode();
                String body = readResponse(conn, code);
                if (code >= 200 && code < 300) {
                    result = "Live backend connected ✓" +
                            (body.isEmpty() ? "" : "\n" + compact(body));
                } else {
                    result = "Backend responded with HTTP " + code +
                            (body.isEmpty() ? "" : "\n" + compact(body));
                }
                conn.disconnect();
            } catch (Exception e) {
                result = "Backend connection failed: " + safeMessage(e);
            }

            final String finalResult = result;
            runOnUiThread(() -> {
                testConnectionButton.setEnabled(true);
                statusText.setText("Ready");
                answerText.setText(finalResult);
            });
        }).start();
    }

    private void callAiFallback(String text) {
        String baseUrl = getBaseUrl();

        if (baseUrl.isEmpty()) {
            reply("এই প্রশ্নের জন্য AI প্রয়োজন। Backend URL দিন।");
            return;
        }

        statusText.setText("Asking JARVIS AI…");
        talkButton.setEnabled(false);

        new Thread(() -> {
            String response;
            try {
                String delegateUrl = endpoint(baseUrl, "/router/delegate");
                HttpURLConnection conn = openConnection(delegateUrl, "POST");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("message", text);
                body.put("input", text);
                body.put("query", text);
                body.put("source", "android-native-v0.2");

                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }

                int code = conn.getResponseCode();
                String raw = readResponse(conn, code);
                conn.disconnect();

                if (code < 200 || code >= 300) {
                    response = "JARVIS server HTTP " + code +
                            (raw.isEmpty() ? "" : ": " + compact(raw));
                } else {
                    response = parseAiResponse(raw);
                    if (response.isEmpty()) {
                        response = "AI server থেকে ব্যবহারযোগ্য উত্তর পাওয়া যায়নি।";
                    }
                }
            } catch (Exception e) {
                response = "AI server-এ সংযোগ করা যায়নি: " + safeMessage(e);
            }

            final String finalResponse = response;
            runOnUiThread(() -> {
                talkButton.setEnabled(true);
                reply(finalResponse);
            });
        }).start();
    }

    private HttpURLConnection openConnection(String urlString, String method) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "JARVIS-Android/0.2");
        return conn;
    }

    private String readResponse(HttpURLConnection conn, int code) throws Exception {
        if (code == HttpURLConnection.HTTP_NO_CONTENT) return "";
        if (conn.getErrorStream() == null && (code < 200 || code >= 300)) return "";

        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8
        ));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString().trim();
    }

    private String parseAiResponse(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";

        try {
            Object json = value.startsWith("[") ? new JSONArray(value) : new JSONObject(value);
            String found = findReply(json, 0);
            if (!found.isEmpty()) return found;
        } catch (Exception ignored) {
            // Plain-text responses are accepted below.
        }

        if (value.startsWith("<")) return "";
        return value;
    }

    private String findReply(Object node, int depth) {
        if (node == null || depth > 5) return "";

        String[] preferred = {
                "reply", "response", "answer", "output", "message", "text",
                "content", "result", "data"
        };

        try {
            if (node instanceof String) return ((String) node).trim();

            if (node instanceof JSONObject) {
                JSONObject obj = (JSONObject) node;
                for (String key : preferred) {
                    if (obj.has(key) && !obj.isNull(key)) {
                        String found = findReply(obj.get(key), depth + 1);
                        if (!found.isEmpty()) return found;
                    }
                }
            }

            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) {
                    String found = findReply(arr.get(i), depth + 1);
                    if (!found.isEmpty()) return found;
                }
            }
        } catch (Exception ignored) {}

        return "";
    }

    private String compact(String value) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) + "…" : compact;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
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
