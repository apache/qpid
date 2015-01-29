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

#include "qpid/client/TCPConnector.h"

#include "config.h"
#include "qpid/Msg.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/util.h"
#include "qpid/sys/windows/SslAsynchIO.h"
#include "qpid/sys/windows/SslCredential.h"

#include <boost/bind.hpp>

#include <memory.h>
#include <winsock2.h>



namespace qpid {
namespace client {
namespace windows {

using qpid::sys::Socket;

class SslConnector : public qpid::client::TCPConnector
{
    qpid::sys::windows::ClientSslAsynchIO *shim;
    boost::shared_ptr<qpid::sys::Poller> poller;
    std::string brokerHost;
    qpid::sys::windows::SslCredential sslCredential;
    bool certLoaded;

    void negotiationDone(SECURITY_STATUS status);

    void connect(const std::string& host, const std::string& port);
    void connected(const Socket&);

public:
    SslConnector(boost::shared_ptr<qpid::sys::Poller>,
                 framing::ProtocolVersion pVersion,
                 const ConnectionSettings&,
                 ConnectionImpl*);
};

// Static constructor which registers connector here
namespace {
    Connector* create(boost::shared_ptr<qpid::sys::Poller> p,
                      framing::ProtocolVersion v,
                      const ConnectionSettings& s,
                      ConnectionImpl* c) {
        return new SslConnector(p, v, s, c);
    }

    struct StaticInit {
        StaticInit() {
            try {
                CommonOptions common("", "", QPIDC_CONF_FILE);
                qpid::sys::ssl::SslOptions options;
                common.parse(0, 0, common.clientConfig, true);
                options.parse (0, 0, common.clientConfig, true);
                Connector::registerFactory("ssl", &create);
                initWinSsl(options);
            } catch (const std::exception& e) {
                QPID_LOG(error, "Failed to initialise SSL connector: " << e.what());
            }
        };
        ~StaticInit() { }
    } init;

}

void SslConnector::negotiationDone(SECURITY_STATUS status)
{
    if (status == SEC_E_OK) {
        initAmqp();
    }
    else {
        if (status == SEC_E_INCOMPLETE_CREDENTIALS && !certLoaded) {
            // Server requested a client cert but we supplied none for the following reason:
            connectFailed(QPID_MSG(sslCredential.error()));
        }
        else
            connectFailed(QPID_MSG(qpid::sys::strError(status)));
    }
}

SslConnector::SslConnector(boost::shared_ptr<qpid::sys::Poller> p,
                           framing::ProtocolVersion ver,
                           const ConnectionSettings& settings,
                           ConnectionImpl* cimpl)
    : TCPConnector(p, ver, settings, cimpl), shim(0), poller(p)
{
    if (settings.sslIgnoreHostnameVerificationFailure) {
        sslCredential.ignoreHostnameVerificationFailure();
    }
    const std::string& name = (settings.sslCertName != "") ?
        settings.sslCertName : qpid::sys::ssl::SslOptions::global.certName;
    certLoaded = sslCredential.load(name);
    QPID_LOG(debug, "SslConnector created for " << ver.toString());
}

void SslConnector::connect(const std::string& host, const std::string& port) {
    brokerHost = host;
    TCPConnector::connect(host, port);
}

void SslConnector::connected(const Socket& s) {
    shim = new qpid::sys::windows::ClientSslAsynchIO(brokerHost,
                                                     s,
                                                     sslCredential.handle(),
                                                     boost::bind(&SslConnector::readbuff, this, _1, _2),
                                                     boost::bind(&SslConnector::eof, this, _1),
                                                     boost::bind(&SslConnector::disconnected, this, _1),
                                                     boost::bind(&SslConnector::socketClosed, this, _1, _2),
                                                     0, // nobuffs
                                                     boost::bind(&SslConnector::writebuff, this, _1),
                                                     boost::bind(&SslConnector::negotiationDone, this, _1));
    start(shim);
    shim->start(poller);
}

}}} // namespace qpid::client::windows
