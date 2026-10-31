using System.Globalization;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
namespace Yoru;

/// <summary>Общие элементы новых экранов: карточки, постеры, статистика профиля.</summary>
public static class Extras
{
    public static readonly Brush Muted = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8));
    public static readonly Brush Accent = new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x9B));
    public static readonly Brush Warm = new SolidColorBrush(Color.FromRgb(0xFF, 0xC7, 0x68));
    public static readonly Brush Raised = new SolidColorBrush(Color.FromRgb(0x21, 0x22, 0x2D));
    public static readonly Brush Soft = new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20));
    public static Border Card(UIElement child) => new() { Background = (Brush)Application.Current.Resources["SectionGradient"], BorderBrush = new SolidColorBrush(Color.FromRgb(0x26, 0x26, 0x30)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(20), Padding = new Thickness(28), Margin = new Thickness(0, 0, 0, 24), Child = child };
    public static TextBlock Text(string text, double size = 14, Brush? color = null, bool bold = false) => new() { Text = text, FontSize = size, Foreground = color ?? Brushes.WhiteSmoke, FontWeight = bold ? FontWeights.SemiBold : FontWeights.Normal, TextWrapping = TextWrapping.Wrap };
    public static string Plural(int n, string one, string few, string many) { int m = n % 100, k = n % 10; return m is >= 11 and <= 14 ? many : k == 1 ? one : k is >= 2 and <= 4 ? few : many; }
    public static bool Failure(Exception e) => e is AccountException or HttpRequestException or TaskCanceledException or JsonException or InvalidOperationException or IOException;

    /// <summary>Постер с подписью; нажатие открывает карточку аниме.</summary>
    public static Button PosterTile(long id, string title, string poster, string reason)
    {
        var stack = new StackPanel { Width = 140 };
        var art = new Border { Height = 198, CornerRadius = new CornerRadius(14), Background = Raised, ClipToBounds = true, Clip = new RectangleGeometry(new Rect(0, 0, 140, 198), 14, 14) };
        if (Uri.TryCreate(poster.StartsWith("//") ? "https:" + poster : poster, UriKind.Absolute, out var uri) && uri.Scheme == "https")
            try { art.Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill }; } catch (Exception e) when (e is NotSupportedException or IOException) { }
        stack.Children.Add(art);
        stack.Children.Add(new TextBlock { Text = title, FontSize = 13, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 12, 0, 0), MaxHeight = 36, TextWrapping = TextWrapping.Wrap, TextTrimming = TextTrimming.CharacterEllipsis });
        if (reason.Length > 0) stack.Children.Add(new TextBlock { Text = reason, FontSize = 12, Foreground = Accent, Margin = new Thickness(0, 6, 0, 0), TextWrapping = TextWrapping.Wrap });
        var button = new Button { Style = (Style)Application.Current.Resources["CatalogCard"], Content = stack, Background = Brushes.Transparent, BorderThickness = new Thickness(0), Padding = new Thickness(0), Margin = new Thickness(0, 8, 20, 28), Cursor = Cursors.Hand };
        System.Windows.Automation.AutomationProperties.SetName(button, title + (reason.Length > 0 ? ". " + reason : ""));
        button.Click += (_, _) => Open(new Anime { Id = id, Title = title, Poster = poster.StartsWith("//") ? "https:" + poster : poster });
        return button;
    }
    public static void Open(Anime anime) => Shell.Navigate(new DetailPage(anime, Shell.Api, Shell.Store));

    /// <summary>Статистика профиля: часы, серии, аниме, средняя оценка, любимые жанры и дни недели.</summary>
    public static Border StatsCard(AccountClient account, string username)
    {
        var panel = new StackPanel();
        panel.Children.Add(Text("Статистика", 20, null, true));
        var wait = Text("Считаем…", 13, Muted); wait.Margin = new Thickness(0, 12, 0, 0); panel.Children.Add(wait);
        var card = Card(panel);
        card.Loaded += async (_, _) =>
        {
            if (panel.Children.Count > 2) return;
            JsonObject? s = null;
            try { s = await account.PublicRpc("watch_stats", new JsonObject { ["p_username"] = username }) as JsonObject; } catch (Exception e) when (Failure(e)) { }
            panel.Children.Remove(wait);
            if (s == null) { panel.Children.Add(Text("Статистика пока недоступна.", 13, Muted)); return; }
            var numbers = new System.Windows.Controls.Primitives.UniformGrid { Columns = 4, Margin = new Thickness(0, 18, 0, 0) };
            var avg = s["avg_score"] is JsonValue v && v.TryGetValue<double>(out var a) ? a.ToString("0.0", CultureInfo.InvariantCulture) : "—";
            foreach (var (value, caption, color) in new (string, string, Brush)[] { (AccountClient.Text(s, "hours") is { Length: > 0 } h ? h : s["hours"]?.ToString() ?? "0", "часов", Accent), (AccountClient.Int(s, "episodes").ToString(), "серий", Brushes.WhiteSmoke), (AccountClient.Int(s, "anime").ToString(), "аниме", Brushes.WhiteSmoke), (avg, "средняя оценка", Warm) })
            {
                var cell = new StackPanel();
                cell.Children.Add(new TextBlock { Text = value, FontSize = 26, FontWeight = FontWeights.Bold, Foreground = color });
                cell.Children.Add(new TextBlock { Text = caption, FontSize = 12, Foreground = Muted, Margin = new Thickness(0, 4, 0, 0) });
                numbers.Children.Add(new Border { Background = Raised, BorderBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(14), Padding = new Thickness(18, 14, 12, 14), Margin = new Thickness(0, 0, 12, 0), Child = cell });
            }
            panel.Children.Add(numbers);
            if (s["genres"] is JsonArray genres && genres.Count > 0)
            {
                var head = Text("Любимые жанры", 15, null, true); head.Margin = new Thickness(0, 28, 0, 12); panel.Children.Add(head);
                var max = Math.Max(1, genres.Max(g => AccountClient.Int(g, "count")));
                var palette = new[] { "#FF4D67", "#FF8A3D", "#FFC14D", "#4ADE80", "#6FC3FF", "#B388FF", "#FF8FB8", "#7EDCA9", "#9FD8FF", "#FFE08A" };
                var i = 0;
                foreach (var g in genres.Take(7))
                {
                    var row = new Grid { Margin = new Thickness(0, 5, 0, 5) };
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(150) }); row.ColumnDefinitions.Add(new ColumnDefinition()); row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
                    row.Children.Add(new TextBlock { Text = AccountClient.Text(g, "genre"), FontSize = 13, TextTrimming = TextTrimming.CharacterEllipsis });
                    var track = new Grid { Height = 12, VerticalAlignment = VerticalAlignment.Center }; Grid.SetColumn(track, 1);
                    track.Children.Add(new Border { Background = Raised, CornerRadius = new CornerRadius(6) });
                    var bar = new Border { Background = new SolidColorBrush(Decor.Parse(palette[i++ % palette.Length], Colors.White)), CornerRadius = new CornerRadius(6), HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
                    var share = AccountClient.Int(g, "count") / (double)max;
                    track.SizeChanged += (_, e) => bar.BeginAnimation(FrameworkElement.WidthProperty, new DoubleAnimation(Math.Max(6, e.NewSize.Width * share), TimeSpan.FromMilliseconds(700)) { EasingFunction = new CubicEase() });
                    track.Children.Add(bar); row.Children.Add(track);
                    var count = new TextBlock { Text = AccountClient.Int(g, "count").ToString(), Foreground = Muted, FontSize = 12, HorizontalAlignment = HorizontalAlignment.Right }; Grid.SetColumn(count, 2); row.Children.Add(count);
                    panel.Children.Add(row);
                }
            }
            if (s["weekdays"] is JsonArray week && week.Count == 7 && week.Sum(d => d?.GetValue<int>() ?? 0) > 0)
            {
                var head = Text("Когда смотрит", 15, null, true); head.Margin = new Thickness(0, 28, 0, 12); panel.Children.Add(head);
                var max = Math.Max(1, week.Max(d => d?.GetValue<int>() ?? 0));
                var bars = new System.Windows.Controls.Primitives.UniformGrid { Columns = 7, Height = 96 };
                var days = new[] { "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс" };
                for (var d = 0; d < 7; d++)
                {
                    var col = new DockPanel();
                    var label = new TextBlock { Text = days[d], FontSize = 11, Foreground = Muted, HorizontalAlignment = HorizontalAlignment.Center }; DockPanel.SetDock(label, Dock.Bottom); col.Children.Add(label);
                    col.Children.Add(new Border { Width = 20, Height = Math.Max(4, 70 * (week[d]?.GetValue<int>() ?? 0) / (double)max), VerticalAlignment = VerticalAlignment.Bottom, CornerRadius = new CornerRadius(5), Background = (Brush)Application.Current.Resources["AccentGradient"], Margin = new Thickness(0, 0, 0, 4) });
                    bars.Children.Add(col);
                }
                panel.Children.Add(bars);
            }
            if (AccountClient.Int(s, "episodes") == 0) { var empty = Text("Статистика появится после первых серий в плеере Yoru.", 13, Muted); empty.Margin = new Thickness(0, 12, 0, 0); panel.Children.Add(empty); }
        };
        return card;
    }

    /// <summary>Обсуждение серии: комментарии с таймкодами, спойлеры и реакции.</summary>
    public static FrameworkElement Comments(AccountClient account, Anime anime, Func<string> episode, Func<double> now, Action<double> seek)
    {
        var root = new StackPanel();
        var input = new TextBox { AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 72, MaxLength = 500 };
        System.Windows.Automation.AutomationProperties.SetName(input, "Комментарий к серии");
        var stamp = new CheckBox { Content = "С таймкодом", IsChecked = true, Foreground = Muted, Margin = new Thickness(0, 12, 0, 0) };
        var spoiler = new CheckBox { Content = "Спойлер", Foreground = Muted, Margin = new Thickness(0, 8, 0, 0) };
        var send = new Button { Content = "Отправить", Style = (Style)Application.Current.Resources["Primary"], Margin = new Thickness(0, 14, 0, 0), Padding = new Thickness(12, 12, 12, 12) };
        var status = Text("", 12, new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)));
        var list = new StackPanel { Margin = new Thickness(0, 20, 0, 0) };
        root.Children.Add(input); root.Children.Add(stamp); root.Children.Add(spoiler); root.Children.Add(send); root.Children.Add(status); root.Children.Add(list);
        string shown = "";
        async Task Reload()
        {
            shown = episode(); list.Children.Clear(); list.Children.Add(Text("Загружаем…", 12, Muted));
            JsonArray rows;
            try { rows = (await account.PublicRpc("episode_comments", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = shown, ["p_limit"] = 150 }))?.AsArray() ?? new JsonArray(); }
            catch (Exception e) when (Failure(e)) { list.Children.Clear(); list.Children.Add(Text(Rewards.Error(e.Message), 12, Muted)); return; }
            list.Children.Clear();
            list.Children.Add(Text($"Серия {shown} · {rows.Count} {Plural(rows.Count, "комментарий", "комментария", "комментариев")}", 12, Muted));
            if (rows.Count == 0) list.Children.Add(Text("Здесь пока тихо. Отметь любимый момент — комментарий запомнит время.", 12, Muted));
            foreach (var c in rows.OfType<JsonObject>())
            {
                var box = new StackPanel();
                var at = c["at_second"] is JsonValue av && av.TryGetValue<long>(out var sec) ? sec : -1;
                box.Children.Add(Decor.Person(c, account, at >= 0 ? "▶ " + TimeSpan.FromSeconds(at).ToString(at >= 3600 ? @"h\:mm\:ss" : @"mm\:ss") : "", 28));
                var spoil = AccountClient.Bool(c, "spoiler");
                var body = new TextBlock { Text = spoil ? "⚠ Спойлер — нажми, чтобы показать" : AccountClient.Text(c, "body"), TextWrapping = TextWrapping.Wrap, FontSize = 13, Foreground = spoil ? Muted : Brushes.WhiteSmoke, Margin = new Thickness(0, 2, 0, 6) };
                if (spoil) body.MouseLeftButtonUp += (_, _) => { body.Text = AccountClient.Text(c, "body"); body.Foreground = Brushes.WhiteSmoke; };
                box.Children.Add(body);
                var reacts = new WrapPanel();
                var mine = (c["my_reactions"] as JsonArray ?? new JsonArray()).Select(x => x?.ToString() ?? "").ToHashSet();
                foreach (var emoji in new[] { "❤", "😂", "😮", "😭", "🔥", "👍" })
                {
                    var n = AccountClient.Int(c["reactions"], emoji);
                    if (n == 0 && !account.SignedIn) continue;
                    var b = new Button { Content = emoji + (n > 0 ? " " + n : ""), Padding = new Thickness(7, 3, 7, 3), Margin = new Thickness(0, 0, 5, 5), MinWidth = 0, FontSize = 12, Background = mine.Contains(emoji) ? Soft : Raised };
                    var id = AccountClient.Long(c, "id");
                    b.Click += async (_, _) => { try { await account.Rpc("react_comment", new JsonObject { ["p_id"] = id, ["p_emoji"] = emoji }); await Reload(); } catch (Exception e) when (Failure(e)) { status.Text = Rewards.Error(e.Message); } };
                    reacts.Children.Add(b);
                }
                if (at >= 0) { var jump = new Button { Content = "▶ К моменту", Padding = new Thickness(7, 3, 7, 3), Margin = new Thickness(0, 0, 5, 5), FontSize = 12, Foreground = Accent, Background = Brushes.Transparent }; jump.Click += (_, _) => seek(at); reacts.Children.Add(jump); }
                if (AccountClient.Bool(c, "mine")) { var del = new Button { Content = "Удалить", Padding = new Thickness(7, 3, 7, 3), FontSize = 12, Foreground = Muted, Background = Brushes.Transparent }; var id = AccountClient.Long(c, "id"); del.Click += async (_, _) => { try { await account.Rpc("delete_comment", new JsonObject { ["p_id"] = id }); await Reload(); } catch (Exception e) when (Failure(e)) { status.Text = Rewards.Error(e.Message); } }; reacts.Children.Add(del); }
                box.Children.Add(reacts);
                list.Children.Add(new Border { Background = Raised, CornerRadius = new CornerRadius(14), Padding = new Thickness(14, 10, 14, 8), Margin = new Thickness(0, 10, 0, 0), Child = box });
            }
        }
        send.Click += async (_, _) =>
        {
            status.Text = "";
            if (!account.SignedIn) { status.Text = "Войди в аккаунт, чтобы писать комментарии."; return; }
            var text = input.Text.Trim(); if (text.Length == 0) return;
            send.IsEnabled = false;
            try
            {
                await account.Rpc("post_comment", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = episode(), ["p_body"] = text, ["p_at_second"] = stamp.IsChecked == true ? (JsonNode)(long)now() : null, ["p_spoiler"] = spoiler.IsChecked == true });
                input.Text = ""; await Reload();
            }
            catch (Exception e) when (Failure(e)) { status.Text = Rewards.Error(e.Message); }
            finally { send.IsEnabled = true; }
        };
        root.IsVisibleChanged += async (_, e) => { if (e.NewValue is true && shown != episode()) await Reload(); };
        root.Tag = (Func<Task>)Reload;
        return root;
    }
}

