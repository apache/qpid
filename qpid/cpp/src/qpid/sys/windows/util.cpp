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
#include "qpid/sys/windows/util.h"
#include "qpid/Exception.h"
#include "qpid/sys/SystemInfo.h"

#include <iostream>
#include <fstream>

namespace qpid {
namespace sys {
namespace ssl {

static const std::string LOCALHOST("127.0.0.1");

std::string defaultCertName()
{
    Address address;
    if (SystemInfo::getLocalHostname(address)) {
        return address.host;
    } else {
        return LOCALHOST;
    }
}

SslOptions::SslOptions() : qpid::Options("SSL Settings"),
                           certName(defaultCertName())
{
    addOptions()
        ("ssl-cert-password-file", optValue(certPasswordFile, "PATH"), "File containing password to use for accessing certificates")
        ("ssl-cert-store", optValue(certStore, "NAME"), "Windows certificate store containing the certificate")
        ("ssl-cert-Filename", optValue(certFilename, "PATH"), "Path to PKCS#12 file containing the certificate")
        ("ssl-cert-name", optValue(certName, "NAME"), "Friendly Name of the certificate to use");
}

SslOptions& SslOptions::operator=(const SslOptions& o)
{
    certStore = o.certStore;
    certName = o.certName;
    certPasswordFile = o.certPasswordFile;
    certFilename = o.certFilename;

    return *this;
}

SslOptions SslOptions::global;

void initWinSsl(const SslOptions& options, bool)
{
    SslOptions::global = options;
}
}}} // namespace qpid::sys::ssl
