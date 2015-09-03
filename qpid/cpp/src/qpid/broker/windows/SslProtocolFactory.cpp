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

#include "qpid/sys/TransportFactory.h"
#include "qpid/sys/SocketTransport.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/windows/SslAsynchIO.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>
#include <memory>

// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32


namespace qpid {
namespace sys {

class Timer;

namespace windows {

struct SslServerOptions : qpid::Options
{
    std::string certStore;
    std::string certStoreLocation;
    std::string certName;
    uint16_t port;
    bool clientAuth;

    SslServerOptions() : qpid::Options("SSL Options"),
                         certStore("My"),
                         certStoreLocation("CurrentUser"),
                         certName("localhost"),
                         port(5671),
                         clientAuth(false)
    {
        qpid::Address me;
        if (qpid::sys::SystemInfo::getLocalHostname(me))
            certName = me.host;

        addOptions()
            ("ssl-cert-store", optValue(certStore, "NAME"), "Local store name from which to obtain certificate")
            ("ssl-cert-store-location", optValue(certStoreLocation, "NAME"),
             "Local store name location for certificates ( CurrentUser | LocalMachine | CurrentService )")
            ("ssl-cert-name", optValue(certName, "NAME"), "Name of the certificate to use")
            ("ssl-port", optValue(port, "PORT"), "Port on which to listen for SSL connections")
            ("ssl-require-client-authentication", optValue(clientAuth),
             "Forces clients to authenticate in order to establish an SSL connection");
    }
};

class SslProtocolFactory : public qpid::sys::SocketAcceptor, public qpid::sys::TransportConnector {
    Timer& brokerTimer;
    uint32_t maxNegotiateTime;
    const bool tcpNoDelay;
    std::string brokerHost;
    const bool clientAuthSelected;
    std::auto_ptr<qpid::sys::AsynchAcceptor> acceptor;
    ConnectFailedCallback connectFailedCallback;
    CredHandle credHandle;

  public:
    SslProtocolFactory(const qpid::broker::Broker& broker, const SslServerOptions&, Timer& timer);
    ~SslProtocolFactory();

    void connect(sys::Poller::shared_ptr, const std::string& name, const std::string& host, const std::string& port,
                 sys::ConnectionCodec::Factory*,
                 ConnectFailedCallback failed);

  private:
    void connectFailed(const qpid::sys::Socket&,
                       int err,
                       const std::string& msg);
    void establishedIncoming(sys::Poller::shared_ptr, const qpid::sys::Socket&, sys::ConnectionCodec::Factory*);
    void establishedOutgoing(sys::Poller::shared_ptr, const qpid::sys::Socket&, sys::ConnectionCodec::Factory*, std::string& );
    void establishedCommon(sys::Poller::shared_ptr, sys::AsynchIOHandler*, sys::AsynchIO*, const qpid::sys::Socket&);
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
                boost::shared_ptr<SslProtocolFactory> protocol(new SslProtocolFactory(*broker, options, broker->getTimer()));
                uint16_t port =
                    protocol->listen(broker->getListenInterfaces(),
                                     options.port, broker->getConnectionBacklog(),
                                     &createSocket);
                QPID_LOG(notice, "Listening for SSL connections on TCP port " << port);
                broker->registerTransport("ssl", protocol, protocol, port);
            } catch (const std::exception& e) {
                QPID_LOG(error, "Failed to initialise SSL listener: " << e.what());
            }
        }
    }
} sslPlugin;

