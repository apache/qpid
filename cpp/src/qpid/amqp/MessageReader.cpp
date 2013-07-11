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
#include "qpid/amqp/MessageReader.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"

using namespace qpid::amqp::message;

namespace qpid {
namespace amqp {
namespace {

//header fields:
const size_t DURABLE(0);
const size_t PRIORITY(1);
const size_t TTL(2);
const size_t FIRST_ACQUIRER(3);
const size_t DELIVERY_COUNT(4);

//properties fields:
const size_t MESSAGE_ID(0);
const size_t USER_ID(1);
const size_t TO(2);
const size_t SUBJECT(3);
const size_t REPLY_TO(4);
const size_t CORRELATION_ID(5);
const size_t CONTENT_TYPE(6);
const size_t CONTENT_ENCODING(7);
const size_t ABSOLUTE_EXPIRY_TIME(8);
const size_t CREATION_TIME(9);
const size_t GROUP_ID(10);
const size_t GROUP_SEQUENCE(11);
const size_t REPLY_TO_GROUP_ID(12);

}

/*
Reader& MessageReader::HeaderReader::getReader(size_t index)
{
    switch (index) {
      case DURABLE: return durableReader;
      case PRIORITY: return priorityReader;
      case TTL: return ttlReader;
      case FIRST_ACQUIRER: return firstAcquirerReader;
      case DELIVERY_COUNT: return deliveryCountReader;
      default: return noSuchFieldReader;
    }
}

Reader& MessageReader::PropertiesReader::getReader(size_t index)
{
    switch (index) {
      case MESSAGE_ID: return messageIdReader;
      case USER_ID: return userIdReader;
      case TO: return toReader;
      case SUBJECT: return subjectReader;
      case REPLY_TO: return replyToReader;
      case CORRELATION_ID: return correlationIdReader;
      case CONTENT_TYPE: return contentTypeReader;
      case CONTENT_ENCODING: return contentEncodingReader;
      case ABSOLUTE_EXPIRY_TIME: return absoluteExpiryTimeReader;
      case CREATION_TIME: return creationTimeReader;
      case GROUP_ID: return groupIdReader;
      case GROUP_SEQUENCE: return groupSequenceReader;
      case REPLY_TO_GROUP_ID: return replyToGroupIdReader;
      default: return noSuchFieldReader;
    }
}
*/

MessageReader::HeaderReader::HeaderReader(MessageReader& p) : parent(p), index(0) {}
void MessageReader::HeaderReader::onBoolean(bool v, const Descriptor*)  // durable, first-acquirer
{
    if (index == DURABLE) {
        parent.onDurable(v);
    } else if (index == FIRST_ACQUIRER) {
        parent.onFirstAcquirer(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got boolean at index " << index << " of headers");
    }
    ++index;
}
void MessageReader::HeaderReader::onUByte(uint8_t v, const Descriptor*) // priority
{
    if (index == PRIORITY) {
        parent.onPriority(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got ubyte at index " << index << " of headers");
    }
    ++index;
}
void MessageReader::HeaderReader::onUInt(uint32_t v, const Descriptor*) // ttl, delivery-count
{
    if (index == TTL) {
        parent.onTtl(v);
    } else if (index == DELIVERY_COUNT) {
        parent.onDeliveryCount(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got uint at index " << index << " of headers");
    }
    ++index;
}
void MessageReader::HeaderReader::onNull(const Descriptor*)
{
    ++index;
}

MessageReader::PropertiesReader::PropertiesReader(MessageReader& p) : parent(p), index(0) {}
void MessageReader::PropertiesReader::onUuid(const CharSequence& v, const Descriptor*) // message-id, correlation-id
{
    if (index == MESSAGE_ID) {
        parent.onMessageId(v, qpid::types::VAR_UUID);
    } else if (index == CORRELATION_ID) {
        parent.onCorrelationId(v, qpid::types::VAR_UUID);
    } else {
        QPID_LOG(warning, "Unexpected message format, got uuid at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onULong(uint64_t v, const Descriptor*) // message-id, correlation-id
{
    if (index == MESSAGE_ID) {
        parent.onMessageId(v);
    } else if (index == CORRELATION_ID) {
        parent.onCorrelationId(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got long at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onBinary(const CharSequence& v, const Descriptor*) // message-id, correlation-id, user-id
{
    if (index == MESSAGE_ID) {
        parent.onMessageId(v, qpid::types::VAR_STRING);
    } else if (index == CORRELATION_ID) {
        parent.onCorrelationId(v, qpid::types::VAR_STRING);
    } else if (index == USER_ID) {
        parent.onUserId(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got binary at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onString(const CharSequence& v, const Descriptor*) // message-id, correlation-id, group-id, reply-to-group-id, subject, to, reply-to
{
    if (index == MESSAGE_ID) {
        parent.onMessageId(v, qpid::types::VAR_STRING);
    } else if (index == CORRELATION_ID) {
        parent.onCorrelationId(v, qpid::types::VAR_STRING);
    } else if (index == GROUP_ID) {
        parent.onGroupId(v);
    } else if (index == REPLY_TO_GROUP_ID) {
        parent.onReplyToGroupId(v);
    } else if (index == SUBJECT) {
        parent.onSubject(v);
    } else if (index == TO) {
        parent.onTo(v);
    } else if (index == REPLY_TO) {
        parent.onReplyTo(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got string at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onSymbol(const CharSequence& v, const Descriptor*) // content-type, content-encoding
{
    if (index == CONTENT_TYPE) {
        parent.onContentType(v);
    } else if (index == CONTENT_ENCODING) {
        parent.onContentEncoding(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got symbol at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onTimestamp(int64_t v, const Descriptor*) // absolute-expiry-time, creation-time
{
    if (index == ABSOLUTE_EXPIRY_TIME) {
        parent.onAbsoluteExpiryTime(v);
    } else if (index == CREATION_TIME) {
        parent.onCreationTime(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got timestamp at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onUInt(uint32_t v, const Descriptor*) // group-sequence
{
    if (index == GROUP_SEQUENCE) {
        parent.onGroupSequence(v);
    } else {
        QPID_LOG(warning, "Unexpected message format, got uint at index " << index << " of properties");
    }
    ++index;
}
void MessageReader::PropertiesReader::onNull(const Descriptor*)
{
    ++index;
}

//header, properties, amqp-sequence, amqp-value
bool MessageReader::onStartList(uint32_t count, const CharSequence& raw, const Descriptor* descriptor)
{
    if (delegate) {
        return delegate->onStartList(count, raw, descriptor);
    } else {
        if (!descriptor) {
            QPID_LOG(warning, "Expected described type but got no descriptor for list.");
            return false;
        } else if (descriptor->match(HEADER_SYMBOL, HEADER_CODE)) {
            delegate = &headerReader;
            return true;
        } else if (descriptor->match(PROPERTIES_SYMBOL, PROPERTIES_CODE)) {
            delegate = &propertiesReader;
            return true;
        } else if (descriptor->match(AMQP_SEQUENCE_SYMBOL, AMQP_SEQUENCE_CODE) || descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(raw, *descriptor);
            return false;
        } else {
            QPID_LOG(warning, "Unexpected described list: " << *descriptor);
            return false;
        }
    }
}
void MessageReader::onEndList(uint32_t count, const Descriptor* descriptor)
{
    if (delegate) {
        if (descriptor && (descriptor->match(HEADER_SYMBOL, HEADER_CODE) || descriptor->match(PROPERTIES_SYMBOL, PROPERTIES_CODE))) {
            delegate = 0;
        } else {
            delegate->onEndList(count, descriptor);
        }
    }
}

//delivery-annotations, message-annotations, application-properties, amqp-value
bool MessageReader::onStartMap(uint32_t count, const CharSequence& raw, const Descriptor* descriptor)
{
    if (delegate) {
        return delegate->onStartMap(count, raw, descriptor);
    } else {
        if (!descriptor) {
            QPID_LOG(warning, "Expected described type but got no descriptor for map.");
            return false;
        } else if (descriptor->match(DELIVERY_ANNOTATIONS_SYMBOL, DELIVERY_ANNOTATIONS_CODE)) {
            onDeliveryAnnotations(raw);
            return false;
        } else if (descriptor->match(MESSAGE_ANNOTATIONS_SYMBOL, MESSAGE_ANNOTATIONS_CODE)) {
            onMessageAnnotations(raw);
            return false;
        } else if (descriptor->match(FOOTER_SYMBOL, FOOTER_CODE)) {
            onFooter(raw);
            return false;
        } else if (descriptor->match(APPLICATION_PROPERTIES_SYMBOL, APPLICATION_PROPERTIES_CODE)) {
            onApplicationProperties(raw);
            return false;
        } else if (descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(raw, *descriptor);
            return false;
        } else {
            QPID_LOG(warning, "Unexpected described map: " << *descriptor);
            return false;
        }
    }
}

void MessageReader::onEndMap(uint32_t count, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onEndMap(count, descriptor);
    }
}

//data, amqp-value
void MessageReader::onBinary(const CharSequence& bytes, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onBinary(bytes, descriptor);
    } else {
        if (!descriptor) {
            QPID_LOG(warning, "Expected described type but got binary value with no descriptor.");
        } else if (descriptor->match(DATA_SYMBOL, DATA_CODE) || descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(bytes, *descriptor);
        } else {
            QPID_LOG(warning, "Unexpected binary value with descriptor: " << *descriptor);
        }
    }

}

//amqp-value
void MessageReader::onNull(const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onNull(descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant v;
            onBody(v, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got null value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected null value with descriptor: " << *descriptor);
            }
        }
    }
}
void MessageReader::onString(const CharSequence& v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onString(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(v, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got string value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected string value with descriptor: " << *descriptor);
            }
        }
    }
}
void MessageReader::onSymbol(const CharSequence& v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onSymbol(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(v, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got symbol value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected symbol value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onBoolean(bool v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onBoolean(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got boolean value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected boolean value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onUByte(uint8_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onUByte(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got ubyte value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected ubyte value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onUShort(uint16_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onUShort(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got ushort value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected ushort value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onUInt(uint32_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onUInt(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got uint value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected uint value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onULong(uint64_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onULong(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got ulong value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected ulong value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onByte(int8_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onByte(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got byte value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected byte value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onShort(int16_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onShort(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got short value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected short value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onInt(int32_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onInt(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got int value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected int value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onLong(int64_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onLong(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got long value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected long value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onFloat(float v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onFloat(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got float value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected float value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onDouble(double v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onDouble(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got double value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected double value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onUuid(const CharSequence& v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onUuid(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(v, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got uuid value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected uuid value with descriptor: " << *descriptor);
            }
        }
    }
}

void MessageReader::onTimestamp(int64_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onTimestamp(v, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            qpid::types::Variant body = v;
            onBody(body, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got timestamp value with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected timestamp value with descriptor: " << *descriptor);
            }
        }
    }
}

bool MessageReader::onStartArray(uint32_t count, const CharSequence& raw, const Constructor& constructor, const Descriptor* descriptor)
{
    if (delegate) {
        return delegate->onStartArray(count, raw, constructor, descriptor);
    } else {
        if (descriptor && descriptor->match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE)) {
            onBody(raw, *descriptor);
        } else {
            if (!descriptor) {
                QPID_LOG(warning, "Expected described type but got array with no descriptor.");
            } else {
                QPID_LOG(warning, "Unexpected array with descriptor: " << *descriptor);
            }
        }
        return false;
    }
}

void MessageReader::onEndArray(uint32_t v, const Descriptor* descriptor)
{
    if (delegate) {
        delegate->onEndArray(v, descriptor);
    }
}

MessageReader::MessageReader() : headerReader(*this), propertiesReader(*this), delegate(0)
{
    bare.init();
}

void MessageReader::onDescriptor(const Descriptor& descriptor, const char* position)
{
    if (bare.data) {
        if (descriptor.match(FOOTER_SYMBOL, FOOTER_CODE)) {
            bare.size = position - bare.data;
        }
    } else {
        if (descriptor.match(PROPERTIES_SYMBOL, PROPERTIES_CODE) || descriptor.match(APPLICATION_PROPERTIES_SYMBOL, APPLICATION_PROPERTIES_CODE)
            || descriptor.match(AMQP_SEQUENCE_SYMBOL, AMQP_SEQUENCE_CODE) || descriptor.match(AMQP_VALUE_SYMBOL, AMQP_VALUE_CODE) || descriptor.match(DATA_SYMBOL, DATA_CODE)) {
            bare.data = position;
        }
    }
}

CharSequence MessageReader::getBareMessage() const { return bare; }

}} // namespace qpid::amqp
