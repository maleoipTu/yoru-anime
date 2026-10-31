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

/**
 * Админ-панель: сводка, пользователи (очки, украшения, бан, сброс оформления), отзывы, украшения и журнал действий.
 * Доступ проверяет сервер: все функции admin_* отказывают, если пользователя нет в таблице admins.
 */
public final class AdminActivity extends Activity {
 Account account;LinearLayout content,tabs;ProgressBar busyBar;String tab="stats";int generation;Map<String,JSONObject> decor=new HashMap<>();

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);account=new Account(this);if(saved!=null)tab=saved.getString("tab","stats");
  LinearLayout root=Ui.root(this);
  LinearLayout toolbar=Ui.row(this);toolbar.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4));
  Button back=Ui.iconButton(this,"back","Назад");back.setOnClickListener(v->finish());toolbar.addView(back);
  TextView heading=Ui.title(this,"Админ-панель",19);heading.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"shield",Ui.ACCENT,20),null,null,null);heading.setCompoundDrawablePadding(Ui.dp(this,8));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,4);toolbar.addView(heading,hp);
  busyBar=new ProgressBar(this);busyBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));busyBar.setVisibility(View.INVISIBLE);toolbar.addView(busyBar,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)));root.addView(toolbar);
  tabs=Ui.row(this);tabs.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,6));HorizontalScrollView ts=new HorizontalScrollView(this);ts.setHorizontalScrollBarEnabled(false);ts.addView(tabs);root.addView(ts);
  ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);content=Ui.column(this);content.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,32));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  renderTabs();open();
 }
 @Override protected void onSaveInstanceState(Bundle s){s.putString("tab",tab);super.onSaveInstanceState(s);}
 @Override protected void onDestroy(){generation++;super.onDestroy();}

 interface Job<T>{T run() throws Exception;}
 interface Done<T>{void accept(T value);}
 <T> void background(Job<T> job,Done<T> done){
  int session=++generation;busyBar.setVisibility(View.VISIBLE);
  Api.IO.execute(()->{try{T v=job.run();runOnUiThread(()->{if(isDestroyed()||session!=generation)return;busyBar.setVisibility(View.INVISIBLE);done.accept(v);});}
   catch(Exception e){runOnUiThread(()->{if(isDestroyed()||session!=generation)return;busyBar.setVisibility(View.INVISIBLE);String m=e.getMessage()==null?"":e.getMessage();
    if(m.contains("admin_only")){content.removeAllViews();content.addView(card(Ui.title(this,"Нет доступа",18),Ui.text(this,"Админ-панель открывается только для аккаунтов из таблицы admins в базе данных.",14,Ui.MUTED)));}
    else Toast.makeText(this,Rewards.error(m),Toast.LENGTH_LONG).show();});}});
 }
 void renderTabs(){
  tabs.removeAllViews();String[][] all={{"stats","Сводка"},{"users","Пользователи"},{"reviews","Отзывы"},{"decor","Украшения"},{"log","Журнал"}};
  for(String[] t:all){Button chip=Ui.chip(this,t[1]);chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);Ui.chipState(chip,tab.equals(t[0]));chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);chip.setOnClickListener(v->{tab=t[0];renderTabs();open();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=Ui.dp(this,8);tabs.addView(chip,lp);}
 }
 void open(){content.removeAllViews();switch(tab){case "users":users("");break;case "reviews":reviews();break;case "decor":decorations();break;case "log":log();break;default:stats();}}

 LinearLayout card(View... children){LinearLayout c=Ui.padded(this,16);c.setBackground(Ui.outlined(this,Ui.PANEL,20));for(int i=0;i<children.length;i++)Ui.add(c,children[i],i==0?0:8);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(this,10);c.setLayoutParams(lp);return c;}
 View tile(String value,String caption,int color){LinearLayout b=Ui.column(this);b.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12));b.setBackground(Ui.outlined(this,Ui.PANEL,16));TextView v=Ui.title(this,value,20);v.setTextColor(color);b.addView(v);Ui.add(b,Ui.text(this,caption,12,Ui.MUTED),4);b.setContentDescription(value+" "+caption);return b;}

 void stats(){background(()->(JSONObject)account.rpc("admin_stats",new JSONObject()),s->{
  String[][] items={{"users","Пользователей"},{"new_today","Новых за сутки"},{"episodes_today","Серий за сутки"},{"episodes_total","Серий всего"},{"reviews","Отзывов"},{"friendships","Дружб"},{"banned","Заблокировано"},{"points_in_circulation","Очков на руках"}};
  for(int i=0;i<items.length;i+=2){LinearLayout row=Ui.row(this);row.addView(tile(Rewards.points(s.optInt(items[i][0])),items[i][1],i==0?Ui.ACCENT:Ui.TEXT),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(this,10);row.addView(tile(Rewards.points(s.optInt(items[i+1][0])),items[i+1][1],Ui.TEXT),lp);Ui.add(content,row,i==0?0:10);}
  JSONArray top=s.optJSONArray("top");if(top!=null&&top.length()>0){LinearLayout c=card(Ui.title(this,"Топ по очкам",16));for(int i=0;i<top.length();i++){JSONObject u=top.optJSONObject(i);Ui.add(c,Ui.text(this,(i+1)+". "+Decor.name(u)+" · @"+u.optString("username")+" — "+Rewards.points(u.optInt("total_earned"))+" очков, "+u.optInt("episodes_watched")+" серий",14,Ui.TEXT),6);}Ui.add(content,c,14);}
 });}

 void users(String query){
  LinearLayout searchRow=Ui.row(this);searchRow.setBackground(Ui.outlined(this,Ui.RAISED,18));searchRow.setPadding(Ui.dp(this,14),0,Ui.dp(this,4),0);
  EditText field=new EditText(this);field.setHint("Ник, имя или почта");field.setHintTextColor(Ui.MUTED);field.setTextColor(Ui.TEXT);field.setSingleLine(true);field.setBackgroundColor(android.graphics.Color.TRANSPARENT);field.setText(query);field.setMinHeight(Ui.dp(this,48));field.setContentDescription("Поиск пользователей");
  searchRow.addView(field,new LinearLayout.LayoutParams(0,-2,1));Button go=Ui.iconButton(this,"search","Найти");searchRow.addView(go);content.addView(searchRow);
  LinearLayout list=Ui.column(this);Ui.add(content,list,10);
  Runnable run=()->background(()->{if(account.cachedDecorations().length()==0)account.decorations();return (JSONArray)account.rpc("admin_users",new JSONObject().put("p_query",field.getText().toString().trim()).put("p_limit",100));},rows->{
   decor=Decor.index(account.cachedDecorations());list.removeAllViews();if(rows.length()==0)list.addView(Ui.text(this,"Никого не нашлось.",14,Ui.MUTED));
   for(int i=0;i<rows.length();i++){JSONObject u=rows.optJSONObject(i);if(u==null)continue;
    String sub=u.optString("email")+" · ◆ "+Rewards.points(u.optInt("points"))+" · ур. "+Rewards.level(u.optInt("total_earned"))+(u.optBoolean("is_admin")?" · админ":"")+(u.optBoolean("banned")?" · ЗАБЛОКИРОВАН":"");
    LinearLayout row=Social.person(this,u,decor,sub,40);row.setBackground(Ui.ripple(this,u.optBoolean("banned")?0x33FF4D67:android.graphics.Color.TRANSPARENT,14));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->userActions(u,()->users(field.getText().toString())));list.addView(row);}
  });
  go.setOnClickListener(v->run.run());field.setOnEditorActionListener((v,a,e)->{run.run();return true;});run.run();
 }
 void userActions(JSONObject u,Runnable reload){
  String id=u.optString("id"),name="@"+u.optString("username");
  List<String> actions=new ArrayList<>(Arrays.asList("Открыть профиль","Начислить или списать очки","Выдать украшение",u.optBoolean("banned")?"Разблокировать":"Заблокировать","Сбросить оформление"));
  Ui.choose(this,name,actions,"",choice->{
   switch(choice){
    case "Открыть профиль":Social.openProfile(this,u.optString("username"));break;
    case "Начислить или списать очки":prompt("Очки для "+name,"Например 100 или -50",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED,"Причина (необязательно)",(value,reason)->{int delta;try{delta=Integer.parseInt(value.trim());}catch(NumberFormatException e){Toast.makeText(this,"Нужно целое число",Toast.LENGTH_SHORT).show();return;}
      background(()->account.rpc("admin_adjust_points",new JSONObject().put("p_user",id).put("p_delta",delta).put("p_reason",reason)),r->{Toast.makeText(this,"Готово",Toast.LENGTH_SHORT).show();reload.run();});});break;
    case "Выдать украшение":{List<String> labels=new ArrayList<>();List<String> ids=new ArrayList<>();JSONArray all=account.cachedDecorations();for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(d==null)continue;labels.add(d.optString("title")+" · "+kind(d.optString("kind")));ids.add(d.optString("id"));}
      Ui.choose(this,"Украшение для "+name,labels,"",c->{String deco=ids.get(labels.indexOf(c));background(()->account.rpc("admin_grant_decoration",new JSONObject().put("p_user",id).put("p_decoration",deco)),r->Toast.makeText(this,"Выдано",Toast.LENGTH_SHORT).show());});break;}
    case "Заблокировать":prompt("Блокировка "+name,"Причина",InputType.TYPE_CLASS_TEXT,null,(reason,ignored)->background(()->account.rpc("admin_set_banned",new JSONObject().put("p_user",id).put("p_banned",true).put("p_reason",reason)),r->reload.run()));break;
    case "Разблокировать":background(()->account.rpc("admin_set_banned",new JSONObject().put("p_user",id).put("p_banned",false)),r->reload.run());break;
    case "Сбросить оформление":{List<String> fields=Arrays.asList("Аватар","Баннер","Обо мне","Статус","Имя","Витрина","Всё сразу");String[] keys={"avatar","banner","bio","status","display_name","showcase"};
      Ui.choose(this,"Что сбросить у "+name,fields,"",c->{JSONArray chosen=new JSONArray();int i=fields.indexOf(c);if(i==6)for(String k:keys)chosen.put(k);else chosen.put(keys[i]);
       background(()->account.rpc("admin_reset_profile",new JSONObject().put("p_user",id).put("p_fields",chosen)),r->{Toast.makeText(this,"Сброшено",Toast.LENGTH_SHORT).show();reload.run();});});break;}
    default:break;
   }
  });
 }
 interface Prompted{void accept(String value,String extra);}
 void prompt(String title,String hint,int type,String extraHint,Prompted done){
  LinearLayout panel=Ui.column(this);EditText value=field(hint,type);Ui.add(panel,value,4);EditText extra=extraHint==null?null:field(extraHint,InputType.TYPE_CLASS_TEXT);if(extra!=null)Ui.add(panel,extra,10);
  Button ok=Ui.button(this,"Применить",true);Ui.add(panel,ok,16);Dialog d=Ui.sheet(this,title,panel,"Закрыть");
  ok.setOnClickListener(v->{d.dismiss();done.accept(value.getText().toString(),extra==null?"":extra.getText().toString().trim());});
 }
 EditText field(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED);e.setTextColor(Ui.TEXT);e.setInputType(type);e.setSingleLine(true);e.setMinHeight(Ui.dp(this,52));e.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);e.setBackground(Ui.outlined(this,Ui.RAISED,14));e.setContentDescription(hint);return e;}
 static final String[][] KINDS={{"frame","обводка аватара"},{"badge","значок"},{"name_color","цвет ника"},{"avatar_deco","украшение аватара"},{"profile_effect","эффект профиля"},{"profile_frame","рамка профиля"}};
 static String kind(String k){for(String[] x:KINDS)if(x[0].equals(k))return x[1];return k;}
 static List<String> kindLabels(){List<String> r=new ArrayList<>();for(String[] x:KINDS)r.add(x[1]);return r;}
 static String kindId(String label){for(String[] x:KINDS)if(x[1].equals(label))return x[0];return "frame";}
 static final String STYLE_HELP="Рисунок кодом (необязательно): cat_ears, bunny, halo, horns, headphones, crown, orbit, sakura_wreath, pumpkin — для аватара; sparkles, bubbles, petals, fireflies, hearts, starfall, bats, snow — эффекты; vines, neon, constellation, flames, crystal, moon, cobweb — рамки профиля";
 EditText pendingImage;static final int PICK_IMAGE=51;
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null||pendingImage==null)return;EditText target=pendingImage;android.net.Uri uri=data.getData();
  background(()->{String mime=getContentResolver().getType(uri);if(mime==null)mime="image/png";byte[] bytes=readAll(uri);if(bytes.length>8*1024*1024)throw new java.io.IOException("Файл больше 8 МБ");String ext=mime.contains("gif")?"gif":mime.contains("webp")?"webp":mime.contains("jpeg")?"jpg":"png";return account.upload("decor-"+System.currentTimeMillis()+"."+ext,bytes,mime);},url->{target.setText(url);Toast.makeText(this,"Картинка загружена",Toast.LENGTH_SHORT).show();});}
 byte[] readAll(android.net.Uri uri) throws java.io.IOException{try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("Файл недоступен");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>9*1024*1024)break;}return out.toByteArray();}}

 void reviews(){background(()->(JSONArray)account.rpc("admin_recent_reviews",new JSONObject().put("p_limit",100)),rows->{
  if(rows.length()==0)content.addView(Ui.text(this,"Отзывов пока нет.",14,Ui.MUTED));
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;
   TextView head=Ui.title(this,"@"+r.optString("username")+" · ★ "+r.optInt("score")+" · "+r.optString("title"),14);head.setMaxLines(2);head.setEllipsize(TextUtils.TruncateAt.END);
   TextView body=Ui.text(this,r.optString("body").isEmpty()?"(без текста)":r.optString("body"),14,0xFFCFCDDC);
   Button delete=Ui.textButton(this,"Удалить отзыв");delete.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
   LinearLayout c=card(head,body,delete);content.addView(c);
   delete.setOnClickListener(v->background(()->{account.rpc("admin_delete_review",new JSONObject().put("p_user",r.optString("user_id")).put("p_anime_id",r.optLong("anime_id")));return true;},ok->{content.removeView(c);Toast.makeText(this,"Удалено",Toast.LENGTH_SHORT).show();}));}
 });}

 void decorations(){
  Button create=Ui.button(this,"Новое украшение",true);content.addView(create);create.setOnClickListener(v->editDecoration(null));
  background(()->account.decorations(),all->{decor=Decor.index(all);
   for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(d==null)continue;
    String sub=kind(d.optString("kind"))+" · "+(d.optBoolean("exclusive")?"за достижение":"◆ "+d.optInt("price"))+" · ур. "+d.optInt("min_level",1)+(d.isNull("available_until")?"":" · до "+Rewards.until(d.optString("available_until")));
    TextView t=Ui.title(this,d.optString("title")+"  ("+d.optString("id")+")",14);LinearLayout c=card(t,Ui.text(this,sub,12,Ui.MUTED));c.setClickable(true);c.setFocusable(true);c.setBackground(Ui.ripple(this,Ui.PANEL,20));c.setOnClickListener(v->editDecoration(d));Ui.add(content,c,10);}
  });
 }
 /** Простая форма украшения: id, тип, название, описание, цена, уровень, редкость, цвета или глиф, окно продажи. */
 void editDecoration(JSONObject d){
  JSONObject src=d==null?new JSONObject():d;JSONObject style=src.optJSONObject("style");if(style==null)style=new JSONObject();
  LinearLayout panel=Ui.column(this);
  EditText id=field("id: латиница, например frame_spring",InputType.TYPE_CLASS_TEXT);id.setText(src.optString("id"));id.setEnabled(d==null);
  String[] k={src.optString("kind","frame")};Button kindField=Ui.field(this,"Тип");Ui.fieldValue(kindField,"Тип",kind(k[0]));kindField.setOnClickListener(v->Ui.choose(this,"Тип",kindLabels(),kind(k[0]),c->{k[0]=kindId(c);Ui.fieldValue(kindField,"Тип",c);}));
  EditText title=field("Название",InputType.TYPE_CLASS_TEXT);title.setText(src.optString("title"));
  EditText desc=field("Описание",InputType.TYPE_CLASS_TEXT);desc.setText(src.optString("description"));
  EditText price=field("Цена в очках",InputType.TYPE_CLASS_NUMBER);price.setText(String.valueOf(src.optInt("price",100)));
  EditText level=field("Минимальный уровень",InputType.TYPE_CLASS_NUMBER);level.setText(String.valueOf(src.optInt("min_level",1)));
  String[] rarity={src.optString("rarity","common")};Button rarityField=Ui.field(this,"Редкость");Ui.fieldValue(rarityField,"Редкость",Rewards.rarity(rarity[0]));rarityField.setOnClickListener(v->Ui.choose(this,"Редкость",Arrays.asList("Обычное","Редкое","Эпическое","Легендарное"),Rewards.rarity(rarity[0]),c->{rarity[0]=c.equals("Редкое")?"rare":c.equals("Эпическое")?"epic":c.equals("Легендарное")?"legendary":"common";Ui.fieldValue(rarityField,"Редкость",c);}));
  JSONArray colors=style.optJSONArray("colors");StringBuilder cs=new StringBuilder();if(colors!=null)for(int i=0;i<colors.length();i++){if(i>0)cs.append(' ');cs.append(colors.optString(i));}
  EditText colorField=field("Цвета через пробел: #FF4D67 #FF8A3D",InputType.TYPE_CLASS_TEXT);colorField.setText(cs.toString());
  EditText glyph=field("Глиф значка, например ★",InputType.TYPE_CLASS_TEXT);glyph.setText(style.optString("glyph"));
  EditText until=field("Продаётся до (ГГГГ-ММ-ДД), пусто — всегда",InputType.TYPE_CLASS_TEXT);until.setText(src.isNull("available_until")?"":src.optString("available_until").length()>=10?src.optString("available_until").substring(0,10):"");
  EditText styleType=field("Рисунок кодом: cat_ears, sparkles, neon…",InputType.TYPE_CLASS_TEXT);styleType.setText(style.optString("type"));
  EditText image=field("Своя картинка (https://…, PNG/GIF/WebP)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);image.setText(style.optString("image_url"));
  Button pick=Ui.tonal(this,"Загрузить картинку…");pick.setOnClickListener(v->{pendingImage=image;startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/gif","image/webp","image/jpeg"}),PICK_IMAGE);});
  TextView styleHelp=Ui.text(this,STYLE_HELP,11,Ui.MUTED);
  boolean[] flags={style.optBoolean("animated"),style.optBoolean("glow"),src.optBoolean("exclusive"),src.optBoolean("active",true)};
  String[] flagNames={"Вращение рамки","Свечение","Только за достижение","Активно"};
  for(View v:new View[]{id,kindField,title,desc,price,level,rarityField,colorField,glyph,styleType,styleHelp,image,pick,until})Ui.add(panel,v,8);
  for(int i=0;i<flags.length;i++){int index=i;CheckBox box=new CheckBox(this);box.setText(flagNames[i]);box.setTextColor(Ui.TEXT);box.setChecked(flags[i]);box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));box.setOnCheckedChangeListener((b,c)->flags[index]=c);Ui.add(panel,box,4);}
  Button save=Ui.button(this,"Сохранить",true);Ui.add(panel,save,16);Dialog dialog=Ui.sheet(this,d==null?"Новое украшение":"Украшение",panel,"Закрыть");
  save.setOnClickListener(v->{try{
    JSONObject st=new JSONObject();String[] parts=colorField.getText().toString().trim().split("\\s+");JSONArray arr=new JSONArray();for(String part:parts)if(part.matches("#[0-9A-Fa-f]{6}"))arr.put(part);
    if(arr.length()>0)st.put("colors",arr);if(!glyph.getText().toString().trim().isEmpty()){st.put("glyph",glyph.getText().toString().trim());st.put("color",arr.length()>0?arr.getString(0):"#FF4D67");}if(flags[0])st.put("animated",true);if(flags[1])st.put("glow",true);String typeName=styleType.getText().toString().trim();if(typeName.matches("[a-z_]{2,30}"))st.put("type",typeName);String img=image.getText().toString().trim();if(img.startsWith("https://"))st.put("image_url",img);
    String day=until.getText().toString().trim();
    JSONObject payload=new JSONObject().put("id",id.getText().toString().trim().toLowerCase(Locale.ROOT)).put("kind",k[0]).put("title",title.getText().toString().trim()).put("description",desc.getText().toString().trim())
     .put("price",Integer.parseInt(price.getText().toString().trim().isEmpty()?"0":price.getText().toString().trim())).put("min_level",Math.max(1,Integer.parseInt(level.getText().toString().trim().isEmpty()?"1":level.getText().toString().trim())))
     .put("rarity",rarity[0]).put("style",st).put("exclusive",flags[2]).put("active",flags[3]).put("sort",src.optInt("sort",500)).put("available_from",src.isNull("available_from")?"":src.optString("available_from")).put("available_until",day.matches("\\d{4}-\\d{2}-\\d{2}")?day+"T23:59:59+03:00":"");
    if(!payload.getString("id").matches("[a-z0-9_]{2,40}")||payload.getString("title").isEmpty()){Toast.makeText(this,"Заполни id (латиница, цифры, _) и название",Toast.LENGTH_LONG).show();return;}
    dialog.dismiss();background(()->account.rpc("admin_upsert_decoration",new JSONObject().put("p_decoration",payload)),r->{Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show();open();});
   }catch(Exception e){Toast.makeText(this,"Проверь числа и даты",Toast.LENGTH_LONG).show();}});
 }

 void log(){background(()->(JSONArray)account.rpc("admin_log_list",new JSONObject().put("p_limit",200)),rows->{
  if(rows.length()==0)content.addView(Ui.text(this,"Действий пока не было.",14,Ui.MUTED));
  for(int i=0;i<rows.length();i++){JSONObject l=rows.optJSONObject(i);if(l==null)continue;String when=l.optString("created_at");when=when.length()>=16?when.substring(0,16).replace('T',' '):when;
   Ui.add(content,card(Ui.title(this,action(l.optString("action"))+(l.optString("target").isEmpty()||l.isNull("target")?"":" · @"+l.optString("target")),14),Ui.text(this,when+" · @"+l.optString("admin")+" · "+l.optJSONObject("details"),12,Ui.MUTED)),i==0?0:8);}
 });}
 static String action(String a){switch(a){case "adjust_points":return "Очки";case "ban":return "Блокировка";case "unban":return "Разблокировка";case "reset_profile":return "Сброс оформления";case "grant_decoration":return "Выдано украшение";case "delete_review":return "Удалён отзыв";case "upsert_decoration":return "Украшение изменено";default:return a;}}
}
