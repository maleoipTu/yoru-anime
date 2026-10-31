package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Друзья: поиск по нику, входящие и исходящие заявки, список друзей и лента их просмотров. */
public final class FriendsActivity extends Activity {
 Account account;LinearLayout content;ProgressBar busyBar;EditText search;LinearLayout results;
 JSONArray friends=new JSONArray(),feed=new JSONArray();Map<String,JSONObject> decor=new HashMap<>();int generation;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);account=new Account(this);
  LinearLayout root=Ui.root(this);
  LinearLayout toolbar=Ui.row(this);toolbar.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4));
  Button back=Ui.iconButton(this,"back","Назад");back.setOnClickListener(v->finish());toolbar.addView(back);
  TextView heading=Ui.title(this,"Друзья",20);if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,4);toolbar.addView(heading,hp);
  busyBar=new ProgressBar(this);busyBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));busyBar.setVisibility(View.INVISIBLE);toolbar.addView(busyBar,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)));
  root.addView(toolbar);
  ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout page=Ui.column(this);page.setPadding(Ui.dp(this,Ui.GUTTER),Ui.dp(this,8),Ui.dp(this,Ui.GUTTER),Ui.dp(this,48));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(!account.signedIn()){page.addView(Ui.text(this,"Войди в профиль, чтобы добавлять друзей.",14,Ui.MUTED));return;}
  // Поиск по нику.
  LinearLayout searchRow=Ui.row(this);searchRow.setPadding(Ui.dp(this,16),0,Ui.dp(this,4),0);searchRow.setBackground(Ui.outlined(this,Ui.PANEL,24));
  search=new EditText(this);search.setHint("Найти по нику");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);search.setTextSize(15);search.setSingleLine(true);search.setBackgroundColor(android.graphics.Color.TRANSPARENT);search.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setMinHeight(Ui.dp(this,52));search.setContentDescription("Поиск пользователей по нику");
  searchRow.addView(search,new LinearLayout.LayoutParams(0,-2,1));Button go=Ui.iconButton(this,"search","Найти");go.setOnClickListener(v->find());searchRow.addView(go);
  search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){find();return true;}return false;});
  page.addView(searchRow);results=Ui.column(this);Ui.add(page,results,4);
  content=Ui.column(this);Ui.add(page,content,8);
  load();
 }
 boolean resumed;
 @Override protected void onResume(){super.onResume();if(resumed&&content!=null)load();resumed=true;}
 @Override protected void onDestroy(){generation++;super.onDestroy();}

 interface Job<T>{T run() throws Exception;}
 interface Done<T>{void accept(T value);}
 <T> void background(Job<T> job,Done<T> done){
  int session=++generation;busyBar.setVisibility(View.VISIBLE);
  Api.IO.execute(()->{try{T value=job.run();runOnUiThread(()->{if(isDestroyed()||session!=generation)return;busyBar.setVisibility(View.INVISIBLE);done.accept(value);});}
   catch(Exception e){runOnUiThread(()->{if(isDestroyed()||session!=generation)return;busyBar.setVisibility(View.INVISIBLE);Toast.makeText(this,Rewards.error(e.getMessage()),Toast.LENGTH_LONG).show();});}});
 }
 void load(){background(()->{if(account.cachedDecorations().length()==0)account.decorations();return new JSONArray[]{account.friends(),account.feed()};},data->{friends=data[0];feed=data[1];decor=Decor.index(account.cachedDecorations());render();});}

 void find(){String q=search.getText().toString().trim();if(q.isEmpty()){results.removeAllViews();return;}
  android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);
  background(()->account.search(q),found->{results.removeAllViews();
   if(found.length()==0){Ui.add(results,Ui.text(this,"Никого не нашлось. Ник пишется латиницей.",13,Ui.MUTED),8);return;}
   for(int i=0;i<found.length();i++){JSONObject p=found.optJSONObject(i);if(p==null||p.optString("id").equals(account.userId()))continue;results.addView(personCard(p,"Уровень "+Rewards.level(p.optInt("total_earned")),"Добавить",()->request(p.optString("username"))));}});}
 void request(String username){background(()->(JSONObject)account.rpc("send_friend_request",new JSONObject().put("p_username",username)),r->{Social.announce(this,r);Toast.makeText(this,r.optString("state").equals("accepted")?"Теперь вы друзья":"Заявка отправлена",Toast.LENGTH_SHORT).show();results.removeAllViews();search.setText("");load();});}

 void render(){
  content.removeAllViews();
  List<JSONObject> incoming=new ArrayList<>(),outgoing=new ArrayList<>(),mutual=new ArrayList<>();
  for(int i=0;i<friends.length();i++){JSONObject f=friends.optJSONObject(i);if(f==null)continue;switch(f.optString("state")){case "incoming":incoming.add(f);break;case "outgoing":outgoing.add(f);break;default:mutual.add(f);}}
  if(!incoming.isEmpty()){Ui.add(content,section("Заявки в друзья · "+incoming.size()),18);
   for(JSONObject f:incoming){View card=personCard(f,"Хочет дружить","Принять",()->background(()->(JSONObject)account.rpc("respond_friend_request",new JSONObject().put("p_requester",f.optString("id")).put("p_accept",true)),r->{Social.announce(this,r);load();}));
    Button decline=Ui.textButton(this,"Отклонить");decline.setOnClickListener(v->background(()->(JSONObject)account.rpc("respond_friend_request",new JSONObject().put("p_requester",f.optString("id")).put("p_accept",false)),r->load()));((LinearLayout)card).addView(decline);content.addView(card);}}
  Ui.add(content,section("Друзья · "+mutual.size()),18);
  if(mutual.isEmpty())Ui.add(content,Ui.text(this,"Пока никого. Найди друзей по нику — когда они примут заявку, здесь появится их лента.",13,Ui.MUTED),6);
  for(JSONObject f:mutual){String sub=(f.optString("status").isEmpty()?"Уровень "+f.optInt("level"):"«"+f.optString("status")+"»")+(f.optInt("streak_days")>1?" · 🔥 "+f.optInt("streak_days"):"");content.addView(personCard(f,sub,null,null));}
  if(!outgoing.isEmpty()){Ui.add(content,section("Ждут ответа"),18);for(JSONObject f:outgoing)content.addView(personCard(f,"Заявка отправлена",null,null));}
  if(feed.length()>0){Ui.add(content,section("Что смотрят друзья"),22);
   for(int i=0;i<feed.length();i++){JSONObject item=feed.optJSONObject(i);if(item!=null)content.addView(feedItem(item));}}
 }
 TextView section(String text){TextView t=Ui.title(this,text,17);if(android.os.Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);return t;}

 View personCard(JSONObject p,String subtitle,String action,Runnable onAction){
  LinearLayout card=Ui.column(this);LinearLayout row=Social.person(this,p,decor,subtitle,44);
  row.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,14));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->Social.openProfile(this,p.optString("username")));
  if(action!=null){Button b=Ui.tonal(this,action);b.setOnClickListener(v->onAction.run());row.addView(b,new LinearLayout.LayoutParams(-2,-2));}
  card.addView(row);return card;
 }
 View feedItem(JSONObject item){
  LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));row.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,12));
  ImageView poster=new ImageView(this);Ui.round(poster,10);Ui.poster(this,poster,item.optString("poster"));row.addView(poster,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,74)));
  LinearLayout text=Ui.column(this);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=Ui.dp(this,12);row.addView(text,tp);
  TextView who=Ui.title(this,Decor.name(item),14);Decor.nameColor(who,Decor.style(decor,item.optString("equipped_color","color_default")));text.addView(who);
  TextView what=Ui.text(this,item.optString("title"),14,Ui.TEXT);what.setMaxLines(2);Ui.add(text,what,4);
  String progress=Account.listLabel(item.optString("status"))+(item.optInt("episodes")>0?" · серия "+item.optInt("episodes")+(item.optInt("total")>0?" из "+item.optInt("total"):""):"");
  Ui.add(text,Ui.text(this,progress,12,Ui.ACCENT),4);
  row.setClickable(true);row.setFocusable(true);row.setContentDescription(Decor.name(item)+": "+item.optString("title")+", "+progress);
  row.setOnClickListener(v->{try{JSONObject raw=new JSONObject().put("anime_id",item.optLong("anime_id")).put("title",item.optString("title")).put("poster",new JSONObject().put("fullsize",item.optString("poster")));startActivity(new Intent(this,DetailActivity.class).putExtra("anime",raw.toString()));}catch(JSONException ignored){}});
  return row;
 }
}
