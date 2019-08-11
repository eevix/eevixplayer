#ifndef YOGHURT_CONDITION_H
#define YOGHURT_CONDITION_H

#include <pthread.h>
#include "Mutex.h"

namespace yoghurt
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

#endif // YOGHURT_CONDITION_H
