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
#include <ProtocolVersionException.h>
#include <sstream>

using namespace qpid::framing;

ProtocolVersionException::ProtocolVersionException() throw ()
{
}

ProtocolVersionException::ProtocolVersionException(const std::string& str) throw () : Exception(str)
{
}

ProtocolVersionException::ProtocolVersionException(const char* str) throw () : Exception(str)
{
}

ProtocolVersionException::ProtocolVersionException(const ProtocolVersion& versionFound_, const std::string& str) throw () : Exception(str)

{
    versionFound = versionFound_;
}

ProtocolVersionException::ProtocolVersionException(const ProtocolVersion& versionFound_, const char* str) throw () : Exception(str)

{
    versionFound = versionFound_;
}

ProtocolVersionException::~ProtocolVersionException() throw ()
{
}

const char* ProtocolVersionException::what() const throw()
{
    std::stringstream ss;
    ss << "ProtocolVersionException: AMQP Version " << versionFound.toString() << " found: " << whatStr;
    return ss.str().c_str();
}

std::string ProtocolVersionException::toString() const throw()
{
    std::stringstream ss;
    ss << "ProtocolVersionException: AMQP Version " << versionFound.toString() << " found: " << whatStr;
    return ss.str();
}
