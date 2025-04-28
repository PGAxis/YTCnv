using Microsoft.Maui.Controls.PlatformConfiguration;
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;

namespace YTCnv
{
    public partial class MainPage : ContentPage
    {
        YoutubeClient youtube = new YoutubeClient();

        public MainPage()
        {
            InitializeComponent();
        }

        private string GetDownloadPath()
        {
#if ANDROID
            return Android.OS.Environment.GetExternalStoragePublicDirectory(Android.OS.Environment.DirectoryDownloads).AbsolutePath;
#elif WINDOWS
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
#else
            return FileSystem.AppDataDirectory;
#endif
        }

        private void OnDownloadClicked(object sender, EventArgs e)
        {
            StatusLabel.IsVisible = false;
            if (Connectivity.NetworkAccess == NetworkAccess.Internet)
            {
                Task.Run(async () => DoTheThing());
            }
            else
            {
                StatusLabel.IsVisible = true;
                StatusLabel.Text = "Please connect to the internet";
            }
        }

        private async Task DoTheThing()
        {
            MainThread.BeginInvokeOnMainThread(async () =>
            {
                DownloadButton.IsEnabled = false;
            });
            string url = UrlEntry.Text;

            Progress<double> progress = new Progress<double>(p =>
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DwnldProgress.Progress = p;
                });
            });

            if (string.IsNullOrWhiteSpace(url))
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    await DisplayAlert("", "Please enter a YouTube URL", "OK");
                    DownloadButton.IsEnabled = true;
                });
                return;
            }

            try
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    DownloadIndicator.IsVisible = true;
                    DownloadIndicator.IsRunning = true;
                });

                YoutubeExplode.Videos.Video? video = await youtube.Videos.GetAsync(url);

                if (video == null)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Invalid URL", "Please enter a valid YouTube URL", "OK");
                        UrlEntry.Text = "";
                        DownloadButton.IsEnabled = true;
                    });
                    return;
                }

                string title = video.Title;
                title = title.Where(c => !Path.GetInvalidFileNameChars().Contains(c)).ToArray().ToString();
                title = title.Replace("(Official Music Video)", "");
                title = title.Replace("[Official Music Video]", "");
                title = title.Replace("(Official Video)", "");
                title = title.Replace("[Official Video]", "");
                title = title.Replace("(Official Audio)", "");
                title = title.Replace("[Official Audio]", "");
                title = title.Replace("(Official Song)", "");
                title = title.Replace("[Official Song]", "");
                if (title.Length > 40)
                {
                    title = title.Substring(0, 50);
                }
                string author = video.Author.ChannelTitle.ToString();

                StreamManifest streamManifest = await youtube.Videos.Streams.GetManifestAsync(url);

                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    DwnldProgress.IsVisible = true;
                });

                IStreamInfo audioStream = streamManifest.GetAudioOnlyStreams().TryGetWithHighestBitrate();
                string finalPath = Path.Combine(GetDownloadPath(), $"{title}.mp3");

                if (File.Exists(finalPath))
                {
                    File.Delete(finalPath);
                }

                await youtube.Videos.Streams.DownloadAsync(audioStream, finalPath, progress: progress);

                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    DwnldProgress.IsVisible = false;
                    DownloadIndicator.IsVisible = true;
                    DownloadIndicator.IsRunning = true;
                });
            }
            catch (Exception ex)
            {
                if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        StatusLabel.IsVisible = true;
                        StatusLabel.Text = "Lost connection, please reconnect";
                        DownloadIndicator.IsVisible = false;
                        DownloadIndicator.IsRunning = false;
                        DwnldProgress.IsVisible = false;
                        UrlEntry.Text = "";
                        DownloadButton.IsEnabled = true;
                    });
                }
                else
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Error", ex.Message, "OK");
                        DownloadIndicator.IsVisible = false;
                        DownloadIndicator.IsRunning = false;
                        DwnldProgress.IsVisible = false;
                        UrlEntry.Text = "";
                        DownloadButton.IsEnabled = true;
                    });
                }
            }
            finally
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    UrlEntry.Text = "";
                    DownloadButton.IsEnabled = true;
                });
            }
        }
    }
}
