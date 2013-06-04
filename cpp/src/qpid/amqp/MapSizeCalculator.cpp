/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "MapSizeCalculator.h"
#include "CharSequence.h"
#include "Descriptor.h"

namespace qpid {
namespace amqp {

MapSizeCalculator::MapSizeCalculator() : size(0), count(0) {}

void MapSizeCalculator::handleKey(const CharSequence& key)
{
    ++count;
    size += getEncodedSize(key);
}

size_t MapSizeCalculator::getEncodedSize(const CharSequence& s)
{
    return 1/*typecode*/ + (s.size < 256 ? 1 : 4)/*size field*/ + s.size;
}

void MapSizeCalculator::handleVoid(const CharSequence& key)
{
    handleKey(key);
    size += 1;/*typecode*/;
}

void MapSizeCalculator::handleBool(const CharSequence& key, bool /*value*/)
{
    handleKey(key);
    size += 1;/*typecode*/;
}

void MapSizeCalculator::handleUint8(const CharSequence& key, uint8_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 1/*value*/;
}

void MapSizeCalculator::handleUint16(const CharSequence& key, uint16_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 2/*value*/;
}

void MapSizeCalculator::handleUint32(const CharSequence& key, uint32_t value)
{
    handleKey(key);
    size += 1;/*typecode*/;
    if (value > 0) {
        if (value < 256) size += 1/*UINT_SMALL*/;
        else size += 4/*UINT*/;
    }//else UINT_ZERO
}

void MapSizeCalculator::handleUint64(const CharSequence& key, uint64_t value)
{
    handleKey(key);
    size += 1;/*typecode*/;
    if (value > 0) {
        if (value < 256) size += 1/*ULONG_SMALL*/;
        else size += 8/*ULONG*/;
    }//else ULONG_ZERO
}

void MapSizeCalculator::handleInt8(const CharSequence& key, int8_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 1/*value*/;
}

void MapSizeCalculator::handleInt16(const CharSequence& key, int16_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 2/*value*/;
}

void MapSizeCalculator::handleInt32(const CharSequence& key, int32_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 4/*value*/;
}

void MapSizeCalculator::handleInt64(const CharSequence& key, int64_t /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 8/*value*/;
}

void MapSizeCalculator::handleFloat(const CharSequence& key, float /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 4/*value*/;
}

void MapSizeCalculator::handleDouble(const CharSequence& key, double /*value*/)
{
    handleKey(key);
    size += 1/*typecode*/ + 8/*value*/;
}

void MapSizeCalculator::handleString(const CharSequence& key, const CharSequence& value, const CharSequence& /*encoding*/)
{
    handleKey(key);
    size += getEncodedSize(value);
}

size_t MapSizeCalculator::getSize() const
{
    return size;
}

size_t MapSizeCalculator::getCount() const
{
    return count;
}

size_t MapSizeCalculator::getTotalSizeRequired(const Descriptor* d) const
{
    size_t result(size);
    if (d) result += d->getSize();
    result += 1/*typecode*/;
    if (count * 2 > 255 || size > 255) {
        result += 4/*size*/ + 4/*count*/;
    } else {
        result += 1/*size*/ + 1/*count*/;
    }
    return result;
}


}} // namespace qpid::amqp
