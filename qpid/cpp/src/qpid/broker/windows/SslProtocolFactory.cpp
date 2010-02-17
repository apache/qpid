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

#include "qpid/sys/ProtocolFactory.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/windows/SslAsynchIO.h"
#include <boost/bind.hpp>
#include <memory>
// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32


namespace qpid {
namespace sys {
namespace windows {

struct SslServerOptions : qpid::Options
{
    std::string certStore;
    std::string certName;
    uint16_t port;
    bool clientAuth;

    SslServerOptions() : qpid::Options("SSL Options"),
                         certStore("My"), port(5671), clientAuth(false)
    {
        qpid::TcpAddress me;
        if (qpid::sys::SystemInfo::getLocalHostname(me))
            certName = me.host;
        else
            certName = "localhost";

        addOptions()
            ("ssl-cert-store", optValue(certStore, "NAME"), "Local store name from which to obtain certificate")
            ("ssl-cert-name", optValue(certName, "NAME"), "Name of the certificate to use")
            ("ssl-port", optValue(port, "PORT"), "Port on which to listen for SSL connections")
            ("ssl-require-client-authentication", optValue(clientAuth), 
             "Forces clients to authenticate in order to establish an SSL connection");
    }
};

class SslProtocolFactory : public qpid::sys::ProtocolFactory {
    qpid::sys::Socket listener;
    const bool tcpNoDelay;
    const uint16_t listeningPort;
    std::string brokerHost;
    const bool clientAuthSelected;
    std::auto_ptr<qpid::sys::AsynchAcceptor> acceptor;
    ConnectFailedCallback connectFailedCallback;
    CredHandle credHandle;

  public:
    SslProtocolFactory(const SslServerOptions&, int backlog, bool nodelay);
    ~SslProtocolFactory();
    void accept(sys::Poller::shared_ptr, sys::ConnectionCodec::Factory*);
    void connect(sys::Poller::shared_ptr, const std::string& host, int16_t port,
                 sys::ConnectionCodec::Factory*,
                 ConnectFailedCallback failed);

    uint16_t getPort() const;
    std::string getHost() const;
    bool supports(const std::string& capability);

  private:
    void connectFailed(const qpid::sys::Socket&,
                       int err,
                       const std::string& msg);
    void established(sys::Poller::shared_ptr,
                     const qpid::sys::Socket&,
                     sys::ConnectionCodec::Factory*,
                     bool isClient);
};

// Static instance to initialise plugin
static struct SslPlugin : public Plugin {
    SslServerOptions options;

    Options* getOptions() { return &options; }

