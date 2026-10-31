using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
namespace Yoru;

public sealed class YummyClient : IDisposable
{
    readonly HttpClient http;
    public string Token { get; set; } = "";
    public YummyClient(HttpMessageHandler? handler = null)
    {
        http = handler == null ? new HttpClient() : new HttpClient(handler);
        http.BaseAddress = new Uri("https://api.yani.tv/");
        http.Timeout = TimeSpan.FromSeconds(25);
    }
    public async Task<JsonElement> Get(string path, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(Token)) throw new InvalidOperationException("Добавьте ключ приложения YummyAnime в настройках.");
        using var request = new HttpRequestMessage(HttpMethod.Get, path);
        request.Headers.Add("X-Application", Token.Trim());
        request.Headers.Add("Lang", "ru");
        request.Headers.Add("Accept", "application/json,image/jpeg");
        using var result = await http.SendAsync(request, ct);
        var code = (int)result.StatusCode;
        if (code is 401 or 403) throw new InvalidOperationException("API отклонил ключ или доступ к этому аниме. Проверьте ключ и разрешения приложения.");
        if (code == 420) throw new InvalidOperationException("YummyAnime требует CAPTCHA. Откройте сайт в браузере и повторите попытку позже.");
        if (code == 429) throw new InvalidOperationException("Слишком много запросов. Подождите немного и повторите попытку.");
        if (!result.IsSuccessStatusCode) throw new InvalidOperationException($"YummyAnime временно недоступен (код {code}). Попробуйте позже.");
        using var doc = JsonDocument.Parse(await result.Content.ReadAsStringAsync(ct));
        if (ApiParser.Field(doc.RootElement, "error").ValueKind is not (JsonValueKind.Undefined or JsonValueKind.Null or JsonValueKind.False))
            throw new InvalidOperationException("API вернул ошибку. Проверьте ключ приложения и повторите запрос.");
        return doc.RootElement.Clone();
    }
    public async Task<List<Anime>> Catalog(string query, string sort, string status, int offset, CancellationToken ct, string filters = "")
    {
        var path = $"anime?limit=24&offset={offset}&sort={Uri.EscapeDataString(sort)}&sort_forward=false&q={Uri.EscapeDataString(query)}";
        if (status.Length > 0) path += "&status=" + Uri.EscapeDataString(status);
        path += filters;
        return ApiParser.Catalog(await Get(path, ct));
    }
    public async Task<(Anime Anime, List<Episode> Videos)> Detail(long id, CancellationToken ct)
    {
        var root = await Get($"anime/{id}?need_videos=true", ct);
        var e = ApiParser.Field(root, "response");
        if (e.ValueKind != JsonValueKind.Object) throw new InvalidOperationException("API не вернул карточку аниме.");
        return (ApiParser.Anime(e), ApiParser.Videos(ApiParser.Field(e, "videos")));
    }
    /// <summary>Быстрый поиск для подсказок: /search, при пустом ответе — обычный каталог.</summary>
    public async Task<List<Anime>> Suggest(string query, CancellationToken ct)
    {
        var found = ApiParser.Catalog(await Get($"search?q={Uri.EscapeDataString(query)}&limit=6", ct));
        if (found.Count == 0) found = ApiParser.Catalog(await Get($"anime?limit=6&q={Uri.EscapeDataString(query)}", ct));
        return found.Take(6).ToList();
    }
    /// <summary>Жанры для фильтра: (id, название).</summary>
    public async Task<List<(int Id, string Title)>> Genres(CancellationToken ct)
    {
        var root = ApiParser.Field(await Get("anime/genres", ct), "response");
        return ApiParser.Array(ApiParser.Field(root, "genres")).Select(g => ((int)ApiParser.Number(ApiParser.Field(g, "value")), ApiParser.Text(ApiParser.Field(g, "title"))))
            .Where(g => g.Item1 > 0 && g.Item2.Length > 0).ToList();
    }
    public async Task<List<Anime>> Schedule(CancellationToken ct) => ApiParser.Catalog(await Get("anime/schedule", ct));
    public void Dispose() => http.Dispose();
}

public sealed class LibraryData
{
    public List<Anime> Favorites { get; set; } = [];
    public List<WatchEntry> History { get; set; } = [];
    /// <summary>Позиция в серии (секунды) по ID видео — для нативного плеера.</summary>
    public Dictionary<string, double> Positions { get; set; } = [];
    public string Quality { get; set; } = "";
    public double Speed { get; set; } = 1;
    public bool AutoNext { get; set; } = true;
    /// <summary>Application ID из Discord Developer Portal для статуса «Смотрит» в Discord.</summary>
    public string DiscordClientId { get; set; } = "";
    public bool DiscordPresence { get; set; } = true;
    public bool NotifyEpisodes { get; set; } = true;
    /// <summary>Сколько серий уже вышло у отслеживаемых аниме — для уведомлений о новых.</summary>
    public Dictionary<string, int> Aired { get; set; } = [];
}
public sealed class LocalStore
{
    public string Root { get; }
    public LibraryData Data { get; private set; } = new();
    public string? LoadWarning { get; private set; }
    public LocalStore(string? root = null)
    {
        Root = root ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Yoru");
        Directory.CreateDirectory(Root);
        var path = Path.Combine(Root, "library.json");
        if (!File.Exists(path)) return;
        try { Data = JsonSerializer.Deserialize<LibraryData>(File.ReadAllText(path)) ?? new(); }
        catch (Exception e) when (e is JsonException or IOException) { LoadWarning = "Не удалось прочитать библиотеку. Исходный файл сохранён; резервная копия будет создана перед записью."; }
    }
    public string ReadToken()
    {
        var path = Path.Combine(Root, "application.key");
        if (!File.Exists(path)) return "";
        try { return Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser)); }
        catch (Exception e) when (e is CryptographicException or IOException) { LoadWarning = "Не удалось прочитать ключ приложения. Введите его повторно в настройках."; return ""; }
    }
    public void SaveToken(string token) => Atomic("application.key", ProtectedData.Protect(Encoding.UTF8.GetBytes(token), null, DataProtectionScope.CurrentUser));
    public void Save()
    {
        if (LoadWarning != null && File.Exists(Path.Combine(Root, "library.json")))
            File.Copy(Path.Combine(Root, "library.json"), Path.Combine(Root, "library.recovery-" + DateTime.Now.ToString("yyyyMMddHHmmssfff") + ".json"));
        Atomic("library.json", JsonSerializer.SerializeToUtf8Bytes(Data, new JsonSerializerOptions { WriteIndented = true }));
        LoadWarning = null;
    }
    void Atomic(string name, byte[] bytes)
    {
        var path = Path.Combine(Root, name);
        File.WriteAllBytes(path + ".tmp", bytes);
        File.Move(path + ".tmp", path, true);
    }
    public double Position(long videoId) => Data.Positions.TryGetValue(videoId.ToString(), out var seconds) ? seconds : 0;
    public bool ToggleFavorite(Anime anime)
    {
        var old = Data.Favorites.FirstOrDefault(a => a.Id == anime.Id);
        if (old == null) Data.Favorites.Add(anime); else Data.Favorites.Remove(old);
        Save(); return old == null;
    }
    public void Started(Anime anime, Episode video)
    {
        Data.History.RemoveAll(w => w.Anime.Id == anime.Id);
        Data.History.Insert(0, new WatchEntry(anime, video.Id, video.Number, video.Track, DateTimeOffset.Now));
        if (Data.History.Count > 200) Data.History.RemoveRange(200, Data.History.Count - 200);
        Save();
    }
}
