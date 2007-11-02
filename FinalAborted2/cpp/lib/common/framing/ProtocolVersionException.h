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

namespace qpid
{
namespace framing
{

class ProtocolVersionException : virtual public qpid::Exception
{
protected:
    ProtocolVersion versionFound;
     
public:
    ProtocolVersionException() throw ();
    ProtocolVersionException(const std::string& str) throw ();
    ProtocolVersionException(const char* str) throw ();
	ProtocolVersionException(const ProtocolVersion& versionFound_, const std::string& str) throw ();
	ProtocolVersionException(const ProtocolVersion& versionFound_, const char* str) throw ();
    virtual ~ProtocolVersionException() throw ();
      
    virtual const char* what() const throw();
    virtual std::string toString() const throw();
}; // class ProtocolVersionException

} // namespace framing
} // namespace qpid

#endif //ifndef _ProtocolVersionException_