/// <summary>Основа новых экранов: «Назад», заголовок, строка ошибки и прокручиваемое содержимое.</summary>
public abstract class ExtrasPage : UserControl
{
    protected readonly AccountClient account;
    protected readonly StackPanel Body = new();
    protected readonly TextBlock Status = new() { Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)), FontSize = 13, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 0) };
    protected readonly ScrollViewer Scroll;
    protected bool Closed;
    protected ExtrasPage(AccountClient client, string title, string subtitle)
    {
        account = client;
        var root = new DockPanel { Margin = new Thickness(40, 32, 40, 20) };
        var header = new StackPanel();
        var back = new Button { Content = "←  Назад", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 24) }; back.Click += (_, _) => Shell.Back(); header.Children.Add(back);
        header.Children.Add(new TextBlock { Text = title, Style = (Style)Application.Current.Resources["PageTitle"] });
        header.Children.Add(new Border { Style = (Style)Application.Current.Resources["Dawn"] });
        if (subtitle.Length > 0) { var sub = Extras.Text(subtitle, 14, Extras.Muted); sub.Margin = new Thickness(0, 12, 0, 0); sub.MaxWidth = 760; sub.HorizontalAlignment = HorizontalAlignment.Left; header.Children.Add(sub); }
        header.Children.Add(Status);
        DockPanel.SetDock(header, Dock.Top); root.Children.Add(header);
        Body.Margin = new Thickness(0, 0, 12, 0);
        Scroll = new ScrollViewer { Content = Body, Margin = new Thickness(0, 28, 0, 0), VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        root.Children.Add(Scroll);
        Content = root;
        Unloaded += (_, _) => Closed = true;
        Loaded += async (_, _) => { if (Body.Children.Count == 0) await Load(); };
    }
    protected abstract Task Load();
    protected async Task<bool> Run(Func<Task> job)
    {
        Status.Text = "";
        try { await job(); return true; }
        catch (Exception e) when (Extras.Failure(e)) { if (!Closed) Status.Text = e is IOException or InvalidOperationException && Regex.IsMatch(e.Message, "[А-Яа-я]") ? e.Message : Rewards.Error(e.Message); return false; }
    }
    protected bool NeedAccount() { if (account.SignedIn) return false; Status.Text = "Войди в аккаунт Yoru."; return true; }
}

