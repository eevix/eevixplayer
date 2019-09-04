//
// Created by Administrator on 2019/5/18.
//


#ifdef WIN32
#include <memory>
#include <cstring>
#endif

#include <KeyedData.h>
#include "KeyedData.h"
#include "Log.h"

namespace eevix
{
KeyedData::KeyedData()
{

}

KeyedData::~KeyedData()
{
    for (Items::iterator iterator = mItems.begin(); iterator != mItems.end(); iterator++)
    {
        clearItem(iterator->second);
    }

    mItems.clear();
}

void KeyedData::setInt32(const int32_t key, const int32_t value)
{
    Item& item = getItem(key);
    item.mSize = sizeof(int32_t);
    item.mData.int32Value = value;
    item.mType = Item::INT32;
}

void KeyedData::setInt64(const int32_t key, const int64_t value)
{
    Item& item = getItem(key);
    item.mSize = sizeof(int64_t);
    item.mData.int64Value = value;
    item.mType = Item::INT64;
}

void KeyedData::setObject(const int32_t key, const void* value)
{
    Item& item = getItem(key);
    item.mSize = sizeof(void *);
    item.mData.objectValue = value;
    item.mType = Item::OBJECT;
}

void KeyedData::setData(const int32_t key, void* data, const uint32_t size)
{
    Item& item = getItem(key);
    item.mSize = size;
    item.mData.dataValue = malloc(size);
    FATAL_IF(item.mData.dataValue == NULL);
    memcpy(item.mData.dataValue, data, size);
    item.mType = Item::DATA;
}

void KeyedData::setString(const int32_t key, const std::string& string)
{
    Item& item = getItem(key);
    item.mSize = (uint32_t)(string.length());
    item.mData.stringValue = new std::string(string);
    item.mType = Item::STRING;
}

void KeyedData::setString(const int32_t key, const char* string, const uint32_t size)
{
    Item& item = getItem(key);
    item.mSize = size;
    item.mData.stringValue = new std::string(string, size);
    item.mType = Item::STRING;
}

void KeyedData::setBool(const int32_t key, bool value)
{
    Item& item = getItem(key);
    item.mSize = sizeof(value);
    item.mData.boolValue = value;
    item.mType = Item::BOOL;
}

bool KeyedData::getInt32(const int32_t key, int32_t &value)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::INT32)
        {
            value = item.mData.int32Value;
            return true;
        }
    }

    return false;
}

bool KeyedData::getInt64(const int32_t key, int64_t &value)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::INT64)
        {
            value = item.mData.int64Value;
            return true;
        }
    }

    return false;
}

bool KeyedData::getObject(const int32_t key, const void **value)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::OBJECT)
        {
            *value = item.mData.objectValue;
            return true;
        }
    }

    return false;
}

bool KeyedData::getData(const int32_t key, void *data, uint32_t &size)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::DATA && data != NULL && size >= item.mSize)
        {
            memcpy(data, item.mData.dataValue, item.mSize);
            size = item.mSize;
            return true;
        }
    }

    return false;
}

bool KeyedData::getString(const int32_t key, std::string& string)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::STRING)
        {
            string = *item.mData.stringValue;
            return true;
        }
    }

    return false;
}

bool KeyedData::getBool(const int32_t key, bool& value)
{
    Items::iterator iterator = mItems.find(key);
    if (iterator != mItems.end())
    {
        const Item& item = iterator->second;
        if (item.mSize != 0 && item.mType == Item::BOOL)
        {
            value = item.mData.boolValue;
            return true;
        }
    }

    return false;
}

void KeyedData::clearItem(Item& item)
{
    if (item.mSize == 0)
    {
        return;
    }

    switch (item.mType)
    {
        case Item::DATA:
        {
            if (item.mData.dataValue != NULL)
            {
                free(item.mData.dataValue);
                item.mData.dataValue = NULL;
            }
            break;
        }
        case Item::STRING:
        {
            if (item.mData.stringValue != NULL)
            {
                item.mData.stringValue->clear();
                delete item.mData.stringValue;
                item.mData.stringValue = NULL;
            }
            break;
        }
        default:
        {
            break;
        }
    }

    item.mSize = 0;
}

KeyedData::Item& KeyedData::getItem(const int32_t key)
{
    Item& item = mItems[key];
    clearItem(item);
    return item;
}

} // namespace eevix