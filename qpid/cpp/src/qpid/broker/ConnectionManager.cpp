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
#include "ConnectionManager.h"
#include "Connection.h"

namespace qpid {
namespace broker {

std::auto_ptr<Connection>
ConnectionManager::create(sys::ConnectionOutputHandler* out, Broker& broker, const std::string& mgmtId, bool isClient) {
    std::auto_ptr<Connection> c(new Connection(out, broker, mgmtId, isClient));
    sys::Mutex::ScopedLock l(lock);
    std::for_each(observers.begin(), observers.end(),
                  boost::bind(&Observer::created, _1, boost::ref(*c)));
    return c;
}

void ConnectionManager::add(const boost::intrusive_ptr<Observer>& observer) {
    sys::Mutex::ScopedLock l(lock);
    observers.push_back(observer);
}

}} // namespace qpid::broker
