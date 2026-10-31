using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>
/// Нарисованные украшения: спрайт-листы из Assets/decor (рендерятся из art/*.mjs).
/// Лист — горизонтальная лента кадров; manifest.json хранит размер кадра, число кадров и частоту.
/// </summary>
public static class Sprites
{
    public sealed class Sheet(BitmapSource source, int w, int h, int frames, int fps)
    {
        public int W { get; } = w; public int H { get; } = h; public int Frames { get; } = frames; public int Fps { get; } = fps;
        readonly CroppedBitmap?[] cut = new CroppedBitmap?[frames];
        public BitmapSource Frame(int i)
        {
            i = Math.Clamp(i, 0, Frames - 1);
            if (cut[i] is { } ready) return ready;
            var scale = source.PixelWidth / (double)(W * Frames);
            var crop = new CroppedBitmap(source, new Int32Rect((int)Math.Round(i * W * scale), 0, (int)Math.Round(W * scale), Math.Min(source.PixelHeight, (int)Math.Round(H * scale))));
            crop.Freeze(); cut[i] = crop; return crop;
        }
        public int FrameAt(long millis) => Frames <= 1 || Fps <= 0 ? 0 : (int)(millis * Fps / 1000 % Frames);
    }

    static JsonObject? manifest;
    static readonly Dictionary<string, Sheet?> cache = [];
    static string Folder => Path.Combine(AppContext.BaseDirectory, "Assets", "decor");
    static JsonObject Manifest()
    {
        if (manifest != null) return manifest;
        try { manifest = JsonNode.Parse(File.ReadAllText(Path.Combine(Folder, "manifest.json"))) as JsonObject ?? new JsonObject(); }
        catch (Exception e) when (e is IOException or System.Text.Json.JsonException or UnauthorizedAccessException) { manifest = new JsonObject(); }
        return manifest;
    }
    public static Sheet? Get(string name)
    {
        if (string.IsNullOrEmpty(name)) return null;
        if (cache.TryGetValue(name, out var known)) return known;
        Sheet? sheet = null;
        if (Manifest()[name] is JsonObject spec && File.Exists(Path.Combine(Folder, name + ".png")))
            try
            {
                var image = new BitmapImage();
                image.BeginInit(); image.UriSource = new Uri(Path.Combine(Folder, name + ".png")); image.CacheOption = BitmapCacheOption.OnLoad; image.EndInit(); image.Freeze();
                sheet = new Sheet(image, AccountClient.Int(spec, "w"), AccountClient.Int(spec, "h"), Math.Max(1, AccountClient.Int(spec, "frames")), AccountClient.Int(spec, "fps"));
            }
            catch (Exception e) when (e is IOException or NotSupportedException or UriFormatException) { }
        cache[name] = sheet;
        return sheet;
    }
    /// <summary>Имя листа украшения аватара по стилю: style.sprite, а для орбит — по глифу.</summary>
    public static string AvatarName(JsonObject? style)
    {
        if (AccountClient.Text(style, "sprite") is { Length: > 0 } sprite) return sprite;
        var type = AccountClient.Text(style, "type");
        return type == "orbit" ? (AccountClient.Text(style, "glyph") == "♥" ? "orbit_hearts" : "orbit_stars") : type;
    }
    static long Now => Environment.TickCount64;
    /// <summary>Темп движения частиц эффектов и деталей рамок (1 — исходный, медленный).</summary>
    public const double EffectPace = 1.6, FramePace = 1.4;

