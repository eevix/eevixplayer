#ifndef EEVIX_QUEUE_H
#define EEVIX_QUEUE_H

#include <vector>
#include "Mutex.h"
#include "Condition.h"

namespace eevix
{
template<typename T>
class Queue
{
public:
    Queue(){};
    ~Queue(){};

    bool push(const T& item)
    {
        AutoMutex lock(mLock);
        mVector.push_back(item);
        mCondition.boardCast();
        return true;
    }

    bool pop(T& item)
    {
        AutoMutex lock(mLock);
        while (mVector.empty())
        {
            mCondition.wait(mLock);
        }

        if (!mVector.empty())
        {
            item = mVector[0];
            mVector.erase(mVector.begin());
            return true;
        }

        return false;
    }

    bool pop(T& item, uint64_t timeout)
    {
        AutoMutex lock(mLock);
        if (mVector.empty())
        {
            mCondition.wait(mLock, timeout);
        }

        if (!mVector.empty())
        {
            item = mVector[0];
            mVector.erase(mVector.begin());
            return true;
        }

        return false;
    }

    bool pop(T* item)
    {
        AutoMutex lock(mLock);
        while (mVector.empty())
        {
            mCondition.wait(mLock);
        }

        if (!mVector.empty())
        {
            *item = mVector[0];
            mVector.erase(mVector.begin());
            return true;
        }

        return false;
    }

    void clear()
    {
        AutoMutex lock(mLock);
        mVector.clear();
        mVector.capacity();
    }
private:
    std::vector<T> mVector;
    Mutex          mLock;
    Condition      mCondition;
};

}
#endif //EEVIXMEDIA_QUEUE_H
