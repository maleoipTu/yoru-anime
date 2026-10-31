using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
namespace Yoru;

/// <summary>Окна подтверждения и сообщений в стиле Yoru вместо системных MessageBox.</summary>
public static class Dialogs
{
    /// <summary>Подтверждение с необязательным превью (например, украшения). Возвращает true, если нажато основное действие.</summary>
    public static bool Confirm(Window? owner, string title, string text, string yes, string no = "Отмена", FrameworkElement? preview = null, string? note = null)
    {
        var result = false;
        var window = Frame(owner, out var body, out var close);
        if (preview != null) { preview.HorizontalAlignment = HorizontalAlignment.Center; preview.Margin = new Thickness(0, 4, 0, 16); body.Children.Add(preview); }
        body.Children.Add(new TextBlock { Text = title, FontSize = 20, FontWeight = FontWeights.SemiBold, TextWrapping = TextWrapping.Wrap, TextAlignment = preview != null ? TextAlignment.Center : TextAlignment.Left });
        body.Children.Add(new TextBlock { Text = text, FontSize = 14, Foreground = Extras.Muted, TextWrapping = TextWrapping.Wrap, LineHeight = 21, Margin = new Thickness(0, 10, 0, 0), TextAlignment = preview != null ? TextAlignment.Center : TextAlignment.Left });
        if (!string.IsNullOrEmpty(note)) body.Children.Add(new Border { Background = new SolidColorBrush(Color.FromArgb(0x22, 0xFF, 0xC7, 0x68)), CornerRadius = new CornerRadius(10), Padding = new Thickness(12, 8, 12, 8), Margin = new Thickness(0, 14, 0, 0), Child = new TextBlock { Text = note, Foreground = Extras.Warm, FontSize = 13, FontWeight = FontWeights.SemiBold, TextAlignment = TextAlignment.Center } });
        var buttons = new Grid { Margin = new Thickness(0, 28, 0, 0) };
        buttons.ColumnDefinitions.Add(new ColumnDefinition()); buttons.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(12) }); buttons.ColumnDefinitions.Add(new ColumnDefinition());
        var cancel = new Button { Content = no, Padding = new Thickness(12, 13, 12, 13), IsCancel = true };
        var ok = new Button { Content = yes, Style = (Style)Application.Current.Resources["Primary"], Padding = new Thickness(12, 13, 12, 13), IsDefault = true };
        Grid.SetColumn(ok, 2); buttons.Children.Add(cancel); buttons.Children.Add(ok);
        cancel.Click += (_, _) => window.Close(); close.Click += (_, _) => window.Close();
        ok.Click += (_, _) => { result = true; window.Close(); };
        body.Children.Add(buttons);
        window.Loaded += (_, _) => ok.Focus();
        window.ShowDialog();
        return result;
    }

    /// <summary>Сообщение с одной кнопкой.</summary>
    public static void Show(Window? owner, string title, string text)
    {
        var window = Frame(owner, out var body, out var close);
        body.Children.Add(new TextBlock { Text = title, FontSize = 20, FontWeight = FontWeights.SemiBold, TextWrapping = TextWrapping.Wrap });
        body.Children.Add(new TextBlock { Text = text, FontSize = 14, Foreground = Extras.Muted, TextWrapping = TextWrapping.Wrap, LineHeight = 21, Margin = new Thickness(0, 10, 0, 0) });
        var ok = new Button { Content = "Понятно", Style = (Style)Application.Current.Resources["Primary"], Padding = new Thickness(22, 11, 22, 11), IsDefault = true, IsCancel = true, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 28, 0, 0) };
        ok.Click += (_, _) => window.Close(); close.Click += (_, _) => window.Close();
        body.Children.Add(ok);
        window.ShowDialog();
    }

    static Window Frame(Window? owner, out StackPanel body, out Button close)
    {
        owner ??= Application.Current?.MainWindow;
        var window = new Window
        {
            WindowStyle = WindowStyle.None, AllowsTransparency = true, Background = Brushes.Transparent, ResizeMode = ResizeMode.NoResize,
            SizeToContent = SizeToContent.WidthAndHeight, ShowInTaskbar = false, Title = "Yoru", FontFamily = owner?.FontFamily ?? new FontFamily("Segoe UI"),
            Foreground = Brushes.WhiteSmoke, WindowStartupLocation = owner != null && owner.IsVisible ? WindowStartupLocation.CenterOwner : WindowStartupLocation.CenterScreen,
        };
        if (owner != null && owner.IsVisible) window.Owner = owner;
        body = new StackPanel { Width = 400 };
        var root = new Grid();
        close = new Button { Content = "✕", Width = 36, Height = 36, MinWidth = 0, Padding = new Thickness(0), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, -10, -12, 0), Background = Brushes.Transparent, BorderThickness = new Thickness(0), Foreground = Extras.Muted };
        System.Windows.Automation.AutomationProperties.SetName(close, "Закрыть");
        root.Children.Add(body); root.Children.Add(close);
        var card = new Border
        {
            Margin = new Thickness(24), Padding = new Thickness(32, 28, 32, 28), CornerRadius = new CornerRadius(24),
            Background = new LinearGradientBrush(Color.FromRgb(0x1E, 0x1E, 0x26), Color.FromRgb(0x14, 0x14, 0x19), 90),
            BorderBrush = new LinearGradientBrush(Color.FromArgb(0x99, 0xFF, 0x4D, 0x67), Color.FromArgb(0x33, 0xFF, 0x8A, 0x3D), 45), BorderThickness = new Thickness(1.2),
            Effect = new System.Windows.Media.Effects.DropShadowEffect { BlurRadius = 30, ShadowDepth = 6, Opacity = .55, Color = Colors.Black },
            Child = root, RenderTransformOrigin = new Point(.5, .5), RenderTransform = new ScaleTransform(.94, .94), Opacity = 0,
        };
        card.MouseLeftButtonDown += (_, e) => { if (e.ButtonState == MouseButtonState.Pressed) try { window.DragMove(); } catch (InvalidOperationException) { } };
        window.Content = card;
        window.Loaded += (_, _) =>
        {
            var duration = TimeSpan.FromMilliseconds(160);
            card.BeginAnimation(UIElement.OpacityProperty, new DoubleAnimation(1, duration));
            var grow = new DoubleAnimation(1, duration) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
            ((ScaleTransform)card.RenderTransform).BeginAnimation(ScaleTransform.ScaleXProperty, grow);
            ((ScaleTransform)card.RenderTransform).BeginAnimation(ScaleTransform.ScaleYProperty, grow);
        };
        window.KeyDown += (_, e) => { if (e.Key == Key.Escape) window.Close(); };
        return window;
    }
}
