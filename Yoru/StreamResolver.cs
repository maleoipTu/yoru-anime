using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
namespace Yoru;

/// <summary>
/// Получает прямые видеопотоки Kodik (порт Android-версии). HTML и JavaScript провайдера не выполняются:
/// из страницы берутся только параметры запроса, ответ содержит список потоков.
/// </summary>
public static class StreamResolver
{
    public const string UserAgent = "Yoru/0.4 (Windows)";
    /// <summary>CVH и Aksor подписывают ссылки под обычный браузер: для них запросы идут с браузерным User-Agent.</summary>
    public const string BrowserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    const int MaxBody = 2 * 1024 * 1024;
    public sealed record Stream(string Label, string Url, string Mime);
    /// <summary>Intro — разметка заставок от источника (Aksor): {opening:{start,end}, ending:{start,end}} или null.</summary>
    public sealed record Result(IReadOnlyList<Stream> Streams, string Referer, string Agent = UserAgent, System.Text.Json.Nodes.JsonObject? Intro = null);
    public delegate Task<(string Url, string Text)> Transport(string url, string? body, string? referer, CancellationToken ct);

    public static bool Supports(string url) =>
        Uri.TryCreate(Normalize(url), UriKind.Absolute, out var uri) && uri.Scheme == "https" && (Kodik(uri) || Cvh(uri) || Aksor(uri));
    public static string Normalize(string url) { url = (url ?? "").Trim(); if (url.StartsWith("//")) url = "https:" + url; return url.Replace(" ", "%20"); }
    static bool Kodik(Uri uri) => uri.Host.ToLowerInvariant() is "kodikplayer.com" or "kodik.info" or "kodik.cc";
    /// <summary>CVH: страница-обёртка YummyAnime iframeCVH.html?anime_id=…&amp;episode=…&amp;dubbing_code=…</summary>
    static bool Cvh(Uri uri) { var host = uri.Host.ToLowerInvariant(); return (host == "yummyani.me" || host.EndsWith(".yummyani.me")) && uri.AbsolutePath.EndsWith("/iframeCVH.html"); }
    /// <summary>Aksor: player.aksor.tv/video/{id}</summary>
    static bool Aksor(Uri uri) => uri.Host.Equals("player.aksor.tv", StringComparison.OrdinalIgnoreCase) && Regex.IsMatch(uri.AbsolutePath, "^/video/[A-Za-z0-9_-]{1,64}/?$");
    static Dictionary<string, string> Query(Uri uri)
    {
        var result = new Dictionary<string, string>();
        foreach (var part in uri.Query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var eq = part.IndexOf('='); if (eq <= 0) continue;
            try { result[Uri.UnescapeDataString(part[..eq].Replace('+', ' '))] = Uri.UnescapeDataString(part[(eq + 1)..].Replace('+', ' ')); } catch (UriFormatException) { }
        }
        return result;
    }
    static string? MediaUrl(JsonElement parent, string name)
    {
        if (!parent.TryGetProperty(name, out var value) || value.ValueKind != JsonValueKind.String || value.GetString() is not { Length: > 0 } raw) return null;
        try { var url = Normalize(raw); return SafeUri(url).AbsoluteUri; } catch (Exception e) when (e is InvalidOperationException or UriFormatException) { return null; }
    }

