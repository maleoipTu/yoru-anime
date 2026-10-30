using System.Globalization;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
namespace Yoru;

/// <summary>Отрисовка украшений профиля по стилю из таблицы decorations.</summary>
public static class Decor
{
    static readonly Color Accent = Color.FromRgb(0xFF, 0x4D, 0x67);
    public static Color Parse(string? hex, Color fallback)
    {
        if (hex is not { Length: 7 or 9 } || hex[0] != '#') return fallback;
        if (!uint.TryParse(hex[1..], NumberStyles.HexNumber, CultureInfo.InvariantCulture, out var v)) return fallback;
        return hex.Length == 7 ? Color.FromRgb((byte)(v >> 16), (byte)(v >> 8), (byte)v) : Color.FromArgb((byte)(v >> 24), (byte)(v >> 16), (byte)(v >> 8), (byte)v);
    }
    public static Color[] Colors(JsonObject style, Color fallback)
    {
        var colors = (style["colors"] as JsonArray)?.Select(c => Parse(c is JsonValue v && v.TryGetValue<string>(out var s) ? s : null, fallback)).ToArray() ?? [];
        return colors.Length == 0 ? [fallback] : colors;
    }
    static Brush Gradient(Color[] colors, Point start, Point end)
    {
        if (colors.Length == 1) return new SolidColorBrush(colors[0]);
        var brush = new LinearGradientBrush { StartPoint = start, EndPoint = end };
        for (var i = 0; i < colors.Length; i++) brush.GradientStops.Add(new GradientStop(colors[i], i / (double)(colors.Length - 1)));
        return brush;
    }
    /// <summary>Цвет ника: один цвет или горизонтальный градиент.</summary>
    public static Brush NameBrush(JsonObject style) => Gradient(Colors(style, Color.FromRgb(0xF4, 0xF2, 0xFA)), new Point(0, 0.5), new Point(1, 0.5));
    public static TextBlock Badge(JsonObject style, double size)
    {
        var glyph = AccountClient.Text(style, "glyph");
        return new TextBlock { Text = glyph, FontSize = size, Foreground = new SolidColorBrush(Parse(AccountClient.Text(style, "color"), Accent)), VerticalAlignment = VerticalAlignment.Center, Visibility = glyph.Length > 0 ? Visibility.Visible : Visibility.Collapsed };
    }

    /// <summary>Круглый аватар (постер или буква) с рамкой: градиент, свечение и вращение для анимированных рамок.</summary>
    public static FrameworkElement Avatar(double size, JsonObject frame, string name, string avatarUrl)
    {
        var colors = Colors(frame, Accent);
        var glow = frame["glow"] is JsonValue g && g.TryGetValue<bool>(out var gv) && gv;
        var animated = frame["animated"] is JsonValue a && a.TryGetValue<bool>(out var av) && av;
        var stroke = Math.Max(2, size * 0.07);
        var ringSize = size - (glow ? size * 0.08 : 0);
        var innerSize = ringSize - stroke * 2 - size * 0.06;
        var grid = new Grid { Width = size, Height = size };
        var inner = new Ellipse { Width = innerSize, Height = innerSize, Fill = new SolidColorBrush(Color.FromRgb(0x3A, 0x18, 0x20)) };
        grid.Children.Add(inner);
        if (Media.IsAnimated(avatarUrl))
        {
            var view = Media.View(avatarUrl); view.Width = view.Height = innerSize;
            view.Clip = new EllipseGeometry(new Point(innerSize / 2, innerSize / 2), innerSize / 2, innerSize / 2);
            view.HorizontalAlignment = HorizontalAlignment.Center; view.VerticalAlignment = VerticalAlignment.Center;
            grid.Children.Add(view);
        }
        else if (Uri.TryCreate(avatarUrl, UriKind.Absolute, out var uri) && uri.Scheme == "https")
        {
            try { inner.Fill = new ImageBrush(new BitmapImage(uri)) { Stretch = Stretch.UniformToFill }; }
            catch (Exception e) when (e is NotSupportedException or System.IO.IOException) { }
        }
        else
        {
            var letter = name.Length > 0 ? char.ToUpper(name[0], CultureInfo.GetCultureInfo("ru-RU")).ToString() : "?";
            grid.Children.Add(new TextBlock { Text = letter, FontSize = innerSize * 0.48, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(Accent), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center });
        }
        var brush = Gradient(colors, new Point(0, 0), new Point(1, 1));
        var ring = new Ellipse { Width = ringSize, Height = ringSize, Stroke = brush, StrokeThickness = stroke };
        if (glow) ring.Effect = new DropShadowEffect { Color = colors[0], BlurRadius = size * 0.2, ShadowDepth = 0, Opacity = 0.85 };
        if (animated && brush is LinearGradientBrush linear && SystemParameters.ClientAreaAnimation)
        {
            var rotate = new RotateTransform(0, 0.5, 0.5);
            linear.RelativeTransform = rotate;
            rotate.BeginAnimation(RotateTransform.AngleProperty, new DoubleAnimation(0, 360, TimeSpan.FromSeconds(6)) { RepeatBehavior = RepeatBehavior.Forever });
        }
        grid.Children.Add(ring);
        System.Windows.Automation.AutomationProperties.SetName(grid, "Аватар " + name);
        return grid;
    }

    /// <summary>Строка пользователя: аватар с рамкой, ник в его цвете, значок и подпись.</summary>
    public static FrameworkElement Person(JsonNode? p, AccountClient account, string subtitle, double size = 44)
    {
        var name = AccountClient.Text(p, "display_name").Trim() is { Length: > 0 } n ? n : AccountClient.Text(p, "username");
        string Equipped(string key, string fallback) => AccountClient.Text(p, key) is { Length: > 0 } v ? v : fallback;
        var row = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
        var avatar = Avatar(size, account.Style(Equipped("equipped_frame", "frame_moon")), name, AccountClient.Text(p, "avatar_url"));
        DockPanel.SetDock(avatar, Dock.Left); row.Children.Add(avatar);
        var text = new StackPanel { Margin = new Thickness(12, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
        var nameRow = new StackPanel { Orientation = Orientation.Horizontal };
        nameRow.Children.Add(new TextBlock { Text = name, FontSize = 15, FontWeight = FontWeights.SemiBold, Foreground = NameBrush(account.Style(Equipped("equipped_color", "color_default"))) });
        var badge = Badge(account.Style(AccountClient.Text(p, "equipped_badge")), 15); badge.Margin = new Thickness(6, 0, 0, 0); nameRow.Children.Add(badge);
        text.Children.Add(nameRow);
        if (subtitle.Length > 0) text.Children.Add(new TextBlock { Text = subtitle, FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0x98, 0xB0)), Margin = new Thickness(0, 3, 0, 0), TextWrapping = TextWrapping.Wrap });
        row.Children.Add(text);
        System.Windows.Automation.AutomationProperties.SetName(row, name + ". " + subtitle);
        return row;
    }
}
