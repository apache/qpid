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
#include "qpid/sys/ssl/check.h"
#include <secerr.h>
#include <sslerr.h>
#include <boost/format.hpp>

using boost::format;
using boost::str;

namespace qpid {
namespace sys {
namespace ssl {

ErrorString::ErrorString() : code(PR_GetError()), buffer(new char[PR_GetErrorTextLength()]), used(PR_GetErrorText(buffer)) {}    

ErrorString::~ErrorString() 
{ 
    delete[] buffer; 
}

std::string ErrorString::getString() const
{
    std::string msg = std::string(buffer, used);
    if (!used) {
        //seems most of the NSPR/NSS errors don't have text set for
        //them, add a few specific ones in here. (TODO: more complete
        //list?):
        return getErrorString(code);
    } else {
        return str(format("%1% [%2%]") % msg % code);
    }
}

std::string getErrorString(int code)
{
    std::string msg;
    switch (code) {
      case SSL_ERROR_EXPORT_ONLY_SERVER: msg =  "Unable to communicate securely. Peer does not support high-grade encryption."; break;
      case SSL_ERROR_US_ONLY_SERVER: msg = "Unable to communicate securely. Peer requires high-grade encryption which is not supported."; break;
      case SSL_ERROR_NO_CYPHER_OVERLAP: msg = "Cannot communicate securely with peer: no common encryption algorithm(s)."; break;
      case SSL_ERROR_NO_CERTIFICATE: msg = "Unable to find the certificate or key necessary for authentication."; break;
      case SSL_ERROR_BAD_CERTIFICATE: msg = "Unable to communicate securely with peer: peers's certificate was rejected."; break;
      case SSL_ERROR_UNSUPPORTED_CERTIFICATE_TYPE: msg = "Unsupported certificate type."; break;
      case SSL_ERROR_WRONG_CERTIFICATE: msg = "Client authentication failed: private key in key database does not correspond to public key in certificate database."; break;
      case SSL_ERROR_BAD_CERT_DOMAIN: msg = "Unable to communicate securely with peer: requested domain name does not match the server's certificate."; break;
      case SSL_ERROR_BAD_CERT_ALERT: msg = "SSL peer cannot verify your certificate."; break;
      case SSL_ERROR_REVOKED_CERT_ALERT: msg = "SSL peer rejected your certificate as revoked."; break;
      case SSL_ERROR_EXPIRED_CERT_ALERT: msg = "SSL peer rejected your certificate as expired."; break;

      case PR_DIRECTORY_LOOKUP_ERROR: msg = "A directory lookup on a network address has failed"; break;
      case PR_CONNECT_RESET_ERROR: msg = "TCP connection reset by peer"; break;
      case PR_END_OF_FILE_ERROR: msg = "Encountered end of file"; break;
      case SEC_ERROR_EXPIRED_CERTIFICATE: msg = "Peer's certificate has expired"; break;
      default: msg = (code < -6000) ? "NSS error" : "NSPR error"; break;
    }
    return str(format("%1% [%2%]") % msg % code);
}

std::ostream& operator<<(std::ostream& out, const ErrorString& err)
{
    out << err.getString();
    return out;
}


}}} // namespace qpid::sys::ssl
