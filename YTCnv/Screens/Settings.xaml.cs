namespace YTCnv.Screens;

public partial class Settings : ContentPage
{
    private const string ApiKeyPref = "YoutubeApiKey";
    private SettingsSave settings = SettingsSave.Instance();

    public Settings()
    {
        InitializeComponent();
        BindingContext = this;
        use4kSwitch.BindingContext = settings;
        LoadMaskedKey();
        VersionLabel.Text = $"{AppInfo.Current.VersionString} ({AppInfo.Current.BuildString})";
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        LoadMaskedKey();
    }

    private void LoadMaskedKey()
    {
        var key = Preferences.Get(ApiKeyPref, null);
        if (string.IsNullOrEmpty(key))
        {
            ApiKeyLabel.Text = "API Key: [ Not Set ]";
        }
        else
        {
            ApiKeyLabel.Text = $"API Key: {new string('*', key.Length)}";
        }
    }

    private async void OnUnlockClicked(object sender, EventArgs e)
    {
        bool confirm = await DisplayAlert("Reveal API key?", "Do you really want to view and edit your saved YouTube API key?", "Yes", "No");

        if (confirm)
        {
            var storedKey = Preferences.Get(ApiKeyPref, string.Empty);
            ApiKeyLabel.Text = $"API Key: {storedKey}";
            KeyEditorPanel.IsVisible = true;
            RevealKeyButton.IsVisible = false;
        }
    }

    private async void OnSaveApiKeyClicked(object sender, EventArgs e)
    {
        var newKey = ApiKeyEntry.Text?.Trim();

        if (string.IsNullOrEmpty(newKey))
        {
            await DisplayAlert("Missing", "Please enter an API key.", "OK");
            return;
        }

        bool valid = await TestApiKey(newKey);
        if (!valid)
        {
            await DisplayAlert("Invalid", "This API key is not valid.", "OK");
            return;
        }

        Preferences.Set(ApiKeyPref, newKey);
        await DisplayAlert("Saved", "API key has been updated successfully.", "OK");

        LoadMaskedKey();
        KeyEditorPanel.IsVisible = false;
        RevealKeyButton.IsVisible = true;
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

    private async void GoBack(object sender, EventArgs e)
    {
        await Shell.Current.GoToAsync("///MainPage");
    }

}