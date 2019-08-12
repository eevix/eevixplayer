#ifndef EEVIX_LOG_H
#define EEVIX_LOG_H

#ifndef LOG_TAG
#define LOG_TAG ""
#endif

#if defined ANDROID
#include <android/log.h>

#define YLOGD(fmt, args...)                 __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[%s][%d] " fmt, __FUNCTION__, __LINE__, ##args)
#define YLOGE(fmt, args...)                 __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[%s][%d] " fmt, __FUNCTION__, __LINE__, ##args)
#define YFATAL_IF(condition)                 do \
                                             { \
                                                 if (condition) \
                                                 { \
                                                     __android_log_assert(#condition, LOG_TAG, NULL); \
                                                 } \
                                             } \
                                             while (0)

#elif defined WIN32
#include <stdio.h>

#define YLOGD(fmt, args...)                 printf(LOG_TAG " [%s][%d] " fmt "\n", __FUNCTION__, __LINE__, ##args)
#define YLOGE(fmt, args...)                 printf(LOG_TAG " [%s][%d] " fmt "\n", __FUNCTION__, __LINE__, ##args)
#define YFATAL_IF(condition)

#endif

#endif //EEVIX_LOG_H
