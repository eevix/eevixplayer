#define LOG_TAG "EevixMediaRender"
#include <jni.h>
#include <Log.h>
#include <unistd.h>
#include <semaphore.h>
#include "Thread.h"
#include "Queue.h"
#include "AndroidBundle.h"
#include "KeyedData.h"
#include "PltUPnP.h"
#include "PltMediaRenderer.h"

using eevix::Thread;
using eevix::Queue;
using eevix::AndroidBundle;
using eevix::KeyedData;

static void nativeInit(JNIEnv* env);
static void nativeSetup(JNIEnv* env, jobject JMediaRender);
static void onStateChanged(JNIEnv* env, jobject JMediaRender, jint state);

static const char *                 sDLNAServiceClassName = "com/eevix/DLNAMediaRender";
static PLT_DeviceHostReference      sMediaRender;
static PLT_UPnP                     sUPNPService;
static JavaVM *                     sJavaVM = NULL;
static jobject                      sJMediaRender = NULL;
static jmethodID                    sSetDataSource = NULL;
static jmethodID                    sIsPlaying = NULL;
static jmethodID                    sGetCurrentPosition = NULL;
static jmethodID                    sGetDuration = NULL;
static jmethodID                    sStop = NULL;
static jmethodID                    sStart = NULL;
static jmethodID                    sPause = NULL;
static jmethodID                    sSeek = NULL;

static struct PlayerState {
    int idle;
    int preparing;
    int paused;
    int playing;
} sPlayerState;

class MediaRenderer : public PLT_MediaRenderer
{
public:
    MediaRenderer(const char*  friendlyName,
                  bool         showIP = false,
                  const char*  uuid = NULL,
                  unsigned int port = 0,
                  bool         portRebind = false);
    ~MediaRenderer();
    void OnStateChanged(int state);

private:
    MediaRenderer();
    MediaRenderer(const MediaRenderer&);
    MediaRenderer& operator=(const MediaRenderer&);

    // AVTransport
    NPT_Result OnNext(PLT_ActionReference& action);
    NPT_Result OnPause(PLT_ActionReference& action);
    NPT_Result OnPlay(PLT_ActionReference& action);
    NPT_Result OnPrevious(PLT_ActionReference& action);
    NPT_Result OnSeek(PLT_ActionReference& action);
    NPT_Result OnStop(PLT_ActionReference& action);
    NPT_Result OnSetAVTransportURI(PLT_ActionReference& action);
    NPT_Result OnSetPlayMode(PLT_ActionReference& action);

    // RenderingControl
    NPT_Result OnSetVolume(PLT_ActionReference& action);
    NPT_Result OnSetVolumeDB(PLT_ActionReference &action);
    NPT_Result OnGetVolumeDBRange(PLT_ActionReference &action);
    NPT_Result OnSetMute(PLT_ActionReference& action);
    void OnStateChanged_l(int state);

private:
    class Looper : public Thread
    {
    public:
        explicit Looper(MediaRenderer* mediaRender);
        ~Looper();
    private:
        Looper();
        bool threadLoop();
        MediaRenderer* mMediaRender;
    };

    class Message : public KeyedData
    {
    public:
        enum Type
        {
            kSetAVTransportURI,
            kStop,
            kStart,
            kPause,
            kSeek,
            kOnStateChanged,
        };
        enum ParameterKeys
        {
            kUrl,
            kReply,
            kState,
            kSeekTarget,
        };
        explicit Message(Type type):
            mType(type)
        {
            sem_init(&mSemaphore, true, 0) ;
        }
        ~Message()
        {
            sem_destroy(&mSemaphore);
        }
        inline Type type()
        {
            return mType;
        }
        void waitReply()
        {
            sem_wait(&mSemaphore);
        }
        void postReply()
        {
            sem_post(&mSemaphore);
        }
    private:
        Message();
        Message(const Message&);
        Message& operator=(const Message&);
        Type mType;
        sem_t mSemaphore;
    };

    bool threadLoop();
    void onMessage(JNIEnv* env, std::shared_ptr<Message>& message);
    void update();
    bool isPlaying();
    bool setDataSource(const char* url);
    uint32_t getCurrentPosition();
    uint32_t getDuration();

private:
    NPT_Reference<PLT_Service> mAVTransportService;
    Thread*                    mThread;
    Queue<std::shared_ptr<Message> > mMessageQueue;
    int mPlayerState;
};