/// <summary>Рейтинг недели: друзья или все пользователи, очки и серии за 7 дней.</summary>
public sealed class LeaderboardPage(AccountClient client) : ExtrasPage(client, "Рейтинг недели", "Очки и серии за последние 7 дней")
{
    string scope = "friends";
    protected override async Task Load()
    {
        if (NeedAccount()) return;
        Body.Children.Clear();
        var tabs = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 20) };
        foreach (var (key, title) in new[] { ("friends", "Друзья"), ("global", "Все") })
        {
            var b = new Button { Content = title, Padding = new Thickness(20, 10, 20, 10), Margin = new Thickness(0, 0, 10, 0), Background = scope == key ? Extras.Soft : Brushes.Transparent, Foreground = scope == key ? Extras.Accent : Brushes.WhiteSmoke };
            b.Click += async (_, _) => { scope = key; await Load(); };
            tabs.Children.Add(b);
        }
        Body.Children.Add(tabs);
        JsonArray rows = new();
        if (!await Run(async () => { await account.EnsureCatalog(); rows = (await account.Rpc("leaderboard", new JsonObject { ["p_scope"] = scope, ["p_days"] = 7 }))?.AsArray() ?? new JsonArray(); })) return;
        if (rows.Count == 0) { Body.Children.Add(Extras.Text(scope == "friends" ? "На этой неделе друзья ещё ничего не смотрели. Позови их на совместный просмотр!" : "Пока пусто.", 14, Extras.Muted)); return; }
        foreach (var r in rows.OfType<JsonObject>())
        {
            var place = AccountClient.Int(r, "place");
            var row = new DockPanel();
            var medal = new TextBlock { Text = place switch { 1 => "🥇", 2 => "🥈", 3 => "🥉", _ => "#" + place }, FontSize = place <= 3 ? 26 : 16, Width = 52, VerticalAlignment = VerticalAlignment.Center, FontWeight = FontWeights.SemiBold };
            DockPanel.SetDock(medal, Dock.Left); row.Children.Add(medal);
            row.Children.Add(Decor.Person(r, account, $"◆ {Rewards.Points(AccountClient.Int(r, "points"))} · {AccountClient.Int(r, "episodes")} {Extras.Plural(AccountClient.Int(r, "episodes"), "серия", "серии", "серий")} · {r["hours"]} ч"));
            var button = new Button { Content = row, HorizontalContentAlignment = HorizontalAlignment.Stretch, Padding = new Thickness(16, 10, 16, 10), Margin = new Thickness(0, 0, 0, 10), Background = AccountClient.Bool(r, "me") ? Extras.Soft : Extras.Raised };
            var username = AccountClient.Text(r, "username");
            button.Click += (_, _) => Shell.Navigate(new ProfilePage(account, Shell.Store, username));
            Body.Children.Add(button);
        }
    }
}

/// <summary>Входящие подарки.</summary>
public sealed class GiftsPage(AccountClient client) : ExtrasPage(client, "Подарки", "Очки и украшения от друзей")
{
    protected override async Task Load()
    {
        if (NeedAccount()) return;
        JsonArray gifts = new();
        if (!await Run(async () => { await account.EnsureCatalog(); gifts = (await account.Rpc("my_gifts", new JsonObject()))?.AsArray() ?? new JsonArray(); await account.Rpc("mark_gifts_seen", new JsonObject()); })) return;
        Body.Children.Clear();
        if (gifts.Count == 0) { Body.Children.Add(Extras.Text("Пока подарков нет. Друзья могут подарить очки или украшение со страницы твоего профиля.", 14, Extras.Muted)); return; }
        foreach (var g in gifts.OfType<JsonObject>())
        {
            var fresh = !AccountClient.Bool(g, "seen");
            var stack = new StackPanel();
            stack.Children.Add(Decor.Person(g, account, (fresh ? "Новый · " : "") + Rewards.Until(AccountClient.Text(g, "created_at"))));
            var points = AccountClient.Text(g, "kind") == "points";
            stack.Children.Add(Extras.Text(points ? $"◆ {Rewards.Points(AccountClient.Int(g, "amount"))} очков" : $"🎁 «{AccountClient.Text(g, "decoration_title")}»", 18, points ? Extras.Warm : Extras.Accent, true));
            if (AccountClient.Text(g, "message") is { Length: > 0 } note) { var t = Extras.Text("«" + note + "»", 14); t.Margin = new Thickness(0, 6, 0, 0); stack.Children.Add(t); }
            var card = Extras.Card(stack); if (fresh) card.Background = Extras.Soft;
            Body.Children.Add(card);
        }
        await account.Refresh();
    }
}

