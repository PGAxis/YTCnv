#if ANDROID
using Com.Arthenica.Ffmpegkit;
using Com.Arthenica.Smartexception;

namespace YTCnv
{
    public static class FFmpegInterop
    {
        public static async Task<bool> RunFFmpegCommand(string command)
        {
            var tcs = new TaskCompletionSource<FFmpegSession>();
            var callback = new FFmpegSessionCompleteCallback(tcs);

            try
            {
                FFmpegKit.ExecuteAsync(command, callback);

                var session = await tcs.Task;
                var returnCode = session.ReturnCode;

                if (ReturnCode.IsSuccess(returnCode))
                {
                    Console.WriteLine("FFmpeg command executed successfully.");
                    return true;
                }
                else
                {
                    Console.WriteLine($"FFmpeg failed with code: {returnCode} and message: {session.FailStackTrace}");
                    return false;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Exception while running FFmpeg: {ex.Message}");
                return false;
            }
        }
    }

    public class FFmpegSessionCompleteCallback : Java.Lang.Object, IFFmpegSessionCompleteCallback
    {
        private readonly TaskCompletionSource<FFmpegSession> _tcs;

        public FFmpegSessionCompleteCallback(TaskCompletionSource<FFmpegSession> tcs)
        {
            _tcs = tcs;
        }

        public void Apply(FFmpegSession session)
        {
            Console.WriteLine("Apply() called");
            _tcs.TrySetResult(session);
        }
    }
}
#endif
