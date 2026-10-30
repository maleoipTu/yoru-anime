package app.yoru.anime;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.util.*;

/**
 * Экран входа и регистрации. Пока пользователь не вошёл, приложение дальше не пускает.
 * Фон — наклонная мозаика популярных постеров с коралловым свечением.
 */
public final class AuthActivity extends Activity {
 Account account;boolean registering,busy;
 LinearLayout card;EditText email,nick,password;TextView message,title,subtitle;Button submit,signIn,signUp,forgot;ProgressBar spinner;GridLayout mosaic;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);account=new Account(this);
  if(saved!=null)registering=saved.getBoolean("registering");
  getWindow().setStatusBarColor(Color.TRANSPARENT);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
  // Мозаика постеров, повёрнутая и растворяющаяся книзу.
  mosaic=new GridLayout(this);mosaic.setColumnCount(4);mosaic.setRotation(-10);mosaic.setAlpha(.38f);
  FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);mp.topMargin=-Ui.dp(this,80);root.addView(mosaic,mp);
  View fade=new View(this);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x330E0E11,0xCC0E0E11,Ui.BG,Ui.BG}));root.addView(fade,new FrameLayout.LayoutParams(-1,-1));
  View glow=new View(this);glow.setBackground(new Glow());root.addView(glow,new FrameLayout.LayoutParams(-1,-1));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  if(android.os.Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  LinearLayout page=Ui.column(this);page.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);page.setPadding(Ui.dp(this,20),Ui.dp(this,40),Ui.dp(this,20),Ui.dp(this,28));scroll.addView(page,new FrameLayout.LayoutParams(-1,-2));
  ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,84),Ui.dp(this,84));lp.topMargin=Ui.dp(this,150);page.addView(logo,lp);
  title=Ui.title(this,"",30);title.setGravity(Gravity.CENTER);if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);Ui.add(page,title,14);
  subtitle=Ui.text(this,"",15,Ui.MUTED);subtitle.setGravity(Gravity.CENTER);Ui.add(page,subtitle,8);
  // Карточка с формой.
  card=Ui.padded(this,20);card.setBackground(Ui.outlined(this,Ui.PANEL,28));Ui.lift(card,16);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=Ui.dp(this,26);cp.width=Math.min(getResources().getDisplayMetrics().widthPixels-Ui.dp(this,40),Ui.dp(this,460));page.addView(card,cp);
  LinearLayout tabs=Ui.row(this);tabs.setBackground(Ui.shape(this,Ui.RAISED,16));tabs.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));
  signIn=tab("Вход");signUp=tab("Регистрация");tabs.addView(signIn,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));tabs.addView(signUp,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));card.addView(tabs);
  signIn.setOnClickListener(v->mode(false));signUp.setOnClickListener(v->mode(true));
  email=input("Почта","mail",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);email.setText(account.email());Ui.add(card,(View)email.getParent(),16);
  nick=input("Ник: латиница, цифры и _","user",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);nick.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});Ui.add(card,(View)nick.getParent(),10);
  password=input("Пароль","lock",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);Ui.add(card,(View)password.getParent(),10);
  password.setImeOptions(EditorInfo.IME_ACTION_DONE);password.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit();return true;}return false;});
  Button eye=Ui.iconButton(this,"eye","Показать пароль");Ui.icon(eye,"eye",Ui.MUTED);eye.setOnClickListener(v->{boolean hidden=(password.getInputType()&InputType.TYPE_TEXT_VARIATION_PASSWORD)==InputType.TYPE_TEXT_VARIATION_PASSWORD;int sel=password.getSelectionEnd();password.setInputType(InputType.TYPE_CLASS_TEXT|(hidden?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));password.setSelection(Math.max(0,sel));eye.setContentDescription(hidden?"Скрыть пароль":"Показать пароль");});((LinearLayout)password.getParent()).addView(eye);
  message=Ui.text(this,"",13,0xFFFF8A8A);message.setVisibility(View.GONE);Ui.add(card,message,12);
  FrameLayout action=new FrameLayout(this);submit=Ui.button(this,"",true);submit.setMinHeight(Ui.dp(this,54));submit.setMinimumHeight(Ui.dp(this,54));submit.setTextSize(16);action.addView(submit,new FrameLayout.LayoutParams(-1,-2));
  spinner=new ProgressBar(this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));spinner.setVisibility(View.GONE);action.addView(spinner,new FrameLayout.LayoutParams(Ui.dp(this,26),Ui.dp(this,26),Gravity.CENTER));Ui.add(card,action,18);
  submit.setOnClickListener(v->submit());
  forgot=Ui.textButton(this,"Забыли пароль?");forgot.setOnClickListener(v->recover());Ui.add(card,forgot,6);
  TextView note=Ui.text(this,"Профиль, очки, списки и украшения хранятся в аккаунте Yoru и синхронизируются между телефоном и ПК.",12,Ui.MUTED);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(cp.width,-2);np.topMargin=Ui.dp(this,18);page.addView(note,np);
  mode(registering);loadMosaic();
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("registering",registering);super.onSaveInstanceState(state);}

 Button tab(String text){Button b=Ui.button(this,text,false);b.setTextSize(14);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);return b;}
 EditText input(String hint,String icon,int type){
  LinearLayout box=Ui.row(this);box.setBackground(Ui.outlined(this,Ui.RAISED,16));box.setPadding(Ui.dp(this,14),0,Ui.dp(this,4),0);box.setMinimumHeight(Ui.dp(this,54));
  ImageView i=new ImageView(this);i.setImageDrawable(new Ui.AppIcon(this,icon,Ui.MUTED,20));i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(i,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));
  EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED);e.setTextColor(Ui.TEXT);e.setTextSize(15);e.setSingleLine(true);e.setInputType(type);e.setBackgroundColor(Color.TRANSPARENT);e.setPadding(Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14));e.setContentDescription(hint);
  box.addView(e,new LinearLayout.LayoutParams(0,-2,1));return e;
 }
 void mode(boolean register){
  registering=register;title.setText(register?"Создай аккаунт":"С возвращением");
  subtitle.setText(register?"Получай очки за серии, открывай достижения и укрась профиль.":"Твои списки, друзья и очки ждут.");
  for(Button b:new Button[]{signIn,signUp}){boolean on=(b==signUp)==register;b.setBackground(on?Ui.accent(this,12):Ui.shape(this,Color.TRANSPARENT,12));b.setTextColor(on?Color.WHITE:Ui.MUTED);b.setContentDescription(b.getText()+(on?", выбрано":""));}
  ((View)nick.getParent()).setVisibility(register?View.VISIBLE:View.GONE);
  submit.setText(register?"Создать аккаунт":"Войти");forgot.setVisibility(register?View.GONE:View.VISIBLE);
  message.setVisibility(View.GONE);
 }
 void show(String text,boolean good){message.setTextColor(good?Ui.LIVE:0xFFFF8A8A);message.setText(text);message.setVisibility(View.VISIBLE);}
 void setBusy(boolean value){busy=value;submit.setText(value?"":registering?"Создать аккаунт":"Войти");submit.setEnabled(!value);spinner.setVisibility(value?View.VISIBLE:View.GONE);}
 void submit(){
  if(busy)return;
  String mail=email.getText().toString().trim(),pass=password.getText().toString(),name=nick.getText().toString().trim().toLowerCase(Locale.ROOT);
  String problem=!Rewards.validEmail(mail)?"Проверь адрес почты.":pass.length()<6?"Пароль — минимум 6 символов.":registering&&!Rewards.validUsername(name)?"Ник: 3–20 символов, латиница, цифры и _.":"";
  if(!problem.isEmpty()){show(problem,false);return;}
  android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null&&getCurrentFocus()!=null)keyboard.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);
  setBusy(true);boolean register=registering;
  Api.IO.execute(()->{try{
    boolean signedIn;
    if(register){if(!"true".equals(Account.call("POST","/rest/v1/rpc/username_available",new JSONObject().put("p_username",name).toString(),null,null).trim()))throw new Account.Failure(409,"username_taken");signedIn=account.signUp(mail,pass,name);}
    else{account.signIn(mail,pass);signedIn=true;}
    if(signedIn){account.profile();}
    boolean done=signedIn;
    runOnUiThread(()->{if(isDestroyed())return;setBusy(false);if(done)enter();else{mode(false);show("Мы отправили письмо на "+mail+". Подтверди почту и войди.",true);}});
   }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;setBusy(false);show(Rewards.error(e.getMessage()),false);});}});
 }
 void recover(){
  String mail=email.getText().toString().trim();if(!Rewards.validEmail(mail)){show("Введи почту — пришлём ссылку для сброса пароля.",false);return;}
  setBusy(true);Api.IO.execute(()->{try{account.recover(mail);runOnUiThread(()->{if(isDestroyed())return;setBusy(false);show("Ссылка для сброса пароля отправлена на "+mail+".",true);});}
   catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;setBusy(false);show(Rewards.error(e.getMessage()),false);});}});
 }
 void enter(){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}

 /** Популярные постеры для фоновой мозаики; без сети фон просто остаётся градиентным. */
 void loadMosaic(){
  int width=getResources().getDisplayMetrics().widthPixels,cell=(int)(width/3.2f),gap=Ui.dp(this,8);
  Api.IO.execute(()->{try{List<Anime> list=Api.catalog(Api.get("anime?limit=16&sort=views&sort_forward=false&offset="+new Random().nextInt(5)*16));
   runOnUiThread(()->{if(isDestroyed())return;for(Anime a:list){ImageView v=new ImageView(this);Ui.round(v,14);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=cell;p.height=(int)(cell*1.42f);p.setMargins(gap,gap,gap,gap);mosaic.addView(v,p);Ui.poster(this,v,a.poster);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}});
  }catch(Exception ignored){}});
 }

 /** Мягкое коралловое и оранжевое свечение по углам. */
 static final class Glow extends Drawable {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  @Override public void draw(Canvas canvas){Rect b=getBounds();float r=Math.max(b.width(),b.height())*.55f;
   paint.setShader(new RadialGradient(b.width()*.1f,b.height()*.42f,r,new int[]{0x55FF4D67,0x00FF4D67},null,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);
   paint.setShader(new RadialGradient(b.width()*.95f,b.height()*.75f,r*.8f,new int[]{0x40FF8A3D,0x00FF8A3D},null,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);}
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
