package app.yoru.anime;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Аккаунт Yoru в Supabase: вход по почте и паролю (Supabase Auth) и данные профиля (PostgREST).
 * Все сетевые методы блокирующие — вызывать только из Api.IO.
 */
final class Account {
 static final String URL=BuildConfig.SUPABASE_URL.replaceAll("/+$",""),KEY=BuildConfig.SUPABASE_ANON_KEY;
 static boolean configured(){return URL.startsWith("https://")&&!KEY.isEmpty();}

 static final class Failure extends IOException {final int code;Failure(int code,String message){super(message);this.code=code;}}

 final SharedPreferences prefs;
 Account(Context c){prefs=c.getApplicationContext().getSharedPreferences("yoru_account",Context.MODE_PRIVATE);}

 boolean signedIn(){return configured()&&!prefs.getString("refresh","").isEmpty();}
 String userId(){return prefs.getString("user","");}
 String email(){return prefs.getString("email","");}
 /** Последний загруженный профиль — чтобы аватар в шапке появлялся без ожидания сети. */
 JSONObject cachedProfile(){try{return new JSONObject(prefs.getString("profile","{}"));}catch(JSONException e){return new JSONObject();}}
 JSONArray cachedDecorations(){try{return new JSONArray(prefs.getString("decorations","[]"));}catch(JSONException e){return new JSONArray();}}

 // ───── Auth ─────
 /** Возвращает true, если вход выполнен сразу; false — если Supabase ждёт подтверждения почты. */
 boolean signUp(String email,String password,String username) throws Exception {
  JSONObject body=new JSONObject().put("email",email).put("password",password).put("data",new JSONObject().put("username",username).put("display_name",username));
  JSONObject result=new JSONObject(call("POST","/auth/v1/signup",body.toString(),null,null));
  if(result.optString("access_token").isEmpty())return false;
  store(result);return true;
 }
 void signIn(String email,String password) throws Exception {store(new JSONObject(call("POST","/auth/v1/token?grant_type=password",new JSONObject().put("email",email).put("password",password).toString(),null,null)));}
 /** Письмо со ссылкой для сброса пароля. */
 void recover(String email) throws Exception {call("POST","/auth/v1/recover",new JSONObject().put("email",email).toString(),null,null);}
 void signOut(){String access=prefs.getString("access","");prefs.edit().clear().apply();if(!access.isEmpty())Api.IO.execute(()->{try{call("POST","/auth/v1/logout","{}",access,null);}catch(Exception ignored){}});}

 void store(JSONObject session) throws JSONException {
  JSONObject user=session.optJSONObject("user");
  prefs.edit().putString("access",session.getString("access_token")).putString("refresh",session.getString("refresh_token"))
   .putLong("expires",System.currentTimeMillis()/1000+session.optLong("expires_in",3600))
   .putString("user",user==null?userId():user.optString("id")).putString("email",user==null?email():user.optString("email")).apply();
 }