    /// <summary>CVH (cdnvideohub): список серий и озвучек, затем прямые HLS/MP4 с CDN.</summary>
    static async Task<Result> ResolveCvh(Uri page, Transport network, CancellationToken ct)
    {
        var q = Query(page);
        if (!q.TryGetValue("anime_id", out var id) || !Regex.IsMatch(id, "^[0-9]{1,10}$") || !q.TryGetValue("episode", out var episode)) throw Changed();
        var code = q.GetValueOrDefault("dubbing_code", ""); var dubbing = q.GetValueOrDefault("dubbing", "").ToLowerInvariant();
        var list = await network("https://plapi.cdnvideohub.com/api/v1/player/sv/playlist?pub=745&aggr=mali&id=" + id, null, "https://ru.yummyani.me/", ct);
        using var listDoc = JsonDocument.Parse(list.Text);
        if (!listDoc.RootElement.TryGetProperty("items", out var items) || items.ValueKind != JsonValueKind.Array) throw Changed();
        JsonElement? chosen = null, byType = null, first = null;
        foreach (var item in items.EnumerateArray())
        {
            if (item.ValueKind != JsonValueKind.Object || !item.TryGetProperty("episode", out var ep) || ep.ToString() != episode.Trim()) continue;
            first ??= item;
            var studio = item.TryGetProperty("voiceStudio", out var vs) ? vs.ToString() : "";
            if (code.Length > 0 && string.Equals(code, studio, StringComparison.OrdinalIgnoreCase)) { chosen = item; break; }
            var type = item.TryGetProperty("voiceType", out var vt) ? vt.ToString().ToLowerInvariant() : "";
            if (byType == null && type.Length > 0 && dubbing.Contains(type)) byType = item;
        }
        var pick = chosen ?? byType ?? first ?? throw new InvalidOperationException("Источник не вернул эту серию. Попробуй другую озвучку или плеер.");
        var vk = pick.TryGetProperty("vkId", out var v) ? v.ToString() : "";
        if (!Regex.IsMatch(vk, "^[0-9]{1,20}$")) throw Changed();
        var video = await network("https://plapi.cdnvideohub.com/api/v1/player/sv/video/" + vk, null, "https://ru.yummyani.me/", ct);
        using var doc = JsonDocument.Parse(video.Text);
        if (!doc.RootElement.TryGetProperty("sources", out var sources) || sources.ValueKind != JsonValueKind.Object) throw Changed();
        var streams = new List<Stream>(); var seen = new HashSet<string>(); var labels = new HashSet<string>();
        if (MediaUrl(sources, "hlsUrl") is { } hls && seen.Add(hls)) streams.Add(new Stream("Авто", hls, "application/x-mpegURL"));
        foreach (var (name, label) in new[] { ("mpeg4kUrl", "2160p"), ("mpeg2kUrl", "1440p"), ("mpegQhdUrl", "1440p"), ("mpegFullHdUrl", "1080p"), ("mpegHighUrl", "720p"), ("mpegMediumUrl", "480p"), ("mpegLowUrl", "360p"), ("mpegLowestUrl", "240p") })
            if (MediaUrl(sources, name) is { } url && labels.Add(label) && seen.Add(url)) streams.Add(new Stream(label, url, "video/mp4"));
        if (streams.Count == 0) throw new InvalidOperationException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        return new Result(streams, "https://player.cdnvideohub.com/", BrowserAgent);
    }

    /// <summary>Aksor: JSON с DASH-потоками по качествам и разметкой опенинга/эндинга.</summary>
    static async Task<Result> ResolveAksor(Uri page, Transport network, CancellationToken ct)
    {
        var id = page.AbsolutePath["/video/".Length..].Trim('/');
        var api = await network("https://player.aksor.tv/api/video/" + id, null, page.AbsoluteUri, ct);
        using var doc = JsonDocument.Parse(api.Text);
        var data = doc.RootElement;
        if (!data.TryGetProperty("qualities", out var qualities) || qualities.ValueKind != JsonValueKind.Object) throw Changed();
        var streams = new List<Stream>();
        foreach (var (name, label) in new[] { ("q4k", "2160p"), ("q2k", "1440p"), ("q1080", "1080p"), ("q720", "720p"), ("q480", "480p"), ("q360", "360p") })
            if (MediaUrl(qualities, name) is { } url) streams.Add(new Stream(label, url, url.Contains(".m3u8", StringComparison.OrdinalIgnoreCase) ? "application/x-mpegURL" : "application/dash+xml"));
        if (streams.Count == 0) throw new InvalidOperationException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        int Int(string n) => data.TryGetProperty(n, out var x) && x.ValueKind == JsonValueKind.Number && x.TryGetInt32(out var i) ? i : 0;
        var intro = new System.Text.Json.Nodes.JsonObject();
        if (Int("opening_length") > 0) intro["opening"] = new System.Text.Json.Nodes.JsonObject { ["start"] = Int("opening_start"), ["end"] = Int("opening_start") + Int("opening_length"), ["exact"] = true };
        if (Int("ending_length") > 0) intro["ending"] = new System.Text.Json.Nodes.JsonObject { ["start"] = Int("ending_start"), ["end"] = Int("ending_start") + Int("ending_length"), ["exact"] = true };
        return new Result(streams, "https://player.aksor.tv/", BrowserAgent, intro.Count > 0 ? intro : null);
    }

    static readonly HttpClient Http = new(new HttpClientHandler { AllowAutoRedirect = false, AutomaticDecompression = DecompressionMethods.All }) { Timeout = TimeSpan.FromSeconds(20) };

