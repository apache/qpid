#ifndef _client_ClientMessage_h
#define _client_ClientMessage_h

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
#include <string>
#include <framing/amqp_framing.h>

namespace qpid {

namespace client {
class IncomingMessage;

/**
 * A representation of messages for sent or recived through the
 * client api.
 *
 * \ingroup clientapi
 */
class Message {
    framing::AMQMethodBody::shared_ptr method;
    framing::AMQHeaderBody::shared_ptr header;
    std::string data;
    bool redelivered;

    // FIXME aconway 2007-02-20: const incorrect, needs const return type.
    framing::BasicHeaderProperties* getHeaderProperties() const;
    Message(qpid::framing::AMQHeaderBody::shared_ptr& header);
        
  public:
    Message(const std::string& data=std::string());
    ~Message();
	
    /**
     * Allows the application to access the content of messages
     * received.
     * 
     * @return a string representing the data of the message
     */
    std::string getData() const { return data; }

    /**
     * Allows the application to set the content of messages to be
     * sent.
     * 
     * @param data a string representing the data of the message
     */
    void setData(const std::string& _data);

    /**
     * @return true if this message was delivered previously (to
     * any consumer) but was not acknowledged.
     */
    bool isRedelivered(){ return redelivered; }
    void setRedelivered(bool _redelivered){  redelivered = _redelivered; }

    uint64_t getDeliveryTag() const;

    const std::string& getContentType() const;
    const std::string& getContentEncoding() const;
    qpid::framing::FieldTable& getHeaders() const;
    uint8_t getDeliveryMode() const;
    uint8_t getPriority() const;
    const std::string& getCorrelationId() const;
    const std::string& getReplyTo() const;
    const std::string& getExpiration() const;
    const std::string& getMessageId() const;
    uint64_t getTimestamp() const;
    const std::string& getType() const;
    const std::string& getUserId() const;
    const std::string& getAppId() const;
    const std::string& getClusterId() const;

    void setContentType(const std::string& type);
    void setContentEncoding(const std::string& encoding);
    void setHeaders(const qpid::framing::FieldTable& headers);
    /**
     * Sets the delivery mode. 1 = non-durable, 2 = durable.
     */
    void setDeliveryMode(uint8_t mode);
    void setPriority(uint8_t priority);
    void setCorrelationId(const std::string& correlationId);
    void setReplyTo(const std::string& replyTo);
    void setExpiration(const std::string&  expiration);
    void setMessageId(const std::string& messageId);
    void setTimestamp(uint64_t timestamp);
    void setType(const std::string& type);
    void setUserId(const std::string& userId);
    void setAppId(const std::string& appId);
    void setClusterId(const std::string& clusterId);

    /** Get the method used to deliver this message */
    boost::shared_ptr<framing::AMQMethodBody> getMethod() const
    { return method; }
        
    void setMethod(framing::AMQMethodBody::shared_ptr m) { method=m; }
    boost::shared_ptr<framing::AMQHeaderBody> getHeader() const
    { return header; }

    // TODO aconway 2007-02-15: remove friendships.
  friend class IncomingMessage;
  friend class Channel;
};

}}

#endif  /*!_client_ClientMessage_h*/
