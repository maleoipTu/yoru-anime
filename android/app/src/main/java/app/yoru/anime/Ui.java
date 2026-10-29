package app.yoru.anime;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class Ui {
 static final int BG=Color.rgb(17,17,23),PANEL=Color.rgb(29,25,40),TEXT=Color.rgb(245,243,255),MUTED=Color.rgb(174,166,190),ACCENT=Color.rgb(179,162,255);
 static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
 static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(c,3),1);return t;}
 static TextView title(Context c,String value,int size){TextView t=text(c,value,size,TEXT);t.setTypeface(null,Typeface.BOLD);return t;}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static Button button(Context c,String value,boolean primary){Button b=new Button(c);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.rgb(25,17,38):TEXT);b.setMinHeight(dp(c,48));b.setMinimumWidth(0);b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));b.setStateListAnimator(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),shape(c,primary?ACCENT:Color.rgb(43,35,55),11),null));return b;}
 static void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(parent.getContext(),top);parent.addView(child,lp);}
 static LinearLayout root(Activity a){LinearLayout root=column(a);root.setBackgroundColor(BG);if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});}a.setContentView(root);return root;}
 static LinearLayout padded(Context c,int padding){LinearLayout l=column(c);int p=dp(c,padding);l.setPadding(p,p,p,p);return l;}
 static void choose(Activity a,String title,List<String> options,String selected,Consumer<String> result){if(options.isEmpty())return;new AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]),options.indexOf(selected),(dialog,which)->{dialog.dismiss();result.accept(options.get(which));}).setNegativeButton("Отмена",null).show();}
 static void site(Activity a,Anime anime){a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://yummyani.me/catalog/item/"+anime.alias)));}
 static final ExecutorService IMAGES=Executors.newFixedThreadPool(3);
 static final android.util.LruCache<String,Bitmap> CACHE=new android.util.LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
 static void poster(Activity a,ImageView target,String url){target.setScaleType(ImageView.ScaleType.CENTER_CROP);target.setBackgroundColor(PANEL);target.setTag(url);Bitmap known=CACHE.get(url);if(known!=null){target.setImageBitmap(known);return;}if(!url.startsWith("https://"))return;IMAGES.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);byte[] data;try(InputStream s=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]buf=new byte[8192];int n;while((n=s.read(buf))!=-1)out.write(buf,0,n);data=out.toByteArray();}BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;while(bounds.outWidth/opts.inSampleSize>500||bounds.outHeight/opts.inSampleSize>700)opts.inSampleSize*=2;Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(b!=null){CACHE.put(url,b);a.runOnUiThread(()->{if(!a.isDestroyed()&&url.equals(target.getTag()))target.setImageBitmap(b);});}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
}
