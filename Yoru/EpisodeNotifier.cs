using System.Text.Json;
using System.Windows.Threading;
namespace Yoru;

/// <summary>
/// Уведомления Windows о новых сериях: пока Yoru открыт, раз в 3 часа проверяет аниме из «Смотрю» и избранного.
/// Первая проверка только запоминает число серий; уведомление приходит, когда их стало больше.
/// </summary>
public static class EpisodeNotifier
{
    static readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromHours(3) };
    static System.Windows.Forms.NotifyIcon? tray;
    static LocalStore? store;
    static Anime? lastNotified;
    static bool running, wired;

    public static void Start(LocalStore local)
    {
        store = local;
        if (!local.Data.NotifyEpisodes) { Stop(); return; }
        if (!wired) { timer.Tick += async (_, _) => await Check(); wired = true; }
        if (timer.IsEnabled) return;
        timer.Start();
        var first = new DispatcherTimer { Interval = TimeSpan.FromSeconds(25) };
        first.Tick += async (_, _) => { first.Stop(); await Check(); };
        first.Start();
    }
    public static void Stop() { timer.Stop(); if (tray != null) { tray.Visible = false; tray.Dispose(); tray = null; } }

    static async Task Check()
    {
        if (running || store is not { Data.NotifyEpisodes: true } || Shell.Main == null) return;
        running = true;
        try
        {
            var follow = new Dictionary<long, Anime>();
            var account = AccountClient.Shared;
            if (account.SignedIn) try { foreach (var a in await account.List("watching")) follow.TryAdd(a.Id, a); } catch (Exception e) when (Extras.Failure(e)) { }
            foreach (var a in store.Data.Favorites) follow.TryAdd(a.Id, a);
            var changed = false;
            foreach (var item in follow.Values.Take(40))
            {
                JsonElement root;
                try { root = await Shell.Api.Get($"anime/{item.Id}"); } catch (Exception e) when (e is System.Net.Http.HttpRequestException or TaskCanceledException or InvalidOperationException or JsonException) { continue; }
                var data = ApiParser.Field(root, "response"); var episodes = ApiParser.Field(data, "episodes");
                var aired = (int)ApiParser.Number(ApiParser.Field(episodes, "aired")); if (aired <= 0) aired = (int)ApiParser.Number(ApiParser.Field(episodes, "count"));
                if (aired <= 0) continue;
                var key = item.Id.ToString();
                var known = store.Data.Aired.TryGetValue(key, out var k) ? k : -1;
                if (known != aired) { store.Data.Aired[key] = aired; changed = true; }
                if (known > 0 && aired > known) Show(ApiParser.Anime(data), aired);
                await Task.Delay(400);
            }
            if (changed) try { store.Save(); } catch (IOException) { }
        }
        finally { running = false; }
    }

    static void Show(Anime anime, int episode)
    {
        if (tray == null)
        {
            tray = new System.Windows.Forms.NotifyIcon { Text = "Yoru", Visible = true };
            try { tray.Icon = System.Drawing.Icon.ExtractAssociatedIcon(Environment.ProcessPath ?? "") ?? System.Drawing.SystemIcons.Information; } catch (Exception e) when (e is ArgumentException or IOException) { tray.Icon = System.Drawing.SystemIcons.Information; }
            tray.BalloonTipClicked += (_, _) => Open();
            tray.DoubleClick += (_, _) => Open();
        }
        lastNotified = anime;
        tray.ShowBalloonTip(10000, $"Вышла {episode}-я серия", anime.Title, System.Windows.Forms.ToolTipIcon.None);
    }
    static void Open()
    {
        if (Shell.Main == null || lastNotified == null) return;
        Shell.Main.Show(); if (Shell.Main.WindowState == System.Windows.WindowState.Minimized) Shell.Main.WindowState = System.Windows.WindowState.Normal; Shell.Main.Activate();
        Extras.Open(lastNotified);
    }
}