/// <summary>Подарок другу: очки или украшение за свои очки.</summary>
public sealed class GiftPage(AccountClient client, JsonObject friend) : ExtrasPage(client, "Подарок для " + (AccountClient.Text(friend, "display_name") is { Length: > 0 } n ? n : AccountClient.Text(friend, "username")), "Переданные очки не повышают уровень друга, но на них можно купить украшения")
{
    protected override async Task Load()
    {
        if (NeedAccount()) return;
        Body.Children.Clear();
        await Run(account.EnsureCatalog);
        var balance = AccountClient.Int(account.Profile, "points");
        var note = new TextBox { MaxLength = 140, Margin = new Thickness(0, 8, 0, 24), MaxWidth = 520, HorizontalAlignment = HorizontalAlignment.Left, MinWidth = 380 };
        System.Windows.Automation.AutomationProperties.SetName(note, "Подпись к подарку");
        var top = new StackPanel();
        top.Children.Add(Extras.Text($"Твой баланс: ◆ {Rewards.Points(balance)}", 14, Extras.Warm, true));
        top.Children.Add(Extras.Text("Подпись (необязательно)", 12, Extras.Muted)); top.Children.Add(note);
        top.Children.Add(Extras.Text("Очки", 16, null, true));
        var amounts = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
        foreach (var value in new[] { 50, 100, 250, 500 })
        {
            var b = new Button { Content = "◆ " + value, Padding = new Thickness(22, 12, 22, 12), Margin = new Thickness(0, 0, 12, 12), IsEnabled = balance >= value };
            b.Click += async (_, _) => { if (await Run(() => account.Rpc("gift_points", new JsonObject { ["p_user"] = AccountClient.Text(friend, "id"), ["p_amount"] = value, ["p_message"] = note.Text }))) { await account.Refresh(); Done($"🎁 Отправлено ◆ {value}"); } };
            amounts.Children.Add(b);
        }
        top.Children.Add(amounts);
        Body.Children.Add(Extras.Card(top));
        var shop = new StackPanel(); shop.Children.Add(Extras.Text("Украшение", 16, null, true));
        var grid = new WrapPanel { Margin = new Thickness(0, 14, 0, 0) };
        var level = Rewards.Level(AccountClient.Int(account.Profile, "total_earned"));
        foreach (var d in account.Decorations.OfType<JsonObject>().Where(d => !AccountClient.Bool(d, "exclusive") && AccountClient.Int(d, "price") > 0 && Rewards.OnSale(AccountClient.Text(d, "available_from"), AccountClient.Text(d, "available_until"), DateTimeOffset.Now)))
        {
            var price = AccountClient.Int(d, "price"); var id = AccountClient.Text(d, "id"); var title = AccountClient.Text(d, "title");
            var stack = new StackPanel { Width = 180 };
            stack.Children.Add(new TextBlock { Text = title, FontWeight = FontWeights.SemiBold, TextWrapping = TextWrapping.Wrap });
            stack.Children.Add(new TextBlock { Text = AdminPage.KindLabel(AccountClient.Text(d, "kind")) + " · " + Rewards.Rarity(AccountClient.Text(d, "rarity")), FontSize = 11, Foreground = Extras.Muted, Margin = new Thickness(0, 3, 0, 0) });
            stack.Children.Add(new TextBlock { Text = "◆ " + Rewards.Points(price), Foreground = Extras.Warm, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 6, 0, 0) });
            var b = new Button { Content = stack, Padding = new Thickness(16, 14, 16, 14), Margin = new Thickness(0, 0, 12, 12), IsEnabled = balance >= price && level >= Math.Max(1, AccountClient.Int(d, "min_level")), HorizontalContentAlignment = HorizontalAlignment.Left };
            b.Click += async (_, _) => { if (await Run(() => account.Rpc("gift_decoration", new JsonObject { ["p_user"] = AccountClient.Text(friend, "id"), ["p_decoration"] = id, ["p_message"] = note.Text }))) { await account.Refresh(); Done($"🎁 «{title}» отправлено"); } };
            grid.Children.Add(b);
        }
        if (grid.Children.Count == 0) shop.Children.Add(Extras.Text("Сейчас нечего подарить.", 13, Extras.Muted));
        shop.Children.Add(grid);
        Body.Children.Add(Extras.Card(shop));
    }
    void Done(string text) { Body.Children.Clear(); Body.Children.Add(Extras.Card(Extras.Text(text, 20, Extras.Warm, true))); }
}

/// <summary>Перенос списков и оценок со старого сайта YummyAnime (old.yummyani.me/users/id…).</summary>
public sealed class ImportPage(AccountClient client) : ExtrasPage(client, "Перенос с YummyAnime", "Списки «Смотрю», «В планах», «Просмотрено», «Отложено», «Брошено», «Любимые» и оценки")
{
    static readonly string[] Statuses = ["watching", "planned", "completed", "on_hold", "dropped"];
    public static long UserId(string text) { var m = Regex.Match(text, @"id(\d{1,10})"); if (m.Success) return long.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture); m = Regex.Match(text, @"^\s*(\d{1,10})\s*$"); return m.Success ? long.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture) : 0; }
    public static JsonObject Item(JsonNode o, string status, bool favorite) => new()
    {
        ["anime_id"] = AccountClient.Long(o, "anime_id"), ["status"] = status, ["title"] = AccountClient.Text(o, "title"), ["poster"] = AccountClient.Text(o["poster"], "big"),
        ["genres"] = new JsonArray((o["genres"] as JsonArray ?? new JsonArray()).Select(g => (JsonNode?)JsonValue.Create(AccountClient.Text(g, "title"))).ToArray()),
        ["score"] = o["user"]?["rating"] is JsonValue v && v.TryGetValue<double>(out var s) ? s : 0, ["favorite"] = favorite,
    };
    protected override Task Load()
    {
        Body.Children.Clear();
        var panel = new StackPanel { MaxWidth = 640, HorizontalAlignment = HorizontalAlignment.Left };
        panel.Children.Add(Extras.Text("Уже добавленное в Yoru не перезапишется. Очки за перенос не начисляются.", 14, Extras.Muted));
        var link = new TextBox { Margin = new Thickness(0, 8, 0, 10) }; System.Windows.Automation.AutomationProperties.SetName(link, "Ссылка на профиль YummyAnime");
        { var label = Extras.Text("Ссылка на профиль (например, old.yummyani.me/users/id529123)", 12, Extras.Muted); label.Margin = new Thickness(0, 20, 0, 0); panel.Children.Add(label); } panel.Children.Add(link);
        panel.Children.Add(Extras.Text("Открой свой профиль на YummyAnime и скопируй адрес из браузера. Списки должны быть открыты для всех.", 12, Extras.Muted));
        var progress = Extras.Text("", 14); progress.Margin = new Thickness(0, 12, 0, 0);
        var go = new Button { Content = "Перенести", Style = (Style)Application.Current.Resources["Primary"], HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 24, 0, 0) };
        panel.Children.Add(go); panel.Children.Add(progress);
        go.Click += async (_, _) =>
        {
            if (NeedAccount()) return;
            var user = UserId(link.Text);
            if (user <= 0) { Status.Text = "Не вижу id в ссылке — она выглядит как …/users/id123456"; return; }
            go.IsEnabled = false; progress.Foreground = Extras.Muted; progress.Text = "Скачиваем списки…";
            var found = 0; var imported = 0; var rated = 0;
            var ok = await Run(async () =>
            {
                using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
                http.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0) Yoru");
                async Task<JsonArray> List(int i)
                {
                    using var response = await http.GetAsync($"https://old.yummyani.me/api/users/{user}/lists/{i}");
                    if (response.StatusCode == System.Net.HttpStatusCode.NotFound) throw new InvalidOperationException("Профиль на старом сайте не найден.");
                    if (!response.IsSuccessStatusCode) throw new InvalidOperationException($"Старый сайт YummyAnime не ответил ({(int)response.StatusCode}). Попробуй позже.");
                    return JsonNode.Parse(await response.Content.ReadAsStringAsync())?["response"] as JsonArray ?? new JsonArray();
                }
                var items = new Dictionary<long, JsonObject>();
                for (var i = 0; i < Statuses.Length; i++) foreach (var o in (await List(i)).OfType<JsonObject>()) if (AccountClient.Long(o, "anime_id") > 0) items[AccountClient.Long(o, "anime_id")] = Item(o, Statuses[i], false);
                foreach (var o in (await List(5)).OfType<JsonObject>()) { var id = AccountClient.Long(o, "anime_id"); if (items.TryGetValue(id, out var e)) e["favorite"] = true; else if (id > 0) items[id] = Item(o, "completed", true); }
                if (items.Count == 0) throw new InvalidOperationException("Списки пусты или скрыты настройками приватности.");
                found = items.Count;
                foreach (var chunk in items.Values.Chunk(500))
                {
                    var payload = new JsonArray(chunk.Select(e => (JsonNode?)e.DeepClone()).ToArray());
                    var r = await account.Rpc("import_lists", new JsonObject { ["p_items"] = payload });
                    imported += AccountClient.Int(r, "imported"); rated += AccountClient.Int(r, "rated");
                    progress.Text = $"Переносим… {imported} из {found}";
                }
            });
            go.IsEnabled = true;
            if (ok) { progress.Foreground = new SolidColorBrush(Color.FromRgb(0x4A, 0xDE, 0x80)); progress.Text = $"Готово: найдено {found}, добавлено {imported}, оценок {rated}."; }
            else progress.Text = "";
        };
        Body.Children.Add(Extras.Card(panel));
        return Task.CompletedTask;
    }
}

