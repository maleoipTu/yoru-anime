package app.yoru.anime;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Random;

/**
 * Нарисованные украшения: спрайт-листы из assets/decor (рендерятся из art/*.mjs).
 * Лист — горизонтальная лента кадров; manifest.json хранит размер кадра, число кадров и частоту.
 */
final class Sprites {
 /** Темп движения частиц эффектов и деталей рамок (1 — исходный, медленный). */
 static final float EFFECT_PACE=1.6f,FRAME_PACE=1.4f;
 private Sprites(){}
 static final class Sheet {
  final Bitmap bitmap;final int w,h,frames,fps;final float scale;
  Sheet(Bitmap bitmap,int w,int h,int frames,int fps,float scale){this.bitmap=bitmap;this.w=w;this.h=h;this.frames=frames;this.fps=fps;this.scale=scale;}
  int frameAt(long millis){return frames<=1||fps<=0?0:(int)((millis*fps/1000)%frames);}
  Rect src(int frame){int fw=Math.round(w*scale),fh=Math.round(h*scale);int i=Math.max(0,Math.min(frames-1,frame));return new Rect(i*fw,0,i*fw+fw,fh);}
 }
 static JSONObject manifest;
 static final android.util.LruCache<String,Sheet> CACHE=new android.util.LruCache<String,Sheet>(48*1024*1024){protected int sizeOf(String k,Sheet s){return s.bitmap.getByteCount();}};
 static final Paint PAINT=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);

 static synchronized JSONObject manifest(Context c){
  if(manifest==null){try(InputStream in=c.getAssets().open("decor/manifest.json")){manifest=new JSONObject(Api.read(in));}catch(Exception e){manifest=new JSONObject();}}
  return manifest;
 }
 /** Лист по имени; small — для маленьких превью (вдвое меньше памяти). null, если такого рисунка нет. */
 static Sheet get(Context c,String name,boolean small){
  if(name==null||name.isEmpty())return null;
  JSONObject spec=manifest(c).optJSONObject(name);if(spec==null)return null;
  String key=name+(small?"@2":"");Sheet cached=CACHE.get(key);if(cached!=null)return cached;
  try(InputStream in=c.getAssets().open("decor/"+name+".png")){
   BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=small?2:1;
   Bitmap b=BitmapFactory.decodeStream(in,null,o);if(b==null)return null;
   Sheet s=new Sheet(b,spec.optInt("w"),spec.optInt("h"),Math.max(1,spec.optInt("frames",1)),spec.optInt("fps"),small?.5f:1f);
   CACHE.put(key,s);return s;
  }catch(Exception e){return null;}
 }
 /** Имя листа украшения аватара по стилю: style.sprite, а для орбит — по глифу. */
 static String avatarName(JSONObject style){
  if(style==null)return "";String s=style.optString("sprite");if(!s.isEmpty())return s;
  String type=style.optString("type");if(type.equals("orbit"))return style.optString("glyph").equals("♥")?"orbit_hearts":"orbit_stars";return type;
 }
 /** Кадр в прямоугольник с поворотом, прозрачностью и отражением. */
 static void draw(Canvas canvas,Sheet s,int frame,float cx,float cy,float width,float height,float rotate,float alpha,boolean flipX,boolean flipY){
  if(s==null||alpha<=0.01f)return;
  int save=canvas.save();canvas.translate(cx,cy);if(rotate!=0)canvas.rotate(rotate);canvas.scale(flipX?-1:1,flipY?-1:1);
  PAINT.setAlpha(Math.round(Math.max(0,Math.min(1,alpha))*255));
  canvas.drawBitmap(s.bitmap,s.src(frame),new RectF(-width/2,-height/2,width/2,height/2),PAINT);
  canvas.restoreToCount(save);
 }
 static void corner(Canvas canvas,Sheet s,int frame,float x,float y,float size,boolean flipX,boolean flipY,float alpha){
  if(s==null)return;float w=size,h=size*s.h/(float)s.w;
  draw(canvas,s,frame,x+(flipX?-w/2:w/2),y+(flipY?-h/2:h/2),w,h,0,alpha,flipX,flipY);
 }

 // ───────── Эффекты профиля из нарисованных частиц ─────────
 static String effectSheet(String type){switch(type){case "sparkles":return "fx_sparkle";case "bubbles":return "fx_bubble";case "petals":return "fx_petal";case "fireflies":return "fx_firefly";case "hearts":return "fx_heart";case "starfall":return "fx_starfall";case "bats":return "fx_bat";case "snow":return "fx_snow";default:return "";}}
 /** Частицы с глубиной: дальние мельче и бледнее, ближние крупнее. Возвращает false, если рисунка нет. */
 static boolean effect(Canvas canvas,Context c,String type,float w,float h,float density){
  if(type.equals("night_watch"))return nightWatch(canvas,c,w,h,density);
  Sheet s=get(c,effectSheet(type),false);if(s==null)return false;
  long now=SystemClock.uptimeMillis();float sec=now/1000f*EFFECT_PACE;Random r=new Random(type.hashCode());
  int count;float base;
  switch(type){case "snow":count=34;base=16;break;case "starfall":count=5;base=90;break;case "bats":count=6;base=46;break;case "fireflies":count=16;base=30;break;case "bubbles":count=14;base=30;break;case "sparkles":count=18;base=24;break;case "hearts":count=12;base=24;break;default:count=20;base=20;}
  // Слои глубины: дальние частицы мельче, бледнее и медленнее (параллакс); пара крупных полупрозрачных частиц
  // «у самой камеры» для лепестков, снега, пузырей, сердечек и искр. Рисуем от дальних к ближним.
  int near=type.equals("petals")||type.equals("snow")||type.equals("bubbles")||type.equals("hearts")||type.equals("sparkles")?2:0;
  float[][] parts=new float[count+near][];
  for(int k=0;k<parts.length;k++){
   float rx=r.nextFloat(),ry=r.nextFloat(),speed=.6f+r.nextFloat()*.8f,phase=r.nextFloat(),depth=.45f+r.nextFloat()*.55f;int variant=r.nextInt(Math.max(1,s.frames));
   if(k>=count)depth=1.6f+depth*.3f;
   parts[k]=new float[]{rx,ry,speed*(.55f+.45f*Math.min(depth,1.3f)),phase,depth,variant,.8f+r.nextFloat()*.4f,k};
  }
  java.util.Arrays.sort(parts,(pa,pb)->Float.compare(pa[4],pb[4]));
  for(float[] part:parts){
   float rx=part[0],ry=part[1],speed=part[2],phase=part[3],depth=part[4];int variant=(int)part[5],i=(int)part[7];
   float size=base*density*depth*part[6],alpha=depth>1.2f?.3f:.45f+.55f*depth;
   switch(type){
    case "sparkles":{float life=(sec*.35f*speed+phase)%1f,a=(float)Math.sin(life*Math.PI);draw(canvas,s,variant,rx*w,ry*h,size*(.4f+.6f*a),size*(.4f+.6f*a),life*90,a*alpha,false,false);break;}
    case "bubbles":{float p=(sec*.05f*speed+phase)%1f;float y=h*(1.1f-p*1.25f),x=rx*w+(float)Math.sin(sec*speed+phase*6)*12*density;draw(canvas,s,variant,x,y,size,size,0,alpha*Math.min(1,(1-p)*3),false,false);break;}
    case "petals":case "snow":{float p=(sec*(type.equals("snow")?.06f:.045f)*speed+phase)%1f;float y=h*(p*1.2f-.1f),x=rx*w+(float)Math.sin(sec*.8f*speed+phase*7)*22*density;draw(canvas,s,variant,x,y,size,size,sec*50*speed+phase*360,alpha,false,(int)(sec*speed+phase*5)%2==0&&type.equals("petals"));break;}
    case "fireflies":{float x=rx*w+(float)Math.sin(sec*.3f*speed+phase*9)*34*density,y=ry*h+(float)Math.cos(sec*.24f*speed+phase*5)*26*density,a=.3f+.7f*(.5f+.5f*(float)Math.sin(sec*2*speed+phase*11));draw(canvas,s,variant,x,y,size,size,0,a*alpha,false,false);break;}
    case "hearts":{float p=(sec*.07f*speed+phase)%1f;float y=h*(1.08f-p*1.15f),x=rx*w+(float)Math.sin(sec*1.2f+phase*5)*10*density;draw(canvas,s,variant,x,y,size*(.85f+.15f*(float)Math.sin(sec*4+i)),size,(float)Math.sin(sec+i)*12,alpha*(float)Math.sin(p*Math.PI),false,false);break;}
    case "starfall":{float p=(sec*.16f*speed+phase)%1f;if(p>.5f)break;float q=p/.5f;float x=w*(rx*.9f+.3f)-q*w*.55f,y=h*(ry*.45f-.15f)+q*h*.75f;float len=size*1.6f;draw(canvas,s,0,x,y,len,len*s.h/s.w,145,alpha*(float)Math.sin(q*Math.PI),false,false);break;}
    case "bats":{float p=(sec*.06f*speed+phase)%1f;boolean left=i%2==1;float x=w*(left?1.15f-p*1.3f:p*1.3f-.15f),y=h*(.12f+ry*.6f)+(float)Math.sin(p*Math.PI*4+i)*14*density;int frame=s.frameAt(now+(long)(phase*1000));draw(canvas,s,frame,x,y,size*1.45f,size,(float)Math.sin(sec*2+i)*8,alpha,left,false);break;}
   }
  }
  return true;
 }

 /** «Ночной дозор» (набор администрации): дальние звёзды, огоньки зари, размытое боке у «камеры» и кометы; дальние слои медленнее. */
 static boolean nightWatch(Canvas canvas,Context c,float w,float h,float density){
  Sheet s=get(c,"fx_night",false);if(s==null)return false;Sheet comet=get(c,"fx_night_comet",false);
  float sec=SystemClock.uptimeMillis()/1000f*EFFECT_PACE;Random r=new Random(4242);
  for(int i=0;i<22;i++){float rx=r.nextFloat(),ry=r.nextFloat(),phase=r.nextFloat()*6.2832f,depth=.3f+r.nextFloat()*.4f;int variant=r.nextInt(2);
   float span=w+40*density,x=(rx*span+sec*5*density*depth)%span-20*density;float tw=(float)Math.pow(.5+.5*Math.sin(sec*(1.1f+depth*1.6f)+phase),2);float size=(8+11*depth)*density*(.65f+.35f*tw);
   draw(canvas,s,variant,x,ry*h*.9f,size,size,tw*45,(.25f+.75f*tw)*(.4f+.6f*depth),false,false);}
  for(int i=0;i<10;i++){float rx=r.nextFloat(),speed=.6f+r.nextFloat()*.7f,phase=r.nextFloat(),depth=.55f+r.nextFloat()*.35f;int variant=2+r.nextInt(2);
   float p=(sec*.045f*speed+phase)%1f,size=(11+14*depth)*density;
   draw(canvas,s,variant,rx*w+(float)Math.sin(sec*.7f*speed+phase*9)*16*density*depth,h*(1.08f-p*1.2f),size,size,0,(float)Math.sin(p*Math.PI)*(.5f+.45f*depth),false,false);}
  for(int i=0;i<3;i++){float rx=r.nextFloat(),ry=r.nextFloat(),phase=r.nextFloat(),size=(64+40*r.nextFloat())*density;float p=(sec*.02f+phase)%1f;
   draw(canvas,s,4,w*(rx*.8f-.1f+p*.4f),h*(.15f+ry*.7f)+(float)Math.sin(sec*.3f+i)*12*density,size,size,0,.45f*(float)Math.sin(p*Math.PI),false,false);}
  if(comet!=null)for(int i=0;i<2;i++){float rx=r.nextFloat(),ry=r.nextFloat(),phase=r.nextFloat();float p=(sec*.085f+phase)%1f;if(p>.32f)continue;float q=p/.32f,dx=-w*.55f,dy=h*.5f,len=Math.min(200*density,w*.34f);
   draw(canvas,comet,0,w*(.6f+rx*.45f)+q*dx,h*(ry*.3f-.08f)+q*dy,len,len*comet.h/comet.w,(float)Math.toDegrees(Math.atan2(dy,dx)),(float)Math.sin(q*Math.PI),false,false);}
  return true;
 }

 // ───────── Рамки профиля с нарисованными деталями ─────────
 /** Возвращает false, если для типа нет рисунка (тогда рисуется процедурная рамка). */
 static boolean frame(Canvas canvas,Context c,String type,int[] colors,float w,float h,float radius,float density){
  long now=SystemClock.uptimeMillis();float sec=now/1000f*FRAME_PACE;float corner=Math.min(Math.min(w,h)*.62f,170*density);
  Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);line.setStyle(Paint.Style.STROKE);RectF box=new RectF(density,density,w-density,h-density);
  switch(type){
   case "cobweb":{Sheet web=get(c,"pf_cobweb",false),spider=get(c,"pf_spider",false);if(web==null)return false;
    corner(canvas,web,0,0,0,corner,false,false,.95f);corner(canvas,web,0,w,h,corner*.85f,true,true,.9f);
    float sx=w*.72f,drop=(float)(.5+.5*Math.sin(sec*.9))*h*.32f+28*density;line.setColor(0xCCE6E6F0);line.setStrokeWidth(density);canvas.drawLine(sx,0,sx,drop,line);
    if(spider!=null)draw(canvas,spider,spider.frameAt(now),sx,drop+14*density,40*density,46*density,(float)Math.sin(sec*1.7)*8,1,false,false);
    line.setColor(0x55E6E6F0);canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "vines":{Sheet v=get(c,"pf_vines",false);if(v==null)return false;
    float sway=(float)Math.sin(sec*.8)*1.5f;int save=canvas.save();canvas.rotate(sway,0,0);corner(canvas,v,0,-2*density,-2*density,corner,false,false,1);canvas.restoreToCount(save);
    save=canvas.save();canvas.rotate(-sway,w,h);corner(canvas,v,0,w+2*density,h+2*density,corner*.9f,true,true,1);canvas.restoreToCount(save);
    line.setStrokeWidth(2*density);line.setShader(new LinearGradient(0,0,w,h,0xFF7DD07A,0x445FA34A,Shader.TileMode.MIRROR));canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "constellation":{Sheet cs=get(c,"pf_constellation",false);if(cs==null)return false;
    float tw=.75f+.25f*(float)Math.sin(sec*1.6);corner(canvas,cs,0,w,0,corner*.9f,true,false,tw);corner(canvas,cs,0,0,h,corner*.75f,false,true,1.75f-tw);
    line.setStrokeWidth(1.4f*density);line.setColor(0x669FD8FF);line.setPathEffect(new DashPathEffect(new float[]{2*density,9*density},sec*20));canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "flames":{Sheet fl=get(c,"pf_flame",false);if(fl==null)return false;
    Paint glowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);glowPaint.setShader(new LinearGradient(0,h,0,h*.55f,0x66FF5A1F,0x00FF5A1F,Shader.TileMode.CLAMP));canvas.drawRect(0,h*.55f,w,h,glowPaint);
    int n=Math.max(6,Math.round(w/(38*density)));Random r=new Random(5);
    for(int i=0;i<n;i++){float fh=(48+r.nextFloat()*30)*density,fw=fh*fl.w/fl.h,x=(i+.5f)*w/n+(r.nextFloat()-.5f)*10*density;draw(canvas,fl,fl.frameAt(now+r.nextInt(1000)),x,h-fh/2+4*density,fw,fh,0,.95f,r.nextBoolean(),false);}
    Paint ember=new Paint(Paint.ANTI_ALIAS_FLAG);for(int i=0;i<14;i++){float p=(sec*.25f*(.6f+r.nextFloat())+r.nextFloat())%1f,x=r.nextFloat()*w+(float)Math.sin(sec*2+i)*6*density,y=h-p*h*.7f;ember.setColor(Ornaments.alpha(0xFFFFB347,1-p));canvas.drawCircle(x,y,(1.2f+1.6f*(1-p))*density,ember);}
    line.setStrokeWidth(2*density);line.setShader(new LinearGradient(0,0,0,h,0x33FFE08A,0xFFFF6A2E,Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "crystal":{Sheet cr=get(c,"pf_crystal",false);if(cr==null)return false;
    corner(canvas,cr,0,-4*density,-4*density,corner*.9f,false,false,1);corner(canvas,cr,0,w+4*density,h+4*density,corner*.8f,true,true,1);
    float g=(sec*.3f)%1f;Paint shine=new Paint(Paint.ANTI_ALIAS_FLAG);shine.setColor(Ornaments.alpha(0xFFFFFFFF,(float)Math.sin(g*Math.PI)));Ornaments.star4(canvas,corner*.45f,corner*.42f,6*density,shine.getColor());
    line.setStrokeWidth(1.6f*density);line.setShader(new LinearGradient(0,0,w,h,new int[]{0xFFB388FF,0x887EDCFF,0xFFB388FF},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "moon":{Sheet m=get(c,"pf_moon",false),cl=get(c,"pf_cloud",false);if(m==null)return false;
    Random r=new Random(3);for(int i=0;i<12;i++){float x=w*(.4f+r.nextFloat()*.6f),y=h*r.nextFloat()*.5f,a=.4f+.6f*(.5f+.5f*(float)Math.sin(sec*1.6f+i));Ornaments.star4(canvas,x,y,(2+2*r.nextFloat())*density,Ornaments.alpha(0xFFFFFFFF,a));}
    float ms=Math.min(120*density,h*.7f);draw(canvas,m,0,w-ms*.55f,ms*.55f,ms,ms,(float)Math.sin(sec*.5)*3,1,false,false);
    if(cl!=null){float cw=ms*1.2f,drift=(float)Math.sin(sec*.25)*14*density;draw(canvas,cl,0,w-ms*.9f+drift,ms*.92f,cw,cw*cl.h/cl.w,0,.9f,false,false);}
    line.setStrokeWidth(1.5f*density);line.setShader(new LinearGradient(w,0,0,h,0xFFFFE9A8,0x226F7BFF,Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,line);return true;}
   case "night_crown":{Sheet cs=get(c,"pf_night_corner",false),crest=get(c,"pf_night_crest",false);if(cs==null)return false;
    // «Полуночная грань» (набор администрации): узорные углы, герб с полумесяцем и бегущий по краю свет зари.
    line.setStrokeWidth(1.4f*density);line.setShader(new LinearGradient(0,0,w,h,new int[]{0xCCFFE3B8,0x55FF8A3D,0x664A3270,0x55FF8A3D,0xCCFF4D67},new float[]{0,.3f,.5f,.7f,1},Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,line);
    float rr=Math.min(radius,Math.min(box.width(),box.height())/2),per=2*(box.width()+box.height())-8*rr+2*(float)Math.PI*rr,light=96*density;
    if(per>light*3){line.setShader(new LinearGradient(0,0,w,0,0xFFFF4D67,0xFFFFE3B8,Shader.TileMode.CLAMP));line.setStrokeCap(Paint.Cap.ROUND);float[][] passes={{7,.2f},{3.5f,.35f},{1.8f,1}};
     for(int k=0;k<2;k++)for(float[] pass:passes){float phase=((sec*62*density+k*per/2)%per);line.setPathEffect(new DashPathEffect(new float[]{light,per-light},per-phase));line.setStrokeWidth(pass[0]*density);line.setAlpha(Math.round(255*pass[1]));canvas.drawRoundRect(box,radius,radius,line);}
     line.setPathEffect(null);line.setAlpha(255);}
    float size=Math.min(Math.min(w,h)*.44f,130*density),breathe=.88f+.12f*(float)Math.sin(sec*1.3f);
    corner(canvas,cs,0,0,0,size,false,false,breathe);corner(canvas,cs,0,w,0,size,true,false,breathe);corner(canvas,cs,0,0,h,size,false,true,breathe);corner(canvas,cs,0,w,h,size,true,true,breathe);
    if(crest!=null&&w>size*2.6f){float cw=Math.min(150*density,w*.3f),ch=cw*crest.h/crest.w;draw(canvas,crest,0,w/2,ch/2+2*density,cw,ch,0,.9f+.1f*(float)Math.sin(sec*1.7f),false,false);}
    return true;}
   case "neon":{float pulse=.75f+.25f*(float)Math.sin(sec*2.4)*(Math.sin(sec*13)>.96?.3f:1f);
    int[] loop={colors[0],colors[Math.min(1,colors.length-1)],colors[colors.length-1],colors[0]};SweepGradient sweep=new SweepGradient(w/2,h/2,loop,null);Matrix m=new Matrix();m.setRotate(sec*30%360,w/2,h/2);sweep.setLocalMatrix(m);
    float[][] passes={{16,.10f},{10,.18f},{6,.35f},{3,.8f}};for(float[] pass:passes){line.setStrokeWidth(pass[0]*density);line.setShader(sweep);line.setAlpha(Math.round(255*pass[1]*pulse));canvas.drawRoundRect(box,radius,radius,line);}
    line.setShader(null);line.setStrokeWidth(1.2f*density);line.setColor(0xFFFFFFFF);line.setAlpha(Math.round(220*pulse));RectF inner=new RectF(box);canvas.drawRoundRect(inner,radius,radius,line);return true;}
   default:return false;
  }
 }
}
