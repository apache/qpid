#ifndef QPID_MESSAGING_MESSAGE_H
#define QPID_MESSAGING_MESSAGE_H

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
#include "qpid/messaging/ImportExport.h"

#include "qpid/messaging/Duration.h"
#include "qpid/types/Exception.h"
#include "qpid/types/Variant.h"

#include <string>

namespace qpid {
namespace messaging {

class Address;
class Codec;
class MessageImpl;

/**   \ingroup messaging 
 * Representation of a message.
 */
class QPID_MESSAGING_CLASS_EXTERN Message
{
  public:
    QPID_MESSAGING_EXTERN Message(qpid::types::Variant&);
    QPID_MESSAGING_EXTERN Message(const std::string& bytes = std::string());
    QPID_MESSAGING_EXTERN Message(const char*, size_t);
    QPID_MESSAGING_EXTERN Message(const Message&);
    QPID_MESSAGING_EXTERN ~Message();

    QPID_MESSAGING_EXTERN Message& operator=(const Message&);

    QPID_MESSAGING_EXTERN void setReplyTo(const Address&);
    QPID_MESSAGING_EXTERN const Address& getReplyTo() const;

    QPID_MESSAGING_EXTERN void setSubject(const std::string&);
    QPID_MESSAGING_EXTERN const std::string& getSubject() const;

    /**
     * Set the content type (i.e. the MIME type) for the message. This
     * should be set by the sending application and indicates to
     * recipients of message how to interpret or decode the content.
     */
    QPID_MESSAGING_EXTERN void setContentType(const std::string&);
    /**
     * Returns the content type (i.e. the MIME type) for the
     * message. This can be used to determine how to decode the
     * message content.
     */
    QPID_MESSAGING_EXTERN const std::string& getContentType() const;

    /**
     * Set an application defined identifier for the message. At
     * present this must be a stringfied UUID (support for less
     * restrictive IDs is anticipated however).
     */
    QPID_MESSAGING_EXTERN void setMessageId(const std::string&);
    QPID_MESSAGING_EXTERN const std::string& getMessageId() const;

    /**
     * Sets the user id of the message. This should in general be the
     * user-id as which the sending connection authenticated itself as
     * the messaging infrastructure will verify this. See
     * Connection::getAuthenticatedUsername()
     */
    QPID_MESSAGING_EXTERN void setUserId(const std::string&);
    QPID_MESSAGING_EXTERN const std::string& getUserId() const;

    /**
     * Can be used to set application specific correlation identifiers
     * as part of a protocol for message exchange patterns. E.g. a
     * request-reponse pattern might require the correlation-id of the
     * request and response to match, or might use the message-id of
     * the request as the correlation-id on the response etc.
     */
    QPID_MESSAGING_EXTERN void setCorrelationId(const std::string&);
    QPID_MESSAGING_EXTERN const std::string& getCorrelationId() const;

    /**
     * Sets a priority level on the message. This may be used by the
     * messaging infrastructure to prioritise delivery of higher
     * priority messages.
     */
    QPID_MESSAGING_EXTERN void setPriority(uint8_t);
    QPID_MESSAGING_EXTERN uint8_t getPriority() const;

    /**
     * Set the time to live for this message in milliseconds. This can
     * be used by the messaging infrastructure to discard messages
     * that are no longer of relevance.
     */
    QPID_MESSAGING_EXTERN void setTtl(Duration ttl);
    /**
     *Get the time to live for this message in milliseconds.
     */
    QPID_MESSAGING_EXTERN Duration getTtl() const;

    /**
     * Mark the message as durable. This is a hint to the messaging
     * infrastructure that the message should be persisted or
     * otherwise stored such that failoures or shutdown do not cause
     * it to be lost.
     */
    QPID_MESSAGING_EXTERN void setDurable(bool durable);
    QPID_MESSAGING_EXTERN bool getDurable() const;

    /**
     * The redelivered flag if set implies that the message *may* have
     * been previously delivered and thus is a hint to the application
     * or messaging infrastructure that if de-duplication is required
     * this message should be examined to determine if it is a
     * duplicate.
     */
    QPID_MESSAGING_EXTERN bool getRedelivered() const;
    /**
     * Can be used to provide a hint to the application or messaging
     * infrastructure that if de-duplication is required this message
     * should be examined to determine if it is a duplicate.
     */
    QPID_MESSAGING_EXTERN void setRedelivered(bool);

