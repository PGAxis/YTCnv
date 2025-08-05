using Android;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using AndroidX.Core.App;
using System.Threading.Tasks;

namespace YTCnv
{
    [Activity(Theme = "@style/Maui.SplashTheme", MainLauncher = true, LaunchMode = LaunchMode.SingleTop, ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
    public class MainActivity : MauiAppCompatActivity
    {
        protected override async void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            // Change the status bar color to transparent
            if (Build.VERSION.SdkInt >= BuildVersionCodes.Lollipop)
            {
                Window.SetStatusBarColor(Android.Graphics.Color.Transparent);
            }

            await RequestNotificationPermission();
        }

        public async Task RequestNotificationPermission()
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.Tiramisu)
            {
                var permissionsToRequest = new List<string>();

                if (CheckSelfPermission(Manifest.Permission.PostNotifications) != Permission.Granted)
                    permissionsToRequest.Add(Manifest.Permission.PostNotifications);

                if (permissionsToRequest.Count > 0)
                {
                    ActivityCompat.RequestPermissions(this, permissionsToRequest.ToArray(), 101);
                }
            }
        }
    }
}
