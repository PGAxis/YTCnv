using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using AndroidX.Core.App;

namespace YTCnv
{
    [Service(Name = "com.pg_axis.downloadnotificationservice", Exported = true, ForegroundServiceType = ForegroundService.TypeDataSync)]
    public class DownloadNotificationService : Service
    {
        private const int NOTIFICATION_ID = 1001;
        private const string CHANNEL_ID = "download_channel";

        public override void OnCreate()
        {
            base.OnCreate();
            CreateNotificationChannel();
        }

        public override void OnDestroy()
        {
            base.OnDestroy();
            StopForeground(true);
        }

        public override StartCommandResult OnStartCommand(Intent intent, StartCommandFlags flags, int startId)
        {
            var notification = BuildNotification();
            StartForeground(NOTIFICATION_ID, notification);
            return StartCommandResult.Sticky;
        }

        public override IBinder OnBind(Intent intent)
        {
            return null;
        }

        private Notification BuildNotification()
        {
            var intent = new Intent(this, typeof(MainActivity));
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);

            var pendingIntent = PendingIntent.GetActivity(
                this,
                0,
                intent,
                PendingIntentFlags.Immutable
            );

            var builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .SetContentTitle("Downloading in background")
                .SetContentText("The download is running. Please keep the app open.")
                .SetSmallIcon(Resource.Drawable.icon)
                .SetColor(Android.Graphics.Color.Cyan)
                .SetContentIntent(pendingIntent)
                .SetOngoing(true)
                .SetPriority(NotificationCompat.PriorityLow)
                .SetVisibility(NotificationCompat.VisibilityPublic);

            return builder.Build();
        }

        private void CreateNotificationChannel()
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
            {
                var channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Background Service",
                    NotificationImportance.Low
                )
                {
                    Description = "Handles background downloading service"
                };

                var manager = (NotificationManager)GetSystemService(NotificationService);
                manager.CreateNotificationChannel(channel);
            }
        }
    }

}
