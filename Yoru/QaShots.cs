using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace Yoru;

/// <summary>
/// Снимки экранов для проверки дизайна в CI: запуск с переменной окружения YORU_QA_SHOTS=&lt;папка&gt;.
/// Окно открывается без входа в аккаунт, проходит по основным экранам, сохраняет PNG и закрывается.
/// </summary>
static class QaShots
{
    public static string? Folder => Environment.GetEnvironmentVariable("YORU_QA_SHOTS") is { Length: > 0 } f ? f : null;
    public static bool Active => Folder != null;

    public static async Task Run(MainWindow window)
    {
        var folder = Folder!; Directory.CreateDirectory(folder);
        try
        {
            window.Width = 1280; window.Height = 860;
            await Until(() => window.QaCardCount > 0, 40);
            await Task.Delay(6000);
            Save(window, folder, "1-catalog.png");
            window.QaScrollCatalog(900); await Task.Delay(2500);
            Save(window, folder, "2-catalog-scrolled.png");
            window.QaScrollCatalog(0);
            if (window.QaOpenFirst())
            {
                await Task.Delay(9000);
                Save(window, folder, "3-detail.png");
                Shell.Back();
            }
            window.QaShowSettings(); await Task.Delay(1200);
            Save(window, folder, "4-settings.png");
            window.QaShowAuth(); await Task.Delay(1200);
            Save(window, folder, "5-auth.png");
        }
        catch (Exception e) { File.WriteAllText(Path.Combine(folder, "error.txt"), e.ToString()); }
        finally { Application.Current.Shutdown(); }
    }

    static async Task Until(Func<bool> condition, int seconds)
    {
        for (var i = 0; i < seconds * 4 && !condition(); i++) await Task.Delay(250);
    }

    static void Save(Window window, string folder, string name)
    {
        window.UpdateLayout();
        if (window.Content is not FrameworkElement root) return;
        var width = (int)Math.Ceiling(root.ActualWidth); var height = (int)Math.Ceiling(root.ActualHeight);
        if (width <= 0 || height <= 0) return;
        var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        var visual = new DrawingVisual();
        using (var context = visual.RenderOpen())
        {
            context.DrawRectangle(window.Background ?? Brushes.Black, null, new Rect(0, 0, width, height));
            context.DrawRectangle(new VisualBrush(root), null, new Rect(0, 0, width, height));
        }
        bitmap.Render(visual);
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var file = File.Create(Path.Combine(folder, name)); encoder.Save(file);
    }
}
