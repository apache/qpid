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
#include "MessageTransfer.h"
#include "qpid/broker/MapHandler.h"
#include "qpid/broker/Message.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/frame_functors.h"
#include "qpid/framing/TypeFilter.h"
#include "qpid/framing/SendContent.h"
#include "qpid/log/Statement.h"
#include "boost/lexical_cast.hpp"

using namespace qpid::framing;

namespace qpid {
namespace broker {
namespace amqp_0_10 {
namespace {
const std::string QMF2("qmf2");
const std::string PARTIAL("partial");
}
MessageTransfer::MessageTransfer() : frames(framing::SequenceNumber()), requiredCredit(0), cachedRequiredCredit(false) {}
MessageTransfer::MessageTransfer(const framing::SequenceNumber& id) : frames(id), requiredCredit(0), cachedRequiredCredit(false) {}

uint64_t MessageTransfer::getContentSize() const
{
    return frames.getContentSize();
}

std::string MessageTransfer::getAnnotationAsString(const std::string& key) const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();
    if (mp && mp->hasApplicationHeaders()) {
        FieldTable::ValuePtr value = mp->getApplicationHeaders().get(key);
        if (value) {
            if (value->convertsTo<std::string>()) return value->get<std::string>();
            else if (value->convertsTo<int>()) return boost::lexical_cast<std::string>(value->get<int>());
        }
        return std::string();
    } else {
        return std::string();
    }
}
std::string MessageTransfer::getPropertyAsString(const std::string& key) const { return getAnnotationAsString(key); }

bool MessageTransfer::getTtl(uint64_t& result) const
{
    const qpid::framing::DeliveryProperties* dp = getProperties<qpid::framing::DeliveryProperties>();
    if (dp && dp->hasTtl()) {
         result = dp->getTtl();
         return true;
    } else {
        return false;
    }
}
bool MessageTransfer::hasExpiration() const
{
    const qpid::framing::DeliveryProperties* dp = getProperties<qpid::framing::DeliveryProperties>();
    if (dp && dp->hasExpiration()) {
         return true;
    } else {
        return false;
    }
}

uint8_t MessageTransfer::getPriority() const
{
    const qpid::framing::DeliveryProperties* dp = getProperties<qpid::framing::DeliveryProperties>();
    if (dp && dp->hasPriority()) {
        return dp->getPriority();
    } else {
        return 0;
    }
}

std::string MessageTransfer::getExchangeName() const
{
    return getFrames().as<framing::MessageTransferBody>()->getDestination();
}

bool MessageTransfer::requiresAccept() const
{
    const framing::MessageTransferBody* b = getFrames().as<framing::MessageTransferBody>();
    return b && b->getAcceptMode() == 0/*EXPLICIT == 0*/;
}
uint32_t MessageTransfer::getRequiredCredit() const
{
    if (cachedRequiredCredit) {
        return requiredCredit;
    } else {
        qpid::framing::SumBodySize sum;
        frames.map_if(sum, qpid::framing::TypeFilter2<qpid::framing::HEADER_BODY, qpid::framing::CONTENT_BODY>());
        return sum.getSize();
    }
}
void MessageTransfer::computeRequiredCredit()
{
    //add up payload for all header and content frames in the frameset
    qpid::framing::SumBodySize sum;
    frames.map_if(sum, qpid::framing::TypeFilter2<qpid::framing::HEADER_BODY, qpid::framing::CONTENT_BODY>());
    requiredCredit = sum.getSize();
    cachedRequiredCredit = true;
}

qpid::framing::FrameSet& MessageTransfer::getFrames()
{
    return frames;
}
const qpid::framing::FrameSet& MessageTransfer::getFrames() const
{
    return frames;
}
void MessageTransfer::sendContent(framing::FrameHandler& out, uint16_t maxFrameSize) const
{
    qpid::framing::Count c;
    frames.map_if(c, qpid::framing::TypeFilter<qpid::framing::CONTENT_BODY>());

    qpid::framing::SendContent f(out, maxFrameSize, c.getCount());
    frames.map_if(f, qpid::framing::TypeFilter<qpid::framing::CONTENT_BODY>());
}

class SendHeader
{
  public:
    SendHeader(FrameHandler& h, bool r, uint64_t t, uint64_t ts, const qpid::types::Variant::Map& a) : handler(h), redelivered(r), ttl(t), timestamp(ts), annotations(a) {}
    void operator()(const AMQFrame& f)
    {
        AMQFrame copy = f;
        if (redelivered || ttl || timestamp || annotations.size()) {
            copy.cloneBody();
            if (annotations.size()) {
                MessageProperties* props =
                    copy.castBody<AMQHeaderBody>()->get<MessageProperties>(true);
                for (qpid::types::Variant::Map::const_iterator i = annotations.begin();
                     i != annotations.end(); ++i) {
                    props->getApplicationHeaders().setString(i->first, i->second.asString());
                }
            }
            if (redelivered || ttl || timestamp) {
                DeliveryProperties* dp =
                    copy.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true);
                if (ttl) dp->setTtl(ttl);
                if (redelivered) dp->setRedelivered(redelivered);
                if (timestamp) dp->setTimestamp(timestamp);
            }
        }
        handler.handle(copy);
    }
  private:
    FrameHandler& handler;
    bool redelivered;
    uint64_t ttl;
    uint64_t timestamp;
    const qpid::types::Variant::Map& annotations;
};

void MessageTransfer::sendHeader(framing::FrameHandler& out, uint16_t /*maxFrameSize*/,
                                 bool redelivered, uint64_t ttl, uint64_t timestamp,
                                 const qpid::types::Variant::Map& annotations) const
{
    SendHeader f(out, redelivered, ttl, timestamp, annotations);
    frames.map_if(f, TypeFilter<HEADER_BODY>());
}
bool MessageTransfer::isImmediateDeliveryRequired(const qpid::broker::Message& /*message*/)
{
    return false;//TODO
}

const framing::SequenceNumber& MessageTransfer::getCommandId() const { return frames.getId(); }

std::string MessageTransfer::getRoutingKey() const
{
    const qpid::framing::DeliveryProperties* dp = getProperties<qpid::framing::DeliveryProperties>();
    if (dp && dp->hasRoutingKey()) {
        return dp->getRoutingKey();
    } else {
        return std::string();
    }
}
bool MessageTransfer::isPersistent() const
{
    const qpid::framing::DeliveryProperties* dp = getProperties<qpid::framing::DeliveryProperties>();
    if (dp && dp->hasDeliveryMode()) {
        return dp->getDeliveryMode() == 2;
    } else {
        return false;
    }
}

std::string MessageTransfer::getContent() const
{
    return frames.getContent();
}

void MessageTransfer::decodeHeader(framing::Buffer& buffer)
{
    AMQFrame method;
    method.decode(buffer);
    frames.append(method);

    AMQFrame header;
    header.decode(buffer);
    frames.append(header);
}
void MessageTransfer::decodeContent(framing::Buffer& buffer)
{
    if (buffer.available()) {
        //get the data as a string and set that as the content
        //body on a frame then add that frame to the frameset
        AMQFrame frame((AMQContentBody()));
        frame.castBody<AMQContentBody>()->decode(buffer, buffer.available());
        frame.setFirstSegment(false);
        frames.append(frame);
    } else {
        //adjust header flags
        MarkLastSegment f;
        frames.map_if(f, TypeFilter<HEADER_BODY>());
    }
}

void MessageTransfer::encode(framing::Buffer& buffer) const
{
    //encode method and header frames
    EncodeFrame f1(buffer);
    frames.map_if(f1, TypeFilter2<METHOD_BODY, HEADER_BODY>());

    //then encode the payload of each content frame
    framing::EncodeBody f2(buffer);
    frames.map_if(f2, TypeFilter<CONTENT_BODY>());
}

void MessageTransfer::encodeContent(framing::Buffer& buffer) const
{
    //encode the payload of each content frame
    EncodeBody f2(buffer);
    frames.map_if(f2, TypeFilter<CONTENT_BODY>());
}

uint32_t MessageTransfer::encodedSize() const
{
    return encodedHeaderSize() + encodedContentSize();
}

uint32_t MessageTransfer::encodedContentSize() const
{
    return  frames.getContentSize();
}

uint32_t MessageTransfer::encodedHeaderSize() const
{
    //add up the size for all method and header frames in the frameset
    SumFrameSize sum;
    frames.map_if(sum, TypeFilter2<METHOD_BODY, HEADER_BODY>());
    return sum.getSize();
}

bool MessageTransfer::isQMFv2() const
{
    const framing::MessageProperties* props = getProperties<framing::MessageProperties>();
    return props && props->getAppId() == QMF2 && props->hasApplicationHeaders();
}

bool MessageTransfer::isQMFv2(const qpid::broker::Message& message)
{
    const MessageTransfer* transfer = dynamic_cast<const MessageTransfer*>(&message.getEncoding());
    return transfer && transfer->isQMFv2();
}

bool MessageTransfer::isLastQMFResponse(const std::string correlation) const
{
    const framing::MessageProperties* props = getProperties<framing::MessageProperties>();
    return props && props->getCorrelationId() == correlation
        && props->hasApplicationHeaders() && !props->getApplicationHeaders().isSet(PARTIAL);
}

bool MessageTransfer::isLastQMFResponse(const qpid::broker::Message& message, const std::string correlation)
{
    const MessageTransfer* transfer = dynamic_cast<const MessageTransfer*>(&message.getEncoding());
    return transfer && transfer->isLastQMFResponse(correlation);
}


void MessageTransfer::processProperties(qpid::broker::MapHandler& handler) const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();
    if (mp && mp->hasApplicationHeaders()) {
        const FieldTable ft = mp->getApplicationHeaders();
        for (FieldTable::const_iterator i = ft.begin(); i != ft.end(); ++i) {
            qpid::broker::MapHandler::CharSequence key;
            key.data = i->first.data();
            key.size = i->first.size();
            FieldTable::ValuePtr v = i->second;
            //TODO: something more sophisticated... 
            if (v->empty()) {
                handler.handleVoid(key);
            } else if (v->convertsTo<uint64_t>()) {
                handler.handleUint64(key, v->get<uint64_t>());
            } else if (v->convertsTo<int64_t>()) {
                handler.handleInt64(key, v->get<int64_t>());
            } else if (v->convertsTo<std::string>()) {
                std::string s = v->get<std::string>();
                qpid::broker::MapHandler::CharSequence value;
                value.data = s.data();
                value.size = s.size();
                qpid::broker::MapHandler::CharSequence encoding; encoding.size = 0; encoding.data = 0;
                handler.handleString(key, value, encoding);
            } else {
                QPID_LOG(debug, "Unhandled key!" << *v);
            }
        }
    }
}

