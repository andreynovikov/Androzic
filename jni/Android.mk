LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ozfdecoder
LOCAL_SRC_FILES := ozfdecoder.cpp
LOCAL_LDLIBS := -lz -llog

include $(BUILD_SHARED_LIBRARY)
