#include <windows.h>
#include "Thread.h"
#include "Log.h"
#include "gtest/gtest.h"

TEST(thread, runNomally)
{
    class ThreadTester : public yoghurt::Thread
    {
    public:
        ThreadTester():mRan(false), mReadyCount(0) {}

        bool readyToRun()
        {
            mReadyCount++;
            return true;
        }

        bool threadLoop()
        {
            mRan = true;
            return false;
        }

        bool     mRan;
        uint32_t mReadyCount;
    };

    ThreadTester* tester = new ThreadTester;
    EXPECT_TRUE(tester->run("test"));
    tester->requestExitAndWait();
    EXPECT_TRUE(tester->mRan);
    EXPECT_EQ(tester->mReadyCount, 1);
    delete tester;
}

TEST(thread, readyToRunReturnFalse)
{
    class ThreadTester : public yoghurt::Thread
    {
    public:
        ThreadTester():mReadied(false), mRan(false) {}

        bool readyToRun()
        {
            mReadied = true;
            return false;
        }

        bool threadLoop()
        {
            mRan = true;
            return true;
        }

        bool mReadied;
        bool mRan;
    };

    ThreadTester* tester = new ThreadTester;
    EXPECT_TRUE(tester->run("test"));
    while (!tester->mReadied)
    {
        Sleep(5);
    }
    EXPECT_TRUE(tester->mReadied);
    EXPECT_FALSE(tester->mRan);
    delete tester;
}
