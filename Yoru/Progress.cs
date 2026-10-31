using System.Text.Json.Nodes;
namespace Yoru;

/// <summary>Прогресс по одному аниме. Watched — номера просмотренных серий; «*» — весь тайтл отмечен просмотренным.</summary>
public sealed class AnimeProgress
{
    public string Episode { get; set; } = "";
    public string Dubbing { get; set; } = "";
    public string Player { get; set; } = "";
    public double Position { get; set; }
    public double Duration { get; set; }
    public List<string> Watched { get; set; } = [];
    public string Title { get; set; } = "";
    public string Poster { get; set; } = "";
    public DateTimeOffset Updated { get; set; }
    public bool Complete => Watched.Contains(Progress.All);
    public bool IsWatched(string number) => Complete || Watched.Contains(number);
    public int WatchedCount => Watched.Count(w => w != Progress.All);
}

/// <summary>
/// Любимая озвучка тайтла, просмотренные серии и позиция просмотра. Хранится локально и, если выполнен вход,
/// синхронизируется с аккаунтом: начал на телефоне — продолжил на ПК.
/// </summary>
public static class Progress
{
    public const string All = "*";
    public static event Action<long>? Changed;
    static readonly Dictionary<long, DateTime> lastSent = [];

    public static AnimeProgress? Get(LocalStore store, long animeId) => store.Data.Progress.GetValueOrDefault(animeId.ToString());
    static AnimeProgress Ensure(LocalStore store, Anime anime)
    {
        var key = anime.Id.ToString();
        if (!store.Data.Progress.TryGetValue(key, out var p)) store.Data.Progress[key] = p = new AnimeProgress();
        if (anime.Title.Length > 0) p.Title = anime.Title;
        if (anime.Poster.Length > 0) p.Poster = anime.Poster;
        return p;
    }
    static void Save(LocalStore store) { try { store.Save(); } catch (IOException) { } }

    /// <summary>Серия, с которой продолжить: сохранённая озвучка и плеер; если серия досмотрена — следующая.</summary>
    public static Episode? Preferred(IReadOnlyList<Episode> videos, AnimeProgress? progress, WatchEntry? history)
    {
        if (videos.Count == 0) return null;
        string? dubbing = progress?.Dubbing is { Length: > 0 } d && videos.Any(v => v.Dubbing == d) ? d : null;
        dubbing ??= PlaybackSources.Resume(videos, history)?.Dubbing;
        if (dubbing == null) return null;
        var players = PlaybackSources.Players(videos, dubbing);
        var player = progress?.Player is { Length: > 0 } pl && players.Contains(pl) ? pl : PlaybackSources.Resume(videos, history) is { } r && r.Dubbing == dubbing ? r.Player : players.FirstOrDefault();
        if (player == null) return null;
        var list = PlaybackSources.Episodes(videos, dubbing, player);
        var number = progress?.Episode is { Length: > 0 } e ? e : history?.Number;
        var index = list.FindIndex(v => v.Number == number);
        if (index >= 0 && progress != null && progress.IsWatched(list[index].Number) && index + 1 < list.Count && (progress.Duration <= 0 || progress.Position <= 0 || progress.Position >= progress.Duration * 0.9)) index++;
        if (index < 0 && progress != null) index = list.FindIndex(v => !progress.IsWatched(v.Number));
        return index >= 0 ? list[index] : list.FirstOrDefault();
    }

    /// <summary>Позиция для старта серии: синхронизированная (если это та же серия) или локальная по видео.</summary>
    public static double StartPosition(LocalStore store, long animeId, Episode episode)
    {
        var local = store.Position(episode.Id);
        var p = Get(store, animeId);
        if (p != null && p.Episode == episode.Number && p.Position > 5 && (p.Duration <= 0 || p.Position < p.Duration - 10)) return p.Position;
        return local;
    }

