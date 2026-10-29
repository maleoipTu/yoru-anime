using System.Diagnostics;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
namespace Yoru;

public partial class MainWindow : Window
{
    readonly LocalStore store = new();
    readonly YummyClient api = new();
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
        InitializeComponent(); WindowTheme.Apply(this);
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
        SearchBox.TextChanged += (_, _) => SearchHint.Visibility = SearchBox.Text.Length == 0 ? Visibility.Visible : Visibility.Collapsed;
        Loaded += (_, _) => { ready = true; CatalogNav.IsChecked = true; if (store.LoadWarning != null) StatusText.Text = store.LoadWarning; };
        Closed += (_, _) => { request?.Cancel(); api.Dispose(); };
    }
    async void Navigate(object sender, RoutedEventArgs e)
    {
        if (!ready) return;
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
        Filters.Visibility = page == "catalog" ? Visibility.Visible : Visibility.Collapsed;
        MoreButton.Visibility = Visibility.Collapsed;
        StatusText.Text = "";
        SetBusy(false);
        if (page == "settings") return;
        if (!append) { offset = 0; displayed = []; Cards.ItemsSource = null; BrowseScroll.ScrollToTop(); }
        PageTitle.Text = page switch { "favorites" => "Твои любимые истории", "history" => "Вернись к своей истории", "schedule" => "Когда следующая серия?", _ => query.Length > 0 ? $"Поиск: {query}" : "Найди свою следующую историю" };
        PageSubtitle.Text = page switch { "favorites" => "Всё, что хочется сохранить на потом", "history" => "Последние открытые серии · запоминаем серию и озвучку", "schedule" => "Ближайшие серии · время указано для этого компьютера", _ => IsPreview ? "Знакомство с интерфейсом · образец каталога YummyAnime" : "Большие миры начинаются с одной серии" };
        try
        {
            SetBusy(true);
            List<Anime> items;
            if (page == "favorites") items = store.Data.Favorites.ToList();
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
                items = await api.Catalog(query, (string)((ComboBoxItem)SortBox.SelectedItem).Tag, (string)((ComboBoxItem)StatusBox.SelectedItem).Tag, offset, ct);
                ct.ThrowIfCancellationRequested();
                offset += items.Count;
                MoreButton.Visibility = items.Count == 24 ? Visibility.Visible : Visibility.Collapsed;
            }
            ct.ThrowIfCancellationRequested();
            if (page is "favorites" or "history") items = items.Where(a => a.Title.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
            displayed = append ? displayed.Concat(items).DistinctBy(a => a.Id).ToList() : items;
            ShowCards(displayed);
            if (IsPreview && page == "catalog") StatusText.Text = "Предпросмотр: поиск работает только по загруженному образцу. Для полного каталога и видео подключите свой ключ.";
        }
        catch (OperationCanceledException) { if (!ct.IsCancellationRequested) StatusText.Text = "Сервер не ответил вовремя. Нажмите «Обновить»."; }
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
        var requestedQuery = SearchBox.Text.Trim();
        if (page is "settings" or "schedule") { page = "catalog"; CatalogNav.IsChecked = true; }
        query = requestedQuery; SearchBox.Text = requestedQuery; await LoadPage();
    }
    async void FilterChanged(object sender, SelectionChangedEventArgs e) { if (ready && page == "catalog") await LoadPage(); }
    async void RefreshClick(object sender, RoutedEventArgs e) => await LoadPage();
    async void MoreClick(object sender, RoutedEventArgs e) { if (!busy) await LoadPage(true); }
    void SettingsClick(object sender, RoutedEventArgs e) => SettingsNav.IsChecked = true;
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
    void CardClick(object sender, RoutedEventArgs e) { if (((Button)sender).DataContext is Anime anime) OpenAnime(anime); }
    void HeroClick(object sender, RoutedEventArgs e) { if (featured != null) OpenAnime(featured); }
    void ContinueClick(object sender, RoutedEventArgs e) { var recent = store.Data.History.FirstOrDefault(); if (recent != null) OpenAnime(recent.Anime); }
    void OpenAnime(Anime anime)
    {
        var detail = new DetailWindow(anime, api, store) { Owner = this };
        detail.ShowDialog();
        if (page == "favorites") { displayed = store.Data.Favorites.ToList(); ShowCards(displayed); }
        if (page == "history") { displayed = store.Data.History.Select(h => h.Anime with { DisplayNote = $"Серия {h.Number} · {h.Track}" }).ToList(); ShowCards(displayed); }
        if (detail.WantsSettings) SettingsNav.IsChecked = true;
    }
}
