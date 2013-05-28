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
#include "qpid/messaging/amqp/SenderContext.h"
#include "qpid/messaging/amqp/EncodedMessage.h"
#include "qpid/messaging/AddressImpl.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/log/Statement.h"
extern "C" {
#include <proton/engine.h>
}
#include <boost/shared_ptr.hpp>
#include <string.h>

namespace qpid {
namespace messaging {
namespace amqp {
//TODO: proper conversion to wide string for address
SenderContext::SenderContext(pn_session_t* session, const std::string& n, const qpid::messaging::Address& a)
  : name(n),
    address(a),
    helper(address),
    sender(pn_sender(session, n.c_str())), capacity(1000) {}

SenderContext::~SenderContext()
{
    //pn_link_free(sender);
}

void SenderContext::close()
{
    pn_link_close(sender);
}

void SenderContext::setCapacity(uint32_t c)
{
    if (c < deliveries.size()) throw qpid::messaging::SenderError("Desired capacity is less than unsettled message count!");
    capacity = c;
}

uint32_t SenderContext::getCapacity()
{
    return capacity;
}

uint32_t SenderContext::getUnsettled()
{
    return processUnsettled();
}

const std::string& SenderContext::getName() const
{
    return name;
}

const std::string& SenderContext::getTarget() const
{
    return address.getName();
}

SenderContext::Delivery* SenderContext::send(const qpid::messaging::Message& message)
{
    if (processUnsettled() < capacity && pn_link_credit(sender)) {
        deliveries.push_back(Delivery(nextId++));
        Delivery& delivery = deliveries.back();
        delivery.encode(MessageImplAccess::get(message), address);
        delivery.send(sender);
        return &delivery;
    } else {
        return 0;
    }
}

uint32_t SenderContext::processUnsettled()
{
    //remove accepted messages from front of deque
    while (!deliveries.empty() && deliveries.front().accepted()) {
        deliveries.front().settle();
        deliveries.pop_front();
    }
    return deliveries.size();
}
namespace {
class HeaderAdapter : public qpid::amqp::MessageEncoder::Header
{
  public:
    HeaderAdapter(const qpid::messaging::MessageImpl& impl) : msg(impl) {}
    virtual bool isDurable() const
    {
        return msg.isDurable();
    }
    virtual uint8_t getPriority() const
    {
        return msg.getPriority();
    }
    virtual bool hasTtl() const
    {
        return msg.getTtl();
    }
    virtual uint32_t getTtl() const
    {
        return msg.getTtl();
    }
    virtual bool isFirstAcquirer() const
    {
        return false;
    }
    virtual uint32_t getDeliveryCount() const
    {
        return msg.isRedelivered() ? 1 : 0;
    }
  private:
    const qpid::messaging::MessageImpl& msg;
};
const std::string EMPTY;
const std::string FORWARD_SLASH("/");

class PropertiesAdapter : public qpid::amqp::MessageEncoder::Properties
{
  public:
    PropertiesAdapter(const qpid::messaging::MessageImpl& impl, const std::string& s) : msg(impl), subject(s) {}
    bool hasMessageId() const
    {
        return getMessageId().size();
    }
    std::string getMessageId() const
    {
        return msg.getMessageId();
    }

    bool hasUserId() const
    {
        return getUserId().size();
    }

    std::string getUserId() const
    {
        return msg.getUserId();
    }

    bool hasTo() const
    {
        return false;//not yet supported
    }

    std::string getTo() const
    {
        return EMPTY;//not yet supported
    }

    bool hasSubject() const
    {
        return subject.size() || getSubject().size();
    }

    std::string getSubject() const
    {
        return subject.size() ? subject : msg.getSubject();
    }

    bool hasReplyTo() const
    {
        return msg.getReplyTo();
    }

    std::string getReplyTo() const
    {
        Address a = msg.getReplyTo();
        if (a.getSubject().size()) {
            return a.getName() + FORWARD_SLASH + a.getSubject();
        } else {
            return a.getName();
        }
    }

    bool hasCorrelationId() const
    {
        return getCorrelationId().size();
    }

    std::string getCorrelationId() const
    {
        return msg.getCorrelationId();
    }

    bool hasContentType() const
    {
        return getContentType().size();
    }

    std::string getContentType() const
    {
        return msg.getContentType();
    }

    bool hasContentEncoding() const
    {
        return false;//not yet supported
    }

    std::string getContentEncoding() const
    {
        return EMPTY;//not yet supported
    }

    bool hasAbsoluteExpiryTime() const
    {
        return false;//not yet supported
    }