MediaRenderer::MediaRenderer(const char*    friendlyName,
                             bool           showIP,
                             const char*    uuid,
                             unsigned int   port,
                             bool           portRebind)
     :PLT_MediaRenderer(friendlyName, showIP, uuid, port, portRebind),
      mPlayerState(sPlayerState.idle)
{
    YLOGD("friendlyName:%s, showIP:%d, uuid:%s, port:%u, portRebind:%d", friendlyName, showIP, uuid, port, portRebind);
    mThread = new Looper(this);
    mThread->run("MediaRenderThread");
}

MediaRenderer::~MediaRenderer()
{
    YLOGD();
    mThread->requestExitAndWait();
    delete mThread;
}

void MediaRenderer::OnStateChanged(int state)
{
    std::shared_ptr<Message> message = std::make_shared<Message>(Message::kOnStateChanged);
    message->setInt32(Message::kState, state);
    mMessageQueue.push(message);
}

NPT_Result MediaRenderer::OnNext(PLT_ActionReference& action)
{
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPause(PLT_ActionReference& action)
{
    YLOGD("action:%s, counter:%d", action->GetActionDesc().GetName().GetChars(), action.GetCounter());
    mMessageQueue.push(std::make_shared<Message>(Message::kPause));
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPlay(PLT_ActionReference& action)
{
    YLOGD("action:%s, counter:%d", action->GetActionDesc().GetName().GetChars(), action.GetCounter());
    mMessageQueue.push(std::make_shared<Message>(Message::kStart));
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPrevious(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSeek(PLT_ActionReference& action)
{
    YLOGD("OnSeek");
    NPT_String unit;
    NPT_String target;
    action->GetArgumentValue("Unit", unit);
    action->GetArgumentValue("Target", target);
    YLOGD("unit:%s, target:%s", unit.GetChars(), target.GetChars());
    std::shared_ptr<Message> message = std::make_shared<Message>(Message::kSeek);
    uint32_t hour = 0;
    uint32_t minute = 0;
    uint32_t second = 0;
    sscanf(target.GetChars(), "%u:%u:%u", &hour, &minute, &second);
    message->setInt32(Message::kSeekTarget, hour * 60 * 60 * 1000 + minute * 60 * 1000 + second * 1000);
    mMessageQueue.push(message);
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnStop(PLT_ActionReference& action)
{
    YLOGD("action:%s, counter:%d", action->GetActionDesc().GetName().GetChars(), action.GetCounter());
    mMessageQueue.push(std::make_shared<Message>(Message::kStop));
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSetAVTransportURI(PLT_ActionReference& action)
{
    YLOGD("action:%s, counter:%d", action->GetActionDesc().GetName().GetChars(), action.GetCounter());
    PLT_MediaRenderer::OnSetAVTransportURI(action);
    std::shared_ptr<Message> message = std::make_shared<Message>(Message::kSetAVTransportURI);
    YLOGD("type:%d", message->type());
    NPT_String uri;
    bool ret = false;
    NPT_CHECK_WARNING(action->GetArgumentValue("CurrentURI", uri));
    message->setString(Message::kUrl, uri.GetChars(), uri.GetLength());
    mMessageQueue.push(message);
    message->waitReply();
    message->getBool(Message::kReply, ret);
    YLOGD("ret:%d", ret);
    if (ret)
    {
        PLT_Service* serviceAVT;
        NPT_CHECK_WARNING(FindServiceByType("urn:schemas-upnp-org:service:AVTransport:1", serviceAVT));
        NPT_String metaData;
        NPT_CHECK_WARNING(action->GetArgumentValue("CurrentURIMetaData", metaData));

        serviceAVT->SetStateVariable("NumberOfTracks", "1");
        serviceAVT->SetStateVariable("AVTransportURI", uri);
        serviceAVT->SetStateVariable("AVTransportURIMetadata", metaData);

        serviceAVT->SetStateVariable("CurrentTrack", "1");
        serviceAVT->SetStateVariable("CurrentTrackURI", uri);
        serviceAVT->SetStateVariable("CurrentTrackMetadata", metaData);

        serviceAVT->SetStateVariable("AbsoluteTimePosition", "NOT_IMPLEMENTED");
    }

    return ret ? NPT_SUCCESS : NPT_FAILURE;
}

NPT_Result MediaRenderer::OnSetPlayMode(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

// RenderingControl
NPT_Result MediaRenderer::OnSetVolume(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSetVolumeDB(PLT_ActionReference &action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnGetVolumeDBRange(PLT_ActionReference &action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSetMute(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

bool MediaRenderer::threadLoop()
{
    YLOGD();
    YFATAL_IF(sJavaVM == NULL);
    JNIEnv* jniEnv = NULL;

    sJavaVM->AttachCurrentThread(&jniEnv, NULL);

    while (!mThread->exitPending())
    {
        std::shared_ptr<Message> message;
        if (mMessageQueue.pop(message, 500))
        {
            YLOGD("message:%d", message->type());
            onMessage(jniEnv, message);
        }
        update();
    }

    if (jniEnv->ExceptionOccurred()) {
        jniEnv->ExceptionClear();
    }

    sJavaVM->DetachCurrentThread();
    return false;
}

void MediaRenderer::onMessage(JNIEnv* jniEnv, std::shared_ptr<Message>& message)
{
    switch (message->type())
    {
        case Message::kSetAVTransportURI:
        {
            YLOGD("SetAVTransportURI");
            std::string uri;
            YFATAL_IF(!message->getString(Message::kUrl, uri));
            YLOGD("%s", uri.c_str());
            jboolean ret = jniEnv->CallBooleanMethod(sJMediaRender, sSetDataSource, jniEnv->NewStringUTF(uri.c_str()));
            message->setBool(Message::kReply, ret);
            message->postReply();
            break;
        }
        case Message::kOnStateChanged:
        {
            YLOGD("kOnStateChanged");
            int state;
            YFATAL_IF(!message->getInt32(Message::kState, state));
            OnStateChanged_l(state);
            break;
        }
        case Message::kStop:
        {
            YLOGD("kStop");
            jniEnv->CallVoidMethod(sJMediaRender, sStop);
            break;
        }
        case Message::kStart:
        {
            YLOGD("kStart");
            jniEnv->CallVoidMethod(sJMediaRender, sStart);
            break;
        }
        case Message::kPause:
        {
            YLOGD("kPause");
            jniEnv->CallVoidMethod(sJMediaRender, sPause);
            break;
        }
        case Message::kSeek:
        {
            int32_t target = 0;
            message->getInt32(Message::kSeekTarget, target);
            YLOGD("kSeek, target:%d", target);
            jniEnv->CallVoidMethod(sJMediaRender, sSeek, target);
            break;
        }
        default:
        {
            break;
        }
    }
}

void MediaRenderer::update()
{
    if (isPlaying())
    {
        uint32_t position = getCurrentPosition();
        char * position1 = NULL;
        asprintf(&position1, "%02u:%02u:%02u", position / 1000 / 3600, position / 1000 / 60 % 60, position / 1000 % 60);
        YLOGD("position1:%s", position1);

        PLT_Service* serviceAVT;
        FindServiceByType("urn:schemas-upnp-org:service:AVTransport:1", serviceAVT);

        // GetPositionInfo
        serviceAVT->SetStateVariable("RelativeTimePosition", position1);

        if (position1 != NULL)
        {
            free(position1);
            position1 = NULL;
        }
    }
}

bool MediaRenderer::isPlaying()
{
    JNIEnv* jniEnv = NULL;
    sJavaVM->AttachCurrentThread(&jniEnv, NULL);
    return jniEnv->CallBooleanMethod(sJMediaRender, sIsPlaying);
}

uint32_t MediaRenderer::getCurrentPosition()
{
    JNIEnv* jniEnv = NULL;
    sJavaVM->AttachCurrentThread(&jniEnv, NULL);
    return jniEnv->CallIntMethod(sJMediaRender, sGetCurrentPosition);
}

uint32_t MediaRenderer::getDuration()
{
    JNIEnv* jniEnv = NULL;
    sJavaVM->AttachCurrentThread(&jniEnv, NULL);
    return jniEnv->CallIntMethod(sJMediaRender, sGetDuration);
}

void MediaRenderer::OnStateChanged_l(int state)
{
    YLOGD("state:%d", state);
    PLT_Service* serviceAVT;
    FindServiceByType("urn:schemas-upnp-org:service:AVTransport:1", serviceAVT);
    if (mPlayerState == state) {
        return;
    }

    if (state == sPlayerState.idle) {
        serviceAVT->SetStateVariable("TransportState", "NO_MEDIA_PRESENT");
        serviceAVT->SetStateVariable("NumberOfTracks", "0");
        serviceAVT->SetStateVariable("CurrentMediaDuration", "00:00:00");
        serviceAVT->SetStateVariable("AVTransportURI", "");
        serviceAVT->SetStateVariable("AVTransportURIMetadata", "");
        serviceAVT->SetStateVariable("CurrentTrack", "0");
        serviceAVT->SetStateVariable("CurrentTrackDuration", "00:00:00");
        serviceAVT->SetStateVariable("CurrentTrackMetadata", "");
        serviceAVT->SetStateVariable("CurrentTrackURI", "");
        serviceAVT->SetStateVariable("RelativeTimePosition", "00:00:00");
        serviceAVT->SetStateVariable("AbsoluteTimePosition", "00:00:00");
        serviceAVT->SetStateVariable("RelativeCounterPosition", "-1"); // means NOT_IMPLEMENTED
        serviceAVT->SetStateVariable("AbsoluteCounterPosition", "-1"); // means NOT_IMPLEMENTED
        serviceAVT->SetStateVariable("TransportState", "NO_MEDIA_PRESENT");
        serviceAVT->SetStateVariable("TransportStatus", "OK");
        serviceAVT->SetStateVariable("TransportPlaySpeed", "1");
    } else if (state == sPlayerState.paused) {
        serviceAVT->SetStateVariable("TransportState", "PAUSED_PLAYBACK");
    } else if (state == sPlayerState.playing) {
        uint32_t duration = getDuration();
        char * duration1 = NULL;
        asprintf(&duration1, "%02u:%02u:%02u", duration / 1000 / 3600, duration / 1000 / 60 % 60, duration / 1000 % 60);
        YLOGD("duration1:%s", duration1);

        // GetMediaInfo
        serviceAVT->SetStateVariable("CurrentMediaDuration", duration1);

        // GetPositionInfo
        serviceAVT->SetStateVariable("CurrentTrackDuration", duration1);

        serviceAVT->SetStateVariable("TransportState", "PLAYING");
        serviceAVT->SetStateVariable("TransportStatus", "OK");
        serviceAVT->SetStateVariable("TransportPlaySpeed", "1");

        if (duration1 != NULL)
        {
            free(duration1);
            duration1 = NULL;
        }
    } else if (state == sPlayerState.preparing) {
        serviceAVT->SetStateVariable("TransportState", "TRANSITIONING");
    } else {
        YFATAL_IF(!"error state");
    }

    mPlayerState = state;
}

MediaRenderer::Looper::Looper(MediaRenderer* mediaRenderer)
    :mMediaRender(mediaRenderer)
{
    YLOGD("mediaRenderer:%p", mediaRenderer);
}

MediaRenderer::Looper::~Looper()
{
    YLOGD("destructor");
}

bool MediaRenderer::Looper::threadLoop()
{
    YLOGD();

    if (mMediaRender)
    {
        return mMediaRender->threadLoop();
    }

    return false;
}

#ifdef __cplusplus
extern "C"
{
#endif

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    YLOGD("vm:%p", vm);
    JNIEnv* env = NULL;
    sJavaVM = vm;

    const JNINativeMethod nativeMethods[] =
    {
            {"nativeInit",     "()V",                             (void*)nativeInit},
            {"nativeSetup",    "(Lcom/eevix/DLNAMediaRender;)V",  (void*)nativeSetup},
            {"onStateChanged", "(I)V",                            (void*)onStateChanged},
    };

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK)
    {
        YLOGE("ERROR: GetEnv failed");
        return -1;
    }

    jclass DLNAServiceClass = env->FindClass(sDLNAServiceClassName);
    YFATAL_IF(DLNAServiceClass == NULL);

    if (0 > env->RegisterNatives(DLNAServiceClass, nativeMethods, sizeof(nativeMethods) / sizeof(nativeMethods[0])))
    {
        return -1;
    }

    return JNI_VERSION_1_4;
}

static void nativeInit(JNIEnv* env)
{
    YLOGD("env:%p", env);

    if (!sUPNPService.IsRunning())
    {
        /* configure NPT logging */
#if 0 // enable log
        NPT_String logConfig("plist:.level=ALL");
        NPT_Result ret = NPT_GetSystemLogConfig(logConfig);
        if (ret != NPT_SUCCESS)
        {
            PLOGE("configure NPT log failed");
        }

        NPT_Logger* logger = NPT_LogManager::GetLogger("mylogger");
        NPT_LogManager& loggerManager = NPT_LogManager::GetDefault();
        logger->AddHandler(&loggerHandle);
        PLOGD("log level:%d IsEnabled:%d", logger->GetLevel(), loggerManager.IsEnabled());
#endif
        /* start media render */
        YFATAL_IF(!sMediaRender.IsNull());
        sMediaRender = new MediaRenderer("eevix-media-render", false, "a6572b54-f3c7-2d91-2fb5-b757f2537e22");
        sUPNPService.AddDevice(sMediaRender);
        sUPNPService.Start();
        YLOGD("UPNP is running");
    }
}

static void nativeSetup(JNIEnv* env, jobject mediaRender)
{
    YLOGD("env:%p", env);
    sJMediaRender = env->NewGlobalRef(mediaRender);
    YFATAL_IF(sJMediaRender == NULL);

    sSetDataSource = env->GetMethodID(env->GetObjectClass(mediaRender), "setDataSource", "(Ljava/lang/String;)Z");
    YFATAL_IF(sSetDataSource == NULL);

    sIsPlaying = env->GetMethodID(env->GetObjectClass(mediaRender), "isPlaying", "()Z");
    YFATAL_IF(sIsPlaying == NULL);

    sGetCurrentPosition = env->GetMethodID(env->GetObjectClass(mediaRender), "getCurrentPosition", "()I");
    YFATAL_IF(sGetCurrentPosition == NULL);

    sGetDuration = env->GetMethodID(env->GetObjectClass(mediaRender), "getDuration", "()I");
    YFATAL_IF(sGetDuration == NULL);

    sStop = env->GetMethodID(env->GetObjectClass(mediaRender), "stop", "()V");
    YFATAL_IF(sStop == NULL);

    sStart = env->GetMethodID(env->GetObjectClass(mediaRender), "start", "()V");
    YFATAL_IF(sStop == NULL);

    sPause = env->GetMethodID(env->GetObjectClass(mediaRender), "pause", "()V");
    YFATAL_IF(sPause == NULL);

    sSeek = env->GetMethodID(env->GetObjectClass(mediaRender), "seek", "(I)V");
    YFATAL_IF(sSeek == NULL);

    jfieldID fieldId = env->GetStaticFieldID(env->GetObjectClass(mediaRender), "STATE_IDLE", "I");
    sPlayerState.idle = env->GetStaticIntField(env->GetObjectClass(mediaRender), fieldId);

    fieldId = env->GetStaticFieldID(env->GetObjectClass(mediaRender), "STATE_PREPARING", "I");
    sPlayerState.preparing = env->GetStaticIntField(env->GetObjectClass(mediaRender), fieldId);

    fieldId = env->GetStaticFieldID(env->GetObjectClass(mediaRender), "STATE_PAUSED", "I");
    sPlayerState.paused = env->GetStaticIntField(env->GetObjectClass(mediaRender), fieldId);

    fieldId = env->GetStaticFieldID(env->GetObjectClass(mediaRender), "STATE_PLAYING", "I");
    sPlayerState.playing = env->GetStaticIntField(env->GetObjectClass(mediaRender), fieldId);
}

static void onStateChanged(JNIEnv* env, jobject jMediaRender, jint state)
{
    YLOGD("state:%d", state);
    ((MediaRenderer*)(sMediaRender.AsPointer()))->OnStateChanged(state);
}

#ifdef __cplusplus
} // extern "C"
#endif