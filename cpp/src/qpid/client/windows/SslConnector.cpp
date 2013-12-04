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

#include <boost/bind.hpp>

#include <memory.h>
// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32
#include <winsock2.h>

/*
 * Note on client certificates: The Posix/NSS implementation performs a lazy
 * client certificate search part way through the ssl handshake if the server
 * requests one.  Here, it is not known in advance if the server will
 * request the certificate so the certificate is pre-loaded (even if never
 * used).  To match the Linux behavior, client certificate load problems are
 * remembered and reported later if appropriate, but do not prevent the
 * connection attempt.
 */

namespace qpid {
namespace client {
namespace windows {

using qpid::sys::Socket;

class SslConnector : public qpid::client::TCPConnector
{
    struct SavedError {
        std::string logMessage;
        std::string error;
        void set(const std::string &lm, const std::string es);
        void set(const std::string &lm, int status);
        void clear();
        bool pending();
    };

    qpid::sys::windows::ClientSslAsynchIO *shim;
    boost::shared_ptr<qpid::sys::Poller> poller;
    std::string brokerHost;
    HCERTSTORE certStore;
    PCCERT_CONTEXT cert;
    SCHANNEL_CRED cred;
    CredHandle credHandle;
    TimeStamp credExpiry;
    SavedError clientCertError;

    virtual ~SslConnector();
    void negotiationDone(SECURITY_STATUS status);

