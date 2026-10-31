package app.yoru.anime;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/**
 * Любимая озвучка тайтла, просмотренные серии и позиция просмотра. Хранится локально и, если выполнен вход,
 * синхронизируется с аккаунтом (watch_progress): начал на телефоне — продолжил на ПК.
 * watched — номера просмотренных серий; «*» — весь тайтл отмечен просмотренным (для трекера франшизы).
 */
final class Progress {
 static final String ALL="*";
 static final Map<Long,Long> lastSent=new HashMap<>();
 interface Listener{void changed(long animeId);}
 static final List<Listener> listeners=new ArrayList<>();

 static SharedPreferences prefs(Context c){return c.getSharedPreferences("yoru_progress",Context.MODE_PRIVATE);}
 static JSONObject get(Context c,long animeId){try{String s=prefs(c).getString("a"+animeId,null);return s==null?null:new JSONObject(s);}catch(JSONException e){return null;}}
 static void put(Context c,long animeId,JSONObject p){prefs(c).edit().putString("a"+animeId,p.toString()).apply();}
 static JSONObject ensure(Context c,long animeId,String title,String poster){JSONObject p=get(c,animeId);if(p==null)p=new JSONObject();try{if(title!=null&&!title.isEmpty())p.put("title",title);if(poster!=null&&!poster.isEmpty())p.put("poster",poster);if(!p.has("watched"))p.put("watched",new JSONArray());}catch(JSONException ignored){}return p;}
 static Set<String> watched(JSONObject p){Set<String> r=new HashSet<>();JSONArray a=p==null?null:p.optJSONArray("watched");if(a!=null)for(int i=0;i<a.length();i++)r.add(a.optString(i));return r;}
 static boolean complete(JSONObject p){return watched(p).contains(ALL);}
 static boolean isWatched(JSONObject p,String number){Set<String> w=watched(p);return w.contains(ALL)||w.contains(number);}
 static int watchedCount(JSONObject p){Set<String> w=watched(p);w.remove(ALL);return w.size();}
 static void notifyChanged(long id){for(Listener l:new ArrayList<>(listeners))l.changed(id);}

