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

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/amqp/MapHandler.h"
#include "qpid/amqp/MessageId.h"
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
const std::string DELIMITER("/");
const std::string EMPTY;
const std::string QMF2("qmf2");
const std::string PARTIAL("partial");
const std::string SUBJECT_KEY("qpid.subject");
}
MessageTransfer::MessageTransfer() : frames(framing::SequenceNumber()), requiredCredit(0), cachedRequiredCredit(false) {}
MessageTransfer::MessageTransfer(const framing::SequenceNumber& id) : frames(id), requiredCredit(0), cachedRequiredCredit(false) {}

uint64_t MessageTransfer::getContentSize() const
{
    return frames.getContentSize();
}

uint64_t MessageTransfer::getMessageSize() const
{
    return getRequiredCredit();
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

amqp::MessageId MessageTransfer::getMessageId() const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();

    amqp::MessageId r;
    if (mp->hasMessageId()) {
        r.set(amqp::CharSequence::create(mp->getMessageId().data(),16), types::VAR_UUID);
    }
    return r;
}

amqp::MessageId MessageTransfer::getCorrelationId() const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();

    amqp::MessageId r;
    if (mp->hasCorrelationId()) {
        r.set(amqp::CharSequence::create(mp->getCorrelationId()), types::VAR_STRING);
    }
    return r;
}

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

void MessageTransfer::setTimestamp()
{
    DeliveryProperties* props = getFrames().getHeaders()->get<DeliveryProperties>(true);
    time_t now = ::time(0);
    props->setTimestamp(now);
}

uint64_t MessageTransfer::getTimestamp() const
{
    const DeliveryProperties* props = getProperties<DeliveryProperties>();
    return props ? props->getTimestamp() : 0;
}

std::string MessageTransfer::getTo() const
{
    const DeliveryProperties* props = getProperties<DeliveryProperties>();
    if (props) {
        //if message was sent to 'nameless exchange' then the routing key is the queue
        return props->getExchange().empty() ? props->getRoutingKey() : props->getExchange();
    } else {
        return EMPTY;
    }
}
std::string MessageTransfer::getSubject() const
{
    const DeliveryProperties* props = getProperties<DeliveryProperties>();
    if (props) {
        //if message was sent to 'nameless exchange' then the routing key is the queue name, not the subject
        return props->getExchange().empty() ? getPropertyAsString(SUBJECT_KEY) : props->getRoutingKey();
    } else {
        return EMPTY;
    }
}
std::string MessageTransfer::getReplyTo() const
{
    const MessageProperties* props = getProperties<MessageProperties>();
    if (props && props->hasReplyTo()) {
        const qpid::framing::ReplyTo& replyto = props->getReplyTo();
        if (replyto.hasExchange() && replyto.hasRoutingKey())
            return replyto.getExchange() + DELIMITER + replyto.getRoutingKey();
        else if (replyto.hasExchange()) return replyto.getExchange();
        else if (replyto.hasRoutingKey()) return replyto.getRoutingKey();
        else return EMPTY;
    } else {
        return EMPTY;
    }
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
    // TODO -- remove this code and replace it with a QPID_ASSERT(cachedRequiredCredit),
    //         then fix whatever breaks.  compute should always be called before get.
        uint32_t sum = 0;
        for(FrameSet::Frames::const_iterator i = frames.begin(); i != frames.end(); ++i ) {
            uint8_t type = (*i).getBody()->type();
            if ((type == qpid::framing::HEADER_BODY ) || (type == qpid::framing::CONTENT_BODY ))
                sum += (*i).getBody()->encodedSize();
        }
        return sum;
    }
}
void MessageTransfer::computeRequiredCredit()
{
    //add up payload for all header and content frames in the frameset
    uint32_t sum = 0;
    for(FrameSet::Frames::const_iterator i = frames.begin(); i != frames.end(); ++i ) {
        uint8_t type = (*i).getBody()->type();
        if ((type == qpid::framing::HEADER_BODY ) || (type == qpid::framing::CONTENT_BODY ))
            sum += (*i).getBody()->encodedSize();
    }
    requiredCredit = sum;
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
    SendHeader(FrameHandler& h, bool r, uint64_t t, const qpid::types::Variant::Map& a) : handler(h), redelivered(r), ttl(t), annotations(a) {}
    void operator()(const AMQFrame& f)
    {
        AMQFrame copy = f;
        if (redelivered || ttl || annotations.size()) {
            copy.cloneBody();
            if (annotations.size()) {
                MessageProperties* props =
                    copy.castBody<AMQHeaderBody>()->get<MessageProperties>(true);
                for (qpid::types::Variant::Map::const_iterator i = annotations.begin();
                     i != annotations.end(); ++i) {
                    props->getApplicationHeaders().set(i->first, qpid::amqp_0_10::translate(i->second));
                }
            }
            if (redelivered || ttl) {
                DeliveryProperties* dp =
                    copy.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true);
                if (ttl) dp->setTtl(ttl);
                if (redelivered) dp->setRedelivered(redelivered);
            }
        }
        handler.handle(copy);
    }
  private:
    FrameHandler& handler;
    bool redelivered;
    uint64_t ttl;
    const qpid::types::Variant::Map& annotations;
};

