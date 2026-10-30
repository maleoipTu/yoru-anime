package app.yoru.anime;

import org.json.*;
import java.util.*;

final class Anime {
 final long id; final String title, alias, description, poster, type, status; final int year; final double rating; final long nextDate; final JSONObject raw;
 Anime(JSONObject o) {
  raw=compact(o); id=o.optLong("anime_id"); title=o.optString("title"); alias=o.optString("anime_url");
  description=android.text.Html.fromHtml(o.optString("description"),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
  JSONObject p=o.optJSONObject("poster"),r=o.optJSONObject("rating"),t=o.optJSONObject("type"),s=o.optJSONObject("anime_status"),e=o.optJSONObject("episodes");
  poster=Api.https(p==null?"":p.optString("fullsize")); rating=r==null?0:r.optDouble("average",0); year=o.optInt("year");
  type=t==null?"":t.optString("name"); status=s==null?"":s.optString("title"); nextDate=e==null?0:e.optLong("next_date");
 }
 static JSONObject compact(JSONObject o){JSONObject r=new JSONObject();for(String key:new String[]{"anime_id","title","anime_url","description","poster","rating","type","anime_status","year","episodes","genres","min_age","yoru_progress"})try{if(o.has(key))r.put(key,o.get(key));}catch(JSONException ignored){}return r;}
 String meta(){return (year>0?year+" · ":"")+type+(status.isEmpty()?"":" · "+statusLabel());}
 String statusLabel(){return status.isEmpty()?"":status.substring(0,1).toUpperCase(Locale.forLanguageTag("ru"))+status.substring(1);}
 /** ongoing, released или announcement — по alias из API, с запасным вариантом по названию статуса. */
 String statusKey(){JSONObject s=raw.optJSONObject("anime_status");String alias=s==null?"":s.optString("alias");if(!alias.isEmpty())return alias;String value=status.toLowerCase(Locale.ROOT);return value.contains("онгоинг")?"ongoing":value.contains("анонс")?"announcement":"";}
 String age(){JSONObject a=raw.optJSONObject("min_age");return a==null?"":a.optString("title").replace('-','\u2011');}
 String genres(){JSONArray all=raw.optJSONArray("genres");if(all==null)return "";List<String>result=new ArrayList<>();for(int i=0;i<all.length()&&result.size()<4;i++){Object value=all.opt(i);String name=value instanceof JSONObject?((JSONObject)value).optString("title"):value instanceof String?(String)value:"";if(!name.isEmpty())result.add(name);}return String.join(" · ",result);}
 /** Число серий из API (episodes.count), 0 — если неизвестно. */
 int totalEpisodes(){JSONObject e=raw.optJSONObject("episodes");return e==null?0:Math.max(e.optInt("count"),0);}
 /** Возрастной рейтинг числом (0, 6, 12, 16, 18) или -1, если неизвестен. */
 int ageValue(){JSONObject a=raw.optJSONObject("min_age");return a==null?-1:a.optInt("value",-1);}
 /** Прогресс из облачного списка, например «3 из 12». */
 String progress(){return raw.optString("yoru_progress");}
 String score(){return rating>0?String.format(Locale.US,"★ %.1f",rating):"Без оценки";}
}
final class Episode {
 final long id; final String number,voice,player,url; final int index;
 Episode(long id,String number,int index,String voice,String player,String url){this.id=id;this.number=number;this.index=index;this.voice=voice;this.player=player;this.url=url;}
 Episode(JSONObject o) {
  id=o.optLong("video_id");number=o.optString("number");index=o.optInt("index");url=Api.https(o.optString("iframe_url"));
  JSONObject d=o.optJSONObject("data");voice=d==null?"Без названия":d.optString("dubbing","Без названия");player=d==null?"Плеер":d.optString("player","Плеер");
 }
 String label(){return "Серия "+number;} String track(){return voice+" · "+player;}
}
final class Sources {
 static List<String> voices(List<Episode> all){TreeSet<String>s=new TreeSet<>();for(Episode e:all)s.add(e.voice);return new ArrayList<>(s);}
 static List<String> players(List<Episode> all,String voice){List<String>s=new ArrayList<>();for(Episode e:all)if(e.voice.equals(voice)&&!s.contains(e.player))s.add(e.player);s.sort(Comparator.comparingInt(Sources::rank));return s;}
 static int rank(String s){return s.contains("Kodik")?0:s.contains("CVH")?1:s.contains("Aksor")?2:3;}
 static List<Episode> episodes(List<Episode> all,String voice,String player){List<Episode>s=new ArrayList<>();Set<String>n=new HashSet<>();for(Episode e:all)if(e.voice.equals(voice)&&e.player.equals(player)&&n.add(e.number))s.add(e);s.sort(Comparator.comparingInt(e->e.index));return s;}
 static Episode match(List<Episode> all,String voice,String player,String number){for(Episode e:all)if(e.voice.equals(voice)&&e.player.equals(player)&&e.number.equals(number))return e;return null;}
 static Episode alternative(List<Episode> all,Episode current){List<String>p=players(all,current.voice);int start=p.indexOf(current.player);for(int i=1;i<p.size();i++){Episode e=match(all,current.voice,p.get((Math.max(0,start)+i)%p.size()),current.number);if(e!=null)return e;}return null;}
}
