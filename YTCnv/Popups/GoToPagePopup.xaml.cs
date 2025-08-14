using CommunityToolkit.Maui.Views;

namespace YTCnv.Popups;

public partial class GoToPagePopup : Popup
{
	private readonly string _contentPage;
	private bool _buttonClicked = false;

	public GoToPagePopup(string message, string contentPageName, string contentPage)
	{
		InitializeComponent();
		this.Color = Colors.Transparent;
		MessageLabel.Text = message;
		MoverButton.Text = MoverButton.Text + contentPageName;
		_contentPage = contentPage;
	}

    private async void OnGoToPageClicked(object sender, EventArgs e)
    {
		_buttonClicked = true;
		Close();
		if (_contentPage != null)
			await Shell.Current.GoToAsync(_contentPage);
    }

    protected override Task OnDismissedByTappingOutsideOfPopup(CancellationToken token = default)
    {
		if (!_buttonClicked)
		{
			Task.Run(async () => await Shell.Current.GoToAsync("///MainPage"));
		}

        return base.OnDismissedByTappingOutsideOfPopup(token);
    }
}