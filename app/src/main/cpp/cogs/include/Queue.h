#ifndef YOGHURT_QUEUE_H
#define YOGHURT_QUEUE_H

#include <vector>
#include "Mutex.h"
#include "Condition.h"

namespace yoghurt
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
#endif //YOGHURTMEDIA_QUEUE_H
