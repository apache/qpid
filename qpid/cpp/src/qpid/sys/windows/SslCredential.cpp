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


#include <string>
#include <windows.h>
#include "qpid/Msg.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/util.h"
#include "qpid/sys/windows/SslCredential.h"


namespace qpid {
namespace sys {
namespace windows {


SslCredential::SslCredential() : certStore(0), cert(0), hostnameVerification(true)
{
    SecInvalidateHandle(&credHandle);
    memset(&cred, 0, sizeof(cred));
    cred.dwVersion = SCHANNEL_CRED_VERSION;
    cred.dwFlags = SCH_CRED_NO_DEFAULT_CREDS;
}

SslCredential::~SslCredential()
{
    if (SecIsValidHandle(&credHandle))
        ::FreeCredentialsHandle(&credHandle);
    if (cert)
        ::CertFreeCertificateContext(cert);
    if (certStore)
        ::CertCloseStore(certStore, CERT_CLOSE_STORE_FORCE_FLAG);
}

bool SslCredential::load(const std::string& certName)
{
    cert = findCertificate(certName);
    if (cert != NULL) {
        // assign the certificate into the credentials
        cred.paCred = &cert;
        cred.cCreds = 1;
    }
    if (!hostnameVerification)
        cred.dwFlags |= SCH_CRED_NO_SERVERNAME_CHECK;

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

    return (cert != NULL);
}

CredHandle SslCredential::handle()
{
    return credHandle;
}

std::string SslCredential::error()
{
    // Certificate needed after all.  Return main error and log additional context
    if (!loadError.logMessage.empty())
        QPID_LOG(warning, loadError.logMessage);
    return loadError.error;
}

void SslCredential::ignoreHostnameVerificationFailure(){
    hostnameVerification = false;
}

void SslCredential::loadPrivCertStore()
{
    //  Get a handle to the system store or pkcs#12 file
    qpid::sys::ssl::SslOptions& opts = qpid::sys::ssl::SslOptions::global;
    if (opts.certFilename.empty()) {
        // opening a system store, names are not case sensitive
        std::string store = opts.certStore.empty() ? "my" : opts.certStore;
        std::transform(store.begin(), store.end(), store.begin(), ::tolower);
        // map confusing GUI name to actual registry store name
        if (store == "personal")
            store = "my";
        certStore = ::CertOpenStore(CERT_STORE_PROV_SYSTEM_A, 0, NULL,
                          CERT_STORE_OPEN_EXISTING_FLAG | CERT_STORE_READONLY_FLAG |
                          CERT_SYSTEM_STORE_CURRENT_USER, store.c_str());
        if (!certStore) {
            HRESULT status = GetLastError();
            loadError.set(Msg() << "Could not open system certificate store: " << store, status);
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
            loadError.set(Msg() << "Failed to open the file holding the private key: " << opts.certFilename, status);
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
                loadError.set(Msg() << "Reading the private key from file failed " << opts.certFilename, status);
                return;
            }
        }
        else {
            HRESULT status = GetLastError();
            loadError.set(Msg() << "Unable to read the certificate file " << opts.certFilename, status);
            return;
        }
        CloseHandle(certFileHandle);

        CRYPT_DATA_BLOB blobData;
        blobData.cbData = certEncodedSize;
        blobData.pbData = &certEncoded[0];

        // get passwd from file and convert to null terminated wchar_t (Windows UCS2)
        std::string passwd = getPasswd(opts.certPasswordFile);
        if (loadError.pending())
            return;
        int pwlen = passwd.length();
        std::vector<wchar_t> pwUCS2(pwlen + 1, L'\0');
        int nwc = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, passwd.data(), pwlen, &pwUCS2[0], pwlen);
        if (!nwc) {
            HRESULT status = GetLastError();
            loadError.set("Error converting password from UTF8", status);
            return;
        }

        certStore = PFXImportCertStore(&blobData, &pwUCS2[0], 0);
        if (certStore == NULL) {
            HRESULT status = GetLastError();
            loadError.set("Failed to open the certificate store", status);
            return;
        }
        QPID_LOG(debug, "SslConnector using certificate from pkcs#12 file: " << opts.certFilename);
    }
}


PCCERT_CONTEXT SslCredential::findCertificate(const std::string& name)
{
        loadPrivCertStore();
        if (loadError.pending())
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
            loadError.set(Msg() << "Client SSL/TLS certificate not found in the certificate store for name " << name,
                                "client certificate not found");
        }
        return tmpctx;
}


std::string SslCredential::getPasswd(const std::string& filename)
{
    std::string passwd;
    if (filename == "")
        return passwd;

    HANDLE pwfHandle = CreateFile(filename.c_str(), GENERIC_READ, 0, NULL, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, NULL);

    if (INVALID_HANDLE_VALUE == pwfHandle) {
        HRESULT status = GetLastError();
        loadError.set(Msg() << "Failed to open the password file: " << filename, status);
        return passwd;
    }

    const DWORD fileSize = GetFileSize(pwfHandle, NULL);
    if (fileSize == INVALID_FILE_SIZE) {
        CloseHandle(pwfHandle);
        loadError.set("", "Cannot read password file");
        return passwd;
    }

    std::vector<char> pwbuf;
    pwbuf.resize(fileSize);
    DWORD nbytes = 0;
    if (!ReadFile(pwfHandle, &pwbuf[0], fileSize, &nbytes, NULL)) {
        HRESULT status = GetLastError();
        CloseHandle(pwfHandle);
        loadError.set("Error reading password file", status);
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

void SslCredential::SavedError::set(const std::string &lm, const std::string es) {
    logMessage = lm;
    error = es;
}

void SslCredential::SavedError::set(const std::string &lm, int status) {
    logMessage = lm;
    error = qpid::sys::strError(status);
}

void SslCredential::SavedError::clear() {
    logMessage.clear();
    error.clear();
}

bool SslCredential::SavedError::pending() {
    return !logMessage.empty() || !error.empty();
}

}}}
