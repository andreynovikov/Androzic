package com.androzic.track;

import com.androzic.track.ITrackingCallback;

interface ITrackingRemoteService
{
    void registerCallback(ITrackingCallback cb);
    void unregisterCallback(ITrackingCallback cb);
}
