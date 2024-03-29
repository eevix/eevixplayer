#ifndef EEVIX_MUTEX
#define EEVIX_MUTEX

#include <stdint.h>
#include <sys/types.h>
#include <pthread.h>

namespace eevix
{
class Mutex
{
public:
    Mutex();
    ~Mutex();
    void lock();
    void unlock();
    class AutoLock
    {
    public:
        inline AutoLock(Mutex& mutex) : mLock(mutex)  { mLock.lock(); }
        inline AutoLock(Mutex* mutex) : mLock(*mutex) { mLock.lock(); }
        inline ~AutoLock() { mLock.unlock(); }
    private:
        Mutex& mLock;
    };

private:
    friend class Condition;
    Mutex(const Mutex&);
    Mutex& operator= (const Mutex&);
    pthread_mutex_t mMutex;
};

inline Mutex::Mutex()
{
    pthread_mutex_init(&mMutex, NULL);
}

inline Mutex::~Mutex()
{
    pthread_mutex_destroy(&mMutex);
}

inline void Mutex::lock()
{
    pthread_mutex_lock(&mMutex);
}

inline void Mutex::unlock()
{
    pthread_mutex_unlock(&mMutex);
}

typedef Mutex::AutoLock AutoMutex;

}
#endif // EEVIX_MUTEX