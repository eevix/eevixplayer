#include "Log.h"
#include "gtest/gtest.h"

int main(int argc, char** argv)
{
    YLOGD("test start");
    testing::GTEST_FLAG(color) = "yes";
    testing::InitGoogleTest(&argc, argv);
    RUN_ALL_TESTS();
    YLOGD("test end");
    return 0;
}
