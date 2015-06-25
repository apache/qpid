#ifndef QPID_HA_CONNECTIONOBSERVER_H
#define QPID_HA_CONNECTIONOBSERVER_H

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
#include "qpid/broker/ConnectionObserver.h"
#include "qpid/types/Uuid.h"
#include "qpid/sys/Mutex.h"
#include "boost/shared_ptr.hpp"

namespace qpid {
struct Address;

namespace ha {
class BrokerInfo;
class HaBroker;
class LogPrefix;

/**
 * Observes connections, delegates to another ConnectionObserver for
 * actions specific to primary or backup.
 *
 * THREAD SAFE: called in arbitrary connection threads.
 *
 * Main role of this class is to provide a continuous observer object
 * on the connection so we can't lose observations between removing
 * one observer and adding another.
 */
class ConnectionObserver : public broker::ConnectionObserver
{
  public:
    typedef boost::shared_ptr<broker::ConnectionObserver> ObserverPtr;

    static const std::string ADMIN_TAG;
    static const std::string BACKUP_TAG;
    static const std::string ADDRESS_TAG;

    static bool getBrokerInfo(const broker::Connection& connection, BrokerInfo&);
    static bool getAddress(const broker::Connection& connection, Address&);

    ConnectionObserver(HaBroker& haBroker, const types::Uuid& self);

    void setObserver(const ObserverPtr&);
    ObserverPtr getObserver();

    void reset();

    void opened(broker::Connection& connection);
    void closed(broker::Connection& connection);

  private:
    bool isSelf(const broker::Connection&);

    sys::Mutex lock;
    HaBroker& haBroker;
    const LogPrefix& logPrefix;
    ObserverPtr observer;
    types::Uuid self;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_CONNECTIONOBSERVER_H*/
