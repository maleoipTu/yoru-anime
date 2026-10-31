using System.Diagnostics;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>Навигация внутри одного окна: страницы открываются поверх каталога и закрываются кнопкой «Назад».</summary>
public static class Shell
{
    public static MainWindow? Main { get; set; }
    public static void Navigate(FrameworkElement page) => Main?.PushPage(page);
    public static void Back() => Main?.PopPage();
    public static void Home(string section = "catalog") => Main?.GoHome(section);
    public static void Title(string text) { if (Main != null) Main.Title = text.Length > 0 ? text + " · Yoru" : "Yoru"; }
    public static void Fullscreen(bool value) => Main?.SetFullscreen(value);
    public static YummyClient Api => Main!.Api;
    public static LocalStore Store => Main!.Store;
}

public partial class MainWindow : Window
{
    readonly LocalStore store = new();
    readonly YummyClient api = new();
    public YummyClient Api => api;
    public LocalStore Store => store;
    List<Anime> preview = [];
    List<Anime> displayed = [];
    string page = "catalog";
    string query = "";
    int offset;
    bool ready;
    bool busy;
    CancellationTokenSource? request;
    Anime? featured;
    bool IsPreview => string.IsNullOrWhiteSpace(api.Token);
    public MainWindow()
    {
        InitializeComponent(); WindowTheme.Apply(this); Shell.Main = this;
        var savedToken = store.ReadToken();
        api.Token = string.IsNullOrWhiteSpace(savedToken) ? AppConfig.DefaultApplicationToken : savedToken;
        TokenBox.Password = api.Token;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Assets", "preview.json")));
            preview = ApiParser.Catalog(doc.RootElement);
        }
        catch (Exception e) when (e is IOException or JsonException) { StatusText.Text = "Предпросмотр недоступен. Подключите API в настройках."; }
        featured = preview.FirstOrDefault(a => a.Title.Contains("Фрирен")) ?? preview.FirstOrDefault();
        if (featured != null)
        {
            HeroTitle.Text = featured.Title;
            HeroMeta.Text = featured.Score + "  ·  " + featured.Meta;
            HeroDescription.Text = featured.Description;
            if (Uri.TryCreate(featured.ImagePath, UriKind.Absolute, out var uri)) HeroImage.Source = new BitmapImage(uri);
        }
        SearchBox.TextChanged += (_, _) => { SearchHint.Visibility = SearchBox.Text.Length == 0 ? Visibility.Visible : Visibility.Collapsed; ScheduleSuggestions(); };
        suggestTimer.Tick += async (_, _) => { suggestTimer.Stop(); await ShowSuggestions(); };
        AccountClient.Shared.Changed += UpdateAuthGate; UpdateAuthGate();
        Loaded += (_, _) => { ready = true; CatalogNav.IsChecked = true; if (store.LoadWarning != null) StatusText.Text = store.LoadWarning; };
        Loaded += async (_, _) => await CheckUpdates();
        Loaded += (_, _) => { NotifyBox.IsChecked = store.Data.NotifyEpisodes; DiscordBox.IsChecked = store.Data.DiscordPresence; DiscordIdBox.Text = store.Data.DiscordClientId; EpisodeNotifier.Start(store); };
        Closed += (_, _) => EpisodeNotifier.Stop();
        Loaded += async (_, _) =>
        {
            AccountClient.Shared.Changed += UpdateProfileCard; UpdateProfileCard();
            if (AccountClient.Shared.SignedIn) try { await AccountClient.Shared.Refresh(); } catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or JsonException or InvalidOperationException) { UpdateProfileCard(); }
        };
        Closed += (_, _) => { request?.Cancel(); api.Dispose(); AccountClient.Shared.Changed -= UpdateProfileCard; AccountClient.Shared.Changed -= UpdateAuthGate; };
    }
    async void Navigate(object sender, RoutedEventArgs e)
    {
        if (!ready) return;
        ClearPages();
        page = (string)((RadioButton)sender).Tag;
        query = ""; SearchBox.Text = "";
        await LoadPage();
    }
    void CancelRequest()
    {
        request?.Cancel(); request?.Dispose();
        request = new CancellationTokenSource();
    }
    void UpdateConnection()
    {
        PreviewBanner.Visibility = IsPreview ? Visibility.Visible : Visibility.Collapsed;
        ConnectionBadge.Text = IsPreview ? "○  Предпросмотр" : "●  YummyAnime";
    }
    void SetBusy(bool value)
    {
        busy = value;
        BusyBar.Visibility = value ? Visibility.Visible : Visibility.Collapsed;
        MoreButton.IsEnabled = RefreshButton.IsEnabled = !value;
    }
    async Task LoadPage(bool append = false)
    {
        CancelRequest(); var ct = request!.Token;
        UpdateConnection();
        SettingsPage.Visibility = page == "settings" ? Visibility.Visible : Visibility.Collapsed;
        BrowseScroll.Visibility = page == "settings" ? Visibility.Collapsed : Visibility.Visible;
        var recent = store.Data.History.FirstOrDefault();
        ContinuePanel.Visibility = page == "catalog" && query.Length == 0 && recent != null ? Visibility.Visible : Visibility.Collapsed;
        Hero.Visibility = page == "catalog" && query.Length == 0 && recent == null && featured != null ? Visibility.Visible : Visibility.Collapsed;
        if (recent != null) { ContinueTitle.Text = recent.Anime.Title; ContinueMeta.Text = $"Серия {recent.Number} · {recent.Track}"; }
        Filters.Visibility = page is "catalog" or "favorites" ? Visibility.Visible : Visibility.Collapsed;
        SortBox.Visibility = StatusBox.Visibility = page == "catalog" ? Visibility.Visible : Visibility.Collapsed;
        ListPicker.Visibility = page == "favorites" ? Visibility.Visible : Visibility.Collapsed;
        MoreButton.Visibility = Visibility.Collapsed;
        StatusText.Text = "";
        SetBusy(false);
        if (page == "settings") return;
        if (!append) { offset = 0; displayed = []; Cards.ItemsSource = null; BrowseScroll.ScrollToTop(); }
        PageTitle.Text = page switch { "favorites" => ListPicker.SelectedItem is ComboBoxItem { Tag: string l } && l.Length > 0 ? "Список: " + AccountClient.ListLabel(l) : "Твои любимые истории", "history" => "Вернись к своей истории", "schedule" => "Когда следующая серия?", _ => query.Length > 0 ? $"Поиск: {query}" : "Найди свою следующую историю" };
        PageSubtitle.Text = page switch { "favorites" => "Всё, что хочется сохранить на потом", "history" => "Последние открытые серии · запоминаем серию и озвучку", "schedule" => "Ближайшие серии · время указано для этого компьютера", _ => IsPreview ? "Знакомство с интерфейсом · образец каталога YummyAnime" : "Большие миры начинаются с одной серии" };
        try
        {
            SetBusy(true);
            List<Anime> items;
            var list = (string)((ComboBoxItem)ListPicker.SelectedItem).Tag;
            if (page == "favorites" && list.Length > 0)
            {
                if (!AccountClient.Shared.SignedIn) { ShowCards([]); EmptyText.Text = "Войди в профиль (карточка слева), чтобы видеть свои списки."; return; }
                items = await AccountClient.Shared.List(list);
            }
            else if (page == "favorites") items = store.Data.Favorites.ToList();
            else if (page == "history") items = store.Data.History.Select(h => h.Anime with { DisplayNote = $"Серия {h.Number} · {h.Track}" }).ToList();
            else if (page == "schedule")
            {
                if (IsPreview) { ShowCards([]); EmptyText.Text = "Подключи API в настройках, чтобы увидеть расписание."; return; }
                items = (await api.Schedule(ct)).OrderBy(a => a.NextEpisode > 0 ? a.NextEpisode : long.MaxValue).ToList();
                items = items.Select(a => a with { DisplayNote = a.ScheduleText }).ToList();
            }
            else if (IsPreview)
            {
                var status = (string)((ComboBoxItem)StatusBox.SelectedItem).Tag;
                IEnumerable<Anime> selection = preview.Where(a => a.Title.Contains(query, StringComparison.OrdinalIgnoreCase) && (status.Length == 0 || a.StatusAlias == status));
                var sort = (string)((ComboBoxItem)SortBox.SelectedItem).Tag;
                selection = sort switch { "year" => selection.OrderByDescending(a => a.Year), "random" => selection.OrderBy(_ => Random.Shared.Next()), _ => selection.OrderByDescending(a => a.Rating) };
                items = selection.ToList();
            }
            else
            {
                var rawItems = await api.Catalog(query, (string)((ComboBoxItem)SortBox.SelectedItem).Tag, (string)((ComboBoxItem)StatusBox.SelectedItem).Tag, offset, ct, FilterQuery());
                items = filterAge >= 0 ? rawItems.Where(a => a.AgeValue == filterAge).ToList() : rawItems;
                MoreButton.Visibility = rawItems.Count == 24 ? Visibility.Visible : Visibility.Collapsed;
                offset += rawItems.Count - items.Count;
                ct.ThrowIfCancellationRequested();
                offset += items.Count;
            }
            ct.ThrowIfCancellationRequested();
            if (page is "favorites" or "history") items = items.Where(a => a.Title.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
            displayed = append ? displayed.Concat(items).DistinctBy(a => a.Id).ToList() : items;
            ShowCards(displayed);
            if (IsPreview && page == "catalog") StatusText.Text = "Предпросмотр: поиск работает только по загруженному образцу. Для полного каталога и видео подключите свой ключ.";
        }
        catch (OperationCanceledException) { if (!ct.IsCancellationRequested) StatusText.Text = "Сервер не ответил вовремя. Нажмите «Обновить»."; }
        catch (AccountException ex) { ShowCards(displayed); StatusText.Text = Rewards.Error(ex.Message); }
        catch (Exception ex) when (ex is HttpRequestException or JsonException or InvalidOperationException)
        {
            ShowCards(displayed);
            if (displayed.Count == 0) EmptyText.Text = "Не получилось загрузить данные. Можно повторить запрос.";
            StatusText.Text = ex is HttpRequestException ? "Не удалось связаться с YummyAnime. Проверьте подключение и попробуйте снова." : ex.Message;
        }
        finally { if (!ct.IsCancellationRequested) SetBusy(false); }
    }
    void ShowCards(List<Anime> items)
    {
        Cards.ItemsSource = items;
        ResultCount.Text = $"Показано: {items.Count}";
        EmptyState.Visibility = items.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        EmptyText.Text = page switch { "favorites" => "Здесь появится избранное. Открой карточку и нажми «В избранное».", "history" => "Здесь появятся серии, которые ты откроешь в плеере.", _ => "Ничего не нашлось. Попробуй другое название или статус." };
    }
    async void SearchClick(object sender, RoutedEventArgs e) => await Search();
    async void SearchKey(object sender, KeyEventArgs e) { if (e.Key == Key.Enter) { e.Handled = true; await Search(); } }
    async Task Search()
    {
        SuggestPopup.IsOpen = false; suggestTimer.Stop(); suggestCts?.Cancel();
        var requestedQuery = SearchBox.Text.Trim();
        if (page is "settings" or "schedule") { page = "catalog"; CatalogNav.IsChecked = true; }
        query = requestedQuery; SearchBox.Text = requestedQuery; await LoadPage();
    }
    async void FilterChanged(object sender, SelectionChangedEventArgs e) { if (ready && page == "catalog") await LoadPage(); }
    async void ListChanged(object sender, SelectionChangedEventArgs e) { if (ready && page == "favorites") await LoadPage(); }
    async void RefreshClick(object sender, RoutedEventArgs e) => await LoadPage();
    async void MoreClick(object sender, RoutedEventArgs e) { if (!busy) await LoadPage(true); }
    void SettingsClick(object sender, RoutedEventArgs e) => SettingsNav.IsChecked = true;
    void RouletteClick(object sender, RoutedEventArgs e) => PushPage(new RoulettePage(AccountClient.Shared));
    void ForYouClick(object sender, RoutedEventArgs e) => PushPage(new ForYouPage(AccountClient.Shared));
    void SocialSettingsChanged(object sender, RoutedEventArgs e)
    {
        store.Data.NotifyEpisodes = NotifyBox.IsChecked == true; store.Data.DiscordPresence = DiscordBox.IsChecked == true;
        var id = DiscordIdBox.Text.Trim(); store.Data.DiscordClientId = System.Text.RegularExpressions.Regex.IsMatch(id, "^[0-9]{15,22}$") ? id : "";
        try { store.Save(); } catch (IOException) { }
        EpisodeNotifier.Start(store);
    }
    void DeveloperClick(object sender, RoutedEventArgs e) => OpenLink("https://yummyani.me/dev/applications");
    public static void OpenLink(string url) => Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    async void ConnectClick(object sender, RoutedEventArgs e)
    {
        var token = TokenBox.Password.Trim();
        if (token.Length == 0 || token.Contains('\r') || token.Contains('\n')) { SettingsResult.Text = "Вставь ключ приложения без переносов строк."; return; }
        ConnectButton.IsEnabled = false; SettingsResult.Text = "Проверяем подключение…";
        try
        {
            using var candidate = new YummyClient { Token = token };
            var root = await candidate.Get("anime?limit=1");
            if (ApiParser.Field(root, "response").ValueKind != JsonValueKind.Array) throw new InvalidOperationException("Сервер вернул неожиданный ответ. Ключ не сохранён.");
            store.SaveToken(token); api.Token = token;
            SettingsResult.Text = "Подключено. Ключ сохранён на этом компьютере.";
            UpdateConnection(); CatalogNav.IsChecked = true;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or InvalidOperationException or IOException or System.Security.Cryptography.CryptographicException or JsonException)
        { SettingsResult.Text = ex is HttpRequestException or TaskCanceledException ? "Не удалось проверить ключ: сервер недоступен. Проверь подключение и повтори попытку." : ex.Message; }
        finally { ConnectButton.IsEnabled = true; }
    }
    /// <summary>Карточка профиля в боковой панели: аватар с рамкой, ник и очки — или приглашение войти.</summary>
    void UpdateProfileCard()
    {
        var account = AccountClient.Shared;
        if (!account.SignedIn)
        {
            ProfileAvatar.Content = Decor.Avatar(44, new System.Text.Json.Nodes.JsonObject(), "☾", "");
            ProfileName.Text = account.Config.Configured ? "Войти в аккаунт" : "Профиль";
            ProfilePoints.Text = "Очки и украшения профиля";
            return;
        }
        var p = account.Profile;
        ProfileAvatar.Content = Decor.Avatar(44, account.Style(AccountClient.Text(p, "equipped_frame") is { Length: > 0 } f ? f : "frame_moon"), account.DisplayName, AccountClient.Text(p, "avatar_url"));
        ProfileName.Text = account.DisplayName;
        ProfileName.Foreground = Decor.NameBrush(account.Style(AccountClient.Text(p, "equipped_color") is { Length: > 0 } c ? c : "color_default"));
        ProfilePoints.Text = $"◆ {Rewards.Points(AccountClient.Int(p, "points"))} · уровень {Rewards.Level(AccountClient.Int(p, "total_earned"))}";
    }
    void ProfileClick(object sender, RoutedEventArgs e) { ClearPages(); PushPage(new ProfilePage(AccountClient.Shared, store)); }
    void CardClick(object sender, RoutedEventArgs e) { if (((Button)sender).DataContext is Anime anime) OpenAnime(anime); }
    void HeroClick(object sender, RoutedEventArgs e) { if (featured != null) OpenAnime(featured); }
    void ContinueClick(object sender, RoutedEventArgs e) { var recent = store.Data.History.FirstOrDefault(); if (recent != null) OpenAnime(recent.Anime); }
    void OpenAnime(Anime anime) { SuggestPopup.IsOpen = false; PushPage(new DetailPage(anime, api, store)); }

    // ───── Страницы внутри окна ─────
    readonly List<FrameworkElement> pages = [];
    public void PushPage(FrameworkElement page)
    {
        if (pages.Count > 0) pages[^1].Visibility = Visibility.Collapsed;
        pages.Add(page); PageHost.Children.Add(page);
        PageHost.Visibility = Visibility.Visible; BrowseRoot.Visibility = Visibility.Collapsed;
        page.Focus();
    }
    public void PopPage()
    {
        if (pages.Count == 0) return;
        var top = pages[^1]; pages.RemoveAt(pages.Count - 1); PageHost.Children.Remove(top);
        if (pages.Count > 0) { pages[^1].Visibility = Visibility.Visible; return; }
        PageHost.Visibility = Visibility.Collapsed; BrowseRoot.Visibility = Visibility.Visible; Title = "Yoru — аниме для Windows";
        UpdateProfileCard();
        if (page == "favorites") _ = LoadPage();
        if (page == "history") { displayed = store.Data.History.Select(h => h.Anime with { DisplayNote = $"Серия {h.Number} · {h.Track}" }).ToList(); ShowCards(displayed); }
    }
    void ClearPages() { while (pages.Count > 0) PopPage(); }
    public void GoHome(string section) { ClearPages(); if (section == "settings") SettingsNav.IsChecked = true; else CatalogNav.IsChecked = true; }
    WindowState stateBeforeFullscreen;
    public void SetFullscreen(bool value)
    {
        Sidebar.Visibility = value ? Visibility.Collapsed : Visibility.Visible;
        SideColumn.Width = new GridLength(value ? 0 : 232);
        if (value) { stateBeforeFullscreen = WindowState; WindowState = WindowState.Normal; WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.NoResize; WindowState = WindowState.Maximized; }
        else { WindowState = WindowState.Normal; WindowStyle = WindowStyle.SingleBorderWindow; ResizeMode = ResizeMode.CanResize; WindowState = stateBeforeFullscreen; }
    }

    // ───── Вход обязателен ─────
    AuthView? auth;
    void UpdateAuthGate()
    {
        var account = AccountClient.Shared;
        var locked = account.Config.Configured && !account.SignedIn;
        if (locked)
        {
            ClearPages();
            auth ??= new AuthView(account, async () => { UpdateAuthGate(); UpdateProfileCard(); try { await account.Refresh(); } catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or JsonException or InvalidOperationException) { } });
            AuthHost.Content = auth; AuthHost.Visibility = Visibility.Visible; auth.FocusFirst();
        }
        else { AuthHost.Visibility = Visibility.Collapsed; AuthHost.Content = null; auth = null; }
    }

    // ───── Подсказки при вводе ─────
    readonly System.Windows.Threading.DispatcherTimer suggestTimer = new() { Interval = TimeSpan.FromMilliseconds(300) };
    CancellationTokenSource? suggestCts;
    void ScheduleSuggestions()
    {
        suggestTimer.Stop();
        if (SearchBox.Text.Trim().Length < 2 || IsPreview || !SearchBox.IsKeyboardFocusWithin) { SuggestPopup.IsOpen = false; return; }
        suggestTimer.Start();
    }
    async Task ShowSuggestions()
    {
        var text = SearchBox.Text.Trim(); if (text.Length < 2 || text == query) return;
        suggestCts?.Cancel(); var cts = suggestCts = new CancellationTokenSource();
        try
        {
            var found = await api.Suggest(text, cts.Token);
            if (cts.IsCancellationRequested || SearchBox.Text.Trim() != text) return;
            SuggestList.Children.Clear();
            foreach (var anime in found)
            {
                var row = new DockPanel();
                var poster = new Border { Width = 40, Height = 56, CornerRadius = new CornerRadius(6), ClipToBounds = true, Background = new SolidColorBrush(Color.FromRgb(0x1F, 0x1F, 0x26)) };
                if (Uri.TryCreate(anime.ImagePath, UriKind.Absolute, out var uri)) poster.Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill };
                DockPanel.SetDock(poster, Dock.Left); row.Children.Add(poster);
                var text2 = new StackPanel { Margin = new Thickness(12, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
                text2.Children.Add(new TextBlock { Text = anime.Title, FontWeight = FontWeights.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis, TextWrapping = TextWrapping.NoWrap });
                text2.Children.Add(new TextBlock { Text = (anime.Rating > 0 ? anime.Score + "  ·  " : "") + anime.Meta, FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)), Margin = new Thickness(0, 3, 0, 0), TextWrapping = TextWrapping.NoWrap, TextTrimming = TextTrimming.CharacterEllipsis });
                row.Children.Add(text2);
                var button = new Button { Content = row, Background = Brushes.Transparent, HorizontalContentAlignment = HorizontalAlignment.Stretch, Padding = new Thickness(8, 6, 8, 6) };
                System.Windows.Automation.AutomationProperties.SetName(button, anime.Title);
                button.Click += (_, _) => OpenAnime(anime);
                SuggestList.Children.Add(button);
            }
            SuggestPopup.IsOpen = SuggestList.Children.Count > 0 && SearchBox.IsKeyboardFocusWithin;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or InvalidOperationException or JsonException) { SuggestPopup.IsOpen = false; }
    }

    // ───── Фильтры: жанры, годы, возрастной рейтинг ─────
    readonly HashSet<int> filterGenres = []; int filterFrom, filterTo, filterAge = -1; List<(int Id, string Title)>? genreList;
    string FilterQuery() => (filterGenres.Count > 0 ? "&genres=" + string.Join(",", filterGenres) : "") + (filterFrom > 0 ? $"&from_year={filterFrom}" : "") + (filterTo > 0 ? $"&to_year={filterTo}" : "");
    async void FiltersClick(object sender, RoutedEventArgs e)
    {
        if (genreList == null) try { genreList = await api.Genres(default); } catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or InvalidOperationException or JsonException) { genreList = []; }
        var chosen = new HashSet<int>(filterGenres);
        var panel = new StackPanel { Width = 560 };
        panel.Children.Add(new TextBlock { Text = "Жанры", FontSize = 15, FontWeight = FontWeights.SemiBold });
        var wrap = new WrapPanel { Margin = new Thickness(0, 10, 0, 0) };
        foreach (var (id, title) in genreList)
        {
            var toggle = new ToggleButton { Content = title, IsChecked = chosen.Contains(id), Margin = new Thickness(0, 0, 6, 6), Padding = new Thickness(12, 6, 12, 6), Style = (Style)FindResource("Chip") };
            toggle.Checked += (_, _) => chosen.Add(id); toggle.Unchecked += (_, _) => chosen.Remove(id);
            wrap.Children.Add(toggle);
        }
        if (genreList.Count == 0) wrap.Children.Add(new TextBlock { Text = "Жанры не загрузились — годы и рейтинг работают.", Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)) });
        panel.Children.Add(new ScrollViewer { Content = wrap, MaxHeight = 220 });
        var years = Enumerable.Range(1960, DateTime.Now.Year - 1958).Reverse().Select(y => y.ToString()).Prepend("Любой").ToList();
        var from = new ComboBox { ItemsSource = years, SelectedItem = filterFrom > 0 ? filterFrom.ToString() : "Любой", Margin = new Thickness(0, 0, 12, 0) };
        var to = new ComboBox { ItemsSource = years, SelectedItem = filterTo > 0 ? filterTo.ToString() : "Любой" };
        var ages = new[] { "Любой", "0+", "6+", "12+", "16+", "18+" };
        var age = new ComboBox { ItemsSource = ages, SelectedItem = filterAge < 0 ? "Любой" : filterAge + "+" , Margin = new Thickness(12, 0, 0, 0) };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 0) };
        foreach (var (label, box) in new (string, ComboBox)[] { ("С года", from), ("По год", to), ("Рейтинг", age) })
        { var col = new StackPanel(); col.Children.Add(new TextBlock { Text = label, FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)), Margin = new Thickness(0, 0, 0, 6) }); col.Children.Add(box); row.Children.Add(col); }
        panel.Children.Add(row);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 18, 0, 0) };
        var apply = new Button { Content = "Показать", Style = (Style)FindResource("Primary") }; var reset = new Button { Content = "Сбросить", Margin = new Thickness(10, 0, 0, 0) };
        actions.Children.Add(apply); actions.Children.Add(reset); panel.Children.Add(actions);
        var popup = new Popup { PlacementTarget = FilterButton, Placement = PlacementMode.Bottom, StaysOpen = false, AllowsTransparency = true,
            Child = new Border { Background = new SolidColorBrush(Color.FromRgb(0x16, 0x16, 0x1B)), BorderBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(14), Padding = new Thickness(18), Margin = new Thickness(0, 8, 16, 16), Child = panel, Effect = (System.Windows.Media.Effects.Effect)FindResource("Shadow") } };
        int Parse(object? v) => int.TryParse(v as string, out var y) ? y : 0;
        apply.Click += async (_, _) =>
        {
            filterGenres.Clear(); filterGenres.UnionWith(chosen); filterFrom = Parse(from.SelectedItem); filterTo = Parse(to.SelectedItem);
            if (filterFrom > 0 && filterTo > 0 && filterFrom > filterTo) (filterFrom, filterTo) = (filterTo, filterFrom);
            filterAge = age.SelectedItem is string a && a.EndsWith('+') ? int.Parse(a.TrimEnd('+')) : -1;
            popup.IsOpen = false; UpdateFilterButton(); await LoadPage();
        };
        reset.Click += async (_, _) => { filterGenres.Clear(); filterFrom = filterTo = 0; filterAge = -1; popup.IsOpen = false; UpdateFilterButton(); await LoadPage(); };
        popup.IsOpen = true;
    }
    void UpdateFilterButton()
    {
        var n = (filterGenres.Count > 0 ? 1 : 0) + (filterFrom > 0 || filterTo > 0 ? 1 : 0) + (filterAge >= 0 ? 1 : 0);
        FilterButton.Content = n == 0 ? "⚲  Фильтры" : $"⚲  Фильтры · {n}";
        FilterButton.Background = n == 0 ? (Brush)FindResource("ButtonBackground") : new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20));
    }

    // ───── Автообновление ─────
    Updater.Release? update;
    async Task CheckUpdates()
    {
        update = await Updater.Check();
        if (update == null) return;
        UpdateTitle.Text = $"Доступна версия {update.Version}";
        UpdateText.Text = Updater.Installed ? "Обновление установится и Yoru перезапустится." : "Установим Yoru с автообновлением; переносная папка останется как есть.";
        UpdateBanner.Visibility = Visibility.Visible;
    }
    async void UpdateClick(object sender, RoutedEventArgs e)
    {
        if (update == null) return;
        UpdateButton.IsEnabled = false; UpdateText.Text = "Скачиваем установщик…";
        try { await Updater.Install(update, p => UpdateText.Text = $"Скачиваем установщик… {p:P0}"); Application.Current.Shutdown(); }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or InvalidOperationException or IOException or System.ComponentModel.Win32Exception)
        { UpdateText.Text = ex is InvalidOperationException ? ex.Message : "Не получилось скачать обновление. Попробуйте позже."; UpdateButton.IsEnabled = true; }
    }
    void UpdateLaterClick(object sender, RoutedEventArgs e) { if (update != null) Updater.Skip(update.Version); UpdateBanner.Visibility = Visibility.Collapsed; }
}
