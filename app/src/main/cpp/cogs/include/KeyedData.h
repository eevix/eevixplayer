#ifndef EEVIX_KEYEDDATA_H
#define EEVIX_KEYEDDATA_H

#include <stdint.h>
#include <map>
#include <string>

namespace eevix
{

class KeyedData
{
public:
    KeyedData();
    ~KeyedData();
    void setInt32(const int32_t key, const int32_t value);
    void setInt64(const int32_t key, const int64_t value);
    void setObject(const int32_t key, const void* value);
    void setData(const int32_t key, void* data, const uint32_t size);
    void setString(const int32_t key, const std::string& string);
    void setString(const int32_t key, const char* string, const uint32_t size);
    void setBool(const int32_t key, bool value);

    bool getInt32(const int32_t key, int32_t& value);
    bool getInt64(const int32_t key, int64_t& value);
    bool getObject(const int32_t key, const void** value);
    bool getData(const int32_t key, void* data, uint32_t& size);
    bool getString(const int32_t key, std::string& string);
    bool getBool(const int32_t key, bool& value);

private:
    struct Item
    {
        enum Type
        {
            INT32,
            INT64,
            OBJECT,
            DATA,
            STRING,
            BOOL,
        } mType;
        union
        {
            int32_t     int32Value;
            int64_t     int64Value;
            const void* objectValue;
            void*       dataValue;
            std::string* stringValue;
            bool        boolValue;
        } mData;
        uint32_t mSize = 0;
    };

    KeyedData(const KeyedData&);
    KeyedData& operator=(const KeyedData&);
    void clearItem(Item& item);
    Item& getItem(const int32_t key);

private:
    typedef std::map<int32_t, Item> Items;
    Items mItems;
};

}
#endif //EEVIX_KEYEDDATA_H
