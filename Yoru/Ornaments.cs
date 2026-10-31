using System.Globalization;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace Yoru;

/// <summary>
/// Украшения в духе Discord, нарисованные кодом: украшение аватара, эффект профиля и рамка профиля.
/// Рисунки собственные, по style.type; style.image_url (своя картинка из админки) показывается вместо них.
/// </summary>
public sealed class OrnamentLayer : FrameworkElement
{
    public enum Kind { Avatar, Effect, Frame }
    readonly Kind kind;
    readonly string type, glyph;
    readonly Color[] colors;
    readonly double avatarRadius;
    public double CornerRadius { get; set; } = 22;
    bool subscribed;
    readonly Sprites.Sheet? sheet;
    static readonly DateTime Start = DateTime.UtcNow;

    public OrnamentLayer(Kind kind, JsonObject? style, double avatarRadius = 0)
    {
        this.kind = kind; this.avatarRadius = avatarRadius;
        type = AccountClient.Text(style, "type"); glyph = AccountClient.Text(style, "glyph");
        colors = style == null ? [Colors.White] : Decor.Colors(style, kind == Kind.Effect ? Colors.White : Color.FromRgb(0xFF, 0x4D, 0x67));
        if (kind == Kind.Avatar) sheet = Sprites.Get(Sprites.AvatarName(style));
        IsHitTestVisible = false; SnapsToDevicePixels = false; RenderOptions.SetBitmapScalingMode(this, BitmapScalingMode.HighQuality);
        Loaded += (_, _) => Subscribe(true); Unloaded += (_, _) => Subscribe(false);
    }
    public bool Empty => type.Length == 0;
    void Subscribe(bool on)
    {
        var animate = on && !Empty && SystemParameters.ClientAreaAnimation;
        if (animate && !subscribed) { CompositionTarget.Rendering += Tick; subscribed = true; }
        else if (!animate && subscribed) { CompositionTarget.Rendering -= Tick; subscribed = false; }
    }
    DateTime last;
    void Tick(object? sender, EventArgs e) { var now = DateTime.UtcNow; if ((now - last).TotalMilliseconds < 14) return; last = now; InvalidateVisual(); }

    protected override void OnRender(DrawingContext dc)
    {
        var seconds = (DateTime.UtcNow - Start).TotalSeconds * Sprites.EffectPace;
        double w = ActualWidth, h = ActualHeight;
        if (w <= 0 || h <= 0 || Empty) return;
        switch (kind)
        {
            case Kind.Avatar:
                if (sheet != null) Sprites.Draw(dc, sheet, sheet.FrameAt(Environment.TickCount64), w / 2, h / 2, w, h);
                else AvatarDeco(dc, type, glyph, colors, w / 2, h / 2, avatarRadius > 0 ? avatarRadius : Math.Min(w, h) * 0.36, seconds % 6 / 6);
                break;
            case Kind.Effect: if (!Sprites.Effect(dc, type, w, h)) Effect(dc, type, colors, w, h, seconds); break;
            default: if (!Sprites.Frame(dc, type, colors, w, h, CornerRadius)) Frame(dc, type, colors, w, h, CornerRadius, seconds); break;
        }
    }

