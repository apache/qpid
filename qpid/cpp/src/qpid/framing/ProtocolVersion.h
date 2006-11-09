/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _ProtocolVersion_
#define _ProtocolVersion_

#include <qpid/framing/amqp_types.h>

namespace qpid
{
namespace framing
{

class ProtocolVersion
{
public:
    u_int8_t major_;
	u_int8_t minor_;
    
    ProtocolVersion();
    ProtocolVersion(u_int8_t _major, u_int8_t _minor);
    ProtocolVersion(const ProtocolVersion& p);
    virtual ~ProtocolVersion();
    
    virtual bool equals(u_int8_t _major, u_int8_t _minor) const;
    virtual bool equals(const ProtocolVersion& p) const;
    virtual const std::string toString() const;
    ProtocolVersion operator=(const ProtocolVersion& p);
};

} // namespace framing
} // namespace qpid


#endif // ifndef _ProtocolVersion_
