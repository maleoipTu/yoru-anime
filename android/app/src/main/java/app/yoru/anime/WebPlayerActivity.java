package app.yoru.anime;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WebPlayerActivity extends Activity {
 static final String HOST="player.yoru.local";
 LinearLayout root,header,controls;FrameLayout surface;WebView web;TextView status,title;Button voiceButton,sourceButton,seriesButton,apply,alternate,previous,next;LinearLayout error;
 View fullscreenView;WebChromeClient.CustomViewCallback fullscreenCallback;WebMessagePort nativePort;List<Episode>videos=new ArrayList<>();List<Episode>playlist=new ArrayList<>();Anime anime;Episode current,selected;Library library;String voice,player,number;boolean dead=false,recreate=false;int generation;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);library=new Library(this);try{JSONObject raw=Api.cached(this,getIntent().getLongExtra("anime_id",0));anime=new Anime(raw);videos=Api.videos(raw);long id=saved==null?getIntent().getLongExtra("video_id",0):saved.getLong("video_id");for(Episode e:videos)if(e.id==id)current=e;if(current==null)throw new IOException();}catch(Exception e){Toast.makeText(this,"Открой карточку аниме заново — данные серий нужно обновить.",Toast.LENGTH_LONG).show();finish();return;}
  root=Ui.root(this);header=Ui.padded(this,14);LinearLayout heading=Ui.row(this);Button back=Ui.button(this,"←",false);back.setContentDescription("К карточке аниме");back.setOnClickListener(v->finish());heading.addView(back);title=Ui.title(this,anime.title,17);title.setMaxLines(2);LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(0,-2,1);t.leftMargin=Ui.dp(this,10);heading.addView(title,t);header.addView(heading);root.addView(header);
  surface=new FrameLayout(this);surface.setBackgroundColor(Color.BLACK);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
  ScrollView controlScroll=new ScrollView(this);controls=Ui.padded(this,14);controlScroll.addView(controls);root.addView(controlScroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,Math.min(300,getResources().getConfiguration().screenHeightDp*.4f))));
  status=Ui.text(this,"Загружаем плеер…",12,Ui.MUTED);controls.addView(status);
  voiceButton=Ui.button(this,"",false);Ui.add(controls,voiceButton,10);LinearLayout selectors=Ui.row(this);sourceButton=Ui.button(this,"",false);seriesButton=Ui.button(this,"",false);selectors.addView(sourceButton,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams se=new LinearLayout.LayoutParams(0,-2,1);se.leftMargin=Ui.dp(this,10);selectors.addView(seriesButton,se);Ui.add(controls,selectors,9);
  apply=Ui.button(this,"▶  Смотреть выбранное",true);Ui.add(controls,apply,10);apply.setOnClickListener(v->{if(selected!=null)navigate(selected);});
  LinearLayout actions=Ui.row(this);previous=Ui.button(this,"← Серия",false);next=Ui.button(this,"Серия →",false);actions.addView(previous,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ne=new LinearLayout.LayoutParams(0,-2,1);ne.leftMargin=Ui.dp(this,10);actions.addView(next,ne);Ui.add(controls,actions,10);previous.setOnClickListener(v->move(-1));next.setOnClickListener(v->move(1));
  alternate=Ui.button(this,"Другой плеер",false);Ui.add(controls,alternate,10);alternate.setOnClickListener(v->{Episode e=Sources.alternative(videos,current);if(e!=null)navigate(e);});Button retry=Ui.button(this,"↻ Повторить загрузку",false);Ui.add(controls,retry,10);retry.setOnClickListener(v->navigate(current));
  voiceButton.setOnClickListener(v->Ui.choose(this,"Озвучка",Sources.voices(videos),voice,s->{voice=s;List<String>ps=Sources.players(videos,voice);if(!ps.contains(player))player=ps.get(0);selection();}));sourceButton.setOnClickListener(v->Ui.choose(this,"Плеер",Sources.players(videos,voice),player,s->{player=s;selection();}));seriesButton.setOnClickListener(v->{List<Episode>es=Sources.episodes(videos,voice,player);List<String>labels=new ArrayList<>();for(Episode e:es)labels.add(e.label());Ui.choose(this,"Серия",labels,"Серия "+number,s->{number=es.get(labels.indexOf(s)).number;selection();});});
  createWeb();navigate(current);
 }
 void createWeb(){web=new WebView(this);web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setGeolocationEnabled(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){Uri u=request.getUrl();if(u.getScheme().equals("https")&&HOST.equals(u.getHost())){try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("player/index.html"));}catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}}return null;}
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){String scheme=r.getUrl().getScheme();return !"https".equals(scheme)||(r.isForMainFrame()&&!HOST.equals(r.getUrl().getHost()));}
   @Override public void onPageFinished(WebView view,String url){if(isCurrentWrapper(Uri.parse(url))&&!dead)connectMessages();}
   @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(isCurrentWrapper(r.getUrl())||r.getUrl().toString().equals(current.url))failed("Не удалось связаться с плеером. Повтори загрузку или выбери другой.");}
   @Override public void onReceivedHttpError(WebView view,WebResourceRequest r,WebResourceResponse e){if(isCurrentWrapper(r.getUrl())||r.getUrl().toString().equals(current.url))failed("Плеер вернул ошибку "+e.getStatusCode()+". Попробуй другой вариант.");}
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail d){recreate=true;failed("Плеер остановился. Нажми «Повторить загрузку».");return true;}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,android.os.Message message){return false;}
   @Override public void onPermissionRequest(PermissionRequest r){r.deny();}
   @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullscreenView!=null){callback.onCustomViewHidden();return;}fullscreenView=view;fullscreenCallback=callback;header.setVisibility(View.GONE);controls.getParent();((View)controls.getParent()).setVisibility(View.GONE);surface.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);root.setPadding(0,0,0,0);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);}
   @Override public void onHideCustomView(){exitFullscreen();}
  });web.setDownloadListener((u,ua,cd,mt,len)->Toast.makeText(WebPlayerActivity.this,"Скачивание видео не поддерживается",Toast.LENGTH_SHORT).show());surface.addView(web,new FrameLayout.LayoutParams(-1,-1));}
 boolean isCurrentWrapper(Uri u){return "https".equals(u.getScheme())&&HOST.equals(u.getHost())&&Integer.toString(generation).equals(u.getQueryParameter("session"));}
 void connectMessages(){if(nativePort!=null)nativePort.close();final int session=generation;WebMessagePort[]ports=web.createWebMessageChannel();nativePort=ports[0];nativePort.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort port,WebMessage message){if(isDestroyed()||session!=generation||dead)return;try{JSONObject o=new JSONObject(message.getData());if(!Integer.toString(session).equals(o.optString("session")))return;String state=o.optString("state");if(state.equals("frame-ready")){status.setText("Плеер загружен. Нажми ▶ в видео.");library.opened(anime,current);}else if(state.equals("playing"))status.setText("Идёт просмотр · "+current.label());else if(state.equals("paused"))status.setText("Пауза · "+current.label());else if(state.equals("ended"))status.setText("Серия закончилась. Можно перейти к следующей.");else if(state.equals("time")){int seconds=o.optInt("value");if(seconds>0)status.setText(String.format(java.util.Locale.ROOT,"Идёт просмотр · %02d:%02d",seconds/60,seconds%60));}else if(state.equals("frame-error")||state.equals("invalid-source"))failed("Не удалось открыть видео. Выбери другой плеер.");}catch(JSONException ignored){}}});web.postWebMessage(new WebMessage("yoru-connect",new WebMessagePort[]{ports[1]}),Uri.parse("https://"+HOST));}
 void navigate(Episode e){if(isDestroyed())return;exitFullscreen();if(nativePort!=null){nativePort.close();nativePort=null;}if(recreate){surface.removeView(web);web.destroy();createWeb();recreate=false;}current=e;dead=false;generation++;if(error!=null){surface.removeView(error);error=null;}web.setVisibility(View.VISIBLE);voice=e.voice;player=e.player;number=e.number;selection();title.setText(anime.title+" · "+e.label());playlist=Sources.episodes(videos,voice,player);int at=playlist.indexOf(e);previous.setEnabled(at>0);next.setEnabled(at>=0&&at<playlist.size()-1);status.setText("Загрузка · "+e.label());web.loadUrl("https://"+HOST+"/index.html?src="+Api.encode(e.url)+"&session="+generation);}
 void selection(){selected=Sources.match(videos,voice,player,number);voiceButton.setText(voice+" ⌄");sourceButton.setText(player+" ⌄");seriesButton.setText(selected==null?"Выбери серию ⌄":selected.label()+" ⌄");apply.setEnabled(selected!=null);apply.setAlpha(selected==null?.5f:1f);alternate.setEnabled(Sources.alternative(videos,current)!=null);if(selected==null)status.setText("Серии "+number+" у этого плеера нет. Выбери номер явно.");else if(selected.id!=current.id)status.setText("Выбор изменён. Нажми «Смотреть выбранное».");}
 void move(int step){int at=playlist.indexOf(current)+step;if(at>=0&&at<playlist.size())navigate(playlist.get(at));}
 void failed(String message){if(isDestroyed())return;dead=true;exitFullscreen();web.setVisibility(View.GONE);status.setText(message);if(error!=null)surface.removeView(error);error=Ui.padded(this,22);error.setGravity(Gravity.CENTER);error.addView(Ui.title(this,"Видео недоступно",21));Ui.add(error,Ui.text(this,message,14,Ui.MUTED),12);Button retry=Ui.button(this,"Повторить",true);Ui.add(error,retry,18);retry.setOnClickListener(v->navigate(current));surface.addView(error,new FrameLayout.LayoutParams(-1,-1));}
 void exitFullscreen(){if(fullscreenView==null)return;surface.removeView(fullscreenView);fullscreenView=null;if(fullscreenCallback!=null){fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}header.setVisibility(View.VISIBLE);((View)controls.getParent()).setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(0);root.requestApplyInsets();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}
 @Override public void onBackPressed(){if(fullscreenView!=null)exitFullscreen();else super.onBackPressed();}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);View panel=(View)controls.getParent();android.view.ViewGroup.LayoutParams lp=panel.getLayoutParams();lp.height=Ui.dp(this,Math.min(300,c.screenHeightDp*.4f));panel.setLayoutParams(lp);}
 @Override protected void onSaveInstanceState(Bundle b){b.putLong("video_id",current.id);super.onSaveInstanceState(b);}
 @Override protected void onPause(){super.onPause();if(web!=null)web.onPause();}
 @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
 @Override protected void onDestroy(){if(nativePort!=null)nativePort.close();if(web!=null){web.stopLoading();surface.removeView(web);web.destroy();}super.onDestroy();}
}

