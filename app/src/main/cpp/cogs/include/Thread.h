#ifndef EEVIX_THREAD_H
#define EEVIX_THREAD_H

#include <pthread.h>
#include <string>
#include "Mutex.h"
#include "Condition.h"

namespace eevix
{

class Thread
{
public:
    Thread();
    virtual ~Thread();
    bool run(const char* name);
    void requestExitAndWait();
    void requestExit();
    bool exitPending();

protected:
    virtual bool readyToRun();
    virtual bool threadLoop() = 0;

private:
    static void* entryFunction(void* userData);
    void _threadLoop();

private:
    bool        mExitPending;
    Condition   mCondition;
    Mutex       mLock;
    bool        mRunning;
    std::string mName;
    pthread_t   mThreadID;
};

} // namespace eevix

#endif // EEVIX_THREAD_H
