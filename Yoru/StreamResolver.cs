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
    const int MaxBody = 2 * 1024 * 1024;
    public sealed record Stream(string Label, string Url, string Mime);
    public sealed record Result(IReadOnlyList<Stream> Streams, string Referer);
    public delegate Task<(string Url, string Text)> Transport(string url, string? body, string? referer, CancellationToken ct);

    public static bool Supports(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == "https" && uri.Host.ToLowerInvariant() is "kodikplayer.com" or "kodik.info" or "kodik.cc";

    static readonly HttpClient Http = new(new HttpClientHandler { AllowAutoRedirect = false, AutomaticDecompression = DecompressionMethods.All }) { Timeout = TimeSpan.FromSeconds(20) };

    public static Task<Result> Resolve(string url, CancellationToken ct = default) => Resolve(url, Request, ct);
    public static async Task<Result> Resolve(string url, Transport network, CancellationToken ct = default)
    {
        if (!Supports(url)) throw new InvalidOperationException("Для этого источника прямой поток пока не поддерживается. Выбери Kodik или открой плеер сайта.");
        var page = await network(url, null, null, ct);
        if (!Supports(page.Url)) throw new InvalidOperationException("Источник перенаправил запрос на неизвестный плеер.");
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
            request.Headers.UserAgent.ParseAdd(UserAgent);
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
    public static async Task<HttpResponseMessage> Fetch(string url, string referer, string? range, CancellationToken ct)
    {
        var uri = SafeUri(url);
        var request = new HttpRequestMessage(HttpMethod.Get, uri);
        request.Headers.UserAgent.ParseAdd(UserAgent);
        request.Headers.Referrer = new Uri(referer);
        if (!string.IsNullOrEmpty(range)) request.Headers.TryAddWithoutValidation("Range", range);
        return await Media.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct);
    }
}
