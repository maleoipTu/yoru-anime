using System.Globalization;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
namespace Yoru;

/// <summary>Адрес и публичный anon-ключ Supabase: из переменных окружения или supabase.properties рядом с Yoru.exe.</summary>
public sealed record SupabaseConfig(string Url, string AnonKey)
{
    public bool Configured => Url.StartsWith("https://") && AnonKey.Length > 0;
    public static SupabaseConfig Load(string? path = null)
    {
        var values = new Dictionary<string, string>();
        path ??= Path.Combine(AppContext.BaseDirectory, "supabase.properties");
        if (File.Exists(path))
            foreach (var line in File.ReadAllLines(path))
            {
                var trimmed = line.Trim();
                if (trimmed.Length == 0 || trimmed.StartsWith('#')) continue;
                var at = trimmed.IndexOf('=');
                if (at > 0) values[trimmed[..at].Trim()] = trimmed[(at + 1)..].Trim();
            }
        var url = Environment.GetEnvironmentVariable("YORU_SUPABASE_URL") is { Length: > 0 } envUrl ? envUrl : values.GetValueOrDefault("url", "");
        var key = Environment.GetEnvironmentVariable("YORU_SUPABASE_ANON_KEY") is { Length: > 0 } envKey ? envKey : values.GetValueOrDefault("anon_key", "");
        return new SupabaseConfig(url.TrimEnd('/'), key);
    }
}

public sealed class AccountException(int status, string message) : Exception(message)
{
    public int Status { get; } = status;
}

