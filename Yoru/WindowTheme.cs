using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
namespace Yoru;
internal static class WindowTheme
{
    [DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
    public static void Apply(Window window)
    {
        var area = SystemParameters.WorkArea;
        var width = Math.Max(400, area.Width - 24);
        var height = Math.Max(400, area.Height - 24);
        window.MinWidth = Math.Min(window.MinWidth, width);
        window.MinHeight = Math.Min(window.MinHeight, height);
        window.Width = Math.Min(window.Width, width);
        window.Height = Math.Min(window.Height, height);
        window.SourceInitialized += (_, _) =>
        {
            var enabled = 1;
            DwmSetWindowAttribute(new WindowInteropHelper(window).Handle, 20, ref enabled, sizeof(int));
        };
    }
}
