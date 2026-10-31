using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
namespace Yoru;
public partial class DetailPage : UserControl
{
    Anime anime;
    List<Episode> videos = [];
    readonly YummyClient api;
    readonly LocalStore store;
    readonly CancellationTokenSource cts = new();
    bool changingSelection;
    bool expanded;
    bool refreshOnActivate;
    Episode? preferred;
    public bool WantsSettings { get; private set; }
    readonly SocialPanel social;
    public DetailPage(Anime item, YummyClient client, LocalStore local)
    {
        InitializeComponent(); anime = item; api = client; store = local; Render();
        social = new SocialPanel(this, anime, store); ScrollBody.Children.Insert(0, social.Root);
        Loaded += async (_, _) => await social.Refresh();
        IsVisibleChanged += async (_, e) => { if (e.NewValue is true && refreshOnActivate) { refreshOnActivate = false; await social.Refresh(); } };
        Loaded += async (_, _) => await LoadVideos();
        Loaded += (_, _) => Progress.Changed += ProgressChanged;
        Unloaded += (_, _) => { cts.Cancel(); Progress.Changed -= ProgressChanged; };
        IsVisibleChanged += (_, e) => { if (e.NewValue is true) { RenderEpisodes(); RenderFranchise(); } };
    }
    void Render()
    {
        Shell.Title(anime.Title); AnimeTitle.Text = anime.Title;
        MetaText.Text = anime.Meta; ScoreText.Text = anime.Score; GenresText.Text = anime.Genres; AgeText.Text = anime.Age;
        DescriptionText.Text = anime.Description.Length > 0 ? anime.Description : "Описание пока не добавлено.";
        if (Uri.TryCreate(anime.ImagePath, UriKind.Absolute, out var uri)) PosterImage.Source = new BitmapImage(uri);
        UpdateFavorite();
        StudioChips.Children.Clear();
        foreach (var studio in anime.Studios)
        {
            var chip = new Button { Content = "Студия: " + studio.Title, Padding = new Thickness(12, 5, 12, 5), Margin = new Thickness(0, 0, 6, 6), FontSize = 12, ToolTip = "Показать все аниме студии" };
            chip.Click += (_, _) => Shell.Main?.ShowStudio(studio);
            StudioChips.Children.Add(chip);
        }
        RenderFranchise();
    }
    void ProgressChanged(long id) { if (id == 0 || id == anime.Id || anime.Franchise.Any(f => f.AnimeId == id)) Dispatcher.Invoke(() => { RenderEpisodes(); RenderFranchise(); }); }
    void RenderFranchise() => FranchiseHost.Content = EpisodeGrid.Franchise(anime, store, part => Shell.Navigate(new DetailPage(new Anime { Id = part.AnimeId, Title = part.Title, Poster = part.Poster }, api, store)), RenderFranchise);
    /// <summary>Плитки серий выбранной озвучки и плеера с отметками «просмотрено».</summary>
    void RenderEpisodes()
    {
        if (EpisodeBox.ItemsSource is not List<Episode> list || list.Count == 0) { EpisodesSection.Visibility = Visibility.Collapsed; return; }
        var progress = Progress.Get(store, anime.Id);
        EpisodesSection.Visibility = Visibility.Visible;
        var seen = list.Count(v => progress?.IsWatched(v.Number) == true);
        WatchedText.Text = $"просмотрено {seen} из {list.Count} · луна в углу или правый клик — отметить вручную";
        EpisodeTiles.Content = EpisodeGrid.Build(list, progress, (EpisodeBox.SelectedItem as Episode)?.Number,
            episode => { EpisodeBox.SelectedItem = episode; WatchClick(this, new RoutedEventArgs()); },
            episode => { Progress.Toggle(store, anime, episode.Number); RenderEpisodes(); RenderFranchise(); });
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
            anime = result.Anime; social.Anime = anime; videos = result.Videos; Render(); _ = AccountClient.Shared.NoteAnime(anime);
            await Progress.PullAnime(store, anime.Id);
            if (cts.IsCancellationRequested) return;
            if (videos.Count == 0) { VideoStatus.Text = "Для этого аниме API не вернул доступных видео. Попробуй открыть его на сайте."; return; }
            VideoStatus.Text = $"Доступно серий: {videos.Select(v => v.Number).Distinct().Count()}. Выбери озвучку.";
            Selectors.Visibility = Visibility.Visible;
            WatchButton.Visibility = Visibility.Visible;
            TrackBox.ItemsSource = PlaybackSources.Dubbings(videos);
            // Любимая озвучка этого тайтла (синхронизируется между устройствами), затем — из истории.
            preferred = Progress.Preferred(videos, Progress.Get(store, anime.Id), store.Data.History.FirstOrDefault(h => h.Anime.Id == anime.Id));
            TrackBox.SelectedItem = preferred?.Dubbing;
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
        var last = preferred;
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
        EpisodeBox.SelectedItem = trackVideos.FirstOrDefault(v => v.Number == (selectedNumber ?? preferred?.Number)) ?? trackVideos.FirstOrDefault();
        RenderEpisodes();
    }
    void EpisodeChanged(object sender, SelectionChangedEventArgs e) { if (!changingSelection) RenderEpisodes(); }
    void FavoriteClick(object sender, RoutedEventArgs e) { store.ToggleFavorite(anime); UpdateFavorite(); }
    void SiteClick(object sender, RoutedEventArgs e) => MainWindow.OpenLink("https://yummyani.me/catalog/item/" + Uri.EscapeDataString(anime.Alias));
    void SetupClick(object sender, RoutedEventArgs e) { WantsSettings = true; Shell.Home("settings"); }
    async void RetryClick(object sender, RoutedEventArgs e) => await LoadVideos();
    void WatchClick(object sender, RoutedEventArgs e)
    {
        if (EpisodeBox.SelectedItem is not Episode video) return;
        Shell.Navigate(new PlayerPage(anime, videos, video, store, AccountClient.Shared));
        refreshOnActivate = true;
    }
    void ExpandClick(object sender, RoutedEventArgs e) { expanded = !expanded; DescriptionText.MaxHeight = expanded ? double.PositiveInfinity : 96; ExpandButton.Content = expanded ? "Свернуть описание ↑" : "Развернуть описание ↓"; }
    void CloseClick(object sender, RoutedEventArgs e) => Shell.Back();
}
