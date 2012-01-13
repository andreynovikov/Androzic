package com.androzic.location;

import com.androzic.location.ILocationCallback;

interface ILocationRemoteService
{
    void registerCallback(ILocationCallback cb);
    void unregisterCallback(ILocationCallback cb);
}
