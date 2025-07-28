#if ANDROID
using Android.Runtime;
using Android.Content;
using Android.Provider;
#endif
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;
using YoutubeExplode.Common;

namespace YTCnv
{
    public partial class MainPage : ContentPage
    {
        YoutubeClient youtube = new YoutubeClient();

        public MainPage()
        {
            InitializeComponent();
            FormatPicker.SelectedIndex = 0;
        }

        private static string GetDownloadPath()
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
            Console.WriteLine("Writing currentaly yay :D");
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
            MainThread.BeginInvokeOnMainThread(() =>
            {
                DownloadButton.IsEnabled = false;
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
                    StatusLabel.IsVisible = true;
                    StatusLabel.Text = "Retrieving video";
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

                string author = video.Author.ChannelTitle;                
                string title = CleanTitle(video.Title, author);

                string thumbnailUrl = video.Thumbnails.GetWithHighestResolution().Url;
                using HttpClient http = new HttpClient();
                byte[] bytes = await http.GetByteArrayAsync(thumbnailUrl);
                string imagePath = Path.Combine(FileSystem.CacheDirectory, "thumbnail.jpg");
                File.WriteAllBytes(imagePath, bytes);

                StreamManifest streamManifest = await youtube.Videos.Streams.GetManifestAsync(url);

                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    DwnldProgress.IsVisible = true;
                    StatusLabel.Text = $"Downloading {title}";
                });

                IStreamInfo audioStream = streamManifest.GetAudioOnlyStreams().Where(s => s.Container == Container.Mp4).TryGetWithHighestBitrate();
                if (selectedFormat == 0)
                {
                    string m4aPath = Path.Combine(FileSystem.CacheDirectory, $"audio.m4a");
                    string semiOutput = Path.Combine(FileSystem.AppDataDirectory, $"semi-output.mp3");
                    string semiOutput2 = Path.Combine(FileSystem.AppDataDirectory, $"semi-output2.mp3");

                    if (File.Exists(m4aPath))
                        File.Delete(m4aPath);

                    if (File.Exists(semiOutput))
                        File.Delete(semiOutput);

                    await youtube.Videos.Streams.DownloadAsync(audioStream, m4aPath, progress: progress);

                    MainThread.BeginInvokeOnMainThread(() =>
                    {
                        DwnldProgress.IsVisible = false;
                        DownloadIndicator.IsVisible = true;
                        DownloadIndicator.IsRunning = true;
                        StatusLabel.Text = "Converting to MP3";
                    });

#if ANDROID
                    await FFmpegInterop.RunFFmpegCommand($"-y -i \"{m4aPath}\" -i \"{imagePath}\" -map 0:a -map 1:v -c:a libmp3lame -b:a 192k -c:v mjpeg -disposition:v attached_pic -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover\" -metadata title=\"{title}\" -metadata artist=\"{author}\"  -threads 1 \"{semiOutput}\"");
                    //await Task.Delay(2000);

                    SaveAudioToDownloads(Android.App.Application.Context, title + ".mp3", semiOutput);
#endif

                    File.Delete(m4aPath);
                    File.Delete(semiOutput);
                }
                if (selectedFormat == 1)
                {
                    IVideoStreamInfo? videoStream = streamManifest.GetVideoOnlyStreams().Where(s => s.Container == Container.Mp4 && s.VideoCodec.ToString().Contains("avc")).TryGetWithHighestVideoQuality();

                    string m4aPath = Path.Combine(FileSystem.CacheDirectory, "audio.m4a");
                    string mp4Path = Path.Combine(FileSystem.CacheDirectory, "video.mp4");

                    if (File.Exists(m4aPath))
                        File.Delete(m4aPath);

                    if (File.Exists(mp4Path))
                        File.Delete(mp4Path);

                    await youtube.Videos.Streams.DownloadAsync(audioStream, m4aPath, progress: progress);

                    MainThread.BeginInvokeOnMainThread(() => StatusLabel.Text = $"Downloading video for {title}");
                    await youtube.Videos.Streams.DownloadAsync(videoStream, mp4Path, progress: progress);

                    MainThread.BeginInvokeOnMainThread(() =>
                    {
                        DwnldProgress.IsVisible = false;
                        DownloadIndicator.IsVisible = true;
                        DownloadIndicator.IsRunning = true;
                        StatusLabel.Text = "Joining audio and video";
                    });

                    string semiOutput = Path.Combine(FileSystem.AppDataDirectory, "semi-output.mp4");

#if ANDROID
                    bool succes = await FFmpegInterop.RunFFmpegCommand($"-y -i \"{mp4Path}\" -i \"{m4aPath}\" -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 -shortest \"{semiOutput}\"");         

                    //await Task.Delay(2000);

                    SaveVideoToDownloads(Android.App.Application.Context, title + ".mp4", semiOutput);
#endif

                    File.Delete(m4aPath);
                    File.Delete(mp4Path);
                    File.Delete(semiOutput);
                }
            }
            catch (System.Exception ex)
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
                        StatusLabel.IsVisible = false;
                    });
                }
            }
            finally
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    DownloadIndicator.IsVisible = false;
                    DownloadIndicator.IsRunning = false;
                    UrlEntry.Text = "";
                    DownloadButton.IsEnabled = true;
                    StatusLabel.IsVisible = false;
                });
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
    }
}
