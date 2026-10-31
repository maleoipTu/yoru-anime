package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.Player;
import org.json.*;
import java.util.*;

/**
 * Совместный просмотр: комната по коду, общая пауза, перемотка и серия, чат и реакции.
 * Состояние комнаты опрашивается раз в 1,5 секунды; любое действие участника рассылается остальным.
 */
final class Party {
 static final String[] REACTIONS={"❤","😂","😮","😭","🔥","👏","💀","🎃"};
 final PlayerActivity player;final Account account;final Handler handler=new Handler(Looper.getMainLooper());
 String code="",lastApplied="";long since;boolean polling,busy;JSONObject pending;long receivedAt;
 LinearLayout panel,messages;TextView heading;final List<View> chat=new ArrayList<>();

 Party(PlayerActivity player){this.player=player;account=new Account(player);build();}
 boolean active(){return !code.isEmpty();}
 String me(){return account.cachedProfile().optString("username");}

 void build(){
  Activity a=player;panel=Ui.padded(a,14);panel.setBackground(Ui.outlined(a,Ui.PANEL,20));panel.setVisibility(View.GONE);
  LinearLayout head=Ui.row(a);heading=Ui.title(a,"",15);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Button invite=Ui.textButton(a,"Пригласить");invite.setOnClickListener(v->share());head.addView(invite);Button leave=Ui.textButton(a,"Выйти");leave.setOnClickListener(v->leave());head.addView(leave);panel.addView(head);
  messages=Ui.column(a);Ui.add(panel,messages,6);
  HorizontalScrollView rs=new HorizontalScrollView(a);rs.setHorizontalScrollBarEnabled(false);LinearLayout reacts=Ui.row(a);rs.addView(reacts);
  for(String e:REACTIONS){Button b=Ui.chip(a,e);b.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(Ui.dp(a,10),0,Ui.dp(a,10),0);b.setOnClickListener(v->say("reaction",e));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(a,38));lp.rightMargin=Ui.dp(a,6);reacts.addView(b,lp);}
  Ui.add(panel,rs,8);
  LinearLayout compose=Ui.row(a);EditText input=new EditText(a);input.setHint("Написать в комнату");input.setSingleLine(true);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.MUTED);input.setTextSize(14);input.setBackground(Ui.outlined(a,Ui.RAISED,14));input.setPadding(Ui.dp(a,12),Ui.dp(a,8),Ui.dp(a,12),Ui.dp(a,8));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
  Button send=Ui.iconButton(a,"send","Отправить");Ui.icon(send,"send",Ui.ACCENT);compose.addView(send);Ui.add(panel,compose,8);
  Runnable post=()->{String text=input.getText().toString().trim();if(text.isEmpty())return;input.setText("");say("chat",text);};
  send.setOnClickListener(v->post.run());input.setOnEditorActionListener((v,action,event)->{post.run();return true;});
 }

 /** Меню «Смотреть вместе»: создать комнату для этой серии или войти по коду. */
 void open(){
  if(!account.signedIn()){Toast.makeText(player,"Войди в аккаунт, чтобы смотреть вместе",Toast.LENGTH_SHORT).show();return;}
  if(active()){share();return;}
  LinearLayout sheet=Ui.column(player);sheet.addView(Ui.text(player,"Пауза, перемотка и выбор серии синхронизируются у всех. В комнате есть чат и реакции.",13,Ui.MUTED));
  Button create=Ui.button(player,"Создать комнату",true);Ui.add(sheet,create,14);
  EditText field=new EditText(player);field.setHint("Код комнаты, например K7QX2M");field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);field.setTextColor(Ui.TEXT);field.setHintTextColor(Ui.MUTED);field.setBackground(Ui.outlined(player,Ui.RAISED,14));field.setPadding(Ui.dp(player,14),Ui.dp(player,12),Ui.dp(player,14),Ui.dp(player,12));Ui.add(sheet,field,18);
  Button join=Ui.tonal(player,"Войти по коду");Ui.add(sheet,join,8);
  Dialog dialog=Ui.sheet(player,"Смотреть вместе",sheet,"Закрыть");
  create.setOnClickListener(v->{dialog.dismiss();Episode e=player.current;Anime an=player.anime;Extras.async(player,()->(JSONObject)account.rpc("room_create",new JSONObject().put("p_anime_id",an.id).put("p_title",an.title).put("p_poster",an.poster).put("p_episode",e.number).put("p_video_id",e.id)),state->{attach(state.optString("code"));lastApplied=state.optString("updated_at");push();share();});});
  join.setOnClickListener(v->{String c=field.getText().toString().trim().toUpperCase(Locale.ROOT);if(c.length()!=6)return;dialog.dismiss();joinCode(player,account,c);});
 }
 /** Вход по коду из любого экрана: загружает нужное аниме и открывает плеер с комнатой. */
 static void joinCode(Activity a,Account account,String code){
  Extras.async(a,()->{JSONObject state=(JSONObject)account.rpc("room_join",new JSONObject().put("p_code",code));JSONObject raw=Api.get("anime/"+state.optLong("anime_id")+"?need_videos=true").getJSONObject("response");Api.save(a,raw);
    long video=state.optLong("video_id");boolean found=false;for(Episode e:Api.videos(raw))if(e.id==video)found=true;if(!found)for(Episode e:Api.videos(raw))if(e.number.equals(state.optString("episode"))&&StreamResolver.supports(e.url)){video=e.id;found=true;break;}
    if(!found)throw new java.io.IOException("В комнате серия, которой нет в плеере Yoru.");return new long[]{state.optLong("anime_id"),video};},
   ids->{if(a instanceof PlayerActivity)a.finish();a.startActivity(new Intent(a,PlayerActivity.class).putExtra("anime_id",ids[0]).putExtra("video_id",ids[1]).putExtra("room",code));});
 }
 void attach(String room){code=room;since=0;panel.setVisibility(View.VISIBLE);heading.setText("Комната "+code);if(!polling){polling=true;handler.post(poll);}}
 void share(){if(!active())return;String text="Смотрим «"+player.anime.title+"» вместе в Yoru! Код комнаты: "+code;try{player.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Пригласить"));}catch(Exception e){Toast.makeText(player,"Код комнаты: "+code,Toast.LENGTH_LONG).show();}}
 void leave(){if(!active())return;String c=code;code="";polling=false;handler.removeCallbacksAndMessages(null);panel.setVisibility(View.GONE);messages.removeAllViews();chat.clear();Api.IO.execute(()->{try{account.rpc("room_leave",new JSONObject().put("p_code",c));}catch(Exception ignored){}});}
 void say(String kind,String body){if(!active())return;String c=code;Extras.async(player,()->account.rpc("room_say",new JSONObject().put("p_code",c).put("p_kind",kind).put("p_body",body)),x->{});}

 final Runnable poll=new Runnable(){public void run(){
  if(!active()||player.isDestroyed()){polling=false;return;}
  if(!busy){busy=true;String c=code;long from=since;
   Api.IO.execute(()->{try{JSONObject state=(JSONObject)account.rpc("room_poll",new JSONObject().put("p_code",c).put("p_since",from));player.runOnUiThread(()->{busy=false;if(c.equals(code))receive(state);});}
    catch(Exception e){player.runOnUiThread(()->{busy=false;if(e.getMessage()!=null&&e.getMessage().contains("not_in_room")){Toast.makeText(player,"Комната закрыта",Toast.LENGTH_SHORT).show();leave();}});}});}
  handler.postDelayed(this,1500);}};

 void receive(JSONObject state){
  receivedAt=SystemClock.elapsedRealtime();JSONArray members=state.optJSONArray("members");int n=members==null?0:members.length();
  heading.setText("Комната "+code+" · "+n+" "+Social.plural(n,"зритель","зрителя","зрителей"));
  JSONArray list=state.optJSONArray("messages");
  if(list!=null)for(int i=0;i<list.length();i++){JSONObject m=list.optJSONObject(i);since=Math.max(since,m.optLong("id"));show(m);}
  if(!state.optString("updated_at").equals(lastApplied)&&!me().equals(state.optString("updated_by"))){lastApplied=state.optString("updated_at");apply(state);}
 }
 void show(JSONObject m){
  String who=m.optString("display_name").isEmpty()?m.optString("username"):m.optString("display_name");String kind=m.optString("kind");
  if(kind.equals("reaction")){bubble(m.optString("body"));if(!m.optString("username").equals(me()))return;}
  String text=kind.equals("system")?(m.optString("body").equals("joined")?who+" зашёл в комнату":m.optString("body").equals("left")?who+" вышел":who+" создал комнату"):kind.equals("reaction")?who+" "+m.optString("body"):who+": "+m.optString("body");
  TextView line=Ui.text(player,text,13,kind.equals("chat")?Ui.TEXT:Ui.MUTED);line.setMaxLines(4);line.setEllipsize(TextUtils.TruncateAt.END);messages.addView(line);chat.add(line);
  while(chat.size()>8){messages.removeView(chat.remove(0));}
 }
 /** Реакция всплывает над видео. */
 void bubble(String emoji){
  FrameLayout surface=player.surface;if(surface==null)return;TextView t=Ui.text(player,emoji,30,Color.WHITE);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);lp.rightMargin=Ui.dp(player,24+new Random().nextInt(60));lp.bottomMargin=Ui.dp(player,56);surface.addView(t,lp);
  t.animate().translationY(-Ui.dp(player,140)).alpha(0).setDuration(2200).withEndAction(()->surface.removeView(t));
 }

 /** Чужое действие: переключить серию, перемотать и поставить на паузу или запустить. */
 void apply(JSONObject state){
  Episode target=null;long video=state.optLong("video_id");
  for(Episode e:player.videos)if(e.id==video)target=e;
  if(target==null)target=Sources.match(player.videos,player.current.voice,player.current.player,state.optString("episode"));
  if(target!=null&&target.id!=player.current.id){pending=state;player.applyingRemote=true;player.navigate(target);release();return;}
  pending=state;ready();
 }
 /** Вызывается, когда видео готово к воспроизведению: догоняем позицию комнаты. */
 void ready(){
  if(pending==null||player.engine==null||!player.wasReady)return;JSONObject state=pending;pending=null;
  boolean paused=state.optBoolean("paused");double seconds=state.optDouble("position")+(paused?0:state.optDouble("age")+(SystemClock.elapsedRealtime()-receivedAt)/1000.0);
  long target=Math.round(seconds*1000);player.applyingRemote=true;
  if(Math.abs(player.engine.getCurrentPosition()-target)>2500)player.engine.seekTo(target);
  player.desiredPlay=!paused;player.engine.setPlayWhenReady(!paused);
  String who=state.optString("updated_by");if(!who.isEmpty())player.status.setText(who+(paused?" поставил паузу":" включил")+" · "+PlaybackProgress.time(target));
  release();
 }
 void release(){handler.postDelayed(()->player.applyingRemote=false,900);}
 /** Моё действие уходит в комнату. */
 void push(){
  if(!active()||player.applyingRemote||player.engine==null&&player.current==null)return;
  String c=code;Episode e=player.current;long pos=player.engine==null?0:player.engine.getCurrentPosition();boolean paused=player.engine==null||!player.engine.getPlayWhenReady()||player.engine.getPlaybackState()==Player.STATE_ENDED;
  Api.IO.execute(()->{try{JSONObject state=(JSONObject)account.rpc("room_control",new JSONObject().put("p_code",c).put("p_position",pos/1000.0).put("p_paused",paused).put("p_episode",e.number).put("p_video_id",e.id));player.runOnUiThread(()->lastApplied=state.optString("updated_at"));}catch(Exception ignored){}});
 }
}
