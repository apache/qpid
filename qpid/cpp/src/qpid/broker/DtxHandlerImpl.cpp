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
#include "DtxHandlerImpl.h"

#include <boost/format.hpp>
#include "Broker.h"
#include "BrokerChannel.h"

using namespace qpid::broker;
using qpid::framing::FieldTable;
using qpid::framing::MethodContext;
using std::string;

DtxHandlerImpl::DtxHandlerImpl(CoreRefs& parent) : CoreRefs(parent) {}


// DtxDemarcationHandler:


void DtxHandlerImpl::select(const MethodContext& /*context*/ )
{
    //don't need to do anything here really
    //send select-ok
}

void DtxHandlerImpl::end(const MethodContext& /*context*/,
                         u_int16_t /*ticket*/,
                         const string& xid,
                         bool fail,
                         bool suspend)
{
    if (fail && suspend) {
        throw ConnectionException(503, "End and suspend cannot both be set.");
    }

    //TODO: handle fail
    if (suspend) {
        channel.suspendDtx(xid);
    } else {
        channel.endDtx(xid);
    }
    //send end-ok
}

void DtxHandlerImpl::start(const MethodContext& /*context*/,
                           u_int16_t /*ticket*/,
                           const string& xid,
                           bool /*join*/,
                           bool resume)
{
    //TODO: handle join
    if (resume) {
        channel.resumeDtx(xid);
    } else {
        channel.startDtx(xid, broker.getDtxManager());
    }
    //send start-ok
}

// DtxCoordinationHandler:

void DtxHandlerImpl::prepare(const MethodContext& /*context*/,
                             u_int16_t /*ticket*/,
                             const string& xid )
{
    broker.getDtxManager().prepare(xid);
    //send prepare-ok
}

void DtxHandlerImpl::commit(const MethodContext& /*context*/,
                            u_int16_t /*ticket*/,
                            const string& xid,
                            bool /*onePhase*/ )
{
    broker.getDtxManager().commit(xid);
    //send commit-ok
    //TODO use onePhase flag to validate correct sequence
}


void DtxHandlerImpl::rollback(const MethodContext& /*context*/,
                              u_int16_t /*ticket*/,
                              const string& xid )
{
    broker.getDtxManager().rollback(xid);
    //send rollback-ok
}

void DtxHandlerImpl::recover(const MethodContext& /*context*/,
                             u_int16_t /*ticket*/,
                             bool /*startscan*/,
                             u_int32_t /*endscan*/ )
{
    //TODO
}

void DtxHandlerImpl::forget(const MethodContext& /*context*/,
                            u_int16_t /*ticket*/,
                            const string& /*xid*/ )
{
    //TODO
}

void DtxHandlerImpl::getTimeout(const MethodContext& /*context*/,
                                const string& /*xid*/ )
{
    //TODO
}


void DtxHandlerImpl::setTimeout(const MethodContext& /*context*/,
                                u_int16_t /*ticket*/,
                                const string& /*xid*/,
                                u_int32_t /*timeout*/ )
{
    //TODO
}