 /** Серия, с которой продолжить: сохранённая озвучка и плеер; если серия досмотрена — следующая. */
 static Episode preferred(List<Episode> videos,JSONObject p,JSONObject history){
  if(videos.isEmpty())return null;
  List<String> voices=Sources.voices(videos);
  String voice=p!=null&&voices.contains(p.optString("voice"))?p.optString("voice"):history!=null&&voices.contains(history.optString("voice"))?history.optString("voice"):null;
  if(voice==null)return null;
  List<String> players=Sources.players(videos,voice);if(players.isEmpty())return null;
  String player=p!=null&&players.contains(p.optString("player"))?p.optString("player"):history!=null&&players.contains(history.optString("player"))?history.optString("player"):players.get(0);
  List<Episode> list=Sources.episodes(videos,voice,player);if(list.isEmpty())return null;
  String number=p!=null&&!p.optString("episode").isEmpty()?p.optString("episode"):history!=null?history.optString("number"):null;
  int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).number.equals(number))index=i;
  double position=p==null?0:p.optDouble("position",0),duration=p==null?0:p.optDouble("duration",0);
  if(index>=0&&p!=null&&isWatched(p,list.get(index).number)&&index+1<list.size()&&(duration<=0||position<=0||position>=duration*.9))index++;
  if(index<0&&p!=null)for(int i=0;i<list.size();i++)if(!isWatched(p,list.get(i).number)){index=i;break;}
  return index>=0?list.get(index):list.get(0);
 }
 /** Позиция старта (мс): синхронизированная, если это та же серия, иначе локальная по видео. */
 static long startPosition(Context c,Library library,long animeId,Episode episode){
  JSONObject p=get(c,animeId);
  if(p!=null&&episode.number.equals(p.optString("episode"))){double pos=p.optDouble("position",0),dur=p.optDouble("duration",0);if(pos>5&&(dur<=0||pos<dur-10))return (long)(pos*1000);}
  return library.position(episode.id);
 }
 /** Запоминает серию, озвучку и позицию (секунды). На сервер — не чаще раза в 20 секунд (или сразу при force). */
 static void track(Context c,Anime anime,Episode episode,double position,double duration,boolean force){
  JSONObject p=ensure(c,anime.id,anime.title,anime.poster);
  try{if(!episode.number.equals(p.optString("episode")))p.put("duration",0);p.put("episode",episode.number).put("voice",episode.voice).put("player",episode.player).put("position",Math.max(0,position));if(duration>0)p.put("duration",duration);p.put("updated",System.currentTimeMillis());}catch(JSONException ignored){}
  put(c,anime.id,p);
  Account account=new Account(c);if(!account.signedIn())return;
  long now=System.currentTimeMillis();Long at=lastSent.get(anime.id);if(!force&&at!=null&&now-at<20000)return;lastSent.put(anime.id,now);
  double pos=p.optDouble("position",0),dur=p.optDouble("duration",0);
  Api.IO.execute(()->{try{account.rpc("save_progress",new JSONObject().put("p_anime_id",anime.id).put("p_episode",episode.number).put("p_dubbing",episode.voice).put("p_player",episode.player).put("p_position",Math.round(pos*10)/10.0).put("p_duration",Math.round(dur*10)/10.0).put("p_title",anime.title).put("p_poster",anime.poster));}catch(Exception ignored){}});
 }
 /** Отмечает серию (или весь тайтл, number = «*») просмотренной либо снимает отметку. */
 static void setWatched(Context c,long animeId,String title,String poster,int total,String number,boolean on){
  JSONObject p=ensure(c,animeId,title,poster);Set<String> w=watched(p);List<Object[]> changes=new ArrayList<>();
  if(on?w.add(number):w.remove(number))changes.add(new Object[]{number,on});
  if(!on&&!number.equals(ALL)&&w.remove(ALL))changes.add(new Object[]{ALL,false});
  if(on&&!number.equals(ALL)&&total>0){Set<String> plain=new HashSet<>(w);plain.remove(ALL);if(plain.size()>=total&&w.add(ALL))changes.add(new Object[]{ALL,true});}
  if(changes.isEmpty())return;
  try{p.put("watched",new JSONArray(w)).put("updated",System.currentTimeMillis());}catch(JSONException ignored){}
  put(c,animeId,p);notifyChanged(animeId);
  Account account=new Account(c);if(!account.signedIn())return;
  Api.IO.execute(()->{for(Object[] change:changes)try{account.rpc("set_watched",new JSONObject().put("p_anime_id",animeId).put("p_episode",change[0]).put("p_watched",change[1]));}catch(Exception ignored){}});
 }
 static void setWatched(Context c,Anime anime,String number,boolean on){setWatched(c,anime.id,anime.title,anime.poster,anime.totalEpisodes(),number,on);}
 static void toggle(Context c,Anime anime,String number){setWatched(c,anime,number,!isWatched(get(c,anime.id),number));}

 static long parseTime(String iso){try{return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
 /** Применяет прогресс с сервера, если он новее локального. Вызывать в фоновом потоке. */
 static boolean merge(Context c,Library library,Object item){
  if(!(item instanceof JSONObject))return false;JSONObject o=(JSONObject)item;long id=o.optLong("anime_id");long updated=parseTime(o.optString("updated_at"));if(id<=0||updated==0)return false;
  JSONObject p=get(c,id);if(p!=null&&p.optLong("updated")>=updated)return false;if(p==null)p=new JSONObject();
  try{p.put("episode",o.optString("episode")).put("voice",o.optString("dubbing")).put("player",o.optString("player")).put("position",o.optDouble("position",0)).put("duration",o.optDouble("duration",0)).put("watched",o.optJSONArray("watched")==null?new JSONArray():o.optJSONArray("watched")).put("updated",updated);
   if(!o.optString("title").isEmpty())p.put("title",o.optString("title"));if(!o.optString("poster").isEmpty())p.put("poster",o.optString("poster"));}catch(JSONException e){return false;}
  put(c,id,p);
  // «Продолжить просмотр» показывает и то, что начато на другом устройстве.
  if(!p.optString("episode").isEmpty()&&!p.optString("title").isEmpty()&&updated>library.lastOpened())library.remote(id,p.optString("title"),p.optString("poster"),p.optString("episode"),p.optString("voice"),p.optString("player"),updated);
  return true;
 }
 /** Забирает изменения прогресса с сервера. Вызывать в фоновом потоке. */
 static boolean pull(Context c){
  Account account=new Account(c);if(!account.signedIn())return false;
  try{SharedPreferences prefs=prefs(c);JSONObject args=new JSONObject().put("p_limit",500);String since=prefs.getString("since","");if(!since.isEmpty())args.put("p_since",since);
   Object result=account.rpc("my_progress",args);if(!(result instanceof JSONArray))return false;JSONArray list=(JSONArray)result;Library library=new Library(c);boolean changed=false;
   for(int i=list.length()-1;i>=0;i--)changed|=merge(c,library,list.opt(i));
   if(list.length()>0)prefs.edit().putString("since",list.getJSONObject(0).optString("updated_at")).apply();
   return changed;}catch(Exception e){return false;}
 }
 static boolean pullAnime(Context c,long animeId){Account account=new Account(c);if(!account.signedIn())return false;try{return merge(c,new Library(c),account.rpc("anime_progress",new JSONObject().put("p_anime_id",animeId)));}catch(Exception e){return false;}}

 // ───── Плитки серий ─────
 /** Плитки серий: нажатие открывает серию, кружок в углу (или долгое нажатие) отмечает просмотренной. */
 static View tiles(Activity a,List<Episode> episodes,JSONObject p,String current,java.util.function.Consumer<Episode> open,java.util.function.Consumer<Episode> toggle){
  Ui.Flow flow=new Ui.Flow(a,8);
  for(Episode episode:episodes){
   boolean seen=isWatched(p,episode.number),here=episode.number.equals(current);
   FrameLayout tile=new FrameLayout(a);
   TextView label=Ui.text(a,episode.number,15,seen?Ui.MUTED:Ui.TEXT);label.setTypeface(Ui.MEDIUM);label.setGravity(Gravity.CENTER);label.setMaxLines(1);
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setCornerRadius(Ui.dp(a,12));bg.setColor(seen?0xFF182A22:Ui.RAISED);if(here)bg.setStroke(Ui.dp(a,2),Ui.ACCENT);
   label.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24FFFFFF),bg,null));
   label.setContentDescription(episode.label()+(seen?", просмотрено":"")+(here?", сейчас":""));
   label.setOnClickListener(v->open.accept(episode));label.setOnLongClickListener(v->{toggle.accept(episode);return true;});
   tile.addView(label,new FrameLayout.LayoutParams(Ui.dp(a,62),Ui.dp(a,46)));
   TextView mark=Ui.text(a,seen?"✓":"",11,0xFF0E0E11);mark.setTypeface(Typeface.DEFAULT_BOLD);mark.setGravity(Gravity.CENTER);
   android.graphics.drawable.GradientDrawable dot=new android.graphics.drawable.GradientDrawable();dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);dot.setColor(seen?0xFF3DD68C:0x40FFFFFF);dot.setStroke(Ui.dp(a,1),0x60FFFFFF);mark.setBackground(dot);
   mark.setContentDescription(seen?"Снять отметку с серии "+episode.number:"Отметить серию "+episode.number+" просмотренной");mark.setOnClickListener(v->toggle.accept(episode));
   // Зона нажатия кружка больше самого кружка.
   FrameLayout hit=new FrameLayout(a);hit.setPadding(Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4));hit.addView(mark,new FrameLayout.LayoutParams(Ui.dp(a,20),Ui.dp(a,20)));hit.setOnClickListener(v->toggle.accept(episode));hit.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   tile.addView(hit,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END));
   flow.addView(tile);
  }
  return flow;
 }

}
