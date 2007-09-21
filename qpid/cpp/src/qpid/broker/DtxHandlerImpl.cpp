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
#include "qpid/framing/constants.h"

using namespace qpid::broker;
using namespace qpid::framing;
using std::string;

DtxHandlerImpl::DtxHandlerImpl(SemanticState& s) : HandlerImpl(s) {}

// DtxDemarcationHandler:


void DtxHandlerImpl::select()
{
    state.selectDtx();
}

DtxDemarcationEndResult DtxHandlerImpl::end(u_int16_t /*ticket*/,
                                            const string& xid,
                                            bool fail,
                                            bool suspend)
{
    try {
        if (fail) {
            state.endDtx(xid, true);
            if (suspend) {
                throw ConnectionException(503, "End and suspend cannot both be set.");
            } else {
                return DtxDemarcationEndResult(XA_RBROLLBACK);
            }
        } else {
            if (suspend) {
                state.suspendDtx(xid);
            } else {
                state.endDtx(xid, false);
            }
            return DtxDemarcationEndResult(XA_OK);
        }
    } catch (const DtxTimeoutException& e) {
        return DtxDemarcationEndResult(XA_RBTIMEOUT);        
    }
}

DtxDemarcationStartResult DtxHandlerImpl::start(u_int16_t /*ticket*/,
                           const string& xid,
                           bool join,
                           bool resume)
{
    if (join && resume) {
        throw ConnectionException(503, "Join and resume cannot both be set.");
    }
    try {
        if (resume) {
            state.resumeDtx(xid);
        } else {
            state.startDtx(xid, getBroker().getDtxManager(), join);
        }
        return DtxDemarcationStartResult(XA_OK);
    } catch (const DtxTimeoutException& e) {
        return DtxDemarcationStartResult(XA_RBTIMEOUT);        
    }
}

// DtxCoordinationHandler:

DtxCoordinationPrepareResult DtxHandlerImpl::prepare(u_int16_t /*ticket*/,
                             const string& xid)
{
    try {
        bool ok = getBroker().getDtxManager().prepare(xid);
        return DtxCoordinationPrepareResult(ok ? XA_OK : XA_RBROLLBACK);
    } catch (const DtxTimeoutException& e) {
        return DtxCoordinationPrepareResult(XA_RBTIMEOUT);        
    }
}

DtxCoordinationCommitResult DtxHandlerImpl::commit(u_int16_t /*ticket*/,
                            const string& xid,
                            bool onePhase)
{
    try {
        bool ok = getBroker().getDtxManager().commit(xid, onePhase);
        return DtxCoordinationCommitResult(ok ? XA_OK : XA_RBROLLBACK);
    } catch (const DtxTimeoutException& e) {
        return DtxCoordinationCommitResult(XA_RBTIMEOUT);        
    }
}


DtxCoordinationRollbackResult DtxHandlerImpl::rollback(u_int16_t /*ticket*/,
                              const string& xid )
{
    try {
        getBroker().getDtxManager().rollback(xid);
        return DtxCoordinationRollbackResult(XA_OK);
    } catch (const DtxTimeoutException& e) {
        return DtxCoordinationRollbackResult(XA_RBTIMEOUT);        
    }
}

DtxCoordinationRecoverResult DtxHandlerImpl::recover(u_int16_t /*ticket*/,
                                                     bool /*startscan*/,
                                                     bool /*endscan*/ )
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
    getBroker().getStore().collectPreparedXids(xids);        
    uint size(0);
    for (std::set<std::string>::iterator i = xids.begin(); i != xids.end(); i++) {
        size += i->size() + 1/*shortstr size*/;        
    }

    char* bytes = static_cast<char*>(::alloca(size + 4/*longstr size*/));
    Buffer wbuffer(bytes, size + 4/*longstr size*/);
    wbuffer.putLong(size);
    for (std::set<std::string>::iterator i = xids.begin(); i != xids.end(); i++) {
        wbuffer.putShortString(*i);
    }

    Buffer rbuffer(bytes, size + 4/*longstr size*/);
    string data;
    rbuffer.getLongString(data);

    FieldTable response;
    response.setString("xids", data);
    return DtxCoordinationRecoverResult(response);
}

void DtxHandlerImpl::forget(u_int16_t /*ticket*/,
                            const string& xid)
{
    //Currently no heuristic completion is supported, so this should never be used.
    throw ConnectionException(503, boost::format("Forget is invalid. Branch with xid %1% not heuristically completed!") % xid);
}

DtxCoordinationGetTimeoutResult DtxHandlerImpl::getTimeout(const string& xid)
{
    uint32_t timeout = getBroker().getDtxManager().getTimeout(xid);
    return DtxCoordinationGetTimeoutResult(timeout);    
}


void DtxHandlerImpl::setTimeout(u_int16_t /*ticket*/,
                                const string& xid,
                                u_int32_t timeout)
{
    getBroker().getDtxManager().setTimeout(xid, timeout);
}


