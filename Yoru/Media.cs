using System.Buffers.Binary;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>Аватары и баннеры: подготовка к загрузке (те же правила, что на Android) и показ анимированных GIF/WebM.</summary>
public static class Media
{
    public const int AvatarSide = 512, BannerWidth = 1500, BannerHeight = 500;
    public sealed record Prepared(byte[] Data, string Mime, string Extension);

    /// <summary>Пустая строка — файл подходит; иначе понятное объяснение, что не так.</summary>
    public static string Problem(bool banner, string kind, long bytes, int width, int height, double seconds)
    {
        long maxBytes = banner ? 8L * 1024 * 1024 : 4L * 1024 * 1024; int maxW = banner ? 1920 : 640, maxH = banner ? 1080 : 640;
        if (bytes > maxBytes) return $"{(banner ? "Баннер" : "Аватар")} больше {maxBytes / 1024 / 1024} МБ. Сожми файл или выбери другой.";
        if (width <= 0 || height <= 0) return "Не получилось определить размер файла.";
        if (width > maxW || height > maxH) return $"Слишком большое разрешение: {width}×{height}. Максимум для {(banner ? "баннера" : "аватара")} — {maxW}×{maxH}.";
        if (kind == "webm" && seconds > 15) return "Видео длиннее 15 секунд. Обрежь его и попробуй снова.";
        if (!banner && Math.Abs(width - height) > Math.Max(width, height) * 0.35) return "Анимированный аватар должен быть почти квадратным.";
        return "";
    }

    /// <summary>Статичные картинки обрезаются по центру и сжимаются; GIF и WebM загружаются как есть, если подходят по правилам.</summary>
    public static Prepared Prepare(string path, bool banner)
    {
        var data = File.ReadAllBytes(path);
        var ext = Path.GetExtension(path).ToLowerInvariant();
        if (ext == ".webm")
        {
            var (w, h, seconds) = WebmInfo(data);
            var problem = Problem(banner, "webm", data.Length, w, h, seconds);
            if (problem.Length > 0) throw new InvalidOperationException(problem);
            return new Prepared(data, "video/webm", "webm");
        }
        BitmapDecoder decoder;
        try { decoder = BitmapDecoder.Create(new MemoryStream(data), BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad); }
        catch (Exception e) when (e is NotSupportedException or FileFormatException or ArgumentException) { throw new InvalidOperationException("Не получилось прочитать файл. Подходят PNG, JPEG, WebP, GIF и WebM."); }
        var first = decoder.Frames[0];
        if (decoder is GifBitmapDecoder && decoder.Frames.Count > 1)
        {
            var problem = Problem(banner, "gif", data.Length, first.PixelWidth, first.PixelHeight, 0);
            if (problem.Length > 0) throw new InvalidOperationException(problem);
            return new Prepared(data, "image/gif", "gif");
        }
        int tw = banner ? BannerWidth : AvatarSide, th = banner ? BannerHeight : AvatarSide;
        var scale = Math.Max((double)tw / first.PixelWidth, (double)th / first.PixelHeight);
        int cw = Math.Min(first.PixelWidth, (int)Math.Round(tw / scale)), ch = Math.Min(first.PixelHeight, (int)Math.Round(th / scale));
        BitmapSource result = new CroppedBitmap(first, new Int32Rect((first.PixelWidth - cw) / 2, (first.PixelHeight - ch) / 2, cw, ch));
        if (scale < 1) result = new TransformedBitmap(result, new ScaleTransform(scale, scale));
        var encoder = new JpegBitmapEncoder { QualityLevel = 88 };
        encoder.Frames.Add(BitmapFrame.Create(new FormatConvertedBitmap(result, PixelFormats.Bgr24, null, 0)));
        using var output = new MemoryStream(); encoder.Save(output);
        return new Prepared(output.ToArray(), "image/jpeg", "jpg");
    }

