#ifndef EEVIX_CONDITION_H
#define EEVIX_CONDITION_H

#include <pthread.h>
#include "Mutex.h"

namespace eevix
{

class Condition
{
public:
    Condition()
    {
        pthread_cond_init(&mCondition, NULL);
    }

    ~Condition()
    {
        pthread_cond_destroy(&mCondition);
    }

    inline void wait(Mutex& lock)
    {
        pthread_cond_wait(&mCondition, &lock.mMutex);
    }

    inline void wait(Mutex& lock, int64_t timeout)
    {
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec  += timeout / 1000;
        ts.tv_nsec += timeout % 1000 * 1000000;
        if (ts.tv_nsec >= 1000000000) {
            ts.tv_nsec -= 1000000000;
            ts.tv_sec  += 1;
        }

        pthread_cond_timedwait(&mCondition, &lock.mMutex, &ts);
    }

    inline void singal()
    {
        pthread_cond_signal(&mCondition);
    }

    inline void boardCast()
    {
        pthread_cond_broadcast(&mCondition);
    }

private:
    pthread_cond_t mCondition;
};

} // namespace eevix

#endif // EEVIX_CONDITION_H
