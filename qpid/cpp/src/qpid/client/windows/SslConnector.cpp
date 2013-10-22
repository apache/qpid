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

namespace qpid {
namespace client {
namespace windows {

using qpid::sys::Socket;

class SslConnector : public qpid::client::TCPConnector
{
    qpid::sys::windows::ClientSslAsynchIO *shim;
    boost::shared_ptr<qpid::sys::Poller> poller;
    std::string brokerHost;
    HCERTSTORE certStore;
    PCCERT_CONTEXT cert;
    SCHANNEL_CRED cred;
    CredHandle credHandle;
    TimeStamp credExpiry;

    virtual ~SslConnector();
    void negotiationDone(SECURITY_STATUS status);

    void connect(const std::string& host, const std::string& port);
    void connected(const Socket&);
    void loadPrivCertStore();
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

    std::string getPasswd(const std::string& filename);
    PCCERT_CONTEXT findCertificate(const std::string& name, HCERTSTORE& store);
}

void SslConnector::negotiationDone(SECURITY_STATUS status)
{
    if (status == SEC_E_OK)
        initAmqp();
    else
        connectFailed(QPID_MSG(qpid::sys::strError(status)));
}

SslConnector::SslConnector(boost::shared_ptr<qpid::sys::Poller> p,
                           framing::ProtocolVersion ver,
                           const ConnectionSettings& settings,
                           ConnectionImpl* cimpl)
    : TCPConnector(p, ver, settings, cimpl), shim(0), poller(p)
{
    memset(&cred, 0, sizeof(cred));
    cred.dwVersion = SCHANNEL_CRED_VERSION;

    // In case EXTERNAL SASL mechanism has been selected, we need to find
    // the client certificate with the private key which should be used
    if (settings.mechanism == std::string("EXTERNAL"))  {
        const std::string& name = (settings.sslCertName != "") ?
            settings.sslCertName : qpid::sys::ssl::SslOptions::global.certName;
        loadPrivCertStore();
        cert = findCertificate(name, certStore);
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
    if (cert)
        ::CertFreeCertificateContext(cert);
    ::CertCloseStore(certStore, CERT_CLOSE_STORE_FORCE_FLAG);
    ::FreeCredentialsHandle(&credHandle);
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
            QPID_LOG(warning, "Could not open system certificate store: " << store);
            throw QPID_WINDOWS_ERROR(status);
        }
        QPID_LOG(debug, "SslConnector using certifcates from system store: " << store);
    } else {
        // opening the store from file and populating it with a private key
        HANDLE certFileHandle = NULL;
        certFileHandle = CreateFile(opts.certFilename.c_str(), GENERIC_READ, 0, NULL, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, NULL);
        if (INVALID_HANDLE_VALUE == certFileHandle) {
            HRESULT status = GetLastError();
            QPID_LOG(warning, "Failed to open the file holding the private key: " << opts.certFilename);
            throw QPID_WINDOWS_ERROR(status);
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
                QPID_LOG(warning, "Reading the private key from file failed " << opts.certFilename);
                CloseHandle(certFileHandle);
                throw QPID_WINDOWS_ERROR(status);
            }
        }
        else {
            HRESULT status = GetLastError();
            QPID_LOG(warning, "Unable to read the certificate file " << opts.certFilename);
            throw QPID_WINDOWS_ERROR(status);
        }
        CloseHandle(certFileHandle);

        CRYPT_DATA_BLOB blobData;
        blobData.cbData = certEncodedSize;
        blobData.pbData = &certEncoded[0];

        // get passwd from file and convert to null terminated wchar_t (Windows UCS2)
        std::string passwd = getPasswd(opts.certPasswordFile);
        int pwlen = passwd.length();
        std::vector<wchar_t> pwUCS2(pwlen + 1, L'\0');
        int nwc = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, passwd.data(), pwlen, &pwUCS2[0], pwlen);
        if (!nwc) {
            HRESULT status = GetLastError();
            QPID_LOG(warning, "Error converting password from UTF8");
            throw QPID_WINDOWS_ERROR(status);
        }

        certStore = PFXImportCertStore(&blobData, &pwUCS2[0], 0);
        if (certStore == NULL) {
            HRESULT status = GetLastError();
            QPID_LOG(warning, "Failed to open the certificate store");
            throw QPID_WINDOWS_ERROR(status);
        }
        QPID_LOG(debug, "SslConnector using certificate from pkcs#12 file: " << opts.certFilename);
    }
}


namespace {

PCCERT_CONTEXT findCertificate(const std::string& name, HCERTSTORE& store)
{
        // search for the certificate by Friendly Name
        PCCERT_CONTEXT tmpctx = NULL;
        while (tmpctx = CertEnumCertificatesInStore(store, tmpctx)) {
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
            QPID_LOG(warning, "Certificate not found in the certificate store for name " << name);
            throw qpid::Exception(QPID_MSG("client certificate not found"));
        }
        return tmpctx;
}


std::string getPasswd(const std::string& filename)
{
    std::string passwd;
    if (filename == "")
        return passwd;

    HANDLE pwfHandle = CreateFile(filename.c_str(), GENERIC_READ, 0, NULL, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, NULL);

    if (INVALID_HANDLE_VALUE == pwfHandle) {
        HRESULT status = GetLastError();
        QPID_LOG(warning, "Failed to open the password file: " << filename);
        throw QPID_WINDOWS_ERROR(status);
    }

    const DWORD fileSize = GetFileSize(pwfHandle, NULL);
    if (fileSize == INVALID_FILE_SIZE) {
        CloseHandle(pwfHandle);
        throw qpid::Exception(QPID_MSG("Cannot read password file"));
    }

    std::vector<char> pwbuf;
    pwbuf.resize(fileSize);
    DWORD nbytes = 0;
    if (!ReadFile(pwfHandle, &pwbuf[0], fileSize, &nbytes, NULL)) {
        HRESULT status = GetLastError();
        CloseHandle(pwfHandle);
        QPID_LOG(warning, "Error reading password file");
        throw QPID_WINDOWS_ERROR(status);
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
} // namespace

}}} // namespace qpid::client::windows