    void connect(const std::string& host, const std::string& port);
    void connected(const Socket&);
    PCCERT_CONTEXT findCertificate(const std::string& name);
    void loadPrivCertStore();
    std::string getPasswd(const std::string& filename);
    void importHostCert(const ConnectionSettings&);

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
        clientCertError.clear();
        initAmqp();
    }
    else {
        if (status == SEC_E_INCOMPLETE_CREDENTIALS && clientCertError.pending()) {
            // Server requested a client cert but we supplied none for the following reason:
            if (!clientCertError.logMessage.empty())
                QPID_LOG(warning, clientCertError.logMessage);
            connectFailed(QPID_MSG(clientCertError.error));
        }
        else
            connectFailed(QPID_MSG(qpid::sys::strError(status)));
    }
}

SslConnector::SslConnector(boost::shared_ptr<qpid::sys::Poller> p,
                           framing::ProtocolVersion ver,
                           const ConnectionSettings& settings,
                           ConnectionImpl* cimpl)
    : TCPConnector(p, ver, settings, cimpl), shim(0), poller(p), certStore(0), cert(0)
{
    SecInvalidateHandle(&credHandle);
    memset(&cred, 0, sizeof(cred));
    cred.dwVersion = SCHANNEL_CRED_VERSION;
    cred.dwFlags = SCH_CRED_NO_DEFAULT_CREDS;

    const std::string& name = (settings.sslCertName != "") ?
        settings.sslCertName : qpid::sys::ssl::SslOptions::global.certName;
    cert = findCertificate(name);
    if (cert != NULL) {
        // assign the certificate into the credentials
        cred.paCred = &cert;
        cred.cCreds = 1;
    }

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
    if (SecIsValidHandle(&credHandle))
        ::FreeCredentialsHandle(&credHandle);
    if (cert)
        ::CertFreeCertificateContext(cert);
    if (certStore)
        ::CertCloseStore(certStore, CERT_CLOSE_STORE_FORCE_FLAG);
}

void SslConnector::connect(const std::string& host, const std::string& port) {
    brokerHost = host;
    TCPConnector::connect(host, port);
}

void SslConnector::connected(const Socket& s) {
    shim = new qpid::sys::windows::ClientSslAsynchIO(brokerHost,
                                                     s,
                                                     credHandle,
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


void SslConnector::loadPrivCertStore()
{
    //  Get a handle to the system store or pkcs#12 file
    qpid::sys::ssl::SslOptions& opts = qpid::sys::ssl::SslOptions::global;
    if (opts.certFilename.empty()) {
        // opening the system store
        const char *store = opts.certStore.empty() ? "MY" : opts.certStore.c_str();
        certStore = ::CertOpenStore(CERT_STORE_PROV_SYSTEM_A, 0, NULL,
                          CERT_STORE_OPEN_EXISTING_FLAG | CERT_STORE_READONLY_FLAG |
                          CERT_SYSTEM_STORE_CURRENT_USER, store);
        if (!certStore) {
            HRESULT status = GetLastError();
            clientCertError.set(Msg() << "Could not open system certificate store: " << store, status);
            return;
        }
        QPID_LOG(debug, "SslConnector using certifcates from system store: " << store);
    } else {
        // opening the store from file and populating it with a private key
        HANDLE certFileHandle = NULL;
        certFileHandle = CreateFile(opts.certFilename.c_str(), GENERIC_READ, 0, NULL, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, NULL);
        if (INVALID_HANDLE_VALUE == certFileHandle) {
            HRESULT status = GetLastError();
            clientCertError.set(Msg() << "Failed to open the file holding the private key: " << opts.certFilename, status);
            return;
        }
        std::vector<BYTE> certEncoded;
        DWORD certEncodedSize = 0L;
        const DWORD fileSize = GetFileSize(certFileHandle, NULL);
        if (INVALID_FILE_SIZE != fileSize) {
            certEncoded.resize(fileSize);
            bool result = false;
            result = ReadFile(certFileHandle, &certEncoded[0],
                fileSize,
                &certEncodedSize,
                NULL);
            if (!result) {
                // the read failed, return the error as an HRESULT
                HRESULT status = GetLastError();
                CloseHandle(certFileHandle);
                clientCertError.set(Msg() << "Reading the private key from file failed " << opts.certFilename, status);
                return;
            }
        }
        else {
            HRESULT status = GetLastError();
            clientCertError.set(Msg() << "Unable to read the certificate file " << opts.certFilename, status);
            return;
        }
        CloseHandle(certFileHandle);

        CRYPT_DATA_BLOB blobData;
        blobData.cbData = certEncodedSize;
        blobData.pbData = &certEncoded[0];

        // get passwd from file and convert to null terminated wchar_t (Windows UCS2)
        std::string passwd = getPasswd(opts.certPasswordFile);
        if (clientCertError.pending())
            return;
        int pwlen = passwd.length();
        std::vector<wchar_t> pwUCS2(pwlen + 1, L'\0');
        int nwc = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, passwd.data(), pwlen, &pwUCS2[0], pwlen);
        if (!nwc) {
            HRESULT status = GetLastError();
            clientCertError.set("Error converting password from UTF8", status);
            return;
        }

        certStore = PFXImportCertStore(&blobData, &pwUCS2[0], 0);
        if (certStore == NULL) {
            HRESULT status = GetLastError();
            clientCertError.set("Failed to open the certificate store", status);
            return;
        }
        QPID_LOG(debug, "SslConnector using certificate from pkcs#12 file: " << opts.certFilename);
    }
}


PCCERT_CONTEXT SslConnector::findCertificate(const std::string& name)
{
        loadPrivCertStore();
        if (clientCertError.pending())
            return NULL;

        // search for the certificate by Friendly Name
        PCCERT_CONTEXT tmpctx = NULL;
        while (tmpctx = CertEnumCertificatesInStore(certStore, tmpctx)) {
            DWORD len = CertGetNameString(tmpctx, CERT_NAME_FRIENDLY_DISPLAY_TYPE,
                                          0, NULL, NULL, 0);
            if (len == 1)
                continue;
            std::vector<char> ctxname(len);
            CertGetNameString(tmpctx, CERT_NAME_FRIENDLY_DISPLAY_TYPE,
                                          0, NULL, &ctxname[0], len);
            bool found = !name.compare(&ctxname[0]);
            if (found)
                break;
        }

        // verify whether some certificate has been found
        if (tmpctx == NULL) {
            clientCertError.set(Msg() << "Client SSL/TLS certificate not found in the certificate store for name " << name,
                                "client certificate not found");
        }
        return tmpctx;
}


std::string SslConnector::getPasswd(const std::string& filename)
{
    std::string passwd;
    if (filename == "")
        return passwd;

    HANDLE pwfHandle = CreateFile(filename.c_str(), GENERIC_READ, 0, NULL, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, NULL);

    if (INVALID_HANDLE_VALUE == pwfHandle) {
        HRESULT status = GetLastError();
        clientCertError.set(Msg() << "Failed to open the password file: " << filename, status);
        return passwd;
    }

    const DWORD fileSize = GetFileSize(pwfHandle, NULL);
    if (fileSize == INVALID_FILE_SIZE) {
        CloseHandle(pwfHandle);
        clientCertError.set("", "Cannot read password file");
        return passwd;
    }

    std::vector<char> pwbuf;
    pwbuf.resize(fileSize);
    DWORD nbytes = 0;
    if (!ReadFile(pwfHandle, &pwbuf[0], fileSize, &nbytes, NULL)) {
        HRESULT status = GetLastError();
        CloseHandle(pwfHandle);
        clientCertError.set("Error reading password file", status);
        return passwd;
    }
    CloseHandle(pwfHandle);

    if (nbytes == 0)
        return passwd;

    while (nbytes) {
        if ((pwbuf[nbytes-1] == 012) || (pwbuf[nbytes-1] == 015))
            nbytes--;
        else
            break;
    }

    if (nbytes)
        passwd.assign(&pwbuf[0], nbytes);

    return passwd;
}

void SslConnector::SavedError::set(const std::string &lm, const std::string es) {
    logMessage = lm;
    error = es;
}

void SslConnector::SavedError::set(const std::string &lm, int status) {
    logMessage = lm;
    error = qpid::sys::strError(status);
}

void SslConnector::SavedError::clear() {
    logMessage.clear();
    error.clear();
}

bool SslConnector::SavedError::pending() {
    return !logMessage.empty() || !error.empty();
}

}}} // namespace qpid::client::windows
