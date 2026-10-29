package app.yoru.anime;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import android.content.Context;

final class Api {
 static final String KEY="5vcntcxpkt3ip7cx";
 static final ExecutorService IO=Executors.newFixedThreadPool(3);
 static String https(String s){return s.startsWith("//")?"https:"+s:s;}
 static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(UnsupportedEncodingException e){throw new AssertionError(e);}}
 static JSONObject get(String path) throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.yani.tv/"+path).openConnection();
  c.setConnectTimeout(20000);c.setReadTimeout(25000);c.setRequestProperty("X-Application",KEY);c.setRequestProperty("Lang","ru");c.setRequestProperty("Accept","application/json,image/jpeg");
  try {
   int code=c.getResponseCode();
   if(code==401||code==403)throw new IOException("YummyAnime отклонил доступ. Попробуй позже или открой аниме на сайте.");
   if(code==420)throw new IOException("Сайт требует CAPTCHA. Открой YummyAnime в браузере и повтори позже.");
   if(code==429)throw new IOException("Слишком много запросов. Подожди немного.");
   if(code!=200)throw new IOException("YummyAnime недоступен ("+code+"). Попробуй ещё раз.");
   JSONObject root=new JSONObject(read(c.getInputStream()));
   if(root.has("error")&&!root.isNull("error")&&!Boolean.FALSE.equals(root.opt("error")))throw new IOException("API вернул ошибку. Повтори запрос позже.");
   return root;
  } finally {c.disconnect();}
 }
 static String read(InputStream in) throws IOException {try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]b=new byte[8192];int n;while((n=s.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
 static List<Anime> catalog(JSONObject root){List<Anime>r=new ArrayList<>();JSONArray a=root.optJSONArray("response");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optLong("anime_id")>0)r.add(new Anime(o));}return r;}
 static List<Episode> videos(JSONObject anime){List<Episode>r=new ArrayList<>();JSONArray a=anime.optJSONArray("videos");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){Episode e=new Episode(o);if(e.id>0&&e.url.startsWith("https://"))r.add(e);}}r.sort(Comparator.comparingInt(e->e.index));return r;}
 static File cache(Context c,long id){return new File(c.getCacheDir(),"anime-"+id+".json");}
 static void save(Context c,JSONObject o) throws IOException {File f=cache(c,o.optLong("anime_id"));File temp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(o.toString().getBytes(StandardCharsets.UTF_8));}if(!temp.renameTo(f)){try(FileOutputStream out=new FileOutputStream(f)){out.write(o.toString().getBytes(StandardCharsets.UTF_8));}temp.delete();}}
 static JSONObject cached(Context c,long id) throws Exception{return new JSONObject(read(new FileInputStream(cache(c,id))));}
}
