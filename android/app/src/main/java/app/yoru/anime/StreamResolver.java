package app.yoru.anime;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Resolves content streams only. Provider HTML and advertising JavaScript are never executed. */
final class StreamResolver {
    static final String USER_AGENT = "Yoru/0.4.0 (Android)";
    /** CVH и Aksor отдают ссылки, подписанные под обычный браузер: для них запросы идут с браузерным User-Agent. */
    static final String BROWSER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";
    static final int MAX_BODY = 2 * 1024 * 1024;
    interface Transport { Page request(String url, String body, String referer) throws Exception; }
    static final class Page {
        final String url, text;
        Page(String url, String text) { this.url = url; this.text = text; }
    }
    static final class Stream {
        final String label, url, mime;
        Stream(String label, String url, String mime) { this.label=label; this.url=url; this.mime=mime; }
    }
    static final class Result {
        final List<Stream> streams;
        final String referer, userAgent;
        /** Разметка заставок от источника (Aksor): {opening:{start,end}, ending:{start,end}} или null. */
        final JSONObject intro;
        Result(List<Stream> streams, String referer) { this(streams, referer, USER_AGENT, null); }
        Result(List<Stream> streams, String referer, String userAgent, JSONObject intro) { this.streams=Collections.unmodifiableList(streams); this.referer=referer; this.userAgent=userAgent; this.intro=intro; }
    }
    static boolean supports(String url) {
        try {
            URI uri = safeUri(normalize(url));
            return kodik(uri.getHost()) || cvh(uri) || aksor(uri);
        } catch (IOException e) { return false; }
    }
    static String normalize(String url) { if (url == null) return null; url = url.trim(); if (url.startsWith("//")) url = "https:" + url; return url.replace(" ", "%20"); }
    static boolean kodik(String host) { host = host.toLowerCase(Locale.ROOT); return host.equals("kodikplayer.com") || host.equals("kodik.info") || host.equals("kodik.cc"); }
    /** CVH: страница-обёртка YummyAnime iframeCVH.html?anime_id=…&episode=…&dubbing_code=… */
    static boolean cvh(URI uri) { String host = uri.getHost().toLowerCase(Locale.ROOT); return (host.equals("yummyani.me") || host.endsWith(".yummyani.me")) && uri.getPath() != null && uri.getPath().endsWith("/iframeCVH.html"); }
    /** Aksor: player.aksor.tv/video/{id} */
    static boolean aksor(URI uri) { return uri.getHost().equalsIgnoreCase("player.aksor.tv") && uri.getPath() != null && uri.getPath().matches("/video/[A-Za-z0-9_-]{1,64}/?"); }
    static Map<String,String> query(URI uri) {
        Map<String,String> result = new HashMap<>(); String raw = uri.getRawQuery(); if (raw == null) return result;
        for (String part : raw.split("&")) { int eq = part.indexOf('='); if (eq <= 0) continue; try { result.put(URLDecoder.decode(part.substring(0, eq), "UTF-8"), URLDecoder.decode(part.substring(eq + 1), "UTF-8")); } catch (Exception ignored) {} }
        return result;
    }
    static String safeMediaUrl(String url) throws IOException { if (url == null || url.isEmpty() || url.equals("null")) return null; String fixed = url.startsWith("//") ? "https:" + url : url; fixed = fixed.replace(" ", "%20"); safeUri(fixed); return fixed; }

