package app.yoru.anime;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Build;
import android.view.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Аватары и баннеры: загрузка картинок и анимаций (GIF/анимированный WebP — через ImageDecoder, WebM — через ExoPlayer). */
final class Media {
 static final ExecutorService IO=Executors.newFixedThreadPool(2);
 static final android.util.LruCache<String,byte[]> BYTES=new android.util.LruCache<String,byte[]>(16*1024*1024){protected int sizeOf(String key,byte[] value){return value.length;}};

 static boolean video(String url){return url!=null&&url.toLowerCase(Locale.ROOT).split("\\?")[0].endsWith(".webm");}

 /** Скачивает и декодирует картинку; анимации возвращаются запущенными. */
 static void drawable(Activity activity,String url,int maxSide,Consumer<Drawable> done){
  if(url==null||!url.startsWith("https://")||video(url))return;
  IO.execute(()->{try{
   byte[] data=BYTES.get(url);
   if(data==null){data=download(url,10*1024*1024);BYTES.put(url,data);}
   Drawable result;
   if(Build.VERSION.SDK_INT>=28){
    result=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)),(decoder,info,source)->{int side=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(side>maxSide)decoder.setTargetSampleSize(Math.max(1,side/maxSide));});
   }else{BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,b);while(Math.max(b.outWidth,b.outHeight)/o.inSampleSize>maxSide)o.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);if(bitmap==null)return;result=new BitmapDrawable(activity.getResources(),bitmap);}
   Drawable ready=result;activity.runOnUiThread(()->{if(activity.isDestroyed())return;if(Build.VERSION.SDK_INT>=28&&ready instanceof AnimatedImageDrawable){((AnimatedImageDrawable)ready).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);((AnimatedImageDrawable)ready).start();}done.accept(ready);});
  }catch(Exception ignored){}});
 }
 static byte[] download(String url,int limit) throws IOException {
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);
  try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("too large");out.write(buf,0,n);}return out.toByteArray();}finally{c.disconnect();}
 }

 /** Беззвучное зацикленное видео WebM с обрезкой по центру (center-crop). */
 static final class VideoSurface extends TextureView {
  ExoPlayer player;String url="";int videoW,videoH;
  VideoSurface(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  void play(String source){
   if(source.equals(url)&&player!=null)return;url=source;release();
   player=new ExoPlayer.Builder(getContext()).build();player.setVolume(0);player.setRepeatMode(Player.REPEAT_MODE_ALL);player.setVideoTextureView(this);
   player.addListener(new Player.Listener(){@Override public void onVideoSizeChanged(VideoSize size){videoW=size.width;videoH=size.height;crop();}});
   player.setMediaItem(MediaItem.fromUri(Uri.parse(source)));player.prepare();player.setPlayWhenReady(isAttachedToWindow());
  }
  void crop(){if(videoW<=0||videoH<=0||getWidth()==0)return;float scale=Math.max(getWidth()/(float)videoW,getHeight()/(float)videoH);Matrix m=new Matrix();m.setScale(videoW*scale/getWidth(),videoH*scale/getHeight(),getWidth()/2f,getHeight()/2f);setTransform(m);}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);crop();}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(player==null&&!url.isEmpty()){String u=url;url="";play(u);}else if(player!=null)player.setPlayWhenReady(true);}
  @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
  void release(){if(player!=null){player.release();player=null;}}
 }

 /** Баннер профиля: картинка, GIF или WebM; без баннера — градиент цветов темы профиля. */
 static final class Banner extends android.widget.FrameLayout {
  final android.widget.ImageView image;VideoSurface video;String url="";
  Banner(Context c){super(c);image=new android.widget.ImageView(c);image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);addView(image,new LayoutParams(-1,-1));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  void show(Activity activity,String source,int primary,int accent){
   setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{primary,accent}));
   if(source==null)source="";if(source.equals(url))return;url=source;image.setImageDrawable(null);
   if(video!=null){removeView(video);video.release();video=null;}
   if(source.isEmpty())return;
   if(video(source)){video=new VideoSurface(activity);addView(video,new LayoutParams(-1,-1));video.play(source);}
   else{String expected=source;drawable(activity,source,1600,d->{if(expected.equals(url))image.setImageDrawable(d);});}
  }
 }

 // ───── Подготовка файла к загрузке ─────
 static final class Prepared {final byte[] data;final String mime,ext;Prepared(byte[] data,String mime,String ext){this.data=data;this.mime=mime;this.ext=ext;}}
 static final int AVATAR_SIDE=512,BANNER_W=1500,BANNER_H=500;

 /**
  * Статичные картинки обрезаются по центру и сжимаются (аватар 512×512, баннер 1500×500, WebP).
  * GIF, анимированный WebP и WebM загружаются как есть, если укладываются в правила Rewards.mediaProblem.
  */
 static Prepared prepare(Context context,byte[] data,String mime,boolean banner) throws IOException {
  mime=mime==null?"":mime.toLowerCase(Locale.ROOT);
  if(mime.equals("video/webm")){
   File temp=File.createTempFile("upload",".webm",context.getCacheDir());
   try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);}
   android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();
   try{r.setDataSource(temp.getAbsolutePath());int w=parse(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),h=parse(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));long d=parse(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
    String problem=Rewards.mediaProblem(banner,"webm",data.length,w,h,d);if(!problem.isEmpty())throw new IOException(problem);}
   finally{try{r.release();}catch(Exception ignored){}temp.delete();}
   return new Prepared(data,"video/webm","webm");
  }
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
  if(bounds.outWidth<=0)throw new IOException("Не получилось прочитать файл. Подходят PNG, JPEG, WebP, GIF и WebM.");
  boolean animated=mime.equals("image/gif");
  if(!animated&&mime.equals("image/webp")&&Build.VERSION.SDK_INT>=28){try{animated=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data))) instanceof AnimatedImageDrawable;}catch(Exception ignored){}}
  if(animated){String problem=Rewards.mediaProblem(banner,mime.endsWith("gif")?"gif":"webp",data.length,bounds.outWidth,bounds.outHeight,0);if(!problem.isEmpty())throw new IOException(problem);return new Prepared(data,mime,mime.endsWith("gif")?"gif":"webp");}
  int tw=banner?BANNER_W:AVATAR_SIDE,th=banner?BANNER_H:AVATAR_SIDE;
  BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while(bounds.outWidth/(o.inSampleSize*2)>=tw&&bounds.outHeight/(o.inSampleSize*2)>=th)o.inSampleSize*=2;
  Bitmap source=BitmapFactory.decodeByteArray(data,0,data.length,o);if(source==null)throw new IOException("Не получилось прочитать картинку.");
  float scale=Math.max(tw/(float)source.getWidth(),th/(float)source.getHeight());int cw=Math.round(tw/scale),ch=Math.round(th/scale);
  Bitmap cropped=Bitmap.createBitmap(source,Math.max(0,(source.getWidth()-cw)/2),Math.max(0,(source.getHeight()-ch)/2),Math.min(cw,source.getWidth()),Math.min(ch,source.getHeight()));
  Bitmap scaled=Bitmap.createScaledBitmap(cropped,Math.min(tw,Math.max(1,Math.round(cropped.getWidth()*scale))),Math.min(th,Math.max(1,Math.round(cropped.getHeight()*scale))),true);
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  scaled.compress(Build.VERSION.SDK_INT>=30?Bitmap.CompressFormat.WEBP_LOSSY:Bitmap.CompressFormat.WEBP,86,out);
  return new Prepared(out.toByteArray(),"image/webp","webp");
 }
 static int parse(String value){try{return Integer.parseInt(value);}catch(Exception e){return 0;}}
}
