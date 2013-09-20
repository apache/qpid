#ifndef _broker_Message_h
#define _broker_Message_h

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

#include "qpid/RefCounted.h"
#include "qpid/broker/ExpiryPolicy.h"
#include "qpid/broker/PersistableMessage.h"
//TODO: move the following out of framing or replace it
#include "qpid/framing/SequenceNumber.h"
#include "qpid/sys/Time.h"
#include "qpid/types/Variant.h"

#include "qpid/broker/BrokerImportExport.h"

#include <string>
#include <vector>
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace amqp {
class MapHandler;
struct MessageId;
}

namespace management {
class ObjectId;
class Manageable;
}

namespace broker {
class OwnershipToken;
class Connection;

enum MessageState
{
    AVAILABLE=1,
    ACQUIRED=2,
    DELETED=4,
    UNAVAILABLE=8
};

class Message {
public:
    class Encoding : public IngressCompletion
    {
      public:
        virtual ~Encoding() {}
        virtual std::string getRoutingKey() const = 0;
        virtual bool isPersistent() const = 0;
        virtual uint8_t getPriority() const = 0;
        virtual uint64_t getContentSize() const = 0;
        virtual qpid::amqp::MessageId getMessageId() const = 0;
        virtual qpid::amqp::MessageId getCorrelationId() const = 0;
        virtual std::string getPropertyAsString(const std::string& key) const = 0;
        virtual std::string getAnnotationAsString(const std::string& key) const = 0;
        virtual bool getTtl(uint64_t&) const = 0;
        virtual std::string getContent() const = 0;
        virtual void processProperties(qpid::amqp::MapHandler&) const = 0;
        virtual std::string getUserId() const = 0;
    };

    QPID_BROKER_EXTERN Message(boost::intrusive_ptr<Encoding>, boost::intrusive_ptr<PersistableMessage>);
    QPID_BROKER_EXTERN Message();
    QPID_BROKER_EXTERN ~Message();

    bool isRedelivered() const { return deliveryCount; }
    void deliver() { ++deliveryCount; }
    void undeliver() { --deliveryCount; }
    int getDeliveryCount() const { return deliveryCount; }
    void resetDeliveryCount() { deliveryCount = -1; }

    void setPublisher(const Connection& p);
    const Connection* getPublisher() const;
    bool isLocalTo(const OwnershipToken*) const;

    QPID_BROKER_EXTERN std::string getRoutingKey() const;
    QPID_BROKER_EXTERN bool isPersistent() const;

    /** determine msg expiration time using the TTL value if present */
    QPID_BROKER_EXTERN void computeExpiration(const boost::intrusive_ptr<ExpiryPolicy>& e);
    void setExpiryPolicy(const boost::intrusive_ptr<ExpiryPolicy>& e);

    bool hasExpired() const;
    sys::AbsTime getExpiration() const { return expiration; }
    void setExpiration(sys::AbsTime exp) { expiration = exp; }
    uint64_t getTtl() const;
    QPID_BROKER_EXTERN bool getTtl(uint64_t&) const;

    /** set the timestamp delivery property to the current time-of-day */
    QPID_BROKER_EXTERN void setTimestamp();
    QPID_BROKER_EXTERN uint64_t getTimestamp() const;

    QPID_BROKER_EXTERN void addAnnotation(const std::string& key, const qpid::types::Variant& value);
    QPID_BROKER_EXTERN bool isExcluded(const std::vector<std::string>& excludes) const;
    QPID_BROKER_EXTERN void addTraceId(const std::string& id);
    QPID_BROKER_EXTERN void clearTrace();
    QPID_BROKER_EXTERN uint8_t getPriority() const;
    QPID_BROKER_EXTERN std::string getPropertyAsString(const std::string& key) const;
    QPID_BROKER_EXTERN qpid::types::Variant getProperty(const std::string& key) const;
    void processProperties(qpid::amqp::MapHandler&) const;

    QPID_BROKER_EXTERN uint64_t getContentSize() const;

    QPID_BROKER_EXTERN Encoding& getEncoding();
    QPID_BROKER_EXTERN const Encoding& getEncoding() const;
    QPID_BROKER_EXTERN operator bool() const;

    bool getIsManagementMessage() const;
    void setIsManagementMessage(bool b);

    QPID_BROKER_EXTERN qpid::framing::SequenceNumber getSequence() const;
    QPID_BROKER_EXTERN void setSequence(const qpid::framing::SequenceNumber&);

    MessageState getState() const;
    void setState(MessageState);

    QPID_BROKER_EXTERN qpid::types::Variant getAnnotation(const std::string& key) const;
    QPID_BROKER_EXTERN const qpid::types::Variant::Map& getAnnotations() const;
    QPID_BROKER_EXTERN std::string getUserId() const;

    QPID_BROKER_EXTERN std::string getContent() const;//Used for ha, management, when content needs to be decoded

    QPID_BROKER_EXTERN boost::intrusive_ptr<AsyncCompletion> getIngressCompletion() const;
    QPID_BROKER_EXTERN boost::intrusive_ptr<PersistableMessage> getPersistentContext() const;
    QPID_BROKER_EXTERN uint64_t getReplicationId() const;
    QPID_BROKER_EXTERN void setReplicationId(framing::SequenceNumber id);

  private:
    boost::intrusive_ptr<Encoding> encoding;
    boost::intrusive_ptr<PersistableMessage> persistentContext;
    int deliveryCount;
    const Connection* publisher;
    qpid::sys::AbsTime expiration;
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;
    uint64_t timestamp;
    qpid::types::Variant::Map annotations;
    bool isManagementMessage;
    MessageState state;
    qpid::framing::SequenceNumber sequence;
    framing::SequenceNumber replicationId;

    void annotationsChanged();
};

}}


#endif
