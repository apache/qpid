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
#include "amqp_types.h"
#include "Buffer.h"

#ifndef _HeaderProperties_
#define _HeaderProperties_

namespace qpid {
namespace framing {

    enum header_classes{BASIC = 60};

    class HeaderProperties
    {
	
    public:
	inline virtual ~HeaderProperties(){}
	virtual u_int8_t classId() = 0;
	virtual u_int32_t size() const = 0;
	virtual void encode(Buffer& buffer) const = 0;
	virtual void decode(Buffer& buffer, u_int32_t size) = 0;
    };
}
}


#endif
