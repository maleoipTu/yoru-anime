package app.yoru.anime;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Optional real-network smoke test: connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.live=true */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class NativePlayerSmokeTest {
    Instrumentation instrumentation;
    PlayerActivity activity;
    @Test public void playbackControlsLifecycleAndEpisodeTransition() throws Exception {
        Assume.assumeTrue("Pass live=true to use the public video service", "true".equals(InstrumentationRegistry.getArguments().getString("live")));
        instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        JSONObject raw=Api.get("anime/10661?need_videos=true").getJSONObject("response");Api.save(context,raw);
        List<Episode> videos=Api.videos(raw);Episode first=null;
        for(Episode episode:videos)if(episode.number.equals("1")&&episode.voice.contains("AniLibria")&&StreamResolver.supports(episode.url)){first=episode;break;}
        assertNotNull("Live Kodik source required",first);new Library(context).position(first.id,45000);
        Intent intent=new Intent(context,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("anime_id",10661L).putExtra("video_id",first.id);
        activity=(PlayerActivity)instrumentation.startActivitySync(intent);
        await(()->activity.engine!=null&&activity.engine.isPlaying()&&activity.engine.getVideoSize().width>0&&activity.engine.getCurrentPosition()>46000,90000);
        instrumentation.runOnMainSync(()->{assertFalse(activity.failed);assertEquals("1",activity.current.number);assertTrue(activity.resolved.streams.size()>=3);assertTrue(activity.surface.getWidth()>0);activity.showControls();});
        screenshot("native-player-portrait.png");
        instrumentation.runOnMainSync(()->activity.play.performClick());
        await(()->!activity.engine.getPlayWhenReady(),5000);
        instrumentation.runOnMainSync(()->{activity.seek(10000);activity.savePosition();assertTrue(activity.library.position(activity.current.id)>55000);activity.play.performClick();});
        await(()->activity.engine.isPlaying(),10000);
        instrumentation.runOnMainSync(()->{activity.savePosition();activity.releaseEngine();activity.rendition=activity.resolved.streams.size()-1;activity.startEngine();});
        await(()->activity.engine!=null&&activity.engine.isPlaying()&&activity.engine.getCurrentPosition()>55000,30000);
        instrumentation.runOnMainSync(()->activity.setFullscreen(true));SystemClock.sleep(2000);
        instrumentation.runOnMainSync(()->{activity.showControls();assertTrue(activity.fullscreen);assertEquals(android.view.View.GONE,activity.details.getVisibility());assertTrue(activity.surface.getWidth()>activity.surface.getHeight());});
        screenshot("native-player-landscape.png");
        instrumentation.runOnMainSync(()->{activity.setFullscreen(false);activity.next.performClick();});
        await(()->activity.engine!=null&&activity.engine.isPlaying()&&activity.current.number.equals("2"),60000);
        instrumentation.runOnMainSync(()->{assertEquals("2",activity.current.number);assertTrue(activity.previous.isEnabled());activity.savePosition();activity.showControls();});
        screenshot("native-player-episode-two.png");
    }
    void await(BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;AtomicBoolean ready=new AtomicBoolean();while(SystemClock.uptimeMillis()<end){instrumentation.runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;SystemClock.sleep(300);}throw new AssertionError("Native player did not reach expected state within "+timeout+" ms");}
    void screenshot(String name)throws IOException{Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);File directory=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"qa");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    @After public void close()throws Exception{if(activity!=null){screenshot("native-player-final-state.png");instrumentation.runOnMainSync(()->activity.finish());}}
}
