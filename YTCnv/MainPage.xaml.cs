#if ANDROID
using Android.Content;
using Android.Provider;
#endif
using YoutubeExplode;
using YoutubeExplode.Common;
using YoutubeExplode.Videos.Streams;
using Settings = YTCnv.Screens.Settings;
using YTCnv.Screens;

namespace YTCnv
{
    public partial class MainPage : ContentPage
    {
        CancellationTokenSource _downloadCts;

        private bool _4KChoice;
        private bool fastDwnld;

        private const string ApiKeyPref = "YoutubeApiKey";

        private Dictionary<double, string> audioOptions;
        private Dictionary<int, string> videoOptions;

        private string url;

        private SettingsSave settings = SettingsSave.Instance();

        public MainPage()
        {
            InitializeComponent();
            FormatPicker.SelectedIndex = 0;
            settings.LoadSettings();
            SetApiKey();
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();

            fastDwnld = settings.QuickDwnld;
            
            if (!settings.IsDownloadRunning)
                ResetMainPageState(fastDwnld);
        }

        private void SetApiKey()
        {
            Task.Run(async () => settings.APIKeyValidity = await settings.TestApiKey(Preferences.Get(ApiKeyPref, null)));
        }

        private void OnLoadClicked(object sender, EventArgs e)
        {
            StatusLabel.IsVisible = false;
            if (Connectivity.NetworkAccess == NetworkAccess.Internet)
            {
                Task.Run(async () => await LoadVideoMetadata());
            }
            else
            {
                StatusLabel.IsVisible = true;
                StatusLabel.Text = "Please connect to the internet";
            }
        }