std::string MessageTransfer::getUserId() const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();
    if (mp && mp->hasUserId()) return mp->getUserId();
    else return std::string();

}
MessageTransfer::MessageTransfer(const qpid::framing::FrameSet& f) : frames(f), requiredCredit(0) {}

boost::intrusive_ptr<PersistableMessage> MessageTransfer::merge(const std::map<std::string, qpid::types::Variant>& annotations) const
{
    boost::intrusive_ptr<MessageTransfer> clone(new MessageTransfer(this->frames));
    qpid::framing::MessageProperties* mp = clone->frames.getHeaders()->get<qpid::framing::MessageProperties>(true);
    for (qpid::types::Variant::Map::const_iterator i = annotations.begin(); i != annotations.end(); ++i) {
        mp->getApplicationHeaders().setString(i->first, i->second);
    }
    return clone;
}
}
// qpid::broker namespace, TODO: move these elsewhere!
void encode(const Message& in, std::string& out)
{
    const amqp_0_10::MessageTransfer& transfer = amqp_0_10::MessageTransfer::get(in);
    uint32_t size = transfer.encodedSize();
    std::vector<char> data(size);
    qpid::framing::Buffer buffer(&(data[0]), size);
    transfer.encode(buffer);
    buffer.reset();
    buffer.getRawData(out, size);
}
void decode(const std::string& in, Message& out)
{
    boost::intrusive_ptr<amqp_0_10::MessageTransfer> transfer(new amqp_0_10::MessageTransfer);
    qpid::framing::Buffer buffer(const_cast<char*>(in.data()), in.size());
    transfer->decodeHeader(buffer);
    transfer->decodeContent(buffer);
    out = Message(transfer, transfer);
}

}} // namespace qpid::broker::amqp_0_10