/// <summary>«Для тебя»: аниме, которые смотрят друзья, и подборка по любимым жанрам.</summary>
public sealed class ForYouPage(AccountClient client) : ExtrasPage(client, "Для тебя", "Подбираем по твоим жанрам и тому, что смотрят друзья")
{
    protected override async Task Load()
    {
        Body.Children.Clear();
        var wrap = new WrapPanel(); var seen = new HashSet<long>();
        Body.Children.Add(Extras.Text("Загружаем…", 13, Extras.Muted));
        await Run(async () =>
        {
            if (account.SignedIn)
            {
                foreach (var list in AccountClient.Lists) foreach (var a in await account.List(list)) seen.Add(a.Id);
                foreach (var f in ((await account.Rpc("friend_recommendations", new JsonObject { ["p_limit"] = 12 }))?.AsArray() ?? new JsonArray()).OfType<JsonObject>())
                {
                    var fans = AccountClient.Int(f, "fans"); seen.Add(AccountClient.Long(f, "anime_id"));
                    wrap.Children.Add(Extras.PosterTile(AccountClient.Long(f, "anime_id"), AccountClient.Text(f, "title"), AccountClient.Text(f, "poster"), $"👥 {fans} {Extras.Plural(fans, "друг", "друга", "друзей")}" + (f["score"] is JsonValue sv && sv.TryGetValue<double>(out var sc) ? $" · ★ {sc:0.0}" : "")));
                }
                var stats = await account.PublicRpc("watch_stats", new JsonObject { ["p_username"] = AccountClient.Text(account.Profile, "username") });
                var genres = (stats?["genres"] as JsonArray ?? new JsonArray()).Take(3).Select(g => AccountClient.Text(g, "genre")).ToList();
                if (genres.Count > 0)
                {
                    var all = await Shell.Api.Genres(default);
                    foreach (var name in genres)
                    {
                        var match = all.FirstOrDefault(g => string.Equals(g.Title, name, StringComparison.OrdinalIgnoreCase));
                        if (match.Id <= 0) continue;
                        foreach (var a in await Shell.Api.Catalog("", "rating", "", 0, default, "&genres=" + match.Id))
                            if (seen.Add(a.Id) && wrap.Children.Count < 48) wrap.Children.Add(Extras.PosterTile(a.Id, a.Title, a.Poster, $"✨ Любишь «{name}»"));
                    }
                }
            }
            if (wrap.Children.Count < 8)
                foreach (var a in await Shell.Api.Catalog("", "rating", "", Random.Shared.Next(4) * 24, default))
                    if (seen.Add(a.Id) && wrap.Children.Count < 30) wrap.Children.Add(Extras.PosterTile(a.Id, a.Title, a.Poster, "★ Высокий рейтинг"));
        });
        Body.Children.Clear();
        if (wrap.Children.Count == 0) Body.Children.Add(Extras.Text("Пока не из чего выбрать — посмотри пару серий или добавь друзей.", 14, Extras.Muted));
        Body.Children.Add(wrap);
    }
}

/// <summary>Рулетка: случайное аниме с фильтрами, которого ещё нет в списках и истории.</summary>
public sealed class RoulettePage(AccountClient client) : ExtrasPage(client, "Рулетка", "Шесть случайных аниме на барабане — какое выпадет?")
{
    readonly ComboBox genre = new() { MinWidth = 220 }, status = new() { MinWidth = 180 };
    readonly TextBlock title = new() { FontSize = 26, FontWeight = FontWeights.SemiBold, TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Center, MaxWidth = 560 };
    readonly TextBlock meta = new() { FontSize = 14, Foreground = Extras.Muted, TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Center, MaxWidth = 560, Margin = new Thickness(0, 10, 0, 0), LineHeight = 22 };
    readonly Button spin = new() { Content = "🎲  Крутить", Margin = new Thickness(0, 0, 12, 0), Padding = new Thickness(24, 13, 24, 13) }, open = new() { Content = "Смотреть  →", IsEnabled = false, Padding = new Thickness(24, 13, 24, 13) };
    readonly Canvas reel = new() { Height = 300, ClipToBounds = true, Background = Brushes.Transparent };
    readonly List<Border> cards = [];
    List<(int Id, string Title)> genres = [];
    List<Anime> six = [];
    Anime? chosen;
    double offset; bool spinning;
    const double Gap = 0.62, Radius = 330, CardW = 150, CardH = 214;
    public const int Picks = 6, Laps = 7;

