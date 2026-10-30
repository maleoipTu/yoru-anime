package app.yoru.anime;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackProgressTest {
    @Test public void preservesPositionInMiddleOfEpisode(){assertEquals(45000,PlaybackProgress.resume(45000,1500000));}
    @Test public void restartsCompletedEpisode(){assertEquals(0,PlaybackProgress.resume(1500000,1500000));assertEquals(0,PlaybackProgress.resume(1495000,1500000));}
    @Test public void unknownDurationDoesNotLoseSavedPosition(){assertEquals(45000,PlaybackProgress.resume(45000,-1));}
    @Test public void seekingStaysInsideMediaBounds(){assertEquals(0,PlaybackProgress.seek(5000,-10000,1500000));assertEquals(1500000,PlaybackProgress.seek(1499000,10000,1500000));}
    @Test public void clockSupportsLongEpisodes(){assertEquals("00:00",PlaybackProgress.time(-1));assertEquals("02:03",PlaybackProgress.time(123000));assertEquals("1:01:01",PlaybackProgress.time(3661000));}
}
