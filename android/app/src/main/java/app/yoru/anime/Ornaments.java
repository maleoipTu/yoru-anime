package app.yoru.anime;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import java.util.Random;

/**
 * Украшения в духе Discord, нарисованные кодом: украшение аватара, эффект профиля и рамка профиля.
 * Все рисунки — собственные, по style.type из таблицы decorations. Если в стиле есть image_url
 * (своя картинка из админки), рисуется она — растянутой на всю область, анимированные GIF/WebP играют.
 */
final class Ornaments {
 private Ornaments(){}
 static final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
 static{stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);text.setTextAlign(Paint.Align.CENTER);}
 static int c(int[] colors,int i){return colors[Math.min(i,colors.length-1)];}
 static int alpha(int color,float a){return (color&0x00FFFFFF)|(Math.round(Math.max(0,Math.min(1,a))*((color>>>24)&0xFF))<<24);}
 static float wave(float t,float phase){return (float)Math.sin((t+phase)*Math.PI*2);}

 // ───────── Украшение аватара ─────────
 /** cx, cy — центр аватара, r — радиус аватара, t — фаза анимации 0…1. Рисунок выходит за круг аватара примерно на 25%. */
 static void avatar(Canvas canvas,String type,String glyph,int[] colors,float cx,float cy,float r,float t){
  fill.setShader(null);stroke.setShader(null);fill.setStyle(Paint.Style.FILL);
  switch(type){
   case "cat_ears":{for(int side=-1;side<=1;side+=2){Path ear=new Path();float bx=cx+side*r*.62f,by=cy-r*.62f;ear.moveTo(bx-side*r*.42f,by+r*.06f);ear.lineTo(bx+side*r*.30f,by-r*.62f);ear.lineTo(bx+side*r*.30f,by+r*.30f);ear.close();fill.setColor(c(colors,0));canvas.drawPath(ear,fill);Path inner=new Path();inner.moveTo(bx-side*r*.18f,by+r*.02f);inner.lineTo(bx+side*r*.20f,by-r*.38f);inner.lineTo(bx+side*r*.20f,by+r*.14f);inner.close();fill.setColor(c(colors,1));canvas.drawPath(inner,fill);}break;}
   case "bunny":{for(int side=-1;side<=1;side+=2){canvas.save();canvas.rotate(side*(14+3*wave(t,side*.25f)),cx+side*r*.35f,cy-r*.8f);RectF ear=new RectF(cx+side*r*.35f-r*.2f,cy-r*1.38f,cx+side*r*.35f+r*.2f,cy-r*.55f);fill.setColor(c(colors,0));canvas.drawOval(ear,fill);ear.inset(r*.08f,r*.14f);fill.setColor(c(colors,1));canvas.drawOval(ear,fill);canvas.restore();}break;}
   case "halo":{float bob=r*.05f*wave(t,0);RectF ring=new RectF(cx-r*.62f,cy-r*1.28f+bob,cx+r*.62f,cy-r*1.0f+bob);stroke.setStrokeWidth(r*.22f);stroke.setColor(alpha(c(colors,1),.35f));canvas.drawOval(ring,stroke);stroke.setStrokeWidth(r*.09f);stroke.setColor(c(colors,0));canvas.drawOval(ring,stroke);break;}
   case "horns":{for(int side=-1;side<=1;side+=2){Path horn=new Path();float bx=cx+side*r*.48f,by=cy-r*.72f;horn.moveTo(bx-side*r*.18f,by+r*.1f);horn.quadTo(bx+side*r*.1f,by-r*.25f,bx+side*r*.42f,by-r*.55f);horn.quadTo(bx+side*r*.2f,by-r*.05f,bx+side*r*.2f,by+r*.2f);horn.close();fill.setShader(new LinearGradient(bx,by+r*.2f,bx+side*r*.42f,by-r*.55f,c(colors,1),c(colors,0),Shader.TileMode.CLAMP));canvas.drawPath(horn,fill);fill.setShader(null);}break;}
   case "headphones":{stroke.setStrokeWidth(r*.16f);stroke.setColor(c(colors,0));canvas.drawArc(new RectF(cx-r*1.08f,cy-r*1.12f,cx+r*1.08f,cy+r*1.0f),198,144,false,stroke);for(int side=-1;side<=1;side+=2){RectF cup=new RectF(cx+side*r*1.0f-r*.2f,cy-r*.32f,cx+side*r*1.0f+r*.2f,cy+r*.38f);fill.setColor(c(colors,0));canvas.drawRoundRect(cup,r*.16f,r*.16f,fill);cup.inset(r*.07f,r*.12f);fill.setColor(c(colors,1));canvas.drawRoundRect(cup,r*.08f,r*.08f,fill);}break;}
   case "crown":{canvas.save();canvas.rotate(-12,cx,cy);Path crown=new Path();float top=cy-r*1.32f,base=cy-r*.84f,w=r*.62f;crown.moveTo(cx-w,base);crown.lineTo(cx-w,top+r*.12f);crown.lineTo(cx-w*.5f,top+r*.3f);crown.lineTo(cx,top);crown.lineTo(cx+w*.5f,top+r*.3f);crown.lineTo(cx+w,top+r*.12f);crown.lineTo(cx+w,base);crown.close();fill.setShader(new LinearGradient(cx,top,cx,base,c(colors,0),c(colors,1),Shader.TileMode.CLAMP));canvas.drawPath(crown,fill);fill.setShader(null);fill.setColor(c(colors,2));canvas.drawCircle(cx,base-r*.14f,r*.08f,fill);canvas.drawCircle(cx-w*.6f,base-r*.12f,r*.055f,fill);canvas.drawCircle(cx+w*.6f,base-r*.12f,r*.055f,fill);float glint=.5f+.5f*wave(t,0);fill.setColor(alpha(0xFFFFFFFF,glint));canvas.drawCircle(cx,top+r*.02f,r*.05f,fill);canvas.restore();break;}
   case "orbit":{if(glyph==null||glyph.isEmpty())glyph="★";int n=6;text.setTextSize(r*.32f);for(int i=0;i<n;i++){double a=Math.PI*2*(i/(double)n+t);float x=cx+(float)Math.cos(a)*r*1.12f,y=cy+(float)Math.sin(a)*r*1.12f;text.setColor(c(colors,i%2));text.setAlpha(Math.round(255*(.65f+.35f*wave(t,i/(float)n))));canvas.drawText(glyph,x,y+r*.11f,text);}break;}
   case "sakura_wreath":{for(int i=0;i<7;i++){double a=Math.toRadians(100+i*26);float x=cx+(float)Math.cos(a)*r*1.0f,y=cy+(float)Math.sin(a)*r*1.0f;if(i%2==1){fill.setColor(0xFF6FBF73);canvas.save();canvas.rotate((float)Math.toDegrees(a),x,y);canvas.drawOval(new RectF(x-r*.16f,y-r*.06f,x+r*.16f,y+r*.06f),fill);canvas.restore();continue;}flower(canvas,x,y,r*(i==2||i==4?.22f:.18f),t*72+i*20,colors);}break;}
   case "pumpkin":{canvas.save();canvas.rotate(14,cx,cy);float px=cx+r*.25f,py=cy-r*1.0f,pw=r*.5f,ph=r*.36f;fill.setColor(c(colors,2));canvas.drawRoundRect(new RectF(px-pw*1.25f,py+ph*.55f,px+pw*1.25f,py+ph*.85f),ph*.2f,ph*.2f,fill);for(int i=-1;i<=1;i++){fill.setColor(i==0?c(colors,0):darker(c(colors,0)));canvas.drawOval(new RectF(px+i*pw*.42f-pw*.55f,py-ph*.5f,px+i*pw*.42f+pw*.55f,py+ph*.62f),fill);}fill.setColor(c(colors,1));canvas.drawRoundRect(new RectF(px-r*.05f,py-ph*.85f,px+r*.05f,py-ph*.4f),r*.03f,r*.03f,fill);fill.setColor(alpha(0xFFFFE08A,.6f+.4f*wave(t,0)));Path eye=new Path();eye.moveTo(px-pw*.4f,py);eye.lineTo(px-pw*.2f,py-ph*.22f);eye.lineTo(px-pw*.05f,py);eye.close();eye.moveTo(px+pw*.05f,py);eye.lineTo(px+pw*.2f,py-ph*.22f);eye.lineTo(px+pw*.4f,py);eye.close();canvas.drawPath(eye,fill);canvas.restore();break;}
   default:break;
  }
 }
 static int darker(int color){return Color.argb(Color.alpha(color),Color.red(color)*4/5,Color.green(color)*4/5,Color.blue(color)*4/5);}
 static void flower(Canvas canvas,float x,float y,float size,float rotate,int[] colors){canvas.save();canvas.rotate(rotate,x,y);fill.setColor(c(colors,0));for(int p=0;p<5;p++){canvas.save();canvas.rotate(p*72,x,y);canvas.drawOval(new RectF(x-size*.32f,y-size,x+size*.32f,y-size*.15f),fill);canvas.restore();}fill.setColor(c(colors,1));canvas.drawCircle(x,y,size*.22f,fill);canvas.restore();}
 static boolean animatedAvatar(String type){return type.equals("halo")||type.equals("orbit")||type.equals("sakura_wreath")||type.equals("crown")||type.equals("bunny")||type.equals("pumpkin");}

 // ───────── Эффект профиля ─────────
 /** Частицы поверх карточки w×h; seconds — время в секундах. */
 static void effect(Canvas canvas,String type,int[] colors,float w,float h,float seconds,float density){
  Random random=new Random(type.hashCode());fill.setShader(null);fill.setStyle(Paint.Style.FILL);
  int count=type.equals("snow")?46:type.equals("starfall")?7:type.equals("bats")?5:type.equals("fireflies")?18:24;
  for(int i=0;i<count;i++){
   float rx=random.nextFloat(),ry=random.nextFloat(),speed=.5f+random.nextFloat(),phase=random.nextFloat(),size=(.6f+random.nextFloat()*.8f)*density;int color=c(colors,i%colors.length);
   switch(type){
    case "sparkles":{float life=(seconds*.45f*speed+phase)%1f,a=(float)Math.sin(life*Math.PI);star4(canvas,rx*w,ry*h,size*(3+5*a),alpha(color,a));break;}
    case "bubbles":{float p=(seconds*.06f*speed+phase)%1f,y=h*(1.05f-p*1.15f),x=rx*w+(float)Math.sin((seconds*speed+phase*6))*10*density;stroke.setStrokeWidth(density*1.2f);stroke.setColor(alpha(color,.7f*(1-p*.6f)));canvas.drawCircle(x,y,size*(4+4*rx),stroke);fill.setColor(alpha(0xFFFFFFFF,.5f*(1-p)));canvas.drawCircle(x-size*2,y-size*2,size*1.1f,fill);break;}
    case "petals":case "snow":{float p=(seconds*(type.equals("snow")?.07f:.05f)*speed+phase)%1f,y=h*(p*1.15f-.08f),x=rx*w+(float)Math.sin(seconds*.9f*speed+phase*7)*18*density;if(type.equals("snow")){fill.setColor(alpha(color,.85f));canvas.drawCircle(x,y,size*(1.2f+2*ry),fill);}else{canvas.save();canvas.rotate(seconds*60*speed+phase*360,x,y);fill.setColor(alpha(color,.9f));canvas.drawOval(new RectF(x-size*5,y-size*2.6f,x+size*5,y+size*2.6f),fill);canvas.restore();}break;}
    case "fireflies":{float x=rx*w+(float)Math.sin(seconds*.35f*speed+phase*9)*30*density,y=ry*h+(float)Math.cos(seconds*.28f*speed+phase*5)*24*density,a=.35f+.65f*(.5f+.5f*(float)Math.sin(seconds*2.2f*speed+phase*11));fill.setShader(new RadialGradient(x,y,size*9,alpha(color,a),alpha(color,0),Shader.TileMode.CLAMP));canvas.drawCircle(x,y,size*9,fill);fill.setShader(null);fill.setColor(alpha(0xFFFFFFFF,a));canvas.drawCircle(x,y,size*1.4f,fill);break;}
    case "hearts":{float p=(seconds*.08f*speed+phase)%1f,y=h*(1.05f-p*1.1f),x=rx*w+(float)Math.sin(seconds*1.3f+phase*5)*8*density;heart(canvas,x,y,size*(5+3*ry),alpha(color,(float)Math.sin(p*Math.PI)*.9f));break;}
    case "starfall":{float p=(seconds*.18f*speed+phase)%1f;if(p>.55f)break;float q=p/.55f,x=w*(rx*.8f+.25f)-q*w*.5f,y=h*(ry*.4f-.1f)+q*h*.7f,len=40*density*size;stroke.setStrokeWidth(density*1.6f);stroke.setShader(new LinearGradient(x,y,x+len,y-len*.7f,alpha(color,(float)Math.sin(q*Math.PI)),alpha(color,0),Shader.TileMode.CLAMP));canvas.drawLine(x,y,x+len,y-len*.7f,stroke);stroke.setShader(null);star4(canvas,x,y,size*4,alpha(0xFFFFFFFF,(float)Math.sin(q*Math.PI)));break;}
    case "bats":{float p=(seconds*.07f*speed+phase)%1f,x=w*(p*1.3f-.15f),y=h*(.15f+ry*.6f)+(float)Math.sin(p*Math.PI*4)*14*density;bat(canvas,x,y,size*9,(float)Math.sin(seconds*12*speed),c(colors,0),c(colors,1));break;}
    default:break;
   }
  }
 }
 static void star4(Canvas canvas,float x,float y,float s,int color){Path p=new Path();p.moveTo(x,y-s);p.quadTo(x,y,x+s,y);p.quadTo(x,y,x,y+s);p.quadTo(x,y,x-s,y);p.quadTo(x,y,x,y-s);p.close();fill.setColor(color);canvas.drawPath(p,fill);}
 static void heart(Canvas canvas,float x,float y,float s,int color){Path p=new Path();p.moveTo(x,y+s*.9f);p.cubicTo(x-s*1.6f,y-s*.2f,x-s*.7f,y-s*1.3f,x,y-s*.45f);p.cubicTo(x+s*.7f,y-s*1.3f,x+s*1.6f,y-s*.2f,x,y+s*.9f);p.close();fill.setColor(color);canvas.drawPath(p,fill);}
 static void bat(Canvas canvas,float x,float y,float s,float flap,int body,int eye){Path p=new Path();float lift=s*.5f*flap;p.moveTo(x,y);p.quadTo(x-s*.6f,y-s*.5f-lift,x-s*1.4f,y-s*.2f-lift);p.quadTo(x-s*1.0f,y,x-s*1.1f,y+s*.25f);p.quadTo(x-s*.6f,y+s*.05f,x-s*.3f,y+s*.3f);p.quadTo(x,y+s*.1f,x+s*.3f,y+s*.3f);p.quadTo(x+s*.6f,y+s*.05f,x+s*1.1f,y+s*.25f);p.quadTo(x+s*1.0f,y,x+s*1.4f,y-s*.2f-lift);p.quadTo(x+s*.6f,y-s*.5f-lift,x,y);p.close();fill.setColor(body);canvas.drawPath(p,fill);fill.setColor(eye);canvas.drawCircle(x-s*.1f,y+s*.02f,s*.05f,fill);canvas.drawCircle(x+s*.1f,y+s*.02f,s*.05f,fill);}

 // ───────── Рамка профиля ─────────
 /** Орнамент по краям карточки w×h со скруглением radius. */
 static void frame(Canvas canvas,String type,int[] colors,float w,float h,float radius,float seconds,float density){
  fill.setShader(null);stroke.setShader(null);fill.setStyle(Paint.Style.FILL);float inset=density*2;RectF box=new RectF(inset,inset,w-inset,h-inset);
  switch(type){
   case "neon":{float pulse=.6f+.4f*(float)Math.sin(seconds*2.4f);stroke.setShader(new SweepGradient(w/2,h/2,new int[]{c(colors,0),c(colors,1),c(colors,2),c(colors,0)},null));for(int pass=0;pass<3;pass++){stroke.setStrokeWidth(density*(pass==0?10:pass==1?5:2));stroke.setAlpha(Math.round(255*(pass==0?.18f*pulse:pass==1?.45f*pulse:1)));canvas.drawRoundRect(box,radius,radius,stroke);}stroke.setShader(null);stroke.setAlpha(255);break;}
   case "vines":{stroke.setStrokeWidth(density*2.2f);stroke.setColor(c(colors,0));for(int edge=0;edge<4;edge++){boolean vertical=edge%2==1;float len=vertical?h:w;Path p=new Path();int waves=Math.max(3,Math.round(len/(60*density)));for(int i=0;i<=waves*8;i++){float u=i/(float)(waves*8),off=(float)Math.sin(u*waves*Math.PI*2)*5*density+6*density;float x=vertical?(edge==1?w-off:off):u*w,y=vertical?u*h:(edge==0?off:h-off);if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}canvas.drawPath(p,stroke);for(int i=0;i<waves*2;i++){float u=(i+.5f)/(waves*2),off=10*density;float x=vertical?(edge==1?w-off:off):u*w,y=vertical?u*h:(edge==0?off:h-off);canvas.save();canvas.rotate((i%2==0?35:-35)+(vertical?90:0)+(float)Math.sin(seconds+i)*6,x,y);fill.setColor(c(colors,i%2));canvas.drawOval(new RectF(x-6*density,y-2.6f*density,x+6*density,y+2.6f*density),fill);canvas.restore();}}break;}
   case "constellation":{Random random=new Random(7);int n=22;float[] xs=new float[n],ys=new float[n];for(int i=0;i<n;i++){float u=i/(float)n,per=2*(w+h),d=u*per+random.nextFloat()*20*density,off=(4+random.nextFloat()*10)*density;if(d<w){xs[i]=d;ys[i]=off;}else if(d<w+h){xs[i]=w-off;ys[i]=d-w;}else if(d<2*w+h){xs[i]=w-(d-w-h);ys[i]=h-off;}else{xs[i]=off;ys[i]=h-(d-2*w-h);}}stroke.setStrokeWidth(density);stroke.setColor(alpha(c(colors,0),.45f));for(int i=0;i<n;i++)canvas.drawLine(xs[i],ys[i],xs[(i+1)%n],ys[(i+1)%n],stroke);for(int i=0;i<n;i++)star4(canvas,xs[i],ys[i],density*(2.5f+2*(.5f+.5f*(float)Math.sin(seconds*2+i))),c(colors,i%2==0?1:0));break;}
   case "flames":{int n=Math.max(8,Math.round(w/(18*density)));for(int i=0;i<n;i++){float x=(i+.5f)*w/n,flick=(float)Math.sin(seconds*7+i*1.7f)*.5f+.5f,fh=(16+14*flick+(i%3)*6)*density,fw=w/n*.7f;for(int layer=0;layer<3;layer++){float s=1-layer*.3f;Path p=new Path();p.moveTo(x-fw*s,h);p.quadTo(x-fw*s*.6f,h-fh*s*.6f,x+(float)Math.sin(seconds*5+i)*3*density,h-fh*s);p.quadTo(x+fw*s*.6f,h-fh*s*.6f,x+fw*s,h);p.close();fill.setColor(alpha(c(colors,2-layer),layer==0?.85f:1));canvas.drawPath(p,fill);}}stroke.setStrokeWidth(density*1.5f);stroke.setShader(new LinearGradient(0,0,0,h,alpha(c(colors,0),.2f),c(colors,2),Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,stroke);stroke.setShader(null);break;}
   case "crystal":{stroke.setStrokeWidth(density*1.5f);stroke.setColor(alpha(c(colors,1),.5f));canvas.drawRoundRect(box,radius,radius,stroke);float[][] corners={{0,0,1,1},{w,0,-1,1},{0,h,1,-1},{w,h,-1,-1}};for(float[] k:corners)for(int j=0;j<3;j++){float len=(26-j*7)*density,wid=(7-j*1.5f)*density,ang=(float)(Math.PI/4+(j-1)*.45f);float dx=(float)Math.cos(ang)*k[2],dy=(float)Math.sin(ang)*k[3],bx=k[0]+k[2]*4*density,by=k[1]+k[3]*4*density;Path p=new Path();p.moveTo(bx,by);p.lineTo(bx+dx*len*.5f-dy*wid,by+dy*len*.5f+dx*wid);p.lineTo(bx+dx*len,by+dy*len);p.lineTo(bx+dx*len*.5f+dy*wid,by+dy*len*.5f-dx*wid);p.close();fill.setShader(new LinearGradient(bx,by,bx+dx*len,by+dy*len,c(colors,j%2),c(colors,2),Shader.TileMode.CLAMP));canvas.drawPath(p,fill);fill.setShader(null);}float glint=(seconds*.25f)%1f;fill.setColor(alpha(0xFFFFFFFF,(float)Math.sin(glint*Math.PI)));canvas.drawCircle(18*density,18*density,2.5f*density,fill);break;}
   case "moon":{float mx=w-34*density,my=34*density,mr=18*density;fill.setShader(new RadialGradient(mx,my,mr*2.4f,alpha(c(colors,0),.35f),alpha(c(colors,0),0),Shader.TileMode.CLAMP));canvas.drawCircle(mx,my,mr*2.4f,fill);fill.setShader(null);Path moon=new Path();moon.addCircle(mx,my,mr,Path.Direction.CW);Path bite=new Path();bite.addCircle(mx+mr*.55f,my-mr*.35f,mr*.85f,Path.Direction.CW);moon.op(bite,Path.Op.DIFFERENCE);fill.setColor(c(colors,0));canvas.drawPath(moon,fill);Random random=new Random(3);for(int i=0;i<9;i++){float sx=w-random.nextFloat()*w*.45f,sy=random.nextFloat()*h*.35f;star4(canvas,sx,sy,density*(1.5f+2*(.5f+.5f*(float)Math.sin(seconds*1.7f+i))),alpha(i%2==0?0xFFFFFFFF:c(colors,1),.9f));}stroke.setStrokeWidth(density*1.5f);stroke.setShader(new LinearGradient(w,0,0,h,c(colors,1),alpha(c(colors,1),.1f),Shader.TileMode.CLAMP));canvas.drawRoundRect(box,radius,radius,stroke);stroke.setShader(null);break;}
   case "cobweb":{stroke.setStrokeWidth(density*.9f);stroke.setColor(alpha(c(colors,0),.75f));for(int corner=0;corner<2;corner++){float ox=corner==0?0:w,oy=corner==0?0:h,sx=corner==0?1:-1,sy=corner==0?1:-1,size=Math.min(w,h)*.42f;for(int i=0;i<=5;i++){double a=Math.PI/2*i/5;canvas.drawLine(ox,oy,ox+sx*(float)Math.cos(a)*size,oy+sy*(float)Math.sin(a)*size,stroke);}for(int ring=1;ring<=4;ring++){Path p=new Path();float rr=size*ring/4.6f;for(int i=0;i<=5;i++){double a=Math.PI/2*i/5;float x=ox+sx*(float)Math.cos(a)*rr,y=oy+sy*(float)Math.sin(a)*rr;if(i==0)p.moveTo(x,y);else{double pa=Math.PI/2*(i-.5)/5;p.quadTo(ox+sx*(float)Math.cos(pa)*rr*.86f,oy+sy*(float)Math.sin(pa)*rr*.86f,x,y);}}canvas.drawPath(p,stroke);}}float drop=(float)(.5+.5*Math.sin(seconds*1.2))*Math.min(w,h)*.25f+20*density;canvas.drawLine(w*.3f,0,w*.3f,drop,stroke);fill.setColor(c(colors,1));canvas.drawCircle(w*.3f,drop,4*density,fill);break;}
   default:break;
  }
 }

 /** Слой поверх карточки профиля: рамка и эффект. Клики проходят сквозь него. */
 static final class Overlay extends View {
  String effectType="",frameType="";int[] effectColors={0xFFFFFFFF},frameColors={Ui.ACCENT};Drawable effectImage,frameImage;String effectUrl="",frameUrl="";float radius;ValueAnimator clock;
  Overlay(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);radius=Ui.dp(c,24);}
  void set(JSONObject effect,JSONObject frame){
   effectType=effect==null?"":effect.optString("type");effectColors=Decor.colors(effect==null?new JSONObject():effect,0xFFFFFFFF);
   frameType=frame==null?"":frame.optString("type");frameColors=Decor.colors(frame==null?new JSONObject():frame,Ui.ACCENT);
   String eu=effect==null?"":effect.optString("image_url"),fu=frame==null?"":frame.optString("image_url");
   if(!eu.equals(effectUrl)){effectUrl=eu;effectImage=null;load(eu,true);}
   if(!fu.equals(frameUrl)){frameUrl=fu;frameImage=null;load(fu,false);}
   restart();invalidate();
  }
  void load(String url,boolean effect){if(!url.startsWith("https://")||!(getContext() instanceof Activity))return;Media.drawable((Activity)getContext(),url,1200,d->{if(effect&&url.equals(effectUrl)){effectImage=d;d.setCallback(this);}else if(!effect&&url.equals(frameUrl)){frameImage=d;d.setCallback(this);}invalidate();});}
  @Override protected boolean verifyDrawable(Drawable who){return who==effectImage||who==frameImage||super.verifyDrawable(who);}
  boolean active(){return !effectType.isEmpty()||!frameType.isEmpty()||effectImage!=null||frameImage!=null;}
  void restart(){boolean run=active()&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();if(run&&clock==null){clock=ValueAnimator.ofFloat(0,1);clock.setDuration(1000);clock.setRepeatCount(ValueAnimator.INFINITE);clock.addUpdateListener(a->invalidate());clock.start();}else if(!run&&clock!=null){clock.cancel();clock=null;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restart();}
  @Override protected void onDetachedFromWindow(){if(clock!=null){clock.cancel();clock=null;}super.onDetachedFromWindow();}
  @Override protected void onDraw(Canvas canvas){
   float seconds=SystemClock.uptimeMillis()/1000f,density=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
   if(effectImage!=null){effectImage.setBounds(0,0,getWidth(),getHeight());effectImage.draw(canvas);}else if(!effectType.isEmpty())effect(canvas,effectType,effectColors,w,h,seconds,density);
   if(frameImage!=null){frameImage.setBounds(0,0,getWidth(),getHeight());frameImage.draw(canvas);}else if(!frameType.isEmpty())frame(canvas,frameType,frameColors,w,h,radius,seconds,density);
  }
 }

 /** Квадратное превью украшения для магазина: эффект или рамка на маленькой карточке. */
 static final class Preview extends View {
  final Overlay painter;String kind;
  Preview(Context c,String kind,JSONObject style){super(c);this.kind=kind;painter=new Overlay(c);painter.radius=Ui.dp(c,14);if(kind.equals("profile_effect"))painter.set(style,null);else painter.set(null,style);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  ValueAnimator clock;
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(ValueAnimator.areAnimatorsEnabled()){clock=ValueAnimator.ofFloat(0,1);clock.setDuration(1000);clock.setRepeatCount(ValueAnimator.INFINITE);clock.addUpdateListener(a->invalidate());clock.start();}}
  @Override protected void onDetachedFromWindow(){if(clock!=null)clock.cancel();clock=null;super.onDetachedFromWindow();}
  @Override protected void onDraw(Canvas canvas){Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setShader(new LinearGradient(0,0,0,getHeight(),0xFF2A2A35,0xFF16161B,Shader.TileMode.CLAMP));float r=Ui.dp(getContext(),14);canvas.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),r,r,bg);painter.layout(0,0,getWidth(),getHeight());painter.draw(canvas);}
 }
}
