package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Общие элементы соцфункций: достижения, строка пользователя, списки, оценки и отзывы в карточке аниме. */
final class Social {
 /** Показывает только что открытые достижения из ответа сервера. */
 static void announce(Activity activity,JSONObject result){
  JSONArray list=result==null?null:result.optJSONArray("achievements");if(list==null)return;
  for(int i=0;i<list.length();i++){JSONObject a=list.optJSONObject(i);if(a==null)continue;
   String text="🏆 Достижение «"+a.optString("title")+"»"+(a.optInt("points")>0?" · +"+a.optInt("points")+" очков":"")+(a.optString("reward").isEmpty()||a.isNull("reward")?"":" и украшение");
   Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
 }

 /** Строка пользователя: аватар с рамкой, ник в его цвете, значок и подпись. */
 static LinearLayout person(Activity activity,JSONObject p,Map<String,JSONObject> decor,String subtitle,int avatarDp){
  LinearLayout row=Ui.row(activity);row.setPadding(Ui.dp(activity,4),Ui.dp(activity,8),Ui.dp(activity,4),Ui.dp(activity,8));
  Decor.AvatarView avatar=new Decor.AvatarView(activity);avatar.frame(Decor.style(decor,p.optString("equipped_frame","frame_moon")));avatar.person(Decor.name(p),p.optString("avatar_url"));row.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(activity,avatarDp),Ui.dp(activity,avatarDp)));
  LinearLayout text=Ui.column(activity);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=Ui.dp(activity,12);row.addView(text,tp);
  LinearLayout nameRow=Ui.row(activity);TextView name=Ui.title(activity,Decor.name(p),15);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);Decor.nameColor(name,Decor.style(decor,p.optString("equipped_color","color_default")));nameRow.addView(name,new LinearLayout.LayoutParams(-2,-2));
  CharSequence badge=Decor.badge(Decor.style(decor,p.optString("equipped_badge")));if(badge.length()>0){TextView g=Ui.text(activity,"",15,Ui.TEXT);g.setText(badge);g.setPadding(Ui.dp(activity,6),0,0,0);nameRow.addView(g);}
  text.addView(nameRow);
  if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=Ui.text(activity,subtitle,12,Ui.MUTED);sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);Ui.add(text,sub,3);}
  row.setContentDescription(Decor.name(p)+(subtitle==null?"":". "+subtitle));
  return row;
 }
 static void openProfile(Activity activity,String username){activity.startActivity(new Intent(activity,ProfileActivity.class).putExtra("username",username));}

 // ───── Карточка «Мои списки и отзывы» на экране аниме ─────
 static final class AnimeCard {
  final Activity activity;final Account account;Anime anime;
  final LinearLayout root;final Button listButton,rateButton;final TextView ratingText;final LinearLayout reviewsBox;
  String listStatus="";JSONObject myReview;JSONArray reviews=new JSONArray();int generation;

  AnimeCard(Activity activity,Anime anime){
   this.activity=activity;this.anime=anime;account=new Account(activity);
   root=Ui.padded(activity,16);root.setBackground(Ui.outlined(activity,Ui.PANEL,22));
   TextView title=Ui.title(activity,"Мой список и оценки",17);if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);root.addView(title);
   LinearLayout actions=Ui.row(activity);listButton=Ui.tonal(activity,"В список");rateButton=Ui.tonal(activity,"Оценить");
   actions.addView(listButton,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=Ui.dp(activity,8);actions.addView(rateButton,rp);Ui.add(root,actions,12);
   ratingText=Ui.text(activity,"Оценки пользователей Yoru загружаются…",13,Ui.MUTED);Ui.add(root,ratingText,12);
   reviewsBox=Ui.column(activity);Ui.add(root,reviewsBox,4);
   listButton.setOnClickListener(v->chooseList());rateButton.setOnClickListener(v->reviewSheet());
   refresh();
  }
  boolean needAccount(){if(account.signedIn())return false;Toast.makeText(activity,Account.configured()?"Войди в профиль, чтобы вести списки и писать отзывы":"Аккаунты в этой сборке не подключены",Toast.LENGTH_LONG).show();if(Account.configured())activity.startActivity(new Intent(activity,ProfileActivity.class));return true;}
  void refresh(){
   if(!Account.configured()){root.setVisibility(View.GONE);return;}
   int session=++generation;
   Api.IO.execute(()->{try{
     JSONObject rating=account.rating(anime.id);JSONArray all=account.reviews(anime.id);JSONObject entry=account.signedIn()?account.listEntry(anime.id):new JSONObject();
     if(account.cachedDecorations().length()==0)account.decorations();
     activity.runOnUiThread(()->{if(activity.isDestroyed()||session!=generation)return;listStatus=entry.optString("status");reviews=all;myReview=null;
      for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&r.optString("user_id").equals(account.userId()))myReview=r;}
      render(rating,entry);});
    }catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed()&&session==generation)ratingText.setText("Оценки сейчас недоступны.");});}});
  }
  void render(JSONObject rating,JSONObject entry){
   String label=listStatus.isEmpty()?"В список":Account.listLabel(listStatus)+(entry.optInt("episodes")>0?" · "+entry.optInt("episodes")+(entry.optInt("total")>0?"/"+entry.optInt("total"):""):"");
   listButton.setText(label);listButton.setContentDescription("Список: "+label);
   rateButton.setText(myReview==null?"Оценить":"Моя оценка · "+myReview.optInt("score"));
   int count=rating.optInt("count");
   ratingText.setText(count==0?"Пока никто из Yoru не оценил — будь первым.":"Yoru: ★ "+rating.optString("average")+" · "+count+" "+plural(count,"оценка","оценки","оценок"));
   reviewsBox.removeAllViews();Map<String,JSONObject> decor=Decor.index(account.cachedDecorations());int shown=0;
   for(int i=0;i<reviews.length()&&shown<3;i++){JSONObject r=reviews.optJSONObject(i);if(r==null||r.optString("body").isEmpty())continue;reviewsBox.addView(reviewView(r,decor));shown++;}
   if(reviews.length()>shown&&reviews.length()>0){Button all=Ui.textButton(activity,"Все отзывы ("+reviews.length()+")");all.setOnClickListener(v->allReviews());reviewsBox.addView(all);}
  }
  View reviewView(JSONObject r,Map<String,JSONObject> decor){
   JSONObject p=r.optJSONObject("profiles");if(p==null)p=new JSONObject();String username=p.optString("username");
   LinearLayout box=Ui.column(activity);box.setPadding(0,Ui.dp(activity,6),0,Ui.dp(activity,6));
   LinearLayout head=person(activity,p,decor,"★ "+r.optInt("score")+" из 10",36);head.setBackground(Ui.ripple(activity,android.graphics.Color.TRANSPARENT,12));head.setClickable(true);head.setFocusable(true);head.setOnClickListener(v->openProfile(activity,username));box.addView(head);
   TextView body=Ui.text(activity,r.optString("body"),14,0xFFCFCDDC);body.setLineSpacing(Ui.dp(activity,3),1);Ui.add(box,body,2);
   return box;
  }
  void allReviews(){LinearLayout list=Ui.column(activity);Map<String,JSONObject> decor=Decor.index(account.cachedDecorations());for(int i=0;i<reviews.length();i++){JSONObject r=reviews.optJSONObject(i);if(r!=null)list.addView(reviewView(r,decor));}Ui.sheet(activity,"Отзывы",list,"Закрыть отзывы");}
  void chooseList(){
   if(needAccount())return;
   List<String> options=new ArrayList<>();for(String s:Account.LISTS)options.add(Account.listLabel(s));if(!listStatus.isEmpty())options.add("Убрать из списков");
   Ui.choose(activity,"Список",options,listStatus.isEmpty()?"":Account.listLabel(listStatus),choice->{String status="";for(String s:Account.LISTS)if(Account.listLabel(s).equals(choice))status=s;String target=status;
    listButton.setEnabled(false);Api.IO.execute(()->{try{JSONObject result=account.setList(anime,target);activity.runOnUiThread(()->{if(activity.isDestroyed())return;listButton.setEnabled(true);announce(activity,result);refresh();});}
     catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;listButton.setEnabled(true);Toast.makeText(activity,Rewards.error(e.getMessage()),Toast.LENGTH_LONG).show();});}});});
  }
  void reviewSheet(){
   if(needAccount())return;
   LinearLayout panel=Ui.column(activity);int[] score={myReview==null?0:myReview.optInt("score")};
   TextView hint=Ui.text(activity,"",13,Ui.MUTED);Ui.add(panel,hint,0);
   List<Button> buttons=new ArrayList<>();Runnable paint=()->{hint.setText(score[0]==0?"Выбери оценку от 1 до 10":"Оценка: "+score[0]+" из 10");for(int i=0;i<buttons.size();i++){boolean on=i+1==score[0];Button b=buttons.get(i);b.setBackground(Ui.ripple(activity,on?Ui.ACCENT:Ui.RAISED,12));b.setTextColor(on?Ui.ON_ACCENT:Ui.TEXT);b.setContentDescription("Оценка "+(i+1)+(on?", выбрано":""));}};
   for(int rowIndex=0;rowIndex<2;rowIndex++){LinearLayout row=Ui.row(activity);for(int j=1;j<=5;j++){int value=rowIndex*5+j;Button b=Ui.button(activity,String.valueOf(value),false);b.setPadding(0,0,0,0);b.setOnClickListener(v->{score[0]=value;paint.run();});buttons.add(b);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(j>1)lp.leftMargin=Ui.dp(activity,6);row.addView(b,lp);}Ui.add(panel,row,rowIndex==0?10:6);}
   EditText body=new EditText(activity);body.setHint("Пара слов об аниме — по желанию");body.setHintTextColor(Ui.MUTED);body.setTextColor(Ui.TEXT);body.setTextSize(15);body.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);body.setMinLines(3);body.setMaxLines(8);body.setGravity(Gravity.TOP|Gravity.START);body.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});body.setPadding(Ui.dp(activity,14),Ui.dp(activity,12),Ui.dp(activity,14),Ui.dp(activity,12));body.setBackground(Ui.outlined(activity,Ui.RAISED,14));body.setContentDescription("Текст отзыва");if(myReview!=null)body.setText(myReview.optString("body"));Ui.add(panel,body,14);
   Button save=Ui.button(activity,"Сохранить",true);Ui.add(panel,save,16);
   Button delete=Ui.textButton(activity,"Удалить мою оценку");delete.setVisibility(myReview==null?View.GONE:View.VISIBLE);Ui.add(panel,delete,4);
   Dialog dialog=Ui.sheet(activity,"Оценка и отзыв",panel,"Закрыть отзыв");paint.run();
   View.OnClickListener submit=v->{int value=v==delete?0:score[0];if(value==0&&v!=delete){hint.setText("Сначала выбери оценку");return;}dialog.dismiss();String text=body.getText().toString();
    Api.IO.execute(()->{try{JSONObject result=account.setReview(anime,value,text);activity.runOnUiThread(()->{if(activity.isDestroyed())return;announce(activity,result);refresh();});}
     catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed())Toast.makeText(activity,Rewards.error(e.getMessage()),Toast.LENGTH_LONG).show();});}});};
   save.setOnClickListener(submit);delete.setOnClickListener(submit);
  }
 }
 static String plural(int n,String one,String few,String many){int m=n%100,k=n%10;return m>=11&&m<=14?many:k==1?one:k>=2&&k<=4?few:many;}
}
