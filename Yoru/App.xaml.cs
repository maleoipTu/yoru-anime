using System.Windows;
namespace Yoru;
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show("Не удалось завершить действие. Попробуйте снова.\n\n" + args.Exception.Message, "Yoru", MessageBoxButton.OK, MessageBoxImage.Warning);
            args.Handled = true;
        };
        base.OnStartup(e);
    }
}
