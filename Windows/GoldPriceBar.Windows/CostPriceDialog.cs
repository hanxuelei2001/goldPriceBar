using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using GoldPriceBar.Core;

namespace GoldPriceBar.Windows;

internal sealed class CostPriceDialog : Window
{
    private readonly Dictionary<GoldProvider, TextBox> inputs = new();

    internal CostPriceDialog(AppSettings settings)
    {
        Title = "设置成本价";
        Width = 380;
        Height = 280;
        ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        ShowInTaskbar = false;
        Topmost = true;

        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(UiStyles.Label("为每个来源设置成本价，留空表示未设置。", 13, System.Windows.Media.Brushes.Black));
        foreach (var provider in Enum.GetValues<GoldProvider>())
        {
            var input = new TextBox
            {
                Text = settings.CostPriceFor(provider)?.ToString("F2", CultureInfo.InvariantCulture) ?? string.Empty,
                Width = 140,
                FontSize = 14,
                Margin = new Thickness(10, 0, 0, 0),
            };
            inputs[provider] = input;
            var row = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 12, 0, 0),
            };
            row.Children.Add(UiStyles.Label(provider.ShortName(), 13, System.Windows.Media.Brushes.Black));
            row.Children.Add(input);
            panel.Children.Add(row);
        }

        var save = new Button { Content = "保存", Width = 80, IsDefault = true, Margin = new Thickness(6, 0, 0, 0) };
        var skip = new Button { Content = "跳过", Width = 80, IsCancel = true, Margin = new Thickness(6, 0, 0, 0) };
        save.Click += (_, _) =>
        {
            var values = new Dictionary<GoldProvider, double>();
            foreach (var pair in inputs)
            {
                var text = pair.Value.Text.Trim();
                if (text.Length == 0) continue;
                if (!double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out var value) ||
                    !double.IsFinite(value) ||
                    value <= 0)
                {
                    MessageBox.Show(this, "请输入有效的正数成本价，或留空表示未设置。", "输入无效", MessageBoxButton.OK, MessageBoxImage.Warning);
                    return;
                }
                values[pair.Key] = value;
            }
            Result = values;
            DialogResult = true;
        };
        var buttons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right,
            Margin = new Thickness(0, 18, 0, 0),
        };
        buttons.Children.Add(skip);
        buttons.Children.Add(save);
        panel.Children.Add(buttons);
        Content = panel;
        Loaded += (_, _) =>
        {
            if (inputs.TryGetValue(settings.Provider, out var focused) && focused is not null) focused.Focus();
        };
    }

    internal Dictionary<GoldProvider, double>? Result { get; private set; }
}
