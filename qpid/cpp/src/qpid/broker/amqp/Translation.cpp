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
#include "qpid/broker/amqp/Translation.h"
#include "qpid/broker/amqp/Outgoing.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/broker/Broker.h"
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/types/Variant.h"
#include "qpid/types/encodings.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace broker {
namespace amqp {
namespace {

const std::string EMPTY;
const std::string FORWARD_SLASH("/");
const std::string TEXT_PLAIN("text/plain");
const std::string SUBJECT_KEY("qpid.subject");
const std::string APP_ID("x-amqp-0-10.app-id");

qpid::framing::ReplyTo translate(const std::string address, Broker* broker)
{
    size_t i = address.find(FORWARD_SLASH);
    if (i == std::string::npos) {
        //is it a queue or an exchange?
        if (broker && broker->getQueues().find(address)) {
            return qpid::framing::ReplyTo(EMPTY, address);
        } else if (broker && broker->getExchanges().find(address)) {
            return qpid::framing::ReplyTo(address, EMPTY);
        } else {
            return qpid::framing::ReplyTo();
        }
    } else {
        return qpid::framing::ReplyTo(i > 0 ? address.substr(0, i) : EMPTY, (i+1) < address.size() ? address.substr(i+1) : EMPTY);
    }
}
std::string translate(const qpid::framing::ReplyTo r)
{
    if (r.getExchange().size()) {
        if (r.getRoutingKey().size()) return r.getExchange() + FORWARD_SLASH + r.getRoutingKey();
        else return r.getExchange();
    } else return r.getRoutingKey();
}
std::string translate(const qpid::amqp::CharSequence& chars)
{
    if (chars.data && chars.size) return std::string(chars.data, chars.size);
    else return EMPTY;
}
bool setMessageId(qpid::framing::MessageProperties& m, const qpid::amqp::CharSequence& chars)
{
    if (chars.data && chars.size) {
        if (chars.size == 16) {
            m.setMessageId(qpid::framing::Uuid(chars.data));
            return true;
        } else {
            std::istringstream in(translate(chars));
            qpid::framing::Uuid uuid;
            in >> uuid;
            if (!in.fail()) {
                m.setMessageId(uuid);
                return true;
            }
        }
    }
    return false;
}
class Properties_0_10 : public qpid::amqp::MessageEncoder::Properties
{
  public:
    bool hasMessageId() const { return messageProperties && messageProperties->hasMessageId(); }
    std::string getMessageId() const { return messageProperties ? messageProperties->getMessageId().str() : EMPTY; }
    bool hasUserId() const { return messageProperties && messageProperties->hasUserId(); }
    std::string getUserId() const { return messageProperties ? messageProperties->getUserId() : EMPTY; }
    bool hasTo() const { return getDestination().size() || hasSubject(); }
    std::string getTo() const { return  getDestination().size() ? getDestination() : getSubject(); }
    bool hasSubject() const
    {
        if (getDestination().empty()) {
            return getApplicationProperties().isSet(SUBJECT_KEY);
        } else {
            return deliveryProperties && deliveryProperties->hasRoutingKey();
        }
    }
    std::string getSubject() const
    {
        if (getDestination().empty()) {
            //message was sent to default exchange, routing key is the queue name
            return getApplicationProperties().getAsString(SUBJECT_KEY);
        } else if (deliveryProperties) {
            return deliveryProperties->getRoutingKey();
        } else {
            return EMPTY;
        }
    }
    bool hasReplyTo() const { return messageProperties && messageProperties->hasReplyTo(); }
    std::string getReplyTo() const { return messageProperties ? translate(messageProperties->getReplyTo()) : EMPTY; }
    bool hasCorrelationId() const { return messageProperties && messageProperties->hasCorrelationId(); }
    std::string getCorrelationId() const { return messageProperties ? messageProperties->getCorrelationId() : EMPTY; }
    bool hasContentType() const { return messageProperties && messageProperties->hasContentType(); }
    std::string getContentType() const { return messageProperties ? messageProperties->getContentType() : EMPTY; }
    bool hasContentEncoding() const { return messageProperties && messageProperties->hasContentEncoding(); }
    std::string getContentEncoding() const { return messageProperties ? messageProperties->getContentEncoding() : EMPTY; }
    bool hasAbsoluteExpiryTime() const { return deliveryProperties && deliveryProperties->hasExpiration(); }
    int64_t getAbsoluteExpiryTime() const { return deliveryProperties ? deliveryProperties->getExpiration() : 0; }
    bool hasCreationTime() const { return false; }
    int64_t getCreationTime() const { return 0; }
    bool hasGroupId() const {return false; }
    std::string getGroupId() const { return EMPTY; }
    bool hasGroupSequence() const { return false; }
    uint32_t getGroupSequence() const { return 0; }
    bool hasReplyToGroupId() const { return false; }
    std::string getReplyToGroupId() const { return EMPTY; }

