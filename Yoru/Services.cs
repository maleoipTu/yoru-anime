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
    public async Task<List<Anime>> Catalog(string query, string sort, string status, int offset, CancellationToken ct)
    {
        var path = $"anime?limit=24&offset={offset}&sort={Uri.EscapeDataString(sort)}&sort_forward=false&q={Uri.EscapeDataString(query)}";
        if (status.Length > 0) path += "&status=" + Uri.EscapeDataString(status);
        return ApiParser.Catalog(await Get(path, ct));
    }
    public async Task<(Anime Anime, List<Episode> Videos)> Detail(long id, CancellationToken ct)
    {
        var root = await Get($"anime/{id}?need_videos=true", ct);
        var e = ApiParser.Field(root, "response");
        if (e.ValueKind != JsonValueKind.Object) throw new InvalidOperationException("API не вернул карточку аниме.");
        return (ApiParser.Anime(e), ApiParser.Videos(ApiParser.Field(e, "videos")));
    }
    public async Task<List<Anime>> Schedule(CancellationToken ct) => ApiParser.Catalog(await Get("anime/schedule", ct));
    public void Dispose() => http.Dispose();
}

public sealed class LibraryData
{
    public List<Anime> Favorites { get; set; } = [];
    public List<WatchEntry> History { get; set; } = [];
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