    public static void Draw(DrawingContext dc, Sheet? s, int frame, double cx, double cy, double w, double h, double rotate = 0, double alpha = 1, bool flipX = false, bool flipY = false)
    {
        if (s == null || alpha <= .01 || w <= 0 || h <= 0) return;
        var group = new TransformGroup();
        group.Children.Add(new ScaleTransform(flipX ? -1 : 1, flipY ? -1 : 1));
        if (rotate != 0) group.Children.Add(new RotateTransform(rotate));
        group.Children.Add(new TranslateTransform(cx, cy));
        dc.PushTransform(group); dc.PushOpacity(Math.Clamp(alpha, 0, 1));
        dc.DrawImage(s.Frame(frame), new Rect(-w / 2, -h / 2, w, h));
        dc.Pop(); dc.Pop();
    }
    static void Corner(DrawingContext dc, Sheet? s, double x, double y, double size, bool flipX, bool flipY, double alpha = 1)
    {
        if (s == null) return;
        double w = size, h = size * s.H / s.W;
        Draw(dc, s, 0, x + (flipX ? -w / 2 : w / 2), y + (flipY ? -h / 2 : h / 2), w, h, 0, alpha, flipX, flipY);
    }
    static Color A(Color c, double a) => Color.FromArgb((byte)Math.Round(Math.Clamp(a, 0, 1) * c.A), c.R, c.G, c.B);
    static Pen Line(Brush brush, double width) { var p = new Pen(brush, width); p.Freeze(); return p; }

    // ───── Эффекты профиля из нарисованных частиц ─────
    static string EffectSheet(string type) => type switch { "sparkles" => "fx_sparkle", "bubbles" => "fx_bubble", "petals" => "fx_petal", "fireflies" => "fx_firefly", "hearts" => "fx_heart", "starfall" => "fx_starfall", "bats" => "fx_bat", "snow" => "fx_snow", _ => "" };
    /// <summary>Частицы с глубиной: дальние мельче и бледнее. false — рисунка нет, нужен процедурный эффект.</summary>
    public static bool Effect(DrawingContext dc, string type, double w, double h)
    {
        if (type == "night_watch") return NightWatch(dc, w, h);
        var s = Get(EffectSheet(type)); if (s == null) return false;
        var now = Now; var sec = now / 1000.0 * EffectPace; var r = new Random(type.Length * 7919 + type[0]);
        var (count, size0) = type switch { "snow" => (40, 16.0), "starfall" => (6, 100.0), "bats" => (7, 50.0), "fireflies" => (18, 32.0), "bubbles" => (16, 32.0), "sparkles" => (22, 26.0), "hearts" => (14, 26.0), _ => (24, 22.0) };
        for (var i = 0; i < count; i++)
        {
            double rx = r.NextDouble(), ry = r.NextDouble(), speed = .6 + r.NextDouble() * .8, phase = r.NextDouble(), depth = .45 + r.NextDouble() * .55; var variant = r.Next(Math.Max(1, s.Frames));
            double size = size0 * depth * (.8 + r.NextDouble() * .4), alpha = .45 + .55 * depth;
            switch (type)
            {
                case "sparkles": { var life = (sec * .35 * speed + phase) % 1; var a = Math.Sin(life * Math.PI); Draw(dc, s, variant, rx * w, ry * h, size * (.4 + .6 * a), size * (.4 + .6 * a), life * 90, a * alpha); break; }
                case "bubbles": { var p = (sec * .05 * speed + phase) % 1; Draw(dc, s, variant, rx * w + Math.Sin(sec * speed + phase * 6) * 12, h * (1.1 - p * 1.25), size, size, 0, alpha * Math.Min(1, (1 - p) * 3)); break; }
                case "petals" or "snow":
                {
                    var p = (sec * (type == "snow" ? .06 : .045) * speed + phase) % 1;
                    Draw(dc, s, variant, rx * w + Math.Sin(sec * .8 * speed + phase * 7) * 22, h * (p * 1.2 - .1), size, size, sec * 50 * speed + phase * 360, alpha, false, type == "petals" && (int)(sec * speed + phase * 5) % 2 == 0);
                    break;
                }
                case "fireflies": { var a = .3 + .7 * (.5 + .5 * Math.Sin(sec * 2 * speed + phase * 11)); Draw(dc, s, variant, rx * w + Math.Sin(sec * .3 * speed + phase * 9) * 34, ry * h + Math.Cos(sec * .24 * speed + phase * 5) * 26, size, size, 0, a * alpha); break; }
                case "hearts": { var p = (sec * .07 * speed + phase) % 1; Draw(dc, s, variant, rx * w + Math.Sin(sec * 1.2 + phase * 5) * 10, h * (1.08 - p * 1.15), size * (.85 + .15 * Math.Sin(sec * 4 + i)), size, Math.Sin(sec + i) * 12, alpha * Math.Sin(p * Math.PI)); break; }
                case "starfall":
                {
                    var p = (sec * .16 * speed + phase) % 1; if (p > .5) break; var q = p / .5; var len = size * 1.6;
                    Draw(dc, s, 0, w * (rx * .9 + .3) - q * w * .55, h * (ry * .45 - .15) + q * h * .75, len, len * s.H / s.W, 145, alpha * Math.Sin(q * Math.PI));
                    break;
                }
                case "bats":
                {
                    var p = (sec * .06 * speed + phase) % 1; var left = i % 2 == 1;
                    Draw(dc, s, s.FrameAt(now + (long)(phase * 1000)), w * (left ? 1.15 - p * 1.3 : p * 1.3 - .15), h * (.12 + ry * .6) + Math.Sin(p * Math.PI * 4 + i) * 14, size * 1.45, size, Math.Sin(sec * 2 + i) * 8, alpha, left);
                    break;
                }
            }
        }
        return true;
    }

