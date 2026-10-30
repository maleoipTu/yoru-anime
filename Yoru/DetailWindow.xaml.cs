using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
namespace Yoru;
public partial class DetailWindow : Window
{
    Anime anime;
    List<Episode> videos = [];
    readonly YummyClient api;
    readonly LocalStore store;
    readonly CancellationTokenSource cts = new();
    bool changingSelection;
    bool expanded;
    bool refreshOnActivate;
    public bool WantsSettings { get; private set; }
    readonly SocialPanel social;
    public DetailWindow(Anime item, YummyClient client, LocalStore local)
    {
        InitializeComponent(); WindowTheme.Apply(this); anime = item; api = client; store = local; Render();
        social = new SocialPanel(this, anime, store); ScrollBody.Children.Insert(0, social.Root);
        Loaded += async (_, _) => await social.Refresh();
        Activated += async (_, _) => { if (refreshOnActivate) { refreshOnActivate = false; await social.Refresh(); } };
        Loaded += async (_, _) => await LoadVideos();
        Closed += (_, _) => cts.Cancel();
    }
    void Render()
    {
        Title = anime.Title + " · Yoru"; AnimeTitle.Text = anime.Title;
        MetaText.Text = anime.Meta; ScoreText.Text = anime.Score; GenresText.Text = anime.Genres; AgeText.Text = anime.Age;
        DescriptionText.Text = anime.Description.Length > 0 ? anime.Description : "Описание пока не добавлено.";
        if (Uri.TryCreate(anime.ImagePath, UriKind.Absolute, out var uri)) PosterImage.Source = new BitmapImage(uri);
        UpdateFavorite();
    }
    void UpdateFavorite() => FavoriteButton.Content = store.Data.Favorites.Any(a => a.Id == anime.Id) ? "♥  В избранном" : "♡  В избранное";
    async Task LoadVideos()
    {
        if (string.IsNullOrWhiteSpace(api.Token))
        {
            VideoStatus.Text = "Это предпросмотр карточки. Для загрузки серий нужен ключ YummyAnime.";
            SetupButton.Visibility = Visibility.Visible; return;
        }
        VideoStatus.Text = "Загружаем серии и озвучки…"; RetryButton.Visibility = Visibility.Collapsed;
        try
        {
            var result = await api.Detail(anime.Id, cts.Token);
            if (cts.IsCancellationRequested) return;
            anime = result.Anime; social.Anime = anime; videos = result.Videos; Render();
            if (videos.Count == 0) { VideoStatus.Text = "Для этого аниме API не вернул доступных видео. Попробуй открыть его на сайте."; return; }
            VideoStatus.Text = $"Доступно серий: {videos.Select(v => v.Number).Distinct().Count()}. Выбери озвучку.";
            Selectors.Visibility = Visibility.Visible;
            WatchButton.Visibility = Visibility.Visible;
            TrackBox.ItemsSource = PlaybackSources.Dubbings(videos);
            var last = store.Data.History.FirstOrDefault(h => h.Anime.Id == anime.Id);
            TrackBox.SelectedItem = PlaybackSources.Resume(videos, last)?.Dubbing;
            if (TrackBox.SelectedIndex < 0) TrackBox.SelectedIndex = 0;
        }
        catch (OperationCanceledException) { if (!cts.IsCancellationRequested) Failed("Сервер не ответил вовремя. Попробуй ещё раз."); }
        catch (Exception ex) when (ex is HttpRequestException or InvalidOperationException or JsonException) { Failed(ex is HttpRequestException ? "Не удалось загрузить серии. Проверь интернет и попробуй ещё раз." : ex.Message); }
    }
    void Failed(string text) { VideoStatus.Text = text; RetryButton.Visibility = Visibility.Visible; }
    void TrackChanged(object sender, SelectionChangedEventArgs e)
    {
        if (changingSelection || TrackBox.SelectedItem is not string dubbing) return;
        var old = ProviderBox.SelectedItem as string;
        var last = PlaybackSources.Resume(videos, store.Data.History.FirstOrDefault(h => h.Anime.Id == anime.Id));
        changingSelection = true;
        ProviderBox.ItemsSource = PlaybackSources.Players(videos, dubbing);
        ProviderBox.SelectedItem = old ?? (last?.Dubbing == dubbing ? last.Player : null);
        if (ProviderBox.SelectedIndex < 0) ProviderBox.SelectedIndex = 0;
        changingSelection = false;
        UpdateEpisodes();
    }
    void ProviderChanged(object sender, SelectionChangedEventArgs e) { if (!changingSelection) UpdateEpisodes(); }
    void UpdateEpisodes()
    {
        if (TrackBox.SelectedItem is not string dubbing || ProviderBox.SelectedItem is not string player) return;
        var selectedNumber = (EpisodeBox.SelectedItem as Episode)?.Number;
        var trackVideos = PlaybackSources.Episodes(videos, dubbing, player);
        EpisodeBox.ItemsSource = trackVideos;
        var last = store.Data.History.FirstOrDefault(h => h.Anime.Id == anime.Id);
        EpisodeBox.SelectedItem = trackVideos.FirstOrDefault(v => v.Number == (selectedNumber ?? last?.Number)) ?? trackVideos.FirstOrDefault();
    }
    void FavoriteClick(object sender, RoutedEventArgs e) { store.ToggleFavorite(anime); UpdateFavorite(); }
    void SiteClick(object sender, RoutedEventArgs e) => MainWindow.OpenLink("https://yummyani.me/catalog/item/" + Uri.EscapeDataString(anime.Alias));
    void SetupClick(object sender, RoutedEventArgs e) { WantsSettings = true; Close(); }
    async void RetryClick(object sender, RoutedEventArgs e) => await LoadVideos();
    void WatchClick(object sender, RoutedEventArgs e)
    {
        if (EpisodeBox.SelectedItem is not Episode video) return;
        new PlayerWindow(anime, videos, video, store, AccountClient.Shared) { Owner = this }.ShowDialog();
        refreshOnActivate = true;
    }
    void ExpandClick(object sender, RoutedEventArgs e) { expanded = !expanded; DescriptionText.MaxHeight = expanded ? double.PositiveInfinity : 96; ExpandButton.Content = expanded ? "Свернуть описание ↑" : "Развернуть описание ↓"; }
    void CloseClick(object sender, RoutedEventArgs e) => Close();
}
