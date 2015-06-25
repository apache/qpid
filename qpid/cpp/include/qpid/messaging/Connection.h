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
#include "qpid/messaging/ImportExport.h"

#include "qpid/messaging/Handle.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/types/Variant.h"

#include <string>

namespace qpid {
namespace messaging {

#ifndef SWIG
template <class> class PrivateImplRef;
#endif
class ConnectionImpl;
class Session;

/**  \ingroup messaging
 * A connection represents a network connection to a remote endpoint.
 */

class QPID_MESSAGING_CLASS_EXTERN Connection : public qpid::messaging::Handle<ConnectionImpl>
{
  public:
    QPID_MESSAGING_EXTERN Connection(ConnectionImpl* impl);
    QPID_MESSAGING_EXTERN Connection(const Connection&);
    QPID_MESSAGING_EXTERN Connection();
    /**
     * Current implementation supports the following options:
     *
     * - heartbeat: the heartbeat interval in seconds
     * - tcp_nodelay: true/false, whether nagle should be disabled or not
     * - transport: the underlying transport to use (e.g. tcp, ssl, rdma)
     * - protocol: the version of AMQP to use (e.g. amqp0-10 or amqp1.0)
     *
     * (Note: the transports and/or protocols recognised may depend on
     * which plugins are loaded. AT present support for heartbeats is
     * missing in AMQP 1.0)
     *
     * - username: the username to authenticate as
     * - password: the password to use if required by the selected authentication mechanism
     * - sasl_mechanisms: a space separated list of acceptable SASL mechanisms
     * - sasl_min_ssf: the minimum acceptable security strength factor
     * - sasl_max_ssf: the maximum acceptable security strength factor
     * - sasl_service: the service name if needed by the SASL mechanism in use
     *
     * Reconnect behaviour can be controlled through the following options:
     *
     * - reconnect: true/false (enables/disables reconnect entirely)
     * - reconnect_timeout: seconds (give up and report failure after specified time)
     * - reconnect_limit: n (give up and report failure after specified number of attempts)
     * - reconnect_interval_min: seconds (initial delay between failed reconnection attempts)
     * - reconnect_interval_max: seconds (maximum delay between failed reconnection attempts)
     * - reconnect_interval: shorthand for setting the same reconnect_interval_min/max
     * - reconnect_urls: list of alternate urls to try when connecting
     *
     * The reconnect_interval is the time that the client waits for
     * after a failed attempt to reconnect before retrying. It starts
     * at the value of the min_retry_interval and is doubled every
     * failure until the value of max_retry_interval is reached.
     *
     * Values in seconds can be fractional, for example 0.001 is a
     * millisecond delay.
     *
     * If the SSL transport is used, the following options apply:
     *
     * - ssl_cert_name: the name of the certificate to use for a given
     * - connection ssl_ignore_hostname_verification_failure: if set
     *               to true, will allow client to connect to server even
     *               if the  hostname used (or ip address) doesn't match
     *               what is in the servers certificate. I.e. this disables
     *               authentication of the server to the client (and should
     *               be used only as a last resort)
     *
     * When AMQP 1.0 is used, the following options apply:
     *
     * - container_id: sets the container id to use for the connection
     * - nest_annotations: if true, any annotations in received
     *      messages will be presented as properties with keys
     *      x-amqp-delivery-annotations or x-amqp-delivery-annotations
     *      and values that are nested maps containing the
     *      annotations. If false, the annotations will simply be merged
     *      in with the properties.
     * - set_to_on_send: If true, all sent messages will have the to
     *      field set to the node name of the sender
     * - properties or client_properties: the properties to include in the open frame sent
     *
     * The following options can be used to tune behaviour if needed
     * (these are not yet supported over AMQP 1.0):
     *
     * - tcp_nodelay: disables Nagle's algorithm on the underlying tcp socket
     * - max_channels: restricts the maximum number of channels supported
     * - max_frame_size: restricts the maximum frame size supported
     */
    QPID_MESSAGING_EXTERN Connection(const std::string& url, const qpid::types::Variant::Map& options = qpid::types::Variant::Map());
    /**
     * Creates a connection using an option string of the form
     * {name:value,name2:value2...}, see above for options supported.
     *
     * @exception InvalidOptionString if the string does not match the correct syntax
     */
    QPID_MESSAGING_EXTERN Connection(const std::string& url, const std::string& options);
    QPID_MESSAGING_EXTERN ~Connection();
    QPID_MESSAGING_EXTERN Connection& operator=(const Connection&);
    QPID_MESSAGING_EXTERN void setOption(const std::string& name, const qpid::types::Variant& value);
    QPID_MESSAGING_EXTERN void open();
    QPID_MESSAGING_EXTERN bool isOpen();
    QPID_MESSAGING_EXTERN bool isOpen() const;

    /**
     * Attempts to reconnect to the specified url, re-establish
     * existing sessions, senders and receivers and resend any indoubt
     * messages.
     *
     * This can be used to directly control reconnect behaviour rather
     * than using the reconnect option for automatically handling
     * that.
     */
    QPID_MESSAGING_EXTERN void reconnect(const std::string& url);
    /**
     * Attempts to reconnect to the original url, including any
     * specified reconnect_urls, re-establish existing sessions,
     * senders and receivers and resend any indoubt messages.
     *
     * This can be used to directly control reconnect behaviour rather
     * than using the reconnect option for automatically handling
     * that.
     */
    QPID_MESSAGING_EXTERN void reconnect();
    /**
     * returns a url reprsenting the broker the client is currently
     * connected to (or an empty string if it is not connected).
     */
    QPID_MESSAGING_EXTERN std::string getUrl() const;

    /**
     * Closes a connection and all sessions associated with it. An
     * opened connection must be closed before the last handle is
     * allowed to go out of scope.
     */
    QPID_MESSAGING_EXTERN void close();
    QPID_MESSAGING_EXTERN Session createTransactionalSession(const std::string& name = std::string());
    QPID_MESSAGING_EXTERN Session createSession(const std::string& name = std::string());

    QPID_MESSAGING_EXTERN Session getSession(const std::string& name) const;
    QPID_MESSAGING_EXTERN std::string getAuthenticatedUsername();

#ifndef SWIG
  private:
  friend class qpid::messaging::PrivateImplRef<Connection>;
#endif
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_CONNECTION_H*/
