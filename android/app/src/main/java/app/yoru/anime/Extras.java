package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.*;

/** Рейтинг недели, подарки, статистика, «Для тебя», рулетка, перенос со старого YummyAnime и комментарии к серии. */
final class Extras {
 private Extras(){}

 static <T> void async(Activity activity,Callable<T> job,Consumer<T> done){
  Api.IO.execute(()->{try{T value=job.call();activity.runOnUiThread(()->{if(!activity.isDestroyed())done.accept(value);});}
   catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed())Toast.makeText(activity,problem(e),Toast.LENGTH_LONG).show();});}});
 }
 static String problem(Exception e){String m=e.getMessage();return e instanceof IOException&&!(e instanceof Account.Failure)&&m!=null&&m.matches(".*[А-Яа-я].*")?m:Rewards.error(m);}
 static TextView hint(Activity a,String text){return Ui.text(a,text,13,Ui.MUTED);}
 static Map<String,JSONObject> decor(Account account){return Decor.index(account.cachedDecorations());}
 static boolean needAccount(Activity a,Account account){if(account.signedIn())return false;Toast.makeText(a,"Войди в аккаунт Yoru",Toast.LENGTH_SHORT).show();return true;}

 // ───────── Рейтинг недели ─────────
 static void leaderboard(Activity a,Account account){
  if(needAccount(a,account))return;
  LinearLayout panel=Ui.column(a);LinearLayout tabs=Ui.row(a);Button friends=Ui.chip(a,"Друзья"),global=Ui.chip(a,"Все");tabs.addView(friends);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-2,-2);gp.leftMargin=Ui.dp(a,8);tabs.addView(global,gp);panel.addView(tabs);
  Ui.add(panel,hint(a,"Очки и серии за последние 7 дней. Обновляется сразу после засчитанной серии."),8);
  LinearLayout list=Ui.column(a);Ui.add(panel,list,10);
  Consumer<String> load=scope->{Ui.chipState(friends,scope.equals("friends"));Ui.chipState(global,scope.equals("global"));friends.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);global.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);
   list.removeAllViews();list.addView(hint(a,"Загружаем…"));
   async(a,()->{JSONArray rows=(JSONArray)account.rpc("leaderboard",new JSONObject().put("p_scope",scope).put("p_days",7));if(account.cachedDecorations().length()==0)account.decorations();return rows;},rows->{
    list.removeAllViews();if(rows.length()==0){list.addView(hint(a,scope.equals("friends")?"На этой неделе друзья ещё ничего не смотрели. Позови их на совместный просмотр!":"Пока пусто."));return;}
    Map<String,JSONObject> decor=decor(account);
    for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);int place=r.optInt("place");
     LinearLayout row=Ui.row(a);row.setPadding(Ui.dp(a,8),0,Ui.dp(a,8),0);row.setBackground(Ui.shape(a,r.optBoolean("me")?Ui.SOFT:Color.TRANSPARENT,14));
     TextView medal=Ui.title(a,place==1?"🥇":place==2?"🥈":place==3?"🥉":"#"+place,place<=3?22:15);medal.setGravity(Gravity.CENTER);row.addView(medal,new LinearLayout.LayoutParams(Ui.dp(a,40),-2));
     LinearLayout person=Social.person(a,r,decor,"◆ "+Rewards.points(r.optInt("points"))+" · "+r.optInt("episodes")+" "+Social.plural(r.optInt("episodes"),"серия","серии","серий")+" · "+r.optString("hours")+" ч",40);
     row.addView(person,new LinearLayout.LayoutParams(0,-2,1));row.setClickable(true);row.setOnClickListener(v->Social.openProfile(a,r.optString("username")));Ui.add(list,row,4);}
   });};
  friends.setOnClickListener(v->load.accept("friends"));global.setOnClickListener(v->load.accept("global"));
  Ui.sheet(a,"Рейтинг недели",panel,"Закрыть рейтинг");load.accept("friends");
 }

 // ───────── Подарки ─────────
 static void inbox(Activity a,Account account,Runnable changed){
  if(needAccount(a,account))return;
  LinearLayout panel=Ui.column(a);panel.addView(hint(a,"Загружаем подарки…"));Dialog dialog=Ui.sheet(a,"Подарки",panel,"Закрыть подарки");
  async(a,()->{JSONArray g=(JSONArray)account.rpc("my_gifts",new JSONObject());account.rpc("mark_gifts_seen",new JSONObject());if(account.cachedDecorations().length()==0)account.decorations();return g;},gifts->{
   panel.removeAllViews();if(gifts.length()==0){panel.addView(hint(a,"Пока подарков нет. Друзья могут подарить очки или украшение со страницы твоего профиля."));return;}
   for(int i=0;i<gifts.length();i++){JSONObject g=gifts.optJSONObject(i);boolean fresh=!g.optBoolean("seen");
    String what=g.optString("kind").equals("points")?"◆ "+Rewards.points(g.optInt("amount"))+" очков":"🎁 «"+g.optString("decoration_title")+"»";
    LinearLayout card=Ui.padded(a,14);card.setBackground(Ui.outlined(a,fresh?Ui.SOFT:Ui.RAISED,18));
    card.addView(Social.person(a,g,decor(account),(fresh?"Новый · ":"")+Rewards.until(g.optString("created_at")),36));
    TextView t=Ui.title(a,what,17);t.setTextColor(g.optString("kind").equals("points")?Ui.WARM:Ui.ACCENT);Ui.add(card,t,6);
    if(!g.optString("message").isEmpty())Ui.add(card,Ui.text(a,"«"+g.optString("message")+"»",14,Ui.TEXT),6);
    Ui.add(panel,card,i==0?0:10);}
   if(changed!=null)changed.run();
  });
 }
 /** Подарок другу: очки или украшение из магазина за свои очки. */
 static void giftTo(Activity a,Account account,JSONObject friend,Runnable done){
  if(needAccount(a,account))return;
  LinearLayout panel=Ui.column(a);int balance=account.cachedProfile().optInt("points");
  panel.addView(hint(a,"Твой баланс: ◆ "+Rewards.points(balance)+". Очки друга не повышают его уровень, но на них можно купить украшения."));
  EditText note=new EditText(a);note.setHint("Подпись к подарку (необязательно)");note.setTextColor(Ui.TEXT);note.setHintTextColor(Ui.MUTED);note.setTextSize(15);note.setBackground(Ui.outlined(a,Ui.RAISED,14));note.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,12));note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(140)});Ui.add(panel,note,12);
  Ui.add(panel,Ui.title(a,"Очки",15),16);Ui.Flow amounts=new Ui.Flow(a,8);Ui.add(panel,amounts,8);
  Dialog[] dialog={null};
  for(int value:new int[]{50,100,250,500}){Button b=Ui.chip(a,"◆ "+value);b.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);b.setEnabled(balance>=value);amounts.addView(b);
   b.setOnClickListener(v->async(a,()->(JSONObject)account.rpc("gift_points",new JSONObject().put("p_user",friend.optString("id")).put("p_amount",value).put("p_message",note.getText().toString())),r->{if(dialog[0]!=null)dialog[0].dismiss();Toast.makeText(a,"🎁 "+Decor.name(friend)+" получит ◆ "+value,Toast.LENGTH_LONG).show();if(done!=null)done.run();}));}
  Ui.add(panel,Ui.title(a,"Украшение",15),18);LinearLayout shop=Ui.column(a);shop.addView(hint(a,"Загружаем магазин…"));Ui.add(panel,shop,8);
  dialog[0]=Ui.sheet(a,"Подарок для "+Decor.name(friend),panel,"Закрыть подарок");
  async(a,()->account.decorations(),all->{shop.removeAllViews();long now=System.currentTimeMillis();int level=Rewards.level(account.cachedProfile().optInt("total_earned"));int shown=0;
   for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(d==null||d.optBoolean("exclusive")||d.optInt("price")==0||!Rewards.onSale(d.optString("available_from"),d.optString("available_until"),now))continue;
    Button item=Ui.field(a,d.optString("title"));Ui.fieldValue(item,kindLabel(d.optString("kind"))+" · "+Rewards.rarity(d.optString("rarity")),d.optString("title")+" · ◆ "+Rewards.points(d.optInt("price")));
    boolean can=balance>=d.optInt("price")&&level>=d.optInt("min_level",1);item.setEnabled(can);
    item.setOnClickListener(v->async(a,()->(JSONObject)account.rpc("gift_decoration",new JSONObject().put("p_user",friend.optString("id")).put("p_decoration",d.optString("id")).put("p_message",note.getText().toString())),r->{if(dialog[0]!=null)dialog[0].dismiss();Toast.makeText(a,"🎁 «"+d.optString("title")+"» отправлено",Toast.LENGTH_LONG).show();if(done!=null)done.run();}));
    Ui.add(shop,item,shown++==0?0:8);}
   if(shown==0)shop.addView(hint(a,"Сейчас нечего подарить."));});
 }
 static String kindLabel(String kind){switch(kind){case "frame":return "Обводка";case "badge":return "Значок";case "name_color":return "Цвет ника";case "avatar_deco":return "Украшение аватара";case "profile_effect":return "Эффект профиля";case "profile_frame":return "Рамка профиля";default:return "Украшение";}}

 // ───────── Статистика профиля ─────────
 static LinearLayout stats(Activity a,Account account,String username){
  LinearLayout card=Ui.padded(a,16);card.setBackground(Ui.outlined(a,Ui.PANEL,22));TextView head=Ui.title(a,"Статистика",17);card.addView(head);TextView wait=hint(a,"Считаем…");Ui.add(card,wait,8);
  async(a,()->account.anonRpc("watch_stats",new JSONObject().put("p_username",username)),value->{
   card.removeView(wait);if(!(value instanceof JSONObject))return;JSONObject s=(JSONObject)value;
   LinearLayout numbers=Ui.row(a);String[][] cells={{s.optString("hours","0"),"часов"},{String.valueOf(s.optInt("episodes")),"серий"},{String.valueOf(s.optInt("anime")),"аниме"},{s.isNull("avg_score")?"—":String.format(Locale.ROOT,"%.1f",s.optDouble("avg_score")),"средняя оценка"}};
   for(int i=0;i<cells.length;i++){LinearLayout cell=Ui.column(a);cell.setGravity(Gravity.CENTER_HORIZONTAL);TextView n=Ui.title(a,cells[i][0],20);n.setTextColor(i==0?Ui.ACCENT:i==3?Ui.WARM:Ui.TEXT);cell.addView(n);TextView l=Ui.text(a,cells[i][1],11,Ui.MUTED);l.setGravity(Gravity.CENTER);cell.addView(l);numbers.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}
   Ui.add(card,numbers,12);
   JSONArray genres=s.optJSONArray("genres");
   if(genres!=null&&genres.length()>0){Ui.add(card,Ui.title(a,"Любимые жанры",14),16);int max=1;for(int i=0;i<genres.length();i++)max=Math.max(max,genres.optJSONObject(i).optInt("count"));
    int[] palette={0xFFFF4D67,0xFFFF8A3D,0xFFFFC14D,0xFF4ADE80,0xFF6FC3FF,0xFFB388FF,0xFFFF8FB8,0xFF7EDCA9,0xFF9FD8FF,0xFFFFE08A};
    for(int i=0;i<Math.min(6,genres.length());i++){JSONObject g=genres.optJSONObject(i);LinearLayout row=Ui.row(a);TextView name=Ui.text(a,g.optString("genre"),12,Ui.TEXT);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(Ui.dp(a,110),-2));
     FrameLayout track=new FrameLayout(a);track.setBackground(Ui.shape(a,Ui.RAISED,6));View bar=new View(a);bar.setBackground(Ui.shape(a,palette[i%palette.length],6));float share=g.optInt("count")/(float)max;track.addView(bar,new FrameLayout.LayoutParams(0,-1));track.post(()->{bar.getLayoutParams().width=Math.max(Ui.dp(a,6),Math.round(track.getWidth()*share));bar.requestLayout();});
     row.addView(track,new LinearLayout.LayoutParams(0,Ui.dp(a,12),1));TextView count=Ui.text(a,String.valueOf(g.optInt("count")),12,Ui.MUTED);count.setGravity(Gravity.END);row.addView(count,new LinearLayout.LayoutParams(Ui.dp(a,32),-2));Ui.add(card,row,8);}}
   JSONArray week=s.optJSONArray("weekdays");
   if(week!=null&&week.length()==7){int total=0,max=1;for(int i=0;i<7;i++){total+=week.optInt(i);max=Math.max(max,week.optInt(i));}
    if(total>0){Ui.add(card,Ui.title(a,"Когда смотрит",14),16);LinearLayout bars=Ui.row(a);bars.setGravity(Gravity.BOTTOM);String[] days={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
     for(int i=0;i<7;i++){LinearLayout col=Ui.column(a);col.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);View b=new View(a);b.setBackground(Ui.gradient(a,Ui.ACCENT2,Ui.ACCENT,6,android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM));col.addView(b,new LinearLayout.LayoutParams(Ui.dp(a,18),Math.max(Ui.dp(a,4),Math.round(Ui.dp(a,56)*week.optInt(i)/(float)max))));TextView d=Ui.text(a,days[i],11,Ui.MUTED);Ui.add(col,d,4);bars.addView(col,new LinearLayout.LayoutParams(0,Ui.dp(a,80),1));}
     Ui.add(card,bars,8);}}
   JSONObject scores=s.optJSONObject("scores");
   if(scores!=null&&scores.length()>0){StringBuilder line=new StringBuilder("Оценки: ");for(int sc=10;sc>=1;sc--)if(scores.optInt(String.valueOf(sc))>0)line.append(sc).append("★×").append(scores.optInt(String.valueOf(sc))).append("  ");Ui.add(card,Ui.text(a,line.toString().trim(),12,Ui.MUTED),12);}
   if(s.optInt("episodes")==0&&(genres==null||genres.length()==0))Ui.add(card,hint(a,"Статистика появится после первых серий в плеере Yoru."),8);
  });
  return card;
 }

 // ───────── Перенос со старого YummyAnime ─────────
 static final String[] OLD_LISTS={"watching","planned","completed","on_hold","dropped"};
 /** id из ссылки вида old.yummyani.me/users/id529123 (или просто числа). */
 static long oldUserId(String text){Matcher m=Pattern.compile("id(\\d{1,10})").matcher(text);if(m.find())return Long.parseLong(m.group(1));m=Pattern.compile("^\\s*(\\d{1,10})\\s*$").matcher(text);return m.find()?Long.parseLong(m.group(1)):0;}
 static JSONArray oldList(long user,int list) throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL("https://old.yummyani.me/api/users/"+user+"/lists/"+list).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) Yoru");
  try{int code=c.getResponseCode();if(code==404)throw new IOException("Профиль на старом сайте не найден.");if(code!=200)throw new IOException("Старый сайт YummyAnime не ответил ("+code+"). Попробуй позже.");JSONObject root=new JSONObject(Api.read(c.getInputStream()));JSONArray r=root.optJSONArray("response");return r==null?new JSONArray():r;}finally{c.disconnect();}
 }
 static JSONObject oldItem(JSONObject o,String status,boolean favorite) throws JSONException{
  JSONObject poster=o.optJSONObject("poster");JSONArray genres=new JSONArray();JSONArray g=o.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length();i++){JSONObject x=g.optJSONObject(i);if(x!=null)genres.put(x.optString("title"));}
  JSONObject user=o.optJSONObject("user");double score=user==null?0:user.optDouble("rating",0);
  return new JSONObject().put("anime_id",o.optLong("anime_id")).put("status",status).put("title",o.optString("title")).put("poster",poster==null?"":poster.optString("big")).put("genres",genres).put("score",score).put("favorite",favorite);
 }
 static void importOld(Activity a,Account account,Runnable done){
  if(needAccount(a,account))return;
  LinearLayout panel=Ui.column(a);
  panel.addView(hint(a,"Перенесём списки «Смотрю», «В планах», «Просмотрено», «Отложено», «Брошено» и «Любимые», а также твои оценки. Уже добавленное в Yoru не перезапишется. Очки за перенос не начисляются."));
  EditText link=new EditText(a);link.setHint("Ссылка на профиль: old.yummyani.me/users/id…");link.setSingleLine(true);link.setInputType(InputType.TYPE_TEXT_VARIATION_URI);link.setTextColor(Ui.TEXT);link.setHintTextColor(Ui.MUTED);link.setTextSize(15);link.setBackground(Ui.outlined(a,Ui.RAISED,14));link.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,12));Ui.add(panel,link,14);
  Ui.add(panel,hint(a,"Где взять: открой свой профиль на YummyAnime и скопируй адрес из браузера. Список должен быть открыт для всех."),6);
  TextView progress=Ui.text(a,"",13,Ui.MUTED);Ui.add(panel,progress,10);
  Button go=Ui.button(a,"Перенести",true);Ui.add(panel,go,12);
  Dialog dialog=Ui.sheet(a,"Перенос с YummyAnime",panel,"Закрыть перенос");
  go.setOnClickListener(v->{long user=oldUserId(link.getText().toString());if(user<=0){progress.setText("Не вижу id в ссылке — она выглядит как …/users/id123456");progress.setTextColor(0xFFFF9A8B);return;}
   go.setEnabled(false);progress.setTextColor(Ui.MUTED);progress.setText("Скачиваем списки…");
   async(a,()->{Map<Long,JSONObject> items=new LinkedHashMap<>();for(int i=0;i<OLD_LISTS.length;i++){JSONArray rows=oldList(user,i);for(int k=0;k<rows.length();k++){JSONObject o=rows.optJSONObject(k);if(o!=null&&o.optLong("anime_id")>0)items.put(o.optLong("anime_id"),oldItem(o,OLD_LISTS[i],false));}}
     JSONArray favorites=oldList(user,5);for(int k=0;k<favorites.length();k++){JSONObject o=favorites.optJSONObject(k);if(o==null)continue;JSONObject e=items.get(o.optLong("anime_id"));if(e!=null)e.put("favorite",true);else items.put(o.optLong("anime_id"),oldItem(o,"completed",true));}
     JSONArray payload=new JSONArray();for(JSONObject e:items.values())payload.put(e);if(payload.length()==0)throw new IOException("Списки пусты или скрыты настройками приватности.");
     JSONObject total=new JSONObject().put("imported",0).put("rated",0).put("found",payload.length());
     for(int from=0;from<payload.length();from+=500){JSONArray part=new JSONArray();for(int k=from;k<Math.min(payload.length(),from+500);k++)part.put(payload.get(k));JSONObject r=(JSONObject)account.rpc("import_lists",new JSONObject().put("p_items",part));total.put("imported",total.optInt("imported")+r.optInt("imported")).put("rated",total.optInt("rated")+r.optInt("rated"));}
     return total;},
    r->{go.setEnabled(true);progress.setTextColor(Ui.LIVE);progress.setText("Готово: найдено "+r.optInt("found")+", добавлено "+r.optInt("imported")+", оценок "+r.optInt("rated")+".");if(done!=null)done.run();});
   progress.postDelayed(()->go.setEnabled(true),20000);
  });
 }

 // ───────── «Для тебя» ─────────
 static void forYou(Activity a,Account account){
  LinearLayout panel=Ui.column(a);panel.addView(hint(a,"Подбираем по твоим жанрам и тому, что смотрят друзья…"));Ui.sheet(a,"Для тебя",panel,"Закрыть рекомендации");
  async(a,()->{
   List<JSONObject> picks=new ArrayList<>();Set<Long> seen=new HashSet<>();
   if(account.signedIn()){for(String key:Account.LISTS)for(Anime x:account.list(key))seen.add(x.id);
    JSONArray friends=(JSONArray)account.rpc("friend_recommendations",new JSONObject().put("p_limit",10));for(int i=0;i<friends.length();i++){JSONObject f=friends.getJSONObject(i);seen.add(f.optLong("anime_id"));picks.add(new JSONObject().put("anime_id",f.optLong("anime_id")).put("title",f.optString("title")).put("poster",new JSONObject().put("fullsize",f.optString("poster"))).put("yoru_reason","👥 "+f.optInt("fans")+" "+Social.plural(f.optInt("fans"),"друг","друга","друзей")+(f.isNull("score")?"":" · ★ "+f.optString("score"))));}
    Object st=account.anonRpc("watch_stats",new JSONObject().put("p_username",account.cachedProfile().optString("username")));
    JSONArray genres=st instanceof JSONObject?((JSONObject)st).optJSONArray("genres"):null;
    if(genres!=null&&genres.length()>0){JSONArray all=Api.get("anime/genres").optJSONObject("response").optJSONArray("genres");List<String> ids=new ArrayList<>();List<String> names=new ArrayList<>();
     for(int g=0;g<Math.min(3,genres.length());g++){String name=genres.getJSONObject(g).optString("genre");for(int i=0;all!=null&&i<all.length();i++){JSONObject x=all.optJSONObject(i);if(x!=null&&x.optString("title").equalsIgnoreCase(name)){ids.add(String.valueOf(x.optInt("value",x.optInt("id"))));names.add(name);}}}
     for(int k=0;k<ids.size();k++){List<Anime> found=Api.catalog(Api.get("anime?limit=24&sort=rating&sort_forward=false&genres="+ids.get(k)));for(Anime x:found)if(seen.add(x.id)&&picks.size()<40){JSONObject raw=new JSONObject(x.raw.toString());raw.put("yoru_reason","✨ Любишь «"+names.get(k)+"»");picks.add(raw);}}}}
   if(picks.size()<8)for(Anime x:Api.catalog(Api.get("anime?limit=24&sort=rating&sort_forward=false&offset="+new Random().nextInt(4)*24)))if(seen.add(x.id)&&picks.size()<24){JSONObject raw=new JSONObject(x.raw.toString());raw.put("yoru_reason","★ Высокий рейтинг");picks.add(raw);}
   return picks;},
   picks->{panel.removeAllViews();if(picks.isEmpty()){panel.addView(hint(a,"Пока не из чего выбрать — посмотри пару серий или добавь друзей."));return;}
    for(int i=0;i<picks.size();i++)Ui.add(panel,animeRow(a,picks.get(i),picks.get(i).optString("yoru_reason")),i==0?0:10);});
 }
 static View animeRow(Activity a,JSONObject raw,String reason){
  Anime anime=new Anime(raw);LinearLayout row=Ui.row(a);row.setPadding(Ui.dp(a,6),Ui.dp(a,6),Ui.dp(a,6),Ui.dp(a,6));row.setBackground(Ui.ripple(a,Ui.RAISED,16));
  ImageView poster=new ImageView(a);Ui.round(poster,10);Ui.poster(a,poster,anime.poster);row.addView(poster,new LinearLayout.LayoutParams(Ui.dp(a,58),Ui.dp(a,82)));
  LinearLayout text=Ui.column(a);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=Ui.dp(a,12);row.addView(text,tp);
  TextView title=Ui.title(a,anime.title,15);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);text.addView(title);
  String meta=(anime.year>0?anime.year+" · ":"")+anime.type+(anime.rating>0?" · "+anime.score():"");if(!meta.trim().isEmpty())Ui.add(text,Ui.text(a,meta,12,Ui.MUTED),4);
  if(reason!=null&&!reason.isEmpty()){TextView r=Ui.text(a,reason,12,Ui.ACCENT);r.setTypeface(Ui.MEDIUM);Ui.add(text,r,6);}
  row.setClickable(true);row.setOnClickListener(v->a.startActivity(new Intent(a,DetailActivity.class).putExtra("anime",anime.raw.toString())));return row;
 }

 // ───────── Рулетка ─────────
 /** Случайное аниме с текущими фильтрами каталога, которого ещё нет в списках и истории. */
 static void roulette(Activity a,Account account,Library library,String filters,String status,int age){
  LinearLayout panel=Ui.column(a);panel.setGravity(Gravity.CENTER_HORIZONTAL);
  ImageView poster=new ImageView(a);Ui.round(poster,18);Ui.lift(poster,12);panel.addView(poster,new LinearLayout.LayoutParams(Ui.dp(a,180),Ui.dp(a,256)));
  TextView title=Ui.title(a,"Крутим барабан…",19);title.setGravity(Gravity.CENTER);title.setMaxLines(3);Ui.add(panel,title,14);
  TextView meta=Ui.text(a,"Учитываются фильтры каталога и то, что ты ещё не смотрел",13,Ui.MUTED);meta.setGravity(Gravity.CENTER);Ui.add(panel,meta,6);
  LinearLayout actions=Ui.row(a);Button again=Ui.tonal(a,"🎲 Ещё раз"),open=Ui.button(a,"Смотреть",true);actions.addView(again,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,-2,1);op.leftMargin=Ui.dp(a,8);actions.addView(open,op);Ui.add(panel,actions,18);
  again.setEnabled(false);open.setEnabled(false);
  Ui.sheet(a,"Рулетка",panel,"Закрыть рулетку");
  Anime[] chosen={null};
  Runnable spin=new Runnable(){public void run(){again.setEnabled(false);open.setEnabled(false);title.setText("Крутим барабан…");
   async(a,()->{Set<Long> seen=new HashSet<>();for(Anime x:library.history())seen.add(x.id);if(account.signedIn())for(String key:Account.LISTS)for(Anime x:account.list(key))seen.add(x.id);
     Random random=new Random();List<Anime> pool=new ArrayList<>();
     for(int attempt=0;attempt<6&&pool.size()<8;attempt++){List<Anime> page=Api.catalog(Api.get("anime?limit=24&sort=views&sort_forward=false&offset="+random.nextInt(attempt<3?20:6)*24+(status.isEmpty()?"":"&status="+status)+filters));if(page.isEmpty())continue;for(Anime x:page)if(!seen.contains(x.id)&&(age<0||x.ageValue()==age))pool.add(x);}
     if(pool.isEmpty())throw new IOException("С такими фильтрами всё уже просмотрено. Ослабь фильтры каталога.");Collections.shuffle(pool);return pool;},
    pool->{int steps=Math.min(pool.size(),12);for(int i=0;i<steps;i++){Anime x=pool.get(i);boolean last=i==steps-1;poster.postDelayed(()->{if(a.isDestroyed())return;Ui.poster(a,poster,x.poster);title.setText(x.title);if(last){chosen[0]=x;meta.setText(x.meta()+(x.genres().isEmpty()?"":"\n"+x.genres()));again.setEnabled(true);open.setEnabled(true);poster.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160).withEndAction(()->poster.animate().scaleX(1).scaleY(1).setDuration(160));}},(long)(60*i+i*i*9));}});}};
  again.setOnClickListener(v->spin.run());open.setOnClickListener(v->{if(chosen[0]!=null)a.startActivity(new Intent(a,DetailActivity.class).putExtra("anime",chosen[0].raw.toString()));});
  spin.run();
 }

 // ───────── Комментарии к серии ─────────
 static final String[] EMOJI={"❤","😂","😮","😭","🔥","👍"};
 interface Clock{long now();}
 interface Seek{void to(long ms);}
 static void comments(Activity a,Account account,Anime anime,String episode,Clock clock,Seek seek){
  LinearLayout panel=Ui.column(a);
  LinearLayout compose=Ui.row(a);EditText input=new EditText(a);input.setHint("Комментарий к серии "+episode);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.MUTED);input.setTextSize(15);input.setMaxLines(4);input.setBackground(Ui.outlined(a,Ui.RAISED,14));input.setPadding(Ui.dp(a,14),Ui.dp(a,10),Ui.dp(a,14),Ui.dp(a,10));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
  Button send=Ui.iconButton(a,"send","Отправить");Ui.icon(send,"send",Ui.ACCENT);compose.addView(send);panel.addView(compose);
  LinearLayout options=Ui.row(a);CheckBox stamp=new CheckBox(a);stamp.setTextColor(Ui.MUTED);stamp.setTextSize(13);stamp.setChecked(clock!=null);stamp.setEnabled(clock!=null);stamp.setText(clock==null?"Без таймкода":"С таймкодом "+PlaybackProgress.time(clock.now()));options.addView(stamp);CheckBox spoiler=new CheckBox(a);spoiler.setText("Спойлер");spoiler.setTextColor(Ui.MUTED);spoiler.setTextSize(13);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.leftMargin=Ui.dp(a,12);options.addView(spoiler,sp);Ui.add(panel,options,4);
  LinearLayout list=Ui.column(a);Ui.add(panel,list,10);
  Dialog dialog=Ui.sheet(a,"Обсуждение · серия "+episode,panel,"Закрыть комментарии");
  Runnable[] reload={null};
  reload[0]=()->{list.removeAllViews();list.addView(hint(a,"Загружаем…"));
   async(a,()->{JSONArray rows=(JSONArray)account.anonRpc("episode_comments",new JSONObject().put("p_anime_id",anime.id).put("p_episode",episode).put("p_limit",150));if(account.cachedDecorations().length()==0&&account.signedIn())account.decorations();return rows;},rows->{
    list.removeAllViews();if(rows.length()==0){list.addView(hint(a,"Здесь пока тихо. Будь первым — отметь любимый момент с таймкодом."));return;}
    Map<String,JSONObject> decor=decor(account);
    for(int i=0;i<rows.length();i++){JSONObject c=rows.optJSONObject(i);LinearLayout card=Ui.padded(a,12);card.setBackground(Ui.outlined(a,Ui.RAISED,16));
     String when=c.isNull("at_second")?Rewards.until(c.optString("created_at")):"▶ "+PlaybackProgress.time(c.optLong("at_second")*1000);
     LinearLayout head=Social.person(a,c,decor,when,32);card.addView(head);
     if(!c.isNull("at_second")&&seek!=null){head.setClickable(true);head.setOnClickListener(v->{seek.to(c.optLong("at_second")*1000);dialog.dismiss();});}
     TextView body=Ui.text(a,c.optBoolean("spoiler")?"⚠ Спойлер — нажми, чтобы показать":c.optString("body"),14,c.optBoolean("spoiler")?Ui.MUTED:Ui.TEXT);if(c.optBoolean("spoiler"))body.setOnClickListener(v->{body.setText(c.optString("body"));body.setTextColor(Ui.TEXT);});Ui.add(card,body,4);
     LinearLayout reacts=Ui.row(a);JSONObject counts=c.optJSONObject("reactions");Set<String> mine=new HashSet<>();JSONArray my=c.optJSONArray("my_reactions");if(my!=null)for(int k=0;k<my.length();k++)mine.add(my.optString(k));
     for(String e:EMOJI){int n=counts==null?0:counts.optInt(e);if(n==0&&!account.signedIn())continue;Button r=Ui.chip(a,e+(n>0?" "+n:""));r.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);Ui.chipState(r,mine.contains(e));r.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);r.setTextSize(12);r.setMinWidth(0);r.setMinimumWidth(0);r.setPadding(Ui.dp(a,8),0,Ui.dp(a,8),0);
      r.setOnClickListener(v->{if(needAccount(a,account))return;async(a,()->account.rpc("react_comment",new JSONObject().put("p_id",c.optLong("id")).put("p_emoji",e)),x->reload[0].run());});
      LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,Ui.dp(a,34));rp.rightMargin=Ui.dp(a,6);reacts.addView(r,rp);}
     if(c.optBoolean("mine")){Button del=Ui.textButton(a,"Удалить");del.setTextSize(12);del.setOnClickListener(v->async(a,()->account.rpc("delete_comment",new JSONObject().put("p_id",c.optLong("id"))),x->reload[0].run()));reacts.addView(del);}
     HorizontalScrollView rs=new HorizontalScrollView(a);rs.setHorizontalScrollBarEnabled(false);rs.addView(reacts);Ui.add(card,rs,8);
     Ui.add(list,card,i==0?0:8);}
   });};
  send.setOnClickListener(v->{if(needAccount(a,account))return;String text=input.getText().toString().trim();if(text.isEmpty())return;send.setEnabled(false);
   async(a,()->account.rpc("post_comment",new JSONObject().put("p_anime_id",anime.id).put("p_episode",episode).put("p_body",text).put("p_at_second",stamp.isChecked()&&clock!=null?clock.now()/1000:JSONObject.NULL).put("p_spoiler",spoiler.isChecked())),x->{send.setEnabled(true);input.setText("");reload[0].run();});
   send.postDelayed(()->send.setEnabled(true),4000);});
  reload[0].run();
 }
}
