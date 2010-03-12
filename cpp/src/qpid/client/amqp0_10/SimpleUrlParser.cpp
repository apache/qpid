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
#include "SimpleUrlParser.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/Exception.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace client {
namespace amqp0_10 {

bool split(const std::string& in, char delim, std::pair<std::string, std::string>& result)
{
    std::string::size_type i = in.find(delim);
    if (i != std::string::npos) {
        result.first = in.substr(0, i);
        if (i+1 < in.size()) {
            result.second = in.substr(i+1);
        }
        return true;
    } else {
        return false;
    }
}

void parseUsernameAndPassword(const std::string& in, qpid::client::ConnectionSettings& result)
{
    std::pair<std::string, std::string> parts;
    if (!split(in, '/', parts)) {
        result.username = in;
    } else {
        result.username = parts.first;
        result.password = parts.second;
    }
}

void parseHostAndPort(const std::string& in, qpid::client::ConnectionSettings& result)
{
    std::pair<std::string, std::string> parts;
    if (!split(in, ':', parts)) {
        result.host = in;
    } else {
        result.host = parts.first;
        if (parts.second.size()) {
            result.port = boost::lexical_cast<uint16_t>(parts.second);
        }
    }
}

void SimpleUrlParser::parse(const std::string& url, qpid::client::ConnectionSettings& result)
{
    std::pair<std::string, std::string> parts;
    if (!split(url, '@', parts)) {
        parseHostAndPort(url, result);
    } else {
        parseUsernameAndPassword(parts.first, result);
        parseHostAndPort(parts.second, result);
    }
}

}}} // namespace qpid::client::amqp0_10
