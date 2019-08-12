#define LOG_TAG "EevixMediaRender"
#include <jni.h>
#include <Log.h>
#include <unistd.h>
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

static const char *                 gDLNAServiceClassName = "com/eevix/DLNAMediaRender";
static PLT_DeviceHostReference      gMediaRender;
static PLT_UPnP                     gUPNPService;
static JavaVM *                     gJavaVM = NULL;
static jobject                      gJMediaRender = NULL;
static jmethodID                    gJOnAction = NULL;
static struct JActions
{
    int32_t INVALID     = -1;
    int32_t SET_URL     = INVALID;
    int32_t PLAY        = INVALID;
    int32_t PAUSE       = INVALID;
    int32_t RESUME      = INVALID;
    int32_t STOP        = INVALID;
} gJActions;

class MediaRenderer : public PLT_MediaRenderer
{
public:
    MediaRenderer(const char*  friendlyName,
                  bool         showIP = false,
                  const char*  uuid = NULL,
                  unsigned int port = 0,
                  bool         portRebind = false);
    ~MediaRenderer();
private:
    MediaRenderer();
    MediaRenderer(const MediaRenderer&);
    MediaRenderer& operator=(const MediaRenderer&);
    NPT_Result SetupServices();
    NPT_Result OnGetCurrentConnectionInfo(PLT_ActionReference& action);

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
        };
        enum ParameterKeys
        {
            kUrl,
        };
        explicit Message(Type type):
            mType(type)
        {
            YLOGD("constructor:%p", this);
        }
        ~Message()
        {
            YLOGD("destructor:%p", this);
        }
        inline Type type()
        {
            return mType;
        }
    private:
        Message();
        Message(const Message&);
        Message& operator=(const Message&);
        Type mType;
    };

    bool threadLoop();
    void onMessage(JNIEnv* env, std::shared_ptr<Message>& message);
    void sendAction(JNIEnv* env, AndroidBundle& action);

private:
    NPT_Reference<PLT_Service> mAVTransportService;
    Thread*                    mThread;
    Queue<std::shared_ptr<Message> > mMessageQueue;
};

MediaRenderer::MediaRenderer(const char*    friendlyName,
                             bool           showIP,
                             const char*    uuid,
                             unsigned int   port,
                             bool           portRebind)
     :PLT_MediaRenderer(friendlyName, showIP, uuid, port, portRebind)
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

