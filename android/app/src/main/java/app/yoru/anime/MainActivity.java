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
 EditText search; GridView grid; Button more,order,filter,retry,clear;
 static final int GAP=12;
 ProgressBar spinner;
 Library library; List<Anime> shown=new ArrayList<>();
 String page="catalog",query="",sort="rating",state="";
 int offset,generation,restoreScroll; boolean firstResume=true,loading,hasMore,failedAppend;
 final String[] keys={"catalog","schedule","favorites","history"};

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);library=new Library(this);
  if(saved!=null){page=saved.getString("page","catalog");query=saved.getString("query","");sort=saved.getString("sort","rating");state=saved.getString("state","");restoreScroll=saved.getInt("scroll");}
  root=Ui.root(this);
  // Шапка: раздел и подзаголовок рядом с «луной», счётчик справа, поиск-пилюля и фильтры-чипы.
  LinearLayout header=Ui.column(this);header.setPadding(Ui.dp(this,16),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,4));
  LinearLayout brand=Ui.row(this);ImageView moon=new ImageView(this);moon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.ACCENT,22));moon.setScaleType(ImageView.ScaleType.CENTER);moon.setBackground(Ui.shape(this,Ui.SOFT,20));moon.setContentDescription("yoru");brand.addView(moon,new LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40)));
  LinearLayout heading=Ui.column(this);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,12);brand.addView(heading,hp);
  sectionTitle=Ui.title(this,"Каталог",22);sectionTitle.setMaxLines(1);sectionTitle.setEllipsize(TextUtils.TruncateAt.END);if(android.os.Build.VERSION.SDK_INT>=28)sectionTitle.setAccessibilityHeading(true);heading.addView(sectionTitle);
  subtitle=Ui.text(this,"Найди свою следующую историю",13,Ui.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);Ui.add(heading,subtitle,3);
  status=Ui.text(this,"",12,Ui.MUTED);status.setTypeface(Ui.MEDIUM);status.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));status.setBackground(Ui.shape(this,Ui.RAISED,12));status.setVisibility(View.GONE);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-2,-2);stp.leftMargin=Ui.dp(this,8);brand.addView(status,stp);header.addView(brand);
  LinearLayout searchRow=Ui.row(this);searchRow.setPadding(Ui.dp(this,16),0,Ui.dp(this,2),0);searchRow.setBackground(Ui.outlined(this,Ui.PANEL,24));
  ImageView magnifier=new ImageView(this);magnifier.setImageDrawable(new Ui.AppIcon(this,"search",Ui.MUTED,20));magnifier.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchRow.addView(magnifier,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));
  search=new EditText(this);search.setTextColor(Ui.TEXT);search.setTextSize(15);search.setHint("Поиск аниме");search.setContentDescription("Поиск аниме по названию");search.setHintTextColor(Ui.MUTED);search.setSingleLine(true);search.setBackgroundColor(android.graphics.Color.TRANSPARENT);search.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(query);searchRow.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
  clear=Ui.iconButton(this,"close","Очистить поиск");Ui.icon(clear,"close",Ui.MUTED);clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);searchRow.addView(clear);clear.setOnClickListener(v->{search.setText("");if(!query.isEmpty())performSearch();});
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int b,int c){}public void onTextChanged(CharSequence t,int a,int b,int c){}public void afterTextChanged(android.text.Editable t){clear.setVisibility(t.length()==0?View.GONE:View.VISIBLE);}});
  search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){performSearch();return true;}return false;});Ui.add(header,searchRow,14);root.addView(header);
  filters=Ui.row(this);filters.setPadding(Ui.dp(this,16),Ui.dp(this,2),Ui.dp(this,16),Ui.dp(this,4));order=Ui.chip(this,sortLabel());filter=Ui.chip(this,stateLabel());
  filters.addView(order,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,Ui.dp(this,48));fp.leftMargin=Ui.dp(this,8);filters.addView(filter,fp);
  order.setOnClickListener(v->Ui.choose(this,"Сортировка",Arrays.asList("По рейтингу","По популярности","Сначала новые"),sortLabel(),choice->{sort=choice.equals("По рейтингу")?"rating":choice.equals("По популярности")?"views":"year";order.setText(sortLabel());updateChips();load(false);}));
  filter.setOnClickListener(v->Ui.choose(this,"Статус",Arrays.asList("Все статусы","Вышедшие","Онгоинги","Анонсы"),stateLabel(),choice->{state=choice.equals("Вышедшие")?"released":choice.equals("Онгоинги")?"ongoing":choice.equals("Анонсы")?"announcement":"";filter.setText(stateLabel());updateChips();load(false);}));
  HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);filterScroll.addView(filters);root.addView(filterScroll,new LinearLayout.LayoutParams(-1,-2));updateChips();
  section=Ui.row(this);section.setVisibility(View.GONE);
  errorPanel=Ui.padded(this,16);errorPanel.setBackground(Ui.outlined(this,Ui.PANEL,18));errorPanel.addView(Ui.title(this,"Не получилось загрузить",16));errorText=Ui.text(this,"",14,Ui.MUTED);Ui.add(errorPanel,errorText,8);retry=Ui.tonal(this,"Повторить загрузку");Ui.icon(retry,"retry",Ui.ACCENT);retry.setCompoundDrawablePadding(Ui.dp(this,8));Ui.add(errorPanel,retry,14);retry.setOnClickListener(v->load(failedAppend));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,12));root.addView(errorPanel,ep);errorPanel.setVisibility(View.GONE);
  content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  grid=new GridView(this);grid.setNumColumns(columns());grid.setHorizontalSpacing(Ui.dp(this,GAP));grid.setVerticalSpacing(Ui.dp(this,18));grid.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,20));grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setVerticalScrollBarEnabled(false);grid.setAdapter(new CatalogAdapter());content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
  // Следующая страница подгружается сама, когда до конца списка остаётся меньше двух рядов.
  grid.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int scrollState){}public void onScroll(AbsListView view,int first,int visible,int total){if(total>0&&first+visible>=total-columns()*2&&hasMore&&!loading&&errorPanel.getVisibility()!=View.VISIBLE)load(true);}});
  emptyPanel=Ui.padded(this,32);emptyPanel.setGravity(Gravity.CENTER);ImageView emptyIcon=new ImageView(this);emptyIcon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.ACCENT,36));emptyIcon.setScaleType(ImageView.ScaleType.CENTER);emptyIcon.setBackground(Ui.shape(this,Ui.SOFT,40));emptyIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);emptyPanel.addView(emptyIcon,new LinearLayout.LayoutParams(Ui.dp(this,80),Ui.dp(this,80)));emptyTitle=Ui.title(this,"Здесь пока тихо",20);emptyTitle.setGravity(Gravity.CENTER);Ui.add(emptyPanel,emptyTitle,20);emptyHint=Ui.text(this,"",14,Ui.MUTED);emptyHint.setGravity(Gravity.CENTER);Ui.add(emptyPanel,emptyHint,8);content.addView(emptyPanel,new FrameLayout.LayoutParams(-1,-1));emptyPanel.setVisibility(View.GONE);
  spinner=new ProgressBar(this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));content.addView(spinner,new FrameLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36),Gravity.CENTER));
  more=Ui.textButton(this,"Показать ещё");more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));root.addView(more,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
  root.addView(Ui.divider(this));nav=Ui.row(this);nav.setBackgroundColor(Ui.PANEL);nav.setPadding(Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,4));String[] labels={"Каталог","Расписание","Избранное","История"},icons={"catalog","calendar","heart","history"};
  for(int i=0;i<keys.length;i++){String target=keys[i];Button button=Ui.button(this,labels[i],false);button.setTextSize(11);button.setMaxLines(1);button.setPadding(Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2),Ui.dp(this,4));button.setCompoundDrawablePadding(Ui.dp(this,4));button.setTag(icons[i]);nav.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));button.setOnClickListener(v->{if(page.equals(target))return;page=target;query="";search.setText("");load(false);});}root.addView(nav);Object retained=getLastNonConfigurationInstance();if(retained instanceof CatalogState){CatalogState snapshot=(CatalogState)retained;if(snapshot.page.equals(page)&&snapshot.query.equals(query)&&snapshot.sort.equals(sort)&&snapshot.state.equals(state)){shown.addAll(snapshot.items);offset=snapshot.offset;hasMore=snapshot.hasMore;updateChrome();render();grid.setSelection(snapshot.first);restoreScroll=0;}else load(false);}else load(false);
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
 void updateChrome(){filters.setVisibility(page.equals("catalog")?View.VISIBLE:View.GONE);((View)filters.getParent()).setVisibility(page.equals("catalog")?View.VISIBLE:View.GONE);sectionTitle.setText(page.equals("favorites")?"Избранное":page.equals("history")?"История":page.equals("schedule")?"Расписание":query.isEmpty()?"Каталог":"Результаты поиска");subtitle.setText(!query.isEmpty()&&!page.equals("schedule")?"По запросу «"+query+"»":page.equals("schedule")?"Новые серии и ближайшие премьеры":page.equals("favorites")?"Истории, к которым хочется вернуться":page.equals("history")?"Продолжай с того места, где остановился":"Найди свою следующую историю");for(int i=0;i<nav.getChildCount();i++){Button b=(Button)nav.getChildAt(i);boolean active=keys[i].equals(page);b.setSelected(active);b.setTextColor(active?Ui.TEXT:Ui.MUTED);b.setTypeface(active?android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD):Ui.MEDIUM);b.setCompoundDrawablesWithIntrinsicBounds(null,new Ui.NavIcon(this,(String)b.getTag(),active),null,null);b.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,16));b.setContentDescription(b.getText()+(active?", выбранный раздел":""));}}
 void updateChips(){Ui.chipState(order,!sort.equals("rating"));Ui.chipState(filter,!state.isEmpty());}
 void setStatus(String value){status.setText(value);status.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
 void load(boolean append){
  int request=++generation;loading=true;failedAppend=append;updateChrome();errorPanel.setVisibility(View.GONE);more.setEnabled(false);retry.setEnabled(false);
  if(!append){shown.clear();offset=0;hasMore=false;((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();grid.setSelection(0);}spinner.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyPanel.setVisibility(View.GONE);setStatus(append?"Загружаем…":"");more.setVisibility(View.GONE);
  String requestedPage=page,requestedQuery=query;int start=offset;String path=page.equals("schedule")?"anime/schedule":"anime?limit=24&sort="+sort+"&sort_forward=false&offset="+start+"&q="+Api.encode(query)+(state.isEmpty()?"":"&status="+state);
  Api.IO.execute(()->{try{
   List<Anime> data=requestedPage.equals("favorites")?library.favorites():requestedPage.equals("history")?library.history():Api.catalog(Api.get(path));
   if(requestedPage.equals("favorites")||requestedPage.equals("history"))data.removeIf(anime->!anime.title.toLowerCase(Locale.ROOT).contains(requestedQuery.toLowerCase(Locale.ROOT)));
   if(requestedPage.equals("schedule"))data.sort(Comparator.comparingLong(anime->anime.nextDate>0?anime.nextDate:Long.MAX_VALUE));
   List<Anime> result=requestedPage.equals("schedule")?new ArrayList<>(data.subList(Math.min(start,data.size()),Math.min(start+24,data.size()))):data;
   runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;offset=start+result.size();Set<Long>ids=new HashSet<>();for(Anime anime:shown)ids.add(anime.id);for(Anime anime:result)if(ids.add(anime.id))shown.add(anime);hasMore=(requestedPage.equals("catalog")||requestedPage.equals("schedule"))&&result.size()==24;render();if(restoreScroll>0){grid.setSelection(Math.min(restoreScroll,Math.max(0,shown.size()-1)));restoreScroll=0;}});
  }catch(Exception exception){runOnUiThread(()->{if(isDestroyed()||request!=generation)return;showLoadError(exception);});}});
 }
 void showLoadError(Exception exception){loading=false;spinner.setVisibility(View.GONE);emptyPanel.setVisibility(View.GONE);retry.setEnabled(true);errorText.setText(exception instanceof java.io.IOException?exception.getMessage():"Не получилось загрузить аниме. Проверь подключение и попробуй ещё раз.");ViewGroup oldParent=(ViewGroup)errorPanel.getParent();if(shown.isEmpty()&&oldParent!=content){oldParent.removeView(errorPanel);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);ep.leftMargin=Ui.dp(this,16);ep.rightMargin=Ui.dp(this,16);content.addView(errorPanel,ep);}else if(!shown.isEmpty()&&oldParent!=root){oldParent.removeView(errorPanel);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,12));root.addView(errorPanel,root.indexOfChild(content),ep);}errorPanel.setVisibility(View.VISIBLE);setStatus(shown.isEmpty()?"":Integer.toString(shown.size()));more.setEnabled(true);more.setVisibility(View.GONE);}
 void render(){spinner.setVisibility(View.GONE);errorPanel.setVisibility(View.GONE);setStatus(shown.isEmpty()?"":Integer.toString(shown.size()));more.setEnabled(true);more.setVisibility(View.GONE);emptyPanel.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyTitle.setText(query.isEmpty()?(page.equals("favorites")?"Сохрани любимые истории":page.equals("history")?"Начни свою историю":"Пока ничего нет"):"Ничего не нашлось");emptyHint.setText(!query.isEmpty()?((page.equals("favorites")||page.equals("history"))?"Попробуй другое название или очисти поиск.":"Попробуй другое название или измени фильтры."):page.equals("favorites")?"Нажми на сердце в карточке аниме — оно появится здесь.":page.equals("history")?"Открой серию, и мы сохраним её здесь для продолжения.":"Загляни сюда немного позже.");((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();}
 final class Holder{LinearLayout card;ImageView poster;TextView score,badge,name,meta,note;}
 final class CatalogAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int position){return shown.get(position);}public long getItemId(int position){return shown.get(position).id;}public boolean hasStableIds(){return true;}
  public View getView(int position,View recycled,ViewGroup parent){
   Holder holder;MainActivity c=MainActivity.this;
   if(recycled==null){holder=new Holder();holder.card=Ui.column(c);holder.card.setBackground(Ui.ripple(c,android.graphics.Color.TRANSPARENT,16));
    FrameLayout art=new FrameLayout(c);Ui.round(art,16);art.setForeground(Ui.outlined(c,android.graphics.Color.TRANSPARENT,16));int height=Math.round((getResources().getConfiguration().screenWidthDp-32-(columns()-1)*(float)GAP)/columns()*1.42f);holder.card.addView(art,new LinearLayout.LayoutParams(-1,Ui.dp(c,height)));
    holder.poster=new ImageView(c);art.addView(holder.poster,new FrameLayout.LayoutParams(-1,-1));
    holder.score=Ui.badge(c,Ui.SCRIM,Ui.TEXT);FrameLayout.LayoutParams rating=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);rating.setMargins(Ui.dp(c,8),Ui.dp(c,8),Ui.dp(c,8),0);art.addView(holder.score,rating);
    holder.badge=Ui.badge(c,Ui.SCRIM,Ui.LIVE);FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);corner.setMargins(Ui.dp(c,8),0,Ui.dp(c,8),Ui.dp(c,8));art.addView(holder.badge,corner);
    holder.name=Ui.title(c,"",14);holder.name.setMaxLines(2);holder.name.setMinLines(2);holder.name.setEllipsize(TextUtils.TruncateAt.END);holder.name.setLineSpacing(Ui.dp(c,2),1);Ui.add(holder.card,holder.name,10);
    holder.meta=Ui.text(c,"",12,Ui.MUTED);holder.meta.setMaxLines(1);holder.meta.setEllipsize(TextUtils.TruncateAt.END);Ui.add(holder.card,holder.meta,6);
    holder.note=Ui.text(c,"",12,Ui.ACCENT);holder.note.setTypeface(Ui.MEDIUM);holder.note.setMaxLines(2);holder.note.setCompoundDrawablePadding(Ui.dp(c,6));Ui.add(holder.card,holder.note,8);holder.card.setTag(holder);recycled=holder.card;
   }else holder=(Holder)recycled.getTag();
   Anime anime=shown.get(position);holder.name.setText(anime.title);holder.score.setText(Ui.score(anime));holder.score.setVisibility(anime.rating>0?View.VISIBLE:View.GONE);holder.meta.setText((anime.year>0?anime.year+" · ":"")+anime.type);holder.poster.setContentDescription(anime.title);Ui.poster(c,holder.poster,anime.poster);
   String key=anime.statusKey();String badge=key.equals("ongoing")?"● Онгоинг":key.equals("announcement")?"Анонс":"";holder.badge.setText(badge);holder.badge.setTextColor(key.equals("ongoing")?Ui.LIVE:Ui.ACCENT);holder.badge.setVisibility(badge.isEmpty()||page.equals("schedule")?View.GONE:View.VISIBLE);
   String note="",icon="";
   if(page.equals("schedule")){icon="calendar";note=anime.nextDate>0?java.time.Instant.ofEpochSecond(anime.nextDate).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.forLanguageTag("ru"))):"Дата пока неизвестна";}
   else if(page.equals("history")){JSONObject last=library.last(anime.id);if(last!=null){icon="play";note="Продолжить · серия "+last.optString("number");}}
   holder.note.setText(note);holder.note.setCompoundDrawablesRelativeWithIntrinsicBounds(icon.isEmpty()?null:new Ui.AppIcon(c,icon,Ui.ACCENT,14),null,null,null);holder.note.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);
   holder.card.setClickable(true);holder.card.setFocusable(true);holder.card.setContentDescription(anime.title+". "+anime.score()+(badge.isEmpty()?"":". "+anime.statusLabel())+(note.isEmpty()?"":". "+note));holder.card.setOnClickListener(v->startActivity(new Intent(c,DetailActivity.class).putExtra("anime",anime.raw.toString())));return recycled;
  }
 }
}
