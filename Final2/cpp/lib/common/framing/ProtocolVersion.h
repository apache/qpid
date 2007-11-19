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
#ifndef _ProtocolVersion_
#define _ProtocolVersion_

#include <amqp_types.h>

namespace qpid
{
namespace framing
{

class ProtocolVersion
{
private:
    u_int8_t major_;
	u_int8_t minor_;
    
public:
    ProtocolVersion();
    ProtocolVersion(u_int8_t _major, u_int8_t _minor);
    ProtocolVersion(const ProtocolVersion& p);
    virtual ~ProtocolVersion();

    inline u_int8_t getMajor() const { return major_; }
    inline void setMajor(u_int8_t major) { major_ = major; }
    inline u_int8_t getMinor() const { return minor_; }
    inline void setMinor(u_int8_t minor) { minor_ = minor; }
    virtual bool equals(u_int8_t _major, u_int8_t _minor) const;
    virtual bool equals(const ProtocolVersion& p) const;
    virtual const std::string toString() const;
    ProtocolVersion operator=(const ProtocolVersion& p);
};

} // namespace framing
} // namespace qpid


#endif // ifndef _ProtocolVersion_
