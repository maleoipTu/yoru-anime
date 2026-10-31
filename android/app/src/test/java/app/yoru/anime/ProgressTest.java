package app.yoru.anime;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class ProgressTest {
    final List<Episode> videos=Arrays.asList(new Episode(1,"1",1,"AniLibria","Kodik","https://kodik.info/a"),new Episode(2,"2",2,"AniLibria","Kodik","https://kodik.info/b"),
            new Episode(3,"1",1,"Amedia","Kodik","https://kodik.info/c"),new Episode(4,"2",2,"Amedia","Kodik","https://kodik.info/d"));
    @Test public void favouriteVoiceWinsOverHistory()throws Exception{
        JSONObject p=new JSONObject().put("voice","Amedia").put("player","Kodik").put("episode","1").put("watched",new JSONArray());
        JSONObject history=new JSONObject().put("voice","AniLibria").put("player","Kodik").put("number","2");
        Episode pick=Progress.preferred(videos,p,history);assertEquals("Amedia",pick.voice);assertEquals("1",pick.number);
        assertEquals("AniLibria",Progress.preferred(videos,null,history).voice);
    }
    @Test public void finishedEpisodeMovesToNext()throws Exception{
        JSONObject p=new JSONObject().put("voice","Amedia").put("player","Kodik").put("episode","1").put("position",1400).put("duration",1420).put("watched",new JSONArray().put("1"));
        assertEquals("2",Progress.preferred(videos,p,null).number);
    }
    @Test public void wholeTitleMarkCountsEveryEpisode()throws Exception{
        JSONObject p=new JSONObject().put("watched",new JSONArray().put(Progress.ALL).put("1"));
        assertTrue(Progress.complete(p));assertTrue(Progress.isWatched(p,"7"));assertEquals(1,Progress.watchedCount(p));assertFalse(Progress.isWatched(null,"1"));
    }
    @Test public void advancedFilters(){
        assertEquals("&ep_from=1&ep_to=12&studio_ids=58&translates=dubbing&status=released",MainActivity.advancedQuery(1,58,"dubbing",true,""));
        assertEquals("&ep_from=27",MainActivity.advancedQuery(3,0,"",true,"ongoing"));
    }
}
