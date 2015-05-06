#ifndef QPID_SASLFACTORY_H
#define QPID_SASLFACTORY_H

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
#include "qpid/Sasl.h"
#include "qpid/sys/Mutex.h"
#include <memory>

namespace qpid {
class SaslServer;
/**
 * Factory for instances of the Sasl interface through which Sasl
 * support is provided to a ConnectionHandler.
 */
class SaslFactory
{
  public:
    QPID_COMMON_EXTERN std::auto_ptr<Sasl> create(const std::string & userName, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf, bool allowInteraction=true );
    QPID_COMMON_EXTERN std::auto_ptr<SaslServer> createServer(const std::string& realm, const std::string& service, bool encryptionRequired, const qpid::sys::SecuritySettings&);
    QPID_COMMON_EXTERN static SaslFactory& getInstance();
    QPID_COMMON_EXTERN ~SaslFactory();
  private:
    SaslFactory();
    static qpid::sys::Mutex lock;
    static std::auto_ptr<SaslFactory> instance;
};
} // namespace qpid

#endif  /*!QPID_SASLFACTORY_H*/
