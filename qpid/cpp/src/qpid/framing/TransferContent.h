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
#ifndef _TransferContent_
#define _TransferContent_

#include "FrameSet.h"
#include "MethodContent.h"
#include "qpid/Exception.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/DeliveryProperties.h"

namespace qpid {
namespace framing {

/** Message content */
class TransferContent : public MethodContent
{
    AMQHeaderBody header;
    std::string data;
public:
    TransferContent(const std::string& data = std::string(), const std::string& key=std::string());

    ///@internal
    AMQHeaderBody getHeader() const;

    void setData(const std::string&);
    const std::string& getData() const;
    std::string& getData();

    void appendData(const std::string&);

    bool hasMessageProperties() const;
    MessageProperties& getMessageProperties();
    const MessageProperties& getMessageProperties() const;

    bool hasDeliveryProperties() const;
    DeliveryProperties& getDeliveryProperties();
    const DeliveryProperties& getDeliveryProperties() const;

    ///@internal
    void populate(const FrameSet& frameset);
};

}}
#endif  