    public static Task<Result> Resolve(string url, CancellationToken ct = default) => Resolve(url, Request, ct);
    public static async Task<Result> Resolve(string url, Transport network, CancellationToken ct = default)
    {
        if (!Supports(url)) throw new InvalidOperationException("Для этого источника прямой поток пока не поддерживается. Выбери Kodik, CVH или Aksor, или открой плеер сайта.");
        url = Normalize(url);
        var target = SafeUri(url);
        if (Cvh(target)) return await ResolveCvh(target, network, ct);
        if (Aksor(target)) return await ResolveAksor(target, network, ct);
        var page = await network(url, null, null, ct);
        if (!Kodik(SafeUri(page.Url))) throw new InvalidOperationException("Источник перенаправил запрос на неизвестный плеер.");
        var form = new List<KeyValuePair<string, string>>();
        foreach (var name in new[] { "domain", "d_sign", "pd", "pd_sign", "ref", "ref_sign" })
        {
            var value = Capture(page.Text, @"\bvar\s+" + name + @"\s*=\s*([""'])(.*?)\1", 2) ?? throw Changed();
            form.Add(new(name == "domain" ? "d" : name, value));
        }
        foreach (var name in new[] { "type", "hash", "id" })
        {
            var value = Capture(page.Text, @"\bvInfo\." + name + @"\s*=\s*([""'])(.*?)\1", 2);
            if (string.IsNullOrEmpty(value)) throw Changed();
            form.Add(new(name, value));
        }
        var script = Capture(page.Text, @"<script[^>]+src=[""']([^""']*/assets/js/app\.player_single[^""']+\.js)[""']", 1) ?? throw Changed();
        var pageUri = SafeUri(page.Url);
        var scriptUri = new Uri(pageUri, script);
        if (!SameOrigin(pageUri, scriptUri)) throw Changed();
        var js = await network(scriptUri.ToString(), null, page.Url, ct);
        if (!SameOrigin(pageUri, SafeUri(js.Url))) throw Changed();
        var encodedRoute = Capture(js.Text, @"url\s*:\s*atob\([""']([A-Za-z0-9+/=]+)[""']\)", 1);
        var shiftText = Capture(js.Text, @"charCodeAt\(0\)\s*\+\s*(\d{1,2})", 1);
        if (encodedRoute == null || shiftText == null) throw Changed();
        var shift = int.Parse(shiftText);
        if (shift > 25) throw Changed();
        string route;
        var padded = encodedRoute.TrimEnd('=');
        padded = padded.PadRight(padded.Length + (4 - padded.Length % 4) % 4, '=');
        try { route = Encoding.UTF8.GetString(Convert.FromBase64String(padded)); } catch (FormatException) { throw Changed(); }
        if (!Regex.IsMatch(route, "^/[a-zA-Z0-9_/-]+$")) throw Changed();
        var endpoint = new Uri(pageUri, route);
        if (!SameOrigin(pageUri, endpoint)) throw Changed();
        var body = string.Join("&", form.Select(p => Uri.EscapeDataString(p.Key) + "=" + Uri.EscapeDataString(p.Value)));
        var response = await network(endpoint.ToString(), body, page.Url, ct);
        if (!SameOrigin(endpoint, SafeUri(response.Url))) throw Changed();
        using var doc = JsonDocument.Parse(response.Text);
        return Parse(doc.RootElement, shift, page.Url);
    }

