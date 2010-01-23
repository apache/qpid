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
#include "qpid/sys/windows/SslAsynchIO.h"

#include <iostream>
#include <boost/bind.hpp>
#include <boost/format.hpp>

#include <memory.h>
// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32
#include <winsock2.h>

namespace qpid {
namespace client {
namespace windows {

using namespace qpid::sys;
using boost::format;
using boost::str;


class SslConnector : public qpid::client::TCPConnector
{
    qpid::sys::windows::ClientSslAsynchIO *shim;
	boost::shared_ptr<qpid::sys::Poller> poller;
    std::string brokerHost;
    SCHANNEL_CRED cred;
    CredHandle credHandle;
    TimeStamp credExpiry;

    virtual ~SslConnector();
    void negotiationDone(SECURITY_STATUS status);

    // A number of AsynchIO callbacks go right through to TCPConnector, but
    // we can't boost::bind to a protected ancestor, so these methods redirect
    // to those TCPConnector methods.
    bool redirectReadbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase*);
    void redirectWritebuff(qpid::sys::AsynchIO&);
    void redirectEof(qpid::sys::AsynchIO&);

public:
    SslConnector(boost::shared_ptr<qpid::sys::Poller>,
                 framing::ProtocolVersion pVersion,
                 const ConnectionSettings&, 
                 ConnectionImpl*);
    virtual void connect(const std::string& host, int port);
    virtual void connected(const Socket&);
    unsigned int getSSF();
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
                Connector::registerFactory("ssl", &create);
            } catch (const std::exception& e) {
                QPID_LOG(error, "Failed to initialise SSL connector: " << e.what());
            }
        };
        ~StaticInit() { }
    } init;
}

void SslConnector::negotiationDone(SECURITY_STATUS status)
{
    if (status == SEC_E_OK)
        initAmqp();
    else
        connectFailed(QPID_MSG(qpid::sys::strError(status)));
}

bool SslConnector::redirectReadbuff(qpid::sys::AsynchIO& a,
                                    qpid::sys::AsynchIOBufferBase* b) {
    return readbuff(a, b);
}

void SslConnector::redirectWritebuff(qpid::sys::AsynchIO& a) {
    writebuff(a);
}

void SslConnector::redirectEof(qpid::sys::AsynchIO& a) {
    eof(a);
}

SslConnector::SslConnector(boost::shared_ptr<qpid::sys::Poller> p,
                           framing::ProtocolVersion ver,
                           const ConnectionSettings& settings,
                           ConnectionImpl* cimpl)
    : TCPConnector(p, ver, settings, cimpl), shim(0), poller(p)
{
    memset(&cred, 0, sizeof(cred));
    cred.dwVersion = SCHANNEL_CRED_VERSION;
    SECURITY_STATUS status = ::AcquireCredentialsHandle(NULL,
                                                        UNISP_NAME,
                                                        SECPKG_CRED_OUTBOUND,
                                                        NULL,
                                                        &cred,
                                                        NULL,
                                                        NULL,
                                                        &credHandle,
                                                        &credExpiry);
    if (status != SEC_E_OK)
        throw QPID_WINDOWS_ERROR(status);
    QPID_LOG(debug, "SslConnector created for " << ver.toString());
}

SslConnector::~SslConnector()
{
    ::FreeCredentialsHandle(&credHandle);
}

  // Will this get reach via virtual method via boost::bind????

void SslConnector::connect(const std::string& host, int port) {
    brokerHost = host;
    TCPConnector::connect(host, port);
}

void SslConnector::connected(const Socket& s) {
    shim = new qpid::sys::windows::ClientSslAsynchIO(brokerHost,
                                                     s,
                                                     credHandle,
                                                     boost::bind(&SslConnector::redirectReadbuff, this, _1, _2),
                                                     boost::bind(&SslConnector::redirectEof, this, _1),
                                                     boost::bind(&SslConnector::redirectEof, this, _1),
                                                     0, // closed
                                                     0, // nobuffs
                                                     boost::bind(&SslConnector::redirectWritebuff, this, _1),
                                                     boost::bind(&SslConnector::negotiationDone, this, _1));
    start(shim);
	shim->start(poller);
}

unsigned int SslConnector::getSSF()
{
    return shim->getSslKeySize();
}

}}} // namespace qpid::client::windows
