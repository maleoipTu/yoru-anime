package app.yoru.anime;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class NativePlayerSmokeTest {
 PlayerActivity activity;
 @Test public void playbackControlsLifecycleAndEpisodeTransition()throws Exception{
  Assume.assumeTrue("Pass live=true to test the video service","true".equals(InstrumentationRegistry.getArguments().getString("live")));
  Context context=DesignChecks.I.getTargetContext();JSONObject raw=Api.get("anime/10661?need_videos=true").getJSONObject("response");Api.save(context,raw);List<Episode>videos=Api.videos(raw);Episode first=null;for(Episode episode:videos)if(episode.number.equals("1")&&episode.voice.contains("AniLibria")&&StreamResolver.supports(episode.url)){first=episode;break;}assertNotNull(first);new Library(context).position(first.id,45000);
  activity=(PlayerActivity)DesignChecks.I.startActivitySync(new Intent(context,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("anime_id",10661L).putExtra("video_id",first.id));
  DesignChecks.await(()->activity.engine!=null&&activity.engine.isPlaying()&&activity.engine.getVideoSize().width>0&&activity.engine.getCurrentPosition()>46000,90000);
  DesignChecks.onUi(()->{assertFalse(activity.failed);assertEquals("1",activity.current.number);activity.showControls();DesignChecks.target(activity.play);DesignChecks.target(activity.timeline);DesignChecks.target(activity.full);assertTrue(activity.details.getHeight()>Ui.dp(activity,100));});DesignChecks.screenshot("native-player-portrait.png");
  // A system pause (e.g. unplugged headphones) must survive a quality change.
  DesignChecks.onUi(()->{activity.engine.pause();assertTrue(activity.desiredPlay);activity.changeQuality(activity.resolved.streams.size()-1);});
  DesignChecks.await(()->activity.engine!=null&&activity.engine.getPlaybackState()==androidx.media3.common.Player.STATE_READY,30000);
  DesignChecks.onUi(()->{assertFalse(activity.engine.getPlayWhenReady());assertFalse(activity.desiredPlay);assertTrue(activity.engine.getCurrentPosition()>=45000);activity.seek(10000);activity.savePosition();assertTrue(activity.library.position(activity.current.id)>=55000);activity.play.performClick();});DesignChecks.await(()->activity.engine!=null&&activity.engine.isPlaying(),10000);
  DesignChecks.onUi(()->activity.openSettings());DesignChecks.screenshot("native-player-settings.png");DesignChecks.onUi(()->activity.settingsDialog.dismiss());
  DesignChecks.onUi(()->activity.setFullscreen(true));SystemClock.sleep(1500);DesignChecks.onUi(()->{activity.showControls();assertTrue(activity.surface.getWidth()>activity.surface.getHeight());assertEquals(View.GONE,activity.details.getVisibility());DesignChecks.target(activity.timeline);});DesignChecks.screenshot("native-player-landscape.png");
  DesignChecks.onUi(()->activity.setFullscreen(false));SystemClock.sleep(1000);DesignChecks.onUi(()->activity.chooseSeries());DesignChecks.tapChoice("Серия 2");
  DesignChecks.await(()->activity.engine!=null&&activity.engine.isPlaying()&&activity.current.number.equals("2")&&activity.engine.getVideoSize().width>0&&activity.engine.getCurrentPosition()>1500,60000);DesignChecks.onUi(()->{assertTrue(activity.previous.isEnabled());activity.showControls();});DesignChecks.screenshot("native-player-episode-two.png");
  // Rotation outside fullscreen must leave a usable scrolling panel.
  DesignChecks.onUi(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));SystemClock.sleep(1500);DesignChecks.onUi(()->{assertFalse(activity.fullscreen);assertTrue(activity.details.getHeight()>=Ui.dp(activity,70));DesignChecks.target(activity.timeline);activity.showControls();});DesignChecks.screenshot("native-player-windowed-landscape.png");
  DesignChecks.onUi(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));SystemClock.sleep(1000);
  DesignChecks.onUi(()->activity.showError("Сервис временно недоступен. Проверь подключение и попробуй снова. Если выбранный источник больше не работает, открой настройки просмотра и выбери другую озвучку или источник. Позиция серии сохранена."));DesignChecks.onUi(()->DesignChecks.target(DesignChecks.findText(activity.error,"Повторить")));DesignChecks.screenshot("native-player-error.png");
 }
 @After public void close(){if(activity!=null)DesignChecks.onUi(()->activity.finish());}
}
