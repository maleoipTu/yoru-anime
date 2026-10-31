using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>
/// Полноэкранный вход и регистрация. Пока пользователь не вошёл, главное окно закрыто этим экраном.
/// Фон — наклонная мозаика постеров из образца каталога с коралловым свечением.
/// </summary>
public sealed class AuthView : UserControl
{
    readonly AccountClient account;
    readonly Func<Task> signedIn;
    bool registering, busy;
    readonly TextBlock title = new() { FontSize = 34, FontWeight = FontWeights.Bold, HorizontalAlignment = HorizontalAlignment.Center };
    readonly TextBlock subtitle = new() { FontSize = 15, Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)), HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center, Margin = new Thickness(0, 8, 0, 0), TextWrapping = TextWrapping.Wrap, MaxWidth = 420 };
    readonly TextBox email = new() { FontSize = 15 }, nick = new() { FontSize = 15, MaxLength = 20 };
    readonly PasswordBox password = new() { FontSize = 15 };
    readonly TextBlock message = new() { FontSize = 13, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 12, 0, 0), Visibility = Visibility.Collapsed };
    readonly Button submit = new() { FontSize = 16, Padding = new Thickness(18, 14, 18, 14), HorizontalAlignment = HorizontalAlignment.Stretch, IsDefault = true };
    readonly Button signIn = new() { Content = "Вход", Padding = new Thickness(0, 10, 0, 10) }, signUp = new() { Content = "Регистрация", Padding = new Thickness(0, 10, 0, 10) };
    readonly Button forgot = new() { Content = "Забыли пароль?", Background = Brushes.Transparent, Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x8A, 0x9B)), HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 10, 0, 0) };
    readonly StackPanel nickRow = new();

    public AuthView(AccountClient client, Func<Task> onSignedIn)
    {
        account = client; signedIn = onSignedIn;
        Background = new SolidColorBrush(Color.FromRgb(0x0E, 0x0E, 0x11));
        var root = new Grid { ClipToBounds = true };
        root.Children.Add(Mosaic());
        root.Children.Add(new Border { Background = new LinearGradientBrush(new GradientStopCollection { new(Color.FromArgb(0x55, 0x0E, 0x0E, 0x11), 0), new(Color.FromArgb(0xEE, 0x0E, 0x0E, 0x11), 0.55), new(Color.FromRgb(0x0E, 0x0E, 0x11), 1) }, 90) });
        root.Children.Add(new Border { Background = new RadialGradientBrush(Color.FromArgb(0x55, 0xFF, 0x4D, 0x67), Colors.Transparent) { Center = new Point(0.15, 0.4), GradientOrigin = new Point(0.15, 0.4), RadiusX = 0.6, RadiusY = 0.8 } });
        root.Children.Add(new Border { Background = new RadialGradientBrush(Color.FromArgb(0x40, 0xFF, 0x8A, 0x3D), Colors.Transparent) { Center = new Point(0.9, 0.8), GradientOrigin = new Point(0.9, 0.8), RadiusX = 0.5, RadiusY = 0.7 } });

        var page = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Width = 460, Margin = new Thickness(0, 40, 0, 40) };
        page.Children.Add(new Image { Source = new BitmapImage(new Uri("pack://application:,,,/Assets/yoru-256.png")), Width = 88, Height = 88, Effect = new DropShadowEffect { BlurRadius = 30, ShadowDepth = 0, Color = Color.FromRgb(0xFF, 0x4D, 0x67), Opacity = 0.6 } });
        title.Margin = new Thickness(0, 18, 0, 0); page.Children.Add(title); page.Children.Add(subtitle);

        var card = new StackPanel();
        var tabs = new UniformGrid2(); tabs.Add(signIn); tabs.Add(signUp);
        signIn.Click += (_, _) => Mode(false); signUp.Click += (_, _) => Mode(true);
        card.Children.Add(new Border { Background = new SolidColorBrush(Color.FromRgb(0x1F, 0x1F, 0x26)), CornerRadius = new CornerRadius(14), Padding = new Thickness(4), Child = tabs.Grid });
        card.Children.Add(Field("Почта", email, 24));
        nickRow.Children.Add(Field("Ник — латиница, цифры и _", nick, 16)); card.Children.Add(nickRow);
        card.Children.Add(Field("Пароль", password, 16));
        card.Children.Add(message);
        submit.Style = (Style)Application.Current.Resources["Primary"]; submit.Margin = new Thickness(0, 24, 0, 0);
        submit.Click += async (_, _) => await Submit();
        card.Children.Add(submit);
        forgot.Click += async (_, _) => await Recover();
        card.Children.Add(forgot);
        page.Children.Add(new Border { Margin = new Thickness(0, 32, 0, 0), Padding = new Thickness(32), CornerRadius = new CornerRadius(24), Background = (Brush)Application.Current.Resources["PanelGradient"], BorderBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)), BorderThickness = new Thickness(1), Effect = (Effect)Application.Current.Resources["Shadow"], Child = card });
        page.Children.Add(new TextBlock { Text = "Профиль, очки, списки и украшения хранятся в аккаунте Yoru и синхронизируются между ПК и телефоном.", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)), TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 18, 0, 0) });
        root.Children.Add(new ScrollViewer { Content = page, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
        Content = root;
        email.Text = account.Email;
        Mode(false);
    }

    public void FocusFirst() => Dispatcher.BeginInvoke(() => (email.Text.Length > 0 ? (Control)password : email).Focus());

    static FrameworkElement Field(string label, Control input, double top)
    {
        var box = new StackPanel { Margin = new Thickness(0, top, 0, 0) };
        box.Children.Add(new TextBlock { Text = label, FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8)), Margin = new Thickness(0, 0, 0, 8) });
        input.Padding = new Thickness(14, 12, 14, 12); System.Windows.Automation.AutomationProperties.SetName(input, label);
        box.Children.Add(input);
        return box;
    }

    /// <summary>Мозаика постеров из образца каталога, наклонённая и полупрозрачная.</summary>
    static FrameworkElement Mosaic()
    {
        var grid = new WrapPanel { Width = 1500, Opacity = 0.35, RenderTransformOrigin = new Point(0.5, 0.5), RenderTransform = new RotateTransform(-10), Margin = new Thickness(-160, -140, -160, 0), VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Center, IsHitTestVisible = false };
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Assets", "preview.json")));
            var posters = ApiParser.Catalog(doc.RootElement);
            for (var i = 0; i < 18 && posters.Count > 0; i++)
            {
                var anime = posters[i % posters.Count];
                if (!Uri.TryCreate(anime.ImagePath, UriKind.Absolute, out var uri)) continue;
                grid.Children.Add(new Border { Width = 170, Height = 240, Margin = new Thickness(10), CornerRadius = new CornerRadius(14), ClipToBounds = true, Child = new Image { Source = new BitmapImage(uri), Stretch = Stretch.UniformToFill } });
            }
        }
        catch (Exception e) when (e is IOException or JsonException or NotSupportedException or UriFormatException) { }
        return grid;
    }

    void Mode(bool register)
    {
        registering = register;
        title.Text = register ? "Создай аккаунт" : "С возвращением";
        subtitle.Text = register ? "Получай очки за серии, открывай достижения и укрась профиль." : "Твои списки, друзья и очки ждут.";
        foreach (var b in new[] { signIn, signUp })
        {
            var on = (b == signUp) == register;
            b.Background = on ? (Brush)Application.Current.Resources["AccentGradient"] : Brushes.Transparent;
            b.Foreground = on ? Brushes.White : new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8));
            b.FontWeight = on ? FontWeights.SemiBold : FontWeights.Normal;
        }
        nickRow.Visibility = register ? Visibility.Visible : Visibility.Collapsed;
        forgot.Visibility = register ? Visibility.Collapsed : Visibility.Visible;
        submit.Content = register ? "Создать аккаунт" : "Войти";
        message.Visibility = Visibility.Collapsed;
    }
    void Show(string text, bool good)
    {
        message.Text = text; message.Visibility = Visibility.Visible;
        message.Foreground = new SolidColorBrush(good ? Color.FromRgb(0x4A, 0xDE, 0x80) : Color.FromRgb(0xFF, 0x8A, 0x8A));
    }
    async Task Submit()
    {
        if (busy) return;
        var mail = email.Text.Trim(); var pass = password.Password; var name = nick.Text.Trim().ToLowerInvariant();
        var problem = !Rewards.ValidEmail(mail) ? "Проверь адрес почты." : pass.Length < 6 ? "Пароль — минимум 6 символов." : registering && !Rewards.ValidUsername(name) ? "Ник: 3–20 символов, латиница, цифры и _." : "";
        if (problem.Length > 0) { Show(problem, false); return; }
        busy = true; submit.IsEnabled = false; submit.Content = "Подождите…";
        try
        {
            if (registering)
            {
                if (!await account.SignUp(mail, pass, name)) { Mode(false); Show($"Мы отправили письмо на {mail}. Подтверди почту и войди.", true); return; }
            }
            else await account.SignIn(mail, pass);
            await signedIn();
        }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or JsonException or InvalidOperationException) { Show(Rewards.Error(e.Message), false); }
        finally { busy = false; submit.IsEnabled = true; submit.Content = registering ? "Создать аккаунт" : "Войти"; }
    }
    async Task Recover()
    {
        var mail = email.Text.Trim();
        if (!Rewards.ValidEmail(mail)) { Show("Введи почту — пришлём ссылку для сброса пароля.", false); return; }
        try { await account.Recover(mail); Show($"Ссылка для сброса пароля отправлена на {mail}.", true); }
        catch (Exception e) when (e is AccountException or HttpRequestException or TaskCanceledException or InvalidOperationException) { Show(Rewards.Error(e.Message), false); }
    }

    /// <summary>Две кнопки-вкладки одинаковой ширины.</summary>
    sealed class UniformGrid2
    {
        public System.Windows.Controls.Primitives.UniformGrid Grid { get; } = new() { Columns = 2 };
        public void Add(UIElement e) => Grid.Children.Add(e);
    }
}
