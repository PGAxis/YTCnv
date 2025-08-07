using System;
using Foundation;
using ObjCRuntime;
using CoreFoundation;

namespace Com.Arthenica.Ffmpegkit
{
    // Delegate types for callbacks used in FFmpegKit
    // These are typedefs in the original, here we define delegates accordingly.

    delegate void FFmpegSessionCompleteCallback(FFmpegSession session);
    delegate void LogCallback(IntPtr log); // You can define this more precisely if you want
    delegate void StatisticsCallback(IntPtr stats); // Same here

    [BaseType(typeof(NSObject))]
    interface FFmpegKit
    {
        // Sync execution with arguments
        [Static]
        [Export("executeWithArguments:")]
        FFmpegSession ExecuteWithArguments(NSArray arguments);

        // Async execution with arguments and complete callback
        [Static]
        [Export("executeWithArgumentsAsync:withCompleteCallback:")]
        FFmpegSession ExecuteWithArgumentsAsync(NSArray arguments, FFmpegSessionCompleteCallback completeCallback);

        // Async execution with arguments, complete, log and statistics callbacks
        [Static]
        [Export("executeWithArgumentsAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:")]
        FFmpegSession ExecuteWithArgumentsAsync(NSArray arguments, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback);

        // Async execution with arguments, complete callback and dispatch queue
        [Static]
        [Export("executeWithArgumentsAsync:withCompleteCallback:onDispatchQueue:")]
        FFmpegSession ExecuteWithArgumentsAsync(NSArray arguments, FFmpegSessionCompleteCallback completeCallback, DispatchQueue queue);

        // Async execution with arguments, complete, log, statistics callbacks, and dispatch queue
        [Static]
        [Export("executeWithArgumentsAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:onDispatchQueue:")]
        FFmpegSession ExecuteWithArgumentsAsync(NSArray arguments, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback, DispatchQueue queue);

        // Sync execution with command string
        [Static]
        [Export("execute:")]
        FFmpegSession Execute(string command);

        // Async execution with command string and complete callback
        [Static]
        [Export("executeAsync:withCompleteCallback:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback);

        // Async execution with command string, complete, log and statistics callbacks
        [Static]
        [Export("executeAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback);

        // Async execution with command string, complete callback and dispatch queue
        [Static]
        [Export("executeAsync:withCompleteCallback:onDispatchQueue:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback, DispatchQueue queue);

        // Async execution with command string, complete, log, statistics callbacks, and dispatch queue
        [Static]
        [Export("executeAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:onDispatchQueue:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback, DispatchQueue queue);

        // Cancel all sessions
        [Static]
        [Export("cancel")]
        void Cancel();

        // Cancel session by id
        [Static]
        [Export("cancel:")]
        void Cancel(long sessionId);

        // List sessions
        [Static]
        [Export("listSessions")]
        NSArray ListSessions();
    }
}
