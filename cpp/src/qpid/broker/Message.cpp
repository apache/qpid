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

#include "Message.h"
#include "ExchangeRegistry.h"
#include "ExpiryPolicy.h"
#include "qpid/StringUtils.h"
#include "qpid/framing/frame_functors.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/SendContent.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/TypeFilter.h"
#include "qpid/log/Statement.h"

#include <time.h>

using boost::intrusive_ptr;
using qpid::sys::AbsTime;
using qpid::sys::Duration;
using qpid::sys::TIME_MSEC;
using qpid::sys::FAR_FUTURE;
using std::string;
using namespace qpid::framing;

namespace qpid {
namespace broker {

TransferAdapter Message::TRANSFER;

Message::Message(const framing::SequenceNumber& id) :
    frames(id), persistenceId(0), redelivered(false), loaded(false),
    staged(false), forcePersistentPolicy(false), publisher(0), adapter(0), 
    expiration(FAR_FUTURE), enqueueCallback(0), dequeueCallback(0) {}

Message::~Message()
{
}

void Message::forcePersistent()
{
    forcePersistentPolicy = true;
}

std::string Message::getRoutingKey() const
{
    return getAdapter().getRoutingKey(frames);
}

std::string Message::getExchangeName() const 
{
    return getAdapter().getExchange(frames);
}

const boost::shared_ptr<Exchange> Message::getExchange(ExchangeRegistry& registry) const
{
    if (!exchange) {
        exchange = registry.get(getExchangeName());
    } 
    return exchange;
}

bool Message::isImmediate() const
{
    return getAdapter().isImmediate(frames);
}

const FieldTable* Message::getApplicationHeaders() const
{
    return getAdapter().getApplicationHeaders(frames);
}

bool Message::isPersistent()
{
    return (getAdapter().isPersistent(frames) || forcePersistentPolicy);
}

bool Message::requiresAccept()
{
    return getAdapter().requiresAccept(frames);
}

uint32_t Message::getRequiredCredit() const
{
    //add up payload for all header and content frames in the frameset
    SumBodySize sum;
    frames.map_if(sum, TypeFilter2<HEADER_BODY, CONTENT_BODY>());
    return sum.getSize();
}

void Message::encode(framing::Buffer& buffer) const
{
    //encode method and header frames
    EncodeFrame f1(buffer);
    frames.map_if(f1, TypeFilter2<METHOD_BODY, HEADER_BODY>());

    //then encode the payload of each content frame
    framing::EncodeBody f2(buffer);
    frames.map_if(f2, TypeFilter<CONTENT_BODY>());
}

void Message::encodeContent(framing::Buffer& buffer) const
{
    //encode the payload of each content frame
    EncodeBody f2(buffer);
    frames.map_if(f2, TypeFilter<CONTENT_BODY>());
}

uint32_t Message::encodedSize() const
{
    return encodedHeaderSize() + encodedContentSize();
}

uint32_t Message::encodedContentSize() const
{
    return  frames.getContentSize();
}

uint32_t Message::encodedHeaderSize() const
{
    //add up the size for all method and header frames in the frameset
    SumFrameSize sum;
    frames.map_if(sum, TypeFilter2<METHOD_BODY, HEADER_BODY>());
    return sum.getSize();
}

void Message::decodeHeader(framing::Buffer& buffer)
{
    AMQFrame method;
    method.decode(buffer);
    frames.append(method);

    AMQFrame header;
    header.decode(buffer);
    frames.append(header);
}

void Message::decodeContent(framing::Buffer& buffer)
{
    if (buffer.available()) {
        //get the data as a string and set that as the content
        //body on a frame then add that frame to the frameset
        AMQFrame frame((AMQContentBody()));
        frame.castBody<AMQContentBody>()->decode(buffer, buffer.available());
        frames.append(frame);
    } else {
        //adjust header flags
        MarkLastSegment f;
        frames.map_if(f, TypeFilter<HEADER_BODY>());    
    }
    //mark content loaded
    loaded = true;
}

void Message::releaseContent(MessageStore* _store)
{
    if (!store) {
        store = _store;
    }
    if (store) {
        if (!getPersistenceId()) {
            intrusive_ptr<PersistableMessage> pmsg(this);
            store->stage(pmsg);
            staged = true;
        }
        //remove any content frames from the frameset
        frames.remove(TypeFilter<CONTENT_BODY>());
        setContentReleased();
    }
}

void Message::destroy()
{
    if (staged) {
        if (store) {
            store->destroy(*this);
        } else {
            QPID_LOG(error, "Message content was staged but no store is set so it can't be destroyed");
        }
    }
}

bool Message::getContentFrame(const Queue& queue, AMQFrame& frame, uint16_t maxContentSize, uint64_t offset) const
{
    if (isContentReleased()) {
        intrusive_ptr<const PersistableMessage> pmsg(this);
        
        bool done = false;
        string& data = frame.castBody<AMQContentBody>()->getData();
        store->loadContent(queue, pmsg, data, offset, maxContentSize);
        done = data.size() < maxContentSize;
        frame.setBof(false);
        frame.setEof(true);
        QPID_LOG(debug, "loaded frame" << frame);
        if (offset > 0) {
            frame.setBos(false);
        }
        if (!done) {
            frame.setEos(false);
        } else return false;
        return true;
    }
    else return false;
}

void Message::sendContent(const Queue& queue, framing::FrameHandler& out, uint16_t maxFrameSize) const
{
    if (isContentReleased()) {

        uint16_t maxContentSize = maxFrameSize - AMQFrame::frameOverhead();
        bool morecontent = true;
        for (uint64_t offset = 0; morecontent; offset += maxContentSize)
        {            
            AMQFrame frame((AMQContentBody()));
            morecontent = getContentFrame(queue, frame, maxContentSize, offset);
            out.handle(frame);
        }
    } else {
        Count c;
        frames.map_if(c, TypeFilter<CONTENT_BODY>());

        SendContent f(out, maxFrameSize, c.getCount());
        frames.map_if(f, TypeFilter<CONTENT_BODY>());
    }
}

void Message::sendHeader(framing::FrameHandler& out, uint16_t /*maxFrameSize*/) const
{
    sys::Mutex::ScopedLock l(lock);
    Relay f(out);
    frames.map_if(f, TypeFilter<HEADER_BODY>());    
}

// TODO aconway 2007-11-09: Obsolete, remove. Was used to cover over
// 0-8/0-9 message differences.
MessageAdapter& Message::getAdapter() const
{
    if (!adapter) {
        if(frames.isA<MessageTransferBody>()) {
            adapter = &TRANSFER;
        } else {
            const AMQMethodBody* method = frames.getMethod();
            if (!method) throw Exception("Can't adapt message with no method");
            else throw Exception(QPID_MSG("Can't adapt message based on " << *method));
        }
    }
    return *adapter;
}

uint64_t Message::contentSize() const
{
    return frames.getContentSize();
}

bool Message::isContentLoaded() const
{
    return loaded;
}


namespace 
{
const std::string X_QPID_TRACE("x-qpid.trace");
}

bool Message::isExcluded(const std::vector<std::string>& excludes) const
{
    const FieldTable* headers = getApplicationHeaders();
    if (headers) {
        std::string traceStr = headers->getAsString(X_QPID_TRACE);
        if (traceStr.size()) {
            std::vector<std::string> trace = split(traceStr, ", ");

            for (std::vector<std::string>::const_iterator i = excludes.begin(); i != excludes.end(); i++) {
                for (std::vector<std::string>::const_iterator j = trace.begin(); j != trace.end(); j++) {
                    if (*i == *j) {
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

void Message::addTraceId(const std::string& id)
{
    sys::Mutex::ScopedLock l(lock);
    if (isA<MessageTransferBody>()) {
        FieldTable& headers = getProperties<MessageProperties>()->getApplicationHeaders();
        std::string trace = headers.getAsString(X_QPID_TRACE);
        if (trace.empty()) {
            headers.setString(X_QPID_TRACE, id);
        } else if (trace.find(id) == std::string::npos) {
            trace += ",";
            trace += id;
            headers.setString(X_QPID_TRACE, trace);
        }        
    }
}

void Message::setTimestamp(const boost::intrusive_ptr<ExpiryPolicy>& e) 
{
    DeliveryProperties* props = getProperties<DeliveryProperties>();    
    if (props->getTtl()) {
        // AMQP requires setting the expiration property to be posix
        // time_t in seconds. TTL is in milliseconds
        time_t now = ::time(0);
        props->setExpiration(now + (props->getTtl()/1000));
        // Use higher resolution time for the internal expiry calculation.
        expiration = AbsTime(AbsTime::now(), Duration(props->getTtl() * TIME_MSEC));
        setExpiryPolicy(e);
    }
}

void Message::setExpiryPolicy(const boost::intrusive_ptr<ExpiryPolicy>& e) {
    expiryPolicy = e;
    if (expiryPolicy)
        expiryPolicy->willExpire(*this);
}

bool Message::hasExpired()
{
    return expiryPolicy && expiryPolicy->hasExpired(*this);
}

boost::intrusive_ptr<Message>& Message::getReplacementMessage(const Queue* qfor) const
{
    Replacement::iterator i = replacement.find(qfor);
    if (i != replacement.end()){
        return i->second;
    }		 
    return empty;
}

void Message::setReplacementMessage(boost::intrusive_ptr<Message> msg, const Queue* qfor)
{
    replacement[qfor] = msg;
}

void Message::allEnqueuesComplete() {
    MessageCallback* cb = 0;
    {
        sys::Mutex::ScopedLock l(lock);
        std::swap(cb, enqueueCallback);
    }
    if (cb && *cb) (*cb)(intrusive_ptr<Message>(this));
}

void Message::allDequeuesComplete() {
    MessageCallback* cb = 0;
    {
        sys::Mutex::ScopedLock l(lock);
        std::swap(cb, dequeueCallback);
    }
    if (cb && *cb) (*cb)(intrusive_ptr<Message>(this));
}

void Message::setEnqueueCompleteCallback(MessageCallback& cb) { enqueueCallback = &cb; }
void Message::resetEnqueueCompleteCallback() { enqueueCallback = 0; }

void Message::setDequeueCompleteCallback(MessageCallback& cb) { dequeueCallback = &cb; }
void Message::resetDequeueCompleteCallback() { dequeueCallback = 0; }

}} // namespace qpid::broker
