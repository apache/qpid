#ifndef QPID_BROKER_TXDEQUEUE_H
#define QPID_BROKER_TXDEQUEUE_H

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
#include "qpid/broker/QueueCursor.h"
#include "qpid/broker/TxOp.h"
#include "qpid/framing/SequenceNumber.h"

namespace qpid {
namespace broker {
class Queue;

/**
 * Transaction dequeue operation
 */
class TxDequeue: public TxOp
{
  public:
    TxDequeue(QueueCursor message, boost::shared_ptr<Queue> queue,
              qpid::framing::SequenceNumber messageId, qpid::framing::SequenceNumber replicationId);
    bool prepare(TransactionContext* ctxt) throw();
    void commit() throw();
    void rollback() throw();
    void callObserver(const boost::shared_ptr<TransactionObserver>&);
  private:
    QueueCursor message;
    boost::shared_ptr<Queue> queue;
    qpid::framing::SequenceNumber messageId;
    qpid::framing::SequenceNumber replicationId;
    bool releaseOnAbort;
    bool redeliveredOnAbort;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_TXDEQUEUE_H*/