    static Color C(Color[] c, int i) => c[Math.Min(i, c.Length - 1)];
    static Color A(Color c, double a) => Color.FromArgb((byte)Math.Round(Math.Clamp(a, 0, 1) * c.A), c.R, c.G, c.B);
    static Brush B(Color c) { var b = new SolidColorBrush(c); b.Freeze(); return b; }
    static Pen P(Color c, double width) { var p = new Pen(B(c), width) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round }; p.Freeze(); return p; }
    static double Wave(double t, double phase) => Math.Sin((t + phase) * Math.PI * 2);
    static Color Darker(Color c) => Color.FromArgb(c.A, (byte)(c.R * 4 / 5), (byte)(c.G * 4 / 5), (byte)(c.B * 4 / 5));
    static Geometry Shape(Action<StreamGeometryContext> draw) { var g = new StreamGeometry(); using (var ctx = g.Open()) draw(ctx); g.Freeze(); return g; }

    // ───────── Украшение аватара ─────────
    public static void AvatarDeco(DrawingContext dc, string type, string glyph, Color[] colors, double cx, double cy, double r, double t)
    {
        switch (type)
        {
            case "cat_ears":
                for (var side = -1; side <= 1; side += 2)
                {
                    double bx = cx + side * r * .62, by = cy - r * .62;
                    dc.DrawGeometry(B(C(colors, 0)), null, Shape(g => { g.BeginFigure(new Point(bx - side * r * .42, by + r * .06), true, true); g.LineTo(new Point(bx + side * r * .30, by - r * .62), true, false); g.LineTo(new Point(bx + side * r * .30, by + r * .30), true, false); }));
                    dc.DrawGeometry(B(C(colors, 1)), null, Shape(g => { g.BeginFigure(new Point(bx - side * r * .18, by + r * .02), true, true); g.LineTo(new Point(bx + side * r * .20, by - r * .38), true, false); g.LineTo(new Point(bx + side * r * .20, by + r * .14), true, false); }));
                }
                break;
            case "bunny":
                for (var side = -1; side <= 1; side += 2)
                {
                    double ex = cx + side * r * .35, ey = cy - r * .96;
                    dc.PushTransform(new RotateTransform(side * (14 + 3 * Wave(t, side * .25)), ex, cy - r * .8));
                    dc.DrawEllipse(B(C(colors, 0)), null, new Point(ex, ey), r * .2, r * .42);
                    dc.DrawEllipse(B(C(colors, 1)), null, new Point(ex, ey), r * .12, r * .28);
                    dc.Pop();
                }
                break;
            case "halo":
            {
                var bob = r * .05 * Wave(t, 0);
                dc.DrawEllipse(null, P(A(C(colors, 1), .35), r * .22), new Point(cx, cy - r * 1.14 + bob), r * .62, r * .14);
                dc.DrawEllipse(null, P(C(colors, 0), r * .09), new Point(cx, cy - r * 1.14 + bob), r * .62, r * .14);
                break;
            }
            case "horns":
                for (var side = -1; side <= 1; side += 2)
                {
                    double bx = cx + side * r * .48, by = cy - r * .72;
                    var brush = new LinearGradientBrush(C(colors, 1), C(colors, 0), new Point(0.5, 1), new Point(side > 0 ? 1 : 0, 0)); brush.Freeze();
                    dc.DrawGeometry(brush, null, Shape(g => { g.BeginFigure(new Point(bx - side * r * .18, by + r * .1), true, true); g.QuadraticBezierTo(new Point(bx + side * r * .1, by - r * .25), new Point(bx + side * r * .42, by - r * .55), true, false); g.QuadraticBezierTo(new Point(bx + side * r * .2, by - r * .05), new Point(bx + side * r * .2, by + r * .2), true, false); }));
                }
                break;
            case "headphones":
                dc.DrawGeometry(null, P(C(colors, 0), r * .16), Shape(g => { g.BeginFigure(new Point(cx - r * 1.0, cy - r * .2), false, false); g.ArcTo(new Point(cx + r * 1.0, cy - r * .2), new Size(r * 1.05, r * 1.1), 0, false, SweepDirection.Clockwise, true, false); }));
                for (var side = -1; side <= 1; side += 2)
                {
                    dc.DrawRoundedRectangle(B(C(colors, 0)), null, new Rect(cx + side * r * 1.0 - r * .2, cy - r * .32, r * .4, r * .7), r * .16, r * .16);
                    dc.DrawRoundedRectangle(B(C(colors, 1)), null, new Rect(cx + side * r * 1.0 - r * .13, cy - r * .2, r * .26, r * .46), r * .08, r * .08);
                }
                break;
            case "crown":
            {
                dc.PushTransform(new RotateTransform(-12, cx, cy));
                double top = cy - r * 1.32, bottom = cy - r * .84, w = r * .62;
                var fill = new LinearGradientBrush(C(colors, 0), C(colors, 1), 90); fill.Freeze();
                dc.DrawGeometry(fill, null, Shape(g => { g.BeginFigure(new Point(cx - w, bottom), true, true); foreach (var p in new[] { new Point(cx - w, top + r * .12), new Point(cx - w * .5, top + r * .3), new Point(cx, top), new Point(cx + w * .5, top + r * .3), new Point(cx + w, top + r * .12), new Point(cx + w, bottom) }) g.LineTo(p, true, false); }));
                dc.DrawEllipse(B(C(colors, 2)), null, new Point(cx, bottom - r * .14), r * .08, r * .08);
                dc.DrawEllipse(B(C(colors, 2)), null, new Point(cx - w * .6, bottom - r * .12), r * .055, r * .055);
                dc.DrawEllipse(B(C(colors, 2)), null, new Point(cx + w * .6, bottom - r * .12), r * .055, r * .055);
                dc.DrawEllipse(B(A(Colors.White, .5 + .5 * Wave(t, 0))), null, new Point(cx, top + r * .02), r * .05, r * .05);
                dc.Pop();
                break;
            }
            case "orbit":
            {
                var g = glyph.Length > 0 ? glyph : "★";
                for (var i = 0; i < 6; i++)
                {
                    var a = Math.PI * 2 * (i / 6.0 + t);
                    var text = new FormattedText(g, CultureInfo.InvariantCulture, FlowDirection.LeftToRight, new Typeface("Segoe UI Symbol"), r * .32, B(A(C(colors, i % 2), .65 + .35 * Wave(t, i / 6.0))), 1.0);
                    dc.DrawText(text, new Point(cx + Math.Cos(a) * r * 1.12 - text.Width / 2, cy + Math.Sin(a) * r * 1.12 - text.Height / 2));
                }
                break;
            }
            case "sakura_wreath":
                for (var i = 0; i < 7; i++)
                {
                    var a = (100 + i * 26) * Math.PI / 180;
                    double x = cx + Math.Cos(a) * r, y = cy + Math.Sin(a) * r;
                    if (i % 2 == 1) { dc.PushTransform(new RotateTransform(a * 180 / Math.PI, x, y)); dc.DrawEllipse(B(Color.FromRgb(0x6F, 0xBF, 0x73)), null, new Point(x, y), r * .16, r * .06); dc.Pop(); continue; }
                    Flower(dc, x, y, r * (i is 2 or 4 ? .22 : .18), t * 72 + i * 20, colors);
                }
                break;
            case "pumpkin":
            {
                dc.PushTransform(new RotateTransform(14, cx, cy));
                double px = cx + r * .25, py = cy - r * 1.0, pw = r * .5, ph = r * .36;
                dc.DrawRoundedRectangle(B(C(colors, 2)), null, new Rect(px - pw * 1.25, py + ph * .55, pw * 2.5, ph * .3), ph * .2, ph * .2);
                for (var i = -1; i <= 1; i++) dc.DrawEllipse(B(i == 0 ? C(colors, 0) : Darker(C(colors, 0))), null, new Point(px + i * pw * .42, py + ph * .06), pw * .55, ph * .56);
                dc.DrawRoundedRectangle(B(C(colors, 1)), null, new Rect(px - r * .05, py - ph * .85, r * .1, ph * .45), r * .03, r * .03);
                var eye = B(A(Color.FromRgb(0xFF, 0xE0, 0x8A), .6 + .4 * Wave(t, 0)));
                dc.DrawGeometry(eye, null, Shape(g => { g.BeginFigure(new Point(px - pw * .4, py), true, true); g.LineTo(new Point(px - pw * .2, py - ph * .22), true, false); g.LineTo(new Point(px - pw * .05, py), true, false); g.BeginFigure(new Point(px + pw * .05, py), true, true); g.LineTo(new Point(px + pw * .2, py - ph * .22), true, false); g.LineTo(new Point(px + pw * .4, py), true, false); }));
                dc.Pop();
                break;
            }
        }
    }
    static void Flower(DrawingContext dc, double x, double y, double size, double rotate, Color[] colors)
    {
        dc.PushTransform(new RotateTransform(rotate, x, y));
        for (var p = 0; p < 5; p++) { dc.PushTransform(new RotateTransform(p * 72, x, y)); dc.DrawEllipse(B(C(colors, 0)), null, new Point(x, y - size * .57), size * .32, size * .43); dc.Pop(); }
        dc.DrawEllipse(B(C(colors, 1)), null, new Point(x, y), size * .22, size * .22);
        dc.Pop();
    }

    // ───────── Эффект профиля ─────────
    public static void Effect(DrawingContext dc, string type, Color[] colors, double w, double h, double seconds)
    {
        var random = new Random(type.GetHashCode(StringComparison.Ordinal) & 0x7FFFFFFF);
        var count = type switch { "snow" => 46, "starfall" => 7, "bats" => 5, "fireflies" => 18, _ => 24 };
        for (var i = 0; i < count; i++)
        {
            double rx = random.NextDouble(), ry = random.NextDouble(), speed = .5 + random.NextDouble(), phase = random.NextDouble(), size = .6 + random.NextDouble() * .8;
            var color = colors[i % colors.Length];
            switch (type)
            {
                case "sparkles": { var life = (seconds * .45 * speed + phase) % 1; var a = Math.Sin(life * Math.PI); Star(dc, rx * w, ry * h, size * (3 + 5 * a), A(color, a)); break; }
                case "bubbles":
                {
                    var p = (seconds * .06 * speed + phase) % 1; double y = h * (1.05 - p * 1.15), x = rx * w + Math.Sin(seconds * speed + phase * 6) * 10;
                    dc.DrawEllipse(null, P(A(color, .7 * (1 - p * .6)), 1.2), new Point(x, y), size * (4 + 4 * rx), size * (4 + 4 * rx));
                    dc.DrawEllipse(B(A(Colors.White, .5 * (1 - p))), null, new Point(x - size * 2, y - size * 2), size * 1.1, size * 1.1);
                    break;
                }
                case "petals" or "snow":
                {
                    var p = (seconds * (type == "snow" ? .07 : .05) * speed + phase) % 1; double y = h * (p * 1.15 - .08), x = rx * w + Math.Sin(seconds * .9 * speed + phase * 7) * 18;
                    if (type == "snow") dc.DrawEllipse(B(A(color, .85)), null, new Point(x, y), size * (1.2 + 2 * ry), size * (1.2 + 2 * ry));
                    else { dc.PushTransform(new RotateTransform(seconds * 60 * speed + phase * 360, x, y)); dc.DrawEllipse(B(A(color, .9)), null, new Point(x, y), size * 5, size * 2.6); dc.Pop(); }
                    break;
                }
                case "fireflies":
                {
                    double x = rx * w + Math.Sin(seconds * .35 * speed + phase * 9) * 30, y = ry * h + Math.Cos(seconds * .28 * speed + phase * 5) * 24, a = .35 + .65 * (.5 + .5 * Math.Sin(seconds * 2.2 * speed + phase * 11));
                    var glow = new RadialGradientBrush(A(color, a), A(color, 0)); glow.Freeze();
                    dc.DrawEllipse(glow, null, new Point(x, y), size * 9, size * 9);
                    dc.DrawEllipse(B(A(Colors.White, a)), null, new Point(x, y), size * 1.4, size * 1.4);
                    break;
                }
                case "hearts": { var p = (seconds * .08 * speed + phase) % 1; Heart(dc, rx * w + Math.Sin(seconds * 1.3 + phase * 5) * 8, h * (1.05 - p * 1.1), size * (5 + 3 * ry), A(color, Math.Sin(p * Math.PI) * .9)); break; }
                case "starfall":
                {
                    var p = (seconds * .18 * speed + phase) % 1; if (p > .55) break; var q = p / .55;
                    double x = w * (rx * .8 + .25) - q * w * .5, y = h * (ry * .4 - .1) + q * h * .7, len = 40 * size;
                    var tail = new LinearGradientBrush(A(color, Math.Sin(q * Math.PI)), A(color, 0), new Point(0, 1), new Point(1, 0)); tail.Freeze();
                    var pen = new Pen(tail, 1.6) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round }; pen.Freeze();
                    dc.DrawLine(pen, new Point(x, y), new Point(x + len, y - len * .7));
                    Star(dc, x, y, size * 4, A(Colors.White, Math.Sin(q * Math.PI)));
                    break;
                }
                case "bats": { var p = (seconds * .07 * speed + phase) % 1; Bat(dc, w * (p * 1.3 - .15), h * (.15 + ry * .6) + Math.Sin(p * Math.PI * 4) * 14, size * 9, Math.Sin(seconds * 12 * speed), C(colors, 0), C(colors, 1)); break; }
            }
        }
    }
    static void Star(DrawingContext dc, double x, double y, double s, Color color) =>
        dc.DrawGeometry(B(color), null, Shape(g => { g.BeginFigure(new Point(x, y - s), true, true); g.QuadraticBezierTo(new Point(x, y), new Point(x + s, y), true, false); g.QuadraticBezierTo(new Point(x, y), new Point(x, y + s), true, false); g.QuadraticBezierTo(new Point(x, y), new Point(x - s, y), true, false); g.QuadraticBezierTo(new Point(x, y), new Point(x, y - s), true, false); }));
    static void Heart(DrawingContext dc, double x, double y, double s, Color color) =>
        dc.DrawGeometry(B(color), null, Shape(g => { g.BeginFigure(new Point(x, y + s * .9), true, true); g.BezierTo(new Point(x - s * 1.6, y - s * .2), new Point(x - s * .7, y - s * 1.3), new Point(x, y - s * .45), true, false); g.BezierTo(new Point(x + s * .7, y - s * 1.3), new Point(x + s * 1.6, y - s * .2), new Point(x, y + s * .9), true, false); }));
    static void Bat(DrawingContext dc, double x, double y, double s, double flap, Color body, Color eye)
    {
        var lift = s * .5 * flap;
        dc.DrawGeometry(B(body), null, Shape(g =>
        {
            g.BeginFigure(new Point(x, y), true, true);
            g.QuadraticBezierTo(new Point(x - s * .6, y - s * .5 - lift), new Point(x - s * 1.4, y - s * .2 - lift), true, false);
            g.QuadraticBezierTo(new Point(x - s * 1.0, y), new Point(x - s * 1.1, y + s * .25), true, false);
            g.QuadraticBezierTo(new Point(x - s * .6, y + s * .05), new Point(x - s * .3, y + s * .3), true, false);
            g.QuadraticBezierTo(new Point(x, y + s * .1), new Point(x + s * .3, y + s * .3), true, false);
            g.QuadraticBezierTo(new Point(x + s * .6, y + s * .05), new Point(x + s * 1.1, y + s * .25), true, false);
            g.QuadraticBezierTo(new Point(x + s * 1.0, y), new Point(x + s * 1.4, y - s * .2 - lift), true, false);
            g.QuadraticBezierTo(new Point(x + s * .6, y - s * .5 - lift), new Point(x, y), true, false);
        }));
        dc.DrawEllipse(B(eye), null, new Point(x - s * .1, y + s * .02), s * .05, s * .05);
        dc.DrawEllipse(B(eye), null, new Point(x + s * .1, y + s * .02), s * .05, s * .05);
    }

    // ───────── Рамка профиля ─────────
    public static void Frame(DrawingContext dc, string type, Color[] colors, double w, double h, double radius, double seconds)
    {
        var box = new Rect(2, 2, Math.Max(0, w - 4), Math.Max(0, h - 4));
        switch (type)
        {
            case "neon":
            {
                var pulse = .6 + .4 * Math.Sin(seconds * 2.4);
                var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
                for (var i = 0; i < 3; i++) brush.GradientStops.Add(new GradientStop(C(colors, i), i / 2.0));
                brush.RelativeTransform = new RotateTransform(seconds * 40 % 360, .5, .5); brush.Freeze();
                foreach (var (width, alpha) in new[] { (10.0, .18 * pulse), (5.0, .45 * pulse), (2.0, 1.0) })
                { dc.PushOpacity(alpha); dc.DrawRoundedRectangle(null, new Pen(brush, width), box, radius, radius); dc.Pop(); }
                break;
            }
            case "vines":
                for (var edge = 0; edge < 4; edge++)
                {
                    var vertical = edge % 2 == 1; var len = vertical ? h : w; var waves = Math.Max(3, (int)Math.Round(len / 60));
                    var steps = waves * 8;
                    dc.DrawGeometry(null, P(C(colors, 0), 2.2), Shape(g =>
                    {
                        for (var i = 0; i <= steps; i++)
                        {
                            double u = i / (double)steps, off = Math.Sin(u * waves * Math.PI * 2) * 5 + 6;
                            var pt = vertical ? new Point(edge == 1 ? w - off : off, u * h) : new Point(u * w, edge == 0 ? off : h - off);
                            if (i == 0) g.BeginFigure(pt, false, false); else g.LineTo(pt, true, true);
                        }
                    }));
                    for (var i = 0; i < waves * 2; i++)
                    {
                        double u = (i + .5) / (waves * 2), off = 10;
                        var pt = vertical ? new Point(edge == 1 ? w - off : off, u * h) : new Point(u * w, edge == 0 ? off : h - off);
                        dc.PushTransform(new RotateTransform((i % 2 == 0 ? 35 : -35) + (vertical ? 90 : 0) + Math.Sin(seconds + i) * 6, pt.X, pt.Y));
                        dc.DrawEllipse(B(C(colors, i % 2)), null, pt, 6, 2.6); dc.Pop();
                    }
                }
                break;
            case "constellation":
            {
                var random = new Random(7); const int n = 22; var pts = new Point[n];
                for (var i = 0; i < n; i++)
                {
                    double per = 2 * (w + h), d = i / (double)n * per + random.NextDouble() * 20, off = 4 + random.NextDouble() * 10;
                    pts[i] = d < w ? new Point(d, off) : d < w + h ? new Point(w - off, d - w) : d < 2 * w + h ? new Point(w - (d - w - h), h - off) : new Point(off, h - (d - 2 * w - h));
                }
                var line = P(A(C(colors, 0), .45), 1);
                for (var i = 0; i < n; i++) dc.DrawLine(line, pts[i], pts[(i + 1) % n]);
                for (var i = 0; i < n; i++) Star(dc, pts[i].X, pts[i].Y, 2.5 + 2 * (.5 + .5 * Math.Sin(seconds * 2 + i)), C(colors, i % 2 == 0 ? 1 : 0));
                break;
            }
            case "flames":
            {
                var n = Math.Max(8, (int)Math.Round(w / 18));
                for (var i = 0; i < n; i++)
                {
                    double x = (i + .5) * w / n, flick = Math.Sin(seconds * 7 + i * 1.7) * .5 + .5, fh = 16 + 14 * flick + i % 3 * 6, fw = w / n * .7;
                    for (var layer = 0; layer < 3; layer++)
                    {
                        var s = 1 - layer * .3; var tip = x + Math.Sin(seconds * 5 + i) * 3;
                        dc.DrawGeometry(B(A(C(colors, 2 - layer), layer == 0 ? .85 : 1)), null, Shape(g => { g.BeginFigure(new Point(x - fw * s, h), true, true); g.QuadraticBezierTo(new Point(x - fw * s * .6, h - fh * s * .6), new Point(tip, h - fh * s), true, false); g.QuadraticBezierTo(new Point(x + fw * s * .6, h - fh * s * .6), new Point(x + fw * s, h), true, false); }));
                    }
                }
                var edge = new LinearGradientBrush(A(C(colors, 0), .2), C(colors, 2), 90); edge.Freeze();
                dc.DrawRoundedRectangle(null, new Pen(edge, 1.5), box, radius, radius);
                break;
            }
            case "crystal":
            {
                dc.DrawRoundedRectangle(null, P(A(C(colors, 1), .5), 1.5), box, radius, radius);
                foreach (var (ox, oy, sx, sy) in new[] { (0.0, 0.0, 1, 1), (w, 0.0, -1, 1), (0.0, h, 1, -1), (w, h, -1, -1) })
                    for (var j = 0; j < 3; j++)
                    {
                        double len = 26 - j * 7, wid = 7 - j * 1.5, ang = Math.PI / 4 + (j - 1) * .45, dx = Math.Cos(ang) * sx, dy = Math.Sin(ang) * sy, bx = ox + sx * 4, by = oy + sy * 4;
                        var fill = new LinearGradientBrush { MappingMode = BrushMappingMode.Absolute, StartPoint = new Point(bx, by), EndPoint = new Point(bx + dx * len, by + dy * len) };
                        fill.GradientStops.Add(new GradientStop(C(colors, j % 2), 0)); fill.GradientStops.Add(new GradientStop(C(colors, 2), 1)); fill.Freeze();
                        dc.DrawGeometry(fill, null, Shape(g => { g.BeginFigure(new Point(bx, by), true, true); g.LineTo(new Point(bx + dx * len * .5 - dy * wid, by + dy * len * .5 + dx * wid), true, false); g.LineTo(new Point(bx + dx * len, by + dy * len), true, false); g.LineTo(new Point(bx + dx * len * .5 + dy * wid, by + dy * len * .5 - dx * wid), true, false); }));
                    }
                var glint = seconds * .25 % 1;
                dc.DrawEllipse(B(A(Colors.White, Math.Sin(glint * Math.PI))), null, new Point(18, 18), 2.5, 2.5);
                break;
            }
            case "moon":
            {
                double mx = w - 34, my = 34, mr = 18;
                var halo = new RadialGradientBrush(A(C(colors, 0), .35), A(C(colors, 0), 0)); halo.Freeze();
                dc.DrawEllipse(halo, null, new Point(mx, my), mr * 2.4, mr * 2.4);
                var moon = new CombinedGeometry(GeometryCombineMode.Exclude, new EllipseGeometry(new Point(mx, my), mr, mr), new EllipseGeometry(new Point(mx + mr * .55, my - mr * .35), mr * .85, mr * .85)); moon.Freeze();
                dc.DrawGeometry(B(C(colors, 0)), null, moon);
                var random = new Random(3);
                for (var i = 0; i < 9; i++) Star(dc, w - random.NextDouble() * w * .45, random.NextDouble() * h * .35, 1.5 + 2 * (.5 + .5 * Math.Sin(seconds * 1.7 + i)), A(i % 2 == 0 ? Colors.White : C(colors, 1), .9));
                var edge = new LinearGradientBrush(C(colors, 1), A(C(colors, 1), .1), new Point(1, 0), new Point(0, 1)); edge.Freeze();
                dc.DrawRoundedRectangle(null, new Pen(edge, 1.5), box, radius, radius);
                break;
            }
            case "cobweb":
            {
                var pen = P(A(C(colors, 0), .75), .9);
                foreach (var (ox, oy, sx, sy) in new[] { (0.0, 0.0, 1, 1), (w, h, -1, -1) })
                {
                    var size = Math.Min(w, h) * .42;
                    for (var i = 0; i <= 5; i++) { var a = Math.PI / 2 * i / 5; dc.DrawLine(pen, new Point(ox, oy), new Point(ox + sx * Math.Cos(a) * size, oy + sy * Math.Sin(a) * size)); }
                    for (var ring = 1; ring <= 4; ring++)
                    {
                        var rr = size * ring / 4.6;
                        dc.DrawGeometry(null, pen, Shape(g =>
                        {
                            for (var i = 0; i <= 5; i++)
                            {
                                var a = Math.PI / 2 * i / 5; var pt = new Point(ox + sx * Math.Cos(a) * rr, oy + sy * Math.Sin(a) * rr);
                                if (i == 0) g.BeginFigure(pt, false, false);
                                else { var pa = Math.PI / 2 * (i - .5) / 5; g.QuadraticBezierTo(new Point(ox + sx * Math.Cos(pa) * rr * .86, oy + sy * Math.Sin(pa) * rr * .86), pt, true, false); }
                            }
                        }));
                    }
                }
                var drop = (.5 + .5 * Math.Sin(seconds * 1.2)) * Math.Min(w, h) * .25 + 20;
                dc.DrawLine(pen, new Point(w * .3, 0), new Point(w * .3, drop));
                dc.DrawEllipse(B(C(colors, 1)), null, new Point(w * .3, drop), 4, 4);
                break;
            }
        }
    }

    /// <summary>Слой эффекта и рамки поверх карточки профиля; своя картинка из админки — вместо рисунка.</summary>
    public static FrameworkElement Overlay(JsonObject? effect, JsonObject? frame, double radius)
    {
        var grid = new Grid { IsHitTestVisible = false };
        foreach (var (style, kind) in new[] { (effect, Kind.Effect), (frame, Kind.Frame) })
        {
            if (style == null) continue;
            var image = AccountClient.Text(style, "image_url");
            if (image.StartsWith("https://")) { var view = Media.View(image, Stretch.Fill); view.IsHitTestVisible = false; grid.Children.Add(view); continue; }
            var layer = new OrnamentLayer(kind, style) { CornerRadius = radius };
            if (!layer.Empty) grid.Children.Add(layer);
        }
        return grid;
    }
}
