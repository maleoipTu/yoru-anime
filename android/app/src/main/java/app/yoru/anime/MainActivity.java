package app.yoru.anime;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.text.TextUtils;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
 LinearLayout root,body,nav,filters;TextView status;EditText search;GridView grid;Button more;
 Library library;List<Anime>shown=new ArrayList<>();String page="catalog",query="",sort="rating",state="";int offset,generation;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);library=new Library(this);root=Ui.root(this);
  LinearLayout header=Ui.padded(this,18);TextView brand=Ui.title(this,"☾ yoru",29);brand.setTextColor(Ui.ACCENT);header.addView(brand);Ui.add(header,Ui.text(this,"Твой вечер. Твоё аниме.",12,Ui.MUTED),3);
  LinearLayout searchRow=Ui.row(this);search=new EditText(this);search.setTextColor(Ui.TEXT);search.setTextSize(15);search.setHint("Название аниме…");search.setHintTextColor(Ui.MUTED);search.setSingleLine(true);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchRow.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Button find=Ui.button(this,"Найти",true);searchRow.addView(find);find.setOnClickListener(v->performSearch());search.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){performSearch();return true;}return false;});Ui.add(header,searchRow,14);root.addView(header);
  filters=Ui.row(this);filters.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,10));Button order=Ui.button(this,"По рейтингу ⌄",false),filter=Ui.button(this,"Все статусы ⌄",false);filters.addView(order,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=Ui.dp(this,10);filters.addView(filter,fp);
  order.setOnClickListener(v->Ui.choose(this,"Сортировка",Arrays.asList("По рейтингу","По популярности","Сначала новые"),order.getText().toString().replace(" ⌄",""),s->{sort=s.equals("По рейтингу")?"rating":s.equals("По популярности")?"views":"year";order.setText(s+" ⌄");load(false);}));
  filter.setOnClickListener(v->Ui.choose(this,"Статус",Arrays.asList("Все статусы","Вышедшие","Онгоинги","Анонсы"),filter.getText().toString().replace(" ⌄",""),s->{state=s.equals("Вышедшие")?"released":s.equals("Онгоинги")?"ongoing":s.equals("Анонсы")?"announcement":"";filter.setText(s+" ⌄");load(false);}));root.addView(filters);
  status=Ui.text(this,"",12,Ui.MUTED);status.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,8));root.addView(status);body=Ui.padded(this,12);root.addView(body);
  grid=new GridView(this);grid.setNumColumns(columns());grid.setHorizontalSpacing(Ui.dp(this,12));grid.setVerticalSpacing(Ui.dp(this,20));grid.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,18));grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setAdapter(new CatalogAdapter());root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
  more=Ui.button(this,"Показать ещё",false);more.setVisibility(View.GONE);more.setOnClickListener(v->{more.setEnabled(false);load(true);});root.addView(more);
  nav=Ui.row(this);nav.setBackgroundColor(Ui.PANEL);String[]titles={"▦\nКаталог","◷\nСерии","♡\nИзбранное","↺\nИстория"},keys={"catalog","schedule","favorites","history"};for(int i=0;i<keys.length;i++){final String target=keys[i];Button b=Ui.button(this,titles[i],false);b.setTextSize(11);nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1));b.setOnClickListener(v->{page=target;query="";search.setText("");load(false);});}root.addView(nav);
  if(saved!=null){page=saved.getString("page","catalog");query=saved.getString("query","");search.setText(query);}load(false);
 }
 int columns(){int width=getResources().getConfiguration().screenWidthDp;return width>=700?4:width>=520?3:2;}
 @Override protected void onSaveInstanceState(Bundle b){b.putString("page",page);b.putString("query",query);super.onSaveInstanceState(b);}
 @Override protected void onResume(){super.onResume();if(library!=null&&(page.equals("favorites")||page.equals("history")))load(false);}
 void performSearch(){query=search.getText().toString().trim();if(page.equals("schedule"))page="catalog";load(false);android.view.inputmethod.InputMethodManager im=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(search.getWindowToken(),0);}
 void load(boolean append){int request=++generation;filters.setVisibility(page.equals("catalog")?View.VISIBLE:View.GONE);for(int i=0;i<nav.getChildCount();i++)nav.getChildAt(i).setAlpha(Arrays.asList("catalog","schedule","favorites","history").indexOf(page)==i?1f:.55f);more.setVisibility(View.GONE);
  if(!append){shown.clear();offset=0;body.removeAllViews();((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();grid.setSelection(0);}status.setText("Загружаем…");String requestedPage=page,requestedQuery=query;int start=offset;String path=page.equals("schedule")?"anime/schedule":"anime?limit=24&sort="+sort+"&sort_forward=false&offset="+start+"&q="+Api.encode(query)+(state.isEmpty()?"":"&status="+state);
  Api.IO.execute(()->{try{
   List<Anime>data=requestedPage.equals("favorites")?library.favorites():requestedPage.equals("history")?library.history():Api.catalog(Api.get(path));
   if(requestedPage.equals("favorites")||requestedPage.equals("history"))data.removeIf(a->!a.title.toLowerCase(Locale.ROOT).contains(requestedQuery.toLowerCase(Locale.ROOT)));
   if(requestedPage.equals("schedule"))data.sort(Comparator.comparingLong(a->a.nextDate>0?a.nextDate:Long.MAX_VALUE));
   List<Anime>result=requestedPage.equals("schedule")?new ArrayList<>(data.subList(Math.min(start,data.size()),Math.min(start+24,data.size()))):data;
   runOnUiThread(()->{if(isDestroyed()||request!=generation)return;offset=start+result.size();Set<Long>ids=new HashSet<>();for(Anime a:shown)ids.add(a.id);for(Anime a:result)if(ids.add(a.id))shown.add(a);render();status.setText("Показано: "+shown.size());more.setEnabled(true);more.setVisibility((requestedPage.equals("catalog")||requestedPage.equals("schedule"))&&result.size()==24?View.VISIBLE:View.GONE);});
  }catch(Exception e){runOnUiThread(()->{if(isDestroyed()||request!=generation)return;status.setText(e instanceof java.io.IOException?e.getMessage():"Не удалось загрузить данные. Проверь подключение.");Button retry=Ui.button(this,"Повторить",true);Ui.add(body,retry,20);retry.setOnClickListener(v->load(false));});}});
 }
 void render(){body.removeAllViews();String title=page.equals("favorites")?"Твои любимые истории":page.equals("history")?"Последние открытые серии":page.equals("schedule")?"Когда следующая серия?":query.isEmpty()?"Что посмотрим сегодня?":"Результаты поиска";Ui.add(body,Ui.title(this,title,22),0);if(shown.isEmpty())Ui.add(body,Ui.text(this,page.equals("favorites")?"Добавляй аниме в избранное из карточки.":page.equals("history")?"Здесь появятся серии, которые ты откроешь.":"Ничего не найдено. Попробуй другое название.",15,Ui.MUTED),20);((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();}
 final class Holder{LinearLayout card;ImageView poster;TextView score,name,meta,note;}
 final class CatalogAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int position){return shown.get(position);}public long getItemId(int position){return shown.get(position).id;}public boolean hasStableIds(){return true;}
  public View getView(int position,View recycled,ViewGroup parent){Holder h;if(recycled==null){h=new Holder();h.card=Ui.column(MainActivity.this);h.poster=new ImageView(MainActivity.this);int height=Math.round((getResources().getConfiguration().screenWidthDp-36-(columns()-1)*12f)/columns()*1.4f);h.card.addView(h.poster,new LinearLayout.LayoutParams(-1,Ui.dp(MainActivity.this,height)));h.score=Ui.text(MainActivity.this,"",12,Ui.ACCENT);Ui.add(h.card,h.score,9);h.name=Ui.title(MainActivity.this,"",14);h.name.setMaxLines(2);h.name.setMinLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);Ui.add(h.card,h.name,4);h.meta=Ui.text(MainActivity.this,"",12,Ui.MUTED);h.meta.setMaxLines(1);h.meta.setEllipsize(TextUtils.TruncateAt.END);Ui.add(h.card,h.meta,5);h.note=Ui.text(MainActivity.this,"",12,Ui.ACCENT);Ui.add(h.card,h.note,5);h.card.setTag(h);recycled=h.card;}else h=(Holder)recycled.getTag();
   Anime a=shown.get(position);h.name.setText(a.title);h.score.setText(a.score());h.meta.setText(a.meta());h.poster.setImageDrawable(null);h.poster.setContentDescription(a.title);Ui.poster(MainActivity.this,h.poster,a.poster);String note="";
   if(page.equals("schedule"))note=a.nextDate>0?java.time.Instant.ofEpochSecond(a.nextDate).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm",new Locale("ru"))):"Дата пока неизвестна";
   else if(page.equals("history")){JSONObject last=library.last(a.id);if(last!=null)note="Серия "+last.optString("number");}
   h.note.setText(note);h.note.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);h.card.setClickable(true);h.card.setFocusable(true);h.card.setContentDescription(a.title+". "+a.score());h.card.setOnClickListener(v->startActivity(new Intent(MainActivity.this,DetailActivity.class).putExtra("anime",a.raw.toString())));return recycled;
  }
 }
}
