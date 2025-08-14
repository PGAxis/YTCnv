using System.Text.Json;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace YTCnv
{
    public class SettingsSave : INotifyPropertyChanged
    {
        public const string ApiKeyPref = "YoutubeApiKey";

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

        //---------- Settings values ----------

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

        private bool quickDwnld = true;
        public bool QuickDwnld
        {
            get => quickDwnld;
            set
            {
                if (quickDwnld != value)
                {
                    quickDwnld = value;
                    OnPropertyChanged(nameof(QuickDwnld));
                    SaveSettings();
                }
            }
        }

        //---------- Singleton functions ----------

        public byte APIKeyValidity = 1;

        public async Task<byte> TestApiKey(string? apiKey)
        {
            if (string.IsNullOrWhiteSpace(apiKey))
                return 3;

            try
            {
                using var http = new HttpClient();
                string testUrl = $"https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=test&maxResults=1&key={apiKey}";
                var result = await http.GetAsync(testUrl);
                if (result.IsSuccessStatusCode)
                    return 0;
                else
                    return 1;
            }
#if ANDROID
            catch (Java.Net.UnknownHostException)
            {
                return 2;
            }
#endif
            catch (HttpRequestException)
            {
                return 2;
            }
            catch (TaskCanceledException)
            {
                return 2;
            }
            catch (Exception)
            {
                return 1;
            }
        }

        //---------- Singleton values ----------

        public bool IsDownloadRunning = false;

        //---------- Save/Load ----------

        public void SaveSettings()
        {
            SettingsClass settings = new SettingsClass
            {
                UseUpTo4K = Use4K,
                QuickDownload = QuickDwnld,
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
                QuickDwnld = settings.QuickDownload;
            }
        }

        public class SettingsClass
        {
            public bool UseUpTo4K { get; set; }
            public bool QuickDownload { get; set; }
        }

        public event PropertyChangedEventHandler PropertyChanged;
        protected void OnPropertyChanged([CallerMemberName] string propertyName = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }

}

