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
#include "QueuePolicy.h"
#include "qpid/framing/FieldValue.h"

using namespace qpid::broker;
using namespace qpid::framing;

QueuePolicy::QueuePolicy(uint32_t _maxCount, uint64_t _maxSize) : 
    maxCount(_maxCount), maxSize(_maxSize), count(0), size(0) {}

QueuePolicy::QueuePolicy(const FieldTable& settings) :
    maxCount(getInt(settings, maxCountKey, 0)), 
    maxSize(getInt(settings, maxSizeKey, 0)), count(0), size(0) {}

void QueuePolicy::enqueued(uint64_t _size)
{
    if (maxCount) count++;
    if (maxSize) size += _size;
}

void QueuePolicy::dequeued(uint64_t _size)
{
    if (maxCount) count--;
    if (maxSize) size -= _size;
}

bool QueuePolicy::limitExceeded()
{
    return (maxSize && size > maxSize) || (maxCount && count > maxCount);
}

void QueuePolicy::update(FieldTable& settings)
{
    if (maxCount) settings.setInt(maxCountKey, maxCount);
    if (maxSize) settings.setInt(maxSizeKey, maxSize);    
}


int QueuePolicy::getInt(const FieldTable& settings, const std::string& key, int defaultValue)
{
    //Note: currently field table only contain signed 32 bit ints, which
    //      restricts the values that can be set on the queue policy.
    try {
        return settings.getInt(key); 
    } catch (FieldValueException& ignore) {
        return defaultValue;
    }
}

const std::string QueuePolicy::maxCountKey("qpid.max_count");
const std::string QueuePolicy::maxSizeKey("qpid.max_size");
