#ifndef QPID_HA_PRIMARYCONNECTIONOBSERVER_H
#define QPID_HA_PRIMARYCONNECTIONOBSERVER_H

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

#include "types.h"
#include "ConnectionObserver.h"
#include "qpid/broker/ConnectionObserver.h"
#include "qpid/types/Uuid.h"
#include "qpid/sys/Mutex.h"
#include <boost/function.hpp>

namespace qpid {

namespace broker {
class Connection;
}

namespace ha {
class HaBroker;

/**
 * Monitor connections on a primary broker. Update membership and
 * primary readiness.
 *
 * THREAD SAFE: has no state, just mediates between other thread-safe objects.
 */
// FIXME aconway 2012-06-06: rename observer
class PrimaryConnectionMonitor : public broker::ConnectionObserver
{
  public:
    PrimaryConnectionMonitor(Primary& p) : primary(p) {}
    void opened(broker::Connection& connection) { primary.opened(connection); }
    void closed(broker::Connection& connection) { primary.closed(connection); }

  private:
    Primary& primary;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARYCONNECTIONOBSERVER_H*/
