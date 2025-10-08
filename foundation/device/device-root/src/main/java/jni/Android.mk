LOCAL_PATH := $(call my-dir)

# Add debugging
APP_OPTIM := debug

#################################
# Library
include $(CLEAR_VARS)
LOCAL_MODULE := toolfile
LOCAL_SRC_FILES := toolFile.cpp
LOCAL_CFLAGS := -O2 -ffunction-sections -fdata-sections -fvisibility=hidden -fstack-protector-all
LOCAL_CPPFLAGS := -O2 -ffunction-sections -fdata-sections -fvisibility=hidden
LOCAL_LDFLAGS := -Wl,--gc-sections,--strip-all -Wl,-z,max-page-size=65536
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)