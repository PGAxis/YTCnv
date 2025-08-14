using CommunityToolkit.Maui.Views;
using Newtonsoft.Json.Linq;
using System.Collections.ObjectModel;
using System.Web;
using System.Xml;
using YTCnv.Popups;

namespace YTCnv.Screens;

public partial class YouTubeSearch : ContentPage
{
    private const string ApiKeyPref = "YoutubeApiKey";

    private SettingsSave settings = SettingsSave.Instance();

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

        switch (settings.APIKeyValidity)
        {
            case 0:
                ApiKeyPrompt.IsVisible = false;
                SearchPanel.IsVisible = true;
                break;
            case 1:
                this.ShowPopup(new GoToPagePopup("The API key seems to be be invalid. Please visit settings to renew your API key", "settings", nameof(Settings)));
                return;
            case 2:
                settings.APIKeyValidity = await settings.TestApiKey(Preferences.Get(ApiKeyPref, null));
                switch (settings.APIKeyValidity)
                {
                    case 0:
                        ApiKeyPrompt.IsVisible = false;
                        SearchPanel.IsVisible = true;
                        break;
                    case 1:
                        this.ShowPopup(new GoToPagePopup("The API key seems to be be invalid. Please visit settings to renew your API key", "settings", nameof(Settings)));
                        break;
                    case 2:
                        await DisplayAlert("Lost connection", "You have to be connected to the internet to use the search feature", "OK");
                        await Shell.Current.GoToAsync("///MainPage");
                        return;
                    case 3:
                        SearchPanel.IsVisible = false;
                        ApiKeyPrompt.IsVisible = true;
                        break;
                }
                break;
            case 3:
                SearchPanel.IsVisible = false;
                ApiKeyPrompt.IsVisible = true;
                break;
        }
    }

    private async void OnSaveApiKeyClicked(object sender, EventArgs e)
    {
        var newKey = ApiKeyEntry.Text?.Trim();
        if (string.IsNullOrWhiteSpace(newKey))
        {
            await DisplayAlert("Missing Key", "Please input your YouTube API key.", "OK");
            return;
        }

        settings.APIKeyValidity = await settings.TestApiKey(newKey);

        switch (settings.APIKeyValidity)
        {
            case 0:
                Preferences.Set(ApiKeyPref, newKey);
                ApiKeyPrompt.IsVisible = false;
                SearchPanel.IsVisible = true;
                break;
            case 1:
                await DisplayAlert("Invalid API key", "Your API key appears to be invalid. To use the search feature, please enter a valid API key", "OK");
                return;
            case 2:
                await DisplayAlert("Lost connection", "The API key cannot be checked and may be invalid.", "OK");

                Preferences.Set(ApiKeyPref, newKey);
                await DisplayAlert("Saved", "API key has been saved, however it may not work.", "OK");

                ApiKeyPrompt.IsVisible = false;
                SearchPanel.IsVisible = true;

                break;
            case 3:
                await DisplayAlert("Missing", "Please enter an API key.", "OK");
                SearchPanel.IsVisible = false;
                ApiKeyPrompt.IsVisible = true;
                break;
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

    private async Task<byte> TestApiKey(string apiKey)
    {
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
        catch(Java.Net.UnknownHostException)
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
        catch (Exception ex)
        {
            await DisplayAlert("Error", ex.Message, "OK");
            return 1;
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