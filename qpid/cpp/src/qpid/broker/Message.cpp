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

#include "qpid/broker/Message.h"

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/MapHandler.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/OwnershipToken.h"
#include "qpid/management/ManagementObject.h"
#include "qpid/management/Manageable.h"
#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/assert.h"

#include <algorithm>
#include <string.h>
#include <time.h>

using boost::intrusive_ptr;
using qpid::sys::AbsTime;
using qpid::sys::Duration;
using qpid::sys::TIME_MSEC;
using qpid::sys::FAR_FUTURE;
using qpid::amqp::CharSequence;
using qpid::amqp::MapHandler;
using std::string;

namespace qpid {
namespace broker {

Message::Message() : deliveryCount(-1), alreadyAcquired(false), replicationId(0), isReplicationIdSet(false)
{}

Message::Message(boost::intrusive_ptr<SharedState> e, boost::intrusive_ptr<PersistableMessage> p)
    : sharedState(e), persistentContext(p), deliveryCount(-1), alreadyAcquired(false), replicationId(0), isReplicationIdSet(false)
{
    if (persistentContext) persistentContext->setIngressCompletion(e);
}

Message::~Message() {}


std::string Message::getRoutingKey() const
{
    return getEncoding().getRoutingKey();
}

std::string Message::getTo() const
{
    return getEncoding().getTo();
}
std::string Message::getSubject() const
{
    return getEncoding().getSubject();
}
std::string Message::getReplyTo() const
{
    return getEncoding().getReplyTo();
}

bool Message::isPersistent() const
{
    return getEncoding().isPersistent();
}

uint64_t Message::getMessageSize() const
{
    return getEncoding().getMessageSize();
}

boost::intrusive_ptr<AsyncCompletion> Message::getIngressCompletion() const
{
    return sharedState;
}

namespace
{
const std::string X_QPID_TRACE("x-qpid.trace");
}

bool Message::isExcluded(const std::vector<std::string>& excludes) const
{
    std::string traceStr = getEncoding().getAnnotationAsString(X_QPID_TRACE);
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
    return false;
}

void Message::addTraceId(const std::string& id)
{
    std::string trace = getEncoding().getAnnotationAsString(X_QPID_TRACE);
    if (trace.empty()) {
        addAnnotation(X_QPID_TRACE, id);
    } else if (trace.find(id) == std::string::npos) {
        trace += ",";
        trace += id;
        addAnnotation(X_QPID_TRACE, trace);
    }
}

void Message::clearTrace()
{
    addAnnotation(X_QPID_TRACE, std::string());
}

uint64_t Message::getTimestamp() const
{
    return sharedState ? sharedState->getTimestamp() : 0;
}

uint64_t Message::getTtl() const
{
    uint64_t ttl;
    if (getTtl(ttl, 1)/*set to 1 if expired*/) {
        return ttl;
    } else {
        return 0;
    }
}

bool Message::getTtl(uint64_t& ttl) const
{
    return getTtl(ttl, 0); //set to 0 if expired
}

bool Message::getTtl(uint64_t& ttl, uint64_t expiredValue) const
{
    if (sharedState->getTtl(ttl) && sharedState->getExpiration() < FAR_FUTURE) {
        sys::Duration remaining = sharedState->getTimeToExpiration();
        // convert from ns to ms
        ttl = (int64_t(remaining) >= 1000000 ? int64_t(remaining)/1000000 : expiredValue);
        return true;
    } else {
        return false;
    }
}

void Message::addAnnotation(const std::string& key, const qpid::types::Variant& value)
{
    annotations.get()[key] = value;
    annotationsChanged();
}

void Message::annotationsChanged()
{
    if (persistentContext) {
        uint64_t id = persistentContext->getPersistenceId();
        persistentContext = persistentContext->merge(getAnnotations());
        persistentContext->setIngressCompletion(sharedState);
        persistentContext->setPersistenceId(id);
    }
}

uint8_t Message::getPriority() const
{
    return getEncoding().getPriority();
}

bool Message::getIsManagementMessage() const { return sharedState->getIsManagementMessage(); }

const Connection* Message::getPublisher() const { return sharedState->getPublisher(); }
bool Message::isLocalTo(const OwnershipToken* token) const {
    return token && sharedState->getPublisher() && token->isLocal(sharedState->getPublisher());
}


qpid::framing::SequenceNumber Message::getSequence() const
{
    return sequence;
}
void Message::setSequence(const qpid::framing::SequenceNumber& s)
{
    sequence = s;
}

MessageState Message::getState() const
{
    return state;
}
void Message::setState(MessageState s)
{
    state = s;
}
namespace {
const qpid::types::Variant::Map EMPTY_MAP;
}

const qpid::types::Variant::Map& Message::getAnnotations() const
{
    return annotations ? *annotations : EMPTY_MAP;
}

qpid::types::Variant Message::getAnnotation(const std::string& key) const
{
    const qpid::types::Variant::Map& a = getAnnotations();
    qpid::types::Variant::Map::const_iterator i = a.find(key);
    if (i != a.end()) return i->second;
    //FIXME: modify Encoding interface to allow retrieval of
    //annotations of different types from the message data as received
    //off the wire
    return qpid::types::Variant(getEncoding().getAnnotationAsString(key));
}

std::string Message::getUserId() const
{
    return sharedState->getUserId();
}

Message::SharedState& Message::getSharedState()
{
    return *sharedState;
}
const Message::Encoding& Message::getEncoding() const
{
    return *sharedState;
}
Message::operator bool() const
{
    return !!sharedState;
}

std::string Message::getContent() const
{
    return sharedState->getContent();
}

std::string Message::getPropertyAsString(const std::string& key) const
{
    return sharedState->getPropertyAsString(key);
}
namespace {
class PropertyRetriever : public MapHandler
{
  public:
    PropertyRetriever(const std::string& key) : name(key) {}
    void handleVoid(const CharSequence&) {}
    void handleBool(const CharSequence& key, bool value) { handle(key, value); }
    void handleUint8(const CharSequence& key, uint8_t value) { handle(key, value); }
    void handleUint16(const CharSequence& key, uint16_t value) { handle(key, value); }
    void handleUint32(const CharSequence& key, uint32_t value) { handle(key, value); }
    void handleUint64(const CharSequence& key, uint64_t value) { handle(key, value); }
    void handleInt8(const CharSequence& key, int8_t value) { handle(key, value); }
    void handleInt16(const CharSequence& key, int16_t value) { handle(key, value); }
    void handleInt32(const CharSequence& key, int32_t value) { handle(key, value); }
    void handleInt64(const CharSequence& key, int64_t value) { handle(key, value); }
    void handleFloat(const CharSequence& key, float value) { handle(key, value); }
    void handleDouble(const CharSequence& key, double value) { handle(key, value); }
    void handleString(const CharSequence& key, const CharSequence& value, const CharSequence& /*encoding*/)
    {
        if (matches(key)) result = std::string(value.data, value.size);
    }
    qpid::types::Variant getResult() { return result; }