SslProtocolFactory::SslProtocolFactory(const qpid::broker::Broker& broker, const SslServerOptions& options, Timer& timer)
    : SocketAcceptor(broker.getTcpNoDelay(), false, broker.getMaxNegotiateTime(), timer,
                     boost::bind(&SslProtocolFactory::establishedIncoming, this, _1, _2, _3)),
      brokerTimer(timer),
      maxNegotiateTime(broker.getMaxNegotiateTime()),
      tcpNoDelay(broker.getTcpNoDelay()),
      clientAuthSelected(options.clientAuth) {

    // Make sure that certificate store is good before listening to sockets
    // to avoid having open and listening sockets when there is no cert store
    SecInvalidateHandle(&credHandle);

    // Get the certificate for this server.
    DWORD flags = 0;
    std::string certStoreLocation = options.certStoreLocation;
    std::transform(certStoreLocation.begin(), certStoreLocation.end(), certStoreLocation.begin(), ::tolower);
    if (certStoreLocation == "currentuser") {
        flags = CERT_SYSTEM_STORE_CURRENT_USER;
    } else if (certStoreLocation == "localmachine") {
        flags = CERT_SYSTEM_STORE_LOCAL_MACHINE;
    } else if (certStoreLocation == "currentservice") {
        flags = CERT_SYSTEM_STORE_CURRENT_SERVICE;
    } else {
        QPID_LOG(error, "Unrecognised SSL certificate store location: " << options.certStoreLocation
            << " - Using default location");
    }
    HCERTSTORE certStoreHandle;
    certStoreHandle = ::CertOpenStore(CERT_STORE_PROV_SYSTEM_A,
                                      X509_ASN_ENCODING,
                                      0,
                                      flags |
                                      CERT_STORE_READONLY_FLAG,
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

void SslProtocolFactory::establishedIncoming(sys::Poller::shared_ptr poller,
                                             const qpid::sys::Socket& s,
                                             sys::ConnectionCodec::Factory* f) {
    sys::AsynchIOHandler* async = new sys::AsynchIOHandler(s.getFullAddress(), f, false, false);

    sys::AsynchIO *aio =
        new qpid::sys::windows::ServerSslAsynchIO(
            clientAuthSelected,
            s,
            credHandle,
            boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
            boost::bind(&AsynchIOHandler::eof, async, _1),
            boost::bind(&AsynchIOHandler::disconnect, async, _1),
            boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
            boost::bind(&AsynchIOHandler::nobuffs, async, _1),
            boost::bind(&AsynchIOHandler::idle, async, _1));

	establishedCommon(poller, async, aio, s);
}

void SslProtocolFactory::establishedOutgoing(sys::Poller::shared_ptr poller,
                                             const qpid::sys::Socket& s,
                                             sys::ConnectionCodec::Factory* f,
                                             std::string& name) {
    sys::AsynchIOHandler* async = new sys::AsynchIOHandler(name, f, true, false);

    sys::AsynchIO *aio =
        new qpid::sys::windows::ClientSslAsynchIO(
            brokerHost,
            s,
            credHandle,
            boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
            boost::bind(&AsynchIOHandler::eof, async, _1),
            boost::bind(&AsynchIOHandler::disconnect, async, _1),
            boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
            boost::bind(&AsynchIOHandler::nobuffs, async, _1),
            boost::bind(&AsynchIOHandler::idle, async, _1));

	establishedCommon(poller, async, aio, s);
}

void SslProtocolFactory::establishedCommon(sys::Poller::shared_ptr poller,
                                           sys::AsynchIOHandler* async,
                                           sys::AsynchIO* aio,
                                           const qpid::sys::Socket& s) {
    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(debug, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    async->init(aio, brokerTimer, maxNegotiateTime);
    aio->start(poller);
}

void SslProtocolFactory::connect(sys::Poller::shared_ptr poller,
                                 const std::string& name,
                                 const std::string& host,
                                 const std::string& port,
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
    qpid::sys::Socket* socket = createSocket();
    connectFailedCallback = failed;
    AsynchConnector::create(*socket,
                            host,
                            port,
                            boost::bind(&SslProtocolFactory::establishedOutgoing,
                                        this, poller, _1, fact, name),
                            boost::bind(&SslProtocolFactory::connectFailed,
                                        this, _1, _2, _3));
}

}}}
