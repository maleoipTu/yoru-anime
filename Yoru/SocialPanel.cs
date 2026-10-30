using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace Yoru;

/// <summary>Блок «Мой список и оценки» в окне аниме: статус списка, оценка, отзыв и отзывы других пользователей Yoru.</summary>
public sealed class SocialPanel
{
    readonly AccountClient account = AccountClient.Shared;
    readonly Window owner;
    readonly LocalStore store;
    public Anime Anime { get; set; }
    public Border Root { get; }
    readonly ComboBox listBox = new() { MinWidth = 190, Margin = new Thickness(0, 0, 12, 0) };
    readonly TextBlock rating = new() { Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0x98, 0xB0)), FontSize = 13, Margin = new Thickness(0, 12, 0, 0), TextWrapping = TextWrapping.Wrap };
    readonly StackPanel reviews = new() { Margin = new Thickness(0, 8, 0, 0) };
    readonly StackPanel editor = new() { Visibility = Visibility.Collapsed, Margin = new Thickness(0, 12, 0, 0) };
    readonly Button rateButton = new() { Content = "Оценить", Margin = new Thickness(0, 0, 0, 0) };
    readonly TextBlock message = new() { Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x9A, 0x8B)), FontSize = 12, Margin = new Thickness(0, 8, 0, 0), TextWrapping = TextWrapping.Wrap };
    JsonObject? mine;
    bool syncing;

    public SocialPanel(Window window, Anime anime, LocalStore local)
    {
        owner = window; Anime = anime; store = local;
        var panel = new StackPanel();
        panel.Children.Add(new TextBlock { Text = "Мой список и оценки", FontSize = 18, FontWeight = FontWeights.SemiBold });
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 12, 0, 0) };
        listBox.Items.Add("Не в списке"); foreach (var s in AccountClient.Lists) listBox.Items.Add(AccountClient.ListLabel(s));
        System.Windows.Automation.AutomationProperties.SetName(listBox, "Список");
        listBox.SelectionChanged += async (_, _) => { if (!syncing) await ChangeList(); };
        rateButton.Click += (_, _) => ToggleEditor();
        actions.Children.Add(listBox); actions.Children.Add(rateButton);
        panel.Children.Add(actions); panel.Children.Add(editor); panel.Children.Add(message); panel.Children.Add(rating); panel.Children.Add(reviews);
        Root = new Border { Background = new SolidColorBrush(Color.FromRgb(0x19, 0x19, 0x23)), BorderBrush = new SolidColorBrush(Color.FromRgb(0x2C, 0x2D, 0x3A)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(14), Padding = new Thickness(20), Margin = new Thickness(0, 0, 0, 18), Child = panel };
        if (!account.Config.Configured) Root.Visibility = Visibility.Collapsed;
    }

    bool NeedAccount()
    {
        if (account.SignedIn) return false;
        message.Text = "Войди в профиль (карточка слева в главном окне), чтобы вести списки и писать отзывы.";
        return true;
    }

    public async Task Refresh()
    {
        if (!account.Config.Configured) return;
        try
        {
            await account.EnsureCatalog();
            var score = await account.Rating(Anime.Id);
            var all = await account.Reviews(Anime.Id);
            var entry = account.SignedIn ? await account.ListEntry(Anime.Id) : new JsonObject();
            mine = all.OfType<JsonObject>().FirstOrDefault(r => AccountClient.Text(r, "user_id") == account.UserId && account.SignedIn);
            syncing = true;
            var status = AccountClient.Text(entry, "status");
            listBox.SelectedIndex = Array.IndexOf(AccountClient.Lists, status) + 1;
            syncing = false;
            rateButton.Content = mine == null ? "Оценить" : $"Моя оценка · {AccountClient.Int(mine, "score")}";
            var count = AccountClient.Int(score, "count");
            rating.Text = count == 0 ? "Пока никто из Yoru не оценил — будь первым." : $"Yoru: ★ {score["average"]} · оценок: {count}" + (AccountClient.Int(entry, "episodes") > 0 ? $"   ·   Засчитано серий: {AccountClient.Int(entry, "episodes")}" : "");
            reviews.Children.Clear();
            foreach (var r in all.OfType<JsonObject>().Where(r => AccountClient.Text(r, "body").Length > 0).Take(10))
            {
                var box = new StackPanel { Margin = new Thickness(0, 6, 0, 6) };
                var person = Decor.Person(r["profiles"], account, $"★ {AccountClient.Int(r, "score")} из 10", 36);
                var username = AccountClient.Text(r["profiles"], "username");
                var open = new Button { Content = person, Background = Brushes.Transparent, Padding = new Thickness(0), HorizontalContentAlignment = HorizontalAlignment.Left, HorizontalAlignment = HorizontalAlignment.Left };
                open.Click += (_, _) => new ProfileWindow(account, store, username) { Owner = owner }.ShowDialog();
                box.Children.Add(open);
                box.Children.Add(new TextBlock { Text = AccountClient.Text(r, "body"), Foreground = new SolidColorBrush(Color.FromRgb(0xCF, 0xCD, 0xDC)), FontSize = 14, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(48, 2, 0, 0) });
                reviews.Children.Add(box);
            }
        }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException)
        { rating.Text = "Оценки сейчас недоступны."; syncing = false; }
    }

    async Task ChangeList()
    {
        if (NeedAccount()) { syncing = true; listBox.SelectedIndex = 0; syncing = false; return; }
        var status = listBox.SelectedIndex <= 0 ? "" : AccountClient.Lists[listBox.SelectedIndex - 1];
        try { var result = await account.SetList(Anime, status); message.Text = AccountClient.Announce(result); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or InvalidOperationException) { message.Text = Rewards.Error(e.Message); }
    }

    void ToggleEditor()
    {
        if (NeedAccount()) return;
        if (editor.Visibility == Visibility.Visible) { editor.Visibility = Visibility.Collapsed; return; }
        editor.Children.Clear();
        var score = mine == null ? 0 : AccountClient.Int(mine, "score");
        var buttons = new List<Button>();
        var row = new StackPanel { Orientation = Orientation.Horizontal };
        void Paint() { for (var i = 0; i < buttons.Count; i++) { buttons[i].Background = i + 1 == score ? new SolidColorBrush(Color.FromRgb(0xB3, 0xA2, 0xFF)) : new SolidColorBrush(Color.FromRgb(0x25, 0x24, 0x30)); buttons[i].Foreground = i + 1 == score ? new SolidColorBrush(Color.FromRgb(0x17, 0x11, 0x26)) : Brushes.WhiteSmoke; } }
        for (var i = 1; i <= 10; i++)
        {
            var value = i;
            var b = new Button { Content = value.ToString(), Width = 42, Padding = new Thickness(0, 8, 0, 8), Margin = new Thickness(0, 0, 6, 0) };
            System.Windows.Automation.AutomationProperties.SetName(b, "Оценка " + value);
            b.Click += (_, _) => { score = value; Paint(); };
            buttons.Add(b); row.Children.Add(b);
        }
        Paint();
        editor.Children.Add(row);
        var text = new TextBox { Text = mine == null ? "" : AccountClient.Text(mine, "body"), AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 90, MaxLength = 1000, Margin = new Thickness(0, 12, 0, 12) };
        System.Windows.Automation.AutomationProperties.SetName(text, "Текст отзыва");
        editor.Children.Add(text);
        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        var save = new Button { Content = "Сохранить отзыв", Style = (Style)Application.Current.Resources["Primary"] };
        var delete = new Button { Content = "Удалить оценку", Margin = new Thickness(12, 0, 0, 0), Visibility = mine == null ? Visibility.Collapsed : Visibility.Visible };
        async Task Submit(int value)
        {
            if (value == 0 && mine == null) { message.Text = "Сначала выбери оценку от 1 до 10."; return; }
            try { var result = await account.SetReview(Anime, value, text.Text); message.Text = AccountClient.Announce(result); editor.Visibility = Visibility.Collapsed; await Refresh(); }
            catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or InvalidOperationException) { message.Text = Rewards.Error(e.Message); }
        }
        save.Click += async (_, _) => { if (score == 0) { message.Text = "Сначала выбери оценку от 1 до 10."; return; } await Submit(score); };
        delete.Click += async (_, _) => await Submit(0);
        actions.Children.Add(save); actions.Children.Add(delete);
        editor.Children.Add(actions);
        editor.Visibility = Visibility.Visible;
    }
}
