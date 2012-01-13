LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := ozfdecoder

LOCAL_CFLAGS := -DANDROID_NDK -DUNDER_ANDROID

LOCAL_SRC_FILES := \
    ozfdecoder.cpp

LOCAL_LDLIBS := -lz -ldl -llog

include $(BUILD_SHARED_LIBRARY)
