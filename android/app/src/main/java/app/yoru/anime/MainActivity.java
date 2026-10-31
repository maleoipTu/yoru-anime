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
 EditText search; GridView grid; Button more,order,filter,listChip,retry,clear;
 static final int GAP=16;
 ProgressBar spinner;FrameLayout profileButton;Decor.AvatarView avatar;ImageView guestIcon;
 Library library; List<Anime> shown=new ArrayList<>();
 String page="catalog",query="",sort="random",state="",list="";
 // Фильтры каталога: жанры (id), годы и возрастной рейтинг (0 — любой). Рейтинг фильтруется на стороне приложения.
 final Set<Integer> genres=new LinkedHashSet<>();int fromYear,toYear,age=-1;Button filterChip;
 // Расширенные фильтры: длительность, студия, только завершённые, тип озвучки.
 int length;long studioId;String studioTitle="",translate="";boolean finished;
 static final String[] LENGTHS={"Любая","До 12 серий","13–26 серий","Длинные (27+)"};static final int[][] LENGTH_RANGE={{0,0},{1,12},{13,26},{27,0}};
 static final String[][] TRANSLATES={{"Любая",""},{"Полный дубляж","dubbing"},{"Многоголосая","multivoice"},{"Двухголосая","duet"},{"Одноголосая","single"},{"Субтитры","subtitles"}};
 /** Популярные студии YummyAnime (ID из API); любую другую можно выбрать кнопкой студии в карточке аниме. */
 static final Object[][] STUDIOS={{31,"J.C.Staff"},{1,"A-1 Pictures"},{19,"Madhouse"},{58,"MAPPA"},{81,"Bones"},{177,"TMS Entertainment"},{447,"David Production"},{96,"CloverWorks"},{36,"White Fox"},{72,"OLM"},{9,"8bit"},{52,"Production I.G"},{421,"Zero-G"},{66688,"Studio Pierrot"},{171,"Passione"},{23,"P.A. Works"},{50,"Wit Studio"},{73,"Lidenfilms"},{278,"Kyoto Animation"},{30,"Sunrise"},{97,"Studio Deen"},{224,"ufotable"},{414,"Brain's Base"},{623,"Doga Kobo"},{838,"Bibury Animation Studios"},{872,"Studio Bind"},{13,"Trigger"},{49,"Lerche"},{143,"Bandai Namco Pictures"},{146,"Science SARU"},{341,"C2C"},{459,"Kinema Citrus"},{876,"Studio Comet"},{1409,"Felix Film"},{1939,"Studio Kai"},{7462,"Gekkou"},{7,"Project No.9"},{33,"SILVER LINK."},{39,"asread."},{160,"Asahi Production"},{174,"Lay-duce"},{710,"Pierrot"},{145,"Toei Animation"},{94,"Satelight"},{161,"Gainax"},{168,"TNK"},{178,"Connect"},{1458,"Studio Mir"},{1581,"Studio Gokumi"}};
 static String advancedQuery(int length,long studioId,String translate,boolean finished,String state){StringBuilder q=new StringBuilder();int[] r=LENGTH_RANGE[Math.max(0,Math.min(length,LENGTH_RANGE.length-1))];if(r[0]>0)q.append("&ep_from=").append(r[0]);if(r[1]>0)q.append("&ep_to=").append(r[1]);if(studioId>0)q.append("&studio_ids=").append(studioId);if(!translate.isEmpty())q.append("&translates=").append(translate);if(finished&&state.isEmpty())q.append("&status=released");return q.toString();}
 static JSONObject genreCache;android.widget.ListPopupWindow suggestions;final List<Anime> suggested=new ArrayList<>();int suggestGeneration;
 final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());Runnable pendingSuggest;int autoLoads;BaseAdapter suggestAdapter;
 List<Integer> pages=new ArrayList<>(); static final int RANDOM_PAGES=400;
 int offset,generation,restoreScroll; boolean firstResume=true,loading,hasMore,failedAppend;
 final String[] keys={"catalog","schedule","favorites","history"};

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  // Без входа в аккаунт приложение не пускает дальше экрана входа (в отладочной сборке тесты могут его пропустить).
  if(Account.configured()&&!new Account(this).signedIn()&&!(BuildConfig.DEBUG&&getIntent().getBooleanExtra("qa_skip_auth",false))){startActivity(new Intent(this,AuthActivity.class));finish();return;}
  library=new Library(this);
  if(saved!=null){page=saved.getString("page","catalog");query=saved.getString("query","");sort=saved.getString("sort","random");state=saved.getString("state","");restoreScroll=saved.getInt("scroll");list=saved.getString("list","");fromYear=saved.getInt("from_year");toYear=saved.getInt("to_year");age=saved.getInt("age",-1);int[] g=saved.getIntArray("genres");if(g!=null)for(int id:g)genres.add(id);}
  root=Ui.root(this);
  // Шапка: раздел и подзаголовок рядом с «луной», счётчик справа, поиск-пилюля и фильтры-чипы.
  LinearLayout header=Ui.column(this);header.setPadding(Ui.dp(this,Ui.GUTTER),Ui.dp(this,16),Ui.dp(this,Ui.GUTTER),Ui.dp(this,12));header.setBackground(new Ui.Sky(this));
  LinearLayout brand=Ui.row(this);ImageView moon=new ImageView(this);moon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.ACCENT,22));moon.setScaleType(ImageView.ScaleType.CENTER);moon.setBackground(Ui.shape(this,Ui.SOFT,22));moon.setContentDescription("yoru");brand.addView(moon,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
  LinearLayout heading=Ui.column(this);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,14);brand.addView(heading,hp);
  sectionTitle=Ui.title(this,"Каталог",26);sectionTitle.setMaxLines(1);sectionTitle.setEllipsize(TextUtils.TruncateAt.END);if(android.os.Build.VERSION.SDK_INT>=28)sectionTitle.setAccessibilityHeading(true);heading.addView(sectionTitle);
  Ui.dawn(heading,8);subtitle=Ui.text(this,"Найди свою следующую историю",14,Ui.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);Ui.add(heading,subtitle,8);
  status=Ui.text(this,"",12,Ui.MUTED);status.setTypeface(Ui.MEDIUM);status.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));status.setBackground(Ui.shape(this,Ui.RAISED,12));status.setVisibility(View.GONE);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-2,-2);stp.leftMargin=Ui.dp(this,8);brand.addView(status,stp);
  // Профиль: аватар с надетой рамкой, если вход выполнен, иначе значок человека.
  profileButton=new FrameLayout(this);profileButton.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,24));profileButton.setClickable(true);profileButton.setFocusable(true);profileButton.setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
  avatar=new Decor.AvatarView(this);profileButton.addView(avatar,new FrameLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40),Gravity.CENTER));guestIcon=new ImageView(this);guestIcon.setImageDrawable(new Ui.AppIcon(this,"user",Ui.TEXT,22));guestIcon.setScaleType(ImageView.ScaleType.CENTER);guestIcon.setBackground(Ui.shape(this,Ui.RAISED,20));guestIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);profileButton.addView(guestIcon,new FrameLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40),Gravity.CENTER));
  LinearLayout.LayoutParams pbp=new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48));pbp.leftMargin=Ui.dp(this,6);brand.addView(profileButton,pbp);updateProfileButton();header.addView(brand);
  Updater.check(this,false);EpisodeWatcher.schedule(this);EpisodeWatcher.ask(this);
  LinearLayout searchRow=Ui.row(this);searchRow.setPadding(Ui.dp(this,16),0,Ui.dp(this,2),0);searchRow.setBackground(Ui.outlined(this,Ui.PANEL,24));
  ImageView magnifier=new ImageView(this);magnifier.setImageDrawable(new Ui.AppIcon(this,"search",Ui.MUTED,20));magnifier.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchRow.addView(magnifier,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));
  search=new EditText(this);search.setTextColor(Ui.TEXT);search.setTextSize(15);search.setHint("Поиск аниме");search.setContentDescription("Поиск аниме по названию");search.setHintTextColor(Ui.MUTED);search.setSingleLine(true);search.setBackgroundColor(android.graphics.Color.TRANSPARENT);search.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(query);search.setMinHeight(Ui.dp(this,52));search.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8));searchRow.addView(search,new LinearLayout.LayoutParams(0,-2,1));
  clear=Ui.iconButton(this,"close","Очистить поиск");Ui.icon(clear,"close",Ui.MUTED);clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);searchRow.addView(clear);clear.setOnClickListener(v->{search.setText("");if(!query.isEmpty())performSearch();});
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int b,int c){}public void onTextChanged(CharSequence t,int a,int b,int c){}public void afterTextChanged(android.text.Editable t){clear.setVisibility(t.length()==0?View.GONE:View.VISIBLE);scheduleSuggestions(t.toString().trim());}});
  search.setOnFocusChangeListener((v,focus)->{if(!focus)hideSuggestions();});search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){performSearch();return true;}return false;});Ui.add(header,searchRow,20);root.addView(header);
  filters=Ui.row(this);filters.setPadding(Ui.dp(this,Ui.GUTTER),Ui.dp(this,4),Ui.dp(this,Ui.GUTTER),Ui.dp(this,8));order=Ui.chip(this,sortLabel());filter=Ui.chip(this,stateLabel());
  filters.addView(order,new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2);fp.leftMargin=Ui.dp(this,10);filters.addView(filter,fp);
  filterChip=Ui.chip(this,filterLabel());filterChip.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"filter",Ui.TEXT,16),null,null,null);filterChip.setCompoundDrawablePadding(Ui.dp(this,6));LinearLayout.LayoutParams fcp=new LinearLayout.LayoutParams(-2,-2);fcp.leftMargin=Ui.dp(this,10);filters.addView(filterChip,fcp);filterChip.setOnClickListener(v->openFilters());
  Button luck=Ui.chip(this,"Рулетка");luck.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"dice",Ui.TEXT,16),null,null,null);luck.setCompoundDrawablePadding(Ui.dp(this,6));LinearLayout.LayoutParams luckParams=new LinearLayout.LayoutParams(-2,-2);luckParams.leftMargin=Ui.dp(this,10);filters.addView(luck,luckParams);luck.setOnClickListener(v->Extras.roulette(this,new Account(this),library,filterQuery(),state,age));
  Button forYou=Ui.chip(this,"Для тебя");forYou.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"spark",Ui.TEXT,16),null,null,null);forYou.setCompoundDrawablePadding(Ui.dp(this,6));LinearLayout.LayoutParams forYouParams=new LinearLayout.LayoutParams(-2,-2);forYouParams.leftMargin=Ui.dp(this,10);filters.addView(forYou,forYouParams);forYou.setOnClickListener(v->Extras.forYou(this,new Account(this)));
  order.setOnClickListener(v->Ui.choose(this,"Сортировка",Arrays.asList("Случайные","По рейтингу","По популярности","Сначала новые"),sortLabel(),choice->{sort=choice.equals("Случайные")?"random":choice.equals("По рейтингу")?"rating":choice.equals("По популярности")?"views":"year";order.setText(sortLabel());updateChips();load(false);}));
  filter.setOnClickListener(v->Ui.choose(this,"Статус",Arrays.asList("Все статусы","Вышедшие","Онгоинги","Анонсы"),stateLabel(),choice->{state=choice.equals("Вышедшие")?"released":choice.equals("Онгоинги")?"ongoing":choice.equals("Анонсы")?"announcement":"";filter.setText(stateLabel());updateChips();load(false);}));
  listChip=Ui.chip(this,Account.listLabel(list));listChip.setOnClickListener(v->{List<String> options=new ArrayList<>();options.add("Избранное");for(String key:Account.LISTS)options.add(Account.listLabel(key));Ui.choose(this,"Что показать",options,Account.listLabel(list),choice->{String chosen="";for(String key:Account.LISTS)if(Account.listLabel(key).equals(choice))chosen=key;if(!chosen.isEmpty()&&!new Account(this).signedIn()){Toast.makeText(this,Account.configured()?"Войди в профиль, чтобы видеть свои списки":"Аккаунты в этой сборке не подключены",Toast.LENGTH_LONG).show();if(Account.configured())startActivity(new Intent(this,ProfileActivity.class));return;}list=chosen;listChip.setText(Account.listLabel(list));Ui.chipState(listChip,!list.isEmpty());load(false);});});LinearLayout.LayoutParams lcp=new LinearLayout.LayoutParams(-2,-2);filters.addView(listChip,0,lcp);Ui.chipState(listChip,!list.isEmpty());
  HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);filterScroll.addView(filters);root.addView(filterScroll,new LinearLayout.LayoutParams(-1,-2));updateChips();
  section=Ui.row(this);section.setVisibility(View.GONE);
  errorPanel=Ui.padded(this,Ui.PANEL_PAD);errorPanel.setBackground(Ui.outlined(this,Ui.PANEL,22));errorPanel.addView(Ui.title(this,"Не получилось загрузить",16));errorText=Ui.text(this,"",14,Ui.MUTED);Ui.add(errorPanel,errorText,8);retry=Ui.tonal(this,"Повторить загрузку");Ui.icon(retry,"retry",Ui.ACCENT);retry.setCompoundDrawablePadding(Ui.dp(this,8));Ui.add(errorPanel,retry,14);retry.setOnClickListener(v->load(failedAppend));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,Ui.GUTTER),Ui.dp(this,8),Ui.dp(this,Ui.GUTTER),Ui.dp(this,16));root.addView(errorPanel,ep);errorPanel.setVisibility(View.GONE);
  content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  grid=new GridView(this);grid.setNumColumns(columns());grid.setHorizontalSpacing(Ui.dp(this,GAP));grid.setVerticalSpacing(Ui.dp(this,28));grid.setPadding(Ui.dp(this,Ui.GUTTER),Ui.dp(this,12),Ui.dp(this,Ui.GUTTER),Ui.dp(this,32));grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setVerticalScrollBarEnabled(false);grid.setAdapter(new CatalogAdapter());content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
  // Следующая страница подгружается сама, когда до конца списка остаётся меньше двух рядов.
  grid.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int scrollState){}public void onScroll(AbsListView view,int first,int visible,int total){if(total>0&&first+visible>=total-columns()*2&&hasMore&&!loading&&errorPanel.getVisibility()!=View.VISIBLE)load(true);}});
  emptyPanel=Ui.padded(this,40);emptyPanel.setGravity(Gravity.CENTER);ImageView emptyIcon=new ImageView(this);emptyIcon.setImageDrawable(new Ui.AppIcon(this,"moon",Ui.ACCENT,36));emptyIcon.setScaleType(ImageView.ScaleType.CENTER);emptyIcon.setBackground(Ui.shape(this,Ui.SOFT,40));emptyIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);emptyPanel.addView(emptyIcon,new LinearLayout.LayoutParams(Ui.dp(this,80),Ui.dp(this,80)));emptyTitle=Ui.title(this,"Здесь пока тихо",22);emptyTitle.setGravity(Gravity.CENTER);Ui.add(emptyPanel,emptyTitle,24);emptyHint=Ui.text(this,"",14,Ui.MUTED);emptyHint.setGravity(Gravity.CENTER);emptyHint.setLineSpacing(Ui.dp(this,4),1);Ui.add(emptyPanel,emptyHint,10);content.addView(emptyPanel,new FrameLayout.LayoutParams(-1,-1));emptyPanel.setVisibility(View.GONE);
  spinner=new ProgressBar(this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));content.addView(spinner,new FrameLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36),Gravity.CENTER));
  more=Ui.textButton(this,"Показать ещё");more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));root.addView(more,new LinearLayout.LayoutParams(-1,-2));
  nav=Ui.row(this);nav.setBackground(Ui.gradient(this,0xFF18181E,0xFF121216,0,android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM));Ui.lift(nav,12);nav.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,6));String[] labels={"Каталог","Расписание","Избранное","История"},icons={"catalog","calendar","heart","history"};
  for(int i=0;i<keys.length;i++){String target=keys[i];Button button=Ui.button(this,labels[i],false);button.setTextSize(11);button.setMaxLines(1);button.setPadding(Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2),Ui.dp(this,4));button.setCompoundDrawablePadding(Ui.dp(this,4));button.setTag(icons[i]);button.setMinHeight(Ui.dp(this,64));button.setMinimumHeight(Ui.dp(this,64));button.setEllipsize(TextUtils.TruncateAt.END);nav.addView(button,new LinearLayout.LayoutParams(0,-1,1));button.setOnClickListener(v->{if(page.equals(target))return;page=target;query="";search.setText("");load(false);});}root.addView(nav);Object retained=getLastNonConfigurationInstance();if(retained instanceof CatalogState){CatalogState snapshot=(CatalogState)retained;if(snapshot.page.equals(page)&&snapshot.query.equals(query)&&snapshot.sort.equals(sort)&&snapshot.state.equals(state)){shown.addAll(snapshot.items);pages=new ArrayList<>(snapshot.pages);offset=snapshot.offset;hasMore=snapshot.hasMore;updateChrome();render();grid.setSelection(snapshot.first);restoreScroll=0;}else load(false);}else load(false);
 }
 static final class CatalogState {
  final List<Anime>items;final List<Integer>pages;final String page,query,sort,state;final int offset,first;final boolean hasMore;
  CatalogState(MainActivity activity){items=new ArrayList<>(activity.shown);pages=new ArrayList<>(activity.pages);page=activity.page;query=activity.query;sort=activity.sort;state=activity.state;offset=activity.offset;first=activity.grid.getFirstVisiblePosition();hasMore=activity.hasMore;}
 }
 @Override public Object onRetainNonConfigurationInstance(){return loading&&shown.isEmpty()?null:new CatalogState(this);}
 String sortLabel(){return sort.equals("views")?"По популярности":sort.equals("year")?"Сначала новые":sort.equals("rating")?"По рейтингу":"Случайные";}
 String stateLabel(){return state.equals("released")?"Вышедшие":state.equals("ongoing")?"Онгоинги":state.equals("announcement")?"Анонсы":"Все статусы";}
 int columns(){int width=getResources().getConfiguration().screenWidthDp;return width>=700?4:width>=520?3:2;}
 @Override protected void onSaveInstanceState(Bundle stateBundle){stateBundle.putString("page",page);stateBundle.putString("query",query);stateBundle.putString("sort",sort);stateBundle.putString("state",state);stateBundle.putString("list",list);stateBundle.putInt("from_year",fromYear);stateBundle.putInt("to_year",toYear);stateBundle.putInt("age",age);int[] g=new int[genres.size()];int gi=0;for(int id:genres)g[gi++]=id;stateBundle.putIntArray("genres",g);stateBundle.putInt("scroll",grid.getFirstVisiblePosition());super.onSaveInstanceState(stateBundle);}
 /** Карточка аниме → «Студия»: каталог со всеми аниме этой студии. */
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);long id=intent.getLongExtra("studio_id",0);if(id<=0)return;studioId=id;studioTitle=String.valueOf(intent.getStringExtra("studio_title"));page="catalog";query="";search.setText("");updateChips();load(false);}
 @Override protected void onResume(){super.onResume();updateProfileButton();
  // Синхронизация прогресса просмотра с аккаунтом (серии, начатые на другом устройстве, появятся в истории).
  Api.IO.execute(()->{if(Progress.pull(this))runOnUiThread(()->{if(!isDestroyed()&&page.equals("history"))load(false);});});
  if(firstResume){firstResume=false;return;}if(library!=null&&(page.equals("favorites")||page.equals("history")))load(false);}
 void updateProfileButton(){
  Account account=new Account(this);boolean signed=account.signedIn();JSONObject profile=account.cachedProfile();
  avatar.setVisibility(signed?View.VISIBLE:View.GONE);guestIcon.setVisibility(signed?View.GONE:View.VISIBLE);
  if(signed){java.util.Map<String,JSONObject> decor=Decor.index(account.cachedDecorations());avatar.frame(Decor.style(decor,profile.optString("equipped_frame","frame_moon")));avatar.person(Decor.name(profile),profile.optString("avatar_url"));}
  profileButton.setContentDescription(signed?"Профиль "+Decor.name(profile)+", очков: "+profile.optInt("points"):"Войти в профиль");
 }
 // ───── Подсказки при вводе ─────
 /** Через 300 мс после последнего символа показывает до 6 похожих тайтлов под строкой поиска. */
 void scheduleSuggestions(String text){
  if(pendingSuggest!=null)ui.removeCallbacks(pendingSuggest);int session=++suggestGeneration;
  if(text.length()<2||!search.hasFocus()||text.equals(query)){hideSuggestions();return;}
  pendingSuggest=()->Api.IO.execute(()->{try{
    List<Anime> found=Api.catalog(Api.get("search?q="+Api.encode(text)+"&limit=6"));
    if(found.isEmpty())found=Api.catalog(Api.get("anime?limit=6&q="+Api.encode(text)));
    List<Anime> top=found.size()>6?new ArrayList<>(found.subList(0,6)):found;
    runOnUiThread(()->{if(isDestroyed()||session!=suggestGeneration||!search.hasFocus())return;showSuggestions(top);});
   }catch(Exception ignored){}});
  ui.postDelayed(pendingSuggest,300);
 }
 void hideSuggestions(){if(suggestions!=null&&suggestions.isShowing())suggestions.dismiss();}
 void showSuggestions(List<Anime> items){
  suggested.clear();suggested.addAll(items);if(items.isEmpty()){hideSuggestions();return;}
  if(suggestions==null){
   suggestions=new android.widget.ListPopupWindow(this);suggestions.setAnchorView((View)search.getParent());suggestions.setModal(false);suggestions.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
   android.graphics.drawable.GradientDrawable bg=Ui.outlined(this,Ui.PANEL,18);suggestions.setBackgroundDrawable(bg);suggestions.setVerticalOffset(Ui.dp(this,6));
   suggestAdapter=new BaseAdapter(){public int getCount(){return suggested.size();}public Object getItem(int i){return suggested.get(i);}public long getItemId(int i){return suggested.get(i).id;}
    public View getView(int i,View v,ViewGroup parent){LinearLayout row;ImageView poster;TextView name,meta;if(v==null){row=Ui.row(MainActivity.this);row.setPadding(Ui.dp(MainActivity.this,10),Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,12),Ui.dp(MainActivity.this,8));poster=new ImageView(MainActivity.this);Ui.round(poster,8);row.addView(poster,new LinearLayout.LayoutParams(Ui.dp(MainActivity.this,40),Ui.dp(MainActivity.this,56)));LinearLayout text=Ui.column(MainActivity.this);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=Ui.dp(MainActivity.this,12);row.addView(text,tp);name=Ui.title(MainActivity.this,"",14);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);text.addView(name);meta=Ui.text(MainActivity.this,"",12,Ui.MUTED);meta.setMaxLines(1);Ui.add(text,meta,4);row.setTag(new Object[]{poster,name,meta});v=row;}
     Object[] h=(Object[])v.getTag();Anime a=suggested.get(i);((TextView)h[1]).setText(a.title);((TextView)h[2]).setText((a.rating>0?Ui.score(a)+"  ·  ":"")+a.meta());Ui.poster(MainActivity.this,(ImageView)h[0],a.poster);v.setContentDescription(a.title);return v;}};suggestions.setAdapter(suggestAdapter);
   suggestions.setOnItemClickListener((parent,view,position,id)->{Anime a=suggested.get(position);hideSuggestions();startActivity(new Intent(this,DetailActivity.class).putExtra("anime",a.raw.toString()));});
  }
  suggestAdapter.notifyDataSetChanged();suggestions.setWidth(((View)search.getParent()).getWidth());suggestions.show();
 }
 void performSearch(){hideSuggestions();if(pendingSuggest!=null)ui.removeCallbacks(pendingSuggest);suggestGeneration++;query=search.getText().toString().trim();if(page.equals("schedule"))page="catalog";load(false);InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
 void updateChrome(){boolean catalog=page.equals("catalog"),favorites=page.equals("favorites");filters.setVisibility(catalog||favorites?View.VISIBLE:View.GONE);((View)filters.getParent()).setVisibility(catalog||favorites?View.VISIBLE:View.GONE);order.setVisibility(catalog?View.VISIBLE:View.GONE);filter.setVisibility(catalog?View.VISIBLE:View.GONE);listChip.setVisibility(favorites?View.VISIBLE:View.GONE);sectionTitle.setText(page.equals("favorites")?Account.listLabel(list):page.equals("history")?"История":page.equals("schedule")?"Расписание":query.isEmpty()?"Каталог":"Результаты поиска");subtitle.setText(!query.isEmpty()&&!page.equals("schedule")?"По запросу «"+query+"»":page.equals("schedule")?"Новые серии и ближайшие премьеры":page.equals("favorites")?"Истории, к которым хочется вернуться":page.equals("history")?"Продолжай с того места, где остановился":"Найди свою следующую историю");for(int i=0;i<nav.getChildCount();i++){Button b=(Button)nav.getChildAt(i);boolean active=keys[i].equals(page);b.setSelected(active);b.setTextColor(active?Ui.TEXT:Ui.MUTED);b.setTypeface(active?android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD):Ui.MEDIUM);b.setCompoundDrawablesWithIntrinsicBounds(null,new Ui.NavIcon(this,(String)b.getTag(),active),null,null);b.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,16));b.setContentDescription(b.getText()+(active?", выбранный раздел":""));}}
 void updateChips(){Ui.chipState(order,!sort.equals("random"));Ui.chipState(filter,!state.isEmpty());if(filterChip!=null){filterChip.setText(filterLabel());Ui.chipState(filterChip,filtered());filterChip.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.AppIcon(this,"filter",filtered()?Ui.ACCENT:Ui.TEXT,16),null,null,null);}}
 boolean filtered(){return !genres.isEmpty()||fromYear>0||toYear>0||age>=0||length>0||studioId>0||finished||!translate.isEmpty();}
 String filterLabel(){int n=(genres.isEmpty()?0:1)+(fromYear>0||toYear>0?1:0)+(age>=0?1:0)+(length>0?1:0)+(studioId>0?1:0)+(finished?1:0)+(translate.isEmpty()?0:1);return n==0?"Фильтры":"Фильтры · "+n;}
 /** Параметры фильтров для запроса каталога. */
 String filterQuery(){StringBuilder q=new StringBuilder();if(!genres.isEmpty()){q.append("&genres=");boolean first=true;for(int id:genres){if(!first)q.append(',');q.append(id);first=false;}}if(fromYear>0)q.append("&from_year=").append(fromYear);if(toYear>0)q.append("&to_year=").append(toYear);q.append(advancedQuery(length,studioId,translate,finished,state));return q.toString();}
 static final int[] AGES={0,6,12,16,18};
 void openFilters(){
  LinearLayout panel=Ui.column(this);Set<Integer> chosen=new LinkedHashSet<>(genres);int[] years={fromYear,toYear};int[] rating={age};
  TextView genreTitle=Ui.title(this,"Жанры",15);Ui.add(panel,genreTitle,4);Ui.Flow flow=new Ui.Flow(this,8);Ui.add(panel,flow,10);
  TextView loadingGenres=Ui.text(this,"Загружаем жанры…",13,Ui.MUTED);flow.addView(loadingGenres);
  Ui.add(panel,Ui.title(this,"Годы выхода",15),20);LinearLayout yearsRow=Ui.row(this);Button from=Ui.field(this,"С года"),to=Ui.field(this,"По год");yearsRow.addView(from,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=Ui.dp(this,8);yearsRow.addView(to,tp);Ui.add(panel,yearsRow,10);
  List<String> yearOptions=new ArrayList<>();yearOptions.add("Любой");int now=java.time.Year.now().getValue()+1;for(int y=now;y>=1960;y--)yearOptions.add(String.valueOf(y));
  Runnable paintYears=()->{Ui.fieldValue(from,"С года",years[0]>0?String.valueOf(years[0]):"Любой");Ui.fieldValue(to,"По год",years[1]>0?String.valueOf(years[1]):"Любой");};paintYears.run();
  from.setOnClickListener(v->Ui.choose(this,"С какого года",yearOptions,years[0]>0?String.valueOf(years[0]):"Любой",c->{years[0]=c.equals("Любой")?0:Integer.parseInt(c);if(years[1]>0&&years[0]>years[1])years[1]=years[0];paintYears.run();}));
  to.setOnClickListener(v->Ui.choose(this,"По какой год",yearOptions,years[1]>0?String.valueOf(years[1]):"Любой",c->{years[1]=c.equals("Любой")?0:Integer.parseInt(c);if(years[0]>0&&years[1]>0&&years[0]>years[1])years[0]=years[1];paintYears.run();}));
  Ui.add(panel,Ui.title(this,"Возрастной рейтинг",15),20);Ui.Flow ages=new Ui.Flow(this,8);Ui.add(panel,ages,10);List<Button> ageButtons=new ArrayList<>();
  Runnable paintAges=()->{for(int i=0;i<ageButtons.size();i++){int value=i==0?-1:AGES[i-1];Ui.chipState(ageButtons.get(i),rating[0]==value);ageButtons.get(i).setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);}};
  for(int i=0;i<=AGES.length;i++){int value=i==0?-1:AGES[i-1];Button b=Ui.chip(this,i==0?"Любой":value+"+");b.setOnClickListener(v->{rating[0]=value;paintAges.run();});ageButtons.add(b);ages.addView(b);}paintAges.run();
  int[] lengthPick={length};String[] translatePick={translate};long[] studioPick={studioId};String[] studioName={studioTitle};boolean[] finishedPick={finished};
  Ui.add(panel,Ui.title(this,"Длительность",15),20);Ui.Flow lengths=new Ui.Flow(this,8);Ui.add(panel,lengths,10);List<Button> lengthButtons=new ArrayList<>();
  Runnable paintLengths=()->{for(int i=0;i<lengthButtons.size();i++)Ui.chipState(lengthButtons.get(i),lengthPick[0]==i);};
  for(int i=0;i<LENGTHS.length;i++){int value=i;Button b=Ui.chip(this,LENGTHS[i]);b.setOnClickListener(v->{lengthPick[0]=value;paintLengths.run();});lengthButtons.add(b);lengths.addView(b);}paintLengths.run();
  Ui.add(panel,Ui.title(this,"Студия",15),20);Button studio=Ui.field(this,"Студия");Ui.add(panel,studio,10);
  Runnable paintStudio=()->Ui.fieldValue(studio,"Студия",studioPick[0]>0?studioName[0]:"Любая");paintStudio.run();
  studio.setOnClickListener(v->{List<String> names=new ArrayList<>();List<Long> ids=new ArrayList<>();names.add("Любая");ids.add(0L);if(studioPick[0]>0){boolean known=false;for(Object[] st:STUDIOS)if(((Integer)st[0]).longValue()==studioPick[0])known=true;if(!known){names.add(studioName[0]);ids.add(studioPick[0]);}}
   List<Object[]> sorted=new ArrayList<>(Arrays.asList(STUDIOS));sorted.sort((x,y)->((String)x[1]).compareToIgnoreCase((String)y[1]));for(Object[] st:sorted){names.add((String)st[1]);ids.add(((Integer)st[0]).longValue());}
   Ui.choose(this,"Студия",names,studioPick[0]>0?studioName[0]:"Любая",c->{int at=names.indexOf(c);studioPick[0]=at<=0?0:ids.get(at);studioName[0]=at<=0?"":c;paintStudio.run();});});
  Ui.add(panel,Ui.title(this,"Озвучка",15),20);Ui.Flow translates=new Ui.Flow(this,8);Ui.add(panel,translates,10);List<Button> translateButtons=new ArrayList<>();
  Runnable paintTranslates=()->{for(int i=0;i<translateButtons.size();i++)Ui.chipState(translateButtons.get(i),TRANSLATES[i][1].equals(translatePick[0]));};
  for(String[] t:TRANSLATES){Button b=Ui.chip(this,t[0]);b.setOnClickListener(v->{translatePick[0]=t[1];paintTranslates.run();});translateButtons.add(b);translates.addView(b);}paintTranslates.run();
  Button finishedChip=Ui.chip(this,"Только завершённые");Runnable paintFinished=()->Ui.chipState(finishedChip,finishedPick[0]);finishedChip.setOnClickListener(v->{finishedPick[0]=!finishedPick[0];paintFinished.run();});paintFinished.run();Ui.add(panel,finishedChip,16);
  Ui.add(panel,Ui.text(this,"Рейтинг проверяется по загруженным страницам каталога — при редком сочетании фильтров подборка может догружаться дольше.",12,Ui.MUTED),8);
  LinearLayout actions=Ui.row(this);Button reset=Ui.tonal(this,"Сбросить");Button apply=Ui.button(this,"Показать",true);actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.leftMargin=Ui.dp(this,10);actions.addView(apply,ap);Ui.add(panel,actions,22);
  android.app.Dialog dialog=Ui.sheet(this,"Фильтры",panel,"Закрыть фильтры");
  reset.setOnClickListener(v->{genres.clear();fromYear=toYear=0;age=-1;length=0;studioId=0;studioTitle="";translate="";finished=false;dialog.dismiss();updateChips();load(false);});
  apply.setOnClickListener(v->{genres.clear();genres.addAll(chosen);fromYear=years[0];toYear=years[1];age=rating[0];length=lengthPick[0];translate=translatePick[0];studioId=studioPick[0];studioTitle=studioName[0];finished=finishedPick[0];dialog.dismiss();if(!query.isEmpty()){query="";search.setText("");}updateChips();load(false);});
  Api.IO.execute(()->{try{if(genreCache==null)genreCache=Api.get("anime/genres").optJSONObject("response");JSONObject data=genreCache;runOnUiThread(()->{if(isDestroyed()||data==null)return;flow.removeView(loadingGenres);JSONArray all=data.optJSONArray("genres");if(all==null)return;
    for(int i=0;i<all.length();i++){JSONObject g=all.optJSONObject(i);if(g==null)continue;int id=g.optInt("value",g.optInt("id"));String name=g.optString("title");if(id<=0||name.isEmpty())continue;Button chip=Ui.chip(this,name);chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);Ui.chipState(chip,chosen.contains(id));chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);
     chip.setOnClickListener(v->{if(!chosen.remove(id))chosen.add(id);Ui.chipState(chip,chosen.contains(id));chip.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,null,null);chip.setContentDescription(name+(chosen.contains(id)?", выбран":""));});flow.addView(chip);}});}
   catch(Exception e){runOnUiThread(()->loadingGenres.setText("Не удалось загрузить жанры. Годы и рейтинг работают."));}});
 }
 void setStatus(String value){status.setText(value);status.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
 /** Сначала 50 самых смотрибельных страниц вперемешку, затем остальной каталог тоже вперемешку: лента бесконечная и каждый раз новая. */
 static List<Integer> shuffledPages(Random random){List<Integer> top=new ArrayList<>(),rest=new ArrayList<>();for(int i=0;i<RANDOM_PAGES;i++)(i<50?top:rest).add(i);Collections.shuffle(top,random);Collections.shuffle(rest,random);top.addAll(rest);return top;}
 void load(boolean append){
  int request=++generation;loading=true;failedAppend=append;updateChrome();errorPanel.setVisibility(View.GONE);more.setEnabled(false);retry.setEnabled(false);
  if(!append){shown.clear();offset=0;hasMore=false;autoLoads=0;((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();grid.setSelection(0);}spinner.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyPanel.setVisibility(View.GONE);setStatus(append?"Загружаем…":"");more.setVisibility(View.GONE);
  String requestedPage=page,requestedQuery=query,requestedState=state,requestedList=list;int start=offset;
  // «Случайные»: страницы популярного каталога в случайном порядке, внутри страницы — перемешанные аниме. Каждая новая загрузка даёт новую подборку.
  boolean random=page.equals("catalog")&&sort.equals("random")&&query.isEmpty();if(random&&!append)pages=shuffledPages(new Random());List<Integer> candidates=new ArrayList<>(pages);Set<Long> known=new HashSet<>();for(Anime anime:shown)known.add(anime.id);
  String filterParams=page.equals("catalog")?filterQuery():"";int requestedAge=page.equals("catalog")?age:-1;
  String path=page.equals("schedule")?"anime/schedule":"anime?limit=24&sort="+(sort.equals("random")?"views":sort)+"&sort_forward=false&offset="+start+"&q="+Api.encode(query)+(state.isEmpty()?"":"&status="+state)+filterParams;
  int[] rawCount={0};
  Api.IO.execute(()->{try{
   List<Anime> data=new ArrayList<>();
   if(random){while(!candidates.isEmpty()){int candidate=candidates.remove(0);List<Anime> found=Api.catalog(Api.get("anime?limit=24&sort=views&sort_forward=false&offset="+candidate*24+(requestedState.isEmpty()?"":"&status="+requestedState)+filterParams));if(found.isEmpty()){candidates.removeIf(other->other>candidate);continue;}if(requestedAge>=0)found.removeIf(a->a.ageValue()!=requestedAge);found.removeIf(a->known.contains(a.id));if(found.isEmpty())continue;Collections.shuffle(found);data=found;break;}}
   else{data=requestedPage.equals("favorites")?(requestedList.isEmpty()?library.favorites():new Account(this).list(requestedList)):requestedPage.equals("history")?library.history():Api.catalog(Api.get(path));
   rawCount[0]=data.size();if(requestedAge>=0&&requestedPage.equals("catalog"))data.removeIf(a->a.ageValue()!=requestedAge);}
   if(requestedPage.equals("favorites")||requestedPage.equals("history"))data.removeIf(anime->!anime.title.toLowerCase(Locale.ROOT).contains(requestedQuery.toLowerCase(Locale.ROOT)));
   if(requestedPage.equals("schedule"))data.sort(Comparator.comparingLong(anime->anime.nextDate>0?anime.nextDate:Long.MAX_VALUE));
   List<Anime> result=requestedPage.equals("schedule")?new ArrayList<>(data.subList(Math.min(start,data.size()),Math.min(start+24,data.size()))):data;
   runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;offset=start+(requestedPage.equals("catalog")&&!random?rawCount[0]:result.size());Set<Long>ids=new HashSet<>();for(Anime anime:shown)ids.add(anime.id);for(Anime anime:result)if(ids.add(anime.id))shown.add(anime);if(random)pages=candidates;hasMore=random?!candidates.isEmpty():requestedPage.equals("catalog")?rawCount[0]==24:requestedPage.equals("schedule")&&result.size()==24;render();if(requestedAge>=0&&hasMore&&shown.size()<12&&autoLoads++<8){load(true);return;}if(restoreScroll>0){grid.setSelection(Math.min(restoreScroll,Math.max(0,shown.size()-1)));restoreScroll=0;}});
  }catch(Exception exception){runOnUiThread(()->{if(isDestroyed()||request!=generation)return;showLoadError(exception);});}});
 }
 void showLoadError(Exception exception){loading=false;spinner.setVisibility(View.GONE);emptyPanel.setVisibility(View.GONE);retry.setEnabled(true);errorText.setText(exception instanceof Account.Failure?Rewards.error(exception.getMessage()):exception instanceof java.io.IOException?exception.getMessage():"Не получилось загрузить аниме. Проверь подключение и попробуй ещё раз.");ViewGroup oldParent=(ViewGroup)errorPanel.getParent();if(shown.isEmpty()&&oldParent!=content){oldParent.removeView(errorPanel);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);ep.leftMargin=Ui.dp(this,Ui.GUTTER);ep.rightMargin=Ui.dp(this,Ui.GUTTER);content.addView(errorPanel,ep);}else if(!shown.isEmpty()&&oldParent!=root){oldParent.removeView(errorPanel);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(Ui.dp(this,Ui.GUTTER),Ui.dp(this,8),Ui.dp(this,Ui.GUTTER),Ui.dp(this,16));root.addView(errorPanel,root.indexOfChild(content),ep);}errorPanel.setVisibility(View.VISIBLE);setStatus("");more.setEnabled(true);more.setVisibility(View.GONE);}
 void render(){spinner.setVisibility(View.GONE);errorPanel.setVisibility(View.GONE);setStatus("");more.setEnabled(true);more.setVisibility(View.GONE);emptyPanel.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);emptyTitle.setText(query.isEmpty()?(page.equals("favorites")&&!list.isEmpty()?"Список «"+Account.listLabel(list)+"» пуст":page.equals("favorites")?"Сохрани любимые истории":page.equals("history")?"Начни свою историю":"Пока ничего нет"):"Ничего не нашлось");emptyHint.setText(!query.isEmpty()?((page.equals("favorites")||page.equals("history"))?"Попробуй другое название или очисти поиск.":"Попробуй другое название или измени фильтры."):page.equals("favorites")&&!list.isEmpty()?"Добавь аниме кнопкой «В список» в его карточке — или просто начни смотреть.":page.equals("favorites")?"Нажми на сердце в карточке аниме — оно появится здесь.":page.equals("history")?"Открой серию, и мы сохраним её здесь для продолжения.":"Загляни сюда немного позже.");((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();}
 final class Holder{LinearLayout card;ImageView poster;TextView score,badge,name,meta,note;}
 final class CatalogAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int position){return shown.get(position);}public long getItemId(int position){return shown.get(position).id;}public boolean hasStableIds(){return true;}
  public View getView(int position,View recycled,ViewGroup parent){
   Holder holder;MainActivity c=MainActivity.this;
   if(recycled==null){holder=new Holder();holder.card=Ui.column(c);holder.card.setBackground(Ui.ripple(c,android.graphics.Color.TRANSPARENT,16));
    FrameLayout art=new FrameLayout(c);Ui.round(art,16);Ui.lift(art,8);art.setForeground(Ui.outlined(c,android.graphics.Color.TRANSPARENT,16));int height=Math.round((getResources().getConfiguration().screenWidthDp-2*Ui.GUTTER-(columns()-1)*(float)GAP)/columns()*1.42f);holder.card.addView(art,new LinearLayout.LayoutParams(-1,Ui.dp(c,height)));
    holder.poster=new ImageView(c);art.addView(holder.poster,new FrameLayout.LayoutParams(-1,-1));View scrim=new View(c);scrim.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x00000000,0xCC0E0E11}));art.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
    holder.score=Ui.badge(c,Ui.SCRIM,Ui.TEXT);FrameLayout.LayoutParams rating=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);rating.setMargins(Ui.dp(c,10),Ui.dp(c,10),Ui.dp(c,10),0);art.addView(holder.score,rating);
    holder.badge=Ui.badge(c,Ui.SCRIM,Ui.LIVE);FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);corner.setMargins(Ui.dp(c,10),0,Ui.dp(c,10),Ui.dp(c,10));art.addView(holder.badge,corner);
    holder.name=Ui.title(c,"",15);holder.name.setMaxLines(2);holder.name.setMinLines(2);holder.name.setEllipsize(TextUtils.TruncateAt.END);holder.name.setLineSpacing(Ui.dp(c,2),1);Ui.add(holder.card,holder.name,12);
    holder.meta=Ui.text(c,"",12,Ui.MUTED);holder.meta.setMaxLines(1);holder.meta.setEllipsize(TextUtils.TruncateAt.END);Ui.add(holder.card,holder.meta,6);
    holder.note=Ui.text(c,"",12,Ui.ACCENT);holder.note.setTypeface(Ui.MEDIUM);holder.note.setMaxLines(2);holder.note.setCompoundDrawablePadding(Ui.dp(c,6));Ui.add(holder.card,holder.note,8);holder.card.setTag(holder);recycled=holder.card;
   }else holder=(Holder)recycled.getTag();
   Anime anime=shown.get(position);holder.name.setText(anime.title);holder.score.setText(Ui.score(anime));holder.score.setVisibility(anime.rating>0?View.VISIBLE:View.GONE);holder.meta.setText((anime.year>0?anime.year+" · ":"")+anime.type);holder.poster.setContentDescription(anime.title);Ui.poster(c,holder.poster,anime.poster);
   String key=anime.statusKey();String badge=key.equals("ongoing")?"● Онгоинг":key.equals("announcement")?"Анонс":"";holder.badge.setText(badge);holder.badge.setTextColor(key.equals("ongoing")?Ui.LIVE:Ui.ACCENT);holder.badge.setVisibility(badge.isEmpty()||page.equals("schedule")?View.GONE:View.VISIBLE);
   String note="",icon="";
   if(page.equals("schedule")){icon="calendar";note=anime.nextDate>0?java.time.Instant.ofEpochSecond(anime.nextDate).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.forLanguageTag("ru"))):"Дата пока неизвестна";}
   else if(page.equals("history")){JSONObject last=library.last(anime.id);if(last!=null){icon="play";note="Продолжить · серия "+last.optString("number");}}
   else if(page.equals("favorites")&&!anime.progress().isEmpty()&&!anime.progress().startsWith("0")){icon="play";note="Серий: "+anime.progress();}
   holder.note.setText(note);holder.note.setCompoundDrawablesRelativeWithIntrinsicBounds(icon.isEmpty()?null:new Ui.AppIcon(c,icon,Ui.ACCENT,14),null,null,null);holder.note.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);
   holder.card.setClickable(true);holder.card.setFocusable(true);holder.card.setContentDescription(anime.title+". "+anime.score()+(badge.isEmpty()?"":". "+anime.statusLabel())+(note.isEmpty()?"":". "+note));holder.card.setOnClickListener(v->startActivity(new Intent(c,DetailActivity.class).putExtra("anime",anime.raw.toString())));return recycled;
  }
 }
}
