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

#include "qpid/QpidError.h"
#include "qpid/log/Statement.h"
#include "MessageHandlerImpl.h"
#include "qpid/framing/FramingContent.h"
#include "Connection.h"
#include "Broker.h"
#include "MessageDelivery.h"
#include "qpid/framing/MessageAppendBody.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "BrokerAdapter.h"

#include <boost/format.hpp>
#include <boost/cast.hpp>
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

using namespace framing;

MessageHandlerImpl::MessageHandlerImpl(SemanticState& s) : 
    HandlerImpl(s),
    releaseOp(boost::bind(&SemanticState::release, &state, _1, _2)),
    rejectOp(boost::bind(&SemanticState::reject, &state, _1, _2))
 {}

//
// Message class method handlers
//

void
MessageHandlerImpl::cancel(const string& destination )
{
    state.cancel(destination);
}

void
MessageHandlerImpl::open(const string& /*reference*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::append(const std::string& /*reference*/, const std::string& /*bytes*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::close(const string& /*reference*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::checkpoint(const string& /*reference*/,
                               const string& /*identifier*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::resume(const string& /*reference*/,
                           const string& /*identifier*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::offset(uint64_t /*value*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::get(uint16_t /*ticket*/,
                        const string& /*queueName*/,
                        const string& /*destination*/,
                        bool /*noAck*/ )
{
    throw ConnectionException(540, "get no longer supported");
}

void
MessageHandlerImpl::empty()
{
    throw ConnectionException(540, "empty no longer supported");
}

void
MessageHandlerImpl::ok()
{
    throw ConnectionException(540, "Message.Ok no longer supported");    
}

void
MessageHandlerImpl::qos(uint32_t prefetchSize,
                        uint16_t prefetchCount,
                        bool /*global*/ )
{
    //TODO: handle global
    state.setPrefetchSize(prefetchSize);
    state.setPrefetchCount(prefetchCount);
}

void
MessageHandlerImpl::subscribe(uint16_t /*ticket*/,
                            const string& queueName,
                            const string& destination,
                            bool noLocal,
                            u_int8_t confirmMode,
                            u_int8_t acquireMode,
                            bool exclusive,
                            const framing::FieldTable& filter )
{
    Queue::shared_ptr queue = state.getQueue(queueName);
    if(!destination.empty() && state.exists(destination))
        throw ConnectionException(530, "Consumer tags must be unique");

    string tag = destination;
    state.consume(MessageDelivery::getMessageDeliveryToken(destination, confirmMode, acquireMode), 
                    tag, queue, noLocal, confirmMode == 1, acquireMode == 0, exclusive, &filter);
    // Dispatch messages as there is now a consumer.
    queue->requestDispatch();
}

void
MessageHandlerImpl::recover(bool requeue)
{
    state.recover(requeue);
}

void
MessageHandlerImpl::reject(const SequenceNumberSet& transfers, uint16_t /*code*/, const string& /*text*/ )
{
    transfers.processRanges(rejectOp);
}

void MessageHandlerImpl::flow(const std::string& destination, u_int8_t unit, u_int32_t value)
{
    if (unit == 0) {
        //message
        state.addMessageCredit(destination, value);
    } else if (unit == 1) {
        //bytes
        state.addByteCredit(destination, value);
    } else {
        //unknown
        throw ConnectionException(502, boost::format("Invalid value for unit %1%") % unit);
    }
    
}
    
void MessageHandlerImpl::flowMode(const std::string& destination, u_int8_t mode)
{
    if (mode == 0) {
        //credit
        state.setCreditMode(destination);
    } else if (mode == 1) {
        //window
        state.setWindowMode(destination);
    } else{
        throw ConnectionException(502, boost::format("Invalid value for mode %1%") % mode);        
    }
}
    
void MessageHandlerImpl::flush(const std::string& destination)
{
    state.flush(destination);        
}

void MessageHandlerImpl::stop(const std::string& destination)
{
    state.stop(destination);        
}

void MessageHandlerImpl::acquire(const SequenceNumberSet& transfers, u_int8_t /*mode*/)
{
    //TODO: implement mode

    SequenceNumberSet results;
    RangedOperation op = boost::bind(&SemanticState::acquire, &state, _1, _2, results);
    transfers.processRanges(op);
    results = results.condense();
    getProxy().getMessage().acquired(results);
}

void MessageHandlerImpl::release(const SequenceNumberSet& transfers)
{
    transfers.processRanges(releaseOp);
}

}} // namespace qpid::broker