    int64_t getAbsoluteExpiryTime() const
    {
        return 0;//not yet supported
    }

    bool hasCreationTime() const
    {
        return false;//not yet supported
    }

    int64_t getCreationTime() const
    {
        return 0;//not yet supported
    }

    bool hasGroupId() const
    {
        return false;//not yet supported
    }

    std::string getGroupId() const
    {
        return EMPTY;//not yet supported
    }

    bool hasGroupSequence() const
    {
        return false;//not yet supported
    }

    uint32_t getGroupSequence() const
    {
        return 0;//not yet supported
    }

    bool hasReplyToGroupId() const
    {
        return false;//not yet supported
    }

    std::string getReplyToGroupId() const
    {
        return EMPTY;//not yet supported
    }
  private:
    const qpid::messaging::MessageImpl& msg;
    const std::string subject;
};

bool changedSubject(const qpid::messaging::MessageImpl& msg, const qpid::messaging::Address& address)
{
    return address.getSubject().size() && address.getSubject() != msg.getSubject();
}

}

SenderContext::Delivery::Delivery(int32_t i) : id(i), token(0) {}

void SenderContext::Delivery::encode(const qpid::messaging::MessageImpl& msg, const qpid::messaging::Address& address)
{
    boost::shared_ptr<const EncodedMessage> original = msg.getEncoded();

    if (original && !changedSubject(msg, address)) { //still have the content as received, send at least the bare message unaltered
        //do we need to alter the header? are durable, priority, ttl, first-acquirer, delivery-count different from what was received?
        if (original->hasHeaderChanged(msg)) {
            //since as yet have no annotations, just write the revised header then the rest of the message as received
            encoded.resize(16/*max header size*/ + original->getBareMessage().size);
            qpid::amqp::MessageEncoder encoder(encoded.getData(), encoded.getSize());
            HeaderAdapter header(msg);
            encoder.writeHeader(header);
            ::memcpy(encoded.getData() + encoder.getPosition(), original->getBareMessage().data, original->getBareMessage().size);
        } else {
            //since as yet have no annotations, if the header hasn't
            //changed and we still have the original bare message, can
            //send the entire content as is
            encoded.resize(original->getSize());
            ::memcpy(encoded.getData(), original->getData(), original->getSize());
        }
    } else {
        HeaderAdapter header(msg);
        PropertiesAdapter properties(msg, address.getSubject());
        //compute size:
        encoded.resize(qpid::amqp::MessageEncoder::getEncodedSize(header, properties, msg.getHeaders(), msg.getBytes()));
        QPID_LOG(debug, "Sending message, buffer is " << encoded.getSize() << " bytes")
        qpid::amqp::MessageEncoder encoder(encoded.getData(), encoded.getSize());
        //write header:
        encoder.writeHeader(header);
        //write delivery-annotations, write message-annotations (none yet supported)
        //write properties
        encoder.writeProperties(properties);
        //write application-properties
        encoder.writeApplicationProperties(msg.getHeaders());
        //write body
        if (msg.getBytes().size()) encoder.writeBinary(msg.getBytes(), &qpid::amqp::message::DATA);//structured content not yet directly supported
        if (encoder.getPosition() < encoded.getSize()) {
            QPID_LOG(debug, "Trimming buffer from " << encoded.getSize() << " to " << encoder.getPosition());
            encoded.trim(encoder.getPosition());
        }
        //write footer (no annotations yet supported)
    }
}
void SenderContext::Delivery::send(pn_link_t* sender)
{
    pn_delivery_tag_t tag;
    tag.size = sizeof(id);
    tag.bytes = reinterpret_cast<const char*>(&id);
    token = pn_delivery(sender, tag);
    pn_link_send(sender, encoded.getData(), encoded.getSize());
    pn_link_advance(sender);
}

bool SenderContext::Delivery::accepted()
{
    return pn_delivery_remote_state(token) == PN_ACCEPTED;
}
void SenderContext::Delivery::settle()
{
    pn_delivery_settle(token);
}
void SenderContext::verify(pn_terminus_t* target)
{
    helper.checkAssertion(target, AddressHelper::FOR_SENDER);
}
void SenderContext::configure()
{
    configure(pn_link_target(sender));
}
void SenderContext::configure(pn_terminus_t* target)
{
    helper.configure(target, AddressHelper::FOR_SENDER);
}

bool SenderContext::settled()
{
    return processUnsettled() == 0;
}

Address SenderContext::getAddress() const
{
    return address;
}

}}} // namespace qpid::messaging::amqp
