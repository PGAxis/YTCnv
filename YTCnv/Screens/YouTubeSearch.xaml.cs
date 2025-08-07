using Newtonsoft.Json.Linq;
using System.Collections.ObjectModel;
using System.Web;
using System.Xml;

namespace YTCnv.Screens;

public partial class YouTubeSearch : ContentPage
{
    private const string ApiKeyPref = "YoutubeApiKey";

    public ObservableCollection<YouTubeResult> SearchResults { get; set; } = new();

    public YouTubeSearch()
    {
        InitializeComponent();
        BindingContext = this;
    }

    private async void GoBack(object sender, EventArgs e)
    {
        await Shell.Current.GoToAsync("///MainPage");
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        SearchResults.Clear();
        SearchEntry.Text = "";

        var apiKey = Preferences.Get(ApiKeyPref, null);

        if (string.IsNullOrWhiteSpace(apiKey))
        {
            ApiKeyPrompt.IsVisible = true;
            SearchPanel.IsVisible = false;
        }
        else
        {
            bool valid = await TestApiKey(apiKey);
            if (valid)
            {
                ApiKeyPrompt.IsVisible = false;
                SearchPanel.IsVisible = true;
            }
            else
            {
                if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                {
                    ApiKeyPrompt.IsVisible = false;
                SearchPanel.IsVisible = true;
                }
                else
                    await DisplayAlert("Invalid Key", "Your API key is invalid or expired. Please enter a new one.", "OK");
                ApiKeyPrompt.IsVisible = true;
                SearchPanel.IsVisible = false;
            }
        }
    }

    private async void OnSaveApiKeyClicked(object sender, EventArgs e)
    {
        var newKey = ApiKeyEntry.Text?.Trim();
        if (string.IsNullOrWhiteSpace(newKey))
        {
            await DisplayAlert("Missing Key", "Please paste your YouTube API key.", "OK");
            return;
        }

        if (await TestApiKey(newKey))
        {
            Preferences.Set(ApiKeyPref, newKey);
            await DisplayAlert("Saved", "Your API key is saved and valid.", "OK");
            ApiKeyPrompt.IsVisible = false;
            SearchPanel.IsVisible = true;
        }
        else
        {
            if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                await DisplayAlert("No Internet", "Please connect to the internet before saving your API key", "OK");
            else
                await DisplayAlert("Invalid", "That API key appears to be invalid.", "OK");
        }
    }

    private async void OnCopyIdClicked(object sender, EventArgs e)
    {
        if (sender is Button btn && btn.CommandParameter is string videoId)
        {
            await Clipboard.SetTextAsync(videoId);
        }
    }

    private async void OnCopyUrlClicked(object sender, EventArgs e)
    {
        if (sender is Button btn && btn.CommandParameter is string videoId)
        {
            string url = $"https://www.youtube.com/watch?v={videoId}";
            await Clipboard.SetTextAsync(url);
        }
    }

    private async void OnSearchClicked(object sender, EventArgs e)
    {
        string query = SearchEntry.Text?.Trim();
        string apiKey = Preferences.Get(ApiKeyPref, null);

        if (string.IsNullOrEmpty(query) || string.IsNullOrEmpty(apiKey))
        {
            await DisplayAlert("Error", "Please enter a search term and ensure an API key is configured.", "OK");
            return;
        }

        GettingVidsIndicator.IsVisible = true;
        GettingVidsIndicator.IsRunning = true;

        SearchResults.Clear();

        try
        {
            string url = $"https://www.googleapis.com/youtube/v3/search" +
                         $"?part=snippet&type=video&maxResults=10" +
                         $"&q={HttpUtility.UrlEncode(query)}&key={apiKey}";

            using var http = new HttpClient();
            var json = await http.GetStringAsync(url);
            JObject obj = JObject.Parse(json);

            Dictionary<string, TimeSpan> durationMap = await GetDurationMap(obj, http, apiKey);

            foreach (var item in obj["items"]!)
            {
                var id = item["id"]?["videoId"]?.ToString();
                var snippet = item["snippet"];
                var title = snippet?["title"]?.ToString();
                var thumbnail = snippet?["thumbnails"]?["medium"]?["url"]?.ToString();
                var author = snippet?["channelTitle"]?.ToString();

                if (!string.IsNullOrEmpty(id) && title != null && thumbnail != null && author != null)
                {
                    durationMap.TryGetValue(id, out TimeSpan duration);

                    SearchResults.Add(new YouTubeResult
                    {
                        Title = title,
                        VideoId = id,
                        ThumbnailUrl = thumbnail,
                        Author = author,
                        Duration = duration,
                    });
                }
            }

            ResultsView.HeightRequest = (DeviceDisplay.MainDisplayInfo.Height / DeviceDisplay.MainDisplayInfo.Density) - 240;
        }
        catch (Exception ex)
        {
            if (Connectivity.NetworkAccess != NetworkAccess.Internet)
                await DisplayAlert("No Internet", "Please connect to the internet before searching for videos", "OK");
            else
                await DisplayAlert("Error", ex.Message, "OK");
            GettingVidsIndicator.IsVisible = false;
            GettingVidsIndicator.IsRunning = false;
        }
        finally
        {
            GettingVidsIndicator.IsVisible = false;
            GettingVidsIndicator.IsRunning = false;
        }
    }

    private async Task<bool> TestApiKey(string apiKey)
    {
        try
        {
            using var http = new HttpClient();
            string testUrl = $"https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=test&maxResults=1&key={apiKey}";
            var result = await http.GetAsync(testUrl);
            return result.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    private async Task<Dictionary<string, TimeSpan>> GetDurationMap(JObject obj, HttpClient http, string apiKey)
    {
        var videoIds = obj["items"]!.Select(item => item["id"]?["videoId"]?.ToString()).Where(id => !string.IsNullOrWhiteSpace(id)).ToList();

        string idsJoined = string.Join(",", videoIds);
        string detailsUrl = $"https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id={idsJoined}&key={apiKey}";

        var detailsJson = await http.GetStringAsync(detailsUrl);
        var detailsObj = JObject.Parse(detailsJson);

        return detailsObj["items"]!.ToDictionary(item => item["id"]!.ToString(), item => XmlConvert.ToTimeSpan(item["contentDetails"]!["duration"]!.ToString()));
    }

    public class YouTubeResult
    {
        public string Title { get; set; }
        public string VideoId { get; set; }
        public string ThumbnailUrl { get; set; }
        public string Author { get; set; }
        public TimeSpan Duration { get; set; }
    }

    private async void OnOpenGoogleApiPage(object sender, EventArgs e)
    {
        await Launcher.Default.OpenAsync("https://console.developers.google.com");
    }
}