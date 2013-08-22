#ifndef QPID_MESSAGING_CONNECTIONOPTIONS_H
#define QPID_MESSAGING_CONNECTIONOPTIONS_H

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
#include "qpid/messaging/ImportExport.h"

#include "qpid/client/ConnectionSettings.h"
#include <map>
#include <vector>

namespace qpid {
namespace types {
class Variant;
}
namespace messaging {

struct ConnectionOptions : qpid::client::ConnectionSettings
{
    std::vector<std::string> urls;
    bool replaceUrls;
    bool reconnect;
    double timeout;
    int32_t limit;
    double minReconnectInterval;
    double maxReconnectInterval;
    int32_t retries;
    bool reconnectOnLimitExceeded;
    std::string identifier;

    QPID_MESSAGING_EXTERN ConnectionOptions(const std::map<std::string, qpid::types::Variant>&);
    QPID_MESSAGING_EXTERN void set(const std::string& name, const qpid::types::Variant& value);
};
}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_CONNECTIONOPTIONS_H*/