    protected override async Task Load()
    {
        Body.Children.Clear();
        var filters = new WrapPanel { Margin = new Thickness(0, 0, 0, 20) };
        filters.Children.Add(new TextBlock { Text = "Жанр", Foreground = Extras.Muted, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) }); filters.Children.Add(genre);
        filters.Children.Add(new TextBlock { Text = "Статус", Foreground = Extras.Muted, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(24, 0, 10, 0) }); filters.Children.Add(status);
        status.ItemsSource = new[] { "Любой", "Вышедшие", "Онгоинги" }; status.SelectedIndex = 0;
        Body.Children.Add(filters);
        // Барабан: карточки на вращающемся цилиндре, по центру — указатель.
        var drum = new Grid { Height = 300, Margin = new Thickness(0, 6, 0, 0) };
        drum.Children.Add(reel);
        var fade = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 0) };
        fade.GradientStops.Add(new GradientStop(Color.FromRgb(0x0E, 0x0E, 0x11), 0)); fade.GradientStops.Add(new GradientStop(Color.FromArgb(0, 0x0E, 0x0E, 0x11), 0.22));
        fade.GradientStops.Add(new GradientStop(Color.FromArgb(0, 0x0E, 0x0E, 0x11), 0.78)); fade.GradientStops.Add(new GradientStop(Color.FromRgb(0x0E, 0x0E, 0x11), 1));
        drum.Children.Add(new Border { Background = fade, IsHitTestVisible = false });
        drum.Children.Add(Pointer(true)); drum.Children.Add(Pointer(false));
        Body.Children.Add(drum);
        reel.SizeChanged += (_, _) => Layout();
        var stage = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        title.Margin = new Thickness(0, 16, 0, 0); stage.Children.Add(title); stage.Children.Add(meta);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 24, 0, 0) };
        open.Style = (Style)Application.Current.Resources["Primary"];
        actions.Children.Add(spin); actions.Children.Add(open); stage.Children.Add(actions);
        Body.Children.Add(stage);
        spin.Click += async (_, _) => await Spin();
        open.Click += (_, _) => { if (chosen != null) Extras.Open(chosen); };
        await Run(async () => { genres = await Shell.Api.Genres(default); });
        genre.ItemsSource = new[] { "Любой" }.Concat(genres.Select(g => g.Title)).ToList(); genre.SelectedIndex = 0;
        await Spin();
    }
    static FrameworkElement Pointer(bool top) => new System.Windows.Shapes.Path
    {
        Data = Geometry.Parse(top ? "M0,0 L18,0 L9,12 Z" : "M0,12 L18,12 L9,0 Z"), Fill = (Brush)Application.Current.Resources["AccentGradient"],
        HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = top ? VerticalAlignment.Top : VerticalAlignment.Bottom, IsHitTestVisible = false,
        Effect = (System.Windows.Media.Effects.Effect)Application.Current.Resources["AccentGlow"]
    };

    /// <summary>Карточки на цилиндре: угол от центра задаёт сдвиг, масштаб, прозрачность и порядок.</summary>
    void Layout()
    {
        var center = reel.ActualWidth / 2;
        for (var i = 0; i < cards.Count; i++)
        {
            var angle = i * Gap - offset;
            var card = cards[i];
            if (Math.Abs(angle) > 1.45) { card.Visibility = Visibility.Collapsed; continue; }
            var depth = Math.Cos(angle);
            var scale = 0.5 + 0.5 * depth;
            card.Visibility = Visibility.Visible;
            card.Opacity = 0.2 + 0.8 * depth * depth;
            ((ScaleTransform)card.RenderTransform).ScaleX = scale * (0.75 + 0.25 * depth); ((ScaleTransform)card.RenderTransform).ScaleY = scale;
            Canvas.SetLeft(card, center + Radius * Math.Sin(angle) - CardW / 2);
            Canvas.SetTop(card, (reel.ActualHeight - CardH) / 2);
            Panel.SetZIndex(card, (int)(depth * 1000));
        }
    }
    Border Card(Anime anime, ImageSource? image)
    {
        var grid = new Grid();
        grid.Children.Add(new Image { Source = image, Stretch = Stretch.UniformToFill });
        grid.Children.Add(new Border { Background = new LinearGradientBrush(Colors.Transparent, Color.FromArgb(0xDD, 0x0E, 0x0E, 0x11), 90), Height = 80, VerticalAlignment = VerticalAlignment.Bottom });
        grid.Children.Add(new TextBlock { Text = anime.Title, FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = Brushes.White, TextWrapping = TextWrapping.Wrap, MaxHeight = 34, TextTrimming = TextTrimming.CharacterEllipsis, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(10, 0, 10, 10) });
        return new Border
        {
            Width = CardW, Height = CardH, CornerRadius = new CornerRadius(16), Background = Extras.Raised, Child = grid, ClipToBounds = true,
            BorderBrush = new SolidColorBrush(Color.FromArgb(0x40, 0xFF, 0xFF, 0xFF)), BorderThickness = new Thickness(1),
            RenderTransform = new ScaleTransform(1, 1), RenderTransformOrigin = new Point(0.5, 0.5), Effect = (System.Windows.Media.Effects.Effect)Application.Current.Resources["Shadow"]
        };
    }
    /// <summary>Конечный угол барабана: после нескольких полных оборотов по центру встаёт выпавшее аниме.</summary>
    public static double Target(int winner) => (Laps * Picks + winner) * Gap;
    /// <summary>Плавное замедление к концу, как у настоящего барабана.</summary>
    public static double Ease(double t) => 1 - Math.Pow(1 - Math.Clamp(t, 0, 1), 4);

    async Task Spin()
    {
        if (spinning) return;
        spin.IsEnabled = open.IsEnabled = false; title.Text = "Набираем аниме на барабан…"; meta.Text = "";
        List<Anime> pool = [];
        var filter = genre.SelectedIndex > 0 ? "&genres=" + genres[genre.SelectedIndex - 1].Id : "";
        var state = status.SelectedIndex switch { 1 => "released", 2 => "ongoing", _ => "" };
        var ok = await Run(async () =>
        {
            var seen = Shell.Store.Data.History.Select(h => h.Anime.Id).ToHashSet();
            if (account.SignedIn) foreach (var list in AccountClient.Lists) foreach (var a in await account.List(list)) seen.Add(a.Id);
            for (var attempt = 0; attempt < 8 && pool.Count < 18; attempt++)
                pool.AddRange((await Shell.Api.Catalog("", "views", state, Random.Shared.Next(attempt < 4 ? 30 : 8) * 24, default, filter)).Where(a => !seen.Contains(a.Id) && pool.All(p => p.Id != a.Id)));
            if (pool.Count == 0) throw new InvalidOperationException("С такими фильтрами всё уже просмотрено. Ослабь фильтры.");
        });
        if (!ok || Closed) { spin.IsEnabled = true; title.Text = ""; return; }
        // Каждый раз — шесть новых случайных аниме.
        Random.Shared.Shuffle(System.Runtime.InteropServices.CollectionsMarshal.AsSpan(pool));
        six = pool.Take(Picks).ToList();
        var images = six.Select(a => { try { return Uri.TryCreate(a.Poster, UriKind.Absolute, out var u) ? (ImageSource)new BitmapImage(u) : null; } catch (Exception e) when (e is NotSupportedException or IOException) { return null; } }).ToList();
        reel.Children.Clear(); cards.Clear();
        for (var i = 0; i < (Laps + 2) * Picks; i++) { var card = Card(six[i % six.Count], images[i % six.Count]); cards.Add(card); reel.Children.Add(card); }
        var winner = Random.Shared.Next(six.Count);
        // Если аниме меньше шести, они повторяются по кругу: выпадает та карточка, что встанет по центру.
        var target = Target(winner); var landing = Laps * Picks + winner;
        offset = 0; Layout();
        spinning = true; title.Text = "Крутим барабан…";
        var clock = System.Diagnostics.Stopwatch.StartNew(); var duration = TimeSpan.FromSeconds(5.2); var lastCenter = -1;
        var done = new TaskCompletionSource();
        void Frame(object? sender, EventArgs e)
        {
            if (Closed) { CompositionTarget.Rendering -= Frame; done.TrySetResult(); return; }
            var t = clock.Elapsed / duration;
            offset = target * Ease(t); Layout();
            var centerIndex = (int)Math.Round(offset / Gap);
            if (centerIndex != lastCenter) { lastCenter = centerIndex; title.Text = six[centerIndex % six.Count].Title; }
            if (t >= 1) { CompositionTarget.Rendering -= Frame; done.TrySetResult(); }
        }
        CompositionTarget.Rendering += Frame;
        await done.Task;
        spinning = false;
        if (Closed) return;
        chosen = six[landing % six.Count];
        title.Text = chosen.Title;
        meta.Text = chosen.Meta + (chosen.Genres.Length > 0 ? "\n" + chosen.Genres : "");
        var win = cards[landing];
        win.BorderBrush = (Brush)Application.Current.Resources["AccentGradient"]; win.BorderThickness = new Thickness(3);
        win.Effect = new System.Windows.Media.Effects.DropShadowEffect { Color = Color.FromRgb(0xFF, 0x4D, 0x67), BlurRadius = 30, ShadowDepth = 0, Opacity = 0.8 };
        var pop = new DoubleAnimation(1.12, TimeSpan.FromMilliseconds(180)) { AutoReverse = true };
        ((ScaleTransform)win.RenderTransform).BeginAnimation(ScaleTransform.ScaleXProperty, pop); ((ScaleTransform)win.RenderTransform).BeginAnimation(ScaleTransform.ScaleYProperty, pop);
        win.Cursor = Cursors.Hand; win.MouseLeftButtonUp += (_, _) => { if (chosen != null) Extras.Open(chosen); };
        spin.IsEnabled = open.IsEnabled = true;
    }
}

/// <summary>Вход в комнату совместного просмотра по коду.</summary>
public sealed class JoinRoomPage(AccountClient client) : ExtrasPage(client, "Смотреть вместе", "Пауза, перемотка и серия синхронизируются у всех. Чтобы создать комнату, открой серию и нажми «Вместе» справа.")
{
    protected override Task Load()
    {
        Body.Children.Clear();
        var panel = new StackPanel { MaxWidth = 420, HorizontalAlignment = HorizontalAlignment.Left };
        var code = new TextBox { MaxLength = 6, FontSize = 22, CharacterCasing = CharacterCasing.Upper, Margin = new Thickness(0, 10, 0, 20) };
        System.Windows.Automation.AutomationProperties.SetName(code, "Код комнаты");
        var go = new Button { Content = "Войти в комнату", Style = (Style)Application.Current.Resources["Primary"], HorizontalAlignment = HorizontalAlignment.Left, IsDefault = true };
        panel.Children.Add(Extras.Text("Код комнаты от друга", 13, Extras.Muted)); panel.Children.Add(code); panel.Children.Add(go);
        go.Click += async (_, _) => { if (NeedAccount() || code.Text.Trim().Length != 6) return; go.IsEnabled = false; await Join(account, code.Text.Trim(), message => Status.Text = message); go.IsEnabled = true; };
        Body.Children.Add(Extras.Card(panel));
        return Task.CompletedTask;
    }
    /// <summary>Входит в комнату, загружает аниме и открывает плеер на серии комнаты.</summary>
    public static async Task Join(AccountClient account, string code, Action<string> error)
    {
        try
        {
            var state = await account.Rpc("room_join", new JsonObject { ["p_code"] = code.ToUpperInvariant() });
            var (anime, videos) = await Shell.Api.Detail(AccountClient.Long(state, "anime_id"), default);
            var video = videos.FirstOrDefault(v => v.Id == AccountClient.Long(state, "video_id")) ?? videos.FirstOrDefault(v => v.Number == AccountClient.Text(state, "episode") && StreamResolver.Supports(v.Url)) ?? videos.FirstOrDefault(v => v.Number == AccountClient.Text(state, "episode"));
            if (video == null) { error("В комнате серия, которой нет в плеере Yoru."); return; }
            Shell.Navigate(new PlayerPage(anime, videos, video, Shell.Store, account, code.ToUpperInvariant()));
        }
        catch (Exception e) when (Extras.Failure(e)) { error(Rewards.Error(e.Message)); }
    }
}

