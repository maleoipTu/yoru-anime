package app.yoru.anime;

final class PlaybackProgress {
    static long resume(long position,long duration) {
        if(position<0)return 0;
        if(duration>0&&position>=Math.max(0,duration-10000))return 0;
        return position;
    }
    static String time(long millis) {
        long seconds=Math.max(0,millis)/1000;
        return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    static long seek(long position,long delta,long duration) {
        long result=Math.max(0,position+delta);
        return duration>0?Math.min(duration,result):result;
    }
}
