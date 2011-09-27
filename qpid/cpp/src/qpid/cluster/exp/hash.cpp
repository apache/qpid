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
#include "hash.h"
#include "QueueContext.h"
#include "qpid/broker/QueuedMessage.h"
#include <boost/functional/hash.hpp>

namespace qpid {
namespace cluster {
size_t hashof(const std::string& s) { return boost::hash_value(s); }
size_t hashof(const QueueContext& qc) { return qc.getHash(); }
size_t hashof(const broker::Queue& q) {
    return QueueContext::get(const_cast<broker::Queue&>(q))->getHash();
}
size_t hashof(uint32_t n) { return boost::hash_value(n); }
size_t hashof(const broker::QueuedMessage& qm) { return hashof(*qm.queue); }

}} // namespace qpid::cluster
