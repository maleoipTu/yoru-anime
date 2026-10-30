package app.yoru.anime;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import org.json.*;
import java.util.*;

/** Украшения профиля: рамки аватара, цвет ника и значки. Стиль приходит из таблицы decorations. */
final class Decor {
 static Map<String,JSONObject> index(JSONArray all){Map<String,JSONObject> result=new HashMap<>();for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null)result.put(o.optString("id"),o);}return result;}
 static JSONObject style(Map<String,JSONObject> index,String id){JSONObject d=index.get(id);JSONObject s=d==null?null:d.optJSONObject("style");return s==null?new JSONObject():s;}
 static int[] colors(JSONObject style,int fallback){
  JSONArray a=style.optJSONArray("colors");if(a==null||a.length()==0)return new int[]{fallback};
  int[] result=new int[a.length()];for(int i=0;i<a.length();i++)result[i]=Rewards.color(a.optString(i),fallback);return result;
 }
 static String name(JSONObject profile){String display=profile.optString("display_name").trim();return display.isEmpty()?profile.optString("username","Гость"):display;}

 /** Цвет ника: один цвет — обычный текст, несколько — горизонтальный градиент по ширине надписи. */
 static void nameColor(TextView view,JSONObject style){
  int[] colors=colors(style,Ui.TEXT);view.getPaint().setShader(null);view.setTextColor(colors[0]);
  if(colors.length<2)return;
  Runnable apply=()->{float width=Math.max(1,view.getPaint().measureText(view.getText().toString()));view.getPaint().setShader(new LinearGradient(0,0,width,0,colors,null,Shader.TileMode.CLAMP));view.invalidate();};
  view.post(apply);
 }
 /** Глиф значка в его цвете, например «★». */
 static CharSequence badge(JSONObject style){String glyph=style.optString("glyph");if(glyph.isEmpty())return "";SpannableString s=new SpannableString(glyph);s.setSpan(new ForegroundColorSpan(Rewards.color(style.optString("color"),Ui.ACCENT)),0,glyph.length(),0);return s;}

 /** Круглый аватар: постер или первая буква ника, а вокруг — рамка из украшения. */
 static final class AvatarView extends View {
  final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG),letterPaint=new Paint(Paint.ANTI_ALIAS_FLAG),image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Matrix matrix=new Matrix();
  int[] frame={Ui.ACCENT};boolean animated,glowing;float angle;ValueAnimator spinner;
  String letter="?";Bitmap picture;String pictureUrl="";

  AvatarView(Context c){super(c);ring.setStyle(Paint.Style.STROKE);glow.setStyle(Paint.Style.STROKE);fill.setColor(Ui.SOFT);letterPaint.setColor(Ui.ACCENT);letterPaint.setTextAlign(Paint.Align.CENTER);letterPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}

  void frame(JSONObject style){frame=colors(style,Ui.ACCENT);animated=style.optBoolean("animated");glowing=style.optBoolean("glow");updateAnimation();invalidate();}
  void person(String name,String avatarUrl){
   letter=name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Locale.forLanguageTag("ru"));
   if(avatarUrl==null||!avatarUrl.startsWith("https://")){picture=null;pictureUrl="";invalidate();return;}
   if(avatarUrl.equals(pictureUrl)&&picture!=null)return;
   pictureUrl=avatarUrl;picture=Ui.CACHE.get(avatarUrl);invalidate();
   if(picture==null&&getContext() instanceof Activity){Activity activity=(Activity)getContext();android.widget.ImageView probe=new android.widget.ImageView(activity){@Override public void setImageBitmap(Bitmap bitmap){if(avatarUrl.equals(pictureUrl)){picture=bitmap;AvatarView.this.invalidate();}}};Ui.poster(activity,probe,avatarUrl);}
  }

  void updateAnimation(){
   boolean run=animated&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();
   if(run&&spinner==null){spinner=ValueAnimator.ofFloat(0,360);spinner.setDuration(6000);spinner.setRepeatCount(ValueAnimator.INFINITE);spinner.setInterpolator(new LinearInterpolator());spinner.addUpdateListener(a->{angle=(float)a.getAnimatedValue();invalidate();});spinner.start();}
   else if(!run&&spinner!=null){spinner.cancel();spinner=null;angle=0;}
  }
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateAnimation();}
  @Override protected void onDetachedFromWindow(){if(spinner!=null){spinner.cancel();spinner=null;}super.onDetachedFromWindow();}

  @Override protected void onDraw(Canvas canvas){
   float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f;
   float stroke=Math.max(2,size*.07f),radius=size/2f-stroke/2f-(glowing?size*.04f:0),inner=radius-stroke/2f-size*.03f;
   if(picture!=null){
    float scale=Math.max(inner*2/picture.getWidth(),inner*2/picture.getHeight());matrix.setScale(scale,scale);matrix.postTranslate(cx-picture.getWidth()*scale/2,cy-picture.getHeight()*scale/2);
    BitmapShader shader=new BitmapShader(picture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);shader.setLocalMatrix(matrix);image.setShader(shader);canvas.drawCircle(cx,cy,inner,image);
   }else{canvas.drawCircle(cx,cy,inner,fill);letterPaint.setTextSize(inner*.95f);Paint.FontMetrics m=letterPaint.getFontMetrics();canvas.drawText(letter,cx,cy-(m.ascent+m.descent)/2,letterPaint);}
   Shader shader;
   if(frame.length==1)shader=null;
   else{int[] loop=frame;if(loop[0]!=loop[loop.length-1]){loop=Arrays.copyOf(frame,frame.length+1);loop[frame.length]=frame[0];}SweepGradient sweep=new SweepGradient(cx,cy,loop,null);Matrix rotate=new Matrix();rotate.setRotate(angle-90,cx,cy);sweep.setLocalMatrix(rotate);shader=sweep;}
   if(glowing){glow.setStrokeWidth(stroke*2.6f);glow.setShader(shader);glow.setColor(frame[0]);glow.setAlpha(70);canvas.drawCircle(cx,cy,radius,glow);}
   ring.setStrokeWidth(stroke);ring.setShader(shader);ring.setColor(frame[0]);canvas.drawCircle(cx,cy,radius,ring);
  }
 }
}
