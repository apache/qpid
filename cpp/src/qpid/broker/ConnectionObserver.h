#ifndef QPID_BROKER_CONNECTIONOBSERVER_H
#define QPID_BROKER_CONNECTIONOBSERVER_H

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

#include <string>

namespace qpid {
namespace broker {

class Connection;

/**
 * Observer that is informed of connection events.  For use by
 * plug-ins that want to be notified of, or influence, connection
 * events.
 */
class ConnectionObserver
{
  public:
    virtual ~ConnectionObserver() {}

    /** Called when a connection is first established. */
    virtual void connection(Connection&) {}

    /** Called when the opening negotiation is done and the connection is authenticated.
     * @exception Throwing an exception will abort the connection.
     */
    virtual void opened(Connection&) {}

    /** Called when a connection is closed. */
    virtual void closed(Connection&) {}

    /** Called when a connection is forced closed. */
    virtual void forced(Connection&, const std::string& /*message*/) {}
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CONNECTIONOBSERVER_H*/