  private:
    std::string name;
    qpid::types::Variant result;

    bool matches(const CharSequence& key)
    {
        return name.size()==key.size &&
            ::strncmp(key.data, name.data(), key.size) == 0;
    }

    template <typename T> void handle(const CharSequence& key, T value)
    {
        if (matches(key)) result = value;
    }
};
}
qpid::types::Variant Message::getProperty(const std::string& key) const
{
    PropertyRetriever r(key);
    sharedState->processProperties(r);
    return r.getResult();
}

std::string Message::printProperties() const
{
    return sharedState->printProperties();
}

boost::intrusive_ptr<PersistableMessage> Message::getPersistentContext() const
{
    return persistentContext;
}

void Message::processProperties(MapHandler& handler) const
{
    sharedState->processProperties(handler);
}

bool Message::hasReplicationId() const {
    return isReplicationIdSet;
}

uint64_t Message::getReplicationId() const {
    return replicationId;
}

void Message::setReplicationId(framing::SequenceNumber id) {
    replicationId = id;
    isReplicationIdSet = true;
}

sys::AbsTime Message::getExpiration() const
{
    return sharedState->getExpiration();
}

Message::SharedStateImpl::SharedStateImpl() : publisher(0), expiration(qpid::sys::FAR_FUTURE), isManagementMessage(false) {}

const Connection* Message::SharedStateImpl::getPublisher() const
{
    return publisher;
}

void Message::SharedStateImpl::setPublisher(const Connection* p)
{
    publisher = p;
}

sys::AbsTime Message::SharedStateImpl::getExpiration() const
{
    return expiration;
}

void Message::SharedStateImpl::setExpiration(sys::AbsTime e)
{
    expiration = e;
}

sys::Duration Message::SharedStateImpl::getTimeToExpiration() const
{
    return sys::Duration(sys::AbsTime::now(), expiration);
}

void Message::SharedStateImpl::computeExpiration()
{
    //TODO: this is still quite 0-10 specific...
    uint64_t ttl;
    if (getTtl(ttl)) {
        // Use higher resolution time for the internal expiry calculation.
        // Prevent overflow as a signed int64_t
        Duration duration(std::min(ttl * TIME_MSEC,
                                   (uint64_t) std::numeric_limits<int64_t>::max()));
        expiration = AbsTime(sys::AbsTime::now(), duration);
    }
}

bool Message::SharedStateImpl::getIsManagementMessage() const
{
    return isManagementMessage;
}
void Message::SharedStateImpl::setIsManagementMessage(bool b)
{
    isManagementMessage = b;
}

}} // namespace qpid::broker
