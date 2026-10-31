using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Threading;
namespace Yoru;

/// <summary>Совместный просмотр, обсуждение серии, метки опенинга и Discord Rich Presence в плеере.</summary>
public partial class PlayerPage
{
    static readonly string[] Reactions = ["❤", "😂", "😮", "😭", "🔥", "👏", "💀", "🎃"];
    string roomCode = "", lastApplied = "";
    double lastSeconds, markStart = -1;
    long since; bool polling;
    JsonObject intro = new();
    JsonObject? pendingSync;
    DateTime suppressUntil, receivedAt;
    readonly DispatcherTimer roomTimer = new() { Interval = TimeSpan.FromMilliseconds(1500) };
    readonly StackPanel partyPanel = new(), chatList = new();
    FrameworkElement? commentsPanel;
    readonly List<ToggleButton> tabs = [];
    long presenceStart;
    AccountClient Me => account ?? AccountClient.Shared;
    string MyName => AccountClient.Text(Me.Profile, "username");

    void BuildSocial()
    {
        roomTimer.Tick += async (_, _) => await Poll();
        foreach (var (title, key) in new[] { ("Плеер", "player"), ("Вместе", "party"), ("Чат серии", "comments") })
        {
            var tab = new ToggleButton { Content = title, Style = (Style)Application.Current.Resources["Chip"], Padding = new Thickness(6, 8, 6, 8), Margin = new Thickness(0, 0, 6, 0), Tag = key, IsChecked = key == "player" };
            tab.Click += (_, _) => SelectTab(key);
            tabs.Add(tab); SidebarTabs.Children.Add(tab);
        }
        // Метки опенинга/эндинга — в конце вкладки «Плеер».
        SettingsPanel.Children.Add(new Border { Height = 1, Background = new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)), Margin = new Thickness(0, 22, 0, 18) });
        SettingsPanel.Children.Add(new TextBlock { Text = "Отметить заставку", FontSize = 14, FontWeight = FontWeights.SemiBold });
        SettingsPanel.Children.Add(new TextBlock { Text = "Поставь видео на начало опенинга или эндинга, нажми «Начало», затем на конец — «Конец». Кнопка пропуска у всех будет вести точно на конец заставки.", FontSize = 12, Foreground = Extras.Muted, TextWrapping = TextWrapping.Wrap, LineHeight = 19, Margin = new Thickness(0, 8, 0, 10) });
        var kind = new ComboBox { ItemsSource = new[] { "Опенинг", "Эндинг" }, SelectedIndex = 0, MinWidth = 0 };
        var range = new TextBlock { FontSize = 12, Foreground = Extras.Muted, Margin = new Thickness(0, 8, 0, 8) };
        void Paint() => range.Text = "Начало: " + (markStart < 0 ? "—" : Clock(markStart));
        Paint();
        var buttons = new UniformGrid { Columns = 2 };
        var start = new Button { Content = "Начало", Margin = new Thickness(0, 0, 4, 0), Padding = new Thickness(6, 9, 6, 9) };
        var end = new Button { Content = "Конец", Margin = new Thickness(4, 0, 0, 0), Padding = new Thickness(6, 9, 6, 9) };
        start.Click += (_, _) => { markStart = lastSeconds; Paint(); };
        end.Click += async (_, _) =>
        {
            if (!Me.SignedIn) { range.Text = "Войди в аккаунт, чтобы отмечать заставки."; return; }
            if (markStart < 0 || lastSeconds <= markStart) { range.Text = "Сначала отметь начало."; return; }
            var episode = current;
            try
            {
                var result = await Me.Rpc("mark_intro", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = episode.Number, ["p_kind"] = kind.SelectedIndex == 0 ? "opening" : "ending", ["p_start"] = (int)markStart, ["p_end"] = (int)lastSeconds });
                if (episode.Id == current.Id && result is JsonObject marks) { intro = marks; SendIntro(); }
                markStart = -1; Paint(); range.Text = "Спасибо! Метка сохранена.";
            }
            catch (Exception e) when (Extras.Failure(e)) { range.Text = Rewards.Error(e.Message); }
        };
        buttons.Children.Add(start); buttons.Children.Add(end);
        SettingsPanel.Children.Add(kind); SettingsPanel.Children.Add(range); SettingsPanel.Children.Add(buttons);
        RenderParty();
        if (roomCode.Length > 0) { AttachRoom(roomCode); SelectTab("party"); }
    }

    void SelectTab(string key)
    {
        foreach (var t in tabs) t.IsChecked = (string)t.Tag == key;
        if (key == "player") SidebarScroll.Content = SettingsPanel;
        else if (key == "party") SidebarScroll.Content = partyPanel;
        else
        {
            commentsPanel ??= Extras.Comments(Me, anime, () => current.Number, () => lastSeconds, s => Post(new JsonObject { ["cmd"] = "seek", ["session"] = navigationSession, ["position"] = s }));
            SidebarScroll.Content = commentsPanel;
        }
    }
    static string Clock(double seconds) => TimeSpan.FromSeconds(Math.Max(0, seconds)).ToString(seconds >= 3600 ? @"h\:mm\:ss" : @"mm\:ss");

    // ───── Метки заставок ─────
    void EpisodeChanged(Episode episode)
    {
        intro = new JsonObject(); markStart = -1;
        _ = LoadIntro(episode);
        if (roomCode.Length > 0 && DateTime.Now > suppressUntil) _ = Push(0, true);
        if (commentsPanel is { IsVisible: true, Tag: Func<Task> reload }) _ = reload();
    }
    async Task LoadIntro(Episode episode)
    {
        try { if (await Me.PublicRpc("anime_intro", new JsonObject { ["p_anime_id"] = anime.Id, ["p_episode"] = episode.Number }) is JsonObject marks && episode.Id == current.Id) { intro = marks; SendIntro(); } }
        catch (Exception e) when (Extras.Failure(e)) { }
    }
    void SendIntro() { if (nativeMode) Post(new JsonObject { ["cmd"] = "intro", ["session"] = navigationSession, ["intro"] = intro.DeepClone() }); }

    // ───── Совместный просмотр ─────
    void RenderParty()
    {
        partyPanel.Children.Clear();
        if (roomCode.Length == 0)
        {
            partyPanel.Children.Add(new TextBlock { Text = "Смотреть вместе", FontSize = 18, FontWeight = FontWeights.SemiBold });
            partyPanel.Children.Add(new TextBlock { Text = "Пауза, перемотка и выбор серии синхронизируются у всех участников. В комнате есть чат и реакции.", FontSize = 12, Foreground = Extras.Muted, TextWrapping = TextWrapping.Wrap, LineHeight = 19, Margin = new Thickness(0, 8, 0, 14) });
            var error = new TextBlock { FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 0) };
            var create = new Button { Content = "Создать комнату", Style = (Style)Application.Current.Resources["Primary"], Padding = new Thickness(10, 11, 10, 11) };
            create.Click += async (_, _) =>
            {
                if (!Me.SignedIn) { error.Text = "Войди в аккаунт, чтобы смотреть вместе."; return; }
                try
                {
                    var state = await Me.Rpc("room_create", new JsonObject { ["p_anime_id"] = anime.Id, ["p_title"] = anime.Title, ["p_poster"] = anime.Poster, ["p_episode"] = current.Number, ["p_video_id"] = current.Id });
                    lastApplied = AccountClient.Text(state, "updated_at"); AttachRoom(AccountClient.Text(state, "code")); await Push(lastSeconds, false);
                }
                catch (Exception e) when (Extras.Failure(e)) { error.Text = Rewards.Error(e.Message); }
            };
            var code = new TextBox { MaxLength = 6, CharacterCasing = CharacterCasing.Upper, Margin = new Thickness(0, 18, 0, 8), FontSize = 16 };
            System.Windows.Automation.AutomationProperties.SetName(code, "Код комнаты");
            var join = new Button { Content = "Войти по коду", Padding = new Thickness(10, 10, 10, 10) };
            join.Click += async (_, _) => { if (code.Text.Trim().Length == 6) await JoinRoomPage.Join(Me, code.Text.Trim(), m => error.Text = m); };
            partyPanel.Children.Add(create); partyPanel.Children.Add(code); partyPanel.Children.Add(join); partyPanel.Children.Add(error);
            return;
        }
        var head = new TextBlock { Text = "Комната " + roomCode, FontSize = 18, FontWeight = FontWeights.SemiBold, Tag = "head" };
        partyPanel.Children.Add(head);
        var actions = new UniformGrid { Columns = 2, Margin = new Thickness(0, 10, 0, 12) };
        var copy = new Button { Content = "Скопировать код", Margin = new Thickness(0, 0, 4, 0), Padding = new Thickness(6, 8, 6, 8) };
        copy.Click += (_, _) => { try { Clipboard.SetText($"Смотрим «{anime.Title}» вместе в Yoru! Код комнаты: {roomCode}"); copy.Content = "✓ Скопировано"; } catch (System.Runtime.InteropServices.COMException) { } };
        var leave = new Button { Content = "Выйти", Margin = new Thickness(4, 0, 0, 0), Padding = new Thickness(6, 8, 6, 8), Foreground = Extras.Accent };
        leave.Click += async (_, _) => await LeaveRoom();
        actions.Children.Add(copy); actions.Children.Add(leave); partyPanel.Children.Add(actions);
        var reacts = new WrapPanel { Margin = new Thickness(0, 0, 0, 10) };
        foreach (var emoji in Reactions)
        {
            var b = new Button { Content = emoji, MinWidth = 0, Width = 40, Height = 36, Padding = new Thickness(0), Margin = new Thickness(0, 0, 6, 6), FontSize = 16 };
            b.Click += async (_, _) => await Say("reaction", emoji);
            reacts.Children.Add(b);
        }
        partyPanel.Children.Add(reacts);
        partyPanel.Children.Add(chatList);
        var input = new TextBox { MaxLength = 300, Margin = new Thickness(0, 10, 0, 6) };
        System.Windows.Automation.AutomationProperties.SetName(input, "Сообщение в комнату");
        input.KeyDown += async (_, e) => { if (e.Key == System.Windows.Input.Key.Enter && input.Text.Trim().Length > 0) { e.Handled = true; var text = input.Text.Trim(); input.Text = ""; await Say("chat", text); } };
        var send = new Button { Content = "Отправить", Padding = new Thickness(6, 8, 6, 8) };
        send.Click += async (_, _) => { if (input.Text.Trim().Length == 0) return; var text = input.Text.Trim(); input.Text = ""; await Say("chat", text); };
        partyPanel.Children.Add(input); partyPanel.Children.Add(send);
    }

    void AttachRoom(string code)
    {
        roomCode = code; since = 0; chatList.Children.Clear(); RenderParty();
        if (!polling) { polling = true; roomTimer.Start(); }
    }
    async Task LeaveRoom()
    {
        var code = roomCode; roomCode = ""; polling = false; roomTimer.Stop(); RenderParty();
        if (code.Length > 0) try { await Me.Rpc("room_leave", new JsonObject { ["p_code"] = code }); } catch (Exception e) when (Extras.Failure(e)) { }
    }
    async Task Say(string kind, string body)
    {
        if (roomCode.Length == 0) return;
        try { await Me.Rpc("room_say", new JsonObject { ["p_code"] = roomCode, ["p_kind"] = kind, ["p_body"] = body }); }
        catch (Exception e) when (Extras.Failure(e)) { AddLine(Rewards.Error(e.Message), true); }
    }
    bool pollBusy;
    async Task Poll()
    {
        if (closed || roomCode.Length == 0 || pollBusy) return;
        pollBusy = true; var code = roomCode;
        try
        {
            var state = await Me.Rpc("room_poll", new JsonObject { ["p_code"] = code, ["p_since"] = since }) as JsonObject;
            if (state != null && code == roomCode && !closed) Receive(state);
        }
        catch (Exception e) when (Extras.Failure(e)) { if (e.Message.Contains("not_in_room")) { AddLine("Комната закрыта.", true); await LeaveRoom(); } }
        finally { pollBusy = false; }
    }
    void Receive(JsonObject state)
    {
        receivedAt = DateTime.Now;
        var members = state["members"] as JsonArray ?? new JsonArray();
        if (partyPanel.Children.Count > 0 && partyPanel.Children[0] is TextBlock head) head.Text = $"Комната {roomCode} · {members.Count} {Extras.Plural(members.Count, "зритель", "зрителя", "зрителей")}";
        foreach (var m in (state["messages"] as JsonArray ?? new JsonArray()).OfType<JsonObject>())
        {
            since = Math.Max(since, AccountClient.Long(m, "id"));
            var who = AccountClient.Text(m, "display_name") is { Length: > 0 } dn ? dn : AccountClient.Text(m, "username");
            var kind = AccountClient.Text(m, "kind"); var body = AccountClient.Text(m, "body");
            if (kind == "reaction") Post(new JsonObject { ["cmd"] = "reaction", ["session"] = navigationSession, ["emoji"] = body });
            AddLine(kind switch { "system" => body switch { "joined" => who + " зашёл в комнату", "left" => who + " вышел", _ => who + " создал комнату" }, "reaction" => who + " " + body, _ => who + ": " + body }, kind != "chat");
        }
        if (AccountClient.Text(state, "updated_at") != lastApplied && AccountClient.Text(state, "updated_by") != MyName)
        { lastApplied = AccountClient.Text(state, "updated_at"); Apply(state); }
    }
    void AddLine(string text, bool muted)
    {
        chatList.Children.Add(new TextBlock { Text = text, FontSize = 12, TextWrapping = TextWrapping.Wrap, Foreground = muted ? Extras.Muted : Brushes.WhiteSmoke, Margin = new Thickness(0, 3, 0, 3) });
        while (chatList.Children.Count > 40) chatList.Children.RemoveAt(0);
    }
    /// <summary>Чужое действие: переключить серию, затем догнать позицию и паузу комнаты.</summary>
    void Apply(JsonObject state)
    {
        var target = videos.FirstOrDefault(v => v.Id == AccountClient.Long(state, "video_id")) ?? PlaybackSources.Episodes(videos, current.Dubbing, current.Player).FirstOrDefault(v => v.Number == AccountClient.Text(state, "episode"));
        pendingSync = state;
        if (target != null && target.Id != current.Id) { suppressUntil = DateTime.Now.AddSeconds(4); NavigateEpisode(target); return; }
        ApplyPending();
        if (AccountClient.Text(state, "updated_by") is { Length: > 0 } who) PlayerStatus.Text = $"{who}: {(AccountClient.Bool(state, "paused") ? "пауза" : "воспроизведение")} · {Clock(state["position"]?.GetValue<double>() ?? 0)}";
    }
    void ApplyPending()
    {
        if (pendingSync is not { } state || !nativeMode) return;
        pendingSync = null;
        var paused = AccountClient.Bool(state, "paused");
        var position = (state["position"]?.GetValue<double>() ?? 0) + (paused ? 0 : (state["age"]?.GetValue<double>() ?? 0) + (DateTime.Now - receivedAt).TotalSeconds);
        suppressUntil = DateTime.Now.AddSeconds(1.5);
        Post(new JsonObject { ["cmd"] = "sync", ["session"] = navigationSession, ["position"] = position, ["paused"] = paused });
    }
    async void RoomLoaded()
    {
        if (roomCode.Length == 0) return;
        suppressUntil = DateTime.Now.AddSeconds(3);
        await Task.Delay(1500); if (!closed) ApplyPending();
        await Task.Delay(2000); if (!closed) ApplyPending();
    }
    void RoomControl(double position, bool paused)
    {
        lastSeconds = position;
        if (roomCode.Length == 0 || DateTime.Now < suppressUntil) return;
        _ = Push(position, paused);
        Presence(!paused);
    }
    async Task Push(double position, bool paused)
    {
        if (roomCode.Length == 0) return;
        try
        {
            var state = await Me.Rpc("room_control", new JsonObject { ["p_code"] = roomCode, ["p_position"] = Math.Max(0, position), ["p_paused"] = paused, ["p_episode"] = current.Number, ["p_video_id"] = current.Id });
            lastApplied = AccountClient.Text(state, "updated_at");
        }
        catch (Exception e) when (Extras.Failure(e)) { }
    }

    // ───── Discord ─────
    void Presence(bool playing)
    {
        var id = store.Data.DiscordClientId.Trim();
        if (!store.Data.DiscordPresence || id.Length == 0) return;
        if (presenceStart == 0 || !playing) presenceStart = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - (long)lastSeconds;
        _ = DiscordPresence.Show(id, anime.Title, current.Label + (playing ? "" : " · пауза") + (roomCode.Length > 0 ? " · вместе с друзьями" : ""), anime.Poster, presenceStart, anime.Alias.Length > 0 ? "https://yummyani.me/catalog/item/" + anime.Alias : "");
    }
    void SocialClosed()
    {
        roomTimer.Stop();
        if (roomCode.Length > 0) { var code = roomCode; roomCode = ""; _ = Me.Rpc("room_leave", new JsonObject { ["p_code"] = code }).ContinueWith(_ => { }, TaskScheduler.Default); }
        if (store.Data.DiscordClientId.Trim().Length > 0) _ = DiscordPresence.Clear(store.Data.DiscordClientId.Trim());
    }
}
