package app.yoru.anime;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** «Год в Yoru»: сводка за год историями, как Spotify Wrapped. Нажатие справа — дальше, слева — назад. */
public final class WrappedActivity extends Activity {
 static final String[] MONTHS={"январь","февраль","март","апрель","май","июнь","июль","август","сентябрь","октябрь","ноябрь","декабрь"};
 static final int[][] SKY={{0xFF2B0F3A,0xFFFF4D67},{0xFF0F1E3A,0xFF6FC3FF},{0xFF3A140F,0xFFFF8A3D},{0xFF0F3A27,0xFF4ADE80},{0xFF221040,0xFFB388FF},{0xFF10102A,0xFF6F7BFF},{0xFF3A0F2A,0xFFFF8FB8},{0xFF1B1B22,0xFFFFC14D}};
 FrameLayout stage;LinearLayout bars;JSONObject data;List<Runnable> slides=new ArrayList<>();int index;Account account;
 final Handler handler=new Handler(Looper.getMainLooper());ValueAnimator timer;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);account=new Account(this);getWindow().setStatusBarColor(0xFF000000);
  stage=new FrameLayout(this);stage.setBackgroundColor(Ui.BG);setContentView(stage);
  bars=Ui.row(this);bars.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10),0);
  ProgressBar wait=new ProgressBar(this);stage.addView(wait,new FrameLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40),Gravity.CENTER));
  int year=Calendar.getInstance().get(Calendar.YEAR);
  Extras.async(this,()->(JSONObject)account.rpc("year_summary",new JSONObject().put("p_year",year)),result->{stage.removeView(wait);data=result;build();});
  stage.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN&&data!=null){show(e.getX()<stage.getWidth()/3f?index-1:index+1);}return true;});
 }
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(timer!=null)timer.cancel();super.onDestroy();}

 void build(){
  int episodes=data.optInt("episodes");
  slides.add(()->intro());
  if(episodes==0){slides.add(()->page(1,"Пока тишина","В этом году ты ещё не досмотрел ни одной серии в плеере Yoru. Включи что-нибудь — и сводка оживёт.",null));}
  else{
   slides.add(()->{LinearLayout p=page(1,"Ты провёл с аниме",null,null);TextView big=Ui.title(this,data.optString("hours")+" ч",72);big.setTextColor(Color.WHITE);Ui.add(p,big,6);
    double days=data.optDouble("hours")/24;Ui.add(p,line(episodes+" "+Social.plural(episodes,"серия","серии","серий")+" · "+data.optInt("anime")+" аниме"+(days>=1?String.format(Locale.ROOT," · это %.1f суток подряд",days):"")),16);
    Ui.add(p,line("И заработал ◆ "+Rewards.points(data.optInt("points"))+" очков"),8);});
   JSONArray top=data.optJSONArray("top_anime");
   if(top!=null&&top.length()>0)slides.add(()->{JSONObject first=top.optJSONObject(0);LinearLayout p=page(2,"Аниме года",null,null);ImageView poster=new ImageView(this);Ui.round(poster,20);Ui.lift(poster,16);Ui.poster(this,poster,first.optString("poster"));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(Ui.dp(this,150),Ui.dp(this,212));pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=Ui.dp(this,14);p.addView(poster,pp);
    TextView name=Ui.title(this,first.optString("title"),24);name.setTextColor(Color.WHITE);name.setGravity(Gravity.CENTER);Ui.add(p,name,14);Ui.add(p,center(line(first.optInt("episodes")+" "+Social.plural(first.optInt("episodes"),"серия","серии","серий")+" · "+first.optString("hours")+" ч")),6);
    for(int i=1;i<top.length();i++){JSONObject t=top.optJSONObject(i);Ui.add(p,line("#"+(i+1)+"  "+t.optString("title")+" — "+t.optInt("episodes")),i==1?18:6);}
    pop(poster);});
   JSONArray genres=data.optJSONArray("genres");
   if(genres!=null&&genres.length()>0)slides.add(()->{LinearLayout p=page(3,"Твои жанры","Больше всего ты смотрел «"+genres.optJSONObject(0).optString("genre")+"»",null);int max=Math.max(1,genres.optJSONObject(0).optInt("episodes"));
    for(int i=0;i<genres.length();i++){int order=i;JSONObject g=genres.optJSONObject(i);TextView label=line((i+1)+". "+g.optString("genre"));Ui.add(p,label,i==0?18:10);View bar=new View(this);bar.setBackground(Ui.shape(this,i==0?Color.WHITE:0x99FFFFFF,8));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,14));bp.topMargin=Ui.dp(this,6);p.addView(bar,bp);float share=g.optInt("episodes")/(float)max;bar.post(()->{ValueAnimator grow=ValueAnimator.ofFloat(0,share);grow.setDuration(900);grow.setStartDelay(150L*order);grow.addUpdateListener(a->{bp.width=Math.round(((View)bar.getParent()).getWidth()*(float)a.getAnimatedValue());bar.setLayoutParams(bp);});grow.start();});}});
   JSONArray months=data.optJSONArray("months");
   if(months!=null)slides.add(()->{int best=data.optInt("top_month",1);LinearLayout p=page(4,"Самый насыщенный месяц",MONTHS[Math.max(0,Math.min(11,best-1))].toUpperCase(Locale.forLanguageTag("ru")),null);LinearLayout chart=Ui.row(this);chart.setGravity(Gravity.BOTTOM);int max=1;for(int i=0;i<12;i++)max=Math.max(max,months.optInt(i));
    for(int i=0;i<12;i++){LinearLayout col=Ui.column(this);col.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);View b=new View(this);b.setBackground(Ui.shape(this,i==best-1?Color.WHITE:0x66FFFFFF,4));int h=Math.max(Ui.dp(this,3),Math.round(Ui.dp(this,150)*months.optInt(i)/(float)max));col.addView(b,new LinearLayout.LayoutParams(Ui.dp(this,14),h));b.setPivotY(h);b.setScaleY(0);b.animate().scaleY(1).setStartDelay(60L*i).setDuration(500);TextView m=Ui.text(this,MONTHS[i].substring(0,1).toUpperCase(Locale.forLanguageTag("ru")),11,0xCCFFFFFF);Ui.add(col,m,4);chart.addView(col,new LinearLayout.LayoutParams(0,Ui.dp(this,180),1));}
    Ui.add(p,chart,24);});
   slides.add(()->{LinearLayout p=page(5,"Твои привычки",null,null);int hour=data.optInt("top_hour",20);String daypart=hour<5?"глубокой ночью 🌙":hour<12?"по утрам ☀️":hour<18?"днём":"по вечерам 🌆";
    Ui.add(p,big("Чаще всего ты смотришь "+daypart),12);Ui.add(p,line("Любимый час — "+hour+":00"),10);
    if(data.optInt("night_episodes")>0)Ui.add(p,line("🦉 "+data.optInt("night_episodes")+" "+Social.plural(data.optInt("night_episodes"),"серия","серии","серий")+" после полуночи"),10);
    JSONObject day=data.optJSONObject("best_day");if(day!=null)Ui.add(p,line("🔥 Рекорд дня — "+day.optInt("episodes")+" "+Social.plural(day.optInt("episodes"),"серия","серии","серий")+" ("+day.optString("date")+")"),10);
    Ui.add(p,line("📅 Лучшая серия дней подряд — "+Rewards.days(data.optInt("streak_best"))),10);
    JSONObject first=data.optJSONObject("first_anime");if(first!=null)Ui.add(p,line("🎬 Год начался с «"+first.optString("title")+"»"),10);});
   slides.add(()->{LinearLayout p=page(6,"Вместе",null,null);Ui.add(p,big(data.optInt("friends")+" "+Social.plural(data.optInt("friends"),"друг","друга","друзей")+" в Yoru"),12);
    Ui.add(p,line("✍ "+data.optInt("reviews")+" "+Social.plural(data.optInt("reviews"),"отзыв","отзыва","отзывов")+(data.isNull("avg_score")?"":" · средняя оценка "+data.optString("avg_score"))),10);
    Ui.add(p,line("✔ Досмотрено аниме — "+data.optInt("completed")),10);});
  }
  slides.add(()->{LinearLayout p=page(7,"Итоги "+data.optInt("year"),null,null);
   if(!data.isNull("top_percent"))Ui.add(p,big("Ты в топ-"+data.optInt("top_percent")+"% зрителей Yoru"),12);
   Ui.add(p,line("Уровень "+data.optInt("level")+" · "+data.optString("hours")+" ч · "+data.optInt("episodes")+" серий"),10);
   Button share=Ui.button(this,"Поделиться",true);Ui.add(p,share,26);share.setOnClickListener(v->{JSONArray top=data.optJSONArray("top_anime");String text="Мой "+data.optInt("year")+" в Yoru: "+data.optString("hours")+" ч аниме, "+data.optInt("episodes")+" серий"+(top!=null&&top.length()>0?", аниме года — «"+top.optJSONObject(0).optString("title")+"»":"")+(data.isNull("top_percent")?"":", топ-"+data.optInt("top_percent")+"% зрителей")+" 🌙";startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Поделиться"));});
   Button close=Ui.textButton(this,"Закрыть");close.setTextColor(Color.WHITE);close.setOnClickListener(v->finish());Ui.add(p,close,6);});
  for(int i=0;i<slides.size();i++){View seg=new View(this);seg.setBackground(Ui.shape(this,0x55FFFFFF,2));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,Ui.dp(this,3),1);if(i>0)sp.leftMargin=Ui.dp(this,4);bars.addView(seg,sp);}
  show(0);
 }
 void show(int next){
  if(next<0)next=0;if(next>=slides.size()){finish();return;}index=next;
  stage.removeAllViews();slides.get(index).run();stage.addView(bars,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
  for(int i=0;i<bars.getChildCount();i++)bars.getChildAt(i).setBackground(Ui.shape(this,i<index?Color.WHITE:0x55FFFFFF,2));
  if(timer!=null)timer.cancel();View current=bars.getChildAt(index);ClipFill fill=new ClipFill();current.setBackground(fill);
  timer=ValueAnimator.ofFloat(0,1);timer.setDuration(index==slides.size()-1?60000:7000);timer.addUpdateListener(a->{fill.level=(float)a.getAnimatedValue();fill.invalidateSelf();});
  timer.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled&&index<slides.size()-1)show(index+1);}});timer.start();
 }
 /** Полоска прогресса истории: белая часть растёт слева направо. */
 final class ClipFill extends android.graphics.drawable.Drawable{float level;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  public void draw(Canvas c){Rect b=getBounds();float r=b.height()/2f;p.setColor(0x55FFFFFF);c.drawRoundRect(new RectF(b),r,r,p);p.setColor(Color.WHITE);c.drawRoundRect(new RectF(b.left,b.top,b.left+b.width()*level,b.bottom),r,r,p);}
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}

 LinearLayout page(int sky,String title,String subtitle,String unused){
  int[] colors=SKY[sky%SKY.length];FrameLayout bg=new FrameLayout(this);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors[0],blend(colors[0],colors[1]),colors[1]});bg.setBackground(g);stage.addView(bg,new FrameLayout.LayoutParams(-1,-1));
  Ornaments.Overlay sparkle=new Ornaments.Overlay(this);try{sparkle.set(new JSONObject().put("type",sky%2==0?"sparkles":"fireflies").put("colors",new JSONArray().put("#FFFFFF").put("#FFE08A")),null);}catch(JSONException ignored){}bg.addView(sparkle,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout p=Ui.column(this);p.setGravity(Gravity.CENTER_VERTICAL);p.setPadding(Ui.dp(this,28),Ui.dp(this,64),Ui.dp(this,28),Ui.dp(this,40));bg.addView(p,new FrameLayout.LayoutParams(-1,-1));
  TextView head=Ui.title(this,title,30);head.setTextColor(Color.WHITE);p.addView(head);head.setAlpha(0);head.setTranslationY(Ui.dp(this,24));head.animate().alpha(1).translationY(0).setDuration(450);
  if(subtitle!=null){TextView sub=Ui.title(this,subtitle,subtitle.length()<24?40:20);sub.setTextColor(0xFFFFF2C2);Ui.add(p,sub,10);sub.setAlpha(0);sub.animate().alpha(1).setStartDelay(250).setDuration(450);}
  return p;
 }
 void intro(){LinearLayout p=page(0,"Год в Yoru",String.valueOf(data.optInt("year")),null);Ui.add(p,line("Давай вспомним, каким был твой аниме-год. Нажимай справа, чтобы листать."),18);
  TextView moon=Ui.title(this,"☾",96);moon.setTextColor(0xFFFFE9A8);moon.setGravity(Gravity.CENTER);Ui.add(p,moon,24);ValueAnimator wobble=ValueAnimator.ofFloat(-8,8);wobble.setDuration(1800);wobble.setRepeatMode(ValueAnimator.REVERSE);wobble.setRepeatCount(ValueAnimator.INFINITE);wobble.addUpdateListener(a->moon.setRotation((float)a.getAnimatedValue()));wobble.start();}
 TextView line(String text){TextView t=Ui.text(this,text,17,0xEEFFFFFF);t.setLineSpacing(Ui.dp(this,3),1);return t;}
 TextView big(String text){TextView t=Ui.title(this,text,26);t.setTextColor(Color.WHITE);return t;}
 View center(TextView t){t.setGravity(Gravity.CENTER);return t;}
 void pop(View v){v.setScaleX(.7f);v.setScaleY(.7f);v.setAlpha(0);v.animate().scaleX(1).scaleY(1).alpha(1).setDuration(500).setInterpolator(new android.view.animation.OvershootInterpolator());}
 static int blend(int a,int b){return Color.rgb((Color.red(a)+Color.red(b))/2,(Color.green(a)+Color.green(b))/2,(Color.blue(a)+Color.blue(b))/2);}
}
