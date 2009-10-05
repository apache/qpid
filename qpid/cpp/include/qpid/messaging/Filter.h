#ifndef QPID_MESSAGING_FILTER_H
#define QPID_MESSAGING_FILTER_H

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
#include <vector>
#include "qpid/client/ClientImportExport.h"

namespace qpid {
namespace client {
}

namespace messaging {

struct Filter
{
    std::string type;
    std::vector<std::string> patterns;

    QPID_CLIENT_EXTERN Filter(std::string type, std::string pattern);
    QPID_CLIENT_EXTERN Filter(std::string type, std::string pattern1, std::string pattern2);

    static QPID_CLIENT_EXTERN const std::string WILDCARD;
    static QPID_CLIENT_EXTERN const std::string EXACT_MATCH;
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_FILTER_H*/
