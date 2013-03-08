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
#include "qpid/broker/MapHandler.h"
#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"

#include <algorithm>
#include <string.h>
#include <time.h>

using boost::intrusive_ptr;
using qpid::sys::AbsTime;
using qpid::sys::Duration;
using qpid::sys::TIME_MSEC;
using qpid::sys::FAR_FUTURE;
using std::string;

namespace qpid {
namespace broker {

Message::Message() : deliveryCount(0), publisher(0), expiration(FAR_FUTURE), timestamp(0), isManagementMessage(false) {}
Message::Message(boost::intrusive_ptr<Encoding> e, boost::intrusive_ptr<PersistableMessage> p)
    : encoding(e), persistentContext(p), deliveryCount(0), publisher(0), expiration(FAR_FUTURE), timestamp(0), isManagementMessage(false)
{
    if (persistentContext) persistentContext->setIngressCompletion(e);
}
Message::~Message() {}


std::string Message::getRoutingKey() const
{
    return getEncoding().getRoutingKey();
}

bool Message::isPersistent() const
{
    return getEncoding().isPersistent();
}

uint64_t Message::getContentSize() const
{
    return getEncoding().getContentSize();
}

boost::intrusive_ptr<AsyncCompletion> Message::getIngressCompletion() const
{
    return encoding;
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
        annotations[X_QPID_TRACE] = id;
    } else if (trace.find(id) == std::string::npos) {
        trace += ",";
        trace += id;
        annotations[X_QPID_TRACE] = trace;
    }
    annotationsChanged();
}

void Message::clearTrace()
{
    annotations[X_QPID_TRACE] = std::string();
    annotationsChanged();
}

void Message::setTimestamp()
{
    timestamp = ::time(0);   // AMQP-0.10: posix time_t - secs since Epoch
}

uint64_t Message::getTimestamp() const
{
    return timestamp;
}

uint64_t Message::getTtl() const
{
    uint64_t ttl;
    if (encoding->getTtl(ttl) && expiration < FAR_FUTURE) {
        sys::AbsTime current(
            expiryPolicy ? expiryPolicy->getCurrentTime() : sys::AbsTime::now());
        sys::Duration ttl(current, getExpiration());
        // convert from ns to ms; set to 1 if expired
        return (int64_t(ttl) >= 1000000 ? int64_t(ttl)/1000000 : 1);
    } else {
        return 0;
    }
}

bool Message::getTtl(uint64_t ttl) const
{
    if (encoding->getTtl(ttl) && expiration < FAR_FUTURE) {
        sys::Duration remaining(sys::AbsTime::now(), getExpiration());
        // convert from ns to ms; set to 0 if expired
        ttl = (int64_t(remaining) >= 1000000 ? int64_t(remaining)/1000000 : 0);
        return true;
    } else {
        return false;
    }
}

void Message::computeExpiration(const boost::intrusive_ptr<ExpiryPolicy>& e)
{
    //TODO: this is still quite 0-10 specific...
    uint64_t ttl;
    if (getEncoding().getTtl(ttl)) {
        if (e) {
            // Use higher resolution time for the internal expiry calculation.
            // Prevent overflow as a signed int64_t
            Duration duration(std::min(ttl * TIME_MSEC,
                                       (uint64_t) std::numeric_limits<int64_t>::max()));
            expiration = AbsTime(e->getCurrentTime(), duration);
            setExpiryPolicy(e);
        }
    }
}

void Message::addAnnotation(const std::string& key, const qpid::types::Variant& value)
{
    annotations[key] = value;
    annotationsChanged();
}

void Message::annotationsChanged()
{
    if (persistentContext) {
        persistentContext = persistentContext->merge(annotations);
        persistentContext->setIngressCompletion(encoding);
    }
}

void Message::setExpiryPolicy(const boost::intrusive_ptr<ExpiryPolicy>& e) {
    expiryPolicy = e;
}

bool Message::hasExpired() const
{
    return expiryPolicy && expiryPolicy->hasExpired(*this);
}

uint8_t Message::getPriority() const
{
    return getEncoding().getPriority();
}

bool Message::getIsManagementMessage() const { return isManagementMessage; }
void Message::setIsManagementMessage(bool b) { isManagementMessage = b; }
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

const qpid::types::Variant::Map& Message::getAnnotations() const
{
    return annotations;
}

qpid::types::Variant Message::getAnnotation(const std::string& key) const
{
    qpid::types::Variant::Map::const_iterator i = annotations.find(key);
    if (i != annotations.end()) return i->second;
    //FIXME: modify Encoding interface to allow retrieval of
    //annotations of different types from the message data as received
    //off the wire
    return qpid::types::Variant(getEncoding().getAnnotationAsString(key));
}

std::string Message::getUserId() const
{
    return encoding->getUserId();
}

Message::Encoding& Message::getEncoding()
{
    return *encoding;
}
const Message::Encoding& Message::getEncoding() const
{
    return *encoding;
}
Message::operator bool() const
{
    return encoding;
}

std::string Message::getContent() const
{
    return encoding->getContent();
}

std::string Message::getPropertyAsString(const std::string& key) const
{
    return encoding->getPropertyAsString(key);
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
        return ::strncmp(key.data, name.data(), std::min(key.size, name.size())) == 0;
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
    encoding->processProperties(r);
    return r.getResult();
}

boost::intrusive_ptr<PersistableMessage> Message::getPersistentContext() const
{
    return persistentContext;
}

void Message::processProperties(MapHandler& handler) const
{
    encoding->processProperties(handler);
}

}} // namespace qpid::broker