        private async Task LoadVideoMetadata()
        {
            settings.IsDownloadRunning = true;

            YoutubeClient YouTube = new YoutubeClient();

            _4KChoice = settings.Use4K;

            LoadButton.IsEnabled = false;
            url = UrlEntry.Text;

            if (string.IsNullOrWhiteSpace(url))
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    await DisplayAlert("", "Please enter a YouTube URL", "OK");
                    ResetMainPageState(fastDwnld);
                });
                settings.IsDownloadRunning = false;
                return;
            }

            MainThread.BeginInvokeOnMainThread(() =>
            {
                DownloadIndicator.IsVisible = true;
                DownloadIndicator.IsRunning = true;
                StatusLabel.IsVisible = true;
                StatusLabel.Text = "Retrieving video metadata";
            });

            try
            {
                YoutubeExplode.Videos.Video? video = await YouTube.Videos.GetAsync(url);

                if (video == null)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Invalid URL", "Please enter a valid YouTube URL", "OK");
                        ResetMainPageState(fastDwnld);
                    });
                    settings.IsDownloadRunning = false;
                    return;
                }

                StreamManifest streamManifest = await YouTube.Videos.Streams.GetManifestAsync(url);

                List<AudioOnlyStreamInfo> audioStreams = streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).OrderByDescending(s => s.Bitrate).ToList();
                audioOptions = audioStreams.GroupBy(s => (int)Math.Floor(s.Bitrate.KiloBitsPerSecond)).Select(g => g.OrderByDescending(s => s.Bitrate.KiloBitsPerSecond).First()).ToDictionary(s => s.Bitrate.KiloBitsPerSecond, s => $"{Math.Round(s.Bitrate.KiloBitsPerSecond)} kbps ({s.Size.MegaBytes:F1} MB)");

                List<VideoOnlyStreamInfo> videoStreams = _4KChoice ?
                    streamManifest.GetVideoOnlyStreams().OrderByDescending(s => s.VideoQuality.MaxHeight).ToList() :
                    streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).OrderByDescending(s => s.VideoQuality.MaxHeight).ToList();
                videoOptions = videoStreams.ToDictionary(s => s.VideoQuality.MaxHeight, s => $"{s.VideoQuality.Label} ({s.Size.MegaBytes:F1} MB)");

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    LoadButton.IsVisible = false;
                    LoadButton.IsEnabled = true;
                    StatusLabel.IsVisible = false;
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    downloadOptions.IsVisible = true;
                    qualityPicker.IsVisible = true;
                    qualityPicker.ItemsSource = audioOptions.Values.ToList();
                    qualityPicker.SelectedIndex = 0;
                    DownloadButton.IsVisible = true;
                });

                settings.IsDownloadRunning = false;
            }
            catch (Exception ex)
            {
                if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Lost connection", "Please connect to the internet", "OK");
                        ResetMainPageState(fastDwnld, false);
                    });
                    settings.IsDownloadRunning = false;
                }
                else
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Error", ex.Message, "OK");
                        ResetMainPageState(fastDwnld);
                    });
                    settings.IsDownloadRunning = false;
                }
            }
        }

        private void OnDownloadClicked(object sender, EventArgs e)
        {
            FormatPicker.IsEnabled = false;
            qualityPicker.IsEnabled = false;

            _downloadCts = new CancellationTokenSource();
            StatusLabel.IsVisible = false;
            if (Connectivity.NetworkAccess == NetworkAccess.Internet)
            {
                Task.Run(async () => await DoTheThing(fastDwnld));
            }
            else
            {
                Task.Run(async () => await DisplayAlert("Lost connection", "Please connect to the internet", "OK"));
                ResetMainPageState(fastDwnld, false);
            }
        }

        private async Task DoTheThing(bool useNewUrl)
        {
            settings.IsDownloadRunning = true;

            YoutubeClient YouTube = new YoutubeClient();

            _4KChoice = settings.Use4K;

            MainThread.BeginInvokeOnMainThread(() =>
            {
                DownloadButton.IsVisible = false;
                CancelButton.IsVisible = true;
            });

            int selectedFormat = FormatPicker.SelectedIndex;

            Progress<double> progress = new Progress<double>(p =>
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DwnldProgress.Progress = p;
                });
            });

            if (useNewUrl)
                url = UrlEntry.Text;

            if (string.IsNullOrWhiteSpace(url))
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    await DisplayAlert("No URL", "Please enter a YouTube URL", "OK");
                    ResetMainPageState(fastDwnld);
                });
                settings.IsDownloadRunning = false;
                return;
            }

            string m4aPath = Path.Combine(FileSystem.CacheDirectory, $"audio.m4a");
            string mp4Path = Path.Combine(FileSystem.CacheDirectory, "video.mp4");
            string semiOutput = Path.Combine(FileSystem.AppDataDirectory, "semi-outputVideo.mp4");
            string semiOutputAudio = Path.Combine(FileSystem.AppDataDirectory, "semi-outputAudio.mp3");
            string imagePath = Path.Combine(FileSystem.CacheDirectory, "thumbnail.jpg");

            try
            {
#if ANDROID
                var context = Android.App.Application.Context;
                var intent = new Intent(context, typeof(DownloadNotificationService));
                context.StartForegroundService(intent);
#endif

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DownloadIndicator.IsVisible = true;
                    DownloadIndicator.IsRunning = true;
                    StatusLabel.IsVisible = true;
                    StatusLabel.Text = "Retrieving video";
                });

                YoutubeExplode.Videos.Video? video = await YouTube.Videos.GetAsync(url, _downloadCts.Token);

                if (video == null)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Invalid URL", "Please enter a valid YouTube URL", "OK");
                        ResetMainPageState(fastDwnld);
                    });
                    settings.IsDownloadRunning = false;
                    return;
                }

                string author = video.Author.ChannelTitle;
                string title = CleanTitle(video.Title, author);

                string thumbnailUrl = video.Thumbnails.GetWithHighestResolution().Url;
                using HttpClient http = new HttpClient();
                byte[] bytes = await http.GetByteArrayAsync(thumbnailUrl);
                File.WriteAllBytes(imagePath, bytes);

                StreamManifest streamManifest = await YouTube.Videos.Streams.GetManifestAsync(url, _downloadCts.Token);

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    DwnldProgress.IsVisible = true;
                    StatusLabel.Text = $"Downloading {title}";
                });

                if (File.Exists(m4aPath))
                    File.Delete(m4aPath);
                if (File.Exists(mp4Path))
                    File.Delete(mp4Path);
                if (File.Exists(semiOutput))
                    File.Delete(semiOutput);
                if (File.Exists(semiOutputAudio))
                    File.Delete(semiOutputAudio);

                if (selectedFormat == 0)
                {
                    IStreamInfo audioStream = fastDwnld ? streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).TryGetWithHighestBitrate() : streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).FirstOrDefault(s => s.Bitrate.KiloBitsPerSecond == audioOptions.ElementAt(qualityPicker.SelectedIndex).Key);
                    await YouTube.Videos.Streams.DownloadAsync(audioStream, m4aPath, progress: progress, cancellationToken: _downloadCts.Token);

                    MainThread.BeginInvokeOnMainThread(() =>
                    {
                        DwnldProgress.IsVisible = false;
                        DownloadIndicator.IsVisible = true;
                        DownloadIndicator.IsRunning = true;
                        StatusLabel.Text = "Adding metadata";
                    });

