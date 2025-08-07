using ObjCRuntime;

namespace FFmpegKitIOSBinding
{
    [Native]
    public enum LogRedirectionStrategy : long
    {
        None = 0,
        Redirect = 1,
        RedirectWithLogFile = 2
    }

    // ReturnCodeEnum for clarity
    [Native]
    public enum ReturnCodeEnum : ulong
    {
        Success = 0,
        Cancel = 255
    }

    [Native]
    public enum SessionState : long
    {
        Created,
        Running,
        Failed,
        Completed
    }
}

