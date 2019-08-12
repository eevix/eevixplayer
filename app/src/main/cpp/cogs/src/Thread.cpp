#ifdef ANDROID
#include <sys/prctl.h>
#endif

#include "Thread.h"
#include "Log.h"

namespace eevix
{
Thread::Thread():
    mExitPending(false),
    mRunning(false)
{
#ifdef ANDROID
    mThreadID = -1;
#endif
}

Thread::~Thread()
{
    requestExitAndWait();
}

bool Thread::run(const char* name)
{
    AutoMutex lock(mLock);
    if (mRunning)
    {
        return false;
    }

    mName = name;
    mExitPending = false;

    pthread_attr_t attr;
    int ret = pthread_attr_init(&attr);
    if (ret < 0)
    {
        return false;
    }

    ret = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (ret < 0)
    {
        pthread_attr_destroy(&attr);
        return false;
    }

    ret = pthread_create(&mThreadID, &attr, &entryFunction, (void*)this);
    if (ret < 0)
    {
        pthread_attr_destroy(&attr);
        return false;
    }

    pthread_attr_destroy(&attr);
    mRunning = true;
    return true;
}

void Thread::requestExitAndWait()
{
    AutoMutex lock(mLock);

    YFATAL_IF(mRunning && mThreadID == pthread_self());

    while (mRunning)
    {
        mExitPending = true;
        mCondition.wait(mLock);
    }
}

void Thread::requestExit()
{
    AutoMutex lock(mLock);
    if (mRunning)
    {
        mExitPending = true;
    }
}

bool Thread::exitPending()
{
    AutoMutex lock(mLock);
    return mExitPending;
}

void* Thread::entryFunction(void* userData)
{
    if (userData != NULL)
    {
        ((Thread*)userData)->_threadLoop();
    }

    return NULL;
}

bool Thread::readyToRun()
{
    return true;
}

void Thread::_threadLoop()
{
#ifdef ANDROID
    if (mName.length() > 0)
    {
        prctl(PR_SET_NAME, mName.c_str());
    }
#endif

    if (!readyToRun())
    {
        goto EXIT;
    }

    while (threadLoop() && !exitPending());

EXIT:
    AutoMutex lock(mLock);
    mExitPending = false;
    mRunning = false;
    mCondition.boardCast();
}

} // namespace eevix
