#ifndef QPID_BROKER_QUEUEOBSERVER_H
#define QPID_BROKER_QUEUEOBSERVER_H

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
namespace qpid {
namespace broker {

class QueuedMessage;
/**
 * Interface for notifying classes who want to act as 'observers' of a
 * queue of particular events.
 */
class QueueObserver
{
  public:
    virtual ~QueueObserver() {}
    virtual void enqueued(const QueuedMessage&) = 0;
    virtual void dequeued(const QueuedMessage&) = 0;
  private:
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEOBSERVER_H*/
