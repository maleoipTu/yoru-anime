package app.yoru.anime;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class Ui {
 static final int BG=0xFF101117,PANEL=0xFF1A1B25,RAISED=0xFF242633,LINE=0xFF30313F,TEXT=0xFFF4F2FA,MUTED=0xFFA4A5B7,ACCENT=0xFFC0AEFF,SOFT=0xFF302B45;
 static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static LinearLayout column(Context c){LinearLayout result=new LinearLayout(c);result.setOrientation(LinearLayout.VERTICAL);return result;}
 static LinearLayout row(Context c){LinearLayout result=new LinearLayout(c);result.setOrientation(LinearLayout.HORIZONTAL);result.setGravity(Gravity.CENTER_VERTICAL);return result;}
 static TextView text(Context c,String value,int size,int color){TextView result=new TextView(c);result.setText(value);result.setTextSize(size);result.setTextColor(color);result.setIncludeFontPadding(false);result.setLineSpacing(dp(c,3),1);return result;}
 static TextView title(Context c,String value,int size){TextView result=text(c,value,size,TEXT);result.setTypeface(null,Typeface.BOLD);return result;}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable result=new GradientDrawable();result.setColor(color);result.setCornerRadius(dp(c,radius));return result;}
 static RippleDrawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x20FFFFFF),shape(c,color,radius),shape(c,Color.WHITE,radius));}
 static Button button(Context c,String value,boolean primary){
  Button result=new Button(c);result.setText(value);result.setTextSize(14);result.setAllCaps(false);result.setIncludeFontPadding(false);result.setMinWidth(0);result.setMinimumWidth(0);result.setMinHeight(dp(c,48));result.setMinimumHeight(dp(c,48));result.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));result.setStateListAnimator(null);result.setMaxLines(2);result.setEllipsize(TextUtils.TruncateAt.END);
  result.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xFF656677,primary?0xFF201A30:TEXT}));
  StateListDrawable backgrounds=new StateListDrawable();backgrounds.addState(new int[]{-android.R.attr.state_enabled},shape(c,PANEL,14));backgrounds.addState(new int[]{},ripple(c,primary?ACCENT:RAISED,14));result.setBackground(backgrounds);return result;
 }
 static Button iconButton(Context c,String icon,String description){Button result=button(c,"",false);result.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));result.setBackground(ripple(c,Color.TRANSPARENT,24));result.setCompoundDrawablesWithIntrinsicBounds(new AppIcon(c,icon,TEXT,24),null,null,null);result.setContentDescription(description);result.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return result;}
 static void icon(Button button,String icon,int color){button.setCompoundDrawablesWithIntrinsicBounds(new AppIcon(button.getContext(),icon,color,24),null,null,null);}
 static Button textButton(Context c,String text){Button result=button(c,text,false);result.setBackground(ripple(c,Color.TRANSPARENT,12));result.setTextColor(ACCENT);return result;}
 static Button field(Context c,String label){Button result=button(c,"",false);result.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);result.setMinHeight(dp(c,64));result.setCompoundDrawablePadding(dp(c,8));result.setCompoundDrawablesWithIntrinsicBounds(null,null,new AppIcon(c,"chevron",MUTED,18),null);fieldValue(result,label,"Выбрать");return result;}
 static void fieldValue(Button button,String label,String value){SpannableString text=new SpannableString(label+"\n"+value);text.setSpan(new RelativeSizeSpan(.76f),0,label.length(),0);text.setSpan(new ForegroundColorSpan(MUTED),0,label.length(),0);button.setText(text);button.setTextSize(15);button.setContentDescription(label+": "+value);}
 static String voice(String value){return value.startsWith("Озвучка ")?value.substring(8):value;}
 static String provider(String value){return value.startsWith("Плеер ")?value.substring(6):value;}
 static void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(parent.getContext(),top);parent.addView(child,params);}
 static LinearLayout root(Activity activity){LinearLayout result=column(activity);result.setBackgroundColor(BG);if(Build.VERSION.SDK_INT>=30)result.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});activity.setContentView(result);return result;}
 static LinearLayout padded(Context c,int padding){LinearLayout result=column(c);int p=dp(c,padding);result.setPadding(p,p,p,p);return result;}
 static void round(View view,int radius){view.setBackground(shape(view.getContext(),PANEL,radius));view.setClipToOutline(true);}
 static View divider(Context c){View view=new View(c);view.setBackgroundColor(LINE);view.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,1)));return view;}
 static void choose(Activity activity,String title,List<String> options,String selected,Consumer<String> result){if(options.isEmpty())return;AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]),options.indexOf(selected),(d,which)->{d.dismiss();result.accept(options.get(which));}).setNegativeButton("Отмена",null).create();dialog.setOnShowListener(d->{if(dialog.getListView()!=null)dialog.getListView().setSelection(Math.max(0,options.indexOf(selected)));});dialog.show();}
 static Dialog sheet(Activity activity,String title,LinearLayout content){Dialog dialog=new Dialog(activity);LinearLayout panel=padded(activity,20);panel.setBackground(shape(activity,PANEL,24));LinearLayout header=row(activity);header.addView(Ui.title(activity,title,20),new LinearLayout.LayoutParams(0,-2,1));Button close=iconButton(activity,"close","Закрыть настройки");close.setOnClickListener(v->dialog.dismiss());header.addView(close);panel.addView(header);ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(panel);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}dialog.show();if(window!=null){window.setLayout(-1,-2);panel.post(()->{int maximum=(int)(activity.getResources().getDisplayMetrics().heightPixels*.84f);if(panel.getHeight()>maximum)window.setLayout(-1,maximum);});}return dialog;}
 static void site(Activity activity,Anime anime){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://yummyani.me/catalog/item/"+anime.alias)));}catch(ActivityNotFoundException e){Toast.makeText(activity,"Не найден браузер для открытия ссылки",Toast.LENGTH_SHORT).show();}}
 static final ExecutorService IMAGES=Executors.newFixedThreadPool(3);
 static final android.util.LruCache<String,Bitmap> CACHE=new android.util.LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}};
 static void poster(Activity activity,ImageView target,String url){target.setScaleType(ImageView.ScaleType.CENTER_CROP);target.setTag(url);target.setImageDrawable(new PosterPlaceholder(activity));Bitmap known=CACHE.get(url);if(known!=null){target.setImageBitmap(known);return;}if(!url.startsWith("https://"))return;IMAGES.execute(()->{HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(15000);byte[] data;try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>8*1024*1024)throw new IOException();out.write(buffer,0,count);}data=out.toByteArray();}BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>500||bounds.outHeight/options.inSampleSize>700)options.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap!=null){CACHE.put(url,bitmap);activity.runOnUiThread(()->{if(!activity.isDestroyed()&&url.equals(target.getTag()))target.setImageBitmap(bitmap);});}}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});}
 static final class PosterPlaceholder extends Drawable {
  final Paint paint=new Paint(3);final AppIcon moon;
  PosterPlaceholder(Context c){moon=new AppIcon(c,"moon",0xFF676176,36);}
  @Override public void draw(Canvas canvas){Rect b=getBounds();paint.setShader(new LinearGradient(0,b.top,b.right,b.bottom,new int[]{0xFF272535,0xFF1A1B25},null,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);int size=Math.min(b.width()/3,b.height()/3);moon.setBounds(b.centerX()-size/2,b.centerY()-size/2,b.centerX()+size/2,b.centerY()+size/2);moon.draw(canvas);}
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 static final class AppIcon extends Drawable {
  final String name;final int tint,size;final Paint paint=new Paint(3);
  AppIcon(Context c,String name,int tint,int size){this.name=name;this.tint=tint;this.size=dp(c,size);setBounds(0,0,this.size,this.size);}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}@Override public boolean isStateful(){return true;}@Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
  void line(Canvas c,float... points){for(int i=0;i+3<points.length;i+=2)c.drawLine(points[i],points[i+1],points[i+2],points[i+3],paint);}
  @Override public void draw(Canvas canvas){int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);boolean enabled=false;for(int state:getState())if(state==android.R.attr.state_enabled)enabled=true;paint.setColor(tint);paint.setAlpha(getState().length==0||enabled?255:95);Path path=new Path();
   switch(name){
    case "back":line(canvas,15,5,8,12,15,19);break;
    case "chevron":line(canvas,9,6,15,12,9,18);break;
    case "down":line(canvas,6,9,12,15,18,9);break;
    case "search":canvas.drawCircle(10.5f,10.5f,6.5f,paint);line(canvas,15.5f,15.5f,21,21);break;
    case "close":line(canvas,6,6,18,18);line(canvas,18,6,6,18);break;
    case "play":paint.setStyle(Paint.Style.FILL);path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();canvas.drawPath(path,paint);break;
    case "pause":paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6,5,10,19,1,1,paint);canvas.drawRoundRect(14,5,18,19,1,1,paint);break;
    case "heart":case "heart-fill":if(name.endsWith("fill"))paint.setStyle(Paint.Style.FILL);path.moveTo(12,20);path.cubicTo(10,18,3,13,3,8);path.cubicTo(3,2,10,2,12,7);path.cubicTo(14,2,21,2,21,8);path.cubicTo(21,13,14,18,12,20);canvas.drawPath(path,paint);break;
    case "catalog":canvas.drawRoundRect(3,3,10,10,1.5f,1.5f,paint);canvas.drawRoundRect(14,3,21,10,1.5f,1.5f,paint);canvas.drawRoundRect(3,14,10,21,1.5f,1.5f,paint);canvas.drawRoundRect(14,14,21,21,1.5f,1.5f,paint);break;
    case "calendar":canvas.drawRoundRect(3,5,21,21,3,3,paint);line(canvas,3,10,21,10);line(canvas,8,3,8,7);line(canvas,16,3,16,7);line(canvas,8,15,10,15);line(canvas,14,15,16,15);break;
    case "history":canvas.drawArc(4,4,21,21,-100,300,false,paint);line(canvas,3,4,3,10,8,10);line(canvas,12,7,12,13,16,15);break;
    case "retry":canvas.drawArc(4,4,20,20,-50,300,false,paint);line(canvas,21,4,21,10,15,10);break;
    case "settings":canvas.drawCircle(12,12,3,paint);for(int i=0;i<8;i++){canvas.save();canvas.rotate(45*i,12,12);line(canvas,12,2,12,5);canvas.restore();}canvas.drawCircle(12,12,7,paint);break;
    case "fullscreen":line(canvas,3,9,3,3,9,3);line(canvas,15,3,21,3,21,9);line(canvas,3,15,3,21,9,21);line(canvas,15,21,21,21,21,15);break;
    case "collapse":line(canvas,3,8,8,8,8,3);line(canvas,16,3,16,8,21,8);line(canvas,3,16,8,16,8,21);line(canvas,16,21,16,16,21,16);break;
    case "previous":line(canvas,15,5,8,12,15,19);line(canvas,4,5,4,19);break;
    case "next":line(canvas,9,5,16,12,9,19);line(canvas,20,5,20,19);break;
    case "external":canvas.drawRoundRect(3,8,16,21,2,2,paint);line(canvas,13,3,21,3,21,11);line(canvas,10,14,21,3);break;
    case "moon":path.moveTo(20,16);path.cubicTo(9,19,5,9,10,3);path.cubicTo(-1,5,1,21,12,21);path.cubicTo(16,21,19,19,20,16);canvas.drawPath(path,paint);break;
    case "rewind":case "forward":canvas.save();if(name.equals("rewind")){canvas.scale(-1,1,12,12);}canvas.drawArc(3,3,21,21,-80,300,false,paint);line(canvas,16,2,20,5,16,8);canvas.restore();paint.setStyle(Paint.Style.FILL);paint.setTextSize(9);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("10",12,15,paint);break;
    default:canvas.drawCircle(12,12,8,paint);
   }canvas.restoreToCount(save);
  }
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
