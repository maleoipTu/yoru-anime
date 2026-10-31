package app.yoru.anime;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class DetailActivity extends Activity {
 Anime anime;List<Episode> videos=new ArrayList<>();String voice="",player="",number="1";Episode selected;Library library;
 TextView status,description,animeTitle,metadata,score,genres;Button voiceButton,playerButton,seriesButton,watch,favorite,retry,expand;ImageView poster,backdrop;
 boolean expanded;long lastReadVideoId;int generation;Social.AnimeCard social;boolean firstResume=true;
 // Серии плитками с отметками «просмотрено», студии и порядок просмотра франшизы.
 LinearLayout episodesCard,episodesBox,studiosBox,franchiseBox;TextView watchedText;JSONArray studios=new JSONArray(),franchise=new JSONArray();
 final Progress.Listener progressListener=id->runOnUiThread(()->{if(!isDestroyed()){renderEpisodes();renderFranchise();}});
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);library=new Library(this);try{anime=new Anime(new JSONObject(getIntent().getStringExtra("anime")));}catch(Exception exception){finish();return;}
  if(saved!=null){voice=saved.getString("voice","");player=saved.getString("player","");number=saved.getString("number","1");expanded=saved.getBoolean("expanded");lastReadVideoId=saved.getLong("last_read_video");}
  else{JSONObject last=library.last(anime.id);lastReadVideoId=last==null?0:last.optLong("video_id");}
  LinearLayout window=Ui.root(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=Ui.column(this);scroll.addView(content);window.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  // Шапка с размытым постером на фоне: панель действий, постер и главное об аниме.
  FrameLayout hero=new FrameLayout(this);content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
  backdrop=new ImageView(this);backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);backdrop.setAlpha(.42f);if(android.os.Build.VERSION.SDK_INT>=31)backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(Ui.dp(this,24),Ui.dp(this,24),android.graphics.Shader.TileMode.CLAMP));hero.addView(backdrop,new FrameLayout.LayoutParams(-1,Ui.dp(this,280)));
  View fade=new View(this);fade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x330D0E13,0xB30D0E13,Ui.BG}));hero.addView(fade,new FrameLayout.LayoutParams(-1,Ui.dp(this,280)));
  LinearLayout heroContent=Ui.column(this);hero.addView(heroContent,new FrameLayout.LayoutParams(-1,-2));
  LinearLayout toolbar=Ui.row(this);toolbar.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));Button back=Ui.iconButton(this,"back","Назад в каталог");back.setBackground(Ui.ripple(this,Ui.SCRIM,24));back.setOnClickListener(v->finish());toolbar.addView(back);TextView heading=Ui.title(this,"Об аниме",16);heading.setGravity(Gravity.CENTER);heading.setTextColor(Ui.MUTED);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));favorite=Ui.iconButton(this,"heart","Добавить в избранное");favorite.setBackground(Ui.ripple(this,Ui.SCRIM,24));favorite.setOnClickListener(v->{library.toggle(anime);updateFavorite();});toolbar.addView(favorite);heroContent.addView(toolbar);
  LinearLayout heroRow=Ui.row(this);heroRow.setGravity(Gravity.BOTTOM);heroRow.setPadding(Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20));poster=new ImageView(this);Ui.round(poster,16);poster.setElevation(Ui.dp(this,8));poster.setContentDescription("Постер: "+anime.title);heroRow.addView(poster,new LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,172)));
  LinearLayout about=Ui.column(this);LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(0,-2,1);infoParams.leftMargin=Ui.dp(this,16);heroRow.addView(about,infoParams);
  animeTitle=Ui.title(this,anime.title,22);animeTitle.setMaxLines(4);animeTitle.setEllipsize(TextUtils.TruncateAt.END);animeTitle.setLineSpacing(Ui.dp(this,2),1);if(android.os.Build.VERSION.SDK_INT>=28)animeTitle.setAccessibilityHeading(true);about.addView(animeTitle);
  score=Ui.text(this,anime.score(),14,Ui.TEXT);score.setTypeface(Ui.MEDIUM);Ui.add(about,score,12);metadata=Ui.text(this,anime.meta(),13,Ui.MUTED);Ui.add(about,metadata,8);genres=Ui.text(this,"",13,Ui.MUTED);genres.setMaxLines(2);genres.setEllipsize(TextUtils.TruncateAt.END);Ui.add(about,genres,6);heroContent.addView(heroRow);
  LinearLayout watchCard=Ui.padded(this,16);watchCard.setBackground(Ui.outlined(this,Ui.PANEL,22));TextView watchTitle=Ui.title(this,"Как будем смотреть?",17);if(android.os.Build.VERSION.SDK_INT>=28)watchTitle.setAccessibilityHeading(true);watchCard.addView(watchTitle);
  voiceButton=Ui.field(this,"Озвучка");playerButton=Ui.field(this,"Источник");seriesButton=Ui.field(this,"Серия");voiceButton.setEnabled(false);playerButton.setEnabled(false);seriesButton.setEnabled(false);Ui.add(watchCard,voiceButton,14);
  LinearLayout choices=Ui.row(this);choices.addView(playerButton,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=Ui.dp(this,8);choices.addView(seriesButton,sp);Ui.add(watchCard,choices,8);
  watch=Ui.button(this,"Смотреть",true);watch.setEnabled(false);watch.setTextSize(16);watch.setMinHeight(Ui.dp(this,56));watch.setMinimumHeight(Ui.dp(this,56));Ui.icon(watch,"play",Ui.ON_ACCENT);watch.setCompoundDrawablePadding(Ui.dp(this,8));Ui.add(watchCard,watch,16);watch.setOnClickListener(v->{if(selected!=null)startActivity(new Intent(this,StreamResolver.supports(selected.url)?PlayerActivity.class:WebPlayerActivity.class).putExtra("anime_id",anime.id).putExtra("video_id",selected.id));});
  status=Ui.text(this,"Загружаем серии…",13,Ui.MUTED);status.setGravity(Gravity.CENTER_HORIZONTAL);Ui.add(watchCard,status,12);
  retry=Ui.tonal(this,"Повторить загрузку");Ui.icon(retry,"retry",Ui.ACCENT);retry.setCompoundDrawablePadding(Ui.dp(this,8));retry.setVisibility(View.GONE);Ui.add(watchCard,retry,12);retry.setOnClickListener(v->load());LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,28));content.addView(watchCard,cardParams);
  episodesCard=Ui.padded(this,16);episodesCard.setBackground(Ui.outlined(this,Ui.PANEL,22));episodesCard.setVisibility(View.GONE);LinearLayout eh=Ui.row(this);TextView et=Ui.title(this,"Серии",17);if(android.os.Build.VERSION.SDK_INT>=28)et.setAccessibilityHeading(true);eh.addView(et);watchedText=Ui.text(this,"",12,Ui.MUTED);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=Ui.dp(this,10);eh.addView(watchedText,wp);episodesCard.addView(eh);
  Ui.add(episodesCard,Ui.text(this,"Кружок в углу или долгое нажатие — отметить серию просмотренной.",12,Ui.MUTED),6);episodesBox=Ui.column(this);Ui.add(episodesCard,episodesBox,12);
  LinearLayout.LayoutParams ecp=new LinearLayout.LayoutParams(-1,-2);ecp.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,28));content.addView(episodesCard,ecp);
  social=new Social.AnimeCard(this,anime);LinearLayout.LayoutParams socialParams=new LinearLayout.LayoutParams(-1,-2);socialParams.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,28));content.addView(social.root,socialParams);
  LinearLayout synopsis=Ui.column(this);synopsis.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,28));TextView synopsisTitle=Ui.title(this,"О чём эта история",17);if(android.os.Build.VERSION.SDK_INT>=28)synopsisTitle.setAccessibilityHeading(true);synopsis.addView(synopsisTitle);description=Ui.text(this,"",15,0xFFCFCDDC);description.setLineSpacing(Ui.dp(this,5),1);description.setEllipsize(TextUtils.TruncateAt.END);Ui.add(synopsis,description,12);expand=Ui.textButton(this,"Читать дальше");expand.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);expand.setPadding(0,0,Ui.dp(this,12),0);expand.setOnClickListener(v->{expanded=!expanded;updateDescription();});Ui.add(synopsis,expand,4);studiosBox=Ui.column(this);Ui.add(synopsis,studiosBox,10);
  Button site=Ui.button(this,"Открыть на YummyAnime",false);site.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24FFFFFF),Ui.outlined(this,android.graphics.Color.TRANSPARENT,16),Ui.shape(this,android.graphics.Color.WHITE,16)));Ui.icon(site,"external",Ui.ACCENT);site.setCompoundDrawablePadding(Ui.dp(this,8));Ui.add(synopsis,site,16);site.setOnClickListener(v->Ui.site(this,anime));content.addView(synopsis);
  franchiseBox=Ui.column(this);franchiseBox.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,28));content.addView(franchiseBox);
  voiceButton.setOnClickListener(v->Ui.choose(this,"Озвучка",Sources.voices(videos),voice,choice->{voice=choice;List<String>providers=Sources.players(videos,voice);if(!providers.contains(player))player=providers.isEmpty()?"":providers.get(0);update();}));
  playerButton.setOnClickListener(v->Ui.choose(this,"Источник видео",Sources.players(videos,voice),player,choice->{player=choice;update();}));seriesButton.setOnClickListener(v->{List<Episode>episodes=Sources.episodes(videos,voice,player);List<String>labels=new ArrayList<>();for(Episode episode:episodes)labels.add(episode.label());Ui.choose(this,"Серия",labels,"Серия "+number,label->{number=episodes.get(labels.indexOf(label)).number;update();});});
  updateMetadata();load();
 }
 void updateMetadata(){animeTitle.setText(anime.title);String age=anime.age();metadata.setText(anime.meta()+(age.isEmpty()?"":" · "+age));score.setText(Ui.score(anime));String list=anime.genres();genres.setText(list);genres.setVisibility(list.isEmpty()?View.GONE:View.VISIBLE);Ui.poster(this,poster,anime.poster);Ui.poster(this,backdrop,anime.poster);updateDescription();updateFavorite();}
 void updateDescription(){description.setText(anime.description.isEmpty()?"Описание пока не добавлено.":anime.description);description.setMaxLines(expanded?Integer.MAX_VALUE:4);expand.setText(expanded?"Свернуть":"Читать дальше");description.post(()->{android.text.Layout layout=description.getLayout();boolean clipped=layout!=null&&(layout.getLineCount()>4||(layout.getLineCount()>0&&layout.getEllipsisCount(Math.min(3,layout.getLineCount()-1))>0));expand.setVisibility((expanded&&!anime.description.isEmpty())||clipped?View.VISIBLE:View.GONE);});}
 void updateFavorite(){boolean saved=library.favorite(anime.id);Ui.icon(favorite,saved?"heart-fill":"heart",saved?Ui.ACCENT:Ui.TEXT);favorite.setContentDescription(saved?"Убрать из избранного":"Добавить в избранное");favorite.setSelected(saved);}
 @Override protected void onSaveInstanceState(Bundle saved){saved.putString("voice",voice);saved.putString("player",player);saved.putString("number",number);saved.putBoolean("expanded",expanded);saved.putLong("last_read_video",lastReadVideoId);super.onSaveInstanceState(saved);}
 @Override protected void onResume(){super.onResume();if(social!=null&&!firstResume)social.refresh();firstResume=false;if(library==null||anime==null||videos.isEmpty())return;updateFavorite();JSONObject last=library.last(anime.id);if(last!=null&&last.optLong("video_id")!=lastReadVideoId){lastReadVideoId=last.optLong("video_id");String newVoice=last.optString("voice"),newPlayer=last.optString("player"),newNumber=last.optString("number");if(Sources.match(videos,newVoice,newPlayer,newNumber)!=null){voice=newVoice;player=newPlayer;number=newNumber;update();}}}
 void load(){
  int request=++generation;status.setText("Загружаем серии и озвучки…");retry.setVisibility(View.GONE);voiceButton.setEnabled(false);playerButton.setEnabled(false);seriesButton.setEnabled(false);watch.setEnabled(false);
  Api.IO.execute(()->{try{
   JSONObject raw=Api.get("anime/"+anime.id+"?need_videos=true").getJSONObject("response");Api.save(this,raw);List<Episode>result=Api.videos(raw);Anime fresh=new Anime(raw);
   JSONArray st=raw.optJSONArray("studios"),fr=raw.optJSONArray("viewing_order");Progress.pullAnime(this,fresh.id);
   runOnUiThread(()->{if(isDestroyed()||request!=generation)return;anime=fresh;studios=st==null?new JSONArray():st;franchise=fr==null?new JSONArray():fr;renderStudios();renderFranchise();if(social!=null)social.anime=fresh;Api.IO.execute(()->new Account(this).noteAnime(fresh));videos=result;updateMetadata();if(videos.isEmpty()){status.setText("Пока нет доступных серий. Можно открыть аниме на сайте.");return;}JSONObject last=library.last(anime.id);if(last!=null)lastReadVideoId=last.optLong("video_id");
    // Любимая озвучка этого тайтла (синхронизируется между устройствами), затем — из истории; досмотренная серия → следующая.
    if(voice.isEmpty()){Episode pref=Progress.preferred(videos,Progress.get(this,anime.id),last);if(pref!=null){voice=pref.voice;player=pref.player;number=pref.number;}}List<String>voices=Sources.voices(videos);if(!voices.contains(voice))voice=voices.get(0);List<String>providers=Sources.players(videos,voice);if(!providers.contains(player))player=providers.get(0);List<Episode>episodes=Sources.episodes(videos,voice,player);if(last==null&&Sources.match(videos,voice,player,number)==null&&!episodes.isEmpty())number=episodes.get(0).number;voiceButton.setEnabled(true);playerButton.setEnabled(true);seriesButton.setEnabled(true);update();});
  }catch(Exception exception){runOnUiThread(()->{if(isDestroyed()||request!=generation)return;status.setText(exception instanceof java.io.IOException?exception.getMessage():"Не получилось загрузить серии. Попробуй ещё раз.");retry.setVisibility(View.VISIBLE);});}});
 }
 void update(){renderEpisodesLater();selected=Sources.match(videos,voice,player,number);Ui.fieldValue(voiceButton,"Озвучка",Ui.voice(voice));Ui.fieldValue(playerButton,"Источник",Ui.provider(player));Ui.fieldValue(seriesButton,"Серия",selected==null?"Выбрать":number);watch.setEnabled(selected!=null);boolean nativeVideo=selected!=null&&StreamResolver.supports(selected.url);watch.setText(selected==null||nativeVideo?(selected!=null&&library.position(selected.id)>0?"Продолжить · серия "+number:"Смотреть · серия "+number):"Открыть плеер сайта");Ui.icon(watch,nativeVideo||selected==null?"play":"external",selected==null?0xFF60616F:Ui.ON_ACCENT);status.setText(selected==null?"Этой серии здесь нет. Выбери другой номер.":nativeVideo?(library.position(selected.id)>0?"Продолжим с "+PlaybackProgress.time(library.position(selected.id)):"Всё готово к просмотру"):"У этого источника может быть реклама. Для своего плеера выбери Kodik.");}
 void renderEpisodesLater(){if(episodesBox!=null)episodesBox.post(this::renderEpisodes);}
 void renderEpisodes(){
  if(episodesBox==null)return;List<Episode> list=Sources.episodes(videos,voice,player);episodesBox.removeAllViews();
  if(list.isEmpty()){episodesCard.setVisibility(View.GONE);return;}
  episodesCard.setVisibility(View.VISIBLE);JSONObject p=Progress.get(this,anime.id);int seen=0;for(Episode e:list)if(Progress.isWatched(p,e.number))seen++;
  watchedText.setText("просмотрено "+seen+" из "+list.size());
  episodesBox.addView(Progress.tiles(this,list,p,number,e->{number=e.number;update();if(selected!=null)watch.performClick();},e->Progress.toggle(this,anime,e.number)));
 }
 void renderStudios(){
  studiosBox.removeAllViews();if(studios.length()==0)return;Ui.Flow flow=new Ui.Flow(this,8);
  for(int i=0;i<studios.length();i++){JSONObject s=studios.optJSONObject(i);if(s==null||s.optLong("id")<=0)continue;long id=s.optLong("id");String title=s.optString("title");Button chip=Ui.chip(this,"Студия: "+title);chip.setContentDescription("Все аниме студии "+title);
   chip.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("studio_id",id).putExtra("studio_title",title).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)));flow.addView(chip);}
  studiosBox.addView(flow);
 }
 /** Трекер франшизы: части в порядке просмотра, отметка «просмотрено», текущая серия, общий прогресс. */
 void renderFranchise(){
  if(franchiseBox==null)return;franchiseBox.removeAllViews();if(franchise.length()<2)return;
  int done=0;for(int i=0;i<franchise.length();i++){JSONObject f=franchise.optJSONObject(i);if(f!=null&&Progress.complete(Progress.get(this,f.optLong("anime_id"))))done++;}
  LinearLayout head=Ui.row(this);TextView ht=Ui.title(this,"Порядок просмотра",17);if(android.os.Build.VERSION.SDK_INT>=28)ht.setAccessibilityHeading(true);head.addView(ht);TextView count=Ui.text(this,"просмотрено "+done+" из "+franchise.length(),12,Ui.MUTED);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=Ui.dp(this,10);head.addView(count,cp);franchiseBox.addView(head);
  ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(franchise.length());bar.setProgress(done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.RAISED));bar.setContentDescription("Просмотрено частей: "+done+" из "+franchise.length());Ui.add(franchiseBox,bar,10);
  for(int i=0;i<franchise.length();i++){
   JSONObject f=franchise.optJSONObject(i);if(f==null)continue;long id=f.optLong("anime_id");if(id<=0)continue;JSONObject p=Progress.get(this,id);boolean here=id==anime.id,complete=Progress.complete(p);
   JSONObject data=f.optJSONObject("data"),type=f.optJSONObject("type"),posterObj=f.optJSONObject("poster");String title=f.optString("title"),posterUrl=Api.https(posterObj==null?"":posterObj.optString("fullsize"));
   String state=complete?"✓ просмотрено":p!=null&&!p.optString("episode").isEmpty()?"▶ серия "+p.optString("episode"):Progress.watchedCount(p)>0?"просмотрено серий: "+Progress.watchedCount(p):"";
   List<String> bits=new ArrayList<>();for(String b:new String[]{data==null?"":data.optString("text"),type==null?"":type.optString("shortname"),f.optInt("year")>0?String.valueOf(f.optInt("year")):"",here?"открыто сейчас":"",state})if(!b.isEmpty())bits.add(b);
   LinearLayout row=Ui.row(this);row.setGravity(Gravity.CENTER_VERTICAL);
   LinearLayout info=Ui.column(this);info.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));info.setBackground(Ui.ripple(this,here?Ui.SOFT:Ui.RAISED,14));
   TextView name=Ui.text(this,(i+1)+". "+title,14,here?Ui.ACCENT:Ui.TEXT);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name);TextView meta=Ui.text(this,TextUtils.join(" · ",bits),12,Ui.MUTED);meta.setMaxLines(1);meta.setEllipsize(TextUtils.TruncateAt.END);Ui.add(info,meta,3);
   if(!here)info.setOnClickListener(v->{try{startActivity(new Intent(this,DetailActivity.class).putExtra("anime",new JSONObject().put("anime_id",id).put("title",title).put("poster",new JSONObject().put("fullsize",posterUrl)).toString()));}catch(JSONException ignored){}});
   row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
   Button check=Ui.iconButton(this,complete?"check":"eye",complete?"Снять отметку: "+title:"Отметить просмотренной: "+title);Ui.icon(check,complete?"check":"eye",complete?0xFF3DD68C:Ui.MUTED);
   check.setOnClickListener(v->Progress.setWatched(this,id,title,posterUrl,0,Progress.ALL,!complete));
   LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48));chp.leftMargin=Ui.dp(this,6);row.addView(check,chp);
   Ui.add(franchiseBox,row,8);
  }
 }
 @Override protected void onStart(){super.onStart();Progress.listeners.add(progressListener);renderEpisodes();renderFranchise();}
 @Override protected void onStop(){Progress.listeners.remove(progressListener);super.onStop();}
}