    /// <summary>Ширина, высота и длительность WebM из заголовка Matroska (EBML), без декодирования видео.</summary>
    public static (int Width, int Height, double Seconds) WebmInfo(byte[] data)
    {
        int width = 0, height = 0; double duration = 0; ulong timecodeScale = 1_000_000;
        void Walk(long start, long end)
        {
            var i = start;
            while (i < end)
            {
                if (!ReadId(data, ref i, out var id) || !ReadSize(data, ref i, out var size)) return;
                var stop = size < 0 ? end : Math.Min(end, i + size);
                switch (id)
                {
                    case 0x18538067: case 0x1549A966: case 0x1654AE6B: case 0xAE: case 0xE0: Walk(i, stop); break; // Segment, Info, Tracks, TrackEntry, Video
                    case 0xB0: width = (int)ReadUInt(data, i, size); break;
                    case 0xBA: height = (int)ReadUInt(data, i, size); break;
                    case 0x2AD7B1: timecodeScale = ReadUInt(data, i, size); break;
                    case 0x4489:
                        if (size == 4 && i + 4 <= data.Length) duration = BinaryPrimitives.ReadSingleBigEndian(data.AsSpan((int)i, 4));
                        else if (size == 8 && i + 8 <= data.Length) duration = BinaryPrimitives.ReadDoubleBigEndian(data.AsSpan((int)i, 8));
                        break;
                    case 0x1F43B675: return; // Cluster — дальше только кадры
                }
                if (size < 0) return;
                i = stop;
            }
        }
        Walk(0, data.Length);
        return (width, height, duration * timecodeScale / 1e9);
    }
    static bool ReadId(byte[] d, ref long i, out uint id)
    {
        id = 0; if (i >= d.Length) return false;
        int length = d[i] >= 0x80 ? 1 : d[i] >= 0x40 ? 2 : d[i] >= 0x20 ? 3 : d[i] >= 0x10 ? 4 : 0;
        if (length == 0 || i + length > d.Length) return false;
        for (var k = 0; k < length; k++) id = id << 8 | d[i + k];
        i += length; return true;
    }
    static bool ReadSize(byte[] d, ref long i, out long size)
    {
        size = 0; if (i >= d.Length) return false;
        int length = 1; while (length <= 8 && (d[i] & (0x80 >> (length - 1))) == 0) length++;
        if (length > 8 || i + length > d.Length) return false;
        ulong value = (ulong)(d[i] & (0xFF >> length)); bool unknown = value == (ulong)(0xFF >> length);
        for (var k = 1; k < length; k++) { value = value << 8 | d[i + k]; if (d[i + k] != 0xFF) unknown = false; }
        i += length; size = unknown ? -1 : (long)value; return true;
    }
    static ulong ReadUInt(byte[] d, long i, long size) { ulong v = 0; for (var k = 0; k < size && k < 8 && i + k < d.Length; k++) v = v << 8 | d[i + k]; return v; }

    public static bool IsAnimated(string url) { var path = Uri.TryCreate(url, UriKind.Absolute, out var u) ? u.AbsolutePath.ToLowerInvariant() : ""; return path.EndsWith(".gif") || path.EndsWith(".webm"); }

    /// <summary>Картинка или зацикленное беззвучное видео/GIF, заполняющее область.</summary>
    public static FrameworkElement View(string url, Stretch stretch = Stretch.UniformToFill)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https") return new Border();
        if (uri.AbsolutePath.EndsWith(".gif", StringComparison.OrdinalIgnoreCase))
        {
            var gif = new Image { Stretch = stretch };
            try
            {
                var source = new BitmapImage(uri);
                if (SystemParameters.ClientAreaAnimation) WpfAnimatedGif.ImageBehavior.SetAnimatedSource(gif, source); else gif.Source = source;
            }
            catch (Exception e) when (e is NotSupportedException or IOException) { }
            return gif;
        }
        if (IsAnimated(url))
        {
            var player = new MediaElement { Source = uri, LoadedBehavior = MediaState.Manual, UnloadedBehavior = MediaState.Close, IsMuted = true, Stretch = stretch };
            player.Loaded += (_, _) => { if (SystemParameters.ClientAreaAnimation) player.Play(); else { player.Play(); player.Pause(); } };
            player.MediaEnded += (_, _) => { player.Position = TimeSpan.Zero; player.Play(); };
            return player;
        }
        try { return new Image { Source = new BitmapImage(uri), Stretch = stretch }; }
        catch (Exception e) when (e is NotSupportedException or IOException) { return new Border(); }
    }
}