 synchronized String token() throws Exception {
  if(!signedIn())throw new Failure(401,"not_authenticated");
  if(prefs.getLong("expires",0)-60>System.currentTimeMillis()/1000)return prefs.getString("access","");
  try{store(new JSONObject(call("POST","/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",prefs.getString("refresh","")).toString(),null,null)));}
  catch(Failure failure){if(failure.code==400||failure.code==401)prefs.edit().remove("refresh").remove("access").apply();throw failure;}
  return prefs.getString("access","");
 }

 // ───── Данные ─────
 JSONObject profile() throws Exception {
  JSONObject result=new JSONObject(call("GET","/rest/v1/profiles?id=eq."+userId()+"&select=*",null,token(),"application/vnd.pgrst.object+json"));
  prefs.edit().putString("profile",result.toString()).apply();return result;
 }
 JSONArray decorations() throws Exception {
  JSONArray result=new JSONArray(call("GET","/rest/v1/decorations?select=*&order=kind.asc,sort.asc",null,token(),null));
  prefs.edit().putString("decorations",result.toString()).apply();return result;
 }
 Set<String> inventory() throws Exception {
  JSONArray rows=new JSONArray(call("GET","/rest/v1/inventory?user_id=eq."+userId()+"&select=decoration_id",null,token(),null));
  Set<String> result=new HashSet<>();for(int i=0;i<rows.length();i++)result.add(rows.getJSONObject(i).optString("decoration_id"));return result;
 }
 /** Меняет только разрешённые поля (p_display_name, p_status, p_avatar_url, p_showcase); отсутствующие остаются прежними. */
 JSONObject update(JSONObject fields) throws Exception {return (JSONObject)rpc("update_profile",fields);}
 Object rpc(String name,JSONObject arguments) throws Exception {
  Object result=new JSONTokener(call("POST","/rest/v1/rpc/"+name,arguments.toString(),token(),null)).nextValue();
  if(result instanceof JSONObject&&((JSONObject)result).has("equipped_frame"))prefs.edit().putString("profile",result.toString()).apply();
  return result;
 }
 /** Начисление очков за просмотренную серию: заодно обновляет список «Смотрю», streak и достижения. */
 JSONObject award(Anime anime,long videoId,String episode,int seconds) throws Exception {
  int tz=java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())/60000;
  JSONObject result=(JSONObject)rpc("award_watch",new JSONObject().put("p_anime_id",anime.id).put("p_video_id",videoId).put("p_episode",episode).put("p_seconds",seconds)
   .put("p_title",anime.title).put("p_poster",anime.poster).put("p_total",anime.totalEpisodes()).put("p_tz_minutes",tz));
  if(result.optBoolean("awarded")){JSONObject cached=cachedProfile();cached.put("points",result.optInt("balance")).put("total_earned",result.optInt("total")).put("episodes_watched",cached.optInt("episodes_watched")+1).put("streak_days",result.optInt("streak"));prefs.edit().putString("profile",cached.toString()).apply();}
  return result;
 }

 // ───── Публичные данные (работают и без входа) ─────
 String bearer() throws Exception {return signedIn()?token():null;}
 Object anonRpc(String name,JSONObject arguments) throws Exception {return new JSONTokener(call("POST","/rest/v1/rpc/"+name,arguments.toString(),bearer(),null)).nextValue();}
 JSONArray get(String path) throws Exception {return new JSONArray(call("GET",path,null,bearer(),null));}

 // ───── Списки ─────
 static final String[] LISTS={"watching","planned","completed","dropped"};
 static String listLabel(String status){switch(status){case "watching":return "Смотрю";case "planned":return "Запланировано";case "completed":return "Просмотрено";case "dropped":return "Брошено";default:return "Избранное";}}
 /** Аниме из облачного списка в формате карточки каталога. */
 List<Anime> list(String status) throws Exception {
  JSONArray rows=get("/rest/v1/anime_lists?user_id=eq."+userId()+"&status=eq."+status+"&select=anime_id,title,poster,episodes,total&order=updated_at.desc&limit=500");
  List<Anime> result=new ArrayList<>();
  for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);JSONObject raw=new JSONObject().put("anime_id",r.optLong("anime_id")).put("title",r.optString("title")).put("poster",new JSONObject().put("fullsize",r.optString("poster"))).put("yoru_progress",r.optInt("episodes")+(r.optInt("total")>0?" из "+r.optInt("total"):""));result.add(new Anime(raw));}
  return result;
 }
 /** Статус аниме в моих списках: {status, episodes, total} или пустой объект. */
 JSONObject listEntry(long animeId) throws Exception {JSONArray rows=get("/rest/v1/anime_lists?user_id=eq."+userId()+"&anime_id=eq."+animeId+"&select=status,episodes,total");return rows.length()>0?rows.getJSONObject(0):new JSONObject();}
 JSONObject setList(Anime anime,String status) throws Exception {return (JSONObject)rpc("set_list_status",new JSONObject().put("p_anime_id",anime.id).put("p_status",status).put("p_title",anime.title).put("p_poster",anime.poster).put("p_total",anime.totalEpisodes()));}

 // ───── Оценки и отзывы ─────
 JSONObject rating(long animeId) throws Exception {return (JSONObject)anonRpc("anime_rating",new JSONObject().put("p_anime_id",animeId));}
 JSONArray reviews(long animeId) throws Exception {return get("/rest/v1/reviews?anime_id=eq."+animeId+"&select=user_id,score,body,updated_at,profiles(username,display_name,avatar_url,equipped_frame,equipped_badge,equipped_color)&order=updated_at.desc&limit=30");}
 JSONObject setReview(Anime anime,int score,String body) throws Exception {return (JSONObject)rpc("set_review",new JSONObject().put("p_anime_id",anime.id).put("p_score",score).put("p_body",body).put("p_title",anime.title).put("p_poster",anime.poster));}

 // ───── Достижения ─────
 JSONArray achievements() throws Exception {JSONArray result=get("/rest/v1/achievements?select=*&order=sort.asc");prefs.edit().putString("achievements",result.toString()).apply();return result;}
 JSONArray cachedAchievements(){try{return new JSONArray(prefs.getString("achievements","[]"));}catch(JSONException e){return new JSONArray();}}
 Set<String> unlocked() throws Exception {JSONArray rows=get("/rest/v1/user_achievements?user_id=eq."+userId()+"&select=achievement_id");Set<String> result=new HashSet<>();for(int i=0;i<rows.length();i++)result.add(rows.getJSONObject(i).optString("achievement_id"));return result;}

 // ───── Друзья ─────
 JSONArray friends() throws Exception {return (JSONArray)rpc("my_friends",new JSONObject());}
 JSONArray feed() throws Exception {return (JSONArray)rpc("friend_feed",new JSONObject());}
 JSONArray search(String query) throws Exception {String q=query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]","");if(q.isEmpty())return new JSONArray();return get("/rest/v1/profiles?username=ilike.*"+q+"*&select=id,username,display_name,avatar_url,status,equipped_frame,equipped_badge,equipped_color,total_earned&order=username.asc&limit=20");}
 JSONObject publicProfile(String username) throws Exception {Object result=anonRpc("public_profile",new JSONObject().put("p_username",username));return result instanceof JSONObject?(JSONObject)result:null;}

 // ───── Хранилище аватаров и баннеров ─────
 /** Загружает файл в бакет media в папку пользователя и возвращает публичный адрес. */
 String upload(String name,byte[] data,String mime) throws Exception {
  String path=userId()+"/"+name;
  HttpURLConnection c=(HttpURLConnection)new URL(URL+"/storage/v1/object/media/"+path).openConnection();
  try{c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);
   c.setRequestProperty("apikey",KEY);c.setRequestProperty("Authorization","Bearer "+token());c.setRequestProperty("Content-Type",mime);c.setRequestProperty("x-upsert","true");c.setRequestProperty("Cache-Control","max-age=31536000");
   try(OutputStream out=c.getOutputStream()){out.write(data);}
   int code=c.getResponseCode();if(code>=400){InputStream e=c.getErrorStream();throw new Failure(code,message(e==null?"":Api.read(e),code));}
  }finally{c.disconnect();}
  return URL+"/storage/v1/object/public/media/"+path;
 }
 /** Удаляет свой прежний файл, если адрес указывает на хранилище Yoru. */
 void deleteOwn(String url){String prefix=URL+"/storage/v1/object/public/media/"+userId()+"/";if(url==null||!url.startsWith(prefix))return;try{call("DELETE","/storage/v1/object/media/"+url.substring(URL.length()+"/storage/v1/object/public/media/".length()),null,token(),null);}catch(Exception ignored){}}
 boolean isAdmin(){try{return Boolean.TRUE.equals(rpc("is_admin",new JSONObject()));}catch(Exception e){return false;}}

 static String call(String method,String path,String body,String bearer,String accept) throws IOException {
  HttpURLConnection c=(HttpURLConnection)new URL(URL+path).openConnection();
  try{
   c.setRequestMethod(method);
   c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("apikey",KEY);c.setRequestProperty("Authorization","Bearer "+(bearer==null?KEY:bearer));
   c.setRequestProperty("Accept",accept==null?"application/json":accept);
   if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
   int code=c.getResponseCode();
   InputStream stream=code>=400?c.getErrorStream():c.getInputStream();
   String text=stream==null?"":Api.read(stream);
   if(code>=400)throw new Failure(code,message(text,code));
   return text;
  }finally{c.disconnect();}
 }
 static String message(String text,int code){
  try{JSONObject o=new JSONObject(text);for(String key:new String[]{"error_code","msg","message","error_description","error"}){String value=o.optString(key);if(!value.isEmpty()&&!value.equals("null"))return value+" "+o.optString("message");}}catch(JSONException ignored){}
  return "HTTP "+code;
 }
}
