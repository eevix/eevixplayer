#define LOG_TAG "JBundle"
#include "AndroidBundle.h"
#include "Log.h"

namespace yoghurt
{

const char* const AndroidBundle::mClassName = "android/os/Bundle";
jclass    AndroidBundle::mClass     = NULL;
jmethodID AndroidBundle::mConstruct = NULL;
jmethodID AndroidBundle::mPutInt    = NULL;
jmethodID AndroidBundle::mPutLong   = NULL;
jmethodID AndroidBundle::mPutFloat  = NULL;
jmethodID AndroidBundle::mPutString = NULL;
Mutex     AndroidBundle::mJniLock;

AndroidBundle::AndroidBundle(JNIEnv* env)
{
    YLOGD("constructor env:%p", env);
    YFATAL_IF(env == NULL);
    jniInit(env);
    mObject = env->NewObject(mClass, mConstruct);
    YFATAL_IF(mObject == NULL);
    YLOGD("construct OK");
}

AndroidBundle::~AndroidBundle()
{
    YLOGD("destructor");
}

void AndroidBundle::putInt(JNIEnv* env, const char* name, int32_t value)
{
    YFATAL_IF(env == NULL || name == NULL);
    env->CallVoidMethod(mObject, mPutInt, env->NewStringUTF(name), value);
}

void AndroidBundle::putLong(JNIEnv* env, const char* name, int64_t value)
{
    YFATAL_IF(env == NULL || name == NULL);
    env->CallVoidMethod(mObject, mPutLong, env->NewStringUTF(name), value);
}

void AndroidBundle::putFloat(JNIEnv* env, const char* name, float value)
{
    YFATAL_IF(env == NULL || name == NULL);
    env->CallVoidMethod(mObject, mPutFloat, env->NewStringUTF(name), value);
}

void AndroidBundle::putString(JNIEnv* env, const char* name, const char* value)
{
    YFATAL_IF(env == NULL || name == NULL || value == NULL);
    env->CallVoidMethod(mObject, mPutString, env->NewStringUTF(name), env->NewStringUTF(value));
}

jobject AndroidBundle::getObject()
{
    return mObject;
}

void AndroidBundle::jniInit(JNIEnv* env)
{
    if (mClass != NULL)
    {
        return;
    }

    AutoMutex lock(mJniLock);

    if (mClass != NULL)
    {
        return;
    }

    YLOGD("env:%p", env);
    YFATAL_IF(env == NULL);

    jclass bundle = env->FindClass(mClassName);
    YFATAL_IF(bundle == NULL);

    //bundle = (jclass)env->NewGlobalRef(bundle);
    YFATAL_IF(bundle == NULL);

    mConstruct = env->GetMethodID(bundle, "<init>", "()V");
    YFATAL_IF(mConstruct == NULL);

    mPutInt = env->GetMethodID(bundle, "putInt", "(Ljava/lang/String;I)V");
    YFATAL_IF(mPutInt == NULL);

    mPutLong = env->GetMethodID(bundle, "putLong", "(Ljava/lang/String;J)V");
    YFATAL_IF(mPutLong == NULL);

    mPutFloat = env->GetMethodID(bundle, "putFloat", "(Ljava/lang/String;F)V");
    YFATAL_IF(mPutFloat == NULL);

    mPutString = env->GetMethodID(bundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V");
    YFATAL_IF(mPutString == NULL);

    mClass = bundle;
}

}