/// <summary>Правила очков и уровней — те же, что в функциях yoru_level и award_watch в Supabase.</summary>
public static class Rewards
{
    public const int EpisodePoints = 10, NewAnimeBonus = 5;
    public static int Level(int total) => Math.Max(1, (int)Math.Floor(Math.Sqrt(Math.Max(0, total) / 50.0)) + 1);
    public static int LevelStart(int level) => 50 * (level - 1) * (level - 1);
    public static int ToNext(int total) => LevelStart(Level(total) + 1) - Math.Max(0, total);
    public static double Progress(int total) { var l = Level(total); return Math.Clamp((Math.Max(0, total) - LevelStart(l)) / (double)(LevelStart(l + 1) - LevelStart(l)), 0, 1); }
    /// <summary>Нужное время просмотра: 60% серии, но от 5 до 12 минут; без длительности — 10 минут.</summary>
    public static TimeSpan Threshold(TimeSpan? duration) => duration is { TotalSeconds: > 0 } d
        ? TimeSpan.FromSeconds(Math.Clamp(d.TotalSeconds * 0.6, 300, 720)) : TimeSpan.FromMinutes(10);
    public static bool ValidUsername(string value) => System.Text.RegularExpressions.Regex.IsMatch(value, "^[a-z0-9_]{3,20}$");
    public static bool ValidEmail(string value) => System.Text.RegularExpressions.Regex.IsMatch(value, @"^[^@\s]+@[^@\s]+\.[^@\s]+$");
    public static string Points(int value) => value.ToString("#,0", CultureInfo.GetCultureInfo("ru-RU"));
    public static string Rarity(string value) => value switch { "rare" => "Редкое", "epic" => "Эпическое", "legendary" => "Легендарное", _ => "Обычное" };
    public static string RarityColor(string value) => value switch { "rare" => "#6FC3FF", "epic" => "#BFAEFF", "legendary" => "#FFC768", _ => "#A2A3B6" };
    public static string SkipReason(string reason) => reason switch
    {
        "already" => "За эту серию очки уже получены",
        "cooldown" => "Очки за серии начисляются не чаще раза в 4 минуты",
        "daily_limit" => "Дневной лимит очков исчерпан — продолжим завтра",
        "too_short" => "Посмотри серию подольше, чтобы получить очки",
        _ => ""
    };
    public static bool OnSale(string from, string until, DateTimeOffset now) =>
        (from.Length == 0 || !DateTimeOffset.TryParse(from, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var f) || f <= now) &&
        (until.Length == 0 || !DateTimeOffset.TryParse(until, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var u) || now <= u);
    public static string Until(string iso) => DateTimeOffset.TryParse(iso, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var u)
        ? u.AddDays(-1).ToLocalTime().ToString("d MMMM", CultureInfo.GetCultureInfo("ru-RU")) : "";
    public static string Days(int n) { int m = n % 100, k = n % 10; return n + (m is >= 11 and <= 14 ? " дней" : k == 1 ? " день" : k is >= 2 and <= 4 ? " дня" : " дней"); }
    public static string Error(string? raw)
    {
        var text = (raw ?? "").ToLowerInvariant();
        if (text.Contains("invalid login credentials")) return "Неверная почта или пароль.";
        if (text.Contains("email not confirmed")) return "Подтверди почту по ссылке из письма и войди снова.";
        if (text.Contains("user already registered") || text.Contains("already been registered")) return "Аккаунт с этой почтой уже есть. Попробуй войти.";
        if (text.Contains("password should be") || text.Contains("weak_password")) return "Пароль слишком простой: нужно минимум 6 символов.";
        if (text.Contains("rate limit") || text.Contains("429")) return "Слишком много попыток. Подожди немного.";
        if (text.Contains("not_enough_points")) return "Не хватает очков.";
        if (text.Contains("level_too_low")) return "Нужен более высокий уровень.";
        if (text.Contains("already_owned")) return "Это украшение уже есть.";
        if (text.Contains("not_owned")) return "Сначала получи это украшение.";
        if (text.Contains("user_not_found")) return "Пользователь с таким ником не найден.";
        if (text.Contains("self_friend")) return "Себя в друзья добавить нельзя.";
        if (text.Contains("achievement_only")) return "Это украшение выдаётся только за достижение.";
        if (text.Contains("not_available")) return "Это сезонное украшение сейчас не продаётся.";
        if (text.Contains("too_many_requests")) return "Слишком много неотвеченных заявок.";
        if (text.Contains("username_taken")) return "Этот ник уже занят.";
        if (text.Contains("bad_username")) return "Ник: 3–20 символов, латиница, цифры и _.";
        if (text.Contains("jwt") || text.Contains("not_authenticated") || text.Contains("refresh token")) return "Сессия истекла. Войди в аккаунт снова.";
        return "Не получилось связаться с сервером аккаунтов. Проверь подключение и попробуй ещё раз.";
    }
}

/// <summary>Аккаунт Yoru в Supabase: вход по почте (Supabase Auth), профиль и украшения (PostgREST/RPC).</summary>
public sealed class AccountClient : IDisposable
{
    static AccountClient? shared;
    public static AccountClient Shared => shared ??= new AccountClient(SupabaseConfig.Load(), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Yoru"));

    readonly HttpClient http;
    readonly string root;
    readonly SemaphoreSlim refreshLock = new(1, 1);
    Session session = new();
    public SupabaseConfig Config { get; }
    public JsonObject Profile { get; private set; } = new();
    public JsonArray Decorations { get; private set; } = new();
    public HashSet<string> Owned { get; private set; } = [];
    public event Action? Changed;

    sealed class Session
    {
        public string Access { get; set; } = "";
        public string Refresh { get; set; } = "";
        public long Expires { get; set; }
        public string User { get; set; } = "";
        public string Email { get; set; } = "";
    }

    public AccountClient(SupabaseConfig config, string storageRoot, HttpMessageHandler? handler = null)
    {
        Config = config; root = storageRoot; Directory.CreateDirectory(root);
        http = handler == null ? new HttpClient() : new HttpClient(handler);
        http.Timeout = TimeSpan.FromSeconds(20);
        try
        {
            var path = Path.Combine(root, "account.session");
            if (File.Exists(path)) session = JsonSerializer.Deserialize<Session>(ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser)) ?? new();
            var cache = Path.Combine(root, "account.cache.json");
            if (File.Exists(cache) && JsonNode.Parse(File.ReadAllText(cache)) is JsonObject saved)
            {
                if (saved["profile"] is JsonObject p) Profile = (JsonObject)p.DeepClone();
                if (saved["decorations"] is JsonArray d) Decorations = (JsonArray)d.DeepClone();
            }
        }
        catch (Exception e) when (e is CryptographicException or IOException or JsonException) { session = new(); }
    }

