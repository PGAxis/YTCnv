using System;
using Foundation;
using ObjCRuntime;

namespace FFmpegKitIOSBinding
{
    // ReturnCode class binding
    [BaseType(typeof(NSObject))]
    interface ReturnCode
    {
        [Export("init:")]
        IntPtr Constructor(int value);

        [Static]
        [Export("isSuccess:")]
        bool IsSuccess(ReturnCode value);

        [Static]
        [Export("isCancel:")]
        bool IsCancel(ReturnCode value);

        [Export("getValue")]
        int GetValue();

        [Export("isValueSuccess")]
        bool IsValueSuccess();

        [Export("isValueError")]
        bool IsValueError();

        [Export("isValueCancel")]
        bool IsValueCancel();
    }

    // FFmpegKit main interface
    [BaseType(typeof(NSObject))]
    interface FFmpegKit
    {
        [Static]
        [Export("execute:")]
        FFmpegSession Execute(string command);

        [Static]
        [Export("executeAsync:withCompleteCallback:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback);

        [Static]
        [Export("executeAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback);

        [Static]
        [Export("executeAsync:withCompleteCallback:withLogCallback:withStatisticsCallback:onDispatchQueue:")]
        FFmpegSession ExecuteAsync(string command, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback, IntPtr queue);

        [Static]
        [Export("cancel:")]
        void Cancel(long sessionId);

        [Static]
        [Export("listSessions")]
        FFmpegSession[] ListSessions();
    }

    // FFmpegSession interface
    [BaseType(typeof(NSObject))]
    interface FFmpegSession
    {
        [Export("getReturnCode")]
        ReturnCode ReturnCode { get; }

        [Export("getFailStackTrace")]
        string FailStackTrace { get; }

        [Export("getSessionId")]
        long SessionId { get; }

        [Export("getState")]
        SessionState State { get; }
        
        [Static]
        [Export("create:")]
        FFmpegSession Create(NSArray arguments);

        [Static]
        [Export("create:withCompleteCallback:")]
        FFmpegSession Create(NSArray arguments, FFmpegSessionCompleteCallback completeCallback);

        [Static]
        [Export("create:withCompleteCallback:withLogCallback:withStatisticsCallback:")]
        FFmpegSession Create(NSArray arguments, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback);

        [Static]
        [Export("create:withCompleteCallback:withLogCallback:withStatisticsCallback:withLogRedirectionStrategy:")]
        FFmpegSession Create(NSArray arguments, FFmpegSessionCompleteCallback completeCallback, LogCallback logCallback, StatisticsCallback statisticsCallback, LogRedirectionStrategy logRedirectionStrategy);

        [Export("getStatisticsCallback")]
        StatisticsCallback GetStatisticsCallback();

        [Export("getCompleteCallback")]
        FFmpegSessionCompleteCallback GetCompleteCallback();

        [Export("getAllStatisticsWithTimeout:")]
        NSArray GetAllStatisticsWithTimeout(int waitTimeout);

        [Export("getAllStatistics")]
        NSArray GetAllStatistics();

        [Export("getStatistics")]
        NSArray GetStatistics();

        [Export("getLastReceivedStatistics")]
        Statistics GetLastReceivedStatistics();

        [Export("addStatistics:")]
        void AddStatistics(Statistics statistics);
    }

    // AbstractSession interface
    [BaseType(typeof(NSObject))]
    interface AbstractSession : ISession
    {
        [Export("init:withLogCallback:withLogRedirectionStrategy:")]
        IntPtr Constructor(NSArray arguments, LogCallback logCallback, LogRedirectionStrategy logRedirectionStrategy);

        [Export("waitForAsynchronousMessagesInTransmit:")]
        void WaitForAsynchronousMessagesInTransmit(int timeout);
    }

    // FFmpegSessionCompleteCallback interface (delegate)
    [BaseType(typeof(NSObject))]
    interface FFmpegSessionCompleteCallback
    {
        [Export("apply:")]
        void Apply(FFmpegSession session);
    }

    // LogCallback interface
    [BaseType(typeof(NSObject))]
    interface LogCallback
    {
        [Export("apply:")]
        void Apply(Log log);
    }

    // StatisticsCallback interface
    [BaseType(typeof(NSObject))]
    interface StatisticsCallback
    {
        [Export("apply:")]
        void Apply(Statistics statistics);
    }

    // Log interface
    [BaseType(typeof(NSObject))]
    interface Log
    {
        [Export("getLevel")]
        int Level { get; }

        [Export("getMessage")]
        string Message { get; }

        [Export("getTime")]
        double Time { get; }
    }

    // Statistics interface
    [BaseType(typeof(NSObject))]
    interface Statistics
    {
        [Export("getVideoFrameNumber")]
        int VideoFrameNumber { get; }

        [Export("getAudioFrameNumber")]
        int AudioFrameNumber { get; }

        [Export("getVideoBitrate")]
        int VideoBitrate { get; }

        [Export("getAudioBitrate")]
        int AudioBitrate { get; }

        [Export("getStartTime")]
        double StartTime { get; }

        [Export("getEndTime")]
        double EndTime { get; }

        [Export("getDuration")]
        double Duration { get; }
    }

    // ISession protocol
    [Protocol]
    interface ISession
    {
        [Export("getSessionId")]
        long SessionId { get; }

        [Export("getCommand")]
        string Command { get; }

        [Export("getArguments")]
        NSArray Arguments { get; }

        [Export("getState")]
        int State { get; }

        [Export("getReturnCode")]
        ReturnCode ReturnCode { get; }

        [Export("getStartTime")]
        double StartTime { get; }

        [Export("getEndTime")]
        double EndTime { get; }

        [Export("getDuration")]
        double Duration { get; }

        [Export("getFailStackTrace")]
        string FailStackTrace { get; }
    }
}