#ifndef QPID_CLIENT_CONNECTIONOPTIONS_H
#define QPID_CLIENT_CONNECTIONOPTIONS_H

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

#include "qpid/Options.h"
#include "qpid/client/ConnectionSettings.h"

namespace qpid {
namespace client {
/**
 * Options parser for ConnectionOptions.
 */
struct  ConnectionOptions : public qpid::Options,
                            public qpid::client::ConnectionSettings
{
    ConnectionOptions() : qpid::Options("Connection Settings")
    {
        using namespace qpid;
        addOptions()
            ("broker,b", optValue(host, "HOST"), "Broker host to connect to") 
            ("port,p", optValue(port, "PORT"), "Broker port to connect to")
            ("virtualhost,v", optValue(virtualhost, "VHOST"), "virtual host")
            ("clientname,n", optValue(clientid, "ID"), "unique client identifier")
            ("username", optValue(username, "USER"), "user name for broker log in.")
            ("password", optValue(password, "PASSWORD"), "password for broker log in.")
            ("mechanism", optValue(mechanism, "MECH"), "SASL mechanism to use when authenticating.")
            ("locale", optValue(locale, "LOCALE"), "locale to use.")
            ("max-channels", optValue(maxChannels, "N"), "the maximum number of channels the client requires.")
            ("max-frame-size", optValue(maxFrameSize, "N"), "the maximum frame size to request.")
            ("bounds-multiplier", optValue(bounds, "N"), 
             "bound size of write queue (as a multiple of the max frame size).");
    }
};

}}

#endif  /*!QPID_CLIENT_CONNECTIONOPTIONS_H*/