    /// <summary>«Ночной дозор» (набор администрации): три слоя глубины — дальние звёзды, огоньки зари, размытое боке
    /// у «камеры», плюс полумесяц и кометы. Дальние слои движутся медленнее (параллакс).</summary>
    static bool NightWatch(DrawingContext dc, double w, double h)
    {
        var s = Get("fx_night"); if (s == null) return false;
        var comet = Get("fx_night_comet");
        var sec = Now / 1000.0 * EffectPace; var r = new Random(4242);
        for (var i = 0; i < 26; i++)
        {
            double rx = r.NextDouble(), ry = r.NextDouble(), phase = r.NextDouble() * Math.PI * 2, depth = .3 + r.NextDouble() * .4; var variant = r.Next(2);
            var x = (rx * (w + 40) + sec * 5 * depth) % (w + 40) - 20;
            var tw = Math.Pow(.5 + .5 * Math.Sin(sec * (1.1 + depth * 1.6) + phase), 2);
            var size = (9 + 12 * depth) * (.65 + .35 * tw);
            Draw(dc, s, variant, x, ry * h * .9, size, size, tw * 45, (.25 + .75 * tw) * (.4 + .6 * depth));
        }
        for (var i = 0; i < 12; i++)
        {
            double rx = r.NextDouble(), speed = .6 + r.NextDouble() * .7, phase = r.NextDouble(), depth = .55 + r.NextDouble() * .35; var variant = 2 + r.Next(2);
            var p = (sec * .045 * speed + phase) % 1; var size = 12 + 16 * depth;
            Draw(dc, s, variant, rx * w + Math.Sin(sec * .7 * speed + phase * 9) * 18 * depth, h * (1.08 - p * 1.2), size, size, 0, Math.Sin(p * Math.PI) * (.5 + .45 * depth));
        }
        for (var i = 0; i < 3; i++)
        {
            double rx = r.NextDouble(), ry = r.NextDouble(), phase = r.NextDouble(), size = 80 + 50 * r.NextDouble();
            var p = (sec * .02 + phase) % 1;
            Draw(dc, s, 4, w * (rx * .8 - .1 + p * .4), h * (.15 + ry * .7) + Math.Sin(sec * .3 + i) * 12, size, size, 0, .45 * Math.Sin(p * Math.PI));
        }
        var ms = Math.Min(42, h * .16);
        Draw(dc, s, 5, w - ms * 1.4 + Math.Sin(sec * .35) * 6, ms * 1.3 + Math.Cos(sec * .45) * 5, ms, ms, Math.Sin(sec * .5) * 8, .85);
        if (comet != null)
            for (var i = 0; i < 2; i++)
            {
                double rx = r.NextDouble(), ry = r.NextDouble(), phase = r.NextDouble();
                var p = (sec * .085 + phase) % 1; if (p > .32) continue; var q = p / .32;
                double dx = -w * .55, dy = h * .5, len = Math.Min(220, w * .3);
                Draw(dc, comet, 0, w * (.6 + rx * .45) + q * dx, h * (ry * .3 - .08) + q * dy, len, len * comet.H / comet.W, Math.Atan2(dy, dx) * 180 / Math.PI, Math.Sin(q * Math.PI));
            }
        return true;
    }

