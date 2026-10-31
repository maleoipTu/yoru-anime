using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>Вход и регистрация, профиль с уровнем и очками, витрина любимых аниме и магазин украшений.</summary>
public partial class ProfilePage : UserControl
{
    readonly AccountClient account;
    readonly LocalStore store;
    string tab = "frame";
    bool registering, busy, closed;
    static readonly Brush Muted = new SolidColorBrush(Color.FromRgb(0x9A, 0x98, 0xB0));
    static readonly Brush Panel = new SolidColorBrush(Color.FromRgb(0x16, 0x16, 0x1B));
    static readonly Brush Line = new SolidColorBrush(Color.FromRgb(0x2C, 0x2D, 0x3A));
    static readonly Brush Warm = new SolidColorBrush(Color.FromRgb(0xFF, 0xC7, 0x68));
    static readonly Brush AccentBrush = new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x9B));

    readonly string viewing;
    public ProfilePage(AccountClient client, LocalStore local, string username = "")
    {
        InitializeComponent();
        account = client; store = local; viewing = username;
        Unloaded += (_, _) => closed = true;
        Loaded += async (_, _) => await Reload();
        IsVisibleChanged += async (_, e) => { if (e.NewValue is true && IsLoaded && !busy) await Reload(); };
    }
    void CloseClick(object sender, RoutedEventArgs e) => Shell.Back();

    async Task Reload()
    {
        StatusText.Text = "";
        if (!account.Config.Configured) { NotConfigured(); return; }
        if (viewing.Length > 0 && viewing != AccountClient.Text(account.Profile, "username"))
        {
            JsonObject? data = null;
            await Run(async () => { await account.EnsureCatalog(); data = await account.PublicProfile(viewing); }, () => RenderPublic(data));
            return;
        }
        if (!account.SignedIn) { AuthForm(); return; }
        if (account.Profile.ContainsKey("username")) RenderProfile();
        await Run(async () => { await account.Refresh(); await account.LoadAchievements(); await account.CheckAdmin(); }, RenderProfile);
    }

    /// <summary>Выполняет запрос, показывает ошибку понятным текстом и перерисовывает экран.</summary>
    async Task Run(Func<Task> job, Action? done = null)
    {
        if (busy) return;
        busy = true; BusyBar.Visibility = Visibility.Visible; StatusText.Text = "";
        try { await job(); if (!closed) done?.Invoke(); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException)
        {
            if (closed) return;
            var text = Rewards.Error(e.Message);
            if (!account.SignedIn) { AuthForm(text); return; }
            StatusText.Text = text;
        }
        finally { busy = false; if (!closed) BusyBar.Visibility = Visibility.Collapsed; }
    }

    // ───── Элементы ─────
    static Border Card(UIElement child, double padding = 24) => new() { Background = Panel, BorderBrush = Line, BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(18), Padding = new Thickness(padding), Margin = new Thickness(0, 0, 0, 16), Child = child };
    static TextBlock Text(string text, double size = 14, Brush? color = null, bool bold = false) => new() { Text = text, FontSize = size, Foreground = color ?? Brushes.WhiteSmoke, FontWeight = bold ? FontWeights.SemiBold : FontWeights.Normal, TextWrapping = TextWrapping.Wrap };
    static Button Primary(string text) => new() { Content = text, Style = (Style)Application.Current.Resources["Primary"], HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(22, 11, 22, 11) };
    static TextBox Input(string name, string value = "", int max = 200) { var box = new TextBox { Text = value, MaxLength = max, Margin = new Thickness(0, 6, 0, 14) }; System.Windows.Automation.AutomationProperties.SetName(box, name); return box; }
    static TextBlock Label(string text) => Text(text, 12, Muted, true);

    void NotConfigured()
    {
        Heading.Text = "Аккаунты пока не подключены";
        Body.Children.Clear();
        var panel = new StackPanel();
        panel.Children.Add(Text("В этой сборке не указан адрес базы данных Supabase. Каталог, избранное и просмотр работают как раньше, без аккаунта.", 14, Muted));
        panel.Children.Add(Text("Чтобы включить аккаунты, заполни файл supabase.properties рядом с Yoru.exe (url и anon_key) и перезапусти приложение. Подробности — в README.", 14, Muted));
        ((TextBlock)panel.Children[1]).Margin = new Thickness(0, 12, 0, 0);
        Body.Children.Add(Card(panel));
    }

    void AuthForm(string error = "")
    {
        Heading.Text = registering ? "Создай аккаунт Yoru" : "С возвращением";
        Body.Children.Clear();
        var panel = new StackPanel { MaxWidth = 460, HorizontalAlignment = HorizontalAlignment.Left };
        panel.Children.Add(Text("Смотри аниме, получай очки и укрась профиль рамками, значками и цветом ника.", 14, Muted));
        var modes = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 18, 0, 18) };
        var signIn = new Button { Content = "Вход", Padding = new Thickness(18, 8, 18, 8), Background = registering ? Brushes.Transparent : new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) };
        var signUp = new Button { Content = "Регистрация", Padding = new Thickness(18, 8, 18, 8), Margin = new Thickness(8, 0, 0, 0), Background = registering ? new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) : Brushes.Transparent };
        signIn.Click += (_, _) => { if (registering) { registering = false; AuthForm(); } };
        signUp.Click += (_, _) => { if (!registering) { registering = true; AuthForm(); } };
        modes.Children.Add(signIn); modes.Children.Add(signUp); panel.Children.Add(modes);
        panel.Children.Add(Label("Почта")); var email = Input("Почта", account.Email); panel.Children.Add(email);
        var nick = Input("Ник");
        if (registering) { panel.Children.Add(Label("Ник — латиница, цифры и _")); panel.Children.Add(nick); }
        panel.Children.Add(Label("Пароль")); var password = new PasswordBox { Margin = new Thickness(0, 6, 0, 14) }; System.Windows.Automation.AutomationProperties.SetName(password, "Пароль"); panel.Children.Add(password);
        var message = Text(error, 13, new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B))); message.Margin = new Thickness(0, 0, 0, 12); panel.Children.Add(message);
        var submit = Primary(registering ? "Создать аккаунт" : "Войти"); submit.IsDefault = true; panel.Children.Add(submit);
        submit.Click += async (_, _) =>
        {
            var mail = email.Text.Trim(); var pass = password.Password; var name = nick.Text.Trim().ToLowerInvariant();
            var problem = !Rewards.ValidEmail(mail) ? "Проверь адрес почты." : pass.Length < 6 ? "Пароль — минимум 6 символов." : registering && !Rewards.ValidUsername(name) ? "Ник: 3–20 символов, латиница, цифры и _." : "";
            if (problem.Length > 0) { message.Text = problem; return; }
            if (registering)
            {
                var signedIn = false;
                await Run(async () => signedIn = await account.SignUp(mail, pass, name), async () =>
                {
                    if (signedIn) { await Reload(); return; }
                    registering = false; AuthForm();
                    StatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x7E, 0xDC, 0xA9));
                    StatusText.Text = $"Мы отправили письмо на {mail}. Подтверди почту и войди.";
                });
            }
            else await Run(() => account.SignIn(mail, pass), async () => await Reload());
        };
        Body.Children.Add(Card(panel));
        if (IsLoaded) (account.Email.Length > 0 ? (Control)password : email).Focus();
    }

    void RenderProfile()
    {
        var offset = Scroll.VerticalOffset;
        Body.Children.Clear(); StatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B));
        var p = account.Profile;
        int points = AccountClient.Int(p, "points"), total = AccountClient.Int(p, "total_earned"), level = Rewards.Level(total);
        Heading.Text = account.DisplayName;

        // Шапка в стиле Discord: баннер, аватар поверх края, имя, местоимения, «Обо мне».
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 0) };
        var edit = new Button { Content = "✎  Настроить профиль", Style = (Style)Application.Current.Resources["Primary"] };
        edit.Click += (_, _) => EditProfile();
        var friends = new Button { Content = "♥  Друзья", Margin = new Thickness(10, 0, 0, 0), Foreground = AccentBrush };
        friends.Click += (_, _) => Shell.Navigate(new FriendsPage(account, store));
        actions.Children.Add(edit); actions.Children.Add(friends);
        if (account.IsAdmin)
        {
            var admin = new Button { Content = "⚑  Админ-панель", Margin = new Thickness(10, 0, 0, 0), Foreground = Warm };
            admin.Click += (_, _) => Shell.Navigate(new AdminPage(account));
            actions.Children.Add(admin);
        }
        Body.Children.Add(ProfileCard(p, actions));

        // Очки и уровень.
        var stats = new UniformGrid3();
        stats.Add("◆ " + Rewards.Points(points), "очков", Warm);
        stats.Add(level.ToString(), "уровень", AccentBrush);
        stats.Add("🔥 " + AccountClient.Int(p, "streak_days"), "дней подряд", new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x5B)));
        var progress = new StackPanel();
        progress.Children.Add(stats.Grid);
        var levelRow = new DockPanel { Margin = new Thickness(0, 20, 0, 8) };
        var next = Text($"ещё {Rewards.Points(Rewards.ToNext(total))} до уровня {level + 1}", 12, Muted); DockPanel.SetDock(next, Dock.Right); levelRow.Children.Add(next);
        levelRow.Children.Add(Text("Уровень " + level, 15, null, true)); progress.Children.Add(levelRow);
        progress.Children.Add(new ProgressBar { Height = 6, Maximum = 1, Value = Rewards.Progress(total), Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x4D, 0x67)), Background = Line, BorderThickness = new Thickness(0) });
        var hint = Text($"Засчитано серий: {AccountClient.Int(p, "episodes_watched")}. Очки начисляются за просмотр в плеере Yoru: {Rewards.EpisodePoints} за серию, +{Rewards.NewAnimeBonus} за новое аниме и до +20 за дни подряд. Ещё очки дают достижения.", 12, Muted); hint.Margin = new Thickness(0, 12, 0, 0); progress.Children.Add(hint);
        Body.Children.Add(Card(progress));

        // Рейтинг, подарки, «Год в Yoru», совместный просмотр, рекомендации и перенос со старого сайта.
        var tools = new WrapPanel { Margin = new Thickness(0, 0, 0, 16) };
        foreach (var (icon, title, open) in new (string, string, Action)[]
        {
            ("🏆", "Рейтинг недели", () => Shell.Navigate(new LeaderboardPage(account))),
            ("🎁", "Подарки", () => Shell.Navigate(new GiftsPage(account))),
            ("✨", "Год в Yoru", () => Shell.Navigate(new WrappedPage(account))),
            ("👥", "Смотреть вместе", () => Shell.Navigate(new JoinRoomPage(account))),
            ("💡", "Для тебя", () => Shell.Navigate(new ForYouPage(account))),
            ("⇩", "Перенос с YummyAnime", () => Shell.Navigate(new ImportPage(account))),
        })
        {
            var button = new Button { Content = icon + "  " + title, Margin = new Thickness(0, 0, 10, 10), Padding = new Thickness(16, 10, 16, 10) };
            button.Click += (_, _) => open();
            tools.Children.Add(button);
        }
        Body.Children.Add(tools);
        Body.Children.Add(Extras.StatsCard(account, AccountClient.Text(p, "username")));

        // Витрина «любимое аниме».
        var showcase = new StackPanel();
        var showHead = new DockPanel();
        var change = new Button { Content = "Изменить", Foreground = AccentBrush, Background = Brushes.Transparent, Padding = new Thickness(10, 4, 10, 4) }; change.Click += (_, _) => EditShowcase(); DockPanel.SetDock(change, Dock.Right); showHead.Children.Add(change);
        showHead.Children.Add(Text("Любимое аниме", 18, null, true)); showcase.Children.Add(showHead);
        var items = p["showcase"] as JsonArray ?? new JsonArray();
        if (items.Count == 0) { var empty = Text("Выбери до 6 аниме из избранного — они появятся здесь.", 13, Muted); empty.Margin = new Thickness(0, 10, 0, 0); showcase.Children.Add(empty); }
        else
        {
            var row = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
            foreach (var item in items)
            {
                var cell = new StackPanel { Width = 116, Margin = new Thickness(0, 0, 14, 8) };
                var poster = new Border { Height = 160, CornerRadius = new CornerRadius(10), Background = new SolidColorBrush(Color.FromRgb(0x27, 0x24, 0x31)), ClipToBounds = true };
                if (Uri.TryCreate(AccountClient.Text(item, "poster"), UriKind.Absolute, out var uri) && uri.Scheme == "https") poster.Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill };
                cell.Children.Add(poster);
                cell.Children.Add(new TextBlock { Text = AccountClient.Text(item, "title"), FontSize = 12, Margin = new Thickness(0, 6, 0, 0), MaxHeight = 34, TextTrimming = TextTrimming.CharacterEllipsis, TextWrapping = TextWrapping.Wrap });
                row.Children.Add(cell);
            }
            showcase.Children.Add(row);
        }
        Body.Children.Add(Card(showcase));

        var achievementsCard = Achievements(account.Unlocked);
        if (achievementsCard != null) Body.Children.Add(achievementsCard);

        // Магазин украшений.
        var shop = new StackPanel();
        var shopHead = new DockPanel();
        var balance = Text("◆ " + Rewards.Points(points), 15, Warm, true); DockPanel.SetDock(balance, Dock.Right); shopHead.Children.Add(balance);
        shopHead.Children.Add(Text("Украшения", 18, null, true)); shop.Children.Add(shopHead);
        var tabs = new WrapPanel { Margin = new Thickness(0, 14, 0, 14) };
        foreach (var (kind, title) in new[] { ("avatar_deco", "Украшения аватара"), ("profile_effect", "Эффекты профиля"), ("profile_frame", "Рамки профиля"), ("frame", "Обводка аватара"), ("badge", "Значки"), ("name_color", "Цвет ника") })
        {
            var button = new Button { Content = title, Padding = new Thickness(16, 7, 16, 7), Margin = new Thickness(0, 0, 8, 0), Background = tab == kind ? new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) : Brushes.Transparent, Foreground = tab == kind ? AccentBrush : Brushes.WhiteSmoke };
            button.Margin = new Thickness(0, 0, 8, 8); button.Click += (_, _) => { tab = kind; RenderProfile(); };
            tabs.Children.Add(button);
        }
        shop.Children.Add(tabs);
        var grid = new WrapPanel();
        foreach (var d in account.Decorations.OfType<JsonObject>().Where(d => AccountClient.Text(d, "kind") == tab
            && (account.Owned.Contains(AccountClient.Text(d, "id")) || Rewards.OnSale(AccountClient.Text(d, "available_from"), AccountClient.Text(d, "available_until"), DateTimeOffset.Now))))
            grid.Children.Add(Tile(d, points, level));
        if (grid.Children.Count == 0) shop.Children.Add(Text(busy ? "Загружаем украшения…" : "Украшения пока не загрузились.", 13, Muted));
        shop.Children.Add(grid);
        Body.Children.Add(Card(shop));

        var footer = new DockPanel { Margin = new Thickness(4, 4, 0, 10) };
        var logout = new Button { Content = "Выйти из аккаунта", Foreground = AccentBrush, Background = Brushes.Transparent };
        logout.Click += (_, _) => { account.SignOut(); registering = false; AuthForm(); };
        DockPanel.SetDock(logout, Dock.Right); footer.Children.Add(logout);
        footer.Children.Add(new TextBlock { Text = "Аккаунт: " + account.Email, Foreground = Muted, FontSize = 12, VerticalAlignment = VerticalAlignment.Center });
        Body.Children.Add(footer);
        Dispatcher.BeginInvoke(() => Scroll.ScrollToVerticalOffset(offset));
    }
    string Frame => Field(account.Profile, "equipped_frame", "frame_moon");
    static FrameworkElement Ornaments_Overlay(JsonObject? effect, JsonObject? frame, double radius) => OrnamentLayer.Overlay(effect, frame, radius);
    public static string EquippedField(string kind) => kind switch { "frame" => "equipped_frame", "badge" => "equipped_badge", "avatar_deco" => "equipped_deco", "profile_effect" => "equipped_effect", "profile_frame" => "equipped_pframe", _ => "equipped_color" };
    static string Field(JsonNode? node, string key, string fallback) => AccountClient.Text(node, key) is { Length: > 0 } v ? v : fallback;

    FrameworkElement Tile(JsonObject d, int points, int level)
    {
        string id = AccountClient.Text(d, "id"), kind = AccountClient.Text(d, "kind"), rarity = AccountClient.Text(d, "rarity");
        int price = AccountClient.Int(d, "price"), minLevel = Math.Max(1, AccountClient.Int(d, "min_level"));
        var style = d["style"] as JsonObject ?? new JsonObject();
        var exclusive = AccountClient.Bool(d, "exclusive");
        var have = (price == 0 && !exclusive) || account.Owned.Contains(id);
        var until = AccountClient.Text(d, "available_until");
        var equipped = AccountClient.Text(account.Profile, EquippedField(kind));
        var removable = kind is not ("frame" or "name_color");
        var worn = id == equipped;
        var panel = new StackPanel { Width = 170 };
        var preview = new Grid { Height = 84 };
        if (kind == "frame") preview.Children.Add(Decor.Avatar(76, style, account.DisplayName, AccountClient.Text(account.Profile, "avatar_url")));
        else if (kind == "badge") { var glyph = Decor.Badge(style, 38); glyph.HorizontalAlignment = HorizontalAlignment.Center; preview.Children.Add(glyph); }
        else if (kind == "avatar_deco") preview.Children.Add(Decor.Avatar(84, account.Style(Frame), account.DisplayName, AccountClient.Text(account.Profile, "avatar_url"), style));
        else if (kind is "profile_effect" or "profile_frame")
        {
            var card = new Grid { Width = 140, Height = 84 };
            card.Children.Add(new Border { CornerRadius = new CornerRadius(12), Background = new LinearGradientBrush(Color.FromRgb(0x2A, 0x2A, 0x35), Color.FromRgb(0x16, 0x16, 0x1B), 90) });
            card.Children.Add(Ornaments_Overlay(kind == "profile_effect" ? style : null, kind == "profile_frame" ? style : null, 12));
            preview.Children.Add(card);
        }
        else preview.Children.Add(new TextBlock { Text = account.DisplayName, FontSize = 19, FontWeight = FontWeights.Bold, Foreground = Decor.NameBrush(style), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis });
        panel.Children.Add(preview);
        panel.Children.Add(new TextBlock { Text = AccountClient.Text(d, "title"), FontSize = 14, FontWeight = FontWeights.SemiBold, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 0) });
        panel.Children.Add(new TextBlock { Text = Rewards.Rarity(rarity) + (until.Length > 0 && !have ? " · до " + Rewards.Until(until) : ""), FontSize = 11, Foreground = new SolidColorBrush(Decor.Parse(Rewards.RarityColor(rarity), Colors.Gray)), HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 3, 0, 0) });
        string state; Brush color;
        if (worn) { state = removable ? "✓ Надето · снять" : "✓ Надето"; color = AccentBrush; }
        else if (have) { state = "Надеть"; color = Brushes.WhiteSmoke; }
        else if (exclusive) { state = "🏆 За достижение"; color = Muted; }
        else if (level < minLevel) { state = "С уровня " + minLevel; color = Muted; }
        else { state = "◆ " + Rewards.Points(price); color = points >= price ? Warm : Muted; }
        panel.Children.Add(new TextBlock { Text = state, FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = color, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 0) });
        var button = new Button { Content = panel, Padding = new Thickness(10, 14, 10, 12), Margin = new Thickness(0, 0, 12, 12), Background = worn ? new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) : new SolidColorBrush(Color.FromRgb(0x21, 0x22, 0x2D)), ToolTip = AccountClient.Text(d, "description") };
        System.Windows.Automation.AutomationProperties.SetName(button, $"{AccountClient.Text(d, "title")}, {Rewards.Rarity(rarity)}. {state.Replace("◆", "цена")}");
        button.Click += async (_, _) =>
        {
            if (busy) return;
            if (worn) { if (removable) await Run(() => account.Equip(kind, ""), RenderProfile); return; }
            if (have) { await Run(() => account.Equip(kind, id), RenderProfile); return; }
            if (exclusive) { StatusText.Text = AccountClient.Text(d, "description"); return; }
            if (level < minLevel) { StatusText.Text = $"«{AccountClient.Text(d, "title")}» откроется на уровне {minLevel}."; return; }
            if (points < price) { StatusText.Text = $"Не хватает {Rewards.Points(price - points)} очков."; return; }
            if (MessageBox.Show(Window.GetWindow(this)!, $"Купить «{AccountClient.Text(d, "title")}» за {Rewards.Points(price)} очков и сразу надеть?\nОстанется: {Rewards.Points(points - price)}.", "Украшение", MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
            await Run(async () => { await account.Buy(id, kind); if (account.Owned.Count >= 5) { await account.Refresh(); await account.LoadAchievements(); } }, RenderProfile);
        };
        return button;
    }

    /// <summary>Карточка профиля: баннер (картинка, GIF, WebM или градиент темы), аватар с рамкой, имя, местоимения, статус и «Обо мне».</summary>
    FrameworkElement ProfileCard(JsonNode p, UIElement? actions)
    {
        var primary = Decor.Parse(AccountClient.Text(p, "theme_primary"), Color.FromRgb(0xFF, 0x4D, 0x67));
        var accent = Decor.Parse(AccountClient.Text(p, "theme_accent"), Color.FromRgb(0xFF, 0x8A, 0x3D));
        var name = AccountClient.Text(p, "display_name").Trim() is { Length: > 0 } dn ? dn : AccountClient.Text(p, "username");
        var root = new StackPanel();
        var banner = new Grid { Height = 190, ClipToBounds = true };
        banner.Children.Add(new Border { Background = new LinearGradientBrush(primary, accent, 20) });
        var bannerUrl = AccountClient.Text(p, "banner_url");
        if (bannerUrl.Length > 0) banner.Children.Add(Media.View(bannerUrl));
        banner.Children.Add(new Border { Background = new LinearGradientBrush(Colors.Transparent, Color.FromArgb(0x99, 0x0E, 0x0E, 0x11), 90) });
        root.Children.Add(new Border { CornerRadius = new CornerRadius(20, 20, 0, 0), ClipToBounds = true, Child = banner });
        var body = new StackPanel { Margin = new Thickness(26, -58, 26, 24) };
        var decoId = AccountClient.Text(p, "equipped_deco");
        var avatarHolder = new Border { Width = 124, Height = 124, CornerRadius = new CornerRadius(62), Background = new SolidColorBrush(Color.FromRgb(0x16, 0x16, 0x1B)), HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(6),
            Child = Decor.Avatar(112, account.Style(Field(p, "equipped_frame", "frame_moon")), name, AccountClient.Text(p, "avatar_url")) };
        if (decoId.Length > 0)
        {
            // С украшением аватар крупнее и без подложки, чтобы рисунок выходил за круг.
            var decorated = new Grid { Width = 168, Height = 168, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(-22, -22, 0, 0) };
            decorated.Children.Add(new System.Windows.Shapes.Ellipse { Width = 124, Height = 124, Fill = new SolidColorBrush(Color.FromRgb(0x16, 0x16, 0x1B)) });
            decorated.Children.Add(Decor.Avatar(168, account.Style(Field(p, "equipped_frame", "frame_moon")), name, AccountClient.Text(p, "avatar_url"), account.Style(decoId)));
            body.Children.Add(decorated);
        }
        else body.Children.Add(avatarHolder);
        var nameRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 10, 0, 0) };
        nameRow.Children.Add(new TextBlock { Text = name, FontSize = 26, FontWeight = FontWeights.Bold, Foreground = Decor.NameBrush(account.Style(Field(p, "equipped_color", "color_default"))) });
        var badge = Decor.Badge(account.Style(AccountClient.Text(p, "equipped_badge")), 22); badge.Margin = new Thickness(10, 0, 0, 0); nameRow.Children.Add(badge);
        body.Children.Add(nameRow);
        var pronouns = AccountClient.Text(p, "pronouns");
        body.Children.Add(new TextBlock { Text = "@" + AccountClient.Text(p, "username") + (pronouns.Length > 0 ? "  ·  " + pronouns : ""), Foreground = Muted, FontSize = 13, Margin = new Thickness(0, 4, 0, 0) });
        var status = AccountClient.Text(p, "status");
        if (status.Length > 0) body.Children.Add(new Border { Background = new SolidColorBrush(Color.FromArgb(0x22, 0xFF, 0xFF, 0xFF)), CornerRadius = new CornerRadius(10), Padding = new Thickness(12, 8, 12, 8), Margin = new Thickness(0, 12, 0, 0), HorizontalAlignment = HorizontalAlignment.Left,
            Child = new TextBlock { Text = "💬  " + status, FontSize = 13, TextWrapping = TextWrapping.Wrap } });
        var bio = AccountClient.Text(p, "bio");
        if (bio.Length > 0)
        {
            body.Children.Add(new TextBlock { Text = "ОБО МНЕ", FontSize = 11, FontWeight = FontWeights.Bold, Foreground = Muted, Margin = new Thickness(0, 16, 0, 6) });
            body.Children.Add(new TextBlock { Text = bio, FontSize = 14, TextWrapping = TextWrapping.Wrap, LineHeight = 21 });
        }
        if (actions != null) body.Children.Add(actions);
        root.Children.Add(body);
        var effectId = AccountClient.Text(p, "equipped_effect"); var pframeId = AccountClient.Text(p, "equipped_pframe");
        var cardBorder = new Border { CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(2), Child = root,
            BorderBrush = new LinearGradientBrush(primary, accent, 45),
            Background = new LinearGradientBrush(Color.FromArgb(0x40, primary.R, primary.G, primary.B), Color.FromRgb(0x14, 0x14, 0x18), 90),
            Effect = (System.Windows.Media.Effects.Effect)Application.Current.Resources["Shadow"] };
        var layered = new Grid { Margin = new Thickness(0, 0, 0, 16) };
        layered.Children.Add(cardBorder);
        if (effectId.Length > 0 || pframeId.Length > 0) layered.Children.Add(OrnamentLayer.Overlay(effectId.Length > 0 ? account.Style(effectId) : null, pframeId.Length > 0 ? account.Style(pframeId) : null, 22));
        return layered;
    }

    /// <summary>Выбор файла и загрузка аватара или баннера с проверкой размера и разрешения.</summary>
    async Task UploadMedia(bool banner)
    {
        var dialog = new Microsoft.Win32.OpenFileDialog { Title = banner ? "Баннер профиля" : "Аватар", Filter = "Картинки и анимации|*.png;*.jpg;*.jpeg;*.webp;*.gif;*.webm" };
        if (dialog.ShowDialog(Window.GetWindow(this)) != true) return;
        var path = dialog.FileName;
        await Run(async () =>
        {
            var prepared = await Task.Run(() => Media.Prepare(path, banner));
            var old = AccountClient.Text(account.Profile, banner ? "banner_url" : "avatar_url");
            var url = await account.Upload($"{(banner ? "banner" : "avatar")}-{DateTime.UtcNow.Ticks}.{prepared.Extension}", prepared.Data, prepared.Mime);
            await account.UpdateProfile(new JsonObject { [banner ? "p_banner_url" : "p_avatar_url"] = url });
            await account.DeleteOwn(old);
        }, EditProfile);
    }

    void EditProfile()
    {
        var p = account.Profile;
        Body.Children.Clear(); Heading.Text = "Редактирование профиля";
        var panel = new StackPanel { MaxWidth = 520, HorizontalAlignment = HorizontalAlignment.Left };
        panel.Children.Add(Label("Имя")); var display = Input("Имя", AccountClient.Text(p, "display_name"), 32); panel.Children.Add(display);
        panel.Children.Add(Label("Ник — латиница, цифры и _")); var nick = Input("Ник", AccountClient.Text(p, "username"), 20); panel.Children.Add(nick);
        panel.Children.Add(Label("Статус")); var status = Input("Статус", AccountClient.Text(p, "status"), 80); panel.Children.Add(status);
        panel.Children.Add(Label("Местоимения — например, он/его")); var pronouns = Input("Местоимения", AccountClient.Text(p, "pronouns"), 20); panel.Children.Add(pronouns);
        panel.Children.Add(Label("Обо мне — до 190 символов")); var bio = Input("Обо мне", AccountClient.Text(p, "bio"), 190); bio.AcceptsReturn = true; bio.TextWrapping = TextWrapping.Wrap; bio.MinHeight = 80; panel.Children.Add(bio);
        panel.Children.Add(Label("Аватар и баннер"));
        var media = new WrapPanel { Margin = new Thickness(0, 6, 0, 6) };
        var uploadAvatar = new Button { Content = "⇪  Загрузить аватар", Margin = new Thickness(0, 0, 8, 8) }; uploadAvatar.Click += async (_, _) => await UploadMedia(false);
        var uploadBanner = new Button { Content = "⇪  Загрузить баннер", Margin = new Thickness(0, 0, 8, 8) }; uploadBanner.Click += async (_, _) => await UploadMedia(true);
        var removeBanner = new Button { Content = "Убрать баннер", Margin = new Thickness(0, 0, 8, 8), Visibility = AccountClient.Text(p, "banner_url").Length > 0 ? Visibility.Visible : Visibility.Collapsed };
        removeBanner.Click += async (_, _) => { var old = AccountClient.Text(account.Profile, "banner_url"); await Run(async () => { await account.UpdateProfile(new JsonObject { ["p_banner_url"] = "" }); await account.DeleteOwn(old); }, EditProfile); };
        media.Children.Add(uploadAvatar); media.Children.Add(uploadBanner); media.Children.Add(removeBanner);
        panel.Children.Add(media);
        panel.Children.Add(Text("PNG, JPEG, WebP — обрежутся и сожмутся автоматически. GIF и WebM: аватар до 4 МБ и 640×640 (почти квадрат), баннер до 8 МБ и 1920×1080, видео до 15 секунд.", 12, Muted));
        panel.Children.Add(Label("Цвета профиля"));
        var palette = new (string A, string B)[] { ("#FF4D67", "#FF8A3D"), ("#7C5CFF", "#3EC6FF"), ("#16C784", "#0E7C66"), ("#FF6FB5", "#B388FF"), ("#FFC768", "#FF6A3D"), ("#3E7BFA", "#1C1530"), ("#E4E6F0", "#5A5F73") };
        var primary = Input("Основной цвет", AccountClient.Text(p, "theme_primary"), 7); var accent = Input("Акцент", AccountClient.Text(p, "theme_accent"), 7);
        var swatches = new WrapPanel { Margin = new Thickness(0, 6, 0, 6) };
        foreach (var (a, b) in palette)
        {
            var swatch = new Button { Width = 44, Height = 30, Margin = new Thickness(0, 0, 8, 8), Background = new LinearGradientBrush(Decor.Parse(a, Colors.Gray), Decor.Parse(b, Colors.Gray), 0), ToolTip = $"{a} → {b}" };
            System.Windows.Automation.AutomationProperties.SetName(swatch, $"Тема {a} {b}");
            swatch.Click += (_, _) => { primary.Text = a; accent.Text = b; };
            swatches.Children.Add(swatch);
        }
        var clearTheme = new Button { Content = "По умолчанию", Height = 30, Margin = new Thickness(0, 0, 8, 8) }; clearTheme.Click += (_, _) => { primary.Text = ""; accent.Text = ""; };
        swatches.Children.Add(clearTheme);
        panel.Children.Add(swatches);
        var colorRow = new System.Windows.Controls.Primitives.UniformGrid { Columns = 2 }; accent.Margin = new Thickness(8, 6, 0, 14); colorRow.Children.Add(primary); colorRow.Children.Add(accent); panel.Children.Add(colorRow);
        panel.Children.Add(Label("Аватар"));
        var avatarBox = new ComboBox { Margin = new Thickness(0, 6, 0, 14), HorizontalAlignment = HorizontalAlignment.Left, MinWidth = 320 };
        var current = AccountClient.Text(p, "avatar_url");
        var choices = new List<(string Title, string Url)>();
        if (current.Contains("/storage/v1/object/public/media/")) choices.Add(("Загруженный аватар", current));
        choices.Add(("Первая буква ника", ""));
        foreach (var item in p["showcase"] as JsonArray ?? new JsonArray()) if (AccountClient.Text(item, "poster").StartsWith("https://")) choices.Add((AccountClient.Text(item, "title"), AccountClient.Text(item, "poster")));
        foreach (var c in choices) avatarBox.Items.Add(c.Title);
        avatarBox.SelectedIndex = Math.Max(0, choices.FindIndex(c => c.Url == AccountClient.Text(p, "avatar_url")));
        panel.Children.Add(avatarBox);
        panel.Children.Add(Text("Или поставь постер из витрины «Любимое аниме».", 12, Muted));
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 18, 0, 0) };
        var save = Primary("Сохранить"); var cancel = new Button { Content = "Отмена", Margin = new Thickness(12, 0, 0, 0) };
        cancel.Click += (_, _) => RenderProfile();
        save.Click += async (_, _) =>
        {
            var newNick = nick.Text.Trim().ToLowerInvariant();
            if (!Rewards.ValidUsername(newNick)) { StatusText.Text = "Ник: 3–20 символов, латиница, цифры и _."; return; }
            var renamed = newNick != AccountClient.Text(p, "username");
            await Run(async () =>
            {
                if (renamed) await account.Rpc("change_username", new JsonObject { ["p_username"] = newNick });
                var fields = new JsonObject { ["p_display_name"] = display.Text.Trim(), ["p_status"] = status.Text.Trim(), ["p_pronouns"] = pronouns.Text.Trim(), ["p_bio"] = bio.Text.Trim(),
                    ["p_theme_primary"] = HexOrEmpty(primary.Text), ["p_theme_accent"] = HexOrEmpty(accent.Text) };
                fields["p_avatar_url"] = choices[Math.Max(0, avatarBox.SelectedIndex)].Url;
                await account.UpdateProfile(fields);
            }, RenderProfile);
        };
        actions.Children.Add(save); actions.Children.Add(cancel); panel.Children.Add(actions);
        Body.Children.Add(Card(panel));
    }

    static string HexOrEmpty(string text) { var t = text.Trim(); if (t.Length == 6 && !t.StartsWith('#')) t = "#" + t; return System.Text.RegularExpressions.Regex.IsMatch(t, "^#[0-9A-Fa-f]{6}$") ? t.ToUpperInvariant() : ""; }

    void EditShowcase()
    {
        Body.Children.Clear(); Heading.Text = "Любимое аниме";
        var panel = new StackPanel();
        var favorites = store.Data.Favorites;
        if (favorites.Count == 0)
        {
            panel.Children.Add(Text("В избранном пока пусто. Открой карточку аниме и нажми «В избранное» — тогда его можно будет добавить в витрину.", 14, Muted));
            var back = new Button { Content = "Назад к профилю", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 16, 0, 0) }; back.Click += (_, _) => RenderProfile(); panel.Children.Add(back);
            Body.Children.Add(Card(panel)); return;
        }
        var chosen = (account.Profile["showcase"] as JsonArray ?? new JsonArray()).Select(o => (long)AccountClient.Int(o, "id")).Where(id => id > 0).ToList();
        var counter = Text("", 13, Muted); panel.Children.Add(counter);
        void Count() => counter.Text = $"Выбрано {chosen.Count} из 6";
        Count();
        foreach (var anime in favorites)
        {
            var box = new CheckBox { Content = anime.Title, IsChecked = chosen.Contains(anime.Id), Foreground = Brushes.WhiteSmoke, Margin = new Thickness(0, 10, 0, 0), FontSize = 14 };
            box.Checked += (_, _) => { if (chosen.Contains(anime.Id)) return; if (chosen.Count >= 6) { box.IsChecked = false; StatusText.Text = "В витрине помещается 6 аниме."; return; } chosen.Add(anime.Id); Count(); };
            box.Unchecked += (_, _) => { chosen.Remove(anime.Id); Count(); };
            panel.Children.Add(box);
        }
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 20, 0, 0) };
        var save = Primary("Сохранить витрину"); var cancel = new Button { Content = "Отмена", Margin = new Thickness(12, 0, 0, 0) };
        cancel.Click += (_, _) => RenderProfile();
        save.Click += async (_, _) =>
        {
            var items = new JsonArray();
            foreach (var id in chosen) if (favorites.FirstOrDefault(a => a.Id == id) is { } a) items.Add(new JsonObject { ["id"] = a.Id, ["title"] = a.Title.Length > 120 ? a.Title[..120] : a.Title, ["poster"] = a.Poster });
            var fields = new JsonObject { ["p_showcase"] = items };
            var avatar = AccountClient.Text(account.Profile, "avatar_url");
            if (avatar.Length > 0 && !items.Any(i => AccountClient.Text(i, "poster") == avatar)) fields["p_avatar_url"] = "";
            await Run(() => account.UpdateProfile(fields), RenderProfile);
        };
        actions.Children.Add(save); actions.Children.Add(cancel); panel.Children.Add(actions);
        Body.Children.Add(Card(panel));
    }

    /// <summary>Сетка достижений: открытые — в цвете, закрытые — приглушены.</summary>
    Border? Achievements(HashSet<string> got)
    {
        if (account.Achievements.Count == 0) return null;
        var panel = new StackPanel();
        panel.Children.Add(Text($"Достижения · {got.Count} из {account.Achievements.Count}", 18, null, true));
        var wrap = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
        foreach (var a in account.Achievements.OfType<JsonObject>())
        {
            var open = got.Contains(AccountClient.Text(a, "id"));
            var cell = new StackPanel { Width = 112 };
            cell.Children.Add(new TextBlock { Text = AccountClient.Text(a, "glyph"), FontSize = 28, HorizontalAlignment = HorizontalAlignment.Center, Foreground = open ? Warm : new SolidColorBrush(Color.FromRgb(0x4A, 0x4B, 0x5A)), Opacity = open ? 1 : 0.6 });
            cell.Children.Add(new TextBlock { Text = AccountClient.Text(a, "title"), FontSize = 11, TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap, Foreground = open ? Brushes.WhiteSmoke : Muted, Margin = new Thickness(0, 6, 0, 0) });
            var reward = AccountClient.Text(a, "reward").Length > 0 ? " · украшение в награду" : "";
            var tip = $"{AccountClient.Text(a, "description")}" + (AccountClient.Int(a, "points") > 0 ? $" · +{AccountClient.Int(a, "points")} очков" : "") + reward + (open ? " · получено" : "");
            var border = new Border { Child = cell, Padding = new Thickness(6, 10, 6, 10), Margin = new Thickness(0, 0, 8, 8), CornerRadius = new CornerRadius(12), Background = open ? new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) : new SolidColorBrush(Color.FromRgb(0x21, 0x22, 0x2D)), ToolTip = tip };
            System.Windows.Automation.AutomationProperties.SetName(border, AccountClient.Text(a, "title") + ". " + tip);
            wrap.Children.Add(border);
        }
        panel.Children.Add(wrap);
        return Card(panel);
    }

    void RenderPublic(JsonObject? data)
    {
        Body.Children.Clear();
        var p = data?["profile"] as JsonObject;
        if (p == null) { Heading.Text = "Профиль не найден"; Body.Children.Add(Card(Text($"Пользователя @{viewing} нет — возможно, он сменил ник.", 14, Muted))); return; }
        var name = AccountClient.Text(p, "display_name").Trim() is { Length: > 0 } n ? n : AccountClient.Text(p, "username");
        Heading.Text = name;
        var hero = new StackPanel();
        var state = AccountClient.Text(data, "friendship");
        if (state != "self")
        {
            var friend = new Button { Content = state switch { "friend" => "✓ Друзья · удалить", "outgoing" => "Заявка отправлена", "incoming" => "Принять заявку в друзья", _ => "Добавить в друзья" }, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 14, 0, 0), IsEnabled = state != "outgoing" && account.SignedIn, Foreground = AccentBrush };
            var id = AccountClient.Text(p, "id"); var username = AccountClient.Text(p, "username");
            friend.Click += async (_, _) =>
            {
                JsonNode? result = null;
                await Run(async () => result = state switch
                {
                    "friend" => await account.Rpc("remove_friend", new JsonObject { ["p_user"] = id }),
                    "incoming" => await account.Rpc("respond_friend_request", new JsonObject { ["p_requester"] = id, ["p_accept"] = true }),
                    _ => await account.Rpc("send_friend_request", new JsonObject { ["p_username"] = username })
                }, async () => { await Reload(); StatusText.Text = AccountClient.Announce(result); });
            };
            hero.Children.Add(friend);
            if (state == "friend")
            {
                var gift = new Button { Content = "🎁  Подарить", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 10, 0, 0), Foreground = Warm };
                gift.Click += (_, _) => Shell.Navigate(new GiftPage(account, p));
                hero.Children.Add(gift);
            }
        }
        Body.Children.Add(ProfileCard(p, hero));
        var stats = new UniformGrid3();
        stats.Add(Rewards.Level(AccountClient.Int(p, "total_earned")).ToString(), "уровень", AccentBrush);
        stats.Add(AccountClient.Int(p, "episodes_watched").ToString(), "серий", new SolidColorBrush(Color.FromRgb(0x7E, 0xDC, 0xA9)));
        stats.Add("🔥 " + AccountClient.Int(p, "streak_days"), "дней подряд", new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x5B)));
        var lists = data?["lists"] as JsonObject;
        var info = new StackPanel(); info.Children.Add(stats.Grid);
        var line = string.Join("  ·  ", AccountClient.Lists.Select(l => $"{AccountClient.ListLabel(l)}: {AccountClient.Int(lists, l)}")) + $"  ·  Отзывов: {AccountClient.Int(data, "reviews")}";
        var listText = Text(line, 13, Muted); listText.Margin = new Thickness(0, 14, 0, 0); info.Children.Add(listText);
        Body.Children.Add(Card(info));
        if (p["showcase"] is JsonArray showcase && showcase.Count > 0)
        {
            var box = new StackPanel(); box.Children.Add(Text("Любимое аниме", 18, null, true));
            var row = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
            foreach (var item in showcase)
            {
                var cell = new StackPanel { Width = 116, Margin = new Thickness(0, 0, 14, 8) };
                var poster = new Border { Height = 160, CornerRadius = new CornerRadius(10), Background = new SolidColorBrush(Color.FromRgb(0x27, 0x24, 0x31)), ClipToBounds = true };
                if (Uri.TryCreate(AccountClient.Text(item, "poster"), UriKind.Absolute, out var uri) && uri.Scheme == "https") poster.Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill };
                cell.Children.Add(poster);
                cell.Children.Add(new TextBlock { Text = AccountClient.Text(item, "title"), FontSize = 12, Margin = new Thickness(0, 6, 0, 0), MaxHeight = 34, TextTrimming = TextTrimming.CharacterEllipsis, TextWrapping = TextWrapping.Wrap });
                row.Children.Add(cell);
            }
            box.Children.Add(row); Body.Children.Add(Card(box));
        }
        Body.Children.Add(Extras.StatsCard(account, AccountClient.Text(p, "username")));
        if (data?["favorites"] is JsonArray favorites && favorites.Count > 0)
        {
            var box = new StackPanel(); box.Children.Add(Text("Любимые", 18, null, true));
            var wrap = new WrapPanel { Margin = new Thickness(0, 12, 0, 0) };
            foreach (var f in favorites.Take(12)) wrap.Children.Add(Extras.PosterTile(AccountClient.Long(f, "anime_id"), AccountClient.Text(f, "title"), AccountClient.Text(f, "poster"), ""));
            box.Children.Add(wrap); Body.Children.Add(Card(box));
        }
        var theirs = (data?["achievements"] as JsonArray ?? new JsonArray()).Select(a => a?.ToString() ?? "").ToHashSet();
        if (Achievements(theirs) is { } card) Body.Children.Add(card);
    }

    /// <summary>Три плитки статистики в ряд.</summary>
    sealed class UniformGrid3
    {
        public System.Windows.Controls.Primitives.UniformGrid Grid { get; } = new() { Columns = 3 };
        public void Add(string value, string caption, Brush color)
        {
            var stack = new StackPanel();
            stack.Children.Add(new TextBlock { Text = value, FontSize = 22, FontWeight = FontWeights.Bold, Foreground = color });
            stack.Children.Add(new TextBlock { Text = caption, FontSize = 12, Foreground = Muted, Margin = new Thickness(0, 4, 0, 0) });
            Grid.Children.Add(new Border { Background = new SolidColorBrush(Color.FromRgb(0x21, 0x22, 0x2D)), CornerRadius = new CornerRadius(14), Padding = new Thickness(16, 12, 12, 12), Margin = new Thickness(0, 0, 10, 0), Child = stack });
        }
    }
}
