#ifndef ANDROID_BUNDLE_H
#define ANDROID_BUNDLE_H

#include <jni.h>
#include "Mutex.h"

namespace yoghurt
{

class AndroidBundle
{
public:
    explicit AndroidBundle(JNIEnv* env);
    ~AndroidBundle();
    void putInt(JNIEnv* env, const char* name, int32_t value);
    void putLong(JNIEnv* env, const char* name, int64_t value);
    void putFloat(JNIEnv* env, const char* name, float value);
    void putString(JNIEnv* env, const char* name, const char* value);
    jobject getObject();
private:
    AndroidBundle();
    AndroidBundle(const AndroidBundle&);
    AndroidBundle& operator=(const AndroidBundle&);
    static void jniInit(JNIEnv* env);
    static const char* const mClassName;
    static jclass mClass;
    static jmethodID mConstruct;
    static jmethodID mPutInt;
    static jmethodID mPutLong;
    static jmethodID mPutFloat;
    static jmethodID mPutString;
    static Mutex mJniLock;
    jobject mObject;
};

}
#endif //ANDROID_BUNDLE_H
