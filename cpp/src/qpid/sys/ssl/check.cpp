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

const std::string SSL_ERROR_BAD_CERT_DOMAIN_STR = 
    "Unable to communicate securely with peer: requested domain name does not match the server's certificate.";
const std::string SSL_ERROR_BAD_CERT_ALERT_STR = "SSL peer cannot verify your certificate.";
const std::string SEC_ERROR_BAD_DATABASE_STR = "Security library: bad database.";
const std::string SSL_ERROR_NO_CERTIFICATE_STR = "Unable to find the certificate or key necessary for authentication.";
const std::string PR_DIRECTORY_LOOKUP_ERROR_STR = "A directory lookup on a network address has failed";
const std::string PR_CONNECT_RESET_ERROR_STR = "TCP connection reset by peer";
const std::string PR_END_OF_FILE_ERROR_STR = "Encountered end of file";
const std::string SSL_ERROR_UNKNOWN = "NSS error";
const std::string NSPR_ERROR_UNKNOWN = "NSPR error";

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
      case SSL_ERROR_BAD_CERT_DOMAIN: msg = SSL_ERROR_BAD_CERT_DOMAIN_STR; break;
      case SSL_ERROR_BAD_CERT_ALERT: msg = SSL_ERROR_BAD_CERT_ALERT_STR; break;
      case SEC_ERROR_BAD_DATABASE: msg = SEC_ERROR_BAD_DATABASE_STR; break;
      case SSL_ERROR_NO_CERTIFICATE: msg = SSL_ERROR_NO_CERTIFICATE_STR; break;
      case PR_DIRECTORY_LOOKUP_ERROR: msg = PR_DIRECTORY_LOOKUP_ERROR_STR; break;
      case PR_CONNECT_RESET_ERROR: msg = PR_CONNECT_RESET_ERROR_STR; break;
      case PR_END_OF_FILE_ERROR: msg = PR_END_OF_FILE_ERROR_STR; break;
      default: msg = (code < -6000) ? SSL_ERROR_UNKNOWN : NSPR_ERROR_UNKNOWN; break;
    }
    return str(format("%1% [%2%]") % msg % code);
}

std::ostream& operator<<(std::ostream& out, const ErrorString& err)
{
    out << err.getString();
    return out;
}


}}} // namespace qpid::sys::ssl