#if ANDROID
                    await FFmpegInterop.RunFFmpegCommand($"-y -i \"{m4aPath}\" -i \"{imagePath}\" -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v mjpeg -disposition:v attached_pic -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover\" -metadata title=\"{title}\" -metadata artist=\"{author}\"  -threads 1 \"{semiOutputAudio}\"");

                    SaveAudioToDownloads(Android.App.Application.Context, title + ".mp3", semiOutputAudio);
#endif

                    File.Delete(m4aPath);
                    File.Delete(semiOutputAudio);
                    settings.IsDownloadRunning = false;
                }
                if (selectedFormat == 1)
                {
                    IStreamInfo audioStream = streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).TryGetWithHighestBitrate();
                    IVideoStreamInfo videoStream = fastDwnld ?
                        (_4KChoice ? 
                            (IVideoStreamInfo)streamManifest.GetVideoOnlyStreams().TryGetWithHighestBitrate() :
                            (IVideoStreamInfo)streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).TryGetWithHighestBitrate()) :
                        (_4KChoice ?
                        (videoOptions.ElementAt(qualityPicker.SelectedIndex).Key > 1080 ?
                            (IVideoStreamInfo)streamManifest.GetVideoOnlyStreams().FirstOrDefault(s => s.VideoQuality.MaxHeight == videoOptions.ElementAt(qualityPicker.SelectedIndex).Key) :
                            (IVideoStreamInfo)streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).FirstOrDefault(s => s.VideoQuality.MaxHeight == videoOptions.ElementAt(qualityPicker.SelectedIndex).Key)) :
                        streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).FirstOrDefault(s => s.VideoQuality.MaxHeight == videoOptions.ElementAt(qualityPicker.SelectedIndex).Key));

                    bool isMoreThan1080p = videoStream.VideoQuality.MaxHeight > 1080;

                    Task audioTask = YouTube.Videos.Streams.DownloadAsync(audioStream, m4aPath, cancellationToken: _downloadCts.Token).AsTask();
                    Task videoTask = YouTube.Videos.Streams.DownloadAsync(videoStream, mp4Path, progress: progress, cancellationToken: _downloadCts.Token).AsTask();

                    await Task.WhenAll(audioTask, videoTask);

                    MainThread.BeginInvokeOnMainThread(() =>
                    {
                        DwnldProgress.IsVisible = false;
                        DownloadIndicator.IsVisible = true;
                        DownloadIndicator.IsRunning = true;
                        StatusLabel.Text = "Joining audio and video";
                    });

