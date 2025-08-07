#if IOS
using FFmpegKitIOSBinding;

namespace YTCnv
{
    public static class FFmpegInteropIOS
    {
        private static FFmpegSession _activeSession;

        public static async Task<bool> RunFFmpegCommand(string command)
        {
            var tcs = new TaskCompletionSource<FFmpegSession>();
            var callback = new OwnFFmpegSessionCompleteCallback(tcs);

            try
            {
                _activeSession = FFmpegKit.ExecuteAsync(command, callback);

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

        public static void CancelFFmpegCommand()
        {
            if (_activeSession != null && _activeSession.State == SessionState.Running)
            {
                FFmpegKit.Cancel(_activeSession.SessionId);
                Console.WriteLine("FFmpeg session cancelled.");
            }
        }
    }

    public class OwnFFmpegSessionCompleteCallback : FFmpegSessionCompleteCallback
    {
        private readonly TaskCompletionSource<FFmpegSession> _tcs;

        public OwnFFmpegSessionCompleteCallback(TaskCompletionSource<FFmpegSession> tcs)
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