    const qpid::framing::FieldTable& getApplicationProperties() const { return messageProperties->getApplicationHeaders(); }
    Properties_0_10(const qpid::broker::amqp_0_10::MessageTransfer& t) : transfer(t),
                                                                         messageProperties(transfer.getProperties<qpid::framing::MessageProperties>()),
                                                                         deliveryProperties(transfer.getProperties<qpid::framing::DeliveryProperties>())
    {}
  private:
    const qpid::broker::amqp_0_10::MessageTransfer& transfer;
    const qpid::framing::MessageProperties* messageProperties;
    const qpid::framing::DeliveryProperties* deliveryProperties;

    std::string getDestination() const
    {
        return transfer.getMethod<qpid::framing::MessageTransferBody>()->getDestination();
    }
};
}

Translation::Translation(const qpid::broker::Message& m, Broker* b) : original(m), broker(b) {}

boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> Translation::getTransfer()
{
    boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> t =
        boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer>(dynamic_cast<const qpid::broker::amqp_0_10::MessageTransfer*>(&original.getEncoding()));
    if (t) {
        return t;//no translation required
    } else {
        const Message* message = dynamic_cast<const Message*>(&original.getEncoding());
        if (message) {
            //translate 1.0 message into 0-10
            boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> transfer(new qpid::broker::amqp_0_10::MessageTransfer());
            qpid::framing::AMQFrame method((qpid::framing::MessageTransferBody(qpid::framing::ProtocolVersion(), EMPTY, 0, 0)));
            qpid::framing::AMQFrame header((qpid::framing::AMQHeaderBody()));
            qpid::framing::AMQFrame content((qpid::framing::AMQContentBody()));
            method.setEof(false);
            header.setBof(false);
            header.setEof(false);
            content.setBof(false);

            transfer->getFrames().append(method);
            transfer->getFrames().append(header);

            qpid::framing::MessageProperties* props =
                transfer->getFrames().getHeaders()->get<qpid::framing::MessageProperties>(true);

            if (message->isTypedBody()) {
                qpid::types::Variant body = message->getTypedBody();
                std::string& data = content.castBody<qpid::framing::AMQContentBody>()->getData();
                if (body.getType() == qpid::types::VAR_MAP) {
                    qpid::amqp_0_10::MapCodec::encode(body.asMap(), data);
                    props->setContentType(qpid::amqp_0_10::MapCodec::contentType);
                } else if (body.getType() == qpid::types::VAR_LIST) {
                    qpid::amqp_0_10::ListCodec::encode(body.asList(), data);
                    props->setContentType(qpid::amqp_0_10::ListCodec::contentType);
                } else if (body.getType() == qpid::types::VAR_STRING) {
                    data = body.getString();
                    if (body.getEncoding() == qpid::types::encodings::UTF8 || body.getEncoding() == qpid::types::encodings::ASCII) {
                        props->setContentType(TEXT_PLAIN);
                    }
                } else {
                    qpid::types::Variant::List container;
                    container.push_back(body);
                    qpid::amqp_0_10::ListCodec::encode(container, data);
                    props->setContentType(qpid::amqp_0_10::ListCodec::contentType);
                }
                transfer->getFrames().append(content);
                props->setContentLength(data.size());
            } else {
                qpid::amqp::CharSequence body = message->getBody();
                content.castBody<qpid::framing::AMQContentBody>()->getData().assign(body.data, body.size);
                transfer->getFrames().append(content);

                props->setContentLength(body.size);
            }

            qpid::amqp::MessageId mid = message->getMessageId();
            qpid::framing::Uuid uuid;
            switch (mid.type) {
              case qpid::amqp::MessageId::NONE:
                break;
              case qpid::amqp::MessageId::UUID:
              case qpid::amqp::MessageId::BYTES:
                if (mid.value.bytes.size == 0) break;
                if (setMessageId(*props, mid.value.bytes)) break;
              case qpid::amqp::MessageId::ULONG:
                QPID_LOG(info, "Skipping message id in translation from 1.0 to 0-10 as it is not a UUID");
                break;
            }

            qpid::amqp::MessageId cid = message->getCorrelationId();
            switch (cid.type) {
              case qpid::amqp::MessageId::NONE:
                break;
              case qpid::amqp::MessageId::UUID:
                assert(cid.value.bytes.size = 16);
                props->setCorrelationId(qpid::framing::Uuid(cid.value.bytes.data).str());
                break;
              case qpid::amqp::MessageId::BYTES:
                if (cid.value.bytes.size) {
                    props->setCorrelationId(translate(cid.value.bytes));
                }
                break;
              case qpid::amqp::MessageId::ULONG:
                props->setCorrelationId(boost::lexical_cast<std::string>(cid.value.ulong));
                break;
            }
            if (message->getReplyToAsCharSequence()) props->setReplyTo(translate(message->getReplyTo(), broker));
            if (message->getContentType()) props->setContentType(translate(message->getContentType()));
            if (message->getContentEncoding()) props->setContentEncoding(translate(message->getContentEncoding()));
            props->setUserId(message->getUserId());
            // TODO: FieldTable applicationHeaders;
            qpid::amqp::CharSequence ap = message->getApplicationProperties();
            if (ap) {
                qpid::amqp::Decoder d(ap.data, ap.size);
                qpid::amqp_0_10::translate(d.readMap(), props->getApplicationHeaders());
                std::string appid = props->getApplicationHeaders().getAsString(APP_ID);
                if (!appid.empty()) {
                    props->setAppId(appid);
                }
            }

            qpid::framing::DeliveryProperties* dp =
                transfer->getFrames().getHeaders()->get<qpid::framing::DeliveryProperties>(true);
            dp->setPriority(message->getPriority());
            if (message->isPersistent()) dp->setDeliveryMode(2);
            if (message->getRoutingKey().size()) {
                if (message->getRoutingKey().size() > std::numeric_limits<uint8_t>::max()) {
                    //have to truncate routing key as it is specified to be a str8
                    dp->setRoutingKey(message->getRoutingKey().substr(0,std::numeric_limits<uint8_t>::max()));
                } else {
                    dp->setRoutingKey(message->getRoutingKey());
                }
                props->getApplicationHeaders().setString(SUBJECT_KEY, message->getRoutingKey());
            }

            return transfer.get();
        } else {
            throw qpid::Exception("Could not write message data in AMQP 0-10 format");
        }
    }
}

void Translation::write(OutgoingFromQueue& out)
{
    const Message* message = dynamic_cast<const Message*>(original.getPersistentContext().get());
    //persistent context will contain any newly added annotations
    if (!message) message = dynamic_cast<const Message*>(&original.getEncoding());
    if (message) {
        //write annotations
        qpid::amqp::CharSequence deliveryAnnotations = message->getDeliveryAnnotations();
        qpid::amqp::CharSequence messageAnnotations = message->getMessageAnnotations();
        if (deliveryAnnotations.size) out.write(deliveryAnnotations.data, deliveryAnnotations.size);
        if (messageAnnotations.size) out.write(messageAnnotations.data, messageAnnotations.size);
        //write bare message
        qpid::amqp::CharSequence bareMessage = message->getBareMessage();
        if (bareMessage.size) out.write(bareMessage.data, bareMessage.size);
        //write footer:
        qpid::amqp::CharSequence footer = message->getFooter();
        if (footer.size) out.write(footer.data, footer.size);
    } else {
        const qpid::broker::amqp_0_10::MessageTransfer* transfer = dynamic_cast<const qpid::broker::amqp_0_10::MessageTransfer*>(&original.getEncoding());
        if (transfer) {
            Properties_0_10 properties(*transfer);
            qpid::types::Variant::Map applicationProperties;
            qpid::amqp_0_10::translate(properties.getApplicationProperties(), applicationProperties);
            if (properties.getContentType() == qpid::amqp_0_10::MapCodec::contentType) {
                qpid::types::Variant::Map content;
                qpid::amqp_0_10::MapCodec::decode(transfer->getContent(), content);
                size_t size = qpid::amqp::MessageEncoder::getEncodedSize(properties);
                size += qpid::amqp::MessageEncoder::getEncodedSize(applicationProperties, true) + 3;/*descriptor*/
                size += qpid::amqp::MessageEncoder::getEncodedSize(content, true) + 3/*descriptor*/;
                std::vector<char> buffer(size);
                qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
                encoder.writeProperties(properties);
                encoder.writeApplicationProperties(applicationProperties);
                encoder.writeMap(content, &qpid::amqp::message::AMQP_VALUE);
                out.write(&buffer[0], encoder.getPosition());
            } else if (properties.getContentType() == qpid::amqp_0_10::ListCodec::contentType) {
                qpid::types::Variant::List content;
                qpid::amqp_0_10::ListCodec::decode(transfer->getContent(), content);
                size_t size = qpid::amqp::MessageEncoder::getEncodedSize(properties);
                size += qpid::amqp::MessageEncoder::getEncodedSize(applicationProperties, true) + 3;/*descriptor*/
                size += qpid::amqp::MessageEncoder::getEncodedSize(content, true) + 3/*descriptor*/;
                std::vector<char> buffer(size);
                qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
                encoder.writeProperties(properties);
                encoder.writeApplicationProperties(applicationProperties);
                encoder.writeList(content, &qpid::amqp::message::AMQP_VALUE);
                out.write(&buffer[0], encoder.getPosition());
            } else {
                std::string content = transfer->getContent();
                size_t size = qpid::amqp::MessageEncoder::getEncodedSize(properties, applicationProperties, content);
                std::vector<char> buffer(size);
                qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
                encoder.writeProperties(properties);
                encoder.writeApplicationProperties(applicationProperties);
                if (content.size()) encoder.writeBinary(content, &qpid::amqp::message::DATA);
                out.write(&buffer[0], encoder.getPosition());
            }
        } else {
            QPID_LOG(error, "Could not write message data in AMQP 1.0 format");
        }
    }
}

}}} // namespace qpid::broker::amqp
