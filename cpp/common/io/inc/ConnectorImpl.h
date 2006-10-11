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
#ifndef _APRConnectorImpl_
#define _APRConnectorImpl_

#ifdef _USE_APR_IO_
#include "APRConnector.h"
#else
#include "LConnector.h"
#endif

namespace qpid {
namespace io {

#ifdef _USE_APR_IO_
    class ConnectorImpl : public virtual APRConnector
    {
        
    public:
	ConnectorImpl(bool _debug = false, u_int32_t buffer_size = 1024):APRConnector(_debug,buffer_size){};
	virtual ~ConnectorImpl(){};
    };
#else
    class ConnectorImpl : public virtual LConnector
    {
        
    public:
	ConnectorImpl(bool _debug = false, u_int32_t buffer_size = 1024):LConnector(_debug, buffer_size){};
	virtual ~ConnectorImpl(){};
    };

#endif

}
}


#endif
