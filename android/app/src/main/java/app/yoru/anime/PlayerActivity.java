package app.yoru.anime;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/** Native surface and controls: no provider WebView or advertising scripts. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PlayerActivity extends Activity {
    LinearLayout root,header,overlay,body,overlayTop;
    ScrollView details;
    FrameLayout surface;
    PlayerView videoView;
    TextView title,status,clock,episodeTag;
    Button play,quality,speed,full,voiceButton,sourceButton,seriesButton,apply,previous,next;
    SeekBar timeline;
    ProgressBar loading;
    LinearLayout error;
    ExoPlayer engine;
    Anime anime; Episode current,selected;
    List<Episode> videos=new ArrayList<>(),playlist=new ArrayList<>();
    Library library;
    StreamResolver.Result resolved;
    int rendition,generation;
    String voice,provider,number;
    Future<?> pending;
    boolean fullscreen,dragging,started,foreground,desiredPlay=true,failed,wasReady;
    long position,lastSave,hideAt;
    float playbackSpeed=1f;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable tick=new Runnable(){public void run(){if(isDestroyed())return;updateProgress();if(engine!=null&&engine.isPlaying()&&!dragging&&SystemClock.uptimeMillis()>hideAt)overlay.setVisibility(View.GONE);handler.postDelayed(this,500);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);library=new Library(this);
        try{
            JSONObject raw=Api.cached(this,getIntent().getLongExtra("anime_id",0));anime=new Anime(raw);videos=Api.videos(raw);
            long id=saved==null?getIntent().getLongExtra("video_id",0):saved.getLong("video_id");
            for(Episode e:videos)if(e.id==id)current=e;
            if(current==null)throw new IOException();
            playbackSpeed=library.prefs.getFloat("native_speed",1f);
            if(playbackSpeed<.5f||playbackSpeed>2f)playbackSpeed=1f;
        }catch(Exception e){Toast.makeText(this,"Открой карточку аниме заново — данные серий нужно обновить.",Toast.LENGTH_LONG).show();finish();return;}
        buildUi();navigate(current);handler.post(tick);
        if(saved!=null&&saved.getBoolean("fullscreen"))setFullscreen(true);
    }
    void buildUi(){
        root=Ui.root(this);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(bars.left,fullscreen?0:bars.top,bars.right,fullscreen?0:bars.bottom);return insets;});
        header=Ui.padded(this,16);LinearLayout heading=Ui.row(this);
        Button back=Ui.button(this,"‹",false);back.setContentDescription("К карточке аниме");back.setOnClickListener(v->finish());heading.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        LinearLayout names=Ui.column(this);names.addView(Ui.text(this,"Y O R U  /  ПРОСМОТР",11,Ui.ACCENT));title=Ui.title(this,anime.title,18);title.setMaxLines(2);names.addView(title);
        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.leftMargin=Ui.dp(this,14);heading.addView(names,nameParams);header.addView(heading);root.addView(header);
        surface=new FrameLayout(this);surface.setBackgroundColor(Color.BLACK);root.addView(surface,new LinearLayout.LayoutParams(-1,Ui.dp(this,220)));
        videoView=new PlayerView(this);videoView.setUseController(false);videoView.setShutterBackgroundColor(Color.BLACK);surface.addView(videoView,new FrameLayout.LayoutParams(-1,-1));
        View touch=new View(this);surface.addView(touch,new FrameLayout.LayoutParams(-1,-1));
        GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){if(overlay.getVisibility()==View.VISIBLE&&engine!=null&&engine.isPlaying())overlay.setVisibility(View.GONE);else showControls();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){seek(e.getX()<surface.getWidth()/2f?-10000:10000);return true;}
        });
        touch.setContentDescription("Видео. Касание — управление; двойное касание слева или справа — перемотка на 10 секунд.");touch.setOnClickListener(v->showControls());touch.setOnTouchListener((v,event)->gestures.onTouchEvent(event));
        overlay=Ui.padded(this,12);overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xA608080D,0x1808080D,0xE608080D}));surface.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlayTop=Ui.row(this);episodeTag=Ui.title(this,"",13);overlayTop.addView(episodeTag,new LinearLayout.LayoutParams(0,-2,1));
        quality=chip("Качество",v->chooseQuality());quality.setContentDescription("Качество видео");overlayTop.addView(quality);
        speed=chip(speedLabel(),v->chooseSpeed());speed.setContentDescription("Скорость воспроизведения");overlayTop.addView(speed);overlay.addView(overlayTop);
        LinearLayout center=Ui.row(this);center.setGravity(Gravity.CENTER);Button rewind=chip("−10",v->seek(-10000));rewind.setContentDescription("Назад на 10 секунд");center.addView(rewind);
        play=Ui.button(this,"▶",true);play.setTextSize(22);play.setContentDescription("Воспроизвести");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(Ui.dp(this,66),Ui.dp(this,54));pp.setMargins(Ui.dp(this,22),0,Ui.dp(this,22),0);center.addView(play,pp);play.setOnClickListener(v->{if(engine!=null){if(engine.getPlaybackState()==Player.STATE_ENDED)engine.seekTo(0);if(engine.isPlaying()){desiredPlay=false;engine.pause();}else{desiredPlay=true;engine.play();}showControls();}});
        Button forward=chip("+10",v->seek(10000));forward.setContentDescription("Вперёд на 10 секунд");center.addView(forward);overlay.addView(center,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=Ui.row(this);clock=Ui.text(this,"00:00 / --:--",12,Ui.TEXT);bottom.addView(clock,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(chip("Серии",v->chooseSeries()));full=chip("На весь экран",v->setFullscreen(!fullscreen));bottom.addView(full);overlay.addView(bottom);
        timeline=new SeekBar(this);timeline.setMax(10000);timeline.setPadding(0,0,0,0);timeline.setContentDescription("Позиция в серии");overlay.addView(timeline,new LinearLayout.LayoutParams(-1,Ui.dp(this,32)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;showControls();}public void onProgressChanged(SeekBar b,int p,boolean user){if(user&&engine!=null&&engine.getDuration()>0)clock.setText(PlaybackProgress.time(engine.getDuration()*p/10000)+" / "+PlaybackProgress.time(engine.getDuration()));}public void onStopTrackingTouch(SeekBar b){if(engine!=null&&engine.getDuration()>0)engine.seekTo(engine.getDuration()*b.getProgress()/10000);dragging=false;showControls();}});
        loading=new ProgressBar(this);surface.addView(loading,new FrameLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42),Gravity.CENTER));
        details=new ScrollView(this);body=Ui.padded(this,18);details.addView(body);root.addView(details,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(Ui.text(this,"YORU PLAYER",11,Ui.ACCENT));status=Ui.text(this,"Получаем видео…",14,Ui.MUTED);Ui.add(body,status,8);
        LinearLayout navigation=Ui.row(this);previous=Ui.button(this,"‹ Предыдущая",false);next=Ui.button(this,"Следующая ›",true);navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=Ui.dp(this,10);navigation.addView(next,np);Ui.add(body,navigation,20);previous.setOnClickListener(v->move(-1));next.setOnClickListener(v->move(1));
        LinearLayout card=Ui.padded(this,14);card.setBackground(Ui.shape(this,Ui.PANEL,16));Ui.add(body,card,18);card.addView(Ui.title(this,"Настроить просмотр",16));
        voiceButton=Ui.button(this,"",false);Ui.add(card,voiceButton,12);sourceButton=Ui.button(this,"",false);Ui.add(card,sourceButton,8);seriesButton=Ui.button(this,"",false);Ui.add(card,seriesButton,8);
        apply=Ui.button(this,"Смотреть выбранное",true);Ui.add(card,apply,12);apply.setOnClickListener(v->{if(selected!=null)navigate(selected);});
        voiceButton.setOnClickListener(v->Ui.choose(this,"Озвучка",Sources.voices(videos),voice,s->{voice=s;List<String> choices=Sources.players(videos,voice);if(!choices.contains(provider))provider=choices.get(0);selection();}));
        sourceButton.setOnClickListener(v->Ui.choose(this,"Источник видео",Sources.players(videos,voice),provider,s->{provider=s;selection();}));seriesButton.setOnClickListener(v->chooseSeries());
        Button retry=Ui.button(this,"↻ Загрузить поток заново",false);Ui.add(body,retry,18);retry.setOnClickListener(v->navigate(current));
        Button site=Ui.button(this,"Плеер сайта · может быть реклама",false);Ui.add(body,site,10);site.setOnClickListener(v->openWebsite());
        Ui.add(body,Ui.text(this,"Двойное касание по краям видео — ±10 секунд. Позиция сохраняется автоматически.",12,Ui.MUTED),14);
        root.post(this::fitSurface);
    }
    Button chip(String text,View.OnClickListener action){Button b=Ui.button(this,text,false);b.setTextSize(12);b.setPadding(Ui.dp(this,9),0,Ui.dp(this,9),0);b.setMinHeight(Ui.dp(this,48));b.setBackground(Ui.shape(this,0x7A201C2D,9));b.setOnClickListener(action);return b;}
    void navigate(Episode episode){
        savePosition();releaseEngine();if(pending!=null)pending.cancel(true);generation++;int session=generation;current=episode;resolved=null;failed=false;wasReady=false;desiredPlay=true;position=library.position(episode.id);removeError();
        voice=episode.voice;provider=episode.player;number=episode.number;playlist=Sources.episodes(videos,voice,provider);selection();int index=playlist.indexOf(episode);previous.setEnabled(index>0);next.setEnabled(index>=0&&index<playlist.size()-1);
        episodeTag.setText("YORU  ·  "+episode.label());status.setText("Получаем видеопоток · "+episode.label());clock.setText("00:00 / --:--");timeline.setProgress(0);quality.setText("Качество");quality.setEnabled(false);play.setEnabled(false);timeline.setEnabled(false);loading.setVisibility(View.VISIBLE);showControls();
        pending=Api.IO.submit(()->{
            try{StreamResolver.Result result=StreamResolver.resolve(episode.url);runOnUiThread(()->{if(isDestroyed()||session!=generation)return;resolved=result;rendition=0;String preferred=library.prefs.getString("native_quality","");for(int i=0;i<result.streams.size();i++)if(result.streams.get(i).label.equals(preferred))rendition=i;if(started)startEngine();});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed()||session!=generation)return;showError(e instanceof IOException?e.getMessage():"Не удалось получить видеопоток. Попробуй другой источник.");});}
        });
    }
    void startEngine(){
        if(resolved==null||!started||engine!=null||failed)return;
        StreamResolver.Stream stream=resolved.streams.get(rendition);int session=generation;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(StreamResolver.USER_AGENT).setConnectTimeoutMs(12000).setReadTimeoutMs(18000).setAllowCrossProtocolRedirects(false).setDefaultRequestProperties(Collections.singletonMap("Referer",resolved.referer));
        ExoPlayer created=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();engine=created;videoView.setPlayer(created);
        created.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);created.setHandleAudioBecomingNoisy(true);created.setPlaybackSpeed(playbackSpeed);
        created.addListener(new Player.Listener(){
            boolean valid(){return !isDestroyed()&&session==generation&&engine==created;}
            @Override public void onPlaybackStateChanged(int state){if(!valid())return;loading.setVisibility(state==Player.STATE_BUFFERING?View.VISIBLE:View.GONE);if(state==Player.STATE_READY){if(!wasReady){position=PlaybackProgress.resume(position,created.getDuration());created.seekTo(position);wasReady=true;}play.setEnabled(true);timeline.setEnabled(created.isCurrentMediaItemSeekable());quality.setEnabled(resolved.streams.size()>1);status.setText(current.label()+" · "+current.voice+" · "+stream.label);library.opened(anime,current);}else if(state==Player.STATE_ENDED){desiredPlay=false;position=0;library.position(current.id,0);showControls();status.setText("Серия закончилась"+(next.isEnabled()?". Можно включить следующую.":"."));}}
            @Override public void onIsPlayingChanged(boolean playing){if(!valid())return;play.setText(playing?"Ⅱ":"▶");play.setContentDescription(playing?"Пауза":"Воспроизвести");surface.setKeepScreenOn(playing);if(!playing)showControls();}
            @Override public void onPlayerError(PlaybackException e){if(valid()){savePosition();showError("Не удалось воспроизвести поток. Загрузи его заново или выбери другое качество.");}}
        });
        quality.setText(stream.label+" ⌄");speed.setText(speedLabel());wasReady=false;created.setMediaItem(new MediaItem.Builder().setUri(stream.url).setMimeType(stream.mime).build(),position);created.prepare();created.setPlayWhenReady(foreground&&desiredPlay);showControls();
    }
    void chooseQuality(){if(resolved==null)return;List<String>labels=new ArrayList<>();for(StreamResolver.Stream stream:resolved.streams)labels.add(stream.label);Ui.choose(this,"Качество видео",labels,resolved.streams.get(rendition).label,label->{int index=labels.indexOf(label);if(index==rendition&&!failed)return;savePosition();releaseEngine();rendition=index;failed=false;wasReady=false;removeError();loading.setVisibility(View.VISIBLE);library.prefs.edit().putString("native_quality",label).apply();startEngine();});}
    String speedLabel(){return String.format(Locale.ROOT,"%s×",playbackSpeed==(int)playbackSpeed?Integer.toString((int)playbackSpeed):Float.toString(playbackSpeed));}
    void chooseSpeed(){List<String>labels=Arrays.asList("0.5×","0.75×","1×","1.25×","1.5×","1.75×","2×");Ui.choose(this,"Скорость воспроизведения",labels,speedLabel(),label->{playbackSpeed=Float.parseFloat(label.replace("×",""));library.prefs.edit().putFloat("native_speed",playbackSpeed).apply();if(engine!=null)engine.setPlaybackSpeed(playbackSpeed);speed.setText(speedLabel());showControls();});}
    void chooseSeries(){List<Episode>options=Sources.episodes(videos,voice,provider);List<String>labels=new ArrayList<>();for(Episode episode:options)labels.add(episode.label());Ui.choose(this,"Серия · "+voice,labels,"Серия "+number,label->{Episode choice=options.get(labels.indexOf(label));number=choice.number;selection();if(fullscreen)navigate(choice);});}
    void selection(){selected=Sources.match(videos,voice,provider,number);voiceButton.setText(voice+" ⌄");sourceButton.setText(provider+" ⌄");seriesButton.setText(selected==null?"Выбери серию ⌄":selected.label()+" ⌄");apply.setEnabled(selected!=null);apply.setAlpha(selected==null?.5f:1f);if(selected==null)status.setText("Серии "+number+" у этого источника нет. Выбери доступную серию.");else if(current!=null&&selected.id!=current.id)status.setText("Выбор изменён. Нажми «Смотреть выбранное».");}
    void move(int step){int index=playlist.indexOf(current)+step;if(index>=0&&index<playlist.size())navigate(playlist.get(index));}
    void seek(long delta){if(engine!=null&&engine.isCurrentMediaItemSeekable()){engine.seekTo(PlaybackProgress.seek(engine.getCurrentPosition(),delta,engine.getDuration()));showControls();}}
    void showControls(){if(overlay==null)return;overlay.setVisibility(View.VISIBLE);hideAt=SystemClock.uptimeMillis()+4000;}
    void updateProgress(){if(engine==null)return;long now=engine.getCurrentPosition(),duration=engine.getDuration();if(!dragging){clock.setText(PlaybackProgress.time(now)+" / "+(duration>0?PlaybackProgress.time(duration):"--:--"));timeline.setProgress(duration>0?(int)Math.min(10000,now*10000/duration):0);timeline.setSecondaryProgress(duration>0?(int)Math.min(10000,engine.getBufferedPosition()*10000/duration):0);}if(SystemClock.uptimeMillis()-lastSave>=5000){savePosition();lastSave=SystemClock.uptimeMillis();}}
    void savePosition(){if(engine!=null&&wasReady){position=engine.getPlaybackState()==Player.STATE_ENDED?0:PlaybackProgress.resume(engine.getCurrentPosition(),engine.getDuration());library.position(current.id,position);}}
    void releaseEngine(){if(engine!=null){ExoPlayer old=engine;engine=null;videoView.setPlayer(null);old.release();}surface.setKeepScreenOn(false);}
    void removeError(){if(error!=null){surface.removeView(error);error=null;}}
    void showError(String message){failed=true;releaseEngine();loading.setVisibility(View.GONE);play.setEnabled(false);timeline.setEnabled(false);if(fullscreen)setFullscreen(false);status.setText(message);removeError();error=Ui.padded(this,18);error.setGravity(Gravity.CENTER);error.setBackgroundColor(0xFA111117);error.addView(Ui.title(this,"Не удалось открыть видео",18));TextView detail=Ui.text(this,message,13,Ui.MUTED);detail.setGravity(Gravity.CENTER);Ui.add(error,detail,8);LinearLayout actions=Ui.row(this);Button retry=Ui.button(this,"Повторить",true);actions.addView(retry,new LinearLayout.LayoutParams(0,-2,1));retry.setOnClickListener(v->navigate(current));if(resolved!=null&&resolved.streams.size()>1){Button otherQuality=Ui.button(this,"Качество",false);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,-2,1);qp.leftMargin=Ui.dp(this,8);actions.addView(otherQuality,qp);otherQuality.setOnClickListener(v->chooseQuality());}Ui.add(error,actions,12);surface.addView(error,new FrameLayout.LayoutParams(-1,-1));}
    void openWebsite(){savePosition();if(engine!=null)engine.pause();startActivity(new Intent(this,WebPlayerActivity.class).putExtra("anime_id",anime.id).putExtra("video_id",current.id));}
    void setFullscreen(boolean value){fullscreen=value;header.setVisibility(value?View.GONE:View.VISIBLE);details.setVisibility(value?View.GONE:View.VISIBLE);full.setText(value?"Свернуть":"На весь экран");getWindow().getDecorView().setSystemUiVisibility(value?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:0);root.requestApplyInsets();setRequestedOrientation(value?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);fitSurface();showControls();}
    void fitSurface(){if(surface==null)return;LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)surface.getLayoutParams();if(fullscreen){params.height=0;params.weight=1;}else{int width=root.getWidth()>0?root.getWidth():getResources().getDisplayMetrics().widthPixels;int height=getResources().getConfiguration().screenHeightDp;params.height=Ui.dp(this,Math.max(216,Math.min(width/getResources().getDisplayMetrics().density*9/16,height*.48f)));params.weight=0;}surface.setLayoutParams(params);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);root.post(this::fitSurface);}
    @Override public void onBackPressed(){if(fullscreen)setFullscreen(false);else super.onBackPressed();}
    @Override protected void onStart(){super.onStart();started=true;if(resolved!=null)startEngine();}
    @Override protected void onResume(){super.onResume();foreground=true;if(engine!=null)engine.setPlayWhenReady(desiredPlay);}
    @Override protected void onPause(){foreground=false;if(engine!=null){desiredPlay=engine.getPlayWhenReady();savePosition();engine.pause();}super.onPause();}
    @Override protected void onStop(){started=false;savePosition();releaseEngine();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){if(current!=null)state.putLong("video_id",current.id);state.putBoolean("fullscreen",fullscreen);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);generation++;if(pending!=null)pending.cancel(true);if(surface!=null){savePosition();releaseEngine();}super.onDestroy();}
}
