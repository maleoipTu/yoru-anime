package app.yoru.anime;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
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

 /** Круглый аватар: картинка, GIF/анимированный WebP или WebM, либо первая буква ника; вокруг — рамка из украшения. */
 static final class AvatarView extends android.widget.FrameLayout {
  final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG),letterPaint=new Paint(Paint.ANTI_ALIAS_FLAG),border=new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path clip=new Path();
  int[] frame={Ui.ACCENT};boolean animated,glowing;float angle;ValueAnimator spinner;
  String letter="?",pictureUrl="";android.graphics.drawable.Drawable picture;Media.VideoSurface video;

  AvatarView(Context c){super(c);setWillNotDraw(false);ring.setStyle(Paint.Style.STROKE);glow.setStyle(Paint.Style.STROKE);border.setStyle(Paint.Style.STROKE);border.setColor(Ui.BG);fill.setColor(Ui.SOFT);letterPaint.setColor(Ui.TEXT);letterPaint.setTextAlign(Paint.Align.CENTER);letterPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}

  void frame(JSONObject style){frame=colors(style,Ui.ACCENT);animated=style.optBoolean("animated");glowing=style.optBoolean("glow");updateAnimation();invalidate();}
  void person(String name,String avatarUrl){
   letter=name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Locale.forLanguageTag("ru"));
   String url=avatarUrl==null?"":avatarUrl;
   if(url.equals(pictureUrl)){invalidate();return;}
   pictureUrl=url;setPicture(null);
   if(video!=null){removeView(video);video.release();video=null;}
   if(!url.startsWith("https://")){invalidate();return;}
   if(Media.video(url)){video=new Media.VideoSurface(getContext());video.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,android.graphics.Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});video.setClipToOutline(true);addView(video);video.play(url);requestLayout();}
   else if(getContext() instanceof Activity)Media.drawable((Activity)getContext(),url,512,d->{if(url.equals(pictureUrl))setPicture(d);});
  }
  void setPicture(android.graphics.drawable.Drawable d){if(picture!=null)picture.setCallback(null);picture=d;if(d!=null)d.setCallback(this);invalidate();}
  @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable who){return who==picture||super.verifyDrawable(who);}

  float stroke(){return Math.max(2,Math.min(getWidth(),getHeight())*.065f);}
  float radius(){float size=Math.min(getWidth(),getHeight());return size/2f-stroke()/2f-(glowing?size*.04f:0);}
  float inner(){return radius()-stroke()/2f-Math.min(getWidth(),getHeight())*.035f;}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){if(video!=null){float in=inner(),cx=(r-l)/2f,cy=(b-t)/2f;video.layout(Math.round(cx-in),Math.round(cy-in),Math.round(cx+in),Math.round(cy+in));}}
  @Override protected void onMeasure(int w,int h){super.onMeasure(w,h);if(video!=null){int side=Math.round(inner()*2);video.measure(MeasureSpec.makeMeasureSpec(Math.max(1,side),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,side),MeasureSpec.EXACTLY));}}

  void updateAnimation(){
   boolean run=animated&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();
   if(run&&spinner==null){spinner=ValueAnimator.ofFloat(0,360);spinner.setDuration(6000);spinner.setRepeatCount(ValueAnimator.INFINITE);spinner.setInterpolator(new LinearInterpolator());spinner.addUpdateListener(a->{angle=(float)a.getAnimatedValue();invalidate();});spinner.start();}
   else if(!run&&spinner!=null){spinner.cancel();spinner=null;angle=0;}
  }
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateAnimation();}
  @Override protected void onDetachedFromWindow(){if(spinner!=null){spinner.cancel();spinner=null;}super.onDetachedFromWindow();}

  @Override protected void onDraw(Canvas canvas){
   float cx=getWidth()/2f,cy=getHeight()/2f,in=inner();
   canvas.drawCircle(cx,cy,in,fill);
   if(picture!=null){int save=canvas.save();clip.reset();clip.addCircle(cx,cy,in,Path.Direction.CW);canvas.clipPath(clip);
    int iw=Math.max(1,picture.getIntrinsicWidth()),ih=Math.max(1,picture.getIntrinsicHeight());float scale=Math.max(in*2/iw,in*2/ih);int w=Math.round(iw*scale),h=Math.round(ih*scale);
    picture.setBounds(Math.round(cx-w/2f),Math.round(cy-h/2f),Math.round(cx+w/2f),Math.round(cy+h/2f));picture.draw(canvas);canvas.restoreToCount(save);}
   else if(video==null){letterPaint.setTextSize(in*.95f);Paint.FontMetrics m=letterPaint.getFontMetrics();canvas.drawText(letter,cx,cy-(m.ascent+m.descent)/2,letterPaint);}
  }
  /** Рамка рисуется поверх видео-аватара. */
  @Override protected void dispatchDraw(Canvas canvas){
   super.dispatchDraw(canvas);
   float cx=getWidth()/2f,cy=getHeight()/2f,stroke=stroke(),radius=radius();
   border.setStrokeWidth(Math.min(getWidth(),getHeight())*.035f+1);canvas.drawCircle(cx,cy,radius-stroke/2f,border);
   Shader shader=null;
   if(frame.length>1){int[] loop=frame;if(loop[0]!=loop[loop.length-1]){loop=Arrays.copyOf(frame,frame.length+1);loop[frame.length]=frame[0];}SweepGradient sweep=new SweepGradient(cx,cy,loop,null);Matrix rotate=new Matrix();rotate.setRotate(angle-90,cx,cy);sweep.setLocalMatrix(rotate);shader=sweep;}
   if(glowing){glow.setStrokeWidth(stroke*2.6f);glow.setShader(shader);glow.setColor(frame[0]);glow.setAlpha(70);canvas.drawCircle(cx,cy,radius,glow);}
   ring.setStrokeWidth(stroke);ring.setShader(shader);ring.setColor(frame[0]);canvas.drawCircle(cx,cy,radius,ring);
  }
 }
}
