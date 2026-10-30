package app.yoru.anime;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
 LinearLayout root,nav,filters,section,errorPanel,emptyPanel; FrameLayout content;
 TextView status,sectionTitle,subtitle,emptyTitle,emptyHint,errorText;
 EditText search; GridView grid; Button more,order,filter,retry;
 ProgressBar spinner;
 Library library; List<Anime> shown=new ArrayList<>();
 String page="catalog",query="",sort="rating",state="";
 int offset,generation,restoreScroll; boolean firstResume=true,loading,hasMore,failedAppend;
 final String[] keys={"catalog","schedule","favorites","history"};

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);library=new Library(this);
  if(saved!=null){page=saved.getString("page","catalog");query=saved.getString("query","");sort=saved.getString("sort","rating");state=saved.getString("state","");restoreScroll=saved.getInt("scroll");}
  root=Ui.root(this);
  LinearLayout header=Ui.padded(this,20);header.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12));
  LinearLayout brand=Ui.row(this);ImageView moon=new ImageView(this);moon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.ACCENT,26));moon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);brand.addView(moon,new LinearLayout.LayoutParams(Ui.dp(this,28),Ui.dp(this,28)));
  TextView name=Ui.title(this,"yoru",27);name.setLetterSpacing(.025f);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=Ui.dp(this,8);brand.addView(name,bp);header.addView(brand);
  subtitle=Ui.text(this,"Найди свою следующую историю",13,Ui.MUTED);Ui.add(header,subtitle,6);
  LinearLayout searchRow=Ui.row(this);searchRow.setPadding(Ui.dp(this,12),0,Ui.dp(this,2),0);searchRow.setBackground(Ui.shape(this,Ui.PANEL,14));
  ImageView magnifier=new ImageView(this);magnifier.setImageDrawable(new Ui.AppIcon(this,"search",Ui.MUTED,21));magnifier.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchRow.addView(magnifier,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)));
  search=new EditText(this);search.setTextColor(Ui.TEXT);search.setTextSize(15);search.setHint("Поиск аниме");search.setContentDescription("Поиск аниме по названию");search.setHintTextColor(Ui.MUTED);search.setSingleLine(true);search.setBackgroundColor(android.graphics.Color.TRANSPARENT);search.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(query);searchRow.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
  Button find=Ui.iconButton(this,"chevron","Найти аниме");searchRow.addView(find);find.setOnClickListener(v->performSearch());search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){performSearch();return true;}return false;});Ui.add(header,searchRow,18);root.addView(header);
  filters=Ui.row(this);filters.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,12));order=Ui.button(this,sortLabel(),false);filter=Ui.button(this,stateLabel(),false);order.setTextSize(13);filter.setTextSize(13);order.setCompoundDrawablesWithIntrinsicBounds(null,null,new Ui.AppIcon(this,"down",Ui.MUTED,16),null);filter.setCompoundDrawablesWithIntrinsicBounds(null,null,new Ui.AppIcon(this,"down",Ui.MUTED,16),null);
  filters.addView(order,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);fp.leftMargin=Ui.dp(this,8);filters.addView(filter,fp);
  order.setOnClickListener(v->Ui.choose(this,"Сортировка",Arrays.asList("По рейтингу","По популярности","Сначала новые"),sortLabel(),choice->{sort=choice.equals("По рейтингу")?"rating":choice.equals("По популярности")?"views":"year";order.setText(sortLabel());load(false);}));
  filter.setOnClickListener(v->Ui.choose(this,"Статус",Arrays.asList("Все статусы","Вышедшие","Онгоинги","Анонсы"),stateLabel(),choice->{state=choice.equals("Вышедшие")?"released":choice.equals("Онгоинги")?"ongoing":choice.equals("Анонсы")?"announcement":"";filter.setText(stateLabel());load(false);}));root.addView(filters);
  section=Ui.row(this);section.setPadding(Ui.dp(this,20),Ui.dp(this,6),Ui.dp(this,20),Ui.dp(this,14));sectionTitle=Ui.title(this,"Каталог",21);section.addView(sectionTitle,new LinearLayout.LayoutParams(0,-2,1));status=Ui.text(this,"",12,Ui.MUTED);section.addView(status);root.addView(section);
  errorPanel=Ui.padded(this,16);errorPanel.setBackground(Ui.shape(this,Ui.PANEL,14));errorPanel.addView(Ui.title(this,"Не получилось загрузить",16));errorText=Ui.text(this,"",13,Ui.MUTED);Ui.add(errorPanel,errorText,8);retry=Ui.textButton(this,"Повторить загрузку");Ui.add(errorPanel,retry,6);retry.setOnClickListener(v->load(failedAppend));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,12));root.addView(errorPanel,ep);errorPanel.setVisibility(View.GONE);
  content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  grid=new GridView(this);grid.setNumColumns(columns());grid.setHorizontalSpacing(Ui.dp(this,14));grid.setVerticalSpacing(Ui.dp(this,20));grid.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,16));grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setAdapter(new CatalogAdapter());content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
  emptyPanel=Ui.padded(this,28);emptyPanel.setGravity(Gravity.CENTER);ImageView emptyIcon=new ImageView(this);emptyIcon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.MUTED,40));emptyPanel.addView(emptyIcon,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,56)));emptyTitle=Ui.title(this,"Здесь пока тихо",20);emptyTitle.setGravity(Gravity.CENTER);Ui.add(emptyPanel,emptyTitle,16);emptyHint=Ui.text(this,"",14,Ui.MUTED);emptyHint.setGravity(Gravity.CENTER);Ui.add(emptyPanel,emptyHint,10);content.addView(emptyPanel,new FrameLayout.LayoutParams(-1,-1));emptyPanel.setVisibility(View.GONE);
  spinner=new ProgressBar(this);content.addView(spinner,new FrameLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36),Gravity.CENTER));
  more=Ui.textButton(this,"Показать ещё");more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));root.addView(more,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
  root.addView(Ui.divider(this));nav=Ui.row(this);nav.setPadding(Ui.dp(this,8),Ui.dp(this,5),Ui.dp(this,8),Ui.dp(this,5));String[] labels={"Каталог","Расписание","Избранное","История"},icons={"catalog","calendar","heart","history"};
  for(int i=0;i<keys.length;i++){String target=keys[i];Button button=Ui.button(this,labels[i],false);button.setTextSize(10);button.setMaxLines(1);button.setPadding(Ui.dp(this,2),Ui.dp(this,7),Ui.dp(this,2),Ui.dp(this,6));button.setCompoundDrawablePadding(Ui.dp(this,4));button.setTag(icons[i]);nav.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));button.setOnClickListener(v->{if(page.equals(target))return;page=target;query="";search.setText("");load(false);});}root.addView(nav);Object retained=getLastNonConfigurationInstance();if(retained instanceof CatalogState){CatalogState snapshot=(CatalogState)retained;if(snapshot.page.equals(page)&&snapshot.query.equals(query)&&snapshot.sort.equals(sort)&&snapshot.state.equals(state)){shown.addAll(snapshot.items);offset=snapshot.offset;hasMore=snapshot.hasMore;updateChrome();render();grid.setSelection(snapshot.first);restoreScroll=0;}else load(false);}else load(false);
 }
 static final class CatalogState {
  final List<Anime>items;final String page,query,sort,state;final int offset,first;final boolean hasMore;
  CatalogState(MainActivity activity){items=new ArrayList<>(activity.shown);page=activity.page;query=activity.query;sort=activity.sort;state=activity.state;offset=activity.offset;first=activity.grid.getFirstVisiblePosition();hasMore=activity.hasMore;}
 }
 @Override public Object onRetainNonConfigurationInstance(){return loading&&shown.isEmpty()?null:new CatalogState(this);}
 String sortLabel(){return sort.equals("views")?"По популярности":sort.equals("year")?"Сначала новые":"По рейтингу";}
 String stateLabel(){return state.equals("released")?"Вышедшие":state.equals("ongoing")?"Онгоинги":state.equals("announcement")?"Анонсы":"Все статусы";}
 int columns(){int width=getResources().getConfiguration().screenWidthDp;return width>=700?4:width>=520?3:2;}
 @Override protected void onSaveInstanceState(Bundle stateBundle){stateBundle.putString("page",page);stateBundle.putString("query",query);stateBundle.putString("sort",sort);stateBundle.putString("state",state);stateBundle.putInt("scroll",grid.getFirstVisiblePosition());super.onSaveInstanceState(stateBundle);}
 @Override protected void onResume(){super.onResume();if(firstResume){firstResume=false;return;}if(library!=null&&(page.equals("favorites")||page.equals("history")))load(false);}
 void performSearch(){query=search.getText().toString().trim();if(page.equals("schedule"))page="catalog";load(false);InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
 void updateChrome(){filters.setVisibility(page.equals("catalog")?View.VISIBLE:View.GONE);sectionTitle.setText(page.equals("favorites")?"Избранное":page.equals("history")?"История":page.equals("schedule")?"Расписание":query.isEmpty()?"Каталог":"Результаты поиска");subtitle.setText(page.equals("schedule")?"Новые серии и ближайшие премьеры":page.equals("favorites")?"Истории, к которым хочется вернуться":page.equals("history")?"Продолжай с того места, где остановился":"Найди свою следующую историю");for(int i=0;i<nav.getChildCount();i++){Button b=(Button)nav.getChildAt(i);boolean active=keys[i].equals(page);b.setSelected(active);b.setTextColor(active?Ui.ACCENT:Ui.MUTED);b.setCompoundDrawablesWithIntrinsicBounds(null,new Ui.AppIcon(this,(String)b.getTag(),active?Ui.ACCENT:Ui.MUTED,21),null,null);b.setBackground(Ui.ripple(this,active?Ui.SOFT:android.graphics.Color.TRANSPARENT,16));b.setContentDescription(b.getText()+(active?", выбранный раздел":""));}}
 void load(boolean append){
  int request=++generation;loading=true;failedAppend=append;updateChrome();errorPanel.setVisibility(View.GONE);more.setEnabled(false);retry.setEnabled(false);
  if(!append){shown.clear();offset=0;hasMore=false;((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();grid.setSelection(0);}spinner.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyPanel.setVisibility(View.GONE);status.setText(append?"Загружаем…":"");more.setVisibility(append?View.VISIBLE:View.GONE);
  String requestedPage=page,requestedQuery=query;int start=offset;String path=page.equals("schedule")?"anime/schedule":"anime?limit=24&sort="+sort+"&sort_forward=false&offset="+start+"&q="+Api.encode(query)+(state.isEmpty()?"":"&status="+state);
  Api.IO.execute(()->{try{
   List<Anime> data=requestedPage.equals("favorites")?library.favorites():requestedPage.equals("history")?library.history():Api.catalog(Api.get(path));
   if(requestedPage.equals("favorites")||requestedPage.equals("history"))data.removeIf(anime->!anime.title.toLowerCase(Locale.ROOT).contains(requestedQuery.toLowerCase(Locale.ROOT)));
   if(requestedPage.equals("schedule"))data.sort(Comparator.comparingLong(anime->anime.nextDate>0?anime.nextDate:Long.MAX_VALUE));
   List<Anime> result=requestedPage.equals("schedule")?new ArrayList<>(data.subList(Math.min(start,data.size()),Math.min(start+24,data.size()))):data;
   runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;offset=start+result.size();Set<Long>ids=new HashSet<>();for(Anime anime:shown)ids.add(anime.id);for(Anime anime:result)if(ids.add(anime.id))shown.add(anime);hasMore=(requestedPage.equals("catalog")||requestedPage.equals("schedule"))&&result.size()==24;render();if(restoreScroll>0){grid.setSelection(Math.min(restoreScroll,Math.max(0,shown.size()-1)));restoreScroll=0;}});
  }catch(Exception exception){runOnUiThread(()->{if(isDestroyed()||request!=generation)return;showLoadError(exception);});}});
 }
 void showLoadError(Exception exception){loading=false;spinner.setVisibility(View.GONE);emptyPanel.setVisibility(View.GONE);retry.setEnabled(true);errorText.setText(exception instanceof java.io.IOException?exception.getMessage():"Не получилось загрузить аниме. Проверь подключение и попробуй ещё раз.");ViewGroup oldParent=(ViewGroup)errorPanel.getParent();if(shown.isEmpty()&&oldParent!=content){oldParent.removeView(errorPanel);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);ep.leftMargin=Ui.dp(this,20);ep.rightMargin=Ui.dp(this,20);content.addView(errorPanel,ep);}else if(!shown.isEmpty()&&oldParent!=root){oldParent.removeView(errorPanel);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,12));root.addView(errorPanel,root.indexOfChild(content),ep);}errorPanel.setVisibility(View.VISIBLE);status.setText(shown.isEmpty()?"":"На экране: "+shown.size());more.setEnabled(true);more.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);}
 void render(){spinner.setVisibility(View.GONE);errorPanel.setVisibility(View.GONE);status.setText(shown.isEmpty()?"":Integer.toString(shown.size()));more.setEnabled(true);more.setVisibility(hasMore?View.VISIBLE:View.GONE);emptyPanel.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyTitle.setText(query.isEmpty()?(page.equals("favorites")?"Сохрани любимые истории":page.equals("history")?"Начни свою историю":"Пока ничего нет"):"Ничего не нашлось");emptyHint.setText(!query.isEmpty()?((page.equals("favorites")||page.equals("history"))?"Попробуй другое название или очисти поиск.":"Попробуй другое название или измени фильтры."):page.equals("favorites")?"Нажми на сердце в карточке аниме — оно появится здесь.":page.equals("history")?"Открой серию, и мы сохраним её здесь для продолжения.":"Загляни сюда немного позже.");((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();}
 final class Holder{LinearLayout card;ImageView poster;TextView score,name,meta,note;}
 final class CatalogAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int position){return shown.get(position);}public long getItemId(int position){return shown.get(position).id;}public boolean hasStableIds(){return true;}
  public View getView(int position,View recycled,ViewGroup parent){
   Holder holder;
   if(recycled==null){holder=new Holder();holder.card=Ui.column(MainActivity.this);holder.card.setBackground(Ui.ripple(MainActivity.this,android.graphics.Color.TRANSPARENT,14));FrameLayout art=new FrameLayout(MainActivity.this);Ui.round(art,14);int height=Math.round((getResources().getConfiguration().screenWidthDp-40-(columns()-1)*14f)/columns()*1.4f);holder.card.addView(art,new LinearLayout.LayoutParams(-1,Ui.dp(MainActivity.this,height)));holder.poster=new ImageView(MainActivity.this);art.addView(holder.poster,new FrameLayout.LayoutParams(-1,-1));holder.score=Ui.title(MainActivity.this,"",12);holder.score.setTextColor(Ui.TEXT);holder.score.setPadding(Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,5),Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,5));holder.score.setBackground(Ui.shape(MainActivity.this,0xDD171823,8));FrameLayout.LayoutParams rating=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);rating.setMargins(Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,8),0);art.addView(holder.score,rating);
    holder.name=Ui.title(MainActivity.this,"",14);holder.name.setMaxLines(2);holder.name.setMinLines(2);holder.name.setEllipsize(TextUtils.TruncateAt.END);Ui.add(holder.card,holder.name,10);holder.meta=Ui.text(MainActivity.this,"",12,Ui.MUTED);holder.meta.setMaxLines(1);holder.meta.setEllipsize(TextUtils.TruncateAt.END);Ui.add(holder.card,holder.meta,5);holder.note=Ui.text(MainActivity.this,"",12,Ui.ACCENT);holder.note.setMaxLines(2);Ui.add(holder.card,holder.note,5);holder.card.setTag(holder);recycled=holder.card;
   }else holder=(Holder)recycled.getTag();
   Anime anime=shown.get(position);holder.name.setText(anime.title);holder.score.setText(anime.score());holder.score.setVisibility(anime.rating>0?View.VISIBLE:View.GONE);holder.meta.setText((anime.year>0?anime.year+" · ":"")+anime.type);holder.poster.setContentDescription(anime.title);Ui.poster(MainActivity.this,holder.poster,anime.poster);String note="";
   if(page.equals("schedule"))note=anime.nextDate>0?java.time.Instant.ofEpochSecond(anime.nextDate).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm",new Locale("ru"))):"Дата пока неизвестна";
   else if(page.equals("history")){JSONObject last=library.last(anime.id);if(last!=null)note="Продолжить · серия "+last.optString("number");}
   holder.note.setText(note);holder.note.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);holder.card.setClickable(true);holder.card.setFocusable(true);holder.card.setContentDescription(anime.title+". "+anime.score());holder.card.setOnClickListener(v->startActivity(new Intent(MainActivity.this,DetailActivity.class).putExtra("anime",anime.raw.toString())));return recycled;
  }
 }
}
