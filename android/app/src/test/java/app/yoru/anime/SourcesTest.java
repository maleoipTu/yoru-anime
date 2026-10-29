package app.yoru.anime;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SourcesTest {
 final Episode one=new Episode(1,"1",1,"Dub","Плеер Kodik","https://kodik.test/1");
 final Episode two=new Episode(2,"2",2,"Dub","Плеер Kodik","https://kodik.test/2");
 final Episode alternative=new Episode(3,"1",1,"Dub","Плеер CVH","https://cvh.test/1");
 final Episode sub=new Episode(4,"1",1,"Sub","Плеер CVH","https://cvh.test/sub");
 final List<Episode> all=Arrays.asList(two,one,alternative,sub);
 @Test public void voicesAreNotDuplicated(){assertEquals(Arrays.asList("Dub","Sub"),Sources.voices(all));}
 @Test public void providersDependOnVoice(){assertEquals(2,Sources.players(all,"Dub").size());assertEquals(1,Sources.players(all,"Sub").size());}
 @Test public void kodikIsDefaultWhenAvailable(){assertEquals("Плеер Kodik",Sources.players(all,"Dub").get(0));}
 @Test public void episodesAreOrdered(){List<Episode>e=Sources.episodes(all,"Dub","Плеер Kodik");assertEquals(1,e.get(0).id);assertEquals(2,e.get(1).id);}
 @Test public void alternativeKeepsVoiceAndNumber(){assertSame(alternative,Sources.alternative(all,one));}
 @Test public void missingSeriesDoesNotJumpToOne(){assertNull(Sources.alternative(all,two));}
 @Test public void alternativesCycle(){assertSame(one,Sources.alternative(all,alternative));}
 @Test public void labelsAreHumanReadable(){assertEquals("Серия 1",one.label());assertEquals("Dub · Плеер Kodik",one.track());}
 @Test public void selectingMissingSeriesIsExplicit(){assertNull(Sources.match(all,"Dub","Плеер CVH","2"));}
 @Test public void duplicateEpisodeNumbersAreRemoved(){List<Episode>dup=new ArrayList<>(all);dup.add(one);assertEquals(2,Sources.episodes(dup,"Dub","Плеер Kodik").size());}
}
