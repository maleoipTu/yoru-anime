using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>Друзья: поиск по нику, заявки, список друзей и лента «что смотрят друзья».</summary>
public sealed class FriendsPage : UserControl
{
    readonly AccountClient account;
    readonly LocalStore store;
    readonly StackPanel body = new();
    readonly StackPanel results = new() { Margin = new Thickness(0, 8, 0, 0) };
    readonly TextBox search = new() { MinWidth = 320, Margin = new Thickness(0, 0, 10, 0) };
    readonly TextBlock status = new() { Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)), FontSize = 12, Margin = new Thickness(0, 8, 0, 0), TextWrapping = TextWrapping.Wrap };
    static readonly Brush Muted = new SolidColorBrush(Color.FromRgb(0x9A, 0x98, 0xB0));
    static readonly Brush Accent = new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x9B));

    public FriendsPage(AccountClient client, LocalStore local)
    {
        account = client; store = local;
        var root = new DockPanel { Margin = new Thickness(28, 24, 28, 18) };
        var header = new StackPanel();
        var back = new Button { Content = "←  Назад", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 12) }; back.Click += (_, _) => Shell.Back(); header.Children.Add(back);
        header.Children.Add(new TextBlock { Text = "Друзья", FontSize = 26, FontWeight = FontWeights.SemiBold });
        var searchRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 0) };
        System.Windows.Automation.AutomationProperties.SetName(search, "Найти по нику");
        search.KeyDown += async (_, e) => { if (e.Key == Key.Enter) { e.Handled = true; await Find(); } };
        var find = new Button { Content = "Найти по нику" }; find.Click += async (_, _) => await Find();
        searchRow.Children.Add(search); searchRow.Children.Add(find);
        header.Children.Add(searchRow); header.Children.Add(results); header.Children.Add(status);
        DockPanel.SetDock(header, Dock.Top); root.Children.Add(header);
        root.Children.Add(new ScrollViewer { Content = body, Margin = new Thickness(0, 12, 0, 0) });
        Content = root;
        Loaded += async (_, _) => await Load();
        IsVisibleChanged += async (_, e) => { if (e.NewValue is true && IsLoaded) await Load(); };
    }

    async Task Run(Func<Task> job)
    {
        status.Text = "";
        try { await job(); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException) { status.Text = Rewards.Error(e.Message); }
    }

    async Task Find()
    {
        results.Children.Clear();
        var q = search.Text.Trim(); if (q.Length == 0) return;
        await Run(async () =>
        {
            var found = await account.Search(q);
            if (found.Count == 0) { results.Children.Add(new TextBlock { Text = "Никого не нашлось. Ник пишется латиницей.", Foreground = Muted }); return; }
            foreach (var p in found.OfType<JsonObject>().Where(p => AccountClient.Text(p, "id") != account.UserId))
                results.Children.Add(Row(p, "Уровень " + Rewards.Level(AccountClient.Int(p, "total_earned")), "Добавить", async () =>
                {
                    var r = await account.Rpc("send_friend_request", new JsonObject { ["p_username"] = AccountClient.Text(p, "username") });
                    status.Foreground = new SolidColorBrush(Color.FromRgb(0x7E, 0xDC, 0xA9));
                    status.Text = (AccountClient.Text(r, "state") == "accepted" ? "Теперь вы друзья. " : "Заявка отправлена. ") + AccountClient.Announce(r);
                    results.Children.Clear(); search.Text = ""; await Load();
                }));
        });
    }

    async Task Load()
    {
        if (!account.SignedIn) { body.Children.Clear(); body.Children.Add(new TextBlock { Text = "Войди в профиль, чтобы добавлять друзей.", Foreground = Muted }); return; }
        await Run(async () =>
        {
            await account.EnsureCatalog();
            var friends = await account.Friends(); var feed = await account.Feed();
            body.Children.Clear();
            var all = friends.OfType<JsonObject>().ToList();
            var incoming = all.Where(f => AccountClient.Text(f, "state") == "incoming").ToList();
            var mutual = all.Where(f => AccountClient.Text(f, "state") == "friend").ToList();
            var outgoing = all.Where(f => AccountClient.Text(f, "state") == "outgoing").ToList();
            if (incoming.Count > 0)
            {
                Section($"Заявки в друзья · {incoming.Count}");
                foreach (var f in incoming)
                {
                    var row = Row(f, "Хочет дружить", "Принять", async () => { var r = await account.Rpc("respond_friend_request", new JsonObject { ["p_requester"] = AccountClient.Text(f, "id"), ["p_accept"] = true }); status.Text = AccountClient.Announce(r); await Load(); });
                    var decline = new Button { Content = "Отклонить", Margin = new Thickness(8, 0, 0, 0), Background = Brushes.Transparent, Foreground = Accent };
                    decline.Click += async (_, _) => await Run(async () => { await account.Rpc("respond_friend_request", new JsonObject { ["p_requester"] = AccountClient.Text(f, "id"), ["p_accept"] = false }); await Load(); });
                    DockPanel.SetDock(decline, Dock.Right); row.Children.Insert(0, decline);
                    body.Children.Add(row);
                }
            }
            Section($"Друзья · {mutual.Count}");
            if (mutual.Count == 0) body.Children.Add(new TextBlock { Text = "Пока никого. Найди друзей по нику — когда они примут заявку, здесь появится их лента.", Foreground = Muted, TextWrapping = TextWrapping.Wrap });
            foreach (var f in mutual)
                body.Children.Add(Row(f, (AccountClient.Text(f, "status") is { Length: > 0 } s ? $"«{s}»" : "Уровень " + AccountClient.Int(f, "level")) + (AccountClient.Int(f, "streak_days") > 1 ? $" · 🔥 {AccountClient.Int(f, "streak_days")}" : ""), null, null));
            if (outgoing.Count > 0) { Section("Ждут ответа"); foreach (var f in outgoing) body.Children.Add(Row(f, "Заявка отправлена", null, null)); }
            if (feed.Count > 0)
            {
                Section("Что смотрят друзья");
                foreach (var item in feed.OfType<JsonObject>()) body.Children.Add(FeedItem(item));
            }
        });
    }

    void Section(string text) => body.Children.Add(new TextBlock { Text = text, FontSize = 18, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 18, 0, 6) });

    DockPanel Row(JsonObject p, string subtitle, string? action, Func<Task>? onAction)
    {
        var row = new DockPanel { Margin = new Thickness(0, 2, 0, 2) };
        if (action != null && onAction != null)
        {
            var button = new Button { Content = action, VerticalAlignment = VerticalAlignment.Center, Foreground = Accent };
            button.Click += async (_, _) => await Run(onAction);
            DockPanel.SetDock(button, Dock.Right); row.Children.Add(button);
        }
        var open = new Button { Content = Decor.Person(p, account, subtitle), Background = Brushes.Transparent, Padding = new Thickness(4, 0, 4, 0), HorizontalContentAlignment = HorizontalAlignment.Left };
        var username = AccountClient.Text(p, "username");
        open.Click += (_, _) => Shell.Navigate(new ProfilePage(account, store, username));
        row.Children.Add(open);
        return row;
    }

    UIElement FeedItem(JsonObject item)
    {
        var row = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
        var poster = new Border { Width = 52, Height = 74, CornerRadius = new CornerRadius(8), ClipToBounds = true, Background = new SolidColorBrush(Color.FromRgb(0x27, 0x24, 0x31)) };
        if (Uri.TryCreate(AccountClient.Text(item, "poster"), UriKind.Absolute, out var uri) && uri.Scheme == "https") poster.Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill };
        DockPanel.SetDock(poster, Dock.Left); row.Children.Add(poster);
        var text = new StackPanel { Margin = new Thickness(12, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
        var name = AccountClient.Text(item, "display_name").Trim() is { Length: > 0 } n ? n : AccountClient.Text(item, "username");
        text.Children.Add(new TextBlock { Text = name, FontWeight = FontWeights.SemiBold, Foreground = Decor.NameBrush(account.Style(AccountClient.Text(item, "equipped_color") is { Length: > 0 } c ? c : "color_default")) });
        text.Children.Add(new TextBlock { Text = AccountClient.Text(item, "title"), FontSize = 14, Margin = new Thickness(0, 3, 0, 0), TextWrapping = TextWrapping.Wrap });
        var episodes = AccountClient.Int(item, "episodes"); var total = AccountClient.Int(item, "total");
        text.Children.Add(new TextBlock { Text = AccountClient.ListLabel(AccountClient.Text(item, "status")) + (episodes > 0 ? $" · серия {episodes}" + (total > 0 ? $" из {total}" : "") : ""), FontSize = 12, Foreground = Accent, Margin = new Thickness(0, 3, 0, 0) });
        row.Children.Add(text);
        return row;
    }
}