    // ───── Рамки профиля с нарисованными деталями ─────
    /// <summary>false — для типа нет рисунка, нужна процедурная рамка.</summary>
    public static bool Frame(DrawingContext dc, string type, Color[] colors, double w, double h, double radius)
    {
        var now = Now; var sec = now / 1000.0 * FramePace; var corner = Math.Min(Math.Min(w, h) * .62, 190);
        var box = new Rect(1, 1, Math.Max(0, w - 2), Math.Max(0, h - 2));
        switch (type)
        {
            case "cobweb":
            {
                var web = Get("pf_cobweb"); var spider = Get("pf_spider"); if (web == null) return false;
                Corner(dc, web, 0, 0, corner, false, false, .95); Corner(dc, web, w, h, corner * .85, true, true, .9);
                double sx = w * .72, drop = (.5 + .5 * Math.Sin(sec * .9)) * h * .32 + 28;
                dc.DrawLine(Line(new SolidColorBrush(Color.FromArgb(0xCC, 0xE6, 0xE6, 0xF0)), 1), new Point(sx, 0), new Point(sx, drop));
                Draw(dc, spider, spider?.FrameAt(now) ?? 0, sx, drop + 15, 42, 48, Math.Sin(sec * 1.7) * 8);
                dc.DrawRoundedRectangle(null, Line(new SolidColorBrush(Color.FromArgb(0x55, 0xE6, 0xE6, 0xF0)), 1.2), box, radius, radius);
                return true;
            }
            case "vines":
            {
                var v = Get("pf_vines"); if (v == null) return false;
                var sway = Math.Sin(sec * .8) * 1.5;
                dc.PushTransform(new RotateTransform(sway, 0, 0)); Corner(dc, v, -2, -2, corner, false, false); dc.Pop();
                dc.PushTransform(new RotateTransform(-sway, w, h)); Corner(dc, v, w + 2, h + 2, corner * .9, true, true); dc.Pop();
                var edge = new LinearGradientBrush(Color.FromRgb(0x7D, 0xD0, 0x7A), Color.FromArgb(0x44, 0x5F, 0xA3, 0x4A), 45); edge.Freeze();
                dc.DrawRoundedRectangle(null, Line(edge, 2), box, radius, radius);
                return true;
            }
            case "constellation":
            {
                var cs = Get("pf_constellation"); if (cs == null) return false;
                var tw = .75 + .25 * Math.Sin(sec * 1.6);
                Corner(dc, cs, w, 0, corner * .9, true, false, tw); Corner(dc, cs, 0, h, corner * .75, false, true, 1.75 - tw);
                var pen = new Pen(new SolidColorBrush(Color.FromArgb(0x66, 0x9F, 0xD8, 0xFF)), 1.4) { DashStyle = new DashStyle([1.5, 6], -sec * 6), DashCap = PenLineCap.Round };
                dc.DrawRoundedRectangle(null, pen, box, radius, radius);
                return true;
            }
            case "flames":
            {
                var fl = Get("pf_flame"); if (fl == null) return false;
                var glowBrush = new LinearGradientBrush(Color.FromArgb(0, 0xFF, 0x5A, 0x1F), Color.FromArgb(0x66, 0xFF, 0x5A, 0x1F), 90); glowBrush.Freeze();
                dc.DrawRectangle(glowBrush, null, new Rect(0, h * .55, w, h * .45));
                var n = Math.Max(6, (int)Math.Round(w / 40)); var r = new Random(5);
                for (var i = 0; i < n; i++)
                {
                    double fh = 52 + r.NextDouble() * 34, fw = fh * fl.W / fl.H, x = (i + .5) * w / n + (r.NextDouble() - .5) * 10;
                    Draw(dc, fl, fl.FrameAt(now + r.Next(1000)), x, h - fh / 2 + 4, fw, fh, 0, .95, r.Next(2) == 0);
                }
                for (var i = 0; i < 16; i++)
                {
                    var p = (sec * .25 * (.6 + r.NextDouble()) + r.NextDouble()) % 1;
                    var ember = new SolidColorBrush(A(Color.FromRgb(0xFF, 0xB3, 0x47), 1 - p)); ember.Freeze();
                    var radiusE = 1.2 + 1.6 * (1 - p);
                    dc.DrawEllipse(ember, null, new Point(r.NextDouble() * w + Math.Sin(sec * 2 + i) * 6, h - p * h * .7), radiusE, radiusE);
                }
                var edge = new LinearGradientBrush(Color.FromArgb(0x33, 0xFF, 0xE0, 0x8A), Color.FromRgb(0xFF, 0x6A, 0x2E), 90); edge.Freeze();
                dc.DrawRoundedRectangle(null, Line(edge, 2), box, radius, radius);
                return true;
            }
            case "crystal":
            {
                var cr = Get("pf_crystal"); if (cr == null) return false;
                Corner(dc, cr, -4, -4, corner * .9, false, false); Corner(dc, cr, w + 4, h + 4, corner * .8, true, true);
                var edge = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
                edge.GradientStops.Add(new GradientStop(Color.FromRgb(0xB3, 0x88, 0xFF), 0)); edge.GradientStops.Add(new GradientStop(Color.FromArgb(0x88, 0x7E, 0xDC, 0xFF), .5)); edge.GradientStops.Add(new GradientStop(Color.FromRgb(0xB3, 0x88, 0xFF), 1)); edge.Freeze();
                dc.DrawRoundedRectangle(null, Line(edge, 1.6), box, radius, radius);
                return true;
            }
            case "moon":
            {
                var m = Get("pf_moon"); var cl = Get("pf_cloud"); if (m == null) return false;
                var r = new Random(3);
                for (var i = 0; i < 14; i++)
                {
                    double x = w * (.4 + r.NextDouble() * .6), y = h * r.NextDouble() * .5, a = .4 + .6 * (.5 + .5 * Math.Sin(sec * 1.6 + i)), s = 2 + 2 * r.NextDouble();
                    var brush = new SolidColorBrush(A(Colors.White, a)); brush.Freeze();
                    dc.DrawGeometry(brush, null, Geometry.Parse(FormattableString.Invariant($"M{x},{y - s} Q{x},{y} {x + s},{y} Q{x},{y} {x},{y + s} Q{x},{y} {x - s},{y} Q{x},{y} {x},{y - s}Z")));
                }
                var ms = Math.Min(130, h * .7);
                Draw(dc, m, 0, w - ms * .55, ms * .55, ms, ms, Math.Sin(sec * .5) * 3);
                if (cl != null) { var cw = ms * 1.2; Draw(dc, cl, 0, w - ms * .9 + Math.Sin(sec * .25) * 14, ms * .92, cw, cw * cl.H / cl.W, 0, .9); }
                var edge = new LinearGradientBrush(Color.FromRgb(0xFF, 0xE9, 0xA8), Color.FromArgb(0x22, 0x6F, 0x7B, 0xFF), new Point(1, 0), new Point(0, 1)); edge.Freeze();
                dc.DrawRoundedRectangle(null, Line(edge, 1.5), box, radius, radius);
                return true;
            }
            case "night_crown":
            {
                // «Полуночная грань» (набор администрации): узорные углы, герб с полумесяцем и бегущий по краю свет зари.
                var cornerSheet = Get("pf_night_corner"); var crest = Get("pf_night_crest"); if (cornerSheet == null) return false;
                var edge = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
                edge.GradientStops.Add(new GradientStop(Color.FromArgb(0xCC, 0xFF, 0xE3, 0xB8), 0)); edge.GradientStops.Add(new GradientStop(Color.FromArgb(0x55, 0xFF, 0x8A, 0x3D), .3));
                edge.GradientStops.Add(new GradientStop(Color.FromArgb(0x66, 0x4A, 0x32, 0x70), .5)); edge.GradientStops.Add(new GradientStop(Color.FromArgb(0x55, 0xFF, 0x8A, 0x3D), .7));
                edge.GradientStops.Add(new GradientStop(Color.FromArgb(0xCC, 0xFF, 0x4D, 0x67), 1)); edge.Freeze();
                dc.DrawRoundedRectangle(null, Line(edge, 1.4), box, radius, radius);
                var dawn = new LinearGradientBrush(Color.FromRgb(0xFF, 0x4D, 0x67), Color.FromRgb(0xFF, 0xE3, 0xB8), 0); dawn.Freeze();
                var r = Math.Min(radius, Math.Min(box.Width, box.Height) / 2);
                var perimeter = 2 * (box.Width + box.Height) - 8 * r + 2 * Math.PI * r;
                const double light = 110;
                if (perimeter > light * 3)
                    for (var k = 0; k < 2; k++)
                        foreach (var (width, alpha) in new[] { (8.0, .2), (4.0, .35), (2.0, 1.0) })
                        {
                            var pen = new Pen(dawn, width) { DashCap = PenLineCap.Round, DashStyle = new DashStyle([light / width, (perimeter - light) / width], (-sec * 70 - k * perimeter / 2) / width) };
                            pen.Freeze(); dc.PushOpacity(alpha); dc.DrawRoundedRectangle(null, pen, box, radius, radius); dc.Pop();
                        }
                var size = Math.Min(Math.Min(w, h) * .44, 150); var breathe = .88 + .12 * Math.Sin(sec * 1.3);
                Corner(dc, cornerSheet, 0, 0, size, false, false, breathe); Corner(dc, cornerSheet, w, 0, size, true, false, breathe);
                Corner(dc, cornerSheet, 0, h, size, false, true, breathe); Corner(dc, cornerSheet, w, h, size, true, true, breathe);
                if (crest != null && w > size * 2.6) { var cw = Math.Min(168, w * .3); var ch = cw * crest.H / crest.W; Draw(dc, crest, 0, w / 2, ch / 2 + 2, cw, ch, 0, .9 + .1 * Math.Sin(sec * 1.7)); }
                return true;
            }
            case "neon":
            {
                var flicker = Math.Sin(sec * 13) > .96 ? .3 : 1; var pulse = .75 + .25 * Math.Sin(sec * 2.4) * flicker;
                var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1), RelativeTransform = new RotateTransform(sec * 30 % 360, .5, .5) };
                for (var i = 0; i < 3; i++) brush.GradientStops.Add(new GradientStop(colors[Math.Min(i, colors.Length - 1)], i / 2.0));
                brush.Freeze();
                foreach (var (width, alpha) in new[] { (16.0, .10), (10.0, .18), (6.0, .35), (3.0, .8) }) { dc.PushOpacity(alpha * pulse); dc.DrawRoundedRectangle(null, Line(brush, width), box, radius, radius); dc.Pop(); }
                dc.PushOpacity(.85 * pulse); dc.DrawRoundedRectangle(null, Line(Brushes.White, 1.2), box, radius, radius); dc.Pop();
                return true;
            }
            default: return false;
        }
    }
}
