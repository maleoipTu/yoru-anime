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
 /** Начисление очков за просмотренную серию. */
 JSONObject award(long animeId,long videoId,String episode,int seconds) throws Exception {
  JSONObject result=(JSONObject)rpc("award_watch",new JSONObject().put("p_anime_id",animeId).put("p_video_id",videoId).put("p_episode",episode).put("p_seconds",seconds));
  if(result.optBoolean("awarded")){JSONObject cached=cachedProfile();cached.put("points",result.optInt("balance")).put("total_earned",result.optInt("total")).put("episodes_watched",cached.optInt("episodes_watched")+1);prefs.edit().putString("profile",cached.toString()).apply();}
  return result;
 }

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