    void earlyInitialize(Target&) {
    }
    
    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            try {
                const broker::Broker::Options& opts = broker->getOptions();
                ProtocolFactory::shared_ptr protocol(new SslProtocolFactory(options,
                                                                            opts.connectionBacklog,
                                                                            opts.tcpNoDelay));
                QPID_LOG(notice, "Listening for SSL connections on TCP port " << protocol->getPort());
                broker->registerProtocolFactory("ssl", protocol);
            } catch (const std::exception& e) {
                QPID_LOG(error, "Failed to initialise SSL listener: " << e.what());
            }
        }
    }
} sslPlugin;

SslProtocolFactory::SslProtocolFactory(const SslServerOptions& options,
                                       int backlog,
                                       bool nodelay)
    : tcpNoDelay(nodelay),
      listeningPort(listener.listen(options.port, backlog)),
      clientAuthSelected(options.clientAuth) {

    SecInvalidateHandle(&credHandle);

    // Get the certificate for this server.
    HCERTSTORE certStoreHandle;
    certStoreHandle = ::CertOpenStore(CERT_STORE_PROV_SYSTEM_A,
                                      X509_ASN_ENCODING,
                                      0,
                                      CERT_SYSTEM_STORE_LOCAL_MACHINE,
                                      options.certStore.c_str());
    if (!certStoreHandle)
        throw qpid::Exception(QPID_MSG("Opening store " << options.certStore << " " << qpid::sys::strError(GetLastError())));

    PCCERT_CONTEXT certContext;
    certContext = ::CertFindCertificateInStore(certStoreHandle,
                                               X509_ASN_ENCODING,
                                               0,
                                               CERT_FIND_SUBJECT_STR_A,
                                               options.certName.c_str(),
                                               NULL);
    if (certContext == NULL) {
        int err = ::GetLastError();
        ::CertCloseStore(certStoreHandle, 0);
        throw qpid::Exception(QPID_MSG("Locating certificate " << options.certName << " in store " << options.certStore << " " << qpid::sys::strError(GetLastError())));
        throw QPID_WINDOWS_ERROR(err);
    }

    SCHANNEL_CRED cred;
    memset(&cred, 0, sizeof(cred));
    cred.dwVersion = SCHANNEL_CRED_VERSION;
    cred.cCreds = 1;
    cred.paCred = &certContext;
    SECURITY_STATUS status = ::AcquireCredentialsHandle(NULL,
                                                        UNISP_NAME,
                                                        SECPKG_CRED_INBOUND,
                                                        NULL,
                                                        &cred,
                                                        NULL,
                                                        NULL,
                                                        &credHandle,
                                                        NULL);
    if (status != SEC_E_OK)
        throw QPID_WINDOWS_ERROR(status);
    ::CertFreeCertificateContext(certContext);
    ::CertCloseStore(certStoreHandle, 0);
}

SslProtocolFactory::~SslProtocolFactory() {
    ::FreeCredentialsHandle(&credHandle);
}

void SslProtocolFactory::connectFailed(const qpid::sys::Socket&,
                                       int err,
                                       const std::string& msg) {
    if (connectFailedCallback)
        connectFailedCallback(err, msg);
}

void SslProtocolFactory::established(sys::Poller::shared_ptr poller,
                                     const qpid::sys::Socket& s,
                                     sys::ConnectionCodec::Factory* f,
                                     bool isClient) {
    sys::AsynchIOHandler* async = new sys::AsynchIOHandler(s.getPeerAddress(), f);

    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(info,
                 "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    SslAsynchIO *aio;
    if (isClient) {
        async->setClient();
        aio =
          new qpid::sys::windows::ClientSslAsynchIO(brokerHost,
                                                    s,
                                                    credHandle,
                                                    boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                                    boost::bind(&AsynchIOHandler::eof, async, _1),
                                                    boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                                    boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                                    boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                                    boost::bind(&AsynchIOHandler::idle, async, _1));
    }
    else {
        aio =
          new qpid::sys::windows::ServerSslAsynchIO(clientAuthSelected,
                                                    s,
                                                    credHandle,
                                                    boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                                    boost::bind(&AsynchIOHandler::eof, async, _1),
                                                    boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                                    boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                                    boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                                    boost::bind(&AsynchIOHandler::idle, async, _1));
    }

    async->init(aio, 4);
    aio->start(poller);
}

uint16_t SslProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

std::string SslProtocolFactory::getHost() const {
    return listener.getSockname();
}

void SslProtocolFactory::accept(sys::Poller::shared_ptr poller,
                                sys::ConnectionCodec::Factory* fact) {
    acceptor.reset(
        AsynchAcceptor::create(listener,
                               boost::bind(&SslProtocolFactory::established, this, poller, _1, fact, false)));
    acceptor->start(poller);
}

void SslProtocolFactory::connect(sys::Poller::shared_ptr poller,
                                 const std::string& host,
                                 int16_t port,
                                 sys::ConnectionCodec::Factory* fact,
                                 ConnectFailedCallback failed)
{
    SCHANNEL_CRED cred;
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
                                                        NULL);
    if (status != SEC_E_OK)
        throw QPID_WINDOWS_ERROR(status);

    brokerHost = host;
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the AsynchConnector
    // upon connection failure or by the AsynchIO upon connection
    // shutdown.  The allocated AsynchConnector frees itself when it
    // is no longer needed.
    qpid::sys::Socket* socket = new qpid::sys::Socket();
    connectFailedCallback = failed;
    AsynchConnector::create(*socket,
                            host,
                            port,
                            boost::bind(&SslProtocolFactory::established,
                                        this, poller, _1, fact, true),
                            boost::bind(&SslProtocolFactory::connectFailed,
                                        this, _1, _2, _3));
}

namespace
{
const std::string SSL = "ssl";
}

bool SslProtocolFactory::supports(const std::string& capability)
{
    std::string s = capability;
    transform(s.begin(), s.end(), s.begin(), tolower);
    return s == SSL;
}

}}} // namespace qpid::sys::windows