#if ANDROID
                    if (_4KChoice)
                    {
                        if (isMoreThan1080p)
                            await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v libx264 -pix_fmt yuv420p -preset faster -crf 23 -c:a copy -map 0:v:0 -map 1:a:0 -shortest -metadata title=\"{title}\" -metadata artist=\"{author}\" \"{semiOutput}\"");
                        else
                            await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v copy -c:a copy -map 0:v:0 -map 1:a:0 -shortest -metadata title=\"{title}\" -metadata artist=\"{author}\" \"{semiOutput}\"");
                    }
                    else
                        await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v copy -c:a copy -map 0:v:0 -map 1:a:0 -shortest -metadata title=\"{title}\" -metadata artist=\"{author}\" \"{semiOutput}\"");

                    SaveVideoToDownloads(Android.App.Application.Context, title + ".mp4", semiOutput);
#endif

                    File.Delete(m4aPath);
                    File.Delete(mp4Path);
                    File.Delete(semiOutput);
                    settings.IsDownloadRunning = false;
                }
            }
            catch (OperationCanceledException)
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    await DisplayAlert("Canceled", "The download was cancelled.", "OK");
                    ResetMainPageState(fastDwnld, false);

                    DeleteFiles();
                });

#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
                settings.IsDownloadRunning = false;
            }
            catch (Exception ex)
            {
                if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Lost connection", "Please connect to the internet", "OK");
                        ResetMainPageState(fastDwnld, false);
                    });

                    DeleteFiles();
                }
                else
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        await DisplayAlert("Error", ex.Message, "OK");
                        ResetMainPageState(fastDwnld);
                    });

                    DeleteFiles();
                }
#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
                settings.IsDownloadRunning = false;
            }
            finally
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    ResetMainPageState(fastDwnld);
                });

                DeleteFiles();
#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
                settings.IsDownloadRunning = false;
            }

            void DeleteFiles()
            {
                if (File.Exists(m4aPath))
                    File.Delete(m4aPath);
                if (File.Exists(mp4Path))
                    File.Delete(mp4Path);
                if (File.Exists(semiOutput))
                    File.Delete(semiOutput);
                if (File.Exists(semiOutputAudio))
                    File.Delete(semiOutputAudio);
                if (File.Exists(imagePath))
                    File.Delete(imagePath);
            }
        }

        public static string CleanTitle(string title, string author)
        {
            title = new string(title.Where(c => !Path.GetInvalidFileNameChars().Contains(c)).ToArray());
            title = title.Replace("(Official Music Video)", "");
            title = title.Replace("[Official Music Video]", "");
            title = title.Replace("(Official Video)", "");
            title = title.Replace("[Official Video]", "");
            title = title.Replace("(Official Audio)", "");
            title = title.Replace("[Official Audio]", "");
            title = title.Replace("(Official Audio Visualizer)", "");
            title = title.Replace("[Official Audio Visualizer]", "");
            title = title.Replace("(Official Song)", "");
            title = title.Replace("[Official Song]", "");
            title = title.Replace("(Full Album)", "");
            title = title.Replace("[Full Album]", "");
            title = title.Replace("(Intro)", "");
            title = title.Replace("[Intro]", "");
            title = title.Replace("(Deluxe Edition)", "");
            title = title.Replace("[Deluxe Edition]", "");
            title = title.Replace("(Lyrics)", "");
            title = title.Replace("[Lyrics]", "");
            title = title.Replace(author, "");
            title = title.Trim();
            title = title.Trim('-');
            title = title.Trim();

            if (string.IsNullOrWhiteSpace(title))
                title = "YouTube_Audio";

            if (title.Length > 60)
                title = title.Substring(0, 60);


            return title;
        }

