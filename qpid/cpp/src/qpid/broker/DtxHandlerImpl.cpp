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
using qpid::framing::AMQP_ClientProxy;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::framing::MethodContext;
using std::string;

DtxHandlerImpl::DtxHandlerImpl(CoreRefs& parent) : 
    CoreRefs(parent),
    dClient(AMQP_ClientProxy::DtxDemarcation::get(proxy)),
    cClient(AMQP_ClientProxy::DtxCoordination::get(proxy))

{
}

const int XA_RBROLLBACK(1);
const int XA_RBTIMEOUT(2);
const int XA_HEURHAZ(3);
const int XA_HEURCOM(4);
const int XA_HEURRB(5);
const int XA_HEURMIX(6);
const int XA_RDONLY(7);
const int XA_OK(8);


// DtxDemarcationHandler:


void DtxHandlerImpl::select(const MethodContext& context )
{
    channel.selectDtx();
    dClient.selectOk(context.getRequestId());
}

void DtxHandlerImpl::end(const MethodContext& context,
                         u_int16_t /*ticket*/,
                         const string& xid,
                         bool fail,
                         bool suspend)
{
    try {
        if (fail) {
            channel.endDtx(xid, true);
            if (suspend) {
                throw ConnectionException(503, "End and suspend cannot both be set.");
            } else {
                dClient.endOk(XA_RBROLLBACK, context.getRequestId());
            }
        } else {
            if (suspend) {
                channel.suspendDtx(xid);
            } else {
                channel.endDtx(xid, false);
            }
            dClient.endOk(XA_OK, context.getRequestId());
        }
    } catch (const DtxTimeoutException& e) {
        dClient.endOk(XA_RBTIMEOUT, context.getRequestId());        
    }
}

void DtxHandlerImpl::start(const MethodContext& context,
                           u_int16_t /*ticket*/,
                           const string& xid,
                           bool join,
                           bool resume)
{
    if (join && resume) {
        throw ConnectionException(503, "Join and resume cannot both be set.");
    }
    try {
        if (resume) {
            channel.resumeDtx(xid);
        } else {
            channel.startDtx(xid, broker.getDtxManager(), join);
        }
        dClient.startOk(XA_OK, context.getRequestId());
    } catch (const DtxTimeoutException& e) {
        dClient.startOk(XA_RBTIMEOUT, context.getRequestId());        
    }
}

// DtxCoordinationHandler:

void DtxHandlerImpl::prepare(const MethodContext& context,
                             u_int16_t /*ticket*/,
                             const string& xid)
{
    try {
        bool ok = broker.getDtxManager().prepare(xid);
        cClient.prepareOk(ok ? XA_OK : XA_RBROLLBACK, context.getRequestId());
    } catch (const DtxTimeoutException& e) {
        cClient.prepareOk(XA_RBTIMEOUT, context.getRequestId());        
    }
}

void DtxHandlerImpl::commit(const MethodContext& context,
                            u_int16_t /*ticket*/,
                            const string& xid,
                            bool onePhase)
{
    try {
        bool ok = broker.getDtxManager().commit(xid, onePhase);
        cClient.commitOk(ok ? XA_OK : XA_RBROLLBACK, context.getRequestId());
    } catch (const DtxTimeoutException& e) {
        cClient.commitOk(XA_RBTIMEOUT, context.getRequestId());        
    }
}


void DtxHandlerImpl::rollback(const MethodContext& context,
                              u_int16_t /*ticket*/,
                              const string& xid )
{
    try {
        broker.getDtxManager().rollback(xid);
        cClient.rollbackOk(XA_OK, context.getRequestId());
    } catch (const DtxTimeoutException& e) {
        cClient.rollbackOk(XA_RBTIMEOUT, context.getRequestId());        
    }
}

void DtxHandlerImpl::recover(const MethodContext& context,
                             u_int16_t /*ticket*/,
                             bool /*startscan*/,
                             u_int32_t /*endscan*/ )
{
    //TODO: what do startscan and endscan actually mean?

    // response should hold on key value pair with key = 'xids' and
    // value = sequence of xids

    // until sequences are supported (0-10 encoding), an alternate
    // scheme is used for testing:
    //
    //   key = 'xids' and value = a longstr containing shortstrs for each xid
    //
    // note that this restricts the length of the xids more than is
    // strictly 'legal', but that is ok for testing
    std::set<std::string> xids;
    broker.getStore().collectPreparedXids(xids);        
    uint size(0);
    for (std::set<std::string>::iterator i = xids.begin(); i != xids.end(); i++) {
        size += i->size() + 1/*shortstr size*/;        
    }
    Buffer buffer(size + 4/*longstr size*/);
    buffer.putLong(size);
    for (std::set<std::string>::iterator i = xids.begin(); i != xids.end(); i++) {
        buffer.putShortString(*i);
    }
    buffer.flip();
    string data;
    buffer.getLongString(data);

    FieldTable response;
    response.setString("xids", data);
    cClient.recoverOk(response, context.getRequestId());
}

void DtxHandlerImpl::forget(const MethodContext& /*context*/,
                            u_int16_t /*ticket*/,
                            const string& xid)
{
    //Currently no heuristic completion is supported, so this should never be used.
    throw ConnectionException(503, boost::format("Forget is invalid. Branch with xid %1% not heuristically completed!") % xid);
}

void DtxHandlerImpl::getTimeout(const MethodContext& context,
                                const string& xid)
{
    uint32_t timeout = broker.getDtxManager().getTimeout(xid);
    cClient.getTimeoutOk(timeout, context.getRequestId());    
}


void DtxHandlerImpl::setTimeout(const MethodContext& context,
                                u_int16_t /*ticket*/,
                                const string& xid,
                                u_int32_t timeout)
{
    broker.getDtxManager().setTimeout(xid, timeout);
    cClient.setTimeoutOk(context.getRequestId());    
}