    public bool SignedIn => Config.Configured && session.Refresh.Length > 0;
    public string Email => session.Email;
    public string UserId => session.User;

    void SaveSession()
    {
        var bytes = ProtectedData.Protect(JsonSerializer.SerializeToUtf8Bytes(session), null, DataProtectionScope.CurrentUser);
        var path = Path.Combine(root, "account.session");
        File.WriteAllBytes(path + ".tmp", bytes); File.Move(path + ".tmp", path, true);
    }
    void SaveCache()
    {
        var path = Path.Combine(root, "account.cache.json");
        File.WriteAllText(path + ".tmp", new JsonObject { ["profile"] = Profile.DeepClone(), ["decorations"] = Decorations.DeepClone() }.ToJsonString());
        File.Move(path + ".tmp", path, true);
        Changed?.Invoke();
    }
    void Store(JsonNode result)
    {
        session.Access = result["access_token"]?.GetValue<string>() ?? throw new AccountException(500, "no session");
        session.Refresh = result["refresh_token"]?.GetValue<string>() ?? "";
        session.Expires = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + (result["expires_in"]?.GetValue<long>() ?? 3600);
        session.User = result["user"]?["id"]?.GetValue<string>() ?? session.User;
        session.Email = result["user"]?["email"]?.GetValue<string>() ?? session.Email;
        SaveSession();
    }

