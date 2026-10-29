using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;
namespace Yoru;
public partial class PlayerWindow : Window
{
    const string Host = "player.yoru.local";
    readonly Anime anime;
    readonly List<Episode> videos;
    readonly LocalStore store;
    Episode current;
    List<Episode> playlist = [];
    bool initialized, initializing, closed, fullscreen, changing, sourceFailed, recreateBrowser;
    string navigationSession = "";
    WindowState previousState;
    public event Action<string>? PlaybackStateChanged;
    public PlayerWindow(Anime item, List<Episode> list, Episode selected, LocalStore local)
    {
        InitializeComponent(); WindowTheme.Apply(this);
        anime=item; videos=list; current=selected; store=local;
        VoiceBox.ItemsSource=PlaybackSources.Dubbings(videos);
        SyncSelection(selected);
        SeriesBox.SelectionChanged += (_,_) => { if(!changing) UpdateSelectionHint(); };
        Loaded += async (_, _) => await InitializePlayer();
        Closed += (_, _) => { closed=true; Browser.Dispose(); };
    }
    void Status(string message, string state)
    {
        PlayerStatus.Text=message; PlaybackStateChanged?.Invoke(state);
    }
    async Task InitializePlayer()
    {
        if(initializing || closed) return;
        initializing=true;
        Status("Запускаем встроенный плеер…","initializing");
        try
        {
            if(recreateBrowser)
            {
                var old=Browser;
                VideoSurface.Children.Remove(old); old.Dispose();
                Browser=new Microsoft.Web.WebView2.Wpf.WebView2 { DefaultBackgroundColor=System.Drawing.Color.FromArgb(8,8,13) };
                VideoSurface.Children.Insert(0,Browser); recreateBrowser=false;
            }
            var environment=await CoreWebView2Environment.CreateAsync(null,Path.Combine(store.Root,"Player"));
            if(closed) return;
            await Browser.EnsureCoreWebView2Async(environment);
            if(closed) return;
            var core=Browser.CoreWebView2;
            core.SetVirtualHostNameToFolderMapping(Host, Path.Combine(AppContext.BaseDirectory,"Assets","PlayerHost"), CoreWebView2HostResourceAccessKind.DenyCors);
            core.Settings.AreDefaultContextMenusEnabled=false;
            core.Settings.AreDevToolsEnabled=false;
            core.Settings.IsStatusBarEnabled=false;
            core.Settings.AreHostObjectsAllowed=false;
            core.Settings.IsPasswordAutosaveEnabled=false;
            core.Settings.IsGeneralAutofillEnabled=false;
            core.NewWindowRequested += (_,e) => e.Handled=true;
            core.DownloadStarting += (_,e) => e.Cancel=true;
            core.PermissionRequested += (_,e) => e.State=CoreWebView2PermissionState.Deny;
            core.NavigationStarting += (_,e) =>
            {
                if(!Uri.TryCreate(e.Uri,UriKind.Absolute,out var uri) || uri.Scheme!="https" || uri.Host!=Host) e.Cancel=true;
            };
            core.FrameNavigationStarting += (_,e) =>
            {
                if(e.Uri!="about:blank" && (!Uri.TryCreate(e.Uri,UriKind.Absolute,out var uri) || uri.Scheme!="https")) e.Cancel=true;
            };
            core.FrameCreated += (_,e) =>
            {
                if(e.Frame.Name!="yoru-provider") return;
                var session = navigationSession;
                e.Frame.NavigationCompleted += (_,args) =>
                {
                    if(closed || session != navigationSession) return;
                    if((!args.IsSuccess && args.WebErrorStatus!=CoreWebView2WebErrorStatus.OperationCanceled) || args.HttpStatusCode>=400)
                        Failed(args.HttpStatusCode>=400 ? $"Сервис плеера вернул ошибку {args.HttpStatusCode}. Выбери другой плеер справа или повтори попытку позже." : "Не удалось связаться с плеером. Проверь соединение или выбери другой вариант справа.");
                };
            };
            core.NavigationCompleted += (_,e) =>
            {
                if(!e.IsSuccess && e.WebErrorStatus!=CoreWebView2WebErrorStatus.OperationCanceled) Failed("Не удалось загрузить окно плеера. Нажми «Повторить».");
            };
            core.WebMessageReceived += (_,e) =>
            {
                if(closed || !Uri.TryCreate(e.Source,UriKind.Absolute,out var source) || source.Host!=Host) return;
                try
                {
                    using var doc=JsonDocument.Parse(e.WebMessageAsJson);
                    if(ApiParser.Text(ApiParser.Field(doc.RootElement,"session"))!=navigationSession) return;
                    var state=ApiParser.Text(ApiParser.Field(doc.RootElement,"state"));
                    if(state=="frame-ready" && !sourceFailed)
                    {
                        Status("Плеер загружен · нажми ▶ в видео",state);
                        try { store.Started(anime,current); } catch(IOException) { Status("Плеер загружен · не удалось сохранить историю",state); }
                    }
                    else if(state=="playing") Status("Идёт просмотр · "+current.Label,state);
                    else if(state=="paused") Status("Пауза · "+current.Label,state);
                    else if(state=="ended") Status("Серия закончилась · можно перейти к следующей",state);
                    else if(state=="time")
                    {
                        var seconds=ApiParser.Number(ApiParser.Field(doc.RootElement,"value"));
                        if(seconds>=1) Status($"Идёт просмотр · {TimeSpan.FromSeconds(seconds):mm\\:ss}","time:"+(int)seconds);
                    }
                    else if(state is "invalid-source" or "frame-error") Failed("Не получилось открыть видео. Выбери другой плеер справа.");
                }
                catch(JsonException) { }
            };
            core.ContainsFullScreenElementChanged += (_,_) => { if(!sourceFailed) SetFullscreen(core.ContainsFullScreenElement); };
            core.ProcessFailed += (_,e) =>
            {
                if(closed) return;
                if(e.ProcessFailedKind==CoreWebView2ProcessFailedKind.BrowserProcessExited) { initialized=false; recreateBrowser=true; }
                Failed("Видеоплеер остановился. Нажми «Повторить» или выбери другой плеер.");
            };
            initialized=true; NavigateEpisode(current);
        }
        catch(Exception e) when(e is WebView2RuntimeNotFoundException or System.Runtime.InteropServices.COMException or InvalidOperationException or IOException)
        {
            if(closed) return;
            Failed("Не удалось запустить встроенный браузер. Проверь наличие Microsoft Edge WebView2 Runtime и перезапусти Yoru.");
            RuntimeButton.Visibility=Visibility.Visible;
        }
        finally { initializing=false; }
    }
    void Failed(string message)
    {
        if(fullscreen) SetFullscreen(false);
        sourceFailed=true; Browser.Visibility=Visibility.Hidden; PlayerMessage.Visibility=Visibility.Visible;
        ErrorText.Text=message; Status("Видео недоступно · можно сменить плеер справа","error");
    }
    void NavigateEpisode(Episode episode)
    {
        if(!initialized || closed) return;
        current=episode; sourceFailed=false;
        SyncSelection(episode);
        navigationSession=Guid.NewGuid().ToString("N");
        Browser.Visibility=Visibility.Visible; PlayerMessage.Visibility=Visibility.Collapsed;
        Title=anime.Title+" — "+episode.Label+" · Yoru";
        ShowTitle.Text=anime.Title+" · "+episode.Label; TrackLabel.Text=episode.Track;
        playlist=PlaybackSources.Episodes(videos,episode.Dubbing,episode.Player);
        var index=playlist.FindIndex(v=>v.Id==episode.Id);
        PreviousButton.IsEnabled=index>0; NextButton.IsEnabled=index>=0 && index<playlist.Count-1;
        Status("Загрузка · "+episode.Label,"loading");
        Browser.CoreWebView2.Navigate($"https://{Host}/index.html?src={Uri.EscapeDataString(episode.Url)}&session={navigationSession}");
    }
    void VoiceChanged(object sender,SelectionChangedEventArgs e)
    {
        if(changing || VoiceBox.SelectedItem is not string voice) return;
        var previous=SourceBox.SelectedItem as string;
        changing=true; SourceBox.ItemsSource=PlaybackSources.Players(videos,voice); SourceBox.SelectedItem=previous;
        if(SourceBox.SelectedIndex<0) SourceBox.SelectedIndex=0;
        changing=false; UpdateSeries();
    }
    void SourceChanged(object sender,SelectionChangedEventArgs e) { if(!changing) UpdateSeries(); }
    void UpdateSeries(string? desired=null)
    {
        if(VoiceBox.SelectedItem is not string voice || SourceBox.SelectedItem is not string player) return;
        var number=desired ?? (SeriesBox.SelectedItem as Episode)?.Number ?? current.Number;
        var options=PlaybackSources.Episodes(videos,voice,player);
        SeriesBox.ItemsSource=options; SeriesBox.SelectedItem=options.FirstOrDefault(v=>v.Number==number);
        UpdateSelectionHint();
    }
    void SyncSelection(Episode episode)
    {
        changing=true;
        VoiceBox.SelectedItem=episode.Dubbing;
        SourceBox.ItemsSource=PlaybackSources.Players(videos,episode.Dubbing); SourceBox.SelectedItem=episode.Player;
        var options=PlaybackSources.Episodes(videos,episode.Dubbing,episode.Player);
        SeriesBox.ItemsSource=options; SeriesBox.SelectedItem=options.FirstOrDefault(v=>v.Id==episode.Id);
        changing=false; UpdateSelectionHint();
    }
    void UpdateSelectionHint()
    {
        ApplyButton.IsEnabled=SeriesBox.SelectedItem is Episode;
        AlternativeButton.IsEnabled=PlaybackSources.Alternative(videos,current)!=null;
        SelectionHint.Text=SeriesBox.SelectedItem is not Episode selected ? $"Серии {current.Number} здесь нет. Выбери доступную серию явно." : selected.Id!=current.Id ? "Выбор изменён. Нажми «Смотреть выбранное», чтобы применить." : "";
    }
    async void ApplyClick(object sender,RoutedEventArgs e)
    {
        if(SeriesBox.SelectedItem is not Episode episode) return;
        if(!initialized) { current=episode; await InitializePlayer(); } else NavigateEpisode(episode);
    }
    async void AlternativeClick(object sender,RoutedEventArgs e)
    {
        var alternative=PlaybackSources.Alternative(videos,current);
        if(alternative==null) return;
        if(!initialized) { current=alternative; await InitializePlayer(); } else NavigateEpisode(alternative);
    }
    void PreviousClick(object sender,RoutedEventArgs e) => Move(-1);
    void NextClick(object sender,RoutedEventArgs e) => Move(1);
    void Move(int step)
    {
        var index=playlist.FindIndex(v=>v.Id==current.Id)+step;
        if(index<0 || index>=playlist.Count) return;
        NavigateEpisode(playlist[index]);
    }
    async void RetryClick(object sender,RoutedEventArgs e)
    {
        if(!initialized) await InitializePlayer(); else NavigateEpisode(current);
    }
    void BackClick(object sender,RoutedEventArgs e) => Close();
    void FullScreenClick(object sender,RoutedEventArgs e) => SetFullscreen(!fullscreen);
    void PlayerKey(object sender,KeyEventArgs e)
    {
        if(e.Key==Key.F11) { SetFullscreen(!fullscreen); e.Handled=true; }
        if(e.Key==Key.Escape && fullscreen) { SetFullscreen(false); e.Handled=true; }
    }
    void SetFullscreen(bool value)
    {
        if(value==fullscreen) return;
        fullscreen=value;
        if(value) { previousState=WindowState; WindowState=WindowState.Normal; WindowStyle=WindowStyle.None; ResizeMode=ResizeMode.NoResize; WindowState=WindowState.Maximized; }
        else { WindowState=WindowState.Normal; WindowStyle=WindowStyle.SingleBorderWindow; ResizeMode=ResizeMode.CanResize; WindowState=previousState; }
        PlayerHeader.Visibility=PlayerFooter.Visibility=PlayerSidebar.Visibility=value ? Visibility.Collapsed : Visibility.Visible;
        SidebarColumn.Width=new GridLength(value ? 0 : 270);
    }
    void RuntimeClick(object sender,RoutedEventArgs e) => MainWindow.OpenLink("https://developer.microsoft.com/microsoft-edge/webview2/");
}