NPT_Result MediaRenderer::SetupServices()
{
    YLOGD();
    extern NPT_UInt8 RDR_ConnectionManagerSCPD[];
    extern NPT_UInt8 RDR_AVTransportSCPD[];
    extern NPT_UInt8 RDR_RenderingControlSCPD[];
    NPT_Reference<PLT_Service> service;

    {
        /* AVTransport */
        mAVTransportService = new PLT_Service(
            this,
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:upnp-org:serviceId:AVTransport",
            "AVTransport",
            "urn:schemas-upnp-org:metadata-1-0/AVT/");
        NPT_CHECK_FATAL(mAVTransportService->SetSCPDXML((const char*) RDR_AVTransportSCPD));
        NPT_CHECK_FATAL(AddService(mAVTransportService.AsPointer()));

        mAVTransportService->SetStateVariableRate("LastChange", NPT_TimeInterval(0.2f));
        mAVTransportService->SetStateVariable("A_ARG_TYPE_InstanceID", "0");

        // GetCurrentTransportActions
        mAVTransportService->SetStateVariable("CurrentTransportActions", "Play,Pause,Stop,Seek,Next,Previous");

        // GetDeviceCapabilities
        mAVTransportService->SetStateVariable("PossiblePlaybackStorageMedia", "NONE,NETWORK,HDD,CD-DA,UNKNOWN");
        mAVTransportService->SetStateVariable("PossibleRecordStorageMedia", "NOT_IMPLEMENTED");
        mAVTransportService->SetStateVariable("PossibleRecordQualityModes", "NOT_IMPLEMENTED");

        // GetMediaInfo
        mAVTransportService->SetStateVariable("NumberOfTracks", "0");
        mAVTransportService->SetStateVariable("CurrentMediaDuration", "00:00:00");
        mAVTransportService->SetStateVariable("AVTransportURI", "");
        mAVTransportService->SetStateVariable("AVTransportURIMetadata", "");
        mAVTransportService->SetStateVariable("NextAVTransportURI", "NOT_IMPLEMENTED");
        mAVTransportService->SetStateVariable("NextAVTransportURIMetadata", "NOT_IMPLEMENTED");
        mAVTransportService->SetStateVariable("PlaybackStorageMedium", "NONE");
        mAVTransportService->SetStateVariable("RecordStorageMedium", "NOT_IMPLEMENTED");
        mAVTransportService->SetStateVariable("RecordMediumWriteStatus", "NOT_IMPLEMENTED");

        // GetPositionInfo
        mAVTransportService->SetStateVariable("CurrentTrack", "0");
        mAVTransportService->SetStateVariable("CurrentTrackDuration", "00:00:00");
        mAVTransportService->SetStateVariable("CurrentTrackMetadata", "");
        mAVTransportService->SetStateVariable("CurrentTrackURI", "");
        mAVTransportService->SetStateVariable("RelativeTimePosition", "00:00:00");
        mAVTransportService->SetStateVariable("AbsoluteTimePosition", "00:00:00");
        mAVTransportService->SetStateVariable("RelativeCounterPosition", "2147483647"); // means NOT_IMPLEMENTED
        mAVTransportService->SetStateVariable("AbsoluteCounterPosition", "2147483647"); // means NOT_IMPLEMENTED

        // disable indirect eventing for certain state variables
        PLT_StateVariable* var;
        var = mAVTransportService->FindStateVariable("RelativeTimePosition");
        if (var) var->DisableIndirectEventing();
        var = mAVTransportService->FindStateVariable("AbsoluteTimePosition");
        if (var) var->DisableIndirectEventing();
        var = mAVTransportService->FindStateVariable("RelativeCounterPosition");
        if (var) var->DisableIndirectEventing();
        var = mAVTransportService->FindStateVariable("AbsoluteCounterPosition");
        if (var) var->DisableIndirectEventing();

        // GetTransportInfo
        mAVTransportService->SetStateVariable("TransportState", "NO_MEDIA_PRESENT");
        mAVTransportService->SetStateVariable("TransportStatus", "OK");
        mAVTransportService->SetStateVariable("TransportPlaySpeed", "1");

        // GetTransportSettings
        mAVTransportService->SetStateVariable("CurrentPlayMode", "NORMAL");
        mAVTransportService->SetStateVariable("CurrentRecordQualityMode", "NOT_IMPLEMENTED");
    }

    {
        /* ConnectionManager */
        service = new PLT_Service(
            this,
            "urn:schemas-upnp-org:service:ConnectionManager:1",
            "urn:upnp-org:serviceId:ConnectionManager",
            "ConnectionManager");
        NPT_CHECK_FATAL(service->SetSCPDXML((const char*) RDR_ConnectionManagerSCPD));
        NPT_CHECK_FATAL(AddService(service.AsPointer()));

        service->SetStateVariable("CurrentConnectionIDs", "0");

        // put all supported mime types here instead
        service->SetStateVariable("SinkProtocolInfo", "http-get:*:video/mp4:*,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVMED_PRO,http-get:*:video/x-ms-asf:DLNA.ORG_PN=MPEG4_P2_ASF_SP_G726,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVMED_FULL,http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_MED,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVMED_BASE,http-get:*:audio/L16;rate=44100;channels=1:DLNA.ORG_PN=LPCM,http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL,http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_NTSC,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVHIGH_PRO,http-get:*:audio/L16;rate=44100;channels=2:DLNA.ORG_PN=LPCM,http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM,http-get:*:video/x-ms-asf:DLNA.ORG_PN=VC1_ASF_AP_L1_WMA,http-get:*:audio/x-ms-wma:DLNA.ORG_PN=WMDRM_WMABASE,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVHIGH_FULL,http-get:*:audio/x-ms-wma:DLNA.ORG_PN=WMAFULL,http-get:*:audio/x-ms-wma:DLNA.ORG_PN=WMABASE,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVSPLL_BASE,http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_NTSC_XAC3,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMDRM_WMVSPLL_BASE,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVSPML_BASE,http-get:*:video/x-ms-asf:DLNA.ORG_PN=MPEG4_P2_ASF_ASP_L5_SO_G726,http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG,http-get:*:audio/mpeg:DLNA.ORG_PN=MP3,http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL_XAC3,http-get:*:audio/x-ms-wma:DLNA.ORG_PN=WMAPRO,http-get:*:video/mpeg:DLNA.ORG_PN=MPEG1,http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN,http-get:*:video/x-ms-asf:DLNA.ORG_PN=MPEG4_P2_ASF_ASP_L4_SO_G726,http-get:*:audio/L16;rate=48000;channels=2:DLNA.ORG_PN=LPCM,http-get:*:audio/mpeg:DLNA.ORG_PN=MP3X,http-get:*:video/x-ms-wmv:DLNA.ORG_PN=WMVSPML_MP3,http-get:*:video/x-ms-wmv:*");
        service->SetStateVariable("SourceProtocolInfo", "");

        service.Detach();
        service = NULL;
    }

    {
        /* RenderingControl */
        service = new PLT_Service(
            this,
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:upnp-org:serviceId:RenderingControl",
            "RenderingControl",
            "urn:schemas-upnp-org:metadata-1-0/RCS/");
        NPT_CHECK_FATAL(service->SetSCPDXML((const char*) RDR_RenderingControlSCPD));
        NPT_CHECK_FATAL(AddService(service.AsPointer()));

        service->SetStateVariableRate("LastChange", NPT_TimeInterval(0.2f));

        service->SetStateVariable("Mute", "0");
        service->SetStateVariableExtraAttribute("Mute", "Channel", "Master");
        service->SetStateVariable("Volume", "100");
        service->SetStateVariableExtraAttribute("Volume", "Channel", "Master");
        service->SetStateVariable("VolumeDB", "0");
        service->SetStateVariableExtraAttribute("VolumeDB", "Channel", "Master");

        service->SetStateVariable("PresetNameList", "FactoryDefaults");

        service.Detach();
        service = NULL;
    }

    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnGetCurrentConnectionInfo(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

// AVTransport
NPT_Result MediaRenderer::OnNext(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPause(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPlay(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnPrevious(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSeek(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnStop(PLT_ActionReference& action)
{
    YLOGD();
    return NPT_SUCCESS;
}

NPT_Result MediaRenderer::OnSetAVTransportURI(PLT_ActionReference& action)
{
    YLOGD("action:%s, counter:%d", action->GetActionDesc().GetName().GetChars(), action.GetCounter());
    std::shared_ptr<Message> message = std::make_shared<Message>(Message::kSetAVTransportURI);
    YLOGD("type:%d", message->type());
    NPT_String uri;
    NPT_CHECK_WARNING(action->GetArgumentValue("CurrentURI", uri));
    message->setString(Message::kUrl, uri.GetChars(), uri.GetLength());
    mMessageQueue.push(message);
    return NPT_SUCCESS;
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
    YFATAL_IF(gJavaVM == NULL);
    JNIEnv* jniEnv = NULL;

    gJavaVM->AttachCurrentThread(&jniEnv, NULL);

    while (!mThread->exitPending())
    {
        std::shared_ptr<Message> message;
        mMessageQueue.pop(message);
        YLOGD("message:%d", message->type());
        onMessage(jniEnv, message);
    }

    if (jniEnv->ExceptionOccurred()) {
        jniEnv->ExceptionClear();
    }

    gJavaVM->DetachCurrentThread();
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

            AndroidBundle bundle(jniEnv);
            bundle.putInt(jniEnv, "action", gJActions.SET_URL);
            bundle.putString(jniEnv, "uri", uri.c_str());
            sendAction(jniEnv, bundle);
            break;
        }
        default:
        {
            break;
        }
    }
}

void MediaRenderer::sendAction(JNIEnv* env, AndroidBundle& action)
{
    YFATAL_IF(gJOnAction == NULL || env == NULL);
    env->CallVoidMethod(gJMediaRender, gJOnAction, action.getObject());
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
    gJavaVM = vm;

    const JNINativeMethod nativeMethods[] =
    {
            {"nativeInit",   "()V",                            (void*)nativeInit},
            {"nativeSetup",  "(Lcom/eevix/DLNAMediaRender;)V",   (void*)nativeSetup},
    };

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK)
    {
        YLOGE("ERROR: GetEnv failed");
        return -1;
    }

    jclass DLNAServiceClass = env->FindClass(gDLNAServiceClassName);
    YFATAL_IF(DLNAServiceClass == NULL);

    if (0 > env->RegisterNatives(DLNAServiceClass, nativeMethods, sizeof(nativeMethods) / sizeof(nativeMethods[0])))
    {
        return -1;
    }

    return JNI_VERSION_1_4;
}

void nativeInit(JNIEnv* env)
{
    YLOGD("env:%p", env);

    if (!gUPNPService.IsRunning())
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
        YFATAL_IF(!gMediaRender.IsNull());
        gMediaRender = new MediaRenderer("YoghurtMediaRender", false, "a6572b54-f3c7-2d91-2fb5-b757f2537e22");
        gUPNPService.AddDevice(gMediaRender);
        gUPNPService.Start();
        YLOGD("UPNP is running");
    }
}

void nativeSetup(JNIEnv* env, jobject mediaRender)
{
    YLOGD("env:%p", env);
    gJMediaRender = env->NewGlobalRef(mediaRender);
    YFATAL_IF(gJMediaRender == NULL);

    gJOnAction = env->GetMethodID(env->GetObjectClass(mediaRender), "onAction", "(Landroid/os/Bundle;)V");
    jclass actionsClass = env->FindClass("com/eevix/DLNAMediaRender$Actions");
    gJActions.SET_URL = env->GetStaticIntField(actionsClass, env->GetStaticFieldID(actionsClass, "SET_URL", "I"));
    gJActions.PLAY    = env->GetStaticIntField(actionsClass, env->GetStaticFieldID(actionsClass, "PLAY", "I"));
    gJActions.PAUSE   = env->GetStaticIntField(actionsClass, env->GetStaticFieldID(actionsClass, "PAUSE", "I"));
    gJActions.RESUME  = env->GetStaticIntField(actionsClass, env->GetStaticFieldID(actionsClass, "RESUME", "I"));
    gJActions.STOP    = env->GetStaticIntField(actionsClass, env->GetStaticFieldID(actionsClass, "STOP", "I"));
}

#ifdef __cplusplus
} // extern "C"
#endif