    /**
     * In addition to a payload (i.e. the content), messages can
     * include annotations describing aspectf of the message. In
     * addition to the standard annotations such as TTL and content
     * type, application- or context- specific properties can also be
     * defined. Each message has a map of name values for such custom
     * properties. The value is specified as a Variant.
     */
    QPID_MESSAGING_EXTERN const qpid::types::Variant::Map& getProperties() const;
    QPID_MESSAGING_EXTERN qpid::types::Variant::Map& getProperties();
    QPID_MESSAGING_EXTERN void setProperties(const qpid::types::Variant::Map&);

    /**
     * Set the content to the data held in the string parameter. Note:
     * this is treated as raw bytes and need not be text. Consider
     * setting the content-type to indicate how the data should be
     * interpreted by recipients.
     */
    QPID_MESSAGING_EXTERN void setContent(const std::string&);
    /**
     * Copy count bytes from the region pointed to by chars as the
     * message content.
     */
    QPID_MESSAGING_EXTERN void setContent(const char* chars, size_t count);

    /** Get the content as a std::string */
    QPID_MESSAGING_EXTERN std::string getContent() const;
    /** Get the content as raw bytes (an alias for getContent() */
    QPID_MESSAGING_EXTERN std::string getContentBytes() const;
    /** Set the content as raw bytes (an alias for setContent() */
    QPID_MESSAGING_EXTERN void setContentBytes(const std::string&);
    /**
     * Get the content as a Variant, which can represent an object of
     * different types. This can be used for content representing a
     * map or a list for example.
     */
    QPID_MESSAGING_EXTERN qpid::types::Variant& getContentObject();
    /**
     * Get the content as a Variant, which can represent an object of
     * different types. This can be used for content representing a
     * map or a list for example.
     */
    QPID_MESSAGING_EXTERN const qpid::types::Variant& getContentObject() const;
    /**
     * Set the content using a Variant, which can represent an object
     * of different types.
     */
    QPID_MESSAGING_EXTERN void setContentObject(const qpid::types::Variant&);
    /**
     * Get a const pointer to the start of the content data. The
     * memory pointed to is owned by the message. The getContentSize()
     * method indicates how much data there is (i.e. the extent of the
     * memory region pointed to by the return value of this method).
     */
    QPID_MESSAGING_EXTERN const char* getContentPtr() const;
    /** Get the size of content in bytes. */
    QPID_MESSAGING_EXTERN size_t getContentSize() const;

    QPID_MESSAGING_EXTERN void setProperty(const std::string&, const qpid::types::Variant&);
  private:
    MessageImpl* impl;
    friend struct MessageImplAccess;
};

struct QPID_MESSAGING_CLASS_EXTERN EncodingException : qpid::types::Exception
{
    QPID_MESSAGING_EXTERN EncodingException(const std::string& msg);
};

/**
 * Decodes message content into a Variant::Map.
 * 
 * @param message the message whose content should be decoded
 * @param map the map into which the message contents will be decoded
 * @param encoding if specified, the encoding to use - this overrides
 * any encoding specified by the content-type of the message
 * @exception EncodingException
 */
QPID_MESSAGING_EXTERN void decode(const Message& message,
                                  qpid::types::Variant::Map& map,
                                  const std::string& encoding = std::string());
/**
 * Decodes message content into a Variant::List.
 * 
 * @param message the message whose content should be decoded
 * @param list the list into which the message contents will be decoded
 * @param encoding if specified, the encoding to use - this overrides
 * any encoding specified by the content-type of the message
 * @exception EncodingException
 */
QPID_MESSAGING_EXTERN void decode(const Message& message,
                                  qpid::types::Variant::List& list,
                                  const std::string& encoding = std::string());
/**
 * Encodes a Variant::Map into a message.
 * 
 * @param map the map to be encoded
 * @param message the message whose content should be set to the encoded map
 * @param encoding if specified, the encoding to use - this overrides
 * any encoding specified by the content-type of the message
 * @exception EncodingException
 */
QPID_MESSAGING_EXTERN void encode(const qpid::types::Variant::Map& map,
                                  Message& message,
                                  const std::string& encoding = std::string());
/**
 * Encodes a Variant::List into a message.
 * 
 * @param list the list to be encoded
 * @param message the message whose content should be set to the encoded list
 * @param encoding if specified, the encoding to use - this overrides
 * any encoding specified by the content-type of the message
 * @exception EncodingException
 */
QPID_MESSAGING_EXTERN void encode(const qpid::types::Variant::List& list,
                                  Message& message,
                                  const std::string& encoding = std::string());

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_MESSAGE_H*/
