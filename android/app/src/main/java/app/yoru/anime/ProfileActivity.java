package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Аккаунт Yoru: вход и регистрация, профиль с уровнем и очками, витрина любимых аниме и магазин украшений. */
public final class ProfileActivity extends Activity {
 Account account;Library library;
 LinearLayout root,content;ScrollView scroll;ProgressBar busyBar;TextView message;
 JSONObject profile=new JSONObject();JSONArray decorations=new JSONArray();Map<String,JSONObject> byId=new HashMap<>();Set<String> owned=new HashSet<>();
 String tab="frame";boolean registering,busy;int generation;
 JSONArray achievements=new JSONArray();Set<String> unlocked=new HashSet<>();String viewing="";boolean isAdmin;
 static final int PICK_AVATAR=41,PICK_BANNER=42;Runnable afterUpload;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);account=new Account(this);library=new Library(this);
  if(saved!=null){tab=saved.getString("tab","frame");registering=saved.getBoolean("registering");}
  viewing=getIntent().getStringExtra("username");if(viewing==null)viewing="";
  root=Ui.root(this);
  LinearLayout toolbar=Ui.row(this);toolbar.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4));
  Button back=Ui.iconButton(this,"back","Назад");back.setOnClickListener(v->finish());toolbar.addView(back);
  TextView heading=Ui.title(this,viewing.isEmpty()?"Профиль":"@"+viewing,20);if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,4);toolbar.addView(heading,hp);
  busyBar=new ProgressBar(this);busyBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));busyBar.setVisibility(View.INVISIBLE);toolbar.addView(busyBar,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)));
  root.addView(toolbar);
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);content=Ui.padded(this,16);content.setPadding(Ui.dp(this,Ui.GUTTER),Ui.dp(this,8),Ui.dp(this,Ui.GUTTER),Ui.dp(this,48));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  show();
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putBoolean("registering",registering);super.onSaveInstanceState(state);}
 @Override protected void onDestroy(){generation++;super.onDestroy();}

 void show(){
  if(!Account.configured())notConfigured();
  else if(!viewing.isEmpty()&&!viewing.equals(account.cachedProfile().optString("username"))){content.removeAllViews();loadPublic();}
  else if(!account.signedIn())authForm("");
  else{profile=account.cachedProfile();decorations=account.cachedDecorations();byId=Decor.index(decorations);achievements=account.cachedAchievements();if(profile.has("username"))renderProfile();else{content.removeAllViews();setBusy(true);}refresh();}
 }
 void setBusy(boolean value){busy=value;busyBar.setVisibility(value?View.VISIBLE:View.INVISIBLE);}

 /** Выполняет действие в фоне и возвращает результат в UI, если экран ещё открыт. */
 interface Job<T>{T run() throws Exception;}
 interface Done<T>{void accept(T value);}
 <T> void background(Job<T> job,Done<T> done){
  int session=++generation;setBusy(true);
  Api.IO.execute(()->{try{T value=job.run();runOnUiThread(()->{if(isDestroyed()||session!=generation)return;setBusy(false);done.accept(value);});}
   catch(Exception exception){runOnUiThread(()->{if(isDestroyed()||session!=generation)return;setBusy(false);failed(exception);});}});
 }
 void failed(Exception exception){
  String text=exception instanceof java.io.IOException&&!(exception instanceof Account.Failure)&&exception.getMessage()!=null&&exception.getMessage().matches(".*[А-Яа-я].*")?exception.getMessage():Rewards.error(exception.getMessage());
  if(!account.signedIn()&&Account.configured()){authForm(text);return;}
  if(message!=null&&message.isAttachedToWindow()){message.setText(text);message.setVisibility(View.VISIBLE);}else Toast.makeText(this,text,Toast.LENGTH_LONG).show();
 }
 void refresh(){background(()->new Object[]{account.profile(),account.decorations(),account.inventory(),account.achievements(),account.unlocked(),account.isAdmin()},result->{profile=(JSONObject)result[0];decorations=(JSONArray)result[1];byId=Decor.index(decorations);@SuppressWarnings("unchecked")Set<String> inventory=(Set<String>)result[2];owned=inventory;achievements=(JSONArray)result[3];@SuppressWarnings("unchecked")Set<String> got=(Set<String>)result[4];unlocked=got;isAdmin=Boolean.TRUE.equals(result[5]);renderProfile();});}

 // ───── Чужой профиль ─────
 void loadPublic(){background(()->{JSONObject data=account.publicProfile(viewing);if(account.cachedDecorations().length()==0||missingStyles(data))account.decorations();if(account.cachedAchievements().length()==0)account.achievements();return data==null?new JSONObject():data;},data->{decorations=account.cachedDecorations();byId=Decor.index(decorations);achievements=account.cachedAchievements();renderPublic(data);});}
 /** Надетое у профиля украшение, которого нет в сохранённом каталоге (например, новое админское), — каталог нужно обновить. */
 boolean missingStyles(JSONObject data){JSONObject p=data==null?null:data.optJSONObject("profile");if(p==null)return false;Map<String,JSONObject> known=Decor.index(account.cachedDecorations());
  for(String key:new String[]{"equipped_frame","equipped_badge","equipped_color","equipped_deco","equipped_effect","equipped_pframe"}){String id=p.optString(key);if(!id.isEmpty()&&!known.containsKey(id))return true;}return false;}
 void renderPublic(JSONObject data){
  content.removeAllViews();JSONObject p=data.optJSONObject("profile");
  if(p==null){LinearLayout card=card();card.addView(Ui.title(this,"Профиль не найден",18));Ui.add(card,Ui.text(this,"Пользователя @"+viewing+" нет — возможно, он сменил ник.",14,Ui.MUTED),8);content.addView(card);return;}
  int total=p.optInt("total_earned"),level=Rewards.level(total);
  LinearLayout hero=hero(p,false);
  String state=data.optString("friendship");
  if(!state.equals("self")){Button friend=Ui.tonal(this,state.equals("friend")?"✓ Друзья · удалить":state.equals("outgoing")?"Заявка отправлена":state.equals("incoming")?"Принять заявку в друзья":"Добавить в друзья");friend.setEnabled(!state.equals("outgoing"));
   friend.setOnClickListener(v->{if(!account.signedIn()){startActivity(new Intent(this,ProfileActivity.class));return;}
    if(state.equals("friend"))background(()->{account.rpc("remove_friend",new JSONObject().put("p_user",p.optString("id")));return true;},ok->loadPublic());
    else if(state.equals("incoming"))background(()->(JSONObject)account.rpc("respond_friend_request",new JSONObject().put("p_requester",p.optString("id")).put("p_accept",true)),r->{Social.announce(this,r);loadPublic();});
    else background(()->(JSONObject)account.rpc("send_friend_request",new JSONObject().put("p_username",p.optString("username"))),r->{Social.announce(this,r);loadPublic();});});
   Ui.add(hero,friend,14);
   if(state.equals("friend")){Button gift=Ui.tonal(this,"Подарить");gift.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"gift",Ui.ACCENT,18),null,null,null);gift.setCompoundDrawablePadding(Ui.dp(this,6));gift.setOnClickListener(v->Extras.giftTo(this,account,p,null));Ui.add(hero,gift,8);}}
  content.addView((View)hero.getTag(),new LinearLayout.LayoutParams(-1,-2));
  LinearLayout stats=Ui.row(this);
  stats.addView(stat("moon",String.valueOf(level),"уровень",Ui.ACCENT),new LinearLayout.LayoutParams(0,-1,1));
  stats.addView(stat("play",String.valueOf(p.optInt("episodes_watched")),"серий",Ui.LIVE),margin(new LinearLayout.LayoutParams(0,-1,1)));
  stats.addView(stat("calendar",String.valueOf(p.optInt("streak_days")),"дней подряд",0xFFFF8A5B),margin(new LinearLayout.LayoutParams(0,-1,1)));
  Ui.add(content,stats,16);
  JSONObject lists=data.optJSONObject("lists");if(lists==null)lists=new JSONObject();
  LinearLayout listCard=card();listCard.addView(section("Списки"));StringBuilder line=new StringBuilder();for(String key:Account.LISTS){if(line.length()>0)line.append(" · ");line.append(Account.listLabel(key)).append(": ").append(lists.optInt(key));}line.append(" · Отзывов: ").append(data.optInt("reviews"));Ui.add(listCard,Ui.text(this,line.toString(),13,Ui.MUTED),8);Ui.add(content,listCard,16);
  JSONArray showcase=p.optJSONArray("showcase");if(showcase!=null&&showcase.length()>0){Ui.add(content,section("Любимое аниме"),32);HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);strip.addView(row);for(int i=0;i<showcase.length();i++){JSONObject item=showcase.optJSONObject(i);if(item!=null)row.addView(showcaseItem(item),new LinearLayout.LayoutParams(Ui.dp(this,104),-2));}Ui.add(content,strip,8);}
  Set<String> theirs=new HashSet<>();JSONArray ids=data.optJSONArray("achievements");if(ids!=null)for(int i=0;i<ids.length();i++)theirs.add(ids.optString(i));
  Ui.add(content,Extras.stats(this,account,p.optString("username")),24);
  JSONArray favorites=data.optJSONArray("favorites");if(favorites!=null&&favorites.length()>0){Ui.add(content,section("Любимые"),32);for(int i=0;i<Math.min(6,favorites.length());i++){JSONObject f=favorites.optJSONObject(i);try{JSONObject raw=new JSONObject().put("anime_id",f.optLong("anime_id")).put("title",f.optString("title")).put("poster",new JSONObject().put("fullsize",f.optString("poster")));Ui.add(content,Extras.animeRow(this,raw,""),8);}catch(JSONException ignored){}}}
  achievementGrid(theirs);
 }

 /** Сетка достижений: открытые — в цвете, закрытые — приглушены. */
 void achievementGrid(Set<String> got){
  if(achievements.length()==0)return;
  Ui.add(content,section("Достижения · "+got.size()+" из "+achievements.length()),32);
  int columns=getResources().getConfiguration().screenWidthDp>=600?5:3;LinearLayout row=null;
  for(int i=0;i<achievements.length();i++){JSONObject a=achievements.optJSONObject(i);if(a==null)continue;boolean open=got.contains(a.optString("id"));
   if(i%columns==0){row=Ui.row(this);row.setGravity(Gravity.TOP);row.setBaselineAligned(false);Ui.add(content,row,8);}
   LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10));cell.setBackground(Ui.ripple(this,open?Ui.SOFT:Ui.PANEL,16));
   TextView glyph=Ui.text(this,a.optString("glyph"),26,open?Ui.WARM:0xFF4A4B5A);glyph.setGravity(Gravity.CENTER);if(!open)glyph.setAlpha(.5f);cell.addView(glyph);
   TextView name=Ui.text(this,a.optString("title"),11,open?Ui.TEXT:Ui.MUTED);name.setGravity(Gravity.CENTER);name.setMaxLines(2);Ui.add(cell,name,6);
   cell.setClickable(true);cell.setFocusable(true);cell.setContentDescription(a.optString("title")+(open?", получено":", не получено")+". "+a.optString("description"));
   cell.setOnClickListener(v->Toast.makeText(this,a.optString("title")+": "+a.optString("description")+(a.optInt("points")>0?" · +"+a.optInt("points")+" очков":"")+(a.isNull("reward")||a.optString("reward").isEmpty()?"":" · украшение в награду"),Toast.LENGTH_LONG).show());
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i%columns>0)lp.leftMargin=Ui.dp(this,8);row.addView(cell,lp);}
  if(row!=null)for(int k=achievements.length()%columns;k>0&&k<columns;k++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.leftMargin=Ui.dp(this,8);row.addView(new View(this),lp);}
 }

 // ───── Нет настроек сервера ─────
 void notConfigured(){
  content.removeAllViews();LinearLayout card=card();
  card.addView(icon("user"));TextView title=Ui.title(this,"Аккаунты пока не подключены",18);Ui.add(card,title,16);
  Ui.add(card,Ui.text(this,"В этой сборке не указан адрес базы данных Supabase. Каталог, избранное и просмотр работают как раньше, без аккаунта. Чтобы включить аккаунты, заполни supabase.properties и пересобери приложение — инструкция в README.",14,Ui.MUTED),8);
  content.addView(card,new LinearLayout.LayoutParams(-1,-2));
 }

 // ───── Вход и регистрация ─────
 /** Вход теперь на отдельном экране: без аккаунта приложение туда и возвращает. */
 void authForm(String error){startActivity(new Intent(this,AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}

 // ───── Профиль ─────
 void renderProfile(){
  int y=scroll.getScrollY();content.removeAllViews();
  int points=profile.optInt("points"),total=profile.optInt("total_earned"),level=Rewards.level(total);
  LinearLayout hero=hero(profile,true);
  LinearLayout heroActions=Ui.row(this);Button edit=Ui.tonal(this,"Изменить");edit.setOnClickListener(v->editProfile());heroActions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));Button friends=Ui.tonal(this,"Друзья");friends.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"user",Ui.ACCENT,18),null,null,null);friends.setCompoundDrawablePadding(Ui.dp(this,6));friends.setOnClickListener(v->startActivity(new Intent(this,FriendsActivity.class)));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=Ui.dp(this,8);heroActions.addView(friends,fp);Ui.add(hero,heroActions,16);
  if(isAdmin){Button admin=Ui.tonal(this,"Админ-панель");admin.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"shield",Ui.ACCENT,18),null,null,null);admin.setCompoundDrawablePadding(Ui.dp(this,6));admin.setOnClickListener(v->startActivity(new Intent(this,AdminActivity.class)));Ui.add(hero,admin,8);}
  content.addView((View)hero.getTag(),new LinearLayout.LayoutParams(-1,-2));

  // Очки, уровень, серии.
  LinearLayout stats=Ui.row(this);
  stats.addView(stat("gem",Rewards.points(points),"очков",Ui.WARM),new LinearLayout.LayoutParams(0,-1,1));
  stats.addView(stat("moon",String.valueOf(level),"уровень",Ui.ACCENT),margin(new LinearLayout.LayoutParams(0,-1,1)));
  int streak=profile.optInt("streak_days");stats.addView(stat("calendar",String.valueOf(streak),streak>0?"🔥 дней подряд":"дней подряд",0xFFFF8A5B),margin(new LinearLayout.LayoutParams(0,-1,1)));
  Ui.add(content,stats,16);
  LinearLayout progress=card();
  LinearLayout pr=Ui.row(this);pr.addView(Ui.title(this,"Уровень "+level,15),new LinearLayout.LayoutParams(0,-2,1));pr.addView(Ui.text(this,"ещё "+Rewards.points(Rewards.toNext(total))+" до "+(level+1),12,Ui.MUTED));progress.addView(pr);
  ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress(Rewards.progress(total));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));bar.setContentDescription("Прогресс уровня "+level);Ui.add(progress,bar,10);
  Ui.add(progress,Ui.text(this,"Засчитано серий: "+profile.optInt("episodes_watched")+". Очки начисляются за просмотр в плеере Yoru: "+Rewards.EPISODE_POINTS+" за серию, +"+Rewards.NEW_ANIME_BONUS+" за новое аниме и до +20 за дни подряд. Серия засчитывается после 5–12 минут просмотра. Ещё очки дают достижения.",12,Ui.MUTED),10);
  Ui.add(content,progress,16);

  // Рейтинг, подарки, «Год в Yoru» и перенос со старого сайта.
  String[][] tools={{"trophy","Рейтинг недели"},{"gift","Подарки"},{"spark","Год в Yoru"},{"party","Смотреть вместе"},{"catalog","Для тебя"},{"download","Перенос с YummyAnime"}};
  for(int i=0;i<tools.length;i+=2){LinearLayout row=Ui.row(this);for(int j=0;j<2;j++){String[] tool=tools[i+j];Button b=Ui.tonal(this,tool[1]);b.setTextSize(13);b.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,tool[0],Ui.ACCENT,18),null,null,null);b.setCompoundDrawablePadding(Ui.dp(this,6));
    b.setOnClickListener(v->{switch(tool[1]){case "Рейтинг недели":Extras.leaderboard(this,account);break;case "Подарки":Extras.inbox(this,account,this::refresh);break;case "Год в Yoru":startActivity(new Intent(this,WrappedActivity.class));break;case "Смотреть вместе":joinRoom();break;case "Для тебя":Extras.forYou(this,account);break;default:Extras.importOld(this,account,this::refresh);}});
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(j>0)lp.leftMargin=Ui.dp(this,10);row.addView(b,lp);}Ui.add(content,row,i==0?24:10);}
  Ui.add(content,Extras.stats(this,account,profile.optString("username")),24);

  // Витрина любимых аниме.
  LinearLayout showHead=Ui.row(this);showHead.addView(section("Любимое аниме"),new LinearLayout.LayoutParams(0,-2,1));Button change=Ui.textButton(this,"Изменить");change.setOnClickListener(v->editShowcase());showHead.addView(change);Ui.add(content,showHead,32);
  JSONArray showcase=profile.optJSONArray("showcase");
  if(showcase==null||showcase.length()==0){TextView empty=Ui.text(this,"Выбери до 6 аниме из избранного — они появятся здесь.",13,Ui.MUTED);Ui.add(content,empty,4);}
  else{HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);strip.addView(row);
   for(int i=0;i<showcase.length();i++){JSONObject item=showcase.optJSONObject(i);if(item==null)continue;row.addView(showcaseItem(item),new LinearLayout.LayoutParams(Ui.dp(this,104),-2));}
   Ui.add(content,strip,8);}

  achievementGrid(unlocked);

  // Магазин украшений.
  LinearLayout decoHead=Ui.row(this);decoHead.addView(section("Украшения"),new LinearLayout.LayoutParams(0,-2,1));TextView balance=Ui.text(this,Rewards.points(points),14,Ui.WARM);balance.setTypeface(Ui.MEDIUM);balance.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"gem",Ui.WARM,16),null,null,null);balance.setCompoundDrawablePadding(Ui.dp(this,6));balance.setContentDescription("Баланс: "+points+" очков");decoHead.addView(balance);Ui.add(content,decoHead,32);
  LinearLayout tabs=Ui.row(this);String[][] kinds={{"avatar_deco","Украшения аватара"},{"profile_effect","Эффекты профиля"},{"profile_frame","Рамки профиля"},{"frame","Обводка аватара"},{"badge","Значки"},{"name_color","Цвет ника"}};
  for(String[] kind:kinds){Button chip=Ui.chip(this,kind[1]);chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);Ui.chipState(chip,tab.equals(kind[0]));chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);chip.setContentDescription(kind[1]+(tab.equals(kind[0])?", выбрано":""));chip.setOnClickListener(v->{tab=kind[0];renderProfile();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=Ui.dp(this,8);tabs.addView(chip,cp);}
  HorizontalScrollView tabScroll=new HorizontalScrollView(this);tabScroll.setHorizontalScrollBarEnabled(false);tabScroll.addView(tabs);Ui.add(content,tabScroll,4);
  List<JSONObject> items=new ArrayList<>();long now=System.currentTimeMillis();
  for(int i=0;i<decorations.length();i++){JSONObject d=decorations.optJSONObject(i);if(d==null||!d.optString("kind").equals(tab))continue;if(d.optBoolean("admin_only")&&!isAdmin)continue;boolean have=owned.contains(d.optString("id"));
   if(!have&&!Rewards.onSale(d.optString("available_from"),d.optString("available_until"),now))continue;items.add(d);}
  int columns=getResources().getConfiguration().screenWidthDp>=600?3:2;
  for(int i=0;i<items.size();i+=columns){LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);row.setBaselineAligned(false);for(int j=0;j<columns;j++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(j>0)lp.leftMargin=Ui.dp(this,10);if(i+j<items.size())row.addView(tile(items.get(i+j),points,level),lp);else row.addView(new View(this),lp);}Ui.add(content,row,10);}
  if(items.isEmpty())Ui.add(content,Ui.text(this,busy?"Загружаем украшения…":"Украшения пока не загрузились.",13,Ui.MUTED),8);

  message=Ui.text(this,"",13,0xFFFF9A8B);message.setVisibility(View.GONE);Ui.add(content,message,12);
  Ui.add(content,Ui.text(this,"Аккаунт: "+account.email(),12,Ui.MUTED),26);
  Button logout=Ui.textButton(this,"Выйти из аккаунта");logout.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);logout.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"logout",Ui.ACCENT,20),null,null,null);logout.setCompoundDrawablePadding(Ui.dp(this,8));logout.setOnClickListener(v->{generation++;setBusy(false);account.signOut();registering=false;authForm("");});Ui.add(content,logout,4);
  Button updates=Ui.textButton(this,"Проверить обновления · версия "+BuildConfig.VERSION_NAME);updates.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);updates.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"download",Ui.ACCENT,20),null,null,null);updates.setCompoundDrawablePadding(Ui.dp(this,8));updates.setOnClickListener(v->Updater.check(this,true));Ui.add(content,updates,0);
  scroll.post(()->scroll.scrollTo(0,y));
 }

 View showcaseItem(JSONObject item){
  LinearLayout cell=Ui.column(this);cell.setPadding(0,0,Ui.dp(this,10),0);
  ImageView poster=new ImageView(this);Ui.round(poster,12);poster.setContentDescription(item.optString("title"));Ui.poster(this,poster,item.optString("poster"));cell.addView(poster,new LinearLayout.LayoutParams(-1,Ui.dp(this,134)));
  TextView title=Ui.text(this,item.optString("title"),12,Ui.TEXT);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);Ui.add(cell,title,6);
  cell.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,12));cell.setClickable(true);cell.setFocusable(true);
  cell.setOnClickListener(v->{JSONObject raw=null;for(Anime a:library.favorites())if(a.id==item.optLong("id"))raw=a.raw;
   try{if(raw==null)raw=new JSONObject().put("anime_id",item.optLong("id")).put("title",item.optString("title")).put("poster",new JSONObject().put("fullsize",item.optString("poster")));}catch(JSONException e){return;}
   startActivity(new Intent(this,DetailActivity.class).putExtra("anime",raw.toString()));});
  return cell;
 }

 View tile(JSONObject d,int points,int level){
  String id=d.optString("id"),kind=d.optString("kind");JSONObject style=d.optJSONObject("style");if(style==null)style=new JSONObject();
  int price=d.optInt("price"),minLevel=d.optInt("min_level",1);boolean exclusive=d.optBoolean("exclusive"),staff=d.optBoolean("admin_only");boolean have=(price==0&&!exclusive)||owned.contains(id);
  String until=d.isNull("available_until")?"":d.optString("available_until");
  String equippedId=profile.optString(equippedField(kind));boolean worn=id.equals(equippedId);boolean removable=!kind.equals("frame")&&!kind.equals("name_color");
  LinearLayout tile=Ui.column(this);tile.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,12));tile.setGravity(Gravity.CENTER_HORIZONTAL);
  android.graphics.drawable.GradientDrawable face=Ui.shape(this,worn?Ui.SOFT:Ui.PANEL,18);face.setStroke(Ui.dp(this,1),worn?0xFF7A2A3A:Ui.LINE);
  // Своя подложка у набора администрации: графит с тонким серебряным кантом (надетое — кант акцента).
  if(staff){face=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF22242C,worn?0xFF2E1C24:0xFF1A1B21});face.setCornerRadius(Ui.dp(this,18));face.setStroke(Ui.dp(this,1),worn?0xFFFF8A3D:0xFF5C6576);}
  tile.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24FFFFFF),face,Ui.shape(this,android.graphics.Color.WHITE,18)));
  FrameLayout preview=new FrameLayout(this);tile.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,72)));
  if(kind.equals("frame")){Decor.AvatarView a=new Decor.AvatarView(this);a.frame(style);a.person(Decor.name(profile),profile.optString("avatar_url"));preview.addView(a,new FrameLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,68),Gravity.CENTER));}
  else if(kind.equals("badge")){TextView g=Ui.text(this,"",34,Ui.TEXT);g.setText(Decor.badge(style));g.setGravity(Gravity.CENTER);preview.addView(g,new FrameLayout.LayoutParams(-1,-1));}
  else if(kind.equals("avatar_deco")){Decor.AvatarView a=new Decor.AvatarView(this);a.frame(Decor.style(byId,profile.optString("equipped_frame","frame_moon")));a.deco(style);a.person(Decor.name(profile),profile.optString("avatar_url"));preview.addView(a,new FrameLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76),Gravity.CENTER));}
  else if(kind.equals("profile_effect")||kind.equals("profile_frame")){preview.addView(new Ornaments.Preview(this,kind,style),new FrameLayout.LayoutParams(Ui.dp(this,104),-1,Gravity.CENTER));}
  else{TextView n=Ui.title(this,Decor.name(profile),18);n.setGravity(Gravity.CENTER);n.setMaxLines(1);n.setEllipsize(TextUtils.TruncateAt.END);Decor.nameColor(n,style);preview.addView(n,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));}
  TextView title=Ui.title(this,d.optString("title"),14);title.setGravity(Gravity.CENTER);title.setMaxLines(2);Ui.add(tile,title,8);
  TextView rarity=staff?Ui.text(this,"☾ Только для администрации",11,0xFFC9D2E3):Ui.text(this,Rewards.rarity(d.optString("rarity"))+(until.isEmpty()||have?"":" · до "+Rewards.until(until)),11,Rewards.rarityColor(d.optString("rarity")));if(staff)rarity.setTypeface(Ui.MEDIUM);rarity.setGravity(Gravity.CENTER);Ui.add(tile,rarity,4);
  String state;int color;
  if(worn){state=removable?"✓ Надето · снять":"✓ Надето";color=Ui.ACCENT;}
  else if(have){state="Надеть";color=Ui.TEXT;}
  else if(staff){state="Выдаётся администрации";color=0xFF60616F;}
  else if(exclusive){state="🏆 За достижение";color=0xFF60616F;}
  else if(level<minLevel){state="С уровня "+minLevel;color=0xFF60616F;}
  else{state="◆ "+Rewards.points(price);color=points>=price?Ui.WARM:0xFF60616F;}
  TextView action=Ui.text(this,state,13,color);action.setTypeface(Ui.MEDIUM);action.setGravity(Gravity.CENTER);Ui.add(tile,action,10);
  tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription(d.optString("title")+", "+(staff?"только для администрации":Rewards.rarity(d.optString("rarity")))+". "+state.replace("◆","цена")+(state.startsWith("◆")?" очков":""));
  tile.setOnClickListener(v->{
   if(busy)return;
   if(worn){if(removable)equip(kind,"");return;}
   if(have){equip(kind,id);return;}
   if(staff){Toast.makeText(this,"Это украшение выдаётся только администрации.",Toast.LENGTH_LONG).show();return;}
   if(exclusive){Toast.makeText(this,d.optString("description"),Toast.LENGTH_LONG).show();return;}
   if(level<minLevel){Toast.makeText(this,"Откроется на уровне "+minLevel,Toast.LENGTH_SHORT).show();return;}
   if(points<price){Toast.makeText(this,"Не хватает "+Rewards.points(price-points)+" очков",Toast.LENGTH_SHORT).show();return;}
   confirmPurchase(d);
  });
  return tile;
 }
 void joinRoom(){
  LinearLayout panel=Ui.column(this);Ui.add(panel,Ui.text(this,"Введи код комнаты от друга. Чтобы создать свою комнату, открой серию и нажми «Вместе».",13,Ui.MUTED),0);
  EditText field=input("Код комнаты",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);Ui.add(panel,field,12);Button go=Ui.button(this,"Войти",true);Ui.add(panel,go,12);
  Dialog dialog=Ui.sheet(this,"Смотреть вместе",panel,"Закрыть");go.setOnClickListener(v->{String code=field.getText().toString().trim().toUpperCase(Locale.ROOT);if(code.length()!=6)return;dialog.dismiss();Party.joinCode(this,account,code);});
 }
 static String equippedField(String kind){switch(kind){case "frame":return "equipped_frame";case "badge":return "equipped_badge";case "avatar_deco":return "equipped_deco";case "profile_effect":return "equipped_effect";case "profile_frame":return "equipped_pframe";default:return "equipped_color";}}
 void equip(String kind,String id){background(()->(JSONObject)account.rpc("equip_decoration",new JSONObject().put("p_kind",kind).put("p_id",id)),result->{profile=result;renderProfile();});}
 void confirmPurchase(JSONObject d){
  LinearLayout panel=Ui.column(this);int price=d.optInt("price");
  Ui.add(panel,Ui.text(this,d.optString("description"),14,Ui.MUTED),4);
  Ui.add(panel,Ui.text(this,"Останется: ◆ "+Rewards.points(profile.optInt("points")-price),13,Ui.MUTED),12);
  Button buy=Ui.button(this,"Купить и надеть за ◆ "+Rewards.points(price),true);Ui.add(panel,buy,16);
  Dialog dialog=Ui.sheet(this,d.optString("title"),panel,"Закрыть покупку");
  buy.setOnClickListener(v->{dialog.dismiss();String id=d.optString("id"),kind=d.optString("kind");
   background(()->{account.rpc("buy_decoration",new JSONObject().put("p_id",id));JSONObject p=(JSONObject)account.rpc("equip_decoration",new JSONObject().put("p_kind",kind).put("p_id",id));return p;},
    result->{profile=result;owned.add(id);if(owned.size()>=5)refresh();else renderProfile();Toast.makeText(this,"«"+d.optString("title")+"» теперь твоё",Toast.LENGTH_SHORT).show();});});
 }

 /** Шапка профиля в стиле Discord: баннер, аватар поверх него с рамкой, ник, местоимения, статус и «Обо мне» на фоне цветов темы. */
 LinearLayout hero(JSONObject p,boolean own){
  int primary=Rewards.color(p.optString("theme_primary"),0xFF22222A),accent=Rewards.color(p.optString("theme_accent"),0xFF16161B);boolean themed=!p.optString("theme_primary").isEmpty()||!p.optString("theme_accent").isEmpty();
  LinearLayout card=Ui.column(this);android.graphics.drawable.GradientDrawable bg=themed?Ui.gradient(this,primary,accent,24,android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM):Ui.outlined(this,Ui.PANEL,24);if(themed)bg.setStroke(Ui.dp(this,1),0x33FFFFFF);card.setBackground(bg);card.setClipToOutline(true);Ui.lift(card,10);
  FrameLayout top=new FrameLayout(this);card.addView(top,new LinearLayout.LayoutParams(-1,Ui.dp(this,176)));
  Media.Banner banner=new Media.Banner(this);top.addView(banner,new FrameLayout.LayoutParams(-1,Ui.dp(this,128)));banner.show(this,p.optString("banner_url"),themed?primary:Ui.ACCENT,themed?accent:Ui.ACCENT2);
  Decor.AvatarView avatar=new Decor.AvatarView(this);avatar.frame(Decor.style(byId,p.optString("equipped_frame","frame_moon")));avatar.deco(Decor.style(byId,p.optString("equipped_deco")));avatar.person(Decor.name(p),p.optString("avatar_url"));
  int avatarSide=avatar.hasDeco()?132:96;FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(Ui.dp(this,avatarSide),Ui.dp(this,avatarSide),Gravity.BOTTOM|Gravity.START);ap.leftMargin=Ui.dp(this,16-(avatarSide-96)/2);top.addView(avatar,ap);
  LinearLayout body=Ui.column(this);body.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,18));card.addView(body);
  LinearLayout nameRow=Ui.row(this);TextView name=Ui.title(this,Decor.name(p),24);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);Decor.nameColor(name,Decor.style(byId,p.optString("equipped_color","color_default")));nameRow.addView(name,new LinearLayout.LayoutParams(-2,-2));
  CharSequence badge=Decor.badge(Decor.style(byId,p.optString("equipped_badge")));if(badge.length()>0){TextView glyph=Ui.text(this,"",20,Ui.TEXT);glyph.setText(badge);glyph.setPadding(Ui.dp(this,8),0,0,0);JSONObject d=byId.get(p.optString("equipped_badge"));glyph.setContentDescription("Значок: "+(d==null?"":d.optString("title")));nameRow.addView(glyph);}
  body.addView(nameRow);
  String handle="@"+p.optString("username")+(p.optString("pronouns").isEmpty()?"":"  ·  "+p.optString("pronouns"));Ui.add(body,Ui.text(this,handle,13,0xCCFFFFFF),4);
  if(p.optBoolean("banned")){TextView ban=Ui.badge(this,0x55FF4D67,Ui.TEXT);ban.setText("Аккаунт заблокирован");Ui.add(body,ban,8);}
  String statusText=p.optString("status");
  if(!statusText.isEmpty()||own){TextView status=Ui.text(this,statusText.isEmpty()?"Добавь статус — о чём сейчас твой вечер?":statusText,14,statusText.isEmpty()?0x99FFFFFF:Ui.TEXT);status.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));status.setBackground(Ui.shape(this,0x33000000,14));Ui.add(body,status,12);}
  String bio=p.optString("bio");
  if(!bio.isEmpty()){LinearLayout about=Ui.column(this);about.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12));about.setBackground(Ui.shape(this,0x33000000,14));TextView head=Ui.text(this,"ОБО МНЕ",11,0xBBFFFFFF);head.setTypeface(Ui.MEDIUM);head.setLetterSpacing(.08f);about.addView(head);Ui.add(about,Ui.text(this,bio,14,Ui.TEXT),6);Ui.add(body,about,10);}
  FrameLayout holder=new FrameLayout(this);holder.addView(card,new FrameLayout.LayoutParams(-1,-2));
  Ornaments.Overlay overlay=new Ornaments.Overlay(this);overlay.set(p.optString("equipped_effect").isEmpty()?null:Decor.style(byId,p.optString("equipped_effect")),p.optString("equipped_pframe").isEmpty()?null:Decor.style(byId,p.optString("equipped_pframe")));
  holder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.setElevation(Ui.dp(this,11));
  body.setTag(holder);return body;
 }

 void editProfile(){
  LinearLayout panel=Ui.column(this);
  // Аватар и баннер: загрузка файлов (PNG/JPEG/WebP/GIF/WebM) или прежние варианты.
  String[] avatar={profile.optString("avatar_url")},bannerUrl={profile.optString("banner_url")};
  Ui.add(panel,label("Аватар и баннер"),4);
  Ui.add(panel,Ui.text(this,"Картинки обрезаются и сжимаются автоматически. GIF и WebM — аватар до 4 МБ и 640×640, баннер до 8 МБ и 1920×1080, видео до 15 секунд.",12,Ui.MUTED),4);
  LinearLayout uploads=Ui.row(this);Button upAvatar=Ui.tonal(this,"Аватар…"),upBanner=Ui.tonal(this,"Баннер…");for(Button b:new Button[]{upAvatar,upBanner}){b.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"upload",Ui.ACCENT,18),null,null,null);b.setCompoundDrawablePadding(Ui.dp(this,6));}
  uploads.addView(upAvatar,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ubp=new LinearLayout.LayoutParams(0,-2,1);ubp.leftMargin=Ui.dp(this,8);uploads.addView(upBanner,ubp);Ui.add(panel,uploads,10);
  List<String> labels=new ArrayList<>();List<String> urls=new ArrayList<>();labels.add("Первая буква ника");urls.add("");
  if(avatar[0].contains("/storage/v1/object/public/media/")){labels.add("Загруженный аватар");urls.add(avatar[0]);}
  JSONArray showcase=profile.optJSONArray("showcase");if(showcase!=null)for(int i=0;i<showcase.length();i++){JSONObject item=showcase.optJSONObject(i);if(item!=null&&item.optString("poster").startsWith("https://")){labels.add("Постер: "+item.optString("title"));urls.add(item.optString("poster"));}}
  Button avatarField=Ui.field(this,"Аватар");Ui.fieldValue(avatarField,"Аватар",labels.get(Math.max(0,urls.indexOf(avatar[0]))));
  avatarField.setOnClickListener(v->Ui.choose(this,"Аватар",labels,labels.get(Math.max(0,urls.indexOf(avatar[0]))),choice->{avatar[0]=urls.get(labels.indexOf(choice));Ui.fieldValue(avatarField,"Аватар",choice);}));Ui.add(panel,avatarField,8);
  Button bannerField=Ui.field(this,"Баннер");Ui.fieldValue(bannerField,"Баннер",bannerUrl[0].isEmpty()?"Градиент цветов темы":"Загруженный баннер");
  bannerField.setOnClickListener(v->Ui.choose(this,"Баннер",bannerUrl[0].isEmpty()?Arrays.asList("Градиент цветов темы"):Arrays.asList("Загруженный баннер","Убрать баннер"),bannerUrl[0].isEmpty()?"Градиент цветов темы":"Загруженный баннер",choice->{if(choice.equals("Убрать баннер")){bannerUrl[0]="";Ui.fieldValue(bannerField,"Баннер","Градиент цветов темы");}}));Ui.add(panel,bannerField,8);
  // Тексты.
  EditText display=input("Имя в профиле",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);display.setText(profile.optString("display_name"));display.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});Ui.add(panel,label("Имя"),18);Ui.add(panel,display,6);
  EditText nick=input("Ник",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);nick.setText(profile.optString("username"));nick.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});Ui.add(panel,label("Ник — латиница, цифры и _"),14);Ui.add(panel,nick,6);
  EditText pronouns=input("например: он/его",InputType.TYPE_CLASS_TEXT);pronouns.setText(profile.optString("pronouns"));pronouns.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});Ui.add(panel,label("Местоимения"),14);Ui.add(panel,pronouns,6);
  EditText status=input("О чём сейчас твой вечер?",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);status.setText(profile.optString("status"));status.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});Ui.add(panel,label("Статус"),14);Ui.add(panel,status,6);
  EditText bio=input("Пара слов о себе",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);bio.setSingleLine(false);bio.setMinLines(3);bio.setMaxLines(6);bio.setGravity(Gravity.TOP|Gravity.START);bio.setText(profile.optString("bio"));bio.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(190)});Ui.add(panel,label("Обо мне · до 190 символов"),14);Ui.add(panel,bio,6);
  // Цвета темы профиля, как в Discord: основной сверху, акцент снизу.
  String[] theme={profile.optString("theme_primary"),profile.optString("theme_accent")};
  Ui.add(panel,label("Цвета темы профиля"),18);
  Ui.add(panel,swatches(theme,0,"Верх"),8);Ui.add(panel,swatches(theme,1,"Низ"),8);
  TextView error=Ui.text(this,"",13,0xFFFF8A8A);error.setVisibility(View.GONE);Ui.add(panel,error,10);
  Button save=Ui.button(this,"Сохранить",true);Ui.add(panel,save,16);
  Dialog dialog=Ui.sheet(this,"Профиль",panel,"Закрыть редактирование");
  upAvatar.setOnClickListener(v->{dialog.dismiss();pick(PICK_AVATAR);});upBanner.setOnClickListener(v->{dialog.dismiss();pick(PICK_BANNER);});
  save.setOnClickListener(v->{
   String newNick=nick.getText().toString().trim().toLowerCase(Locale.ROOT);
   if(!Rewards.validUsername(newNick)){error.setText("Ник: 3–20 символов, латиница, цифры и _.");error.setVisibility(View.VISIBLE);return;}
   boolean renamed=!newNick.equals(profile.optString("username"));dialog.dismiss();String oldAvatar=profile.optString("avatar_url"),oldBanner=profile.optString("banner_url");
   background(()->{if(renamed)account.rpc("change_username",new JSONObject().put("p_username",newNick));
     JSONObject result=account.update(new JSONObject().put("p_display_name",display.getText().toString().trim()).put("p_status",status.getText().toString().trim()).put("p_avatar_url",avatar[0])
      .put("p_bio",bio.getText().toString().trim()).put("p_pronouns",pronouns.getText().toString().trim()).put("p_banner_url",bannerUrl[0]).put("p_theme_primary",theme[0]).put("p_theme_accent",theme[1]));
     if(!oldAvatar.equals(avatar[0]))account.deleteOwn(oldAvatar);if(!oldBanner.equals(bannerUrl[0]))account.deleteOwn(oldBanner);return result;},
    result->{profile=result;renderProfile();});
  });
 }
 static final String[] THEME_COLORS={"","#FF4D67","#FF8A3D","#FFC14D","#4ADE80","#22D3EE","#3B82F6","#8B5CF6","#EC4899","#F5F5F7","#2A2A33","#0E0E11"};
 /** Ряд образцов цвета; первый — «без цвета». */
 View swatches(String[] theme,int index,String title){
  LinearLayout row=Ui.row(this);TextView name=Ui.text(this,title,13,Ui.MUTED);row.addView(name,new LinearLayout.LayoutParams(Ui.dp(this,44),-2));
  HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout list=Ui.row(this);scroll.addView(list);row.addView(scroll,new LinearLayout.LayoutParams(0,-2,1));
  List<View> all=new ArrayList<>();
  Runnable paint=()->{for(int i=0;i<all.size();i++){String c=THEME_COLORS[i];boolean on=c.equalsIgnoreCase(theme[index]);android.graphics.drawable.GradientDrawable d=c.isEmpty()?Ui.shape(this,Ui.RAISED,18):Ui.shape(this,Rewards.color(c,0),18);d.setStroke(Ui.dp(this,on?3:1),on?Ui.TEXT:Ui.LINE);all.get(i).setBackground(d);all.get(i).setContentDescription((c.isEmpty()?"Без цвета":"Цвет "+c)+(on?", выбран":""));}};
  for(int i=0;i<THEME_COLORS.length;i++){String c=THEME_COLORS[i];TextView sw=Ui.text(this,c.isEmpty()?"✕":"",14,Ui.MUTED);sw.setGravity(Gravity.CENTER);sw.setClickable(true);sw.setFocusable(true);sw.setOnClickListener(v->{theme[index]=c;paint.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36));lp.rightMargin=Ui.dp(this,8);list.addView(sw,lp);all.add(sw);}
  paint.run();return row;
 }

 // ───── Загрузка аватара и баннера ─────
 void pick(int request){
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/jpeg","image/webp","image/gif","video/webm"});
  try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Не найдено приложение для выбора файлов",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null||(request!=PICK_AVATAR&&request!=PICK_BANNER))return;
  boolean banner=request==PICK_BANNER;android.net.Uri uri=data.getData();String mime=getContentResolver().getType(uri);
  Toast.makeText(this,banner?"Загружаем баннер…":"Загружаем аватар…",Toast.LENGTH_SHORT).show();
  String old=profile.optString(banner?"banner_url":"avatar_url");
  background(()->{
   byte[] bytes;try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("Файл недоступен");byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>12*1024*1024)throw new java.io.IOException("Файл больше 12 МБ.");out.write(buf,0,n);}bytes=out.toByteArray();}
   Media.Prepared prepared=Media.prepare(this,bytes,mime,banner);
   String url=account.upload((banner?"banner-":"avatar-")+System.currentTimeMillis()+"."+prepared.ext,prepared.data,prepared.mime);
   JSONObject updated=account.update(new JSONObject().put(banner?"p_banner_url":"p_avatar_url",url));
   account.deleteOwn(old);return updated;},
   updated->{profile=updated;renderProfile();Toast.makeText(this,banner?"Баннер обновлён":"Аватар обновлён",Toast.LENGTH_SHORT).show();});
 }

 void editShowcase(){
  List<Anime> favorites=library.favorites();LinearLayout panel=Ui.column(this);
  if(favorites.isEmpty()){Ui.add(panel,Ui.text(this,"В избранном пока пусто. Нажми на сердце в карточке аниме, и его можно будет добавить в витрину.",14,Ui.MUTED),4);Ui.sheet(this,"Любимое аниме",panel,"Закрыть");return;}
  Set<Long> chosen=new LinkedHashSet<>();JSONArray current=profile.optJSONArray("showcase");if(current!=null)for(int i=0;i<current.length();i++){JSONObject o=current.optJSONObject(i);if(o!=null)chosen.add(o.optLong("id"));}
  TextView hint=Ui.text(this,"",13,Ui.MUTED);Ui.add(panel,hint,0);Runnable count=()->hint.setText("Выбрано "+chosen.size()+" из 6");count.run();
  for(Anime anime:favorites){CheckBox box=new CheckBox(this);box.setText(anime.title);box.setTextColor(Ui.TEXT);box.setTextSize(15);box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));box.setMinHeight(Ui.dp(this,52));box.setChecked(chosen.contains(anime.id));
   box.setOnCheckedChangeListener((b,checked)->{if(checked&&!chosen.contains(anime.id)&&chosen.size()>=6){b.setChecked(false);Toast.makeText(this,"В витрине помещается 6 аниме",Toast.LENGTH_SHORT).show();return;}if(checked)chosen.add(anime.id);else chosen.remove(anime.id);count.run();});
   Ui.add(panel,box,2);}
  Button save=Ui.button(this,"Сохранить витрину",true);Ui.add(panel,save,16);Dialog dialog=Ui.sheet(this,"Любимое аниме",panel,"Закрыть витрину");
  save.setOnClickListener(v->{dialog.dismiss();JSONArray result=new JSONArray();
   for(Long id:chosen)for(Anime a:favorites)if(a.id==id)try{result.put(new JSONObject().put("id",a.id).put("title",a.title.length()>120?a.title.substring(0,120):a.title).put("poster",a.poster));}catch(JSONException ignored){}
   String avatarUrl=profile.optString("avatar_url");boolean keepAvatar=avatarUrl.isEmpty();for(int i=0;i<result.length();i++)if(result.optJSONObject(i).optString("poster").equals(avatarUrl))keepAvatar=true;
   JSONObject fields=new JSONObject();try{fields.put("p_showcase",result);if(!keepAvatar)fields.put("p_avatar_url","");}catch(JSONException ignored){}
   background(()->account.update(fields),p->{profile=p;renderProfile();});});
 }

 // ───── Мелкие элементы ─────
 LinearLayout card(){LinearLayout card=Ui.padded(this,20);card.setBackground(Ui.outlined(this,Ui.PANEL,24));return card;}
 ImageView icon(String name){ImageView v=new ImageView(this);v.setImageDrawable(new Ui.AppIcon(this,name,Ui.ACCENT,28));v.setScaleType(ImageView.ScaleType.CENTER);v.setBackground(Ui.shape(this,Ui.SOFT,28));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,56)));return v;}
 TextView section(String text){TextView t=Ui.title(this,text,19);if(android.os.Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);return t;}
 TextView label(String text){TextView t=Ui.text(this,text,12,Ui.MUTED);t.setTypeface(Ui.MEDIUM);return t;}
 LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p){p.leftMargin=Ui.dp(this,12);return p;}
 View stat(String icon,String value,String caption,int color){
  LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14));box.setBackground(Ui.outlined(this,Ui.PANEL,20));
  TextView v=Ui.title(this,value,21);v.setTextColor(color);v.setMaxLines(1);v.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,icon,color,16),null,null,null);v.setCompoundDrawablePadding(Ui.dp(this,6));box.addView(v);
  Ui.add(box,Ui.text(this,caption,12,Ui.MUTED),6);box.setContentDescription(value+" "+caption);box.setFocusable(true);return box;
 }
 EditText input(String hint,int type){
  EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED);e.setTextColor(Ui.TEXT);e.setTextSize(15);e.setSingleLine(true);e.setInputType(type);e.setContentDescription(hint);
  e.setMinHeight(Ui.dp(this,52));e.setPadding(Ui.dp(this,16),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,10));e.setBackground(Ui.outlined(this,Ui.RAISED,14));return e;
 }
 void hideKeyboard(){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);View focus=getCurrentFocus();if(keyboard!=null&&focus!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);}
}
