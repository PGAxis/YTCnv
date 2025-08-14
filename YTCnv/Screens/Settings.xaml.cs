using System.Threading.Tasks;

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
        enableQuickDwnld.BindingContext = settings;
        //LoadMaskedKey();
        VersionLabel.Text = $"{AppInfo.Current.VersionString} ({AppInfo.Current.BuildString})";
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        LoadMaskedKey();
    }

    private async Task LoadMaskedKey()
    {
        settings.APIKeyValidity = settings.APIKeyValidity;
        string apiKey = Preferences.Get(ApiKeyPref, null);

        if (apiKey != null)
            ApiKeyLabel.Text = $"API Key: {new string('*', apiKey.Length)}";
        else
            ApiKeyLabel.Text = "API Key: [ Not Set ]";

        switch (settings.APIKeyValidity)
        {
            case 0:
                ApiKeyLabel.Text = $"API Key: {new string('*', apiKey.Length)}";
                break;
            case 1:
                await DisplayAlert("Invalid API key", "Your API key appears to be invalid. To use the search feature, please enter a valid API key", "OK");
                break;
            case 2:
                settings.APIKeyValidity = await settings.TestApiKey(apiKey);
                switch (settings.APIKeyValidity)
                {
                    case 0:
                        ApiKeyLabel.Text = $"API Key: {new string('*', apiKey.Length)}";
                        break;
                    case 1:
                        await DisplayAlert("Invalid API key", "Your API key appears to be invalid. To use the search feature, please enter a valid API key", "OK");
                        break;
                    case 2:
                        bool show = await DisplayAlert("Network Error", "Your API key can not be checked. Please connect to the internet and try again.\nClicking OK will show the API key.", "OK", "CANCEl");
                        if (show)
                        {
                            ApiKeyLabel.Text = $"API Key: {apiKey}";
                            KeyEditorPanel.IsVisible = true;
                            RevealKeyButton.IsVisible = false;
                        }
                        else
                            ApiKeyLabel.Text = $"API Key: {new string('*', apiKey.Length)}";
                        break;
                    case 3:
                        ApiKeyLabel.Text = "API Key: [ Not Set ]";
                        break;
                }
                break;
            case 3:
                ApiKeyLabel.Text = "API Key: [ Not Set ]";
                break;
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
            bool delete = await DisplayAlert("Missing", "Do you wish to remove your API key from this app?", "OK", "CANCEL");
            if (delete)
            {
                Preferences.Set(ApiKeyPref, string.Empty);

                LoadMaskedKey();
                KeyEditorPanel.IsVisible = false;
                RevealKeyButton.IsVisible = true;
            }
            else
            {
                LoadMaskedKey();
                KeyEditorPanel.IsVisible = false;
                RevealKeyButton.IsVisible = true;
            }
            return;
        }

        byte validity = await settings.TestApiKey(newKey);

        switch (validity)
        {
            case 0:
                Preferences.Set(ApiKeyPref, newKey);
                await DisplayAlert("Saved", "API key has been updated successfully.", "OK");

                LoadMaskedKey();
                KeyEditorPanel.IsVisible = false;
                RevealKeyButton.IsVisible = true;
                break;
            case 1:
                await DisplayAlert("Invalid API key", "Your API key appears to be invalid. To use the search feature, please enter a valid API key", "OK");
                return;
            case 2:
                await DisplayAlert("Lost connection", "The API key cannot be checked and may be invalid.", "OK");

                Preferences.Set(ApiKeyPref, newKey);
                await DisplayAlert("Saved", "API key has been saved, however it may not work.", "OK");

                LoadMaskedKey();
                KeyEditorPanel.IsVisible = false;
                RevealKeyButton.IsVisible = true;
                break;
            case 3:
                await DisplayAlert("Missing", "Please enter an API key.", "OK");
                break;
        }
    }

    private async void GoBack(object sender, EventArgs e)
    {
        await Shell.Current.GoToAsync("///MainPage");
    }

}