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

#ifndef _ProtocolVersionException_
#define _ProtocolVersionException_

#include <Exception.h>
#include <ProtocolVersion.h>
#include <string>
#include <vector>

namespace qpid {
namespace framing {

class ProtocolVersionException : public qpid::Exception
{
protected:
    ProtocolVersion versionFound;
     
public:
    ~ProtocolVersionException() throw() {}

    template <class T>
    ProtocolVersionException(
        ProtocolVersion ver, const T& msg) throw () : versionFound(ver)
    { init(boost::lexical_cast<std::string>(msg)); }

    template <class T>
    ProtocolVersionException(const T& msg) throw () 
    { init(boost::lexical_cast<std::string>(msg)); }

  private:
    void init(const std::string& msg);
};

}} // namespace qpid::framing

#endif //ifndef _ProtocolVersionException_
