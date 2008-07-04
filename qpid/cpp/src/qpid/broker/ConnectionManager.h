#ifndef QPID_BROKER_CONNECTIONMANAGER_H
#define QPID_BROKER_CONNECTIONMANAGER_H

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

#include "qpid/RefCounted.h"
#include "qpid/sys/Mutex.h"
#include <boost/intrusive_ptr.hpp>
#include <vector>
#include <memory>

namespace qpid {

namespace sys {
class ConnectionOutputHandler;
}

namespace broker {

class Broker;
class Connection;

/**
 * Manages connections and observers.
 */
class ConnectionManager {
  public:

    /**
     * Observer notified of ConnectionManager events.
     */
    struct Observer : public RefCounted {
        /** Called when a connection is attached. */
        virtual void created(Connection&) {}
    };

    /** Called to create a new Connection, applies observers. */
    std::auto_ptr<Connection> create(sys::ConnectionOutputHandler* out, Broker& broker, const std::string& mgmtId, bool isClient = false);

    /** Add an observer */
    void add(const boost::intrusive_ptr<Observer>&);

  private:
    typedef std::vector<boost::intrusive_ptr<Observer> > Observers;

    sys::Mutex lock;
    Observers observers;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CONNECTIONMANAGER_H*/
