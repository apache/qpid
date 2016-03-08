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

#include "qpid/sys/ssl/SslSocket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/ssl/check.h"
#include "qpid/sys/ssl/util.h"
#include "qpid/Exception.h"
#include "qpid/sys/posix/check.h"
#include "qpid/sys/posix/PrivatePosix.h"
#include "qpid/log/Statement.h"

#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/errno.h>
#include <poll.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <cstdlib>
#include <string.h>
#include <iostream>

#include <private/pprio.h>
#include <nss.h>
#include <pk11pub.h>
#include <ssl.h>
#include <key.h>
#include <sslerr.h>

namespace qpid {
namespace sys {
namespace ssl {

namespace {
const std::string DOMAIN_SEPARATOR("@");
const std::string DC_SEPARATOR(".");
const std::string DC("DC");
const std::string DN_DELIMS(" ,=");

std::string getDomainFromSubject(std::string subject)
{
    std::string::size_type last = subject.find_first_not_of(DN_DELIMS, 0);
    std::string::size_type i = subject.find_first_of(DN_DELIMS, last);

    std::string domain;
    bool nextTokenIsDC = false;
    while (std::string::npos != i || std::string::npos != last)
    {
        std::string token = subject.substr(last, i - last);
        if (nextTokenIsDC) {
            if (domain.size()) domain += DC_SEPARATOR;
            domain += token;
            nextTokenIsDC = false;
        } else if (token == DC) {
            nextTokenIsDC = true;
        }
        last = subject.find_first_not_of(DN_DELIMS, i);
        i = subject.find_first_of(DN_DELIMS, last);
    }
    return domain;
}

struct LocalCertificateGetter
{
    LocalCertificateGetter(PRFileDesc* nssSocket) : socket(nssSocket) {}
    CERTCertificate* operator()() const {return SSL_LocalCertificate(socket);}
    PRFileDesc* socket;
};
struct PeerCertificateGetter
{
    PeerCertificateGetter(PRFileDesc* nssSocket) : socket(nssSocket) {}
    CERTCertificate* operator()() const {return SSL_PeerCertificate(socket);}
    PRFileDesc* socket;
};
template<typename CertificateGetter>
std::string getAuthId(CertificateGetter certificateGetter)
{
    std::string authId;
    CERTCertificate* cert = certificateGetter();
    if (cert) {
        char *cn = CERT_GetCommonName(&(cert->subject));
        if (cn) {
            authId = std::string(cn);
            /*
             * The NSS function CERT_GetDomainComponentName only returns
             * the last component of the domain name, so we have to parse
             * the subject manually to extract the full domain.
             */
            std::string domain = getDomainFromSubject(cert->subjectName);
            if (!domain.empty()) {
                authId += DOMAIN_SEPARATOR;
                authId += domain;
            }
        }
        CERT_DestroyCertificate(cert);
    }
    return authId;
}
}

SslSocket::SslSocket(const std::string& certName, bool clientAuth) :
    nssSocket(0), certname(certName), prototype(0), hostnameVerification(true)
{
    //configure prototype socket:
    prototype = SSL_ImportFD(0, PR_NewTCPSocket());

    if (clientAuth) {
        NSS_CHECK(SSL_OptionSet(prototype, SSL_REQUEST_CERTIFICATE, PR_TRUE));
        NSS_CHECK(SSL_OptionSet(prototype, SSL_REQUIRE_CERTIFICATE, PR_TRUE));
    }
}

/**
 * This form of the constructor is used with the server-side sockets
 * returned from accept. Because we use posix accept rather than
 * PR_Accept, we have to reset the handshake.
 */
SslSocket::SslSocket(int fd, PRFileDesc* model) : BSDSocket(fd), nssSocket(0), prototype(0)
{
    nssSocket = SSL_ImportFD(model, PR_ImportTCPSocket(fd));
    NSS_CHECK(SSL_ResetHandshake(nssSocket, PR_TRUE));
}

void SslSocket::ignoreHostnameVerificationFailure()
{
    hostnameVerification = false;
}

void SslSocket::setNonblocking() const
{
    if (!nssSocket) {
        BSDSocket::setNonblocking();
        return;
    }
    PRSocketOptionData option;
    option.option = PR_SockOpt_Nonblocking;
    option.value.non_blocking = true;
    PR_SetSocketOption(nssSocket, &option);
}

void SslSocket::setTcpNoDelay() const
{
    if (!nssSocket) {
        BSDSocket::setTcpNoDelay();
        return;
    }
    PRSocketOptionData option;
    option.option = PR_SockOpt_NoDelay;
    option.value.no_delay = true;
    PR_SetSocketOption(nssSocket, &option);
}

void SslSocket::connect(const SocketAddress& addr) const
{
    BSDSocket::connect(addr);
}

namespace {
SECStatus bad_certificate(void* arg, PRFileDesc* /*fd*/) {
    switch (PR_GetError()) {
      case SSL_ERROR_BAD_CERT_DOMAIN:
        QPID_LOG(info, "Ignoring hostname verification failure for " << (const char*) arg);
        return SECSuccess;
      default:
        return SECFailure;
    }
}
}

void SslSocket::finishConnect(const SocketAddress& addr) const
{
    nssSocket = SSL_ImportFD(0, PR_ImportTCPSocket(fd));

    void* arg;
    // Use the connection's cert-name if it has one; else use global cert-name
    if (certname != "") {
        arg = const_cast<char*>(certname.c_str());
    } else if (SslOptions::global.certName.empty()) {
        arg = 0;
    } else {
        arg = const_cast<char*>(SslOptions::global.certName.c_str());
    }
    NSS_CHECK(SSL_GetClientAuthDataHook(nssSocket, NSS_GetClientAuthData, arg));

    url = addr.getHost();
    if (!hostnameVerification) {
        NSS_CHECK(SSL_BadCertHook(nssSocket, bad_certificate, const_cast<char*>(url.data())));
    }
    NSS_CHECK(SSL_SetURL(nssSocket, url.data()));

    NSS_CHECK(SSL_ResetHandshake(nssSocket, PR_FALSE));
    NSS_CHECK(SSL_ForceHandshake(nssSocket));
}

void SslSocket::close() const
{
    if (!nssSocket) {
        BSDSocket::close();
        return;
    }
    if (fd > 0) {
        PR_Close(nssSocket);
        fd = -1;
    }
}

int SslSocket::listen(const SocketAddress& sa, int backlog) const
{
    //get certificate and key (is this the correct way?)
    std::string cName( (certname == "") ? "localhost.localdomain" : certname);
    CERTCertificate *cert = PK11_FindCertFromNickname(const_cast<char*>(cName.c_str()), 0);
    if (!cert) throw Exception(QPID_MSG("Failed to load certificate '" << cName << "'"));
    SECKEYPrivateKey *key = PK11_FindKeyByAnyCert(cert, 0);
    if (!key) throw Exception(QPID_MSG("Failed to retrieve private key from certificate"));
    NSS_CHECK(SSL_ConfigSecureServer(prototype, cert, key, NSS_FindCertKEAType(cert)));
    SECKEY_DestroyPrivateKey(key);
    CERT_DestroyCertificate(cert);

    return BSDSocket::listen(sa, backlog);
}

Socket* SslSocket::accept() const
{
    QPID_LOG(trace, "Accepting SSL connection.");
    int afd = ::accept(fd, 0, 0);
    if ( afd >= 0) {
        return new SslSocket(afd, prototype);
    } else if (errno == EAGAIN) {
        return 0;
    } else {
        throw QPID_POSIX_ERROR(errno);
    }
}

#define SSL_STREAM_MAX_WAIT_ms 20
#define SSL_STREAM_MAX_RETRIES 2

static bool isSslStream(int afd) {
    int retries = SSL_STREAM_MAX_RETRIES;
    unsigned char buf[5] = {};

    do {
        struct pollfd fd = {afd, POLLIN, 0};

        /*
         * Note that this is blocking the accept thread, so connections that
         * send no data can limit the rate at which we can accept new
         * connections.
         */
        if (::poll(&fd, 1, SSL_STREAM_MAX_WAIT_ms) > 0) {
            errno = 0;
            int result = recv(afd, buf, sizeof(buf), MSG_PEEK | MSG_DONTWAIT);
            if (result == sizeof(buf)) {
                break;
            }
            if (errno && errno != EAGAIN) {
                int err = errno;
                ::close(afd);
                throw QPID_POSIX_ERROR(err);
            }
        }
    } while (retries-- > 0);

    if (retries < 0) {
        return false;
    }

    /*
     * SSLv2 Client Hello format
     * http://www.mozilla.org/projects/security/pki/nss/ssl/draft02.html
     *
     * Bytes 0-1: RECORD-LENGTH
     * Byte    2: MSG-CLIENT-HELLO (1)
     * Byte    3: CLIENT-VERSION-MSB
     * Byte    4: CLIENT-VERSION-LSB
     *
     * Allowed versions:
     * 2.0 - SSLv2
     * 3.0 - SSLv3
     * 3.1 - TLS 1.0
     * 3.2 - TLS 1.1
     * 3.3 - TLS 1.2
     *
     * The version sent in the Client-Hello is the latest version supported by
     * the client. NSS may send version 3.x in an SSLv2 header for
     * maximum compatibility.
     */
    bool isSSL2Handshake = buf[2] == 1 &&   // MSG-CLIENT-HELLO
        ((buf[3] == 3 && buf[4] <= 3) ||    // SSL 3.0 & TLS 1.0-1.2 (v3.1-3.3)
         (buf[3] == 2 && buf[4] == 0));     // SSL 2

    /*
     * SSLv3/TLS Client Hello format
     * RFC 2246
     *
     * Byte    0: ContentType (handshake - 22)
     * Bytes 1-2: ProtocolVersion {major, minor}
     *
     * Allowed versions:
     * 3.0 - SSLv3
     * 3.1 - TLS 1.0
     * 3.2 - TLS 1.1
     * 3.3 - TLS 1.2
     */
    bool isSSL3Handshake = buf[0] == 22 &&  // handshake
        (buf[1] == 3 && buf[2] <= 3);       // SSL 3.0 & TLS 1.0-1.2 (v3.1-3.3)

    return isSSL2Handshake || isSSL3Handshake;
}

SslMuxSocket::SslMuxSocket(const std::string& certName, bool clientAuth) :
    SslSocket(certName, clientAuth)
{
}

Socket* SslMuxSocket::accept() const
{
    int afd = ::accept(fd, 0, 0);
    if (afd >= 0) {
        QPID_LOG(trace, "Accepting connection with optional SSL wrapper.");
        if (isSslStream(afd)) {
            QPID_LOG(trace, "Accepted SSL connection.");
            return new SslSocket(afd, prototype);
        } else {
            QPID_LOG(trace, "Accepted Plaintext connection.");
            return new BSDSocket(afd);
        }
    } else if (errno == EAGAIN) {
        return 0;
    } else {
        throw QPID_POSIX_ERROR(errno);
    }
}

std::string SslSocket::lastErrorCodeText() const
{
  return getErrorString(lastErrorCode);
}

int SslSocket::read(void *buf, size_t count) const
{
    PRInt32 r = PR_Read(nssSocket, buf, count);
    lastErrorCode = PR_GetError();
    return r;
}

int SslSocket::write(const void *buf, size_t count) const
{
    PRInt32 r = PR_Write(nssSocket, buf, count);
    lastErrorCode = PR_GetError();
    return r;
}

void SslSocket::setCertName(const std::string& name)
{
    certname = name;
}


/** get the bit length of the current cipher's key */
int SslSocket::getKeyLen() const
{
    int enabled = 0;
    int keySize = 0;
    SECStatus   rc;

    rc = SSL_SecurityStatus( nssSocket,
                             &enabled,
                             NULL,
                             NULL,
                             &keySize,
                             NULL, NULL );
    if (rc == SECSuccess && enabled) {
        return keySize;
    }
    return 0;
}

std::string SslSocket::getPeerAuthId() const
{
    return getAuthId(PeerCertificateGetter(nssSocket));
}

std::string SslSocket::getLocalAuthId() const
{
    return getAuthId(LocalCertificateGetter(nssSocket));
}

}}} // namespace qpid::sys::ssl
