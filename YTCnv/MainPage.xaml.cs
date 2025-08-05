#if ANDROID
using Android.Runtime;
using Android.Content;
using Android.Provider;
#endif
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;
using YoutubeExplode.Common;
using Settings = YTCnv.Screens.Settings;
using System.Threading.Tasks;
using YTCnv.Screens;

namespace YTCnv
{
    /*var context = Android.App.Application.Context;
    var intent = new Intent(context, typeof(MediaPlayerNotificationService));
    context.StartForegroundService(intent);*/

    public partial class MainPage : ContentPage
    {
        CancellationTokenSource _downloadCts;
        private bool _4KChoice;

        private SettingsSave settings = SettingsSave.Instance();

        public MainPage()
        {
            InitializeComponent();
            FormatPicker.SelectedIndex = 0;
            settings.LoadSettings();
        }

        private void OnDownloadClicked(object sender, EventArgs e)
        {
            _downloadCts = new CancellationTokenSource();
            StatusLabel.IsVisible = false;
            //Console.WriteLine("Writing currently yay :D");
            if (Connectivity.NetworkAccess == NetworkAccess.Internet)
            {
                Task.Run(async () => await DoTheThing());
            }
            else
            {
                StatusLabel.IsVisible = true;
                StatusLabel.Text = "Please connect to the internet";
            }
        }

        private async Task DoTheThing()
        {

            YoutubeClient YouTube = new YoutubeClient();

            _4KChoice = settings.Use4K;

            MainThread.BeginInvokeOnMainThread(() =>
            {
                DownloadButton.IsVisible = false;
                CancelButton.IsVisible = true;
            });
            string url = UrlEntry.Text;
            int selectedFormat = FormatPicker.SelectedIndex;

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
                    DownloadButton.IsVisible = true;
                    CancelButton.IsVisible = false;
                });
                return;
            }

            string m4aPath = Path.Combine(FileSystem.CacheDirectory, $"audio.m4a");
            string mp4Path = Path.Combine(FileSystem.CacheDirectory, "video.mp4");
            string semiOutput = Path.Combine(FileSystem.AppDataDirectory, "semi-outputVideo.mp4");
            string semiOutputAudio = Path.Combine(FileSystem.AppDataDirectory, "semi-outputAudio.mp3");

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
                        UrlEntry.Text = "";
                        DownloadButton.IsVisible = true;
                        CancelButton.IsVisible= false;
                    });
                    return;
                }

                string author = video.Author.ChannelTitle;                
                string title = CleanTitle(video.Title, author);

                string thumbnailUrl = video.Thumbnails.GetWithHighestResolution().Url;
                using HttpClient http = new HttpClient();
                byte[] bytes = await http.GetByteArrayAsync(thumbnailUrl);
                string imagePath = Path.Combine(FileSystem.CacheDirectory, "thumbnail.jpg");
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
                    IStreamInfo audioStream = streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).TryGetWithHighestBitrate();
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
                }
                if (selectedFormat == 1)
                {
                    IStreamInfo audioStream = streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).TryGetWithHighestBitrate();
                    IVideoStreamInfo videoStream = _4KChoice ?
                        streamManifest.GetVideoOnlyStreams().TryGetWithHighestVideoQuality() :
                        streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).TryGetWithHighestVideoQuality();

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
                        await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v libx264 -pix_fmt yuv420p -preset faster -crf 23 -c:a copy -map 0:v:0 -map 1:a:0 -shortest -metadata title=\"{title}\" -metadata artist=\"{author}\" \"{semiOutput}\"");
                    else
                        await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v copy -c:a copy -map 0:v:0 -map 1:a:0 -shortest -metadata title=\"{title}\" -metadata artist=\"{author}\" \"{semiOutput}\"");

                    SaveVideoToDownloads(Android.App.Application.Context, title + ".mp4", semiOutput);
#endif

                    File.Delete(m4aPath);
                    File.Delete(mp4Path);
                    File.Delete(semiOutput);
                }
            }
            catch (OperationCanceledException)
            {
                MainThread.BeginInvokeOnMainThread(async () =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    //UrlEntry.Text = "";
                    DownloadButton.IsVisible = true;
                    CancelButton.IsVisible = false;
                    StatusLabel.IsVisible = false;

                    await DisplayAlert("Canceled", "The download was cancelled.", "OK");

                    if (File.Exists(m4aPath))
                        File.Delete(m4aPath);
                    if (File.Exists(mp4Path))
                        File.Delete(mp4Path);
                    if (File.Exists(semiOutput))
                        File.Delete(semiOutput);
                    if (File.Exists(semiOutputAudio))
                        File.Delete(semiOutputAudio);
                });

#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
            }
            catch (Exception ex)
            {
                if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                {
                    MainThread.BeginInvokeOnMainThread(() =>
                    {
                        StatusLabel.IsVisible = true;
                        StatusLabel.Text = "Lost connection, please reconnect";
                        DownloadIndicator.IsVisible = false;
                        DownloadIndicator.IsRunning = false;
                        DwnldProgress.IsVisible = false;
                        UrlEntry.Text = "";
                        DownloadButton.IsVisible = true;
                        CancelButton.IsVisible = false;
                    });

                    if (File.Exists(m4aPath))
                        File.Delete(m4aPath);
                    if (File.Exists(mp4Path))
                        File.Delete(mp4Path);
                    if (File.Exists(semiOutput))
                        File.Delete(semiOutput);
                    if (File.Exists(semiOutputAudio))
                        File.Delete(semiOutputAudio);
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
                        DownloadButton.IsVisible = true;
                        CancelButton.IsVisible = false;
                        StatusLabel.IsVisible = false;
                    });

                    if (File.Exists(m4aPath))
                        File.Delete(m4aPath);
                    if (File.Exists(mp4Path))
                        File.Delete(mp4Path);
                    if (File.Exists(semiOutput))
                        File.Delete(semiOutput);
                    if (File.Exists(semiOutputAudio))
                        File.Delete(semiOutputAudio);
                }
#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
            }
            finally
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    UrlEntry.Text = "";
                    DownloadButton.IsVisible = true;
                    CancelButton.IsVisible = false;
                    StatusLabel.IsVisible = false;
                });

                if (File.Exists(m4aPath))
                    File.Delete(m4aPath);
                if (File.Exists(mp4Path))
                    File.Delete(mp4Path);
                if (File.Exists(semiOutput))
                    File.Delete(semiOutput);
                if (File.Exists(semiOutputAudio))
                    File.Delete(semiOutputAudio);
#if ANDROID
                var context = Android.App.Application.Context;
                var stopIntent = new Intent(context, Java.Lang.Class.FromType(typeof(DownloadNotificationService)));
                context.StopService(stopIntent);
#endif
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
            title = title.Replace("(Official Song)", "");
            title = title.Replace("[Official Song]", "");
            title = title.Replace("[Lyrics]", "");
            title = title.Replace("(Lyrics)", "");
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
    }
}
