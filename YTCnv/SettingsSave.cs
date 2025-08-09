using System.Text.Json;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace YTCnv
{
    public class SettingsSave : INotifyPropertyChanged
    {
        private static SettingsSave instance;
        private static object instanceLock = new object();
        private static string settingsPath = Path.Combine(FileSystem.AppDataDirectory, "settings.json");

        public static SettingsSave Instance()
        {
            if (instance == null)
            {
                lock (instanceLock)
                {
                    if (instance == null)
                    {
                        instance = new SettingsSave();
                    }
                }
            }
            return instance;
        }

        private bool use4K = false;
        public bool Use4K
        {
            get => use4K;
            set
            {
                if (use4K != value)
                {
                    use4K = value;
                    OnPropertyChanged(nameof(Use4K));
                    SaveSettings();
                }
            }
        }

        public void SaveSettings()
        {
            SettingsClass settings = new SettingsClass
            {
                UseUpTo4K = Use4K,
            };
            string json = JsonSerializer.Serialize(settings);
            File.WriteAllText(settingsPath, json);
        }

        public void LoadSettings()
        {
            if (File.Exists(settingsPath))
            {
                string json = File.ReadAllText(settingsPath);
                SettingsClass settings = JsonSerializer.Deserialize<SettingsClass>(json);
                Use4K = settings.UseUpTo4K;
            }
        }

        public class SettingsClass
        {
            public bool UseUpTo4K { get; set; }
        }

        public event PropertyChangedEventHandler PropertyChanged;
        protected void OnPropertyChanged([CallerMemberName] string propertyName = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }

}