/// <summary>«Год в Yoru»: сводка за год историями, как Spotify Wrapped. Клик справа — дальше, слева — назад.</summary>
public sealed class WrappedPage : UserControl
{
    static readonly string[] Months = ["январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"];
    static readonly (string, string)[] Skies = [("#2B0F3A", "#FF4D67"), ("#0F1E3A", "#6FC3FF"), ("#3A140F", "#FF8A3D"), ("#0F3A27", "#4ADE80"), ("#221040", "#B388FF"), ("#10102A", "#6F7BFF"), ("#3A0F2A", "#FF8FB8"), ("#1B1B22", "#FFC14D")];
    readonly AccountClient account;
    readonly Grid stage = new();
    readonly UniformGrid bars = new() { Rows = 1, Margin = new Thickness(18, 14, 18, 0), VerticalAlignment = VerticalAlignment.Top, Height = 4 };
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromMilliseconds(50) };
    readonly List<Func<FrameworkElement>> slides = [];
    JsonObject data = new();
    int index; DateTime shownAt;
    sealed class UniformGrid : System.Windows.Controls.Primitives.UniformGrid { }
    public WrappedPage(AccountClient client)
    {
        account = client;
        var root = new Grid { Background = new SolidColorBrush(Color.FromRgb(0x0E, 0x0E, 0x11)), Cursor = Cursors.Hand };
        root.Children.Add(stage); root.Children.Add(bars);
        var close = new Button { Content = "✕", Width = 40, Height = 40, MinWidth = 0, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 26, 18, 0), Background = new SolidColorBrush(Color.FromArgb(0x55, 0, 0, 0)) };
        close.Click += (_, e) => { e.Handled = true; Shell.Back(); };
        root.Children.Add(close);
        root.MouseLeftButtonUp += (_, e) => { if (slides.Count == 0 || e.OriginalSource is Button || IsInsideButton(e.OriginalSource as DependencyObject)) return; Show(e.GetPosition(root).X < root.ActualWidth / 3 ? index - 1 : index + 1); };
        Content = root; Focusable = true;
        KeyDown += (_, e) => { if (e.Key is Key.Right or Key.Space) Show(index + 1); else if (e.Key == Key.Left) Show(index - 1); else if (e.Key == Key.Escape) Shell.Back(); };
        timer.Tick += (_, _) => Progress();
        Loaded += async (_, _) => { Focus(); await Build(); };
        Unloaded += (_, _) => timer.Stop();
    }
    static bool IsInsideButton(DependencyObject? node) { while (node != null) { if (node is Button) return true; node = node is Visual ? VisualTreeHelper.GetParent(node) : LogicalTreeHelper.GetParent(node); } return false; }
    async Task Build()
    {
        stage.Children.Add(new TextBlock { Text = "Собираем твой год…", FontSize = 22, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center });
        try { data = (await account.Rpc("year_summary", new JsonObject { ["p_year"] = DateTime.Now.Year }))?.AsObject() ?? new JsonObject(); }
        catch (Exception e) when (Extras.Failure(e)) { stage.Children.Clear(); stage.Children.Add(new TextBlock { Text = Rewards.Error(e.Message), Foreground = Brushes.White, FontSize = 16, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }); return; }
        var episodes = AccountClient.Int(data, "episodes");
        string N(string key) => data[key]?.ToString() ?? "0";
        slides.Add(() => { var p = Page(0, "Год в Yoru", N("year")); p.Children.Add(Line("Давай вспомним, каким был твой аниме-год. Кликай справа, чтобы листать.")); p.Children.Add(Big("☾", 110, Color.FromRgb(0xFF, 0xE9, 0xA8))); return Wrap(p, 0); });
        if (episodes == 0) slides.Add(() => { var p = Page(1, "Пока тишина", null); p.Children.Add(Line("В этом году ты ещё не досмотрел ни одной серии в плеере Yoru. Включи что-нибудь — и сводка оживёт.")); return Wrap(p, 1); });
        else
        {
            slides.Add(() => { var p = Page(1, "Ты провёл с аниме", null); p.Children.Add(Big(N("hours") + " ч", 84, Colors.White)); var days = (data["hours"]?.GetValue<double>() ?? 0) / 24; p.Children.Add(Line($"{episodes} {Extras.Plural(episodes, "серия", "серии", "серий")} · {AccountClient.Int(data, "anime")} аниме" + (days >= 1 ? $" · это {days:0.0} суток подряд" : ""))); p.Children.Add(Line($"И заработал ◆ {Rewards.Points(AccountClient.Int(data, "points"))} очков")); return Wrap(p, 1); });
            if (data["top_anime"] is JsonArray top && top.Count > 0) slides.Add(() =>
            {
                var first = top[0]!; var p = Page(2, "Аниме года", null);
                var art = new Border { Width = 190, Height = 268, CornerRadius = new CornerRadius(20), ClipToBounds = true, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 14, 0, 0), Background = Extras.Raised };
                if (Uri.TryCreate(AccountClient.Text(first, "poster"), UriKind.Absolute, out var u)) try { art.Child = new Image { Source = new BitmapImage(u), Stretch = Stretch.UniformToFill }; } catch (Exception e) when (e is NotSupportedException or IOException) { }
                art.RenderTransformOrigin = new Point(.5, .5); var scale = new ScaleTransform(.7, .7); art.RenderTransform = scale;
                var grow = new DoubleAnimation(1, TimeSpan.FromMilliseconds(550)) { EasingFunction = new BackEase { Amplitude = .4 } }; scale.BeginAnimation(ScaleTransform.ScaleXProperty, grow); scale.BeginAnimation(ScaleTransform.ScaleYProperty, grow);
                p.Children.Add(art); p.Children.Add(Big(AccountClient.Text(first, "title"), 30, Colors.White));
                p.Children.Add(Line($"{AccountClient.Int(first, "episodes")} {Extras.Plural(AccountClient.Int(first, "episodes"), "серия", "серии", "серий")} · {first["hours"]} ч"));
                for (var i = 1; i < top.Count; i++) p.Children.Add(Line($"#{i + 1}  {AccountClient.Text(top[i], "title")} — {AccountClient.Int(top[i], "episodes")}"));
                return Wrap(p, 2);
            });
            if (data["genres"] is JsonArray genres && genres.Count > 0) slides.Add(() =>
            {
                var p = Page(3, "Твои жанры", "Больше всего — «" + AccountClient.Text(genres[0], "genre") + "»"); var max = Math.Max(1, AccountClient.Int(genres[0], "episodes")); var i = 0;
                foreach (var g in genres)
                {
                    p.Children.Add(Line($"{i + 1}. {AccountClient.Text(g, "genre")}"));
                    var bar = new Border { Height = 14, CornerRadius = new CornerRadius(7), Background = new SolidColorBrush(i == 0 ? Colors.White : Color.FromArgb(0x99, 0xFF, 0xFF, 0xFF)), HorizontalAlignment = HorizontalAlignment.Left, Width = 0, Margin = new Thickness(0, 4, 0, 6) };
                    bar.BeginAnimation(FrameworkElement.WidthProperty, new DoubleAnimation(Math.Max(10, 520.0 * AccountClient.Int(g, "episodes") / max), TimeSpan.FromMilliseconds(900)) { BeginTime = TimeSpan.FromMilliseconds(150 * i), EasingFunction = new CubicEase() });
                    p.Children.Add(bar); i++;
                }
                return Wrap(p, 3);
            });
            if (data["months"] is JsonArray months && months.Count == 12) slides.Add(() =>
            {
                var best = Math.Clamp(AccountClient.Int(data, "top_month"), 1, 12);
                var p = Page(4, "Самый насыщенный месяц", Months[best - 1].ToUpper(CultureInfo.GetCultureInfo("ru-RU")));
                var max = Math.Max(1, months.Max(m => m?.GetValue<int>() ?? 0));
                var chart = new System.Windows.Controls.Primitives.UniformGrid { Columns = 12, Height = 200, MaxWidth = 620, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 24, 0, 0) };
                for (var m = 0; m < 12; m++)
                {
                    var col = new DockPanel { Margin = new Thickness(3, 0, 3, 0) };
                    var label = new TextBlock { Text = Months[m][..1].ToUpper(CultureInfo.GetCultureInfo("ru-RU")), Foreground = Brushes.White, Opacity = .8, HorizontalAlignment = HorizontalAlignment.Center, FontSize = 12 }; DockPanel.SetDock(label, Dock.Bottom); col.Children.Add(label);
                    var bar = new Border { VerticalAlignment = VerticalAlignment.Bottom, CornerRadius = new CornerRadius(5), Background = new SolidColorBrush(m == best - 1 ? Colors.White : Color.FromArgb(0x66, 0xFF, 0xFF, 0xFF)), Height = 0, Margin = new Thickness(0, 0, 0, 4) };
                    bar.BeginAnimation(FrameworkElement.HeightProperty, new DoubleAnimation(Math.Max(3, 160.0 * (months[m]?.GetValue<int>() ?? 0) / max), TimeSpan.FromMilliseconds(500)) { BeginTime = TimeSpan.FromMilliseconds(60 * m) });
                    col.Children.Add(bar); chart.Children.Add(col);
                }
                p.Children.Add(chart); return Wrap(p, 4);
            });
            slides.Add(() =>
            {
                var p = Page(5, "Твои привычки", null); var hour = AccountClient.Int(data, "top_hour");
                p.Children.Add(Big("Чаще всего ты смотришь " + (hour < 5 ? "глубокой ночью 🌙" : hour < 12 ? "по утрам ☀️" : hour < 18 ? "днём" : "по вечерам 🌆"), 30, Colors.White));
                p.Children.Add(Line($"Любимый час — {hour}:00"));
                if (AccountClient.Int(data, "night_episodes") is > 0 and var night) p.Children.Add(Line($"🦉 {night} {Extras.Plural(night, "серия", "серии", "серий")} после полуночи"));
                if (data["best_day"] is JsonObject day) p.Children.Add(Line($"🔥 Рекорд дня — {AccountClient.Int(day, "episodes")} {Extras.Plural(AccountClient.Int(day, "episodes"), "серия", "серии", "серий")} ({AccountClient.Text(day, "date")})"));
                p.Children.Add(Line("📅 Лучшая серия дней подряд — " + Rewards.Days(AccountClient.Int(data, "streak_best"))));
                if (data["first_anime"] is JsonObject first) p.Children.Add(Line($"🎬 Год начался с «{AccountClient.Text(first, "title")}»"));
                return Wrap(p, 5);
            });
            slides.Add(() =>
            {
                var p = Page(6, "Вместе", null); var friends = AccountClient.Int(data, "friends"); var reviews = AccountClient.Int(data, "reviews");
                p.Children.Add(Big($"{friends} {Extras.Plural(friends, "друг", "друга", "друзей")} в Yoru", 32, Colors.White));
                p.Children.Add(Line($"✍ {reviews} {Extras.Plural(reviews, "отзыв", "отзыва", "отзывов")}" + (data["avg_score"] is JsonValue a && a.TryGetValue<double>(out var avg) ? $" · средняя оценка {avg:0.0}" : "")));
                p.Children.Add(Line("✔ Досмотрено аниме — " + AccountClient.Int(data, "completed")));
                return Wrap(p, 6);
            });
        }
        slides.Add(() =>
        {
            var p = Page(7, "Итоги " + N("year"), null);
            if (data["top_percent"] is JsonValue tp && tp.TryGetValue<int>(out var percent)) p.Children.Add(Big($"Ты в топ-{percent}% зрителей Yoru", 34, Colors.White));
            p.Children.Add(Line($"Уровень {AccountClient.Int(data, "level")} · {N("hours")} ч · {episodes} серий"));
            var copy = new Button { Content = "Скопировать итог", Style = (Style)Application.Current.Resources["Primary"], HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 26, 0, 0) };
            copy.Click += (_, e) =>
            {
                e.Handled = true;
                var top = data["top_anime"] as JsonArray;
                var text = $"Мой {N("year")} в Yoru: {N("hours")} ч аниме, {episodes} серий" + (top is { Count: > 0 } ? $", аниме года — «{AccountClient.Text(top[0], "title")}»" : "") + (data["top_percent"] is JsonValue t && t.TryGetValue<int>(out var pc) ? $", топ-{pc}% зрителей" : "") + " 🌙";
                try { Clipboard.SetText(text); copy.Content = "✓ Скопировано"; } catch (System.Runtime.InteropServices.COMException) { }
            };
            p.Children.Add(copy);
            return Wrap(p, 7);
        });
        for (var i = 0; i < slides.Count; i++) bars.Children.Add(new ProgressBar { Height = 4, Maximum = 1, Margin = new Thickness(3, 0, 3, 0), Foreground = Brushes.White, Background = new SolidColorBrush(Color.FromArgb(0x55, 0xFF, 0xFF, 0xFF)), BorderThickness = new Thickness(0) });
        bars.Columns = slides.Count;
        Show(0); timer.Start();
    }
    void Show(int next)
    {
        if (slides.Count == 0) return;
        if (next < 0) next = 0;
        if (next >= slides.Count) { Shell.Back(); return; }
        index = next; shownAt = DateTime.Now;
        stage.Children.Clear(); var slide = slides[index](); slide.Opacity = 0; stage.Children.Add(slide);
        slide.BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(380)));
        for (var i = 0; i < bars.Children.Count; i++) ((ProgressBar)bars.Children[i]).Value = i < index ? 1 : 0;
    }
    void Progress()
    {
        if (slides.Count == 0 || index >= bars.Children.Count) return;
        var duration = index == slides.Count - 1 ? 60.0 : 7.0;
        var p = (DateTime.Now - shownAt).TotalSeconds / duration;
        ((ProgressBar)bars.Children[index]).Value = Math.Min(1, p);
        if (p >= 1 && index < slides.Count - 1) Show(index + 1);
    }
    static StackPanel Page(int sky, string title, string? subtitle)
    {
        var p = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(80, 60, 80, 40), MaxWidth = 760, HorizontalAlignment = HorizontalAlignment.Left };
        p.Children.Add(new TextBlock { Text = title, FontSize = 34, FontWeight = FontWeights.Bold, Foreground = Brushes.White, TextWrapping = TextWrapping.Wrap });
        if (subtitle != null) p.Children.Add(new TextBlock { Text = subtitle, FontSize = subtitle.Length < 24 ? 48 : 24, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0xF2, 0xC2)), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 0) });
        p.Tag = sky;
        return p;
    }
    static FrameworkElement Wrap(StackPanel p, int sky)
    {
        var (from, to) = Skies[sky % Skies.Length];
        var grid = new Grid();
        var a = Decor.Parse(from, Colors.Black); var b = Decor.Parse(to, Colors.Black);
        var bg = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
        bg.GradientStops.Add(new GradientStop(a, 0)); bg.GradientStops.Add(new GradientStop(Color.FromRgb((byte)((a.R + b.R) / 2), (byte)((a.G + b.G) / 2), (byte)((a.B + b.B) / 2)), .55)); bg.GradientStops.Add(new GradientStop(b, 1));
        grid.Background = bg;
        grid.Children.Add(new OrnamentLayer(OrnamentLayer.Kind.Effect, new JsonObject { ["type"] = sky % 2 == 0 ? "sparkles" : "fireflies", ["colors"] = new JsonArray("#FFFFFF", "#FFE08A") }));
        grid.Children.Add(p);
        return grid;
    }
    static TextBlock Line(string text) => new() { Text = text, FontSize = 19, Foreground = new SolidColorBrush(Color.FromArgb(0xEE, 0xFF, 0xFF, 0xFF)), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 10, 0, 0), LineHeight = 28 };
    static TextBlock Big(string text, double size, Color color) => new() { Text = text, FontSize = size, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(color), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 12, 0, 0) };
}