    async Task<string> Send(HttpMethod method, string path, JsonNode? body, string? bearer, string? accept = null, CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(method, Config.Url + path);
        request.Headers.Add("apikey", Config.AnonKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", bearer ?? Config.AnonKey);
        request.Headers.Accept.ParseAdd(accept ?? "application/json");
        if (body != null) request.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json");
        using var response = await http.SendAsync(request, ct);
        var text = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new AccountException((int)response.StatusCode, Describe(text, (int)response.StatusCode));
        return text;
    }
    static string Describe(string text, int code)
    {
        try
        {
            if (JsonNode.Parse(text) is JsonObject o)
                foreach (var key in new[] { "error_code", "msg", "message", "error_description", "error" })
                    if (o[key] is JsonValue v && v.ToString() is { Length: > 0 } s) return s + " " + o["message"];
        }
        catch (JsonException) { }
        return "HTTP " + code;
    }

    /// <summary>true — вход выполнен; false — Supabase ждёт подтверждения почты.</summary>
    public async Task<bool> SignUp(string email, string password, string username)
    {
        var available = await Send(HttpMethod.Post, "/rest/v1/rpc/username_available", new JsonObject { ["p_username"] = username }, null);
        if (available.Trim() != "true") throw new AccountException(409, "username_taken");
        var result = JsonNode.Parse(await Send(HttpMethod.Post, "/auth/v1/signup",
            new JsonObject { ["email"] = email, ["password"] = password, ["data"] = new JsonObject { ["username"] = username, ["display_name"] = username } }, null))!;
        if (result["access_token"] == null) return false;
        Store(result); return true;
    }
    public Task Recover(string email) => Send(HttpMethod.Post, "/auth/v1/recover", new JsonObject { ["email"] = email }, null);
    public async Task SignIn(string email, string password) =>
        Store(JsonNode.Parse(await Send(HttpMethod.Post, "/auth/v1/token?grant_type=password", new JsonObject { ["email"] = email, ["password"] = password }, null))!);
    public void SignOut()
    {
        var access = session.Access;
        session = new(); Profile = new(); Owned = []; IsAdmin = false;
        try { File.Delete(Path.Combine(root, "account.session")); File.Delete(Path.Combine(root, "account.cache.json")); } catch (IOException) { }
        if (access.Length > 0) _ = Send(HttpMethod.Post, "/auth/v1/logout", new JsonObject(), access).ContinueWith(_ => { });
        Changed?.Invoke();
    }
    async Task<string> Token()
    {
        if (!SignedIn) throw new AccountException(401, "not_authenticated");
        await refreshLock.WaitAsync();
        try
        {
            if (session.Expires - 60 > DateTimeOffset.UtcNow.ToUnixTimeSeconds()) return session.Access;
            try { Store(JsonNode.Parse(await Send(HttpMethod.Post, "/auth/v1/token?grant_type=refresh_token", new JsonObject { ["refresh_token"] = session.Refresh }, null))!); }
            catch (AccountException e) when (e.Status is 400 or 401) { session.Refresh = ""; session.Access = ""; SaveSession(); throw; }
            return session.Access;
        }
        finally { refreshLock.Release(); }
    }

    public async Task Refresh()
    {
        var token = await Token();
        var profile = JsonNode.Parse(await Send(HttpMethod.Get, $"/rest/v1/profiles?id=eq.{session.User}&select=*", null, token, "application/vnd.pgrst.object+json"))!.AsObject();
        var decorations = JsonNode.Parse(await Send(HttpMethod.Get, "/rest/v1/decorations?select=*&order=kind.asc,sort.asc", null, token))!.AsArray();
        var owned = JsonNode.Parse(await Send(HttpMethod.Get, $"/rest/v1/inventory?user_id=eq.{session.User}&select=decoration_id", null, token))!.AsArray();
        Profile = profile; Decorations = decorations;
        Owned = owned.Select(o => o?["decoration_id"]?.GetValue<string>() ?? "").ToHashSet();
        SaveCache();
    }
    public async Task<JsonNode?> Rpc(string name, JsonObject arguments)
    {
        var text = await Send(HttpMethod.Post, "/rest/v1/rpc/" + name, arguments, await Token());
        // Функции без результата (void) отвечают пустым телом — это успех.
        var result = string.IsNullOrWhiteSpace(text) ? null : JsonNode.Parse(text);
        if (result is JsonObject o && o.ContainsKey("equipped_frame")) { Profile = (JsonObject)o.DeepClone(); SaveCache(); }
        return result;
    }
    public Task<JsonNode?> UpdateProfile(JsonObject fields) => Rpc("update_profile", fields);
    public async Task Buy(string id, string kind)
    {
        await Rpc("buy_decoration", new JsonObject { ["p_id"] = id });
        Owned.Add(id);
        await Rpc("equip_decoration", new JsonObject { ["p_kind"] = kind, ["p_id"] = id });
    }
    public Task<JsonNode?> Equip(string kind, string id) => Rpc("equip_decoration", new JsonObject { ["p_kind"] = kind, ["p_id"] = id });
    public async Task<JsonObject> Award(Anime anime, long videoId, string episode, int seconds)
    {
        var tz = (int)TimeZoneInfo.Local.GetUtcOffset(DateTime.Now).TotalMinutes;
        var result = (await Rpc("award_watch", new JsonObject { ["p_anime_id"] = anime.Id, ["p_video_id"] = videoId, ["p_episode"] = episode, ["p_seconds"] = seconds,
            ["p_title"] = anime.Title, ["p_poster"] = anime.Poster, ["p_total"] = anime.EpisodeCount, ["p_tz_minutes"] = tz }))!.AsObject();
        if (result["awarded"]?.GetValue<bool>() == true)
        {
            Profile["points"] = result["balance"]?.GetValue<int>() ?? 0;
            Profile["total_earned"] = result["total"]?.GetValue<int>() ?? 0;
            Profile["episodes_watched"] = Int(Profile, "episodes_watched") + 1;
            Profile["streak_days"] = Int(result, "streak");
            SaveCache();
        }
        return result;
    }

    // ───── Публичные данные (работают и без входа) ─────
    async Task<string?> Bearer() => SignedIn ? await Token() : null;
    public async Task<JsonNode?> PublicRpc(string name, JsonObject arguments) { var text = await Send(HttpMethod.Post, "/rest/v1/rpc/" + name, arguments, await Bearer()); return string.IsNullOrWhiteSpace(text) ? null : JsonNode.Parse(text); }
    public async Task<JsonArray> Get(string path) => JsonNode.Parse(await Send(HttpMethod.Get, path, null, await Bearer()))!.AsArray();

    // ───── Списки ─────
    public static readonly string[] Lists = ["watching", "planned", "completed", "dropped"];
    public static string ListLabel(string status) => status switch { "watching" => "Смотрю", "planned" => "Запланировано", "completed" => "Просмотрено", "dropped" => "Брошено", _ => "Избранное" };
    public async Task<List<Anime>> List(string status) =>
        (await Get($"/rest/v1/anime_lists?user_id=eq.{UserId}&status=eq.{status}&select=anime_id,title,poster,episodes,total&order=updated_at.desc&limit=500"))
        .Select(r => new Anime { Id = Long(r, "anime_id"), Title = Text(r, "title"), Poster = Text(r, "poster"), EpisodeCount = Int(r, "total"),
            DisplayNote = Int(r, "episodes") > 0 ? $"Серий: {Int(r, "episodes")}" + (Int(r, "total") > 0 ? $" из {Int(r, "total")}" : "") : "" }).ToList();
    public async Task<JsonObject> ListEntry(long animeId) => (await Get($"/rest/v1/anime_lists?user_id=eq.{UserId}&anime_id=eq.{animeId}&select=status,episodes,total")).FirstOrDefault() as JsonObject ?? new JsonObject();
    public async Task<JsonObject> SetList(Anime anime, string status) => (await Rpc("set_list_status", new JsonObject { ["p_anime_id"] = anime.Id, ["p_status"] = status, ["p_title"] = anime.Title, ["p_poster"] = anime.Poster, ["p_total"] = anime.EpisodeCount }))!.AsObject();

    // ───── Оценки и отзывы ─────
    public async Task<JsonObject> Rating(long animeId) => (await PublicRpc("anime_rating", new JsonObject { ["p_anime_id"] = animeId }))!.AsObject();
    public Task<JsonArray> Reviews(long animeId) => Get($"/rest/v1/reviews?anime_id=eq.{animeId}&select=user_id,score,body,updated_at,profiles(username,display_name,avatar_url,equipped_frame,equipped_badge,equipped_color)&order=updated_at.desc&limit=30");
    public async Task<JsonObject> SetReview(Anime anime, int score, string body) => (await Rpc("set_review", new JsonObject { ["p_anime_id"] = anime.Id, ["p_score"] = score, ["p_body"] = body, ["p_title"] = anime.Title, ["p_poster"] = anime.Poster }))!.AsObject();

    // ───── Достижения ─────
    public JsonArray Achievements { get; private set; } = new();
    public HashSet<string> Unlocked { get; private set; } = [];
    public async Task LoadAchievements()
    {
        Achievements = await Get("/rest/v1/achievements?select=*&order=sort.asc");
        if (SignedIn) Unlocked = (await Get($"/rest/v1/user_achievements?user_id=eq.{UserId}&select=achievement_id")).Select(r => Text(r, "achievement_id")).ToHashSet();
    }
    public async Task EnsureCatalog()
    {
        if (Decorations.Count == 0) { Decorations = await Get("/rest/v1/decorations?select=*&order=kind.asc,sort.asc"); }
        if (Achievements.Count == 0) Achievements = await Get("/rest/v1/achievements?select=*&order=sort.asc");
    }
    /// <summary>Текст про только что открытые достижения из ответа сервера, или пустая строка.</summary>
    public static string Announce(JsonNode? result) => string.Join("  ", (result?["achievements"] as JsonArray ?? new JsonArray()).Select(a =>
        $"🏆 «{Text(a, "title")}»" + (Int(a, "points") > 0 ? $" +{Int(a, "points")}" : "") + (Text(a, "reward").Length > 0 ? " и украшение" : "")));

    // ───── Друзья ─────
    public async Task<JsonArray> Friends() => (await Rpc("my_friends", new JsonObject()))!.AsArray();
    public async Task<JsonArray> Feed() => (await Rpc("friend_feed", new JsonObject()))!.AsArray();
    public async Task<JsonArray> Search(string query)
    {
        var q = new string(query.ToLowerInvariant().Where(c => c is (>= 'a' and <= 'z') or (>= '0' and <= '9') or '_').ToArray());
        return q.Length == 0 ? new JsonArray() : await Get($"/rest/v1/profiles?username=ilike.*{q}*&select=id,username,display_name,avatar_url,status,equipped_frame,equipped_badge,equipped_color,total_earned&order=username.asc&limit=20");
    }
    public async Task<JsonObject?> PublicProfile(string username) => await PublicRpc("public_profile", new JsonObject { ["p_username"] = username }) as JsonObject;

    public static long Long(JsonNode? node, string key) => node?[key] is JsonValue v && v.TryGetValue<long>(out var l) ? l : 0;
    public static bool Bool(JsonNode? node, string key) => node?[key] is JsonValue v && v.TryGetValue<bool>(out var b) && b;
    // ───── Хранилище аватаров и баннеров (бакет media, папка пользователя) ─────
    public async Task<string> Upload(string name, byte[] data, string mime)
    {
        var path = $"{UserId}/{name}";
        using var request = new HttpRequestMessage(HttpMethod.Post, $"{Config.Url}/storage/v1/object/media/{path}");
        request.Headers.Add("apikey", Config.AnonKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", await Token());
        request.Headers.Add("x-upsert", "true");
        request.Headers.CacheControl = new CacheControlHeaderValue { MaxAge = TimeSpan.FromDays(365) };
        request.Content = new ByteArrayContent(data);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue(mime);
        using var response = await http.SendAsync(request);
        if (!response.IsSuccessStatusCode) throw new AccountException((int)response.StatusCode, Describe(await response.Content.ReadAsStringAsync(), (int)response.StatusCode));
        return $"{Config.Url}/storage/v1/object/public/media/{path}";
    }
    /// <summary>Удаляет свой прежний файл, если адрес указывает на хранилище Yoru.</summary>
    public async Task DeleteOwn(string url)
    {
        var prefix = $"{Config.Url}/storage/v1/object/public/media/";
        if (!url.StartsWith(prefix + UserId + "/")) return;
        try { await Send(HttpMethod.Delete, "/storage/v1/object/media/" + url[prefix.Length..], null, await Token()); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException) { }
    }

    // ───── Админ-панель (доступ выдаётся только через таблицу admins в базе) ─────
    public bool IsAdmin { get; private set; }
    public async Task<bool> CheckAdmin()
    {
        try { IsAdmin = (await Rpc("is_admin", new JsonObject()))?.GetValue<bool>() == true; }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or InvalidOperationException or FormatException) { IsAdmin = false; }
        return IsAdmin;
    }

    public static string Text(JsonNode? node, string key) => node?[key] is JsonValue v && v.TryGetValue<string>(out var s) ? s : "";
    public static int Int(JsonNode? node, string key) => node?[key] is JsonValue v && v.TryGetValue<int>(out var i) ? i : 0;
    public string DisplayName => Text(Profile, "display_name").Trim() is { Length: > 0 } name ? name : Text(Profile, "username") is { Length: > 0 } nick ? nick : "Гость";
    public JsonObject Style(string id) => Decorations.FirstOrDefault(d => Text(d, "id") == id)?["style"] as JsonObject ?? new JsonObject();

    public void Dispose() => http.Dispose();
}
