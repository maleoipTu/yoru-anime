package app.yoru.anime;

import android.app.*;
import android.content.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import org.json.JSONObject;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class AppDesignSmokeTest {
 MainActivity main,cover;DetailActivity detail;Library library;String historyBefore;boolean addedFavorite;Anime anime;
 @Test(timeout=180000) public void catalogDetailsStateAndEmptyViews()throws Exception{
  Assume.assumeTrue("Pass live=true for UI screenshots","true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("live")));
  Context context=DesignChecks.I.getTargetContext();library=new Library(context);historyBefore=library.prefs.getString("history",null);
  JSONObject raw=Api.get("anime/10661?need_videos=true").getJSONObject("response");Api.save(context,raw);anime=new Anime(raw);Episode first=null,second=null;for(Episode episode:Api.videos(raw))if(episode.voice.contains("AniLibria")&&StreamResolver.supports(episode.url)){if(episode.number.equals("1"))first=episode;if(episode.number.equals("2"))second=episode;}assertNotNull(first);assertNotNull(second);library.opened(anime,first);
  main=(MainActivity)DesignChecks.I.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));DesignChecks.await(()->!main.loading&&!main.shown.isEmpty(),45000);DesignChecks.await(()->main.grid.getChildCount()>0&&((MainActivity.Holder)main.grid.getChildAt(0).getTag()).poster.getDrawable() instanceof BitmapDrawable,25000);DesignChecks.screenshot("catalog-portrait.png");
  DesignChecks.onUi(()->main.order.performClick());DesignChecks.tapChoice("Сначала новые");DesignChecks.onUi(()->main.filter.performClick());DesignChecks.tapChoice("Вышедшие");DesignChecks.await(()->!main.loading,45000);DesignChecks.onUi(()->main.more.performClick());DesignChecks.await(()->!main.loading&&main.shown.size()>24,45000);DesignChecks.onUi(()->main.grid.setSelection(34));DesignChecks.I.waitForIdleSync();
  Instrumentation.ActivityMonitor monitor=DesignChecks.I.addMonitor(MainActivity.class.getName(),null,false);DesignChecks.onUi(()->main.recreate());MainActivity recreated=(MainActivity)DesignChecks.I.waitForMonitorWithTimeout(monitor,10000);DesignChecks.I.removeMonitor(monitor);assertNotNull(recreated);main=recreated;DesignChecks.await(()->!main.loading,45000);DesignChecks.onUi(()->{assertTrue(main.shown.size()>24);assertTrue(main.grid.getFirstVisiblePosition()>=30);assertEquals("year",main.sort);assertEquals("released",main.state);assertEquals("Сначала новые",main.order.getText().toString());assertEquals("Вышедшие",main.filter.getText().toString());});
  detail=(DetailActivity)DesignChecks.I.startActivitySync(new Intent(context,DetailActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("anime",anime.raw.toString()));DesignChecks.await(()->!detail.videos.isEmpty()&&detail.watch.isEnabled(),45000);DesignChecks.screenshot("anime-detail.png");
  Episode choice=second;DesignChecks.onUi(()->{detail.voice=choice.voice;detail.player=choice.player;detail.number=choice.number;detail.update();});
  cover=(MainActivity)DesignChecks.I.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));DesignChecks.onUi(()->cover.finish());cover=null;SystemClock.sleep(600);DesignChecks.onUi(()->{assertEquals("2",detail.number);assertEquals(choice.id,detail.selected.id);});
  Anime actual=detail.anime;
  DesignChecks.onUi(()->{try{JSONObject empty=new JSONObject(actual.raw.toString());empty.put("description","");empty.put("rating",new JSONObject().put("average",0));detail.anime=new Anime(empty);detail.updateDescription();assertEquals("Описание пока не добавлено.",detail.description.getText().toString());assertEquals("Без оценки",detail.anime.score());empty.put("description","Первая строка<br>Вторая строка<br>Третья строка<br>Четвёртая строка<br>Пятая строка");detail.anime=new Anime(empty);detail.expanded=false;detail.updateDescription();}catch(Exception exception){throw new AssertionError(exception);}});
  DesignChecks.await(()->detail.expand.getVisibility()==View.VISIBLE,5000);DesignChecks.onUi(()->{detail.expand.performClick();assertEquals(Integer.MAX_VALUE,detail.description.getMaxLines());detail.anime=actual;detail.expanded=false;detail.updateDescription();if(!library.favorite(anime.id)){detail.favorite.performClick();addedFavorite=true;}detail.finish();});detail=null;
  DesignChecks.onUi(()->main.nav.getChildAt(2).performClick());DesignChecks.await(()->!main.loading&&!main.shown.isEmpty(),10000);DesignChecks.screenshot("favorites.png");
  DesignChecks.onUi(()->{main.search.setText("not-found-yoru-987654321");main.performSearch();});DesignChecks.await(()->!main.loading&&main.shown.isEmpty(),10000);DesignChecks.screenshot("empty-library-search.png");
  DesignChecks.onUi(()->{main.generation++;main.showLoadError(new java.io.IOException("Не удалось связаться с сервисом. Проверь подключение и попробуй ещё раз."));main.showLoadError(new java.io.IOException("Не удалось связаться с сервисом. Проверь подключение и попробуй ещё раз."));assertEquals(3,main.errorPanel.getChildCount());});DesignChecks.await(()->{android.graphics.Rect visible=new android.graphics.Rect();return main.retry.getGlobalVisibleRect(visible)&&visible.height()>=Ui.dp(main,48);},5000);DesignChecks.onUi(()->DesignChecks.target(main.retry));DesignChecks.screenshot("catalog-error.png");
 }
 @After public void close(){if(cover!=null)DesignChecks.onUi(()->cover.finish());if(detail!=null)DesignChecks.onUi(()->detail.finish());if(main!=null)DesignChecks.onUi(()->main.finish());if(library!=null){if(historyBefore==null)library.prefs.edit().remove("history").apply();else library.prefs.edit().putString("history",historyBefore).apply();if(addedFavorite)library.toggle(anime);}}
}
