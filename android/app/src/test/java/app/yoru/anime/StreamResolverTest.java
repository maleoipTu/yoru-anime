package app.yoru.anime;

import org.junit.Test;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class StreamResolverTest {
    final String page="https://kodikplayer.com/season/10/hash/720p?episode=2";
    final String html="<script src=\"/assets/js/app.player_single.fixture.js\"></script>"+
            "var domain = \"kodikplayer.com\"; var d_sign = \"page-sign\"; var pd = \"kodikplayer.com\"; var pd_sign=\"parent-sign\"; var ref=\"\"; var ref_sign=\"ref-sign\";"+
            "var vInfo={}; vInfo.type='seria'; vInfo.hash='episode-two'; vInfo.id='202';";
    final String script="$.ajax({type:'POST',url:atob(\"L2Z0b3I=\")}); x.charCodeAt(0)+18";
    static String encoded(String plain,int shift){String raw=Base64.getEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(char c:raw.toCharArray()){if(c>='A'&&c<='Z')c=(char)('A'+(c-'A'+26-shift)%26);else if(c>='a'&&c<='z')c=(char)('a'+(c-'a'+26-shift)%26);result.append(c);}return result.toString();}
    JSONObject streams()throws Exception{return new JSONObject().put("links",new JSONObject().put("360",new JSONArray().put(new JSONObject().put("src",encoded("//video.example/content/360.mp4:hls:manifest.m3u8",18)))).put("720",new JSONArray().put(new JSONObject().put("src","https://video.example/content/720.mp4"))))
            .put("vast",new JSONArray().put("https://ads.example/preroll.mp4")).put("advert_script","<script src='https://ads.example/code.js'></script>");}
    @Test public void decodesTheProviderEncodingWithoutExecutingJavascript()throws Exception{assertEquals("https://video.example/content/360.mp4:hls:manifest.m3u8",StreamResolver.decode(encoded("//video.example/content/360.mp4:hls:manifest.m3u8",18),18));}
    @Test public void obtainsExactlyTheSelectedEpisodeAndDoesNotFetchAdvertisements()throws Exception{
        List<String> requests=new ArrayList<>();
        StreamResolver.Result result=StreamResolver.resolve(page,(url,body,referer)->{
            requests.add(url);
            if(url.equals(page)){assertNull(body);return new StreamResolver.Page(url,html);}
            if(url.endsWith(".js")){assertEquals(page,referer);return new StreamResolver.Page(url,script);}
            assertEquals("https://kodikplayer.com/ftor",url);assertEquals(page,referer);assertTrue(body.contains("id=202"));assertTrue(body.contains("hash=episode-two"));assertTrue(body.contains("d_sign=page-sign"));assertFalse(body.contains("X-Application"));
            return new StreamResolver.Page(url,streams().toString());
        });
        assertEquals(3,requests.size());assertEquals(2,result.streams.size());assertEquals("720p",result.streams.get(0).label);assertEquals(page,result.referer);
    }
    @Test public void qualitiesAreOrderedAndAdFieldsAreIgnored()throws Exception{StreamResolver.Result result=StreamResolver.parse(streams(),18,page);assertEquals(Arrays.asList("720p","360p"),Arrays.asList(result.streams.get(0).label,result.streams.get(1).label));assertFalse(result.streams.get(0).url.contains("ads.example"));}
    @Test public void keepsOtherQualitiesWhenOneIsInvalid()throws Exception{JSONObject data=streams();data.getJSONObject("links").put("1080",new JSONArray().put(new JSONObject().put("src","broken***")));assertEquals(2,StreamResolver.parse(data,18,page).streams.size());}
    @Test public void adaptiveManifestIsOfferedFirst()throws Exception{JSONObject data=streams().put("link","https://video.example/master.m3u8");assertEquals("Авто",StreamResolver.parse(data,18,page).streams.get(0).label);}
    @Test(expected=IOException.class) public void noContentDoesNotFallBackToAdvert()throws Exception{StreamResolver.parse(new JSONObject().put("vast","https://ads.example/preroll.mp4"),18,page);}
    @Test(expected=IOException.class) public void rejectsScriptOnDifferentOrigin()throws Exception{StreamResolver.resolve(page,(url,body,ref)->new StreamResolver.Page(url,html.replace("/assets/js/","https://elsewhere.example/assets/js/")));}
    @Test(expected=IOException.class) public void rejectsChangedProviderInsteadOfGuessing()throws Exception{StreamResolver.resolve(page,(url,body,ref)->new StreamResolver.Page(url,url.equals(page)?html:"new unknown format"));}
    @Test public void validatesSourceHostExactly(){assertTrue(StreamResolver.supports(page));assertFalse(StreamResolver.supports("https://kodikplayer.com.attacker.example/"));assertFalse(StreamResolver.supports("http://kodikplayer.com/"));assertFalse(StreamResolver.supports("https://elsewhere.example/"));}
    @Test public void rejectsUnsafeMediaUrls()throws Exception{for(String url:Arrays.asList("http://video.example/movie.mp4","file:///private/movie.mp4","https://name:password@video.example/movie.mp4","https://localhost/movie.mp4","https://127.0.0.1/movie.mp4")){try{StreamResolver.safeUri(url);fail(url);}catch(IOException expected){}}}
    @Test public void skipsHtmlDisguisedAsQuality()throws Exception{JSONObject data=streams();data.getJSONObject("links").put("1080",new JSONArray().put(new JSONObject().put("src","https://video.example/page.html")));assertEquals(2,StreamResolver.parse(data,18,page).streams.size());}
    @Test public void formEncodingPreservesSignedValues()throws Exception{Map<String,String> form=new LinkedHashMap<>();form.put("ref","a+b&c=two words");assertEquals("ref=a%2Bb%26c%3Dtwo+words",StreamResolver.encodeForm(form));}

    @Test public void cvhPicksRequestedVoiceAndDirectFiles()throws Exception{
        String cvh="//ru.yummyani.me/iframeCVH.html?dubbing_code=Amedia&anime_id=21&episode=2&dubbing=Озвучка Amedia";
        assertTrue(StreamResolver.supports(cvh));assertFalse(StreamResolver.supports("https://elsewhere.example/iframeCVH.html?anime_id=1"));
        List<String> requests=new ArrayList<>();
        StreamResolver.Result result=StreamResolver.resolve(cvh,(url,body,ref)->{requests.add(url);
            if(url.contains("/playlist"))return new StreamResolver.Page(url,new JSONObject().put("items",new JSONArray()
                .put(new JSONObject().put("episode",1).put("voiceStudio","Amedia").put("vkId","11"))
                .put(new JSONObject().put("episode",2).put("voiceStudio","AniLibria").put("vkId","21"))
                .put(new JSONObject().put("episode",2).put("voiceStudio","Amedia").put("vkId","22"))).toString());
            assertTrue(url.endsWith("/video/22"));
            return new StreamResolver.Page(url,new JSONObject().put("sources",new JSONObject().put("hlsUrl","https://vd.okcdn.ru/video.m3u8?x=1").put("mpegHighUrl","https://vd.okcdn.ru/?id=5&type=3").put("dashUrl","https://vd.okcdn.ru/d.mpd")).toString());});
        assertTrue(requests.get(0).contains("id=21"));assertEquals("Авто",result.streams.get(0).label);assertEquals("720p",result.streams.get(1).label);assertEquals("video/mp4",result.streams.get(1).mime);assertEquals(StreamResolver.BROWSER_AGENT,result.userAgent);
    }
    @Test public void aksorReturnsDashQualitiesAndIntroMarks()throws Exception{
        String aksor="https://player.aksor.tv/video/abc123";assertTrue(StreamResolver.supports(aksor));
        StreamResolver.Result result=StreamResolver.resolve(aksor,(url,body,ref)->{assertEquals("https://player.aksor.tv/api/video/abc123",url);
            return new StreamResolver.Page(url,new JSONObject().put("qualities",new JSONObject().put("q720","https://cdn.aksor.tv/a b/720.mpd").put("q1080","https://cdn.aksor.tv/a b/1080.mpd").put("q4k",JSONObject.NULL)).put("opening_start",60).put("opening_length",90).toString());});
        assertEquals(2,result.streams.size());assertEquals("1080p",result.streams.get(0).label);assertEquals("application/dash+xml",result.streams.get(0).mime);assertFalse(result.streams.get(0).url.contains(" "));
        assertEquals(150,result.intro.getJSONObject("opening").getInt("end"));assertFalse(result.intro.has("ending"));
    }
}
