using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace Yoru;

/// <summary>
/// Список серий плитками: нажатие на плитку открывает серию, кружок в углу отмечает её просмотренной (или снимает отметку).
/// Просмотренные серии приглушены и помечены галочкой, текущая — обведена акцентом.
/// </summary>
public static class EpisodeGrid
{
    static readonly Brush Tile = new SolidColorBrush(Color.FromRgb(0x1F, 0x1F, 0x26));
    static readonly Brush Seen = new SolidColorBrush(Color.FromRgb(0x18, 0x2A, 0x22));
    static readonly Brush SeenMark = new SolidColorBrush(Color.FromRgb(0x3D, 0xD6, 0x8C));
    static readonly Brush Muted = new SolidColorBrush(Color.FromRgb(0x9C, 0x9C, 0xA8));
    static readonly Brush Accent = new SolidColorBrush(Color.FromRgb(0xFF, 0x4D, 0x67));

    static readonly Brush SeenTile = new LinearGradientBrush(Color.FromRgb(0x3A, 0x17, 0x2B), Color.FromRgb(0x1E, 0x15, 0x2C), 45);
    static readonly Brush SeenText = new SolidColorBrush(Color.FromRgb(0xFF, 0xB3, 0xC1));

    /// <summary>
    /// Плитки серий в духе Yoru: просмотренная серия «засыпает» — тёплый ночной градиент, луна в углу и полная полоска снизу;
    /// начатая серия показывает, сколько уже просмотрено; текущая — подсвечена. Луна в углу (появляется при наведении)
    /// или правый клик по плитке — отметить вручную.
    /// </summary>
    public static FrameworkElement Build(IReadOnlyList<Episode> episodes, AnimeProgress? progress, string? current, Action<Episode> open, Action<Episode> toggle)
    {
        var wrap = new WrapPanel();
        foreach (var episode in episodes)
        {
            var watched = progress?.IsWatched(episode.Number) ?? false;
            var isCurrent = episode.Number == current;
            var started = !watched && progress != null && progress.Episode == episode.Number && progress.Duration > 0 && progress.Position > 5 ? Math.Min(1, progress.Position / progress.Duration) : 0;
            var tile = new Grid { Width = 70, Height = 54, Margin = new Thickness(0, 0, 8, 8), Cursor = System.Windows.Input.Cursors.Hand, Background = Brushes.Transparent };
            var body = new Border
            {
                CornerRadius = new CornerRadius(12), Background = watched ? SeenTile : Tile, ClipToBounds = true,
                BorderBrush = isCurrent ? Accent : watched ? new SolidColorBrush(Color.FromArgb(0x55, 0xFF, 0x8A, 0x9B)) : new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)),
                BorderThickness = new Thickness(isCurrent ? 2 : 1),
                Effect = isCurrent ? new System.Windows.Media.Effects.DropShadowEffect { Color = Color.FromRgb(0xFF, 0x4D, 0x67), BlurRadius = 14, ShadowDepth = 0, Opacity = 0.6 } : null
            };
            var inner = new Grid();
            inner.Children.Add(new TextBlock { Text = episode.Number, FontSize = 17, FontWeight = FontWeights.SemiBold, Foreground = watched ? SeenText : Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis, TextWrapping = TextWrapping.NoWrap, Margin = new Thickness(8, 0, 8, 2) });
            if (isCurrent) inner.Children.Add(new TextBlock { Text = "сейчас", FontSize = 9, Foreground = Accent, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(0, 0, 0, 6) });
            // Полоска снизу: целиком — просмотрено, частично — где остановился.
            var fill = watched ? 1 : started;
            if (fill > 0)
            {
                var bar = new Border { Height = 3, VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Left, Background = (Brush)Application.Current.FindResource("AccentGradient") };
                inner.SizeChanged += (_, e) => bar.Width = e.NewSize.Width * fill;
                inner.Children.Add(bar);
            }
            body.Child = inner;
            tile.Children.Add(body);
            // Луна в углу: у просмотренной — всегда, у остальных — при наведении.
            var moon = new Border
            {
                Width = 20, Height = 20, CornerRadius = new CornerRadius(10), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 4, 4, 0),
                Background = watched ? (Brush)Application.Current.FindResource("AccentGradient") : new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)),
                BorderThickness = new Thickness(0),
                Child = new TextBlock { Text = "☾", FontSize = 11, Foreground = watched ? Brushes.White : SeenText, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, -2, 0, 0) },
                Opacity = watched ? 1 : 0, ToolTip = watched ? "Снять отметку «просмотрено»" : "Отметить просмотренной"
            };
            moon.MouseLeftButtonUp += (_, e) => { e.Handled = true; toggle(episode); };
            moon.MouseLeftButtonDown += (_, e) => e.Handled = true;
            tile.Children.Add(moon);
            tile.MouseEnter += (_, _) => { if (!watched) moon.Opacity = 1; body.Background = watched ? SeenTile : new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x33)); };
            tile.MouseLeave += (_, _) => { if (!watched) moon.Opacity = 0; body.Background = watched ? SeenTile : Tile; };
            tile.MouseLeftButtonUp += (_, _) => open(episode);
            tile.MouseRightButtonUp += (_, e) => { e.Handled = true; toggle(episode); };
            tile.ToolTip = episode.Label + (watched ? " · просмотрено" : started > 0 ? $" · просмотрено {started:P0}" : "") + (isCurrent ? " · сейчас" : "") + "\nПравый клик или луна — отметить";
            // Клавиатура: плитка фокусируемая, Enter — открыть, пробел — отметить.
            tile.Focusable = true; tile.FocusVisualStyle = (Style)Application.Current.FindResource("KeyboardFocus");
            tile.KeyDown += (_, e) => { if (e.Key == System.Windows.Input.Key.Enter) { e.Handled = true; open(episode); } else if (e.Key == System.Windows.Input.Key.Space) { e.Handled = true; toggle(episode); } };
            AutomationProperties(tile, episode.Label + (watched ? ", просмотрено" : "") + (isCurrent ? ", сейчас" : "") + ". Enter — открыть, пробел — отметить просмотренной");
            wrap.Children.Add(tile);
        }
        return wrap;
    }
    static void AutomationProperties(UIElement e, string name) => System.Windows.Automation.AutomationProperties.SetName(e, name);
    static ControlTemplate Round()
    {
        var border = new FrameworkElementFactory(typeof(Border));
        border.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
        border.SetValue(Border.BackgroundProperty, new TemplateBindingExtension(Control.BackgroundProperty));
        border.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromArgb(0x60, 0xFF, 0xFF, 0xFF)));
        border.SetValue(Border.BorderThicknessProperty, new Thickness(1));
        var content = new FrameworkElementFactory(typeof(ContentPresenter));
        content.SetValue(FrameworkElement.HorizontalAlignmentProperty, HorizontalAlignment.Center);
        content.SetValue(FrameworkElement.VerticalAlignmentProperty, VerticalAlignment.Center);
        border.AppendChild(content);
        return new ControlTemplate(typeof(Button)) { VisualTree = border };
    }
    static ControlTemplate Outlined(Button b)
    {
        var border = new FrameworkElementFactory(typeof(Border));
        border.SetValue(Border.CornerRadiusProperty, new CornerRadius(9));
        border.SetValue(Border.BackgroundProperty, new TemplateBindingExtension(Control.BackgroundProperty));
        border.SetValue(Border.BorderBrushProperty, new TemplateBindingExtension(Control.BorderBrushProperty));
        border.SetValue(Border.BorderThicknessProperty, new TemplateBindingExtension(Control.BorderThicknessProperty));
        border.SetValue(Border.PaddingProperty, new TemplateBindingExtension(Control.PaddingProperty));
        var content = new FrameworkElementFactory(typeof(ContentPresenter));
        content.SetValue(FrameworkElement.HorizontalAlignmentProperty, HorizontalAlignment.Center);
        content.SetValue(FrameworkElement.VerticalAlignmentProperty, VerticalAlignment.Center);
        border.AppendChild(content);
        return new ControlTemplate(typeof(Button)) { VisualTree = border };
    }

    /// <summary>Трекер франшизы: части в порядке просмотра, отметка «просмотрено», текущая серия, общий прогресс.</summary>
    public static FrameworkElement Franchise(Anime anime, LocalStore store, Action<FranchisePart> open, Action refresh)
    {
        var parts = anime.Franchise;
        var panel = new StackPanel { Margin = new Thickness(0, 24, 0, 0) };
        if (parts.Count < 2) return panel;
        var done = parts.Count(f => Progress.Get(store, f.AnimeId)?.Complete == true);
        var head = new DockPanel();
        head.Children.Add(new TextBlock { Text = "Порядок просмотра", FontSize = 18, FontWeight = FontWeights.SemiBold });
        head.Children.Add(new TextBlock { Text = $"просмотрено {done} из {parts.Count}", Foreground = Muted, FontSize = 12, Margin = new Thickness(12, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center });
        panel.Children.Add(head);
        var bar = new Grid { Height = 6, Margin = new Thickness(0, 10, 0, 12) };
        bar.Children.Add(new Border { Background = Tile, CornerRadius = new CornerRadius(3) });
        bar.Children.Add(new Border { Background = (Brush)Application.Current.FindResource("AccentGradient"), CornerRadius = new CornerRadius(3), HorizontalAlignment = HorizontalAlignment.Left, Width = 0, Tag = (double)done / parts.Count });
        bar.SizeChanged += (_, e) => ((Border)bar.Children[1]).Width = e.NewSize.Width * (double)((Border)bar.Children[1]).Tag;
        panel.Children.Add(bar);
        for (var i = 0; i < parts.Count; i++)
        {
            var part = parts[i]; var p = Progress.Get(store, part.AnimeId); var here = part.AnimeId == anime.Id;
            var row = new Grid { Margin = new Thickness(0, 0, 0, 8) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            row.Children.Add(new TextBlock { Text = (i + 1).ToString(), Foreground = Muted, FontSize = 13, VerticalAlignment = VerticalAlignment.Center });
            var state = p?.Complete == true ? "☾ просмотрено" : p is { Episode.Length: > 0 } ? $"▶ серия {p.Episode}" : p?.WatchedCount > 0 ? $"просмотрено серий: {p.WatchedCount}" : "";
            var info = new StackPanel();
            info.Children.Add(new TextBlock { Text = part.Title, FontSize = 14, FontWeight = here ? FontWeights.SemiBold : FontWeights.Normal, Foreground = here ? Accent : Brushes.White, TextTrimming = TextTrimming.CharacterEllipsis, TextWrapping = TextWrapping.NoWrap });
            info.Children.Add(new TextBlock { Text = string.Join("  ·  ", new[] { part.Relation, part.Type, part.Year > 0 ? part.Year.ToString() : "", here ? "открыто сейчас" : "", state }.Where(s => s.Length > 0)), FontSize = 12, Foreground = Muted, TextWrapping = TextWrapping.NoWrap, TextTrimming = TextTrimming.CharacterEllipsis });
            var openButton = new Button { Content = info, Background = here ? new SolidColorBrush(Color.FromRgb(0x24, 0x18, 0x1E)) : Tile, Padding = new Thickness(12, 8, 12, 8), HorizontalContentAlignment = HorizontalAlignment.Stretch, IsEnabled = !here };
            openButton.Click += (_, _) => open(part);
            Grid.SetColumn(openButton, 1); row.Children.Add(openButton);
            var complete = p?.Complete == true;
            var check = new Button { Content = complete ? "☾" : "○", Width = 38, Margin = new Thickness(8, 0, 0, 0), Foreground = complete ? SeenText : Muted, Background = complete ? SeenTile : Tile, FontSize = 16, ToolTip = complete ? "Снять отметку" : "Отметить часть просмотренной" };
            System.Windows.Automation.AutomationProperties.SetName(check, (complete ? "Снять отметку: " : "Отметить просмотренной: ") + part.Title);
            check.Click += (_, _) => { Progress.SetWatched(store, new Anime { Id = part.AnimeId, Title = part.Title, Poster = part.Poster }, Progress.All, !complete); refresh(); };
            Grid.SetColumn(check, 2); row.Children.Add(check);
            panel.Children.Add(row);
        }
        return panel;
    }
}
