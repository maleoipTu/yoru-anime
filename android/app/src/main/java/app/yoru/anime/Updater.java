package app.yoru.anime;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

/**
 * Автообновление: раз в несколько часов читает latest.json из релиза nightly на GitHub.
 * Если версия новее — предлагает обновиться, скачивает APK, сверяет SHA-256 и ставит через PackageInstaller.
 */
final class Updater {
 static final String FEED="https://github.com/maleoipTu/yoru-anime/releases/download/nightly/latest.json";
 static final String PAGE="https://github.com/maleoipTu/yoru-anime/releases/tag/nightly";

 /** Сравнение по номеру сборки: обновляться, только если номер больше текущего. */
 static boolean newer(int remoteCode,int localCode){return remoteCode>localCode;}

 static void check(Activity activity,boolean force){
  android.content.SharedPreferences prefs=activity.getSharedPreferences("yoru_update",Context.MODE_PRIVATE);
  long now=System.currentTimeMillis();if(!force&&now-prefs.getLong("checked",0)<6*3600_000L)return;prefs.edit().putLong("checked",now).apply();
  Api.IO.execute(()->{try{
   JSONObject feed=new JSONObject(new String(Media.download(FEED,64*1024),"UTF-8"));JSONObject android=feed.optJSONObject("android");if(android==null)return;
   int code=android.optInt("version_code");if(!newer(code,BuildConfig.VERSION_CODE)){if(force)activity.runOnUiThread(()->Toast.makeText(activity,"У тебя последняя версия",Toast.LENGTH_SHORT).show());return;}
   if(!force&&prefs.getInt("skipped",0)==code)return;
   activity.runOnUiThread(()->{if(!activity.isDestroyed())offer(activity,android,prefs);});
  }catch(Exception e){if(force)activity.runOnUiThread(()->Toast.makeText(activity,"Не удалось проверить обновления",Toast.LENGTH_SHORT).show());}});
 }

 static void offer(Activity activity,JSONObject android,android.content.SharedPreferences prefs){
  LinearLayout panel=Ui.column(activity);boolean signed=android.optBoolean("signed");
  Ui.add(panel,Ui.text(activity,"Вышла сборка "+android.optString("version_name")+" (сейчас "+BuildConfig.VERSION_NAME+").",15,Ui.TEXT),4);
  Ui.add(panel,Ui.text(activity,signed?"Обновление скачается и установится поверх — данные сохранятся.":"Эта сборка подписана временным ключом, поэтому поверх не встанет: скачай её со страницы релиза и переустанови приложение.",13,Ui.MUTED),8);
  Button go=Ui.button(activity,signed?"Обновить":"Открыть страницу загрузки",true);Ui.add(panel,go,16);
  Button later=Ui.textButton(activity,"Пропустить эту версию");Ui.add(panel,later,4);
  Dialog dialog=Ui.sheet(activity,"Доступно обновление",panel,"Закрыть");
  later.setOnClickListener(v->{prefs.edit().putInt("skipped",android.optInt("version_code")).apply();dialog.dismiss();});
  go.setOnClickListener(v->{dialog.dismiss();if(signed)download(activity,android);else open(activity,PAGE);});
 }
 static void open(Activity activity,String url){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(activity,"Не найден браузер",Toast.LENGTH_SHORT).show();}}

 static void download(Activity activity,JSONObject android){
  if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
   Toast.makeText(activity,"Разреши Yoru устанавливать обновления и нажми «Обновить» ещё раз",Toast.LENGTH_LONG).show();
   try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException ignored){}
   return;
  }
  Toast.makeText(activity,"Скачиваем обновление…",Toast.LENGTH_SHORT).show();
  Api.IO.execute(()->{try{
   File dir=new File(activity.getCacheDir(),"updates");dir.mkdirs();File apk=new File(dir,"yoru.apk");
   HttpURLConnection c=(HttpURLConnection)new URL(android.getString("url")).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(apk)){byte[] buf=new byte[65536];int n;long total=0;while((n=in.read(buf))!=-1){total+=n;if(total>200L*1024*1024)throw new IOException("too large");sha.update(buf,0,n);out.write(buf,0,n);}}finally{c.disconnect();}
   StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b));
   if(!hex.toString().equalsIgnoreCase(android.optString("sha256")))throw new IOException("checksum");
   install(activity,apk);
  }catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"Не получилось скачать обновление. Попробуй позже.",Toast.LENGTH_LONG).show());}});
 }

 static void install(Activity activity,File apk) throws IOException {
  PackageInstaller installer=activity.getPackageManager().getPackageInstaller();
  PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(activity.getPackageName());
  int id=installer.createSession(params);
  try(PackageInstaller.Session session=installer.openSession(id)){
   try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("yoru.apk",0,apk.length())){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);session.fsync(out);}
   Intent status=new Intent(activity,UpdateReceiver.class);
   PendingIntent pending=PendingIntent.getBroadcast(activity,id,status,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
   session.commit(pending.getIntentSender());
  }
 }

 /** Получает статус установки: показывает системное подтверждение или сообщает об ошибке. */
 public static final class UpdateReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent){
   int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
   if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class):legacy(intent);if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirm);}}
   else if(status!=PackageInstaller.STATUS_SUCCESS)Toast.makeText(context,"Обновление не установлено: "+intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),Toast.LENGTH_LONG).show();
  }
  @SuppressWarnings("deprecation") static Intent legacy(Intent intent){return intent.getParcelableExtra(Intent.EXTRA_INTENT);}
 }
}
