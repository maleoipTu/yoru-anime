package app.yoru.anime;
import android.content.*;
import org.json.*;
import java.util.*;
final class Library {
 final SharedPreferences prefs;
 Library(Context c){prefs=c.getSharedPreferences("yoru_library",Context.MODE_PRIVATE);}
 JSONArray array(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
 List<Anime> favorites(){List<Anime>r=new ArrayList<>();JSONArray a=array("favorites");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)r.add(new Anime(o));}return r;}
 boolean favorite(long id){for(Anime a:favorites())if(a.id==id)return true;return false;}
 boolean toggle(Anime item){JSONArray n=new JSONArray();boolean found=false;for(Anime a:favorites())if(a.id==item.id)found=true;else n.put(a.raw);if(!found)n.put(item.raw);prefs.edit().putString("favorites",n.toString()).apply();return !found;}
 List<Anime> history(){List<Anime>r=new ArrayList<>();JSONArray a=array("history");for(int i=0;i<a.length();i++){JSONObject h=a.optJSONObject(i);if(h!=null&&h.optJSONObject("anime")!=null)r.add(new Anime(h.optJSONObject("anime")));}return r;}
 JSONObject last(long id){JSONArray a=array("history");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optLong("anime_id")==id)return o;}return null;}
 long position(long videoId){JSONArray a=array("positions");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optLong("id")==videoId)return Math.max(0,o.optLong("position"));}return 0;}
 void position(long videoId,long position){try{JSONArray result=new JSONArray().put(new JSONObject().put("id",videoId).put("position",Math.max(0,position)));JSONArray old=array("positions");for(int i=0;i<old.length()&&result.length()<200;i++){JSONObject o=old.optJSONObject(i);if(o!=null&&o.optLong("id")!=videoId)result.put(o);}prefs.edit().putString("positions",result.toString()).apply();}catch(JSONException ignored){}}
 void opened(Anime item,Episode video){try{JSONObject h=new JSONObject().put("anime_id",item.id).put("anime",item.raw).put("video_id",video.id).put("number",video.number).put("voice",video.voice).put("player",video.player);JSONArray n=new JSONArray().put(h),old=array("history");for(int i=0;i<old.length()&&n.length()<200;i++){JSONObject o=old.optJSONObject(i);if(o!=null&&o.optLong("anime_id")!=item.id)n.put(o);}prefs.edit().putString("history",n.toString()).apply();}catch(JSONException ignored){}}
}
