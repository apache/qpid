#ifndef _sys_SslCredential
#define _sys_SslCredential
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

#include "qpid/CommonImportExport.h"

#include <string.h>
// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32

namespace qpid {
namespace sys {
namespace windows {

/*
 * Manage certificate data structures for SChannel.
 *
 * Note on client certificates: The Posix/NSS implementation performs a lazy
 * client certificate search part way through the ssl handshake if the server
 * requests one.  Here, it is not known in advance if the server will
 * request the certificate so the certificate is pre-loaded (even if never
 * used).  To match the Linux behavior, client certificate load problems are
 * remembered and reported later if appropriate, but do not prevent the
 * connection attempt.
 */

class SslCredential {
public:
    QPID_COMMON_EXTERN SslCredential();
    QPID_COMMON_EXTERN ~SslCredential();
    QPID_COMMON_EXTERN bool load(const std::string& certName);
    QPID_COMMON_EXTERN CredHandle handle();
    QPID_COMMON_EXTERN std::string error();
    /** Proceed with connect inspite of hostname verifcation failures*/
    QPID_COMMON_EXTERN void ignoreHostnameVerificationFailure();

private:
    struct SavedError {
        std::string logMessage;
        std::string error;
        void set(const std::string &lm, const std::string es);
        void set(const std::string &lm, int status);
        void clear();
        bool pending();
    };

    HCERTSTORE certStore;
    PCCERT_CONTEXT cert;
    SCHANNEL_CRED cred;
    CredHandle credHandle;
    TimeStamp credExpiry;
    SavedError loadError;
    bool hostnameVerification;

    PCCERT_CONTEXT findCertificate(const std::string& name);
    void loadPrivCertStore();
    std::string getPasswd(const std::string& filename);
};

}}}

#endif // _sys_SslCredential