    /** CVH (cdnvideohub): список серий и озвучек, затем прямые HLS/MP4 с CDN. */
    static Result resolveCvh(URI page, Transport network) throws Exception {
        Map<String,String> q = query(page);
        String id = q.get("anime_id"), episode = q.get("episode"), code = q.getOrDefault("dubbing_code", ""), dubbing = q.getOrDefault("dubbing", "");
        if (id == null || !id.matches("[0-9]{1,10}") || episode == null) throw changed();
        Page list = network.request("https://plapi.cdnvideohub.com/api/v1/player/sv/playlist?pub=745&aggr=mali&id=" + id, null, "https://ru.yummyani.me/");
        JSONArray items = new JSONObject(list.text).optJSONArray("items");
        if (items == null) throw changed();
        JSONObject chosen = null, byType = null, first = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null || !String.valueOf(item.optInt("episode")).equals(episode.trim())) continue;
            if (first == null) first = item;
            if (!code.isEmpty() && code.equalsIgnoreCase(item.optString("voiceStudio"))) { chosen = item; break; }
            if (byType == null && !item.optString("voiceType").isEmpty() && dubbing.toLowerCase(Locale.ROOT).contains(item.optString("voiceType").toLowerCase(Locale.ROOT))) byType = item;
        }
        if (chosen == null) chosen = byType != null ? byType : first;
        if (chosen == null) throw new IOException("Источник не вернул эту серию. Попробуй другую озвучку или плеер.");
        String vk = chosen.optString("vkId");
        if (!vk.matches("[0-9]{1,20}")) throw changed();
        Page video = network.request("https://plapi.cdnvideohub.com/api/v1/player/sv/video/" + vk, null, "https://ru.yummyani.me/");
        JSONObject sources = new JSONObject(video.text).optJSONObject("sources");
        if (sources == null) throw changed();
        List<Stream> streams = new ArrayList<>(); Set<String> seen = new HashSet<>();
        String hls = safeMediaUrl(sources.optString("hlsUrl"));
        if (hls != null && seen.add(hls)) streams.add(new Stream("Авто", hls, "application/x-mpegURL"));
        String[][] mp4 = {{"mpeg4kUrl", "2160p"}, {"mpeg2kUrl", "1440p"}, {"mpegQhdUrl", "1440p"}, {"mpegFullHdUrl", "1080p"}, {"mpegHighUrl", "720p"}, {"mpegMediumUrl", "480p"}, {"mpegLowUrl", "360p"}, {"mpegLowestUrl", "240p"}};
        Set<String> labels = new HashSet<>();
        for (String[] m : mp4) { try { String url = safeMediaUrl(sources.optString(m[0])); if (url != null && labels.add(m[1]) && seen.add(url)) streams.add(new Stream(m[1], url, "video/mp4")); } catch (IOException ignored) {} }
        if (streams.isEmpty()) throw new IOException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        return new Result(streams, "https://player.cdnvideohub.com/", BROWSER_AGENT, null);
    }

    /** Aksor: JSON с DASH-потоками по качествам и разметкой опенинга/эндинга. */
    static Result resolveAksor(URI page, Transport network) throws Exception {
        String id = page.getPath().substring("/video/".length()).replace("/", "");
        Page api = network.request("https://player.aksor.tv/api/video/" + id, null, page.toString());
        JSONObject data = new JSONObject(api.text);
        JSONObject qualities = data.optJSONObject("qualities");
        if (qualities == null) throw changed();
        List<Stream> streams = new ArrayList<>();
        String[][] order = {{"q4k", "2160p"}, {"q2k", "1440p"}, {"q1080", "1080p"}, {"q720", "720p"}, {"q480", "480p"}, {"q360", "360p"}};
        for (String[] q : order) { try { String url = qualities.isNull(q[0]) ? null : safeMediaUrl(qualities.optString(q[0])); if (url != null) streams.add(new Stream(q[1], url, url.toLowerCase(Locale.ROOT).contains(".m3u8") ? "application/x-mpegURL" : "application/dash+xml")); } catch (IOException ignored) {} }
        if (streams.isEmpty()) throw new IOException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        JSONObject intro = new JSONObject();
        int os = data.optInt("opening_start"), ol = data.optInt("opening_length"), es = data.optInt("ending_start"), el = data.optInt("ending_length");
        if (ol > 0) intro.put("opening", new JSONObject().put("start", os).put("end", os + ol).put("exact", true));
        if (el > 0) intro.put("ending", new JSONObject().put("start", es).put("end", es + el).put("exact", true));
        return new Result(streams, "https://player.aksor.tv/", BROWSER_AGENT, intro.length() > 0 ? intro : null);
    }
    static Result resolve(String url) throws Exception { return resolve(url, StreamResolver::request); }
    static Result resolve(String url, Transport network) throws Exception {
        if (!supports(url)) throw new IOException("Для этого источника прямой поток пока не поддерживается. Выбери Kodik, CVH или Aksor, или открой плеер сайта.");
        url = normalize(url);
        URI target = safeUri(url);
        if (cvh(target)) return resolveCvh(target, network);
        if (aksor(target)) return resolveAksor(target, network);
        Page page = network.request(url, null, null);
        if (!kodik(safeUri(page.url).getHost())) throw new IOException("Источник перенаправил запрос на неизвестный плеер.");
        Map<String,String> form = new LinkedHashMap<>();
        for (String name : new String[]{"domain","d_sign","pd","pd_sign","ref","ref_sign"}) {
            String value = capture(page.text, "\\bvar\\s+"+name+"\\s*=\\s*([\"'])(.*?)\\1", 2);
            if (value == null) throw changed();
            form.put(name.equals("domain") ? "d" : name, value);
        }
        for (String name : new String[]{"type","hash","id"}) {
            String value = capture(page.text,"\\bvInfo\\."+name+"\\s*=\\s*([\"'])(.*?)\\1",2);
            if (value == null || value.isEmpty()) throw changed();
            form.put(name,value);
        }
        String script = capture(page.text,"<script[^>]+src=[\"']([^\"']*/assets/js/app\\.player_single[^\"']+\\.js)[\"']",1);
        if (script == null) throw changed();
        URI scriptUri = safeUri(page.url).resolve(script);
        if (!sameOrigin(safeUri(page.url),scriptUri)) throw changed();
        Page js = network.request(scriptUri.toString(),null,page.url);
        if (!sameOrigin(safeUri(page.url),safeUri(js.url))) throw changed();
        String encodedRoute = capture(js.text,"url\\s*:\\s*atob\\([\"']([A-Za-z0-9+/=]+)[\"']\\)",1);
        String shiftText = capture(js.text,"charCodeAt\\(0\\)\\s*\\+\\s*(\\d{1,2})",1);
        if (encodedRoute == null || shiftText == null) throw changed();
        int shift = Integer.parseInt(shiftText);
        if (shift > 25) throw changed();
        String route = new String(Base64.getDecoder().decode(encodedRoute),StandardCharsets.UTF_8);
        if (!route.matches("/[a-zA-Z0-9_/-]+")) throw changed();
        URI endpoint = safeUri(page.url).resolve(route);
        if (!sameOrigin(safeUri(page.url),endpoint)) throw changed();
        Page response = network.request(endpoint.toString(),encodeForm(form),page.url);
        if (!sameOrigin(endpoint,safeUri(response.url))) throw changed();
        return parse(new JSONObject(response.text),shift,page.url);
    }
    static Result parse(JSONObject data, int shift, String referer) throws Exception {
        List<Stream> streams = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        String adaptive = data.optString("link", "");
        if (!adaptive.isEmpty()) add(streams,urls,"Авто",adaptive,"application/x-mpegURL",shift);
        JSONObject links = data.optJSONObject("links");
        if (links != null) {
            List<String> qualities = new ArrayList<>();
            Iterator<String> keys = links.keys();
            while(keys.hasNext()) { String key=keys.next(); if(key.matches("[1-9][0-9]{1,3}")) qualities.add(key); }
            qualities.sort((a,b)->Integer.compare(Integer.parseInt(b),Integer.parseInt(a)));
            for(String quality:qualities) {
                JSONArray variants=links.optJSONArray(quality);
                if(variants==null)continue;
                for(int i=0;i<variants.length();i++) {
                    JSONObject variant=variants.optJSONObject(i);
                    if(variant==null)continue;
                    int before=streams.size();
                    add(streams,urls,quality+"p",variant.optString("src"),variant.optString("type"),shift);
                    if(streams.size()>before)break;
                }
            }
        }
        if(streams.isEmpty())throw new IOException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        return new Result(streams,referer);
    }
    private static void add(List<Stream> streams,Set<String> urls,String label,String raw,String mime,int shift) {
        try {
            String url=decode(raw,shift);
            safeUri(url);
            String path=URI.create(url).getPath().toLowerCase(Locale.ROOT);
            boolean hls=path.endsWith(".m3u8"),mp4=path.endsWith(".mp4");
            if(!hls&&!mp4)return;
            if(urls.add(url))streams.add(new Stream(label,url,hls?"application/x-mpegURL":"video/mp4"));
        }catch(Exception ignored) { /* An invalid rendition must not discard the other qualities. */ }
    }
    static String decode(String value,int shift) throws IOException {
        if(value==null||value.isEmpty()||value.length()>16384)throw changed();
        String result=value;
        if(!value.startsWith("//")&&!value.startsWith("https://")) {
            StringBuilder decoded=new StringBuilder();
            for(char c:value.toCharArray()) {
                if(c>='A'&&c<='Z')c=(char)('A'+(c-'A'+shift)%26);
                else if(c>='a'&&c<='z')c=(char)('a'+(c-'a'+shift)%26);
                decoded.append(c);
            }
            try { result=new String(Base64.getDecoder().decode(decoded.toString()),StandardCharsets.UTF_8); }
            catch(IllegalArgumentException e){throw changed();}
        }
        return result.startsWith("//")?"https:"+result:result;
    }
    static URI safeUri(String url) throws IOException {
        try {
            URI uri=new URI(url);
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||uri.getFragment()!=null||
                    host.equalsIgnoreCase("localhost")||host.endsWith(".local")||host.matches("[0-9.]+")||host.contains(":"))throw changed();
            return uri;
        }catch(URISyntaxException|NullPointerException e){throw changed();}
    }
    static boolean sameOrigin(URI a,URI b){return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&a.getPort()==b.getPort();}
    static String capture(String text,String expression,int group){Matcher m=Pattern.compile(expression,Pattern.DOTALL).matcher(text);return m.find()?m.group(group):null;}
    static String encodeForm(Map<String,String> values) throws UnsupportedEncodingException {
        StringJoiner body=new StringJoiner("&");
        for(Map.Entry<String,String> entry:values.entrySet())body.add(URLEncoder.encode(entry.getKey(),"UTF-8")+"="+URLEncoder.encode(entry.getValue(),"UTF-8"));
        return body.toString();
    }
    static IOException changed(){return new IOException("Формат источника изменился. Попробуй другой плеер или повтори позже.");}
    static Page request(String url,String body,String referer) throws Exception {
        for(int redirect=0;redirect<5;redirect++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            safeUri(url);
            HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(18000);
            connection.setRequestProperty("User-Agent",kodik(safeUri(url).getHost())?USER_AGENT:BROWSER_AGENT);
            if(referer!=null)connection.setRequestProperty("Referer",referer);
            try {
                if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null||body!=null)throw changed();String target=safeUri(url).resolve(location).toString();safeUri(target);url=target;continue;}
                if(code==403||code==451)throw new IOException("Источник ограничил доступ к видео. Выбери другой источник.");
                if(code!=200)throw new IOException("Источник видео недоступен ("+code+"). Попробуй ещё раз.");
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int size;
                    while((size=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+size>MAX_BODY)throw changed();out.write(buffer,0,size);}
                    return new Page(url,out.toString("UTF-8"));
                }
            }finally{connection.disconnect();}
        }
        throw changed();
    }
}