    /// <summary>Запоминает серию, озвучку и позицию. На сервер — не чаще раза в 20 секунд (или сразу при force).</summary>
    public static void Track(LocalStore store, Anime anime, Episode episode, double position, double duration, bool force = false)
    {
        var p = Ensure(store, anime);
        if (p.Episode != episode.Number) p.Duration = 0;
        p.Episode = episode.Number; p.Dubbing = episode.Dubbing; p.Player = episode.Player;
        p.Position = Math.Max(0, position); if (duration > 0) p.Duration = duration; p.Updated = DateTimeOffset.UtcNow;
        var account = AccountClient.Shared;
        if (!account.SignedIn) return;
        if (!force && lastSent.TryGetValue(anime.Id, out var at) && (DateTime.UtcNow - at).TotalSeconds < 20) return;
        lastSent[anime.Id] = DateTime.UtcNow;
        _ = Send(account, "save_progress", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = episode.Number, ["p_dubbing"] = episode.Dubbing, ["p_player"] = episode.Player,
            ["p_position"] = Math.Round(p.Position, 1), ["p_duration"] = Math.Round(p.Duration, 1), ["p_title"] = anime.Title, ["p_poster"] = anime.Poster });
    }

    /// <summary>Отмечает серию (или весь тайтл, number = «*») просмотренной либо снимает отметку.</summary>
    public static void SetWatched(LocalStore store, Anime anime, string number, bool watched)
    {
        var p = Ensure(store, anime);
        var changes = new List<(string, bool)>();
        void Apply(string n, bool on) { if (on ? !p.Watched.Contains(n) : p.Watched.Remove(n)) { if (on) p.Watched.Add(n); changes.Add((n, on)); } }
        Apply(number, watched);
        if (!watched && number != All) Apply(All, false);
        // Все серии отмечены — тайтл целиком просмотрен (для трекера франшизы).
        if (watched && number != All && anime.EpisodeCount > 0 && p.WatchedCount >= anime.EpisodeCount) Apply(All, true);
        if (changes.Count == 0) return;
        p.Updated = DateTimeOffset.UtcNow; Save(store); Changed?.Invoke(anime.Id);
        var account = AccountClient.Shared;
        if (account.SignedIn) foreach (var (n, on) in changes) _ = Send(account, "set_watched", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = n, ["p_watched"] = on });
    }
    public static void Toggle(LocalStore store, Anime anime, string number) => SetWatched(store, anime, number, !(Get(store, anime.Id)?.IsWatched(number) ?? false));

    static async Task Send(AccountClient account, string rpc, JsonObject args)
    {
        try { await account.Rpc(rpc, args); }
        catch (Exception e) when (e is AccountException or System.Net.Http.HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException) { }
    }

    /// <summary>Применяет прогресс с сервера, если он новее локального. Возвращает true, если что-то изменилось.</summary>
    public static bool Merge(LocalStore store, JsonNode? item)
    {
        if (item is not JsonObject o) return false;
        long id = AccountClient.Int(o, "anime_id");
        if (id <= 0) return false;
        if (!DateTimeOffset.TryParse(AccountClient.Text(o, "updated_at"), System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.AssumeUniversal, out var updated)) return false;
        var key = id.ToString();
        var p = store.Data.Progress.GetValueOrDefault(key);
        if (p != null && p.Updated >= updated) return false;
        p ??= store.Data.Progress[key] = new AnimeProgress();
        p.Episode = AccountClient.Text(o, "episode"); p.Dubbing = AccountClient.Text(o, "dubbing"); p.Player = AccountClient.Text(o, "player");
        p.Position = Number(o["position"]); p.Duration = Number(o["duration"]);
        p.Watched = o["watched"] is JsonArray w ? w.Select(x => x?.GetValue<string>() ?? "").Where(x => x.Length > 0).ToList() : [];
        if (AccountClient.Text(o, "title") is { Length: > 0 } t) p.Title = t;
        if (AccountClient.Text(o, "poster") is { Length: > 0 } poster) p.Poster = poster;
        p.Updated = updated;
        // «Продолжить просмотр» на главной показывает и то, что начато на другом устройстве.
        if (p.Episode.Length > 0 && p.Title.Length > 0)
        {
            var newest = store.Data.History.FirstOrDefault();
            if (newest == null || newest.StartedAt < updated)
            {
                var old = store.Data.History.FirstOrDefault(h => h.Anime.Id == id);
                var anime = old?.Anime ?? new Anime { Id = id, Title = p.Title, Poster = p.Poster };
                store.Data.History.RemoveAll(h => h.Anime.Id == id);
                store.Data.History.Insert(0, new WatchEntry(anime, 0, p.Episode, $"{p.Dubbing} · {p.Player}", updated));
            }
        }
        return true;
    }

    static double Number(JsonNode? n) => n is JsonValue v && v.TryGetValue<double>(out var d) ? d : 0;
    /// <summary>Забирает изменения прогресса с сервера (все аниме, изменённые после прошлой синхронизации).</summary>
    public static async Task<bool> Pull(LocalStore store)
    {
        var account = AccountClient.Shared;
        if (!account.SignedIn) return false;
        try
        {
            var args = new JsonObject { ["p_limit"] = 500 };
            if (store.Data.ProgressSince.Length > 0) args["p_since"] = store.Data.ProgressSince;
            if (await account.Rpc("my_progress", args) is not JsonArray list) return false;
            var changed = false;
            foreach (var item in list.Reverse()) changed |= Merge(store, item);
            if (list.FirstOrDefault() is JsonObject first && AccountClient.Text(first, "updated_at") is { Length: > 0 } since) store.Data.ProgressSince = since;
            if (changed || list.Count > 0) Save(store);
            if (changed) Changed?.Invoke(0);
            return changed;
        }
        catch (Exception e) when (e is AccountException or System.Net.Http.HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException) { return false; }
    }
    /// <summary>Свежий прогресс одного аниме с сервера (при открытии карточки).</summary>
    public static async Task<bool> PullAnime(LocalStore store, long animeId)
    {
        var account = AccountClient.Shared;
        if (!account.SignedIn) return false;
        try { var changed = Merge(store, await account.Rpc("anime_progress", new JsonObject { ["p_anime_id"] = animeId })); if (changed) Save(store); return changed; }
        catch (Exception e) when (e is AccountException or System.Net.Http.HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException) { return false; }
    }
}
