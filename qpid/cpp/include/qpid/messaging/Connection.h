#ifndef QPID_MESSAGING_CONNECTION_H
#define QPID_MESSAGING_CONNECTION_H

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
#include "qpid/messaging/ImportExport.h"
#include "qpid/messaging/Handle.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace messaging {

template <class> class PrivateImplRef;
class ConnectionImpl;
class Session;

struct InvalidOptionString : public qpid::Exception 
{
    InvalidOptionString(const std::string& msg);
};

class Connection : public qpid::messaging::Handle<ConnectionImpl>
{
  public:
    QPID_CLIENT_EXTERN Connection(ConnectionImpl* impl);
    QPID_CLIENT_EXTERN Connection(const Connection&);
    /**
     * Current implementation supports the following options:
     * 
     *     username
     *     password
     *     heartbeat
     *     tcp-nodelay
     *     sasl-mechanism
     *     sasl-min-ssf
     *     sasl-max-ssf
     *     protocol
     *     urls
     * 
     * (note also bounds, locale, max-channels and max-framesize, but
     * not sure whether those should be documented here)
     * 
     * Reconnect behaviour can be controlled through the following options:
     * 
     *     reconnect: true/false (enables/disables reconnect entirely)
     *     reconnect-timeout: number of seconds (give up and report failure after specified time)
     *     reconnect-limit: n (give up and report failure after specified number of attempts)
     *     reconnect-interval-min: number of seconds (initial delay between failed reconnection attempts)
     *     reconnect-interval-max: number of seconds (maximum delay between failed reconnection attempts)
     *     reconnect-interval: shorthand for setting the same reconnect_interval_min/max
     *
     *     The reconnect-interval is the time that the client waits
     *     for after a failed attempt to reconnect before retrying. It
     *     starts at the value of the min-retry-interval and is
     *     doubled every failure until the value of max-retry-interval
     *     is reached.
     */
    QPID_CLIENT_EXTERN Connection(const qpid::types::Variant::Map& options = qpid::types::Variant::Map());
    /**
     * Creates a connection using an option string of the form
     * {name=value,name2=value2...}, see above for options supported.
     * 
     * @exception InvalidOptionString if the string does not match the correct syntax
     */
    QPID_CLIENT_EXTERN Connection(const std::string& options);
    QPID_CLIENT_EXTERN ~Connection();
    QPID_CLIENT_EXTERN Connection& operator=(const Connection&);
    QPID_CLIENT_EXTERN void setOption(const std::string& name, const qpid::types::Variant& value);
    QPID_CLIENT_EXTERN void open(const std::string& url);
    /**
     * Closes a connection and all sessions associated with it. An
     * opened connection must be closed before the last handle is
     * allowed to go out of scope.
     */
    QPID_CLIENT_EXTERN void close();
    QPID_CLIENT_EXTERN Session createTransactionalSession(const std::string& name = std::string());
    QPID_CLIENT_EXTERN Session createSession(const std::string& name = std::string());

    QPID_CLIENT_EXTERN Session getSession(const std::string& name) const;
  private:
  friend class qpid::messaging::PrivateImplRef<Connection>;

};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_CONNECTION_H*/
