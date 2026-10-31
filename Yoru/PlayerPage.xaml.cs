using System.Text.Json;
using System.Windows.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;
namespace Yoru;
public partial class PlayerPage : UserControl
{
    const string Host = "player.yoru.local";
    readonly Anime anime;
    readonly List<Episode> videos;
    readonly LocalStore store;
    Episode current;
    List<Episode> playlist = [];
    bool initialized, initializing, closed, fullscreen, changing, sourceFailed, recreateBrowser;
    string navigationSession = "";
    public event Action<string>? PlaybackStateChanged;
    // Очки за просмотр: секунды реального воспроизведения текущей серии.
    readonly AccountClient? account;
    readonly DispatcherTimer watchTimer = new() { Interval = TimeSpan.FromSeconds(1) };
    long watchedEpisode = -1; int watchedSeconds; bool playing, awardSent; DateTime awardRetryAt;
    // Нативный режим: прямой поток Kodik в собственном плеере (native.html), видео идёт через прокси приложения.
    bool nativeMode, forceSite; StreamResolver.Result? resolved; CancellationTokenSource? resolveCts; double duration; DateTime lastPositionSave;
    public PlayerPage(Anime item, List<Episode> list, Episode selected, LocalStore local, AccountClient? accountClient = null, string? room = null)
    {
        InitializeComponent();
        roomCode=room ?? "";
        account=accountClient;
        watchTimer.Tick += async (_, _) => await WatchTick();
        anime=item; videos=list; current=selected; store=local;
        VoiceBox.ItemsSource=PlaybackSources.Dubbings(videos);
        SyncSelection(selected);
        SeriesBox.SelectionChanged += (_,_) => { if(!changing) UpdateSelectionHint(); };
        Loaded += async (_, _) => await InitializePlayer();
        BuildSocial();
        Unloaded += (_, _) => { if(closed) return; closed=true; watchTimer.Stop(); SocialClosed(); resolveCts?.Cancel(); SavePrefs(); if(fullscreen) Shell.Fullscreen(false); Browser.Dispose(); };
    }
    void Status(string message, string state)
    {
        PlayerStatus.Text=message; PlaybackStateChanged?.Invoke(state);
        // Время считается, пока плеер сообщает о воспроизведении; пауза, конец, загрузка и ошибка его останавливают.
        if(state is "playing" || state.StartsWith("time:")) { playing=true; if(account is { SignedIn: true } && !watchTimer.IsEnabled) watchTimer.Start(); }
        else if(state is "paused" or "ended" or "loading" or "error" or "initializing") playing=false;
    }
    async Task WatchTick()
    {
        if(closed) return;
        if(watchedEpisode!=current.Id) { watchedEpisode=current.Id; watchedSeconds=0; awardSent=false; }
        if(playing) watchedSeconds++;
        if(account is not { SignedIn: true } || awardSent || DateTime.Now<awardRetryAt || TimeSpan.FromSeconds(watchedSeconds)<Rewards.Threshold(nativeMode && duration>0 ? TimeSpan.FromSeconds(duration) : null)) return;
        awardSent=true; var episode=current;
        try
        {
            var result=await account.Award(anime,episode.Id,episode.Number,watchedSeconds);
            if(closed) return;
            if(result["awarded"]?.GetValue<bool>()==true)
            {
                var bonus=AccountClient.Int(result,"streak_bonus");
                PlayerStatus.Text=$"+{AccountClient.Int(result,"points")} очков за {episode.Label}"+(bonus>0?$" · 🔥 {Rewards.Days(AccountClient.Int(result,"streak"))} подряд (+{bonus})":"")+$" · баланс ◆ {Rewards.Points(AccountClient.Int(result,"balance"))}  {AccountClient.Announce(result)}";
            }
            else if(Rewards.SkipReason(AccountClient.Text(result,"reason")) is { Length: > 0 } reason && AccountClient.Text(result,"reason")!="already") PlayerStatus.Text=reason;
        }
        catch(Exception e) when (e is AccountException or System.Net.Http.HttpRequestException or TaskCanceledException or JsonException or InvalidOperationException)
        { if(watchedEpisode==episode.Id) { awardSent=false; awardRetryAt=DateTime.Now.AddMinutes(1); } }
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
            core.AddWebResourceRequestedFilter($"https://{Host}/proxy*", CoreWebView2WebResourceContext.All);
            core.WebResourceRequested += Proxy;
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
                    else if(state=="ready" && nativeMode) _ = StartNative(current, navigationSession);
                    else if(state=="control") RoomControl(ApiParser.Number(ApiParser.Field(doc.RootElement,"value")), ApiParser.Field(doc.RootElement,"paused").ValueKind==JsonValueKind.True);
                    else if(state=="next") Move(1);
                    else if(state=="site") { forceSite=true; NavigateEpisode(current); }
                    else if(state=="retry") NavigateEpisode(current);
                    else if(state=="fullscreen") SetFullscreen(!fullscreen);
                    else if(state=="exit-fullscreen") SetFullscreen(false);
                    else if(state=="quality") { store.Data.Quality=ApiParser.Text(ApiParser.Field(doc.RootElement,"value")); SavePrefs(); }
                    else if(state=="speed") { store.Data.Speed=ApiParser.Number(ApiParser.Field(doc.RootElement,"value")); SavePrefs(); }
                    else if(state=="autonext") { store.Data.AutoNext=ApiParser.Field(doc.RootElement,"value").ValueKind==JsonValueKind.True; SavePrefs(); }
                    else if(state=="error") Status("Видео недоступно · можно открыть плеер сайта","error");
                    else if(state=="playing") { Status("Идёт просмотр · "+current.Label,state); Presence(true); }
                    else if(state=="paused") { Status("Пауза · "+current.Label,state); Presence(false); }
                    else if(state=="ended") { Status("Серия закончилась · можно перейти к следующей",state); if(nativeMode) SavePosition(0, true); }
                    else if(state=="time")
                    {
                        var seconds=ApiParser.Number(ApiParser.Field(doc.RootElement,"value"));
                        var length=ApiParser.Number(ApiParser.Field(doc.RootElement,"duration"));
                        if(length>0) duration=length;
                        if(nativeMode) SavePosition(seconds, false);
                        lastSeconds=seconds;
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
        if(episode.Id!=current.Id) forceSite=false;
        current=episode; sourceFailed=false; resolved=null; resolveCts?.Cancel(); duration=0;
        nativeMode=StreamResolver.Supports(episode.Url) && !forceSite;
        SyncSelection(episode);
        navigationSession=Guid.NewGuid().ToString("N");
        Browser.Visibility=Visibility.Visible; PlayerMessage.Visibility=Visibility.Collapsed;
        Shell.Title(anime.Title+" — "+episode.Label);
        ShowTitle.Text=anime.Title+" · "+episode.Label; TrackLabel.Text=episode.Track;
        playlist=PlaybackSources.Episodes(videos,episode.Dubbing,episode.Player);
        var index=playlist.FindIndex(v=>v.Id==episode.Id);
        PreviousButton.IsEnabled=index>0; NextButton.IsEnabled=index>=0 && index<playlist.Count-1;
        Status("Загрузка · "+episode.Label,"loading");
        EpisodeChanged(episode);
        if(nativeMode) { TrackLabel.Text=episode.Track+" · плеер Yoru без рекламы"; Browser.CoreWebView2.Navigate($"https://{Host}/native.html?session={navigationSession}"); }
        else Browser.CoreWebView2.Navigate($"https://{Host}/index.html?src={Uri.EscapeDataString(episode.Url)}&session={navigationSession}");
    }
    /// <summary>Получает прямые потоки и передаёт их странице плеера вместе с позицией и настройками.</summary>
    async Task StartNative(Episode episode, string session)
    {
        Status("Получаем видеопоток…","loading");
        resolveCts?.Cancel(); var cts=new CancellationTokenSource(); resolveCts=cts;
        try
        {
            var result=await StreamResolver.Resolve(episode.Url, cts.Token);
            if(closed || session!=navigationSession) return;
            resolved=result;
            var streams=new System.Text.Json.Nodes.JsonArray();
            foreach(var stream in result.Streams) streams.Add(new System.Text.Json.Nodes.JsonObject { ["label"]=stream.Label, ["url"]=stream.Url, ["mime"]=stream.Mime });
            Post(new System.Text.Json.Nodes.JsonObject { ["cmd"]="load", ["session"]=session, ["streams"]=streams, ["quality"]=store.Data.Quality,
                ["speed"]=store.Data.Speed is >= 0.5 and <= 2 ? store.Data.Speed : 1, ["autoNext"]=store.Data.AutoNext, ["hasNext"]=NextButton.IsEnabled, ["start"]=store.Position(episode.Id) });
            Status("Плеер Yoru · "+episode.Label,"loaded");
            SendIntro(); RoomLoaded();
            try { store.Started(anime,episode); } catch(IOException) { }
        }
        catch(Exception ex) when (ex is InvalidOperationException or System.Net.Http.HttpRequestException or TaskCanceledException or JsonException)
        {
            if(closed || session!=navigationSession || cts.IsCancellationRequested) return;
            var text=ex is System.Net.Http.HttpRequestException or TaskCanceledException ? "Не удалось связаться с источником видео. Проверь подключение и повтори." : ex.Message;
            Post(new System.Text.Json.Nodes.JsonObject { ["cmd"]="error", ["session"]=session, ["text"]=text });
            Status("Видео недоступно · можно открыть плеер сайта","error");
        }
    }
    void Post(System.Text.Json.Nodes.JsonObject message) { if(!closed && Browser.CoreWebView2!=null) Browser.CoreWebView2.PostWebMessageAsJson(message.ToJsonString()); }
    void SavePrefs() { try { store.Save(); } catch(IOException) { } }
    void SavePosition(double seconds, bool force)
    {
        if(!force && (DateTime.Now-lastPositionSave).TotalSeconds<15) return;
        lastPositionSave=DateTime.Now;
        store.Data.Positions[current.Id.ToString()]=duration>0 && seconds>=duration-10 ? 0 : Math.Max(0,seconds);
        SavePrefs();
    }
    /// <summary>Прокси видео: страница плеера запрашивает https://player.yoru.local/proxy?u=…, приложение скачивает с CDN с нужным Referer.</summary>
    async void Proxy(object? sender, CoreWebView2WebResourceRequestedEventArgs e)
    {
        var core=Browser.CoreWebView2;
        var source=resolved;
        if(core==null) return;
        if(source==null || !nativeMode) { e.Response=core.Environment.CreateWebResourceResponse(null,403,"Forbidden",""); return; }
        var query=new Uri(e.Request.Uri).Query;
        var target=query.StartsWith("?u=") ? Uri.UnescapeDataString(query[3..]) : "";
        var range=e.Request.Headers.Contains("Range") ? e.Request.Headers.GetHeader("Range") : null;
        var deferral=e.GetDeferral();
        try
        {
            var response=await StreamResolver.Fetch(target, source.Referer, range, resolveCts?.Token ?? default);
            var body=await response.Content.ReadAsStreamAsync();
            var headers=new System.Text.StringBuilder("Access-Control-Allow-Origin: *\r\nCache-Control: no-store\r\n");
            if(response.Content.Headers.ContentType is { } type) headers.Append("Content-Type: ").Append(type).Append("\r\n");
            if(response.Content.Headers.ContentLength is { } length) headers.Append("Content-Length: ").Append(length).Append("\r\n");
            if(response.Content.Headers.ContentRange is { } contentRange) headers.Append("Content-Range: ").Append(contentRange).Append("\r\n");
            if(response.Headers.AcceptRanges.Count>0) headers.Append("Accept-Ranges: ").Append(string.Join(",",response.Headers.AcceptRanges)).Append("\r\n");
            if(!closed) e.Response=core.Environment.CreateWebResourceResponse(body,(int)response.StatusCode,response.ReasonPhrase ?? "OK",headers.ToString());
        }
        catch(Exception ex) when (ex is InvalidOperationException or System.Net.Http.HttpRequestException or TaskCanceledException or IOException or UriFormatException)
        { if(!closed) e.Response=core.Environment.CreateWebResourceResponse(null,502,"Bad Gateway",""); }
        finally { deferral.Complete(); }
    }
    void SiteClick(object sender,RoutedEventArgs e) { forceSite=!forceSite || !StreamResolver.Supports(current.Url); if(initialized) NavigateEpisode(current); }
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
    void BackClick(object sender,RoutedEventArgs e) => Shell.Back();
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
        Shell.Fullscreen(value);
        PlayerHeader.Visibility=PlayerFooter.Visibility=PlayerSidebar.Visibility=value ? Visibility.Collapsed : Visibility.Visible;
        SidebarColumn.Width=new GridLength(value ? 0 : 320);
    }
    void RuntimeClick(object sender,RoutedEventArgs e) => MainWindow.OpenLink("https://developer.microsoft.com/microsoft-edge/webview2/");
}
