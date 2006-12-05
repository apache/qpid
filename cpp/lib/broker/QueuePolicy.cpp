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
#include <QueuePolicy.h>

using namespace qpid::broker;

QueuePolicy::QueuePolicy(u_int32_t _maxCount, u_int64_t _maxSize) : maxCount(_maxCount), maxSize(_maxSize) {}

void QueuePolicy::enqueued(Message::shared_ptr& msg, MessageStore* store)
{
    if (checkCount(msg) || checkSize(msg)) {
        msg->releaseContent(store);
    }
}

void QueuePolicy::dequeued(Message::shared_ptr& msg, MessageStore* /*store*/)
{
    if (maxCount) count--;
    if (maxSize) size -= msg->contentSize();
}

bool QueuePolicy::checkCount(Message::shared_ptr& /*msg*/)
{
    return maxCount && ++count > maxCount;
}

bool QueuePolicy::checkSize(Message::shared_ptr& msg)
{
    return maxSize && (size += msg->contentSize()) > maxSize;
}

