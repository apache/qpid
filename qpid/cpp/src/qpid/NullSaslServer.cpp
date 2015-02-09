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
#include "NullSaslServer.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SecurityLayer.h"
#include <assert.h>
#include <boost/format.hpp>

namespace qpid {
NullSaslServer::NullSaslServer(const std::string& r) : realm(r) {}
NullSaslServer::Status NullSaslServer::start(const std::string& mechanism, const std::string* response, std::string& /*challenge*/)
{
    if (mechanism == "PLAIN") {
        if (response) {
            std::string uid;
            std::string::size_type i = response->find((char)0);
            if (i == 0 && response->size() > 1) {
                //no authorization id; use authentication id
                i = response->find((char)0, 1);
                if (i != std::string::npos) uid = response->substr(1, i-1);
            } else if (i != std::string::npos) {
                //authorization id is first null delimited field
                uid = response->substr(0, i);
            } else {
                QPID_LOG(error, "Invalid PLAIN request, null delimiter not found in response data");
                return FAIL;
            }
            if (!uid.empty()) {
                //append realm if it has not already been added
                i = uid.find(realm);
                if (i == std::string::npos || realm.size() + i < uid.size()) {
                    uid = boost::str(boost::format("%1%@%2%") % uid % realm);
                }
                userid = uid;
            }
            return OK;
        } else {
            QPID_LOG(error, "Invalid PLAIN request, expected response containing user credentials");
            return FAIL;
        }
    } else if (mechanism == "ANONYMOUS") {
        userid = "anonymous";
        return OK;
    } else {
        return FAIL;
    }
}

NullSaslServer::Status NullSaslServer::step(const std::string* /*response*/, std::string& /*challenge*/)
{
    return FAIL;
}
std::string NullSaslServer::getMechanisms()
{
    return std::string("ANONYMOUS PLAIN");
}
std::string NullSaslServer::getUserid()
{
    return userid;
}

std::auto_ptr<qpid::sys::SecurityLayer> NullSaslServer::getSecurityLayer(size_t)
{
    return std::auto_ptr<qpid::sys::SecurityLayer>();
}

} // namespace qpid
