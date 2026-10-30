package app.yoru.anime;

import java.util.Locale;

/** Правила очков и уровней — те же, что в функции yoru_level и award_watch в Supabase. */
final class Rewards {
 /** Сервер не начисляет очки, если серия проигрывалась меньше пяти минут. */
 static final long MIN_WATCH_MS=300_000;
 static final int EPISODE_POINTS=10,NEW_ANIME_BONUS=5;

 /** Уровень N требует 50·(N−1)² очков за всё время. */
 static int level(int total){return Math.max(1,(int)Math.floor(Math.sqrt(Math.max(0,total)/50.0))+1);}
 static int levelStart(int level){return 50*(level-1)*(level-1);}
 /** Сколько очков осталось до следующего уровня. */
 static int toNext(int total){return levelStart(level(total)+1)-Math.max(0,total);}
 /** Прогресс внутри текущего уровня, 0…1000. */
 static int progress(int total){int level=level(total),start=levelStart(level),end=levelStart(level+1);return (int)Math.min(1000,Math.max(0,(long)(Math.max(0,total)-start)*1000/(end-start)));}

 /** Сколько реального воспроизведения нужно для начисления: 60% серии, но не меньше 5 и не больше 12 минут. */
 static long threshold(long durationMs){if(durationMs<=0)return 600_000;return Math.max(MIN_WATCH_MS,Math.min(720_000,durationMs*6/10));}

 static boolean validUsername(String value){return value!=null&&value.matches("[a-z0-9_]{3,20}");}
 static boolean validEmail(String value){return value!=null&&value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");}

 static String rarity(String value){switch(value){case "rare":return "Редкое";case "epic":return "Эпическое";case "legendary":return "Легендарное";default:return "Обычное";}}
 static int rarityColor(String value){switch(value){case "rare":return 0xFF6FC3FF;case "epic":return 0xFFBFAEFF;case "legendary":return 0xFFFFC768;default:return 0xFFA2A3B6;}}

 /** Цвет из "#RRGGBB" или "#AARRGGBB"; при ошибке — запасной. */
 static int color(String value,int fallback){
  if(value==null||!value.startsWith("#"))return fallback;
  try{long parsed=Long.parseLong(value.substring(1),16);if(value.length()==7)return (int)(0xFF000000L|parsed);if(value.length()==9)return (int)parsed;}catch(NumberFormatException ignored){}
  return fallback;
 }

 static String points(int value){return String.format(Locale.ROOT,"%,d",value).replace(',',' ');}

 /** Человеческое сообщение для ответа award_watch, когда очки не начислены. */
 static String skipReason(String reason){
  switch(reason){
   case "already":return "За эту серию очки уже получены";
   case "cooldown":return "Очки за серии начисляются не чаще раза в 4 минуты";
   case "daily_limit":return "Дневной лимит очков исчерпан — продолжим завтра";
   case "too_short":return "Посмотри серию подольше, чтобы получить очки";
   default:return "";
  }
 }

 /** Перевод ошибок Supabase Auth и функций базы в понятный текст. */
 static String error(String raw){
  String text=raw==null?"":raw.toLowerCase(Locale.ROOT);
  if(text.contains("invalid login credentials"))return "Неверная почта или пароль.";
  if(text.contains("email not confirmed"))return "Подтверди почту по ссылке из письма и войди снова.";
  if(text.contains("user already registered")||text.contains("already been registered"))return "Аккаунт с этой почтой уже есть. Попробуй войти.";
  if(text.contains("password should be")||text.contains("weak_password"))return "Пароль слишком простой: нужно минимум 6 символов.";
  if(text.contains("rate limit")||text.contains("over_email_send_rate_limit")||text.contains("429"))return "Слишком много попыток. Подожди немного.";
  if(text.contains("not_enough_points"))return "Не хватает очков.";
  if(text.contains("level_too_low"))return "Нужен более высокий уровень.";
  if(text.contains("already_owned"))return "Это украшение уже есть.";
  if(text.contains("not_owned"))return "Сначала получи это украшение.";
  if(text.contains("username_taken"))return "Этот ник уже занят.";
  if(text.contains("bad_username"))return "Ник: 3–20 символов, латиница, цифры и _.";
  if(text.contains("jwt")||text.contains("not_authenticated")||text.contains("refresh token"))return "Сессия истекла. Войди в аккаунт снова.";
  return "Не получилось связаться с сервером аккаунтов. Проверь подключение и попробуй ещё раз.";
 }

 /** Считает реальное время воспроизведения одной серии. */
 static final class WatchMeter {
  long episode=-1,watched,lastTick;boolean sent;
  void start(long episodeId){if(episodeId!=episode){episode=episodeId;watched=0;sent=false;}lastTick=0;}
  /** Вызывается периодически; учитываются только промежутки, когда видео действительно шло, не больше 2 секунд за шаг. */
  void tick(boolean playing,long now){if(playing&&lastTick>0)watched+=Math.min(2000,Math.max(0,now-lastTick));lastTick=playing?now:0;}
  boolean due(long durationMs){return !sent&&watched>=threshold(durationMs);}
  int seconds(){return (int)(watched/1000);}
 }
}
