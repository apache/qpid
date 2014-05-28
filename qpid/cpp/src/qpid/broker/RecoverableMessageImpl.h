#ifndef QPID_BROKER_RECOVERABLEMESSAGEIMPL_H
#define QPID_BROKER_RECOVERABLEMESSAGEIMPL_H

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
#include "RecoverableMessage.h"
#include "Message.h"

namespace qpid {
namespace broker {
class DtxBuffer;
class Queue;

class RecoverableMessageImpl : public RecoverableMessage
{
    Message msg;
public:
    QPID_BROKER_EXTERN RecoverableMessageImpl(const Message& _msg);
    ~RecoverableMessageImpl() {};
    void setPersistenceId(uint64_t id);
    void setRedelivered();
    void computeExpiration();
    bool loadContent(uint64_t available);
    void decodeContent(framing::Buffer& buffer);
    void recover(boost::shared_ptr<Queue> queue);
    void enqueue(boost::intrusive_ptr<DtxBuffer> buffer, boost::shared_ptr<Queue> queue);
    void dequeue(boost::intrusive_ptr<DtxBuffer> buffer, boost::shared_ptr<Queue> queue);
    Message getMessage();
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_RECOVERABLEMESSAGEIMPL_H*/
