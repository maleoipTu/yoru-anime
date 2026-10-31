using System.Windows;
namespace Yoru;
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        DispatcherUnhandledException += (_, args) =>
        {
            Dialogs.Show(Current?.MainWindow, "Что-то пошло не так", "Не удалось завершить действие. Попробуй снова.\n\n" + args.Exception.Message);
            args.Handled = true;
        };
        base.OnStartup(e);
    }
}
