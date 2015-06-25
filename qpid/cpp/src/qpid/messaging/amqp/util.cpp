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
#include "util.h"
#include <sstream>

namespace qpid {
namespace messaging {
namespace amqp {

std::string get_error_string(pn_condition_t* error, const std::string& general, const std::string& delim)
{
    std::string name;
    std::stringstream text;
    if (pn_condition_is_set(error)) {
        name = pn_condition_get_name(error);
        text << general << delim << name;
        const char* desc = pn_condition_get_description(error);
        if (desc) text << ": " << desc;
    } else {
        text << general;
    }
    return text.str();
}

}}} // namespace qpid::messaging::amqp
