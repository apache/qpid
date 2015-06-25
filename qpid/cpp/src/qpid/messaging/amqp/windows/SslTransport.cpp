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

#include "qpid/messaging/amqp/TcpTransport.h"
#include "qpid/messaging/amqp/TransportContext.h"
#include "qpid/messaging/ConnectionOptions.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Poller.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <boost/format.hpp>

#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/util.h"
#include "qpid/sys/windows/SslAsynchIO.h"
#include "qpid/sys/windows/SslCredential.h"

using namespace qpid::sys;

namespace qpid {
namespace messaging {
namespace amqp {

class SslTransport : public TcpTransport
{
  public:
    SslTransport(TransportContext&, boost::shared_ptr<qpid::sys::Poller> p);

    void connect(const std::string& host, const std::string& port);
    void negotiationDone(SECURITY_STATUS status);
    const qpid::sys::SecuritySettings* getSecuritySettings();

  private:
    std::string brokerHost;
    qpid::sys::windows::SslCredential sslCredential;
    bool certLoaded;
    qpid::sys::SecuritySettings securitySettings;

    void connected(const qpid::sys::Socket&);
};

// Static constructor which registers connector here
namespace {
Transport* create(TransportContext& c, Poller::shared_ptr p)
{
    return new SslTransport(c, p);
}

struct StaticInit
{
    StaticInit()
    {
        Transport::add("ssl", &create);
    };
} init;
}


void SslTransport::negotiationDone(SECURITY_STATUS status)
{
    if (status == SEC_E_OK) {
        connector = 0;
        context.opened();
        id = boost::str(boost::format("[%1%]") % socket->getFullAddress());
    } else {
        if (status == SEC_E_INCOMPLETE_CREDENTIALS && !certLoaded) {
            // Server requested a client cert but we supplied none for the following reason:
            failed(QPID_MSG(sslCredential.error()));
        }
        else
            failed(QPID_MSG(qpid::sys::strError(status)));
    }
}

SslTransport::SslTransport(TransportContext& c, boost::shared_ptr<Poller> p) : TcpTransport(c, p)
{
    const ConnectionOptions* options = context.getOptions();
    if (options->sslIgnoreHostnameVerificationFailure) {
        sslCredential.ignoreHostnameVerificationFailure();
    }
    const std::string& name = (options->sslCertName != "") ?
        options->sslCertName : qpid::sys::ssl::SslOptions::global.certName;
    certLoaded = sslCredential.load(name);
    QPID_LOG(debug, "SslTransport created");
}

void SslTransport::connect(const std::string& host, const std::string& port)
{
    brokerHost = host;
    TcpTransport::connect(host, port);
}

void SslTransport::connected(const Socket& s)
{
    aio = new qpid::sys::windows::ClientSslAsynchIO(brokerHost,
                                                     s,
                                                     sslCredential.handle(),
                                                     boost::bind(&SslTransport::read, this, _1, _2),
                                                     boost::bind(&SslTransport::eof, this, _1),
                                                     boost::bind(&SslTransport::disconnected, this, _1),
                                                     boost::bind(&SslTransport::socketClosed, this, _1, _2),
                                                     0, // nobuffs
                                                     boost::bind(&SslTransport::write, this, _1),
                                                     boost::bind(&SslTransport::negotiationDone, this, _1));

    aio->createBuffers(std::numeric_limits<uint16_t>::max());//note: AMQP 1.0 _can_ handle large frame sizes
    aio->start(poller);
}

const qpid::sys::SecuritySettings* SslTransport::getSecuritySettings()
{
    securitySettings.ssf = socket->getKeyLen();
    securitySettings.authid = "dummy";//set to non-empty string to enable external authentication
    return &securitySettings;
}

}}} // namespace qpid::messaging::amqp
