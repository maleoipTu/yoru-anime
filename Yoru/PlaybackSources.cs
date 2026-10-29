namespace Yoru;
public static class PlaybackSources
{
    public static List<string> Dubbings(IEnumerable<Episode> videos) => videos.Select(v => v.Dubbing).Distinct().OrderBy(s => s, StringComparer.CurrentCultureIgnoreCase).ToList();
    public static List<string> Players(IEnumerable<Episode> videos, string dubbing) => videos.Where(v => v.Dubbing == dubbing).Select(v => v.Player).Distinct().OrderBy(Rank).ThenBy(s => s).ToList();
    static int Rank(string name) => name.Contains("Kodik") ? 0 : name.Contains("CVH") ? 1 : name.Contains("Aksor") ? 2 : 3;
    public static List<Episode> Episodes(IEnumerable<Episode> videos, string dubbing, string player) => videos.Where(v => v.Dubbing == dubbing && v.Player == player).DistinctBy(v => v.Number).OrderBy(v => v.Index).ToList();
    public static Episode? Resume(IEnumerable<Episode> videos, WatchEntry? last) => last == null ? null : videos.FirstOrDefault(v => v.Id == last.VideoId) ?? videos.FirstOrDefault(v => v.Track == last.Track && v.Number == last.Number);
    public static Episode? Alternative(IEnumerable<Episode> videos, Episode current)
    {
        var choices = videos.ToList();
        var players = Players(choices, current.Dubbing);
        var start = players.IndexOf(current.Player);
        for (var step = 1; step < players.Count; step++)
        {
            var player = players[(Math.Max(0, start) + step) % players.Count];
            var match = choices.FirstOrDefault(v => v.Dubbing == current.Dubbing && v.Player == player && v.Number == current.Number);
            if (match != null) return match;
        }
        return null;
    }
}
