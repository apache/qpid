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

#ifndef QPID_LINEARSTORE_PREPAREDTRANSACTION_H
#define QPID_LINEARSTORE_PREPAREDTRANSACTION_H

#include <boost/ptr_container/ptr_list.hpp>
#include <boost/shared_ptr.hpp>
#include <list>
#include <map>
#include <stdint.h>

namespace qpid{
namespace linearstore{

typedef uint64_t queue_id;
typedef uint64_t message_id;

class LockedMappings
{
public:
    typedef boost::shared_ptr<LockedMappings> shared_ptr;
    typedef std::map<std::string, shared_ptr> map;
    typedef std::pair<queue_id, message_id> idpair;
    typedef std::list<idpair>::iterator iterator;

    void add(queue_id queue, message_id message);
    bool isLocked(queue_id queue, message_id message);
    std::size_t size() { return locked.size(); }
    iterator begin() { return locked.begin(); }
    iterator end() { return locked.end(); }

    static void add(LockedMappings::map& map, std::string& key, queue_id queue, message_id message);

private:
    std::list<idpair> locked;
};

struct PreparedTransaction
{
    typedef boost::ptr_list<PreparedTransaction> list;

    const std::string xid;
    const LockedMappings::shared_ptr enqueues;
    const LockedMappings::shared_ptr dequeues;

    PreparedTransaction(const std::string& xid, LockedMappings::shared_ptr enqueues, LockedMappings::shared_ptr dequeues);
    bool isLocked(queue_id queue, message_id message);
    static bool isLocked(PreparedTransaction::list& txns, queue_id queue, message_id message);
    static PreparedTransaction::list::iterator getLockedPreparedTransaction(PreparedTransaction::list& txns, queue_id queue, message_id message);
};

}}

#endif // ifndef QPID_LINEARSTORE_PREPAREDTRANSACTION_H
