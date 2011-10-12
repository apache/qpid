#ifndef QPID_CLUSTER_EXP_HASH_H
#define QPID_CLUSTER_EXP_HASH_H

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

#include "qpid/sys/IntegerTypes.h"
#include <stdlib.h>
#include <string>

namespace qpid {
namespace broker {
class Queue;
class QueuedMessage;
}

namespace cluster {

class QueueContext;

/**@file hash functions */

// The following all uses the cached hash value on the Queue::getContext()
size_t hashof(const broker::Queue& q);
size_t hashof(const QueueContext& qc);
size_t hashof(const broker::QueuedMessage& qm);

// Hash directly from a value string.
size_t hashof(const std::string& s);
size_t hashof(uint32_t n);

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_HASH_H*/
