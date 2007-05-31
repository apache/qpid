#ifndef _broker_DtxHandlerImpl_h
#define _broker_DtxHandlerImpl_h

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

#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "HandlerImpl.h"

namespace qpid {
namespace broker {

class DtxHandlerImpl 
    : public CoreRefs,
      public framing::AMQP_ServerOperations::DtxCoordinationHandler,
      public framing::AMQP_ServerOperations::DtxDemarcationHandler
{    
    framing::AMQP_ClientProxy::DtxDemarcation dClient;
    framing::AMQP_ClientProxy::DtxCoordination cClient;
public:
    DtxHandlerImpl(CoreRefs& parent);

    // DtxCoordinationHandler:

    void commit(const framing::MethodContext& context,
                        u_int16_t ticket,
                        const std::string& xid,
                        bool onePhase );

    void forget(const framing::MethodContext& context,
                        u_int16_t ticket,
                        const std::string& xid );

    void getTimeout(const framing::MethodContext& context,
                            const std::string& xid );

    void prepare(const framing::MethodContext& context,
                         u_int16_t ticket,
                         const std::string& xid );

    void recover(const framing::MethodContext& context,
                         u_int16_t ticket,
                         bool startscan,
                         u_int32_t endscan );

    void rollback(const framing::MethodContext& context,
                          u_int16_t ticket,
                          const std::string& xid );

    void setTimeout(const framing::MethodContext& context,
                            u_int16_t ticket,
                            const std::string& xid,
                            u_int32_t timeout );

    // DtxDemarcationHandler:

    void end(const framing::MethodContext& context,
                     u_int16_t ticket,
                     const std::string& xid,
                     bool fail,
                     bool suspend );

    void select(const framing::MethodContext& context );

    void start(const framing::MethodContext& context,
                       u_int16_t ticket,
                       const std::string& xid,
                       bool join,
                       bool resume );
};


}} // namespace qpid::broker



#endif  /*!_broker_DtxHandlerImpl_h*/