    public static Result Parse(JsonElement data, int shift, string referer)
    {
        var streams = new List<Stream>(); var seen = new HashSet<string>();
        if (data.TryGetProperty("link", out var link) && link.ValueKind == JsonValueKind.String && link.GetString() is { Length: > 0 } adaptive) Add(streams, seen, "Авто", adaptive, shift);
        if (data.TryGetProperty("links", out var links) && links.ValueKind == JsonValueKind.Object)
            foreach (var quality in links.EnumerateObject().Where(p => Regex.IsMatch(p.Name, "^[1-9][0-9]{1,3}$")).OrderByDescending(p => int.Parse(p.Name)))
            {
                if (quality.Value.ValueKind != JsonValueKind.Array) continue;
                foreach (var variant in quality.Value.EnumerateArray())
                {
                    var before = streams.Count;
                    if (variant.ValueKind == JsonValueKind.Object && variant.TryGetProperty("src", out var src) && src.ValueKind == JsonValueKind.String) Add(streams, seen, quality.Name + "p", src.GetString()!, shift);
                    if (streams.Count > before) break;
                }
            }
        if (streams.Count == 0) throw new InvalidOperationException("Источник не вернул доступный видеопоток. Попробуй другую озвучку или плеер.");
        return new Result(streams, referer);
    }
    static void Add(List<Stream> streams, HashSet<string> seen, string label, string raw, int shift)
    {
        try
        {
            var url = Decode(raw, shift); var uri = SafeUri(url);
            var path = uri.AbsolutePath.ToLowerInvariant();
            bool hls = path.EndsWith(".m3u8"), mp4 = path.EndsWith(".mp4");
            if ((hls || mp4) && seen.Add(url)) streams.Add(new Stream(label, url, hls ? "application/x-mpegURL" : "video/mp4"));
        }
        catch (Exception e) when (e is InvalidOperationException or FormatException or UriFormatException) { /* одно битое качество не отменяет остальные */ }
    }
    public static string Decode(string value, int shift)
    {
        if (string.IsNullOrEmpty(value) || value.Length > 16384) throw Changed();
        var result = value;
        if (!value.StartsWith("//") && !value.StartsWith("https://"))
        {
            var chars = value.Select(c => c is >= 'A' and <= 'Z' ? (char)('A' + (c - 'A' + shift) % 26) : c is >= 'a' and <= 'z' ? (char)('a' + (c - 'a' + shift) % 26) : c).ToArray();
            // Kodik отдаёт Base64 без «=» на конце (Java это допускает, .NET — нет): дополняем до кратности 4.
            var text = new string(chars).TrimEnd('=');
            text = text.PadRight(text.Length + (4 - text.Length % 4) % 4, '=');
            try { result = Encoding.UTF8.GetString(Convert.FromBase64String(text)); } catch (FormatException) { throw Changed(); }
        }
        return result.StartsWith("//") ? "https:" + result : result;
    }
    /// <summary>Только https на публичный домен: без логинов, фрагментов, localhost, .local и IP-адресов.</summary>
    public static Uri SafeUri(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https" || uri.UserInfo.Length > 0 || uri.Fragment.Length > 0
            || uri.HostNameType != UriHostNameType.Dns || uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase) || uri.Host.EndsWith(".local", StringComparison.OrdinalIgnoreCase))
            throw Changed();
        return uri;
    }
    static bool SameOrigin(Uri a, Uri b) => a.Scheme == b.Scheme && string.Equals(a.Host, b.Host, StringComparison.OrdinalIgnoreCase) && a.Port == b.Port;
    static string? Capture(string text, string pattern, int group) { var m = Regex.Match(text, pattern, RegexOptions.Singleline); return m.Success ? m.Groups[group].Value : null; }
    static InvalidOperationException Changed() => new("Формат источника изменился. Попробуй другой плеер или повтори позже.");

    static async Task<(string Url, string Text)> Request(string url, string? body, string? referer, CancellationToken ct)
    {
        for (var redirect = 0; redirect < 5; redirect++)
        {
            SafeUri(url);
            using var request = new HttpRequestMessage(body == null ? HttpMethod.Get : HttpMethod.Post, url);
            request.Headers.UserAgent.ParseAdd(Kodik(new Uri(url)) ? UserAgent : BrowserAgent);
            if (referer != null) request.Headers.Referrer = new Uri(referer);
            if (body != null) request.Content = new StringContent(body, Encoding.UTF8, "application/x-www-form-urlencoded");
            using var response = await Http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct);
            var code = (int)response.StatusCode;
            if (code is >= 300 and < 400)
            {
                if (response.Headers.Location == null || body != null) throw Changed();
                url = SafeUri(new Uri(new Uri(url), response.Headers.Location).ToString()).ToString(); continue;
            }
            if (code is 403 or 451) throw new InvalidOperationException("Источник ограничил доступ к видео. Выбери другой источник.");
            if (code != 200) throw new InvalidOperationException($"Источник видео недоступен ({code}). Попробуй ещё раз.");
            if (response.Content.Headers.ContentLength > MaxBody) throw Changed();
            var text = await response.Content.ReadAsStringAsync(ct);
            if (text.Length > MaxBody) throw Changed();
            return (url, text);
        }
        throw Changed();
    }

    // ───── Прокси для плеера: запросы видео идут через приложение с нужными Referer и User-Agent ─────
    static readonly HttpClient Media = new(new HttpClientHandler { AllowAutoRedirect = true, MaxAutomaticRedirections = 3 }) { Timeout = Timeout.InfiniteTimeSpan };
    /// <summary>Запрашивает видео или плейлист у CDN. Возвращает ответ, который вызывающий освобождает после чтения.</summary>
    public static async Task<HttpResponseMessage> Fetch(string url, string referer, string? range, CancellationToken ct, string agent = UserAgent)
    {
        var uri = SafeUri(url);
        var request = new HttpRequestMessage(HttpMethod.Get, uri);
        request.Headers.UserAgent.ParseAdd(agent);
        request.Headers.Referrer = new Uri(referer);
        if (!string.IsNullOrEmpty(range)) request.Headers.TryAddWithoutValidation("Range", range);
        return await Media.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct);
    }
}
