using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Markup;
using System.Xml.Linq;
using Yoru;

internal static class Program
{
    static int passed;
    static void Check(bool condition, string title)
    {
        if (!condition) throw new Exception("FAIL: " + title);
        Console.WriteLine("PASS: " + title); passed++;
    }
    [STAThread]
    static int Main(string[] args)
    {
        try
        {
            if (args.Length == 1 && args[0] == "--live")
            {
                Live().GetAwaiter().GetResult(); return 0;
            }
            if (args.Length == 2 && args[0] == "--player")
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(args[1]));
                var item = ApiParser.Field(doc.RootElement, "response");
                var anime = ApiParser.Anime(item);
                var videos = ApiParser.Videos(ApiParser.Field(item, "videos"));
                var first = videos.FirstOrDefault(v => v.Url.Contains("kodik")) ?? videos.First();
                var app = new Application();
                var xaml = XDocument.Load("Yoru/App.xaml");
                XNamespace ns = "http://schemas.microsoft.com/winfx/2006/xaml/presentation";
                var dictionary = new XElement(ns + "ResourceDictionary", new XAttribute(XNamespace.Xmlns + "x", "http://schemas.microsoft.com/winfx/2006/xaml"), xaml.Root!.Element(ns + "Application.Resources")!.Elements());
                app.Resources = (ResourceDictionary)XamlReader.Parse(dictionary.ToString());
                var local = new LocalStore(Path.Combine(Environment.CurrentDirectory, "work", "player-test"));
                var player = new PlayerWindow(anime, videos, first, local);
                player.PlaybackStateChanged += state => Console.WriteLine(DateTime.Now.ToString("HH:mm:ss") + " " + state);
                app.Run(player);
                return 0;
            }
            Run().GetAwaiter().GetResult();
            Console.WriteLine($"{passed} checks passed."); return 0;
        }
        catch (Exception e) { Console.Error.WriteLine(e); return 1; }
    }
    static async Task Live()
    {
        using var client = new YummyClient { Token = AppConfig.DefaultApplicationToken };
        var catalog = await client.Catalog("Фрирен", "rating", "", 0, default);
        Check(catalog.Count > 0, "live authenticated search returns results");
        var detail = await client.Detail(catalog[0].Id, default);
        Check(detail.Anime.Id == catalog[0].Id && detail.Videos.Count > 0, "live authenticated detail returns episode players");
        var schedule = await client.Schedule(default);
        Check(schedule.Count > 0, "live authenticated schedule parses");
        Console.WriteLine($"Live catalog: {catalog.Count} titles; detail: {detail.Videos.Count} videos; schedule: {schedule.Count} titles.");
    }
    static async Task Run()
    {
        var root = Path.Combine(Environment.CurrentDirectory, "work", "tests-" + Guid.NewGuid().ToString("N"));
        using var catalogDoc = JsonDocument.Parse(File.ReadAllText("Yoru/Assets/preview.json"));
        var catalog = ApiParser.Catalog(catalogDoc.RootElement);
        Check(catalog.Count == 12 && catalog.All(a => a.Id > 0 && a.Title.Length > 0), "real catalog fixture parses");
        Check(catalog.All(a => a.Poster.StartsWith("https://")), "protocol-relative poster URLs normalized");
        Check(ApiParser.Plain("<p>Текст &amp; текст</p>") == "Текст & текст", "description is plain decoded text");
        using var sparse = JsonDocument.Parse("{\"anime_id\":22,\"title\":\"Test\"}");
        Check(ApiParser.Anime(sparse.RootElement).Rating == 0, "missing optional fields are tolerated");
        using var videosDoc = JsonDocument.Parse("""
        [{"video_id":2,"number":"2","index":2,"data":{"dubbing":"Dub","player":"Player"},"iframe_url":"//player.test/2"},
         {"video_id":1,"number":"1","index":1,"data":{"dubbing":"Dub","player":"Player"},"iframe_url":"https://player.test/1"},
         {"video_id":3,"iframe_url":"javascript:alert(1)"}, {"video_id":4,"iframe_url":"file:///secret"}]
        """);
        var videos = ApiParser.Videos(videosDoc.RootElement);
        Check(videos.Count == 2 && videos[0].Number == "1" && videos[1].Number == "2", "episodes ordered and unsafe URL schemes rejected");
        var options = videos.Concat([new Episode(5,"1",1,"Dub","Other","https://other.test/1"),new Episode(6,"1",1,"Sub","Player","https://player.test/sub")]).ToList();
        Check(PlaybackSources.Dubbings(options).Count == 2 && PlaybackSources.Players(options,"Dub").Count == 2 && PlaybackSources.Players(options,"Sub").Count == 1, "voice and provider selections are independent");
        Check(PlaybackSources.Episodes(options,"Dub","Other").Single().Id == 5, "switching provider uses its matching episode URL");
        Check(PlaybackSources.Resume(options,new WatchEntry(catalog[0],5,"1","Dub · Other",DateTimeOffset.Now))?.Player == "Other", "resume restores provider and voice by video ID");
        Check(PlaybackSources.Alternative(options,videos[0])?.Id==5, "alternative preserves current episode and voice");
        Check(PlaybackSources.Alternative(options,videos[1])==null, "missing episode on alternative never falls back to episode one");
        Check(videos[0].ToString()=="Серия 1", "episode selector shows a label instead of internal data");
        var handler = new Stub(HttpStatusCode.OK, "{\"response\":[]}");
        using var client = new YummyClient(handler) { Token = "fixture-app-key" };
        await client.Catalog("Фрирен & test", "rating", "ongoing", 24, default);
        Check(handler.Url!.Host == "api.yani.tv" && handler.Url.AbsolutePath == "/anime" && handler.Url.Query.Contains("offset=24"), "catalog endpoint and pagination");
        Check(handler.Key == "fixture-app-key" && handler.Language == "ru", "application key and language sent as headers");
        Check(handler.Url.Query.Contains("%26") && handler.Url.Query.Contains("status=ongoing"), "search is encoded and status applied");
        client.Token = "";
        var noKey = false; try { await client.Get("anime"); } catch (InvalidOperationException) { noKey = true; }
        Check(noKey, "missing key requires setup");
        foreach (var status in new[] {401,403,420,429,503})
        {
            using var errorClient = new YummyClient(new Stub((HttpStatusCode)status, "{}")) { Token = "fixture" };
            var handled = false; try { await errorClient.Get("anime"); } catch (InvalidOperationException) { handled = true; }
            Check(handled, "API error " + status + " produces actionable message");
        }
        var store = new LocalStore(root);
        Check(store.ToggleFavorite(catalog[0]), "add favorite");
        Check(new LocalStore(root).Data.Favorites.Single().Id == catalog[0].Id, "favorite survives restart");
        store.Started(catalog[0], videos[0]); store.Started(catalog[0], videos[1]);
        var reloaded = new LocalStore(root);
        Check(reloaded.Data.History.Count == 1 && reloaded.Data.History[0].Number == "2", "history resumes latest series and avoids duplicates");
        Check(!store.ToggleFavorite(catalog[0]) && new LocalStore(root).Data.Favorites.Count == 0, "remove favorite persists");
        store.SaveToken("private-fixture-key");
        Check(store.ReadToken() == "private-fixture-key" && !File.ReadAllText(Path.Combine(root, "application.key")).Contains("private-fixture-key"), "key protected with Windows DPAPI and round-trips");
        File.WriteAllText(Path.Combine(root, "library.json"), "corrupted fixture");
        var damaged = new LocalStore(root); damaged.Save();
        Check(Directory.GetFiles(root, "library.recovery-*.json").Any(), "damaged library backed up before replacement");
        await Accounts(root);
    }
    static async Task Accounts(string root)
    {
        Check(Rewards.Level(0) == 1 && Rewards.Level(49) == 1 && Rewards.Level(50) == 2 && Rewards.Level(800) == 5, "levels match database formula");
        Check(Rewards.ToNext(50) == 150 && Math.Abs(Rewards.Progress(125) - 0.5) < 1e-9, "level progress");
        Check(Rewards.Threshold(null) == TimeSpan.FromMinutes(10) && Rewards.Threshold(TimeSpan.FromMinutes(24)) == TimeSpan.FromMinutes(12) && Rewards.Threshold(TimeSpan.FromMinutes(3)) == TimeSpan.FromMinutes(5), "watch threshold between 5 and 12 minutes");
        Check(Rewards.ValidUsername("haruka_01") && !Rewards.ValidUsername("Haruka") && !Rewards.ValidUsername("ab"), "username rules");
        Check(Rewards.Error("not_enough_points") == "Не хватает очков." && Rewards.Error("Invalid login credentials").StartsWith("Неверная"), "server errors explained in Russian");
        Check(Decor.Parse("#BFAEFF", System.Windows.Media.Colors.Black) == System.Windows.Media.Color.FromRgb(0xBF, 0xAE, 0xFF) && Decor.Parse("oops", System.Windows.Media.Colors.Black) == System.Windows.Media.Colors.Black, "decoration colors parse");
        var properties = Path.Combine(root, "supabase.properties");
        File.WriteAllText(properties, "# comment\nurl=https://demo.supabase.co/\nanon_key=public-anon\n");
        var config = SupabaseConfig.Load(properties);
        Check(config.Configured && config.Url == "https://demo.supabase.co" && config.AnonKey == "public-anon", "supabase.properties parsed");
        Check(!SupabaseConfig.Load(Path.Combine(root, "missing.properties")).Configured || Environment.GetEnvironmentVariable("YORU_SUPABASE_URL") != null, "missing config disables accounts");

        Check(StreamResolver.Supports("https://kodik.info/seria/1/abc/720p") && !StreamResolver.Supports("https://example.com/x"), "native player handles Kodik only");
        var encoded = new string(Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes("//cdn.example.net/video/720.mp4:hls:manifest.m3u8")).Select(c => c is >= 'A' and <= 'Z' ? (char)('A' + (c - 'A' + 26 - 3) % 26) : c is >= 'a' and <= 'z' ? (char)('a' + (c - 'a' + 26 - 3) % 26) : c).ToArray());
        using (var links = JsonDocument.Parse("{\"links\":{\"360\":[{\"src\":\"" + encoded + "\"}],\"720\":[{\"src\":\"" + encoded.Replace("720", "720") + "\"}],\"bad\":[]}}"))
        {
            var parsed = StreamResolver.Parse(links.RootElement, 3, "https://kodik.info/seria/1");
            Check(parsed.Streams.Count == 1 && parsed.Streams[0].Url == "https://cdn.example.net/video/720.mp4:hls:manifest.m3u8" && parsed.Streams[0].Mime == "application/x-mpegURL", "Kodik stream links decoded and deduplicated");
        }
        var unsafeRejected = false; try { StreamResolver.SafeUri("https://127.0.0.1/x"); } catch (InvalidOperationException) { unsafeRejected = true; }
        Check(unsafeRejected, "proxy refuses local addresses");
        var store2 = new LocalStore(Path.Combine(root, "positions")); store2.Data.Positions["5"] = 42; store2.Save();
        Check(new LocalStore(Path.Combine(root, "positions")).Position(5) == 42, "native player position survives restart");

        var server = new FakeSupabase();
        var accountRoot = Path.Combine(root, "account");
        using (var account = new AccountClient(config, accountRoot, server))
        {
            Check(!account.SignedIn, "signed out by default");
            await account.SignIn("me@yoru.app", "secret1");
            Check(account.SignedIn && account.UserId == "u1" && server.Calls[0] == "POST /auth/v1/token?grant_type=password", "sign in stores session");
            Check(!File.ReadAllText(Path.Combine(accountRoot, "account.session")).Contains("refresh-1"), "session protected with DPAPI");
            await account.Refresh();
            Check(account.DisplayName == "Харука" && account.Owned.Contains("frame_sakura") && account.Decorations.Count == 2, "profile, decorations and inventory load");
            Check(server.LastAuthorization == "Bearer access-1" && server.LastApiKey == "public-anon", "requests use session token and anon key");
            var award = await account.Award(new Anime { Id = 10, Title = "Фрирен", Poster = "https://p/f.jpg", EpisodeCount = 28 }, 100, "1", 650);
            Check(award["awarded"]!.GetValue<bool>() && AccountClient.Int(account.Profile, "points") == 165 && server.LastBody.Contains("\"p_episode\":\"1\"") && server.LastBody.Contains("\"p_total\":28"), "award updates balance and sends list data");
            var failed = false; try { await account.Rpc("buy_decoration", new System.Text.Json.Nodes.JsonObject { ["p_id"] = "frame_gold" }); } catch (AccountException e) { failed = Rewards.Error(e.Message) == "Не хватает очков."; }
            Check(failed, "purchase errors surface as readable messages");
        }
        using (var restored = new AccountClient(config, accountRoot, server))
        {
            Check(restored.SignedIn && AccountClient.Int(restored.Profile, "points") == 165, "session and profile cache survive restart");
            restored.SignOut();
            Check(!restored.SignedIn && !File.Exists(Path.Combine(accountRoot, "account.session")), "sign out removes session");
        }
    }
    sealed class FakeSupabase : HttpMessageHandler
    {
        public List<string> Calls = []; public string LastAuthorization = "", LastApiKey = "", LastBody = "";
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
        {
            var path = request.RequestUri!.PathAndQuery;
            Calls.Add(request.Method + " " + path);
            LastAuthorization = request.Headers.Authorization?.ToString() ?? ""; LastApiKey = request.Headers.GetValues("apikey").Single();
            LastBody = request.Content == null ? "" : await request.Content.ReadAsStringAsync(ct);
            var (status, body) = path switch
            {
                "/auth/v1/token?grant_type=password" => (200, "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600,\"user\":{\"id\":\"u1\",\"email\":\"me@yoru.app\"}}"),
                "/auth/v1/logout" => (204, ""),
                _ when path.StartsWith("/rest/v1/profiles") => (200, "{\"id\":\"u1\",\"username\":\"haruka\",\"display_name\":\"Харука\",\"points\":150,\"total_earned\":300,\"episodes_watched\":20,\"equipped_frame\":\"frame_sakura\",\"showcase\":[]}"),
                _ when path.StartsWith("/rest/v1/decorations") => (200, "[{\"id\":\"frame_moon\",\"kind\":\"frame\",\"price\":0,\"style\":{\"colors\":[\"#BFAEFF\"]}},{\"id\":\"frame_sakura\",\"kind\":\"frame\",\"price\":150,\"style\":{\"colors\":[\"#FFB7D5\",\"#FF8FB8\"]}}]"),
                _ when path.StartsWith("/rest/v1/inventory") => (200, "[{\"decoration_id\":\"frame_sakura\"}]"),
                "/rest/v1/rpc/award_watch" => (200, "{\"awarded\":true,\"reason\":\"ok\",\"points\":15,\"balance\":165,\"total\":315,\"level\":3}"),
                "/rest/v1/rpc/buy_decoration" => (400, "{\"code\":\"42501\",\"message\":\"not_enough_points\"}"),
                _ => (404, "{}")
            };
            return new HttpResponseMessage((HttpStatusCode)status) { Content = new StringContent(body) };
        }
    }
    sealed class Stub(HttpStatusCode status, string body) : HttpMessageHandler
    {
        public Uri? Url; public string Key = ""; public string Language = "";
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
        {
            Url = request.RequestUri;
            Key = request.Headers.GetValues("X-Application").Single(); Language = request.Headers.GetValues("Lang").Single();
            return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body) });
        }
    }
}
