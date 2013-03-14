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
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/types/Variant.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace broker {
namespace amqp {
namespace {

const std::string EMPTY;
const std::string FORWARD_SLASH("/");

std::string translate(const qpid::framing::ReplyTo r)
{
    if (r.hasExchange()) {
        if (r.hasRoutingKey()) return r.getExchange() + FORWARD_SLASH + r.getRoutingKey();
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
    bool hasSubject() const { return deliveryProperties && getDestination().size() && deliveryProperties->hasRoutingKey(); }
    std::string getSubject() const { return deliveryProperties && getDestination().size() ? deliveryProperties->getRoutingKey() : EMPTY; }
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

    const qpid::framing::FieldTable& getApplicationProperties() { return messageProperties->getApplicationHeaders(); }
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

Translation::Translation(const qpid::broker::Message& m) : original(m) {}


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

            qpid::amqp::CharSequence body = message->getBody();
            content.castBody<qpid::framing::AMQContentBody>()->getData().assign(body.data, body.size);
            transfer->getFrames().append(content);

            qpid::framing::MessageProperties* props =
                transfer->getFrames().getHeaders()->get<qpid::framing::MessageProperties>(true);
            props->setContentLength(body.size);

            qpid::amqp::MessageId mid = message->getMessageId();
            qpid::framing::Uuid uuid;
            switch (mid.type) {
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
            // TODO: ReplyTo - there is no way to reliably determine
            // the type of the node from just its name, unless we
            // query the brokers registries

            if (message->getContentType()) props->setContentType(translate(message->getContentType()));
            if (message->getContentEncoding()) props->setContentEncoding(translate(message->getContentEncoding()));
            props->setUserId(message->getUserId());
            // TODO: FieldTable applicationHeaders;
            qpid::amqp::CharSequence ap = message->getApplicationProperties();
            if (ap) {
                qpid::amqp::Decoder d(ap.data, ap.size);
                qpid::amqp_0_10::translate(d.readMap(), props->getApplicationHeaders());
            }

            qpid::framing::DeliveryProperties* dp =
                transfer->getFrames().getHeaders()->get<qpid::framing::DeliveryProperties>(true);
            dp->setPriority(message->getPriority());
            if (message->isPersistent()) dp->setDeliveryMode(2);
            if (message->getRoutingKey().size()) dp->setRoutingKey(message->getRoutingKey());

            return transfer.get();
        } else {
            throw qpid::Exception("Could not write message data in AMQP 0-10 format");
        }
    }
}

void Translation::write(OutgoingFromQueue& out)
{
    const Message* message = dynamic_cast<const Message*>(&original.getEncoding());
    if (message) {
        //write annotations
        //TODO: merge in any newly added annotations
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
            std::string content = transfer->getContent();
            size_t size = qpid::amqp::MessageEncoder::getEncodedSize(properties, applicationProperties, content);
            std::vector<char> buffer(size);
            qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
            encoder.writeProperties(properties);
            encoder.writeApplicationProperties(applicationProperties);
            encoder.writeBinary(content, &qpid::amqp::message::DATA);
            out.write(&buffer[0], encoder.getPosition());
        } else {
            QPID_LOG(error, "Could not write message data in AMQP 1.0 format");
        }
    }
}

}}} // namespace qpid::broker::amqp
