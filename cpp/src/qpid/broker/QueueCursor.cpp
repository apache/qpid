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
#include "QueueCursor.h"
#include "qpid/broker/Message.h"

namespace qpid {
namespace broker {
QueueCursor::QueueCursor(SubscriptionType t) : type(t), position(0), version(0), valid(false) {}

void QueueCursor::setPosition(int32_t p, int32_t v)
{
    position = p;
    version = v;
    valid = true;
}

bool QueueCursor::check(const Message& m)
{
    return (m.getState() == AVAILABLE || ((type == REPLICATOR || type == PURGE) && m.getState() == ACQUIRED));
}

bool QueueCursor::isValid(int32_t v)
{
    return valid && (valid = (v == version));
}
}} // namespace qpid::broker
