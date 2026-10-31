using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
namespace Yoru;

/// <summary>
/// Админ-панель. Видна только тем, кто есть в таблице public.admins (доступ выдаётся через базу),
/// и каждая операция дополнительно проверяется на сервере функцией require_admin().
/// </summary>
public sealed class AdminPage : UserControl
{
    readonly AccountClient account;
    readonly StackPanel body = new();
    readonly TextBlock status = new() { FontSize = 12, Margin = new Thickness(0, 8, 0, 0), TextWrapping = TextWrapping.Wrap, Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)) };
    readonly StackPanel tabs = new() { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 12) };
    static readonly Brush Muted = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8));
    static readonly Brush Card = new SolidColorBrush(Color.FromRgb(0x1C, 0x1C, 0x23));
    string tab = "stats";

    public AdminPage(AccountClient client)
    {
        account = client;
        var root = new DockPanel { Margin = new Thickness(28, 24, 28, 18) };
        var header = new DockPanel();
        var back = new Button { Content = "←  Назад" }; back.Click += (_, _) => Shell.Back();
        DockPanel.SetDock(back, Dock.Right); header.Children.Add(back);
        var titles = new StackPanel();
        titles.Children.Add(new TextBlock { Text = "⚑  АДМИНИСТРИРОВАНИЕ", FontSize = 11, FontWeight = FontWeights.SemiBold, Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0xC7, 0x68)) });
        titles.Children.Add(new TextBlock { Text = "Админ-панель Yoru", FontSize = 26, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 6, 0, 0) });
        header.Children.Add(titles);
        var top = new StackPanel(); top.Children.Add(header);
        foreach (var (key, label) in new[] { ("stats", "Обзор"), ("users", "Пользователи"), ("reviews", "Отзывы"), ("decorations", "Украшения"), ("log", "Журнал") })
        {
            var button = new Button { Content = label, Margin = new Thickness(0, 0, 8, 0), Tag = key };
            button.Click += async (_, _) => { tab = key; await Show(); };
            tabs.Children.Add(button);
        }
        top.Children.Add(tabs);
        DockPanel.SetDock(top, Dock.Top); root.Children.Add(top);
        DockPanel.SetDock(status, Dock.Bottom); root.Children.Add(status);
        root.Children.Add(new ScrollViewer { Content = body });
        Content = root;
        Loaded += async (_, _) => await Show();
    }

    async Task Run(Func<Task> job)
    {
        status.Text = "";
        try { await job(); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException or FormatException)
        { status.Text = e.Message.Contains("admin_only") ? "Нет доступа к админ-панели. Доступ выдаётся через таблицу admins в базе." : Rewards.Error(e.Message) is var m && !m.StartsWith("Не получилось связаться") ? m : "Ошибка: " + e.Message; }
    }
    Task<JsonNode?> Call(string name, JsonObject args) => account.Rpc(name, args);

    async Task Show()
    {
        foreach (Button b in tabs.Children) b.Background = (string)b.Tag == tab ? new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) : new SolidColorBrush(Color.FromRgb(0x1F, 0x1F, 0x26));
        body.Children.Clear();
        body.Children.Add(new TextBlock { Text = "Загружаем…", Foreground = Muted });
        Func<Task> job = tab switch { "users" => Users, "reviews" => Reviews, "decorations" => Decorations, "log" => Log, _ => Stats };
        await Run(job);
    }

    static Border Box(UIElement child) => new() { Background = Card, CornerRadius = new CornerRadius(14), Padding = new Thickness(18), Margin = new Thickness(0, 0, 0, 12), Child = child };
    static TextBlock Line(string text, double size = 14, Brush? color = null, bool bold = false) => new() { Text = text, FontSize = size, Foreground = color ?? Brushes.WhiteSmoke, FontWeight = bold ? FontWeights.SemiBold : FontWeights.Normal, TextWrapping = TextWrapping.Wrap };

    async Task Stats()
    {
        var s = await Call("admin_stats", new JsonObject());
        body.Children.Clear();
        var grid = new WrapPanel();
        foreach (var (key, label) in new[] { ("users", "пользователей"), ("new_today", "новых за сутки"), ("episodes_today", "серий за сутки"), ("episodes_total", "серий всего"), ("reviews", "отзывов"), ("friendships", "дружб"), ("points_in_circulation", "очков на руках"), ("banned", "заблокировано") })
        {
            var tile = new StackPanel { Width = 170 };
            tile.Children.Add(Line(Rewards.Points(AccountClient.Int(s, key)), 24, new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x9B)), true));
            tile.Children.Add(Line(label, 12, Muted));
            grid.Children.Add(new Border { Background = Card, CornerRadius = new CornerRadius(14), Padding = new Thickness(16), Margin = new Thickness(0, 0, 10, 10), Child = tile });
        }
        body.Children.Add(grid);
        var topPanel = new StackPanel(); topPanel.Children.Add(Line("Топ по очкам за всё время", 16, null, true));
        foreach (var u in s?["top"] as JsonArray ?? new JsonArray())
            topPanel.Children.Add(Line($"@{AccountClient.Text(u, "username")} — {Rewards.Points(AccountClient.Int(u, "total_earned"))} очков, {AccountClient.Int(u, "episodes_watched")} серий", 13, Muted));
        body.Children.Add(Box(topPanel));
    }

    async Task Users()
    {
        body.Children.Clear();
        var search = new TextBox { MinWidth = 360, Margin = new Thickness(0, 0, 10, 0) };
        System.Windows.Automation.AutomationProperties.SetName(search, "Поиск по нику, имени или почте");
        var find = new Button { Content = "Найти" };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) }; row.Children.Add(search); row.Children.Add(find);
        body.Children.Add(row);
        var list = new StackPanel(); body.Children.Add(list);
        async Task Load()
        {
            await account.EnsureCatalog();
            var users = await Call("admin_users", new JsonObject { ["p_query"] = search.Text.Trim(), ["p_limit"] = 100 }) as JsonArray ?? new JsonArray();
            list.Children.Clear();
            if (users.Count == 0) list.Children.Add(Line("Никого не нашлось.", 13, Muted));
            foreach (var u in users.OfType<JsonObject>()) list.Children.Add(UserRow(u, () => Run(Load)));
        }
        find.Click += async (_, _) => await Run(Load);
        search.KeyDown += async (_, e) => { if (e.Key == Key.Enter) { e.Handled = true; await Run(Load); } };
        await Load();
    }

    UIElement UserRow(JsonObject u, Func<Task> reload)
    {
        var id = AccountClient.Text(u, "id"); var nick = AccountClient.Text(u, "username");
        var banned = AccountClient.Bool(u, "banned");
        var panel = new StackPanel();
        var subtitle = $"{AccountClient.Text(u, "email")} · ◆ {Rewards.Points(AccountClient.Int(u, "points"))} · всего {Rewards.Points(AccountClient.Int(u, "total_earned"))} · серий {AccountClient.Int(u, "episodes_watched")}"
            + (AccountClient.Bool(u, "is_admin") ? " · админ" : "") + (banned ? $" · ЗАБЛОКИРОВАН: {AccountClient.Text(u, "ban_reason")}" : "");
        panel.Children.Add(Decor.Person(u, account, subtitle, 40));
        var actions = new WrapPanel { Margin = new Thickness(52, 6, 0, 0) };
        Button Act(string text, Func<Task> act) { var b = new Button { Content = text, Margin = new Thickness(0, 0, 8, 8), Padding = new Thickness(12, 6, 12, 6), FontSize = 12 }; b.Click += async (_, _) => await Run(async () => { await act(); await reload(); }); return b; }
        var amount = new TextBox { Width = 90, Text = "100", Margin = new Thickness(0, 0, 8, 8) };
        System.Windows.Automation.AutomationProperties.SetName(amount, "Количество очков");
        var reason = new TextBox { Width = 200, Margin = new Thickness(0, 0, 8, 8) };
        System.Windows.Automation.AutomationProperties.SetName(reason, "Причина");
        actions.Children.Add(amount);
        actions.Children.Add(Act("+ очки", () => Call("admin_adjust_points", new JsonObject { ["p_user"] = id, ["p_delta"] = Math.Abs(int.Parse(amount.Text)), ["p_reason"] = reason.Text })));
        actions.Children.Add(Act("− очки", () => Call("admin_adjust_points", new JsonObject { ["p_user"] = id, ["p_delta"] = -Math.Abs(int.Parse(amount.Text)), ["p_reason"] = reason.Text })));
        actions.Children.Add(reason);
        actions.Children.Add(banned
            ? Act("Разблокировать", () => Call("admin_set_banned", new JsonObject { ["p_user"] = id, ["p_banned"] = false }))
            : Act("Заблокировать", () => Call("admin_set_banned", new JsonObject { ["p_user"] = id, ["p_banned"] = true, ["p_reason"] = reason.Text })));
        var deco = new ComboBox { MinWidth = 180, Margin = new Thickness(0, 0, 8, 8) };
        foreach (var d in account.Decorations.OfType<JsonObject>()) deco.Items.Add(new ComboBoxItem { Content = AccountClient.Text(d, "title") + " · " + AccountClient.Text(d, "kind"), Tag = AccountClient.Text(d, "id") });
        System.Windows.Automation.AutomationProperties.SetName(deco, "Украшение для выдачи");
        actions.Children.Add(deco);
        actions.Children.Add(Act("Выдать украшение", () => deco.SelectedItem is ComboBoxItem { Tag: string decoId } ? Call("admin_grant_decoration", new JsonObject { ["p_user"] = id, ["p_decoration"] = decoId }) : throw new InvalidOperationException("Выбери украшение.")));
        var reset = new ComboBox { MinWidth = 160, Margin = new Thickness(0, 0, 8, 8) };
        foreach (var (field, label) in new[] { ("avatar", "Аватар"), ("banner", "Баннер"), ("bio", "«Обо мне»"), ("status", "Статус"), ("display_name", "Имя"), ("showcase", "Витрину") }) reset.Items.Add(new ComboBoxItem { Content = label, Tag = field });
        System.Windows.Automation.AutomationProperties.SetName(reset, "Что сбросить");
        actions.Children.Add(reset);
        actions.Children.Add(Act("Сбросить", () => reset.SelectedItem is ComboBoxItem { Tag: string f } ? Call("admin_reset_profile", new JsonObject { ["p_user"] = id, ["p_fields"] = new JsonArray(f) }) : throw new InvalidOperationException("Выбери, что сбросить.")));
        var open = new Button { Content = "Профиль", Margin = new Thickness(0, 0, 8, 8), Padding = new Thickness(12, 6, 12, 6), FontSize = 12 };
        open.Click += (_, _) => Shell.Navigate(new ProfilePage(account, new LocalStore(), nick));
        actions.Children.Add(open);
        panel.Children.Add(actions);
        return Box(panel);
    }

    async Task Reviews()
    {
        var rows = await Call("admin_recent_reviews", new JsonObject { ["p_limit"] = 100 }) as JsonArray ?? new JsonArray();
        body.Children.Clear();
        if (rows.Count == 0) body.Children.Add(Line("Отзывов пока нет.", 13, Muted));
        foreach (var r in rows.OfType<JsonObject>())
        {
            var panel = new StackPanel();
            panel.Children.Add(Line($"@{AccountClient.Text(r, "username")} → {AccountClient.Text(r, "title")}  ·  ★ {AccountClient.Int(r, "score")}", 14, null, true));
            if (AccountClient.Text(r, "body").Length > 0) panel.Children.Add(Line(AccountClient.Text(r, "body"), 13, Muted));
            var delete = new Button { Content = "Удалить отзыв", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 8, 0, 0), FontSize = 12 };
            var user = AccountClient.Text(r, "user_id"); var anime = AccountClient.Long(r, "anime_id");
            delete.Click += async (_, _) => await Run(async () => { await Call("admin_delete_review", new JsonObject { ["p_user"] = user, ["p_anime_id"] = anime }); await Reviews(); });
            panel.Children.Add(delete);
            body.Children.Add(Box(panel));
        }
    }

    async Task Decorations()
    {
        var all = await account.Get("/rest/v1/decorations?select=*&order=kind.asc,sort.asc");
        body.Children.Clear();
        body.Children.Add(Line("Здесь меняются цены, уровень, окно продажи и видимость. Новое украшение — кнопкой внизу.", 13, Muted));
        foreach (var d in all.OfType<JsonObject>()) body.Children.Add(DecorationEditor(d));
        var add = new Button { Content = "+  Новое украшение", Style = (Style)Application.Current.Resources["Primary"], HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 8, 0, 0) };
        add.Click += (_, _) => body.Children.Insert(1, DecorationEditor(new JsonObject { ["id"] = "", ["kind"] = "frame", ["title"] = "", ["price"] = 100, ["min_level"] = 1, ["rarity"] = "rare", ["style"] = new JsonObject { ["colors"] = new JsonArray("#FF4D67", "#FF8A3D") }, ["active"] = true }));
        body.Children.Add(add);
    }

    public const string StyleTypes = "украшения аватара: cat_ears, bunny, halo, horns, headphones, crown, orbit, sakura_wreath, pumpkin; эффекты: sparkles, bubbles, petals, fireflies, hearts, starfall, bats, snow; рамки профиля: vines, neon, constellation, flames, crystal, moon, cobweb";
    public static string KindLabel(string kind) => kind switch { "frame" => "Обводка аватара", "badge" => "Значок", "name_color" => "Цвет ника", "avatar_deco" => "Украшение аватара", "profile_effect" => "Эффект профиля", "profile_frame" => "Рамка профиля", _ => "Украшение" };
    UIElement DecorationEditor(JsonObject d)
    {
        var grid = new WrapPanel();
        TextBox Field(string label, string value, double width) { var box = new TextBox { Text = value, Width = width, Margin = new Thickness(0, 0, 8, 8), ToolTip = label }; System.Windows.Automation.AutomationProperties.SetName(box, label); return box; }
        var id = Field("ID (латиница)", AccountClient.Text(d, "id"), 150); id.IsReadOnly = AccountClient.Text(d, "id").Length > 0;
        var kind = new ComboBox { MinWidth = 120, Margin = new Thickness(0, 0, 8, 8), ItemsSource = new[] { "frame", "badge", "name_color", "avatar_deco", "profile_effect", "profile_frame" }, SelectedItem = AccountClient.Text(d, "kind"), ToolTip = "frame — обводка аватара, avatar_deco — украшение аватара, profile_effect — эффект профиля, profile_frame — рамка профиля" };
        var title = Field("Название", AccountClient.Text(d, "title"), 170);
        var price = Field("Цена", AccountClient.Int(d, "price").ToString(), 70);
        var level = Field("Уровень", Math.Max(1, AccountClient.Int(d, "min_level")).ToString(), 60);
        var rarity = new ComboBox { MinWidth = 120, Margin = new Thickness(0, 0, 8, 8), ItemsSource = new[] { "common", "rare", "epic", "legendary" }, SelectedItem = AccountClient.Text(d, "rarity") is { Length: > 0 } r ? r : "common" };
        var from = Field("Продаётся с (ГГГГ-ММ-ДД)", AccountClient.Text(d, "available_from") is { Length: >= 10 } f ? f[..10] : "", 110);
        var until = Field("Продаётся до (ГГГГ-ММ-ДД)", AccountClient.Text(d, "available_until") is { Length: >= 10 } u ? u[..10] : "", 110);
        var style = Field("Стиль JSON", (d["style"] ?? new JsonObject()).ToJsonString(), 360);
        var exclusive = new CheckBox { Content = "Только за достижение", IsChecked = AccountClient.Bool(d, "exclusive"), Foreground = Brushes.WhiteSmoke, Margin = new Thickness(0, 6, 12, 8) };
        var active = new CheckBox { Content = "Показывать", IsChecked = d["active"] is null || AccountClient.Bool(d, "active"), Foreground = Brushes.WhiteSmoke, Margin = new Thickness(0, 6, 12, 8) };
        style.ToolTip = "Стиль JSON: colors — цвета, type — рисунок кодом (" + StyleTypes + "), image_url — своя картинка PNG/GIF/WebP";
        var upload = new Button { Content = "Картинка…", Margin = new Thickness(0, 0, 8, 8), ToolTip = "Загрузить свою картинку (PNG, GIF, WebP до 8 МБ) — она заменит рисунок кодом" };
        upload.Click += async (_, _) =>
        {
            var dialog = new Microsoft.Win32.OpenFileDialog { Title = "Картинка украшения", Filter = "Картинки|*.png;*.gif;*.webp;*.jpg;*.jpeg" };
            if (dialog.ShowDialog(Window.GetWindow(this)) != true) return;
            await Run(async () =>
            {
                var bytes = await File.ReadAllBytesAsync(dialog.FileName);
                if (bytes.Length > 8 * 1024 * 1024) throw new InvalidOperationException("Файл больше 8 МБ.");
                var ext = Path.GetExtension(dialog.FileName).ToLowerInvariant().TrimStart('.');
                var mime = ext switch { "gif" => "image/gif", "webp" => "image/webp", "jpg" or "jpeg" => "image/jpeg", _ => "image/png" };
                var url = await account.Upload($"decor-{DateTime.UtcNow:yyyyMMddHHmmss}.{(ext == "jpeg" ? "jpg" : ext)}", bytes, mime);
                var node = JsonNode.Parse(style.Text) as JsonObject ?? new JsonObject();
                node["image_url"] = url; style.Text = node.ToJsonString();
                status.Foreground = new SolidColorBrush(Color.FromRgb(0x7E, 0xDC, 0xA9)); status.Text = "Картинка загружена — нажми «Сохранить».";
            });
        };
        foreach (UIElement e in new UIElement[] { id, kind, title, price, level, rarity, from, until, style, upload, exclusive, active }) grid.Children.Add(e);
        var save = new Button { Content = "Сохранить", Margin = new Thickness(0, 0, 8, 8) };
        save.Click += async (_, _) => await Run(async () =>
        {
            var payload = new JsonObject
            {
                ["id"] = id.Text.Trim(), ["kind"] = kind.SelectedItem as string ?? "frame", ["title"] = title.Text.Trim(), ["description"] = AccountClient.Text(d, "description"),
                ["price"] = int.Parse(price.Text), ["min_level"] = int.Parse(level.Text), ["rarity"] = rarity.SelectedItem as string ?? "common",
                ["style"] = JsonNode.Parse(style.Text), ["sort"] = AccountClient.Int(d, "sort") > 0 ? AccountClient.Int(d, "sort") : 500,
                ["active"] = active.IsChecked == true, ["exclusive"] = exclusive.IsChecked == true, ["available_from"] = from.Text.Trim(), ["available_until"] = until.Text.Trim()
            };
            await Call("admin_upsert_decoration", payload);
            status.Foreground = new SolidColorBrush(Color.FromRgb(0x7E, 0xDC, 0xA9)); status.Text = "Сохранено: " + payload["title"];
        });
        grid.Children.Add(save);
        var preview = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 8) };
        var styleObject = d["style"] as JsonObject ?? new JsonObject();
        preview.Children.Add(AccountClient.Text(d, "kind") switch
        {
            "badge" => Decor.Badge(styleObject, 26),
            "name_color" => new TextBlock { Text = AccountClient.Text(d, "title"), FontSize = 18, FontWeight = FontWeights.Bold, Foreground = Decor.NameBrush(styleObject) },
            "avatar_deco" => Decor.Avatar(64, account.Style("frame_moon"), account.DisplayName, "", styleObject),
            "profile_effect" or "profile_frame" => new Grid { Width = 120, Height = 64, Children = { new Border { CornerRadius = new CornerRadius(10), Background = new SolidColorBrush(Color.FromRgb(0x21, 0x22, 0x2D)) }, OrnamentLayer.Overlay(AccountClient.Text(d, "kind") == "profile_effect" ? styleObject : null, AccountClient.Text(d, "kind") == "profile_frame" ? styleObject : null, 10) } },
            _ => Decor.Avatar(48, styleObject, account.DisplayName, "")
        });
        var panel = new StackPanel(); panel.Children.Add(preview); panel.Children.Add(grid);
        return Box(panel);
    }

    async Task Log()
    {
        var rows = await Call("admin_log_list", new JsonObject { ["p_limit"] = 200 }) as JsonArray ?? new JsonArray();
        body.Children.Clear();
        if (rows.Count == 0) body.Children.Add(Line("Действий пока не было.", 13, Muted));
        foreach (var r in rows.OfType<JsonObject>())
            body.Children.Add(Line($"{AccountClient.Text(r, "created_at").Replace('T', ' ')[..Math.Min(16, AccountClient.Text(r, "created_at").Length)]}  @{AccountClient.Text(r, "admin")}  {AccountClient.Text(r, "action")}  {(AccountClient.Text(r, "target") is { Length: > 0 } t ? "→ @" + t : "")}  {r["details"]?.ToJsonString()}", 12, Muted));
    }
}
