package app.yoru.anime;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.util.*;

/**
 * Уведомления о новых сериях: раз в несколько часов проверяет аниме из списка «Смотрю» и избранного.
 * Первая проверка только запоминает число серий, уведомление приходит, когда серий стало больше.
 */
public final class EpisodeWatcher extends JobService {
 static final int JOB=7301;static final String CHANNEL="episodes";
 static void schedule(Context c){
  JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
  for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==JOB)return;
  scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,EpisodeWatcher.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(3*60*60*1000L).setPersisted(false).build());
 }
 static boolean allowed(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED;}
 static void ask(Activity a){
  SharedPreferences prefs=a.getSharedPreferences("yoru",MODE_PRIVATE);
  if(Build.VERSION.SDK_INT>=33&&!allowed(a)&&!prefs.getBoolean("asked_notifications",false)){prefs.edit().putBoolean("asked_notifications",true).apply();a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77);}
 }

 Thread worker;
 @Override public boolean onStartJob(JobParameters params){
  worker=new Thread(()->{try{check(this);}catch(Exception ignored){}jobFinished(params,false);});worker.start();return true;
 }
 @Override public boolean onStopJob(JobParameters params){if(worker!=null)worker.interrupt();return true;}

 static void check(Context c) throws Exception {
  SharedPreferences prefs=c.getSharedPreferences("episode_watch",MODE_PRIVATE);
  Map<Long,Anime> follow=new LinkedHashMap<>();
  Account account=new Account(c);if(account.signedIn())for(Anime a:account.list("watching"))follow.put(a.id,a);
  for(Anime a:new Library(c).favorites())follow.putIfAbsent(a.id,a);
  int checked=0;SharedPreferences.Editor edit=prefs.edit();
  for(Anime item:follow.values()){if(checked++>=40||Thread.interrupted())break;
   JSONObject raw;try{raw=Api.get("anime/"+item.id).getJSONObject("response");}catch(Exception e){continue;}
   JSONObject eps=raw.optJSONObject("episodes");if(eps==null)continue;int aired=eps.optInt("aired",eps.optInt("count"));if(aired<=0)continue;
   String key="aired_"+item.id;int known=prefs.getInt(key,-1);edit.putInt(key,aired);
   if(known>0&&aired>known)notify(c,new Anime(raw),aired);
   Thread.sleep(400);}
  edit.apply();
 }
 static void notify(Context c,Anime anime,int episode){
  if(!allowed(c))return;NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
  manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Новые серии",NotificationManager.IMPORTANCE_DEFAULT));
  Intent open=new Intent(c,DetailActivity.class).putExtra("anime",anime.raw.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  PendingIntent tap=PendingIntent.getActivity(c,(int)(anime.id%100000),open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notify).setContentTitle("Вышла "+episode+"-я серия").setContentText(anime.title).setAutoCancel(true).setContentIntent(tap).build();
  manager.notify((int)(anime.id%100000),n);
 }
}
