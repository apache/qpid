#ifndef QPID_BROKER_MESSAGES_H
#define QPID_BROKER_MESSAGES_H

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
#include <boost/function.hpp>

namespace qpid {
namespace framing {
class SequenceNumber;
}
namespace broker {
struct QueuedMessage;

/**
 * This interface abstracts out the access to the messages held for
 * delivery by a Queue instance. Note the the assumption at present is
 * that all locking is done in the Queue itself.
 */
class Messages
{
  public:
    typedef boost::function1<void, QueuedMessage&> Functor;
    typedef boost::function1<bool, QueuedMessage&> Predicate;

    virtual ~Messages() {}
    /**
     * @return the number of messages available for delivery.
     */
    virtual size_t size() = 0;
    /**
     * @return true if there are no messages for delivery, false otherwise
     */
    virtual bool empty() = 0;

    /**
     * Re-inserts a message back into its original position - used
     * when requeing released messages.
     */
    virtual void reinsert(const QueuedMessage&) = 0;
    /**
     * Remove the message at the specified position, returning true if
     * found, false otherwise. The removed message is passed back via
     * the second parameter.
     */
    virtual bool remove(const framing::SequenceNumber&, QueuedMessage&) = 0;
    /**
     * Find the message at the specified position, returning true if
     * found, false otherwise. The matched message is passed back via
     * the second parameter.
     */
    virtual bool find(const framing::SequenceNumber&, QueuedMessage&) = 0;
    /**
     * Return the next message to be given to a browsing subscrption
     * that has reached the specified poisition. The next messages is
     * passed back via the second parameter.
     *
     * @return true if there is another message, false otherwise.
     */
    virtual bool next(const framing::SequenceNumber&, QueuedMessage&) = 0;

    /**
     * Note: Caller is responsible for ensuring that there is a front
     * (e.g. empty() returns false)
     *
     * @return the next message to be delivered
     */
    virtual QueuedMessage& front() = 0;
    /**
     * Removes the front message
     */
    virtual void pop() = 0;
    /**
     * @return true if there is a mesage to be delivered - in which
     * case that message will be returned via the parameter and
     * removed - otherwise false.
     */
    virtual bool pop(QueuedMessage&) = 0;
    /**
     * Pushes a message to the back of the 'queue'. For some types of
     * queue this may cause another message to be removed; if that is
     * the case the method will return true and the removed message
     * will be passed out via the second parameter.
     */
    virtual bool push(const QueuedMessage& added, QueuedMessage& removed) = 0;

    /**
     * Apply the functor to each message held
     */
    virtual void foreach(Functor) = 0;
    /**
     * Remove every message held that for which the specified
     * predicate returns true
     */
    virtual void removeIf(Predicate) = 0;
  private:
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_MESSAGES_H*/