#if ANDROID
        public static void SaveAudioToDownloads(Context context, string fileName, string inputFilePath)
        {
            ContentValues values = new ContentValues();
            values.Put(MediaStore.IMediaColumns.DisplayName, fileName);
            values.Put(MediaStore.IMediaColumns.MimeType, "audio/mpeg");
            values.Put(MediaStore.IMediaColumns.RelativePath, "Download/");

            Android.Net.Uri collection = MediaStore.Downloads.ExternalContentUri;
            ContentResolver resolver = context.ContentResolver;

            Android.Net.Uri fileUri = resolver.Insert(collection, values);

            if (fileUri != null)
            {
                using var outputStream = resolver.OpenOutputStream(fileUri);
                using var inputStream = File.OpenRead(inputFilePath);
                inputStream.CopyTo(outputStream);
            }
        }

        public static void SaveVideoToDownloads(Context context, string fileName, string inputFilePath)
        {
            ContentValues values = new ContentValues();
            values.Put(MediaStore.IMediaColumns.DisplayName, fileName);
            values.Put(MediaStore.IMediaColumns.MimeType, "video/mp4");
            values.Put(MediaStore.IMediaColumns.RelativePath, "Download/");

            Android.Net.Uri collection = MediaStore.Downloads.ExternalContentUri;
            ContentResolver resolver = context.ContentResolver;

            Android.Net.Uri fileUri = resolver.Insert(collection, values);

            if (fileUri != null)
            {
                using var outputStream = resolver.OpenOutputStream(fileUri);
                using var inputStream = File.OpenRead(inputFilePath);
                inputStream.CopyTo(outputStream);
            }
        }
#endif

        private void OnCancelClicked(object sender, EventArgs e)
        {
#if ANDROID
            FFmpegInterop.CancelFFmpegCommand();
#endif

            _downloadCts?.Cancel();

            CancelButton.IsVisible = false;
            DownloadButton.IsVisible = true;
            DwnldProgress.IsVisible = false;
            DownloadIndicator.IsVisible = false;
            DownloadIndicator.IsRunning = false;
            StatusLabel.IsVisible = false;
        }

        private async void OpenSettings(object sender, EventArgs e)
        {
            await Shell.Current.GoToAsync(nameof(Settings));
        }

        private async void OpenSearch(object sender, EventArgs e)
        {
            await Shell.Current.GoToAsync(nameof(YouTubeSearch));
        }

        private void OnWantedFormatChanged(object sender, EventArgs e)
        {
            if (qualityPicker == null || audioOptions == null || videoOptions == null)
                return;

            if (sender is not Picker picker)
                return;

            switch (picker.SelectedIndex)
            {
                case 0:
                    qualityPicker.ItemsSource = audioOptions.Values.ToList();
                    qualityPicker.SelectedIndex = 0;
                    break;
                case 1:
                    qualityPicker.ItemsSource = videoOptions.Values.ToList();
                    qualityPicker.SelectedIndex = 0;
                    break;
            }
        }

        private void ResetMainPageState(bool isQuickDownload, bool clearUrl = true)
        {
            downloadOptions.IsVisible = false;
            FormatPicker.IsEnabled = true;
            qualityPicker.IsEnabled = true;
            qualityPicker.SelectedIndex = 0;
            DownloadButton.IsVisible = false;
            CancelButton.IsVisible = false;
            DwnldProgress.IsVisible = false;
            DownloadIndicator.IsVisible = false;
            DownloadIndicator.IsRunning = false;
            StatusLabel.IsVisible = false;

            UrlEntry.Text = clearUrl ? "" : UrlEntry.Text;
            url = "";

            LoadButton.IsVisible = true;
            LoadButton.IsEnabled = true;

            if (isQuickDownload)
            {
                QuickDownloadPage();
            }
        }

        private void QuickDownloadPage()
        {
            downloadOptions.IsVisible = true;
            qualityPicker.IsVisible = false;
            LoadButton.IsVisible = false;
            DownloadButton.IsVisible = true;
        }
    }
}