void MessageTransfer::sendHeader(framing::FrameHandler& out, uint16_t /*maxFrameSize*/,
                                 bool redelivered, uint64_t ttl,
                                 const qpid::types::Variant::Map& annotations) const
{
    SendHeader f(out, redelivered, ttl, annotations);
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
    decodeContent(buffer, buffer.available());
}

void MessageTransfer::decodeContent(framing::Buffer& buffer, size_t size)
{
    if (size) {
        //get the data as a string and set that as the content
        //body on a frame then add that frame to the frameset
        AMQFrame frame((AMQContentBody()));
        frame.castBody<AMQContentBody>()->decode(buffer, size);
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

std::string MessageTransfer::printProperties() const
{
    std::stringstream out;
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();
    if (mp) {
        out << *mp;
    }
    return out.str();
}

void MessageTransfer::processProperties(qpid::amqp::MapHandler& handler) const
{
    const qpid::framing::MessageProperties* mp = getProperties<qpid::framing::MessageProperties>();
    if (mp && mp->hasApplicationHeaders()) {
        const FieldTable ft = mp->getApplicationHeaders();
        for (FieldTable::const_iterator i = ft.begin(); i != ft.end(); ++i) {
            qpid::types::Variant v;
            qpid::amqp_0_10::translate(i->second, v);
            qpid::amqp::CharSequence key = {i->first.data(), i->first.size()};
            switch (v.getType()) {
            case qpid::types::VAR_VOID:
                handler.handleVoid(key); break;
            case qpid::types::VAR_BOOL:
                handler.handleBool(key, v); break;
            case qpid::types::VAR_UINT8:
                handler.handleUint8(key, v); break;
            case qpid::types::VAR_UINT16:
                handler.handleUint16(key, v); break;
            case qpid::types::VAR_UINT32:
                handler.handleUint32(key, v); break;
            case qpid::types::VAR_UINT64:
                handler.handleUint64(key, v); break;
            case qpid::types::VAR_INT8:
                handler.handleInt8(key, v); break;
            case qpid::types::VAR_INT16:
                handler.handleInt16(key, v); break;
            case qpid::types::VAR_INT32:
                handler.handleInt32(key, v); break;
            case qpid::types::VAR_INT64:
                handler.handleInt64(key, v); break;
            case qpid::types::VAR_FLOAT:
                handler.handleFloat(key, v); break;
            case qpid::types::VAR_DOUBLE:
                handler.handleDouble(key, v); break;
            case qpid::types::VAR_STRING: {
                std::string s(v);
                qpid::amqp::CharSequence value = {s.data(), s.size()};
                qpid::amqp::CharSequence encoding = {0, 0};
                handler.handleString(key, value, encoding);
                break;
            }
            case qpid::types::VAR_MAP:
            case qpid::types::VAR_LIST:
            case qpid::types::VAR_UUID:
                 QPID_LOG(debug, "Unhandled key!" << v);
                 break;
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
        mp->getApplicationHeaders().set(i->first, qpid::amqp_0_10::translate(i->second));
    }
    return clone;
}
}}} // namespace qpid::broker::amqp_0_10
