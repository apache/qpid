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

#include "qpid/legacystore/PreparedTransaction.h"
#include <algorithm>

using namespace mrg::msgstore;
using std::string;

void LockedMappings::add(queue_id queue, message_id message)
{
    locked.push_back(std::make_pair(queue, message));
}

bool LockedMappings::isLocked(queue_id queue, message_id message)
{
    idpair op( std::make_pair(queue, message) );
    return find(locked.begin(), locked.end(), op) != locked.end();
}

void LockedMappings::add(LockedMappings::map& map, std::string& key, queue_id queue, message_id message)
{
    LockedMappings::map::iterator i = map.find(key);
    if (i == map.end()) {
        LockedMappings::shared_ptr ptr(new LockedMappings());
        i = map.insert(std::make_pair(key, ptr)).first;
    }
    i->second->add(queue, message);
}

bool PreparedTransaction::isLocked(queue_id queue, message_id message)
{
    return (enqueues.get() && enqueues->isLocked(queue, message))
        || (dequeues.get() && dequeues->isLocked(queue, message));
}


bool PreparedTransaction::isLocked(PreparedTransaction::list& txns, queue_id queue, message_id message)
{
    for (PreparedTransaction::list::iterator i = txns.begin(); i != txns.end(); i++) {
        if (i->isLocked(queue, message)) {
            return true;
        }
    }
    return false;
}

PreparedTransaction::list::iterator PreparedTransaction::getLockedPreparedTransaction(PreparedTransaction::list& txns, queue_id queue, message_id message)
{
    for (PreparedTransaction::list::iterator i = txns.begin(); i != txns.end(); i++) {
        if (i->isLocked(queue, message)) {
            return i;
        }
    }
    return txns.end();
}

PreparedTransaction::PreparedTransaction(const std::string& _xid,
                                         LockedMappings::shared_ptr _enqueues,
                                         LockedMappings::shared_ptr _dequeues)

    : xid(_xid), enqueues(_enqueues), dequeues(_dequeues) {}

