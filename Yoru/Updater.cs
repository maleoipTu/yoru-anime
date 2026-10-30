using System.Diagnostics;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
namespace Yoru;

/// <summary>
/// Автообновление: CI публикует latest.json в pre-release nightly. Если там версия новее — скачиваем
/// установщик, проверяем SHA-256 и запускаем его тихо; установщик сам перезапускает Yoru.
/// </summary>
public static class Updater
{
    public const string Feed = "https://github.com/maleoipTu/yoru-anime/releases/download/nightly/latest.json";
    public sealed record Release(string Version, string Url, string Sha256);
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(10) };
    static string Root => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Yoru");

    public static Version Current => typeof(Updater).Assembly.GetName().Version ?? new Version(0, 0);
    /// <summary>Запущена ли установленная версия (а не переносная из архива).</summary>
    public static bool Installed => AppContext.BaseDirectory.StartsWith(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Yoru"), StringComparison.OrdinalIgnoreCase);

    public static bool Newer(string remote, Version local) => Version.TryParse(remote, out var v) && v > local;

    public static async Task<Release?> Check()
    {
        try
        {
            using var doc = JsonDocument.Parse(await Http.GetStringAsync(Feed));
            var windows = ApiParser.Field(doc.RootElement, "windows");
            var version = ApiParser.Text(ApiParser.Field(windows, "version"));
            var url = ApiParser.Text(ApiParser.Field(windows, "url"));
            var sha = ApiParser.Text(ApiParser.Field(windows, "sha256"));
            if (!Newer(version, Current) || !url.StartsWith("https://github.com/maleoipTu/yoru-anime/") || sha.Length != 64) return null;
            var skipped = Path.Combine(Root, "update.skip");
            if (File.Exists(skipped) && File.ReadAllText(skipped).Trim() == version) return null;
            return new Release(version, url, sha);
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or JsonException or IOException) { return null; }
    }

    public static void Skip(string version)
    {
        try { Directory.CreateDirectory(Root); File.WriteAllText(Path.Combine(Root, "update.skip"), version); } catch (IOException) { }
    }

    /// <summary>Скачивает установщик с прогрессом (0…1), проверяет контрольную сумму и запускает тихую установку.</summary>
    public static async Task Install(Release release, Action<double> progress)
    {
        var report = new Progress<double>(progress);
        var path = Path.Combine(Path.GetTempPath(), $"Yoru-Setup-{release.Version}.exe");
        using (var response = await Http.GetAsync(release.Url, HttpCompletionOption.ResponseHeadersRead))
        {
            response.EnsureSuccessStatusCode();
            var total = response.Content.Headers.ContentLength ?? 0;
            await using var input = await response.Content.ReadAsStreamAsync();
            await using var output = File.Create(path);
            var buffer = new byte[81920]; long done = 0; int read;
            while ((read = await input.ReadAsync(buffer)) > 0)
            {
                await output.WriteAsync(buffer.AsMemory(0, read)); done += read;
                if (total > 0) ((IProgress<double>)report).Report((double)done / total);
            }
        }
        string hash;
        await using (var file = File.OpenRead(path)) hash = Convert.ToHexString(await SHA256.HashDataAsync(file));
        if (!hash.Equals(release.Sha256, StringComparison.OrdinalIgnoreCase)) { File.Delete(path); throw new InvalidOperationException("Файл обновления повреждён. Попробуй ещё раз позже."); }
        Process.Start(new ProcessStartInfo(path, "/SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS") { UseShellExecute = true });
    }
}
