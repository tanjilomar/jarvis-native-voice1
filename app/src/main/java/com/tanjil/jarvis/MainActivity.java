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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        statusText=findViewById(R.id.statusText); userText=findViewById(R.id.userText); answerText=findViewById(R.id.answerText);
        apiUrlInput=findViewById(R.id.apiUrlInput); talkButton=findViewById(R.id.talkButton); testConnectionButton=findViewById(R.id.testConnectionButton);
        Button saveApiButton=findViewById(R.id.saveApiButton);
        prefs=getSharedPreferences("jarvis",MODE_PRIVATE); apiUrlInput.setText(normalizeBaseUrl(prefs.getString(PREF_API_URL,BuildConfig.DEFAULT_API_URL)));
        tts=new TextToSpeech(this,this); setupSpeechRecognizer();
        talkButton.setOnClickListener(v->ensureAudioPermissionAndListen());
        saveApiButton.setOnClickListener(v->{String url=normalizeBaseUrl(apiUrlInput.getText().toString()); if(url.isEmpty()){Toast.makeText(this,"Backend URL cannot be empty",Toast.LENGTH_SHORT).show();return;} apiUrlInput.setText(url);prefs.edit().putString(PREF_API_URL,url).apply();Toast.makeText(this,"Backend URL saved",Toast.LENGTH_SHORT).show();});
        testConnectionButton.setOnClickListener(v->testLiveConnection());
    }

    private void setupSpeechRecognizer(){if(!SpeechRecognizer.isRecognitionAvailable(this)){statusText.setText("Speech recognition unavailable");return;} speechRecognizer=SpeechRecognizer.createSpeechRecognizer(this); speechRecognizer.setRecognitionListener(new RecognitionListener(){
        @Override public void onReadyForSpeech(Bundle p){statusText.setText("Listening…");} @Override public void onBeginningOfSpeech(){statusText.setText("Listening…");}
        @Override public void onRmsChanged(float r){} @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){statusText.setText("Processing…");}
        @Override public void onError(int e){statusText.setText("Ready");answerText.setText("I couldn't hear that clearly. Please try again.");}
        @Override public void onResults(Bundle r){ArrayList<String> m=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(m!=null&&!m.isEmpty()){String text=m.get(0);userText.setText(text);handleCommand(text);}}
        @Override public void onPartialResults(Bundle p){} @Override public void onEvent(int e,Bundle p){}
    });}

    private void ensureAudioPermissionAndListen(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}startListening();}
    private void startListening(){if(speechRecognizer==null)return;Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"bn-BD");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"bn-BD");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);speechRecognizer.startListening(i);}

    private void handleCommand(String raw){String text=raw.trim(),lower=text.toLowerCase(Locale.ROOT);
        if(containsAny(lower,"সময়","time","কটা বাজে","কয়টা বাজে")){reply("এখন সময় "+new SimpleDateFormat("hh:mm a",Locale.getDefault()).format(new Date()));return;}
        if(containsAny(lower,"তারিখ","date","আজ কত তারিখ","আজকের তারিখ")){reply("আজ "+new SimpleDateFormat("dd MMMM yyyy",Locale.getDefault()).format(new Date()));return;}
        if(containsAny(lower,"হ্যালো","hello","hi","হাই","jarvis","জারভিস")){reply("জি, আমি শুনছি।");return;}
        if(containsAny(lower,"ধন্যবাদ","thank you","thanks")){reply("স্বাগতম।");return;}
        if(containsAny(lower,"চুপ","stop speaking","stop talking")){if(tts!=null)tts.stop();answerText.setText("ঠিক আছে।");statusText.setText("Ready");return;}
        callAiFallback(text);
    }
    private boolean containsAny(String t,String...k){for(String x:k)if(t.contains(x))return true;return false;}
    private String getBaseUrl(){return normalizeBaseUrl(prefs.getString(PREF_API_URL,BuildConfig.DEFAULT_API_URL));}
    private String normalizeBaseUrl(String v){if(v==null)return "";String u=v.trim();while(u.endsWith("/"))u=u.substring(0,u.length()-1);return u;}
    private String endpoint(String b,String p){b=normalizeBaseUrl(b);return b.endsWith(p)?b:b+p;}

    private void testLiveConnection(){String base=getBaseUrl();if(base.isEmpty()){answerText.setText("Backend URL সেট করা নেই।");return;}statusText.setText("Testing server…");testConnectionButton.setEnabled(false);new Thread(()->{String result;try{HttpURLConnection c=openConnection(endpoint(base,"/health"),"GET");int code=c.getResponseCode();String body=readResponse(c,code);result=code>=200&&code<300?"Live backend connected ✓"+(body.isEmpty()?"":"\n"+compact(body)):"Backend responded with HTTP "+code+(body.isEmpty()?"":"\n"+compact(body));c.disconnect();}catch(Exception e){result="Backend connection failed: "+safeMessage(e);}String f=result;runOnUiThread(()->{testConnectionButton.setEnabled(true);statusText.setText("Ready");answerText.setText(f);});}).start();}

    private void callAiFallback(String text){String base=getBaseUrl();if(base.isEmpty()){reply("এই প্রশ্নের জন্য AI প্রয়োজন। Backend URL দিন।");return;}statusText.setText("Asking JARVIS AI…");talkButton.setEnabled(false);new Thread(()->{String response;try{HttpURLConnection c=openConnection(endpoint(base,"/router/delegate"),"POST");c.setDoOutput(true);JSONObject body=new JSONObject();body.put("prompt",text);body.put("text",text);body.put("message",text);body.put("input",text);body.put("query",text);body.put("source","android-native-v0.2");byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(data);}int code=c.getResponseCode();String raw=readResponse(c,code);c.disconnect();if(code<200||code>=300)response="JARVIS server HTTP "+code+(raw.isEmpty()?"":": "+compact(raw));else{response=parseAiResponse(raw);if(response.isEmpty())response="AI server থেকে ব্যবহারযোগ্য উত্তর পাওয়া যায়নি।";}}catch(Exception e){response="AI server-এ সংযোগ করা যায়নি: "+safeMessage(e);}String f=response;runOnUiThread(()->{talkButton.setEnabled(true);reply(f);});}).start();}

    private HttpURLConnection openConnection(String u,String m)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setRequestMethod(m);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/json, text/plain, */*");c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("User-Agent","JARVIS-Android/0.2");return c;}
    private String readResponse(HttpURLConnection c,int code)throws Exception{if(code==HttpURLConnection.HTTP_NO_CONTENT)return "";if(c.getErrorStream()==null&&(code<200||code>=300))return "";BufferedReader br=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();return sb.toString().trim();}
    private String parseAiResponse(String raw){if(raw==null)return "";String v=raw.trim();if(v.isEmpty())return "";try{Object j=v.startsWith("[")?new JSONArray(v):new JSONObject(v);String f=findReply(j,0);if(!f.isEmpty())return f;}catch(Exception ignored){}return v.startsWith("<")?"":v;}
    private String findReply(Object n,int d){if(n==null||d>5)return "";String[] keys={"reply","response","answer","output","message","text","content","result","data"};try{if(n instanceof String)return((String)n).trim();if(n instanceof JSONObject){JSONObject o=(JSONObject)n;for(String k:keys)if(o.has(k)&&!o.isNull(k)){String f=findReply(o.get(k),d+1);if(!f.isEmpty())return f;}}if(n instanceof JSONArray){JSONArray a=(JSONArray)n;for(int i=0;i<a.length();i++){String f=findReply(a.get(i),d+1);if(!f.isEmpty())return f;}}}catch(Exception ignored){}return "";}
    private String compact(String v){String c=v==null?"":v.replaceAll("\\s+"," ").trim();return c.length()>240?c.substring(0,240)+"…":c;}
    private String safeMessage(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private void reply(String t){statusText.setText("Ready");answerText.setText(t);speak(t);}private void speak(String t){if(tts!=null)tts.speak(t,TextToSpeech.QUEUE_FLUSH,null,"jarvis_reply");}
    @Override public void onInit(int s){if(s==TextToSpeech.SUCCESS){int r=tts.setLanguage(new Locale("bn","BD"));if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.US);tts.setSpeechRate(1f);}}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_AUDIO&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startListening();}
    @Override protected void onDestroy(){if(speechRecognizer!=null)speechRecognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
