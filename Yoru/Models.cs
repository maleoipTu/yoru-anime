using System.Globalization;
using System.Net;
using System.Text.Json;
using System.Text.RegularExpressions;
namespace Yoru;

public sealed record Anime
{
    public long Id { get; init; }
    public string Alias { get; init; } = "";
    public string Title { get; init; } = "";
    public string Description { get; init; } = "";
    public string Poster { get; init; } = "";
    public int Year { get; init; }
    public double Rating { get; init; }
    public string Type { get; init; } = "";
    public string Status { get; init; } = "";
    public string StatusAlias { get; init; } = "";
    public string Genres { get; init; } = "";
    public string Age { get; init; } = "";
    public int EpisodeCount { get; init; }
    public long NextEpisode { get; init; }
    public string DisplayNote { get; init; } = "";
    /// <summary>Студии (только в карточке аниме).</summary>
    [System.Text.Json.Serialization.JsonIgnore] public IReadOnlyList<Studio> Studios { get; init; } = [];
    /// <summary>Порядок просмотра франшизы (только в карточке аниме).</summary>
    [System.Text.Json.Serialization.JsonIgnore] public IReadOnlyList<FranchisePart> Franchise { get; init; } = [];
    public string Meta => string.Join("  ·  ", new[] { Year > 0 ? Year.ToString() : "", Type, StatusLabel }.Where(x => x.Length > 0));
    /** Статус с заглавной буквы: «Онгоинг», «Вышел». */
    public string StatusLabel => Status.Length > 0 ? char.ToUpper(Status[0], CultureInfo.GetCultureInfo("ru-RU")) + Status[1..] : "";
    public int AgeValue { get; init; } = -1;
    public string Score => Rating > 0 ? "★ " + Rating.ToString("0.0", CultureInfo.InvariantCulture) : "Новинка";
    public string ImagePath => File.Exists(Path.Combine(AppContext.BaseDirectory, "Assets", $"{Id}.jpg"))
        ? Path.Combine(AppContext.BaseDirectory, "Assets", $"{Id}.jpg") : Poster;
    public string ScheduleText => NextEpisode > 0 ? DateTimeOffset.FromUnixTimeSeconds(NextEpisode).ToLocalTime().ToString("dd MMM, HH:mm", CultureInfo.GetCultureInfo("ru-RU")) : "Дата серии пока неизвестна";
}
public sealed record Studio(int Id, string Title);
public sealed record FranchisePart(long AnimeId, string Title, string Poster, string Relation, int Year, string Type, string Status);
public sealed record Episode(long Id, string Number, int Index, string Dubbing, string Player, string Url)
{
    public override string ToString() => Label;
    public string Track => $"{Dubbing} · {Player}";
    public string Label => $"Серия {Number}";
}
public sealed record WatchEntry(Anime Anime, long VideoId, string Number, string Track, DateTimeOffset StartedAt);

public static class ApiParser
{
    public static JsonElement Field(JsonElement e, string key) => e.ValueKind == JsonValueKind.Object && e.TryGetProperty(key, out var v) ? v : default;
    public static string Text(JsonElement e) => e.ValueKind == JsonValueKind.String ? e.GetString() ?? "" : e.ValueKind == JsonValueKind.Number ? e.GetRawText() : "";
    public static double Number(JsonElement e) => double.TryParse(Text(e), NumberStyles.Any, CultureInfo.InvariantCulture, out var n) ? n : 0;
    public static JsonElement[] Array(JsonElement e) => e.ValueKind == JsonValueKind.Array ? e.EnumerateArray().ToArray() : [];
    public static string Https(string raw) => raw.StartsWith("//") ? "https:" + raw : raw;
    public static string Plain(string raw) => WebUtility.HtmlDecode(Regex.Replace(raw, "<[^>]+>", " ")).Trim();
    public static Anime Anime(JsonElement e) => new()
    {
        Id = (long)Number(Field(e, "anime_id")), Alias = Text(Field(e, "anime_url")), Title = Text(Field(e, "title")),
        Description = Plain(Text(Field(e, "description"))), Poster = Https(Text(Field(Field(e, "poster"), "fullsize"))),
        Year = (int)Number(Field(e, "year")), Rating = Number(Field(Field(e, "rating"), "average")),
        Type = Text(Field(Field(e, "type"), "name")), Status = Text(Field(Field(e, "anime_status"), "title")),
        StatusAlias = Text(Field(Field(e, "anime_status"), "alias")), Age = Text(Field(Field(e, "min_age"), "title_long")),
        AgeValue = Field(Field(e, "min_age"), "value").ValueKind == JsonValueKind.Number ? Field(Field(e, "min_age"), "value").GetInt32() : -1,
        Genres = string.Join(" · ", Array(Field(e, "genres")).Select(g => Text(Field(g, "title")))),
        EpisodeCount = (int)Number(Field(Field(e, "episodes"), "count")), NextEpisode = (long)Number(Field(Field(e, "episodes"), "next_date")),
        Studios = Array(Field(e, "studios")).Select(s => new Studio((int)Number(Field(s, "id")), Text(Field(s, "title")))).Where(s => s.Id > 0 && s.Title.Length > 0).ToList(),
        Franchise = Array(Field(e, "viewing_order")).Select(v => new FranchisePart((long)Number(Field(v, "anime_id")), Text(Field(v, "title")),
            Https(Text(Field(Field(v, "poster"), "medium")) is { Length: > 0 } m ? m : Text(Field(Field(v, "poster"), "fullsize"))),
            Text(Field(Field(v, "data"), "text")), (int)Number(Field(v, "year")), Text(Field(Field(v, "type"), "shortname")), Text(Field(Field(v, "anime_status"), "title"))))
            .Where(f => f.AnimeId > 0 && f.Title.Length > 0).ToList()
    };
    public static List<Anime> Catalog(JsonElement root) => Array(Field(root, "response")).Select(Anime).Where(a => a.Id > 0).ToList();
    public static List<Episode> Videos(JsonElement e) => Array(e).Select(v => new Episode(
        (long)Number(Field(v, "video_id")), Text(Field(v, "number")), (int)Number(Field(v, "index")),
        Text(Field(Field(v, "data"), "dubbing")), Text(Field(Field(v, "data"), "player")), Https(Text(Field(v, "iframe_url")))))
        .Where(v => v.Id > 0 && Uri.TryCreate(v.Url, UriKind.Absolute, out var u) && u.Scheme == "https")
        .OrderBy(v => v.Index).ThenBy(v => NumberText(v.Number)).ToList();
    static double NumberText(string value) => double.TryParse(value, CultureInfo.InvariantCulture, out var n) ? n : double.MaxValue;
}
