#ifndef QPID_BROKER_INDEXEDDEQUE_H
#define QPID_BROKER_INDEXEDDEQUE_H

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
#include "qpid/framing/SequenceNumber.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Messages.h"
#include "qpid/broker/QueueCursor.h"
#include "qpid/log/Statement.h"
#include <deque>

namespace qpid {
namespace broker {

/**
 * Template for a deque whose contents can be refered to by
 * QueueCursor
 */
template <typename T> class IndexedDeque
{
  public:
    typedef boost::function1<T, qpid::framing::SequenceNumber> Padding;
    IndexedDeque(Padding p) : head(0), version(0), padding(p) {}

    bool index(const QueueCursor& cursor, size_t& result)
    {
        return cursor.valid && index(qpid::framing::SequenceNumber(cursor.position + 1), result);
    }

    /**
     * Finds the index for the message with the specified sequence number.
     *
     * @returns true if a message was found with the specified sequence,
     * in which case the second parameter will be set to the index of that
     * message; false if no message with that sequence exists, in which
     * case the second parameter will be 0 if the sequence is less than
     * that of the first message and non-zero if it is greater than that
     * of the last message
     */
    bool index(const qpid::framing::SequenceNumber& position, size_t& i)
    {
        //assuming a monotonic sequence, with no messages removed except
        //from the ends of the deque, we can use the position to determine
        //an index into the deque
        if (messages.size()) {
            qpid::framing::SequenceNumber front(messages.front().getSequence());
            if (position < front) {
                i = 0;
            } else {
                i = position - front;
                return i < messages.size();
            }
        }
        return false;
    }

    bool deleted(const QueueCursor& cursor)
    {
        size_t i;
        if (cursor.valid && index(cursor.position, i)) {
            messages[i].setState(DELETED);
            clean();
            return true;
        } else {
            return false;
        }
    }

    T& publish(const T& added)
    {
        // QPID-4046: let producer help clean the backlog of deleted messages
        clean();
        //for ha replication, the queue can sometimes be reset by
        //removing some of the more recent messages, in this case we
        //need to ensure the DELETED records at the tail do not interfere with indexing
        while (messages.size() && added.getSequence() <= messages.back().getSequence() && messages.back().getState() == DELETED)
            messages.pop_back();
        if (messages.size() && added.getSequence() <= messages.back().getSequence()) throw qpid::Exception(QPID_MSG("Index out of sequence!"));

        //add padding to prevent gaps in sequence, which break the index
        //calculation (needed for queue replication)
        while (messages.size() && (added.getSequence() - messages.back().getSequence()) > 1)
            messages.push_back(padding(messages.back().getSequence() + 1));

        messages.push_back(added);
        T& m = messages.back();
        m.setState(AVAILABLE);
        if (head >= messages.size()) head = messages.size() - 1;
        QPID_LOG(debug, "Message " << &m << " published, state is " << m.getState() << " (head is now " << head << ")");
        return m;
    }

    T* release(const QueueCursor& cursor)
    {
        size_t i;
        if (cursor.valid && index(cursor.position, i) && messages[i].getState() == ACQUIRED) {
            messages[i].setState(AVAILABLE);
            ++version;
            QPID_LOG(debug, "Released message at position " << cursor.position << ", index " << i);
            return &messages[i];
        } else {
            if (!cursor.valid) { QPID_LOG(debug, "Could not release message; cursor was invalid");}
            else { QPID_LOG(debug, "Could not release message at position " << cursor.position); }
            return 0;
        }
    }

    bool reset(const QueueCursor& cursor)
    {
        return !cursor.valid || (cursor.type == CONSUMER && cursor.version != version);
    }

    T* next(QueueCursor& cursor)
    {
        size_t i = 0;
        if (reset(cursor)) i = head; //start from head
        else index(cursor, i); //get first message that is greater than position

        if (cursor.valid) {
            QPID_LOG(debug, "next() called for cursor at " << cursor.position << ", index set to " << i << " (of " << messages.size() << ")");
        } else {
            QPID_LOG(debug, "next() called for invalid cursor, index started at " << i << " (of " << messages.size() << ")");
        }
        while (i < messages.size()) {
            T& m = messages[i++];
            if (m.getState() == DELETED) continue;
            cursor.setPosition(m.getSequence(), version);
            QPID_LOG(debug, "in next(), cursor set to " << cursor.position);

            if (cursor.check(m)) {
                QPID_LOG(debug, "in next(), returning message at " << cursor.position);
                return &m;
            }
        }
        QPID_LOG(debug, "no message to return from next");
        return 0;
    }

    size_t size()
    {
        size_t count(0);
        for (size_t i = head; i < messages.size(); ++i) {
            if (messages[i].getState() == AVAILABLE) ++count;
        }
        return count;
    }

    T* find(const qpid::framing::SequenceNumber& position, QueueCursor* cursor)
    {
        size_t i = 0;
        if (index(position, i)){
            T& m = messages[i];
            if (cursor) cursor->setPosition(position, version);
            if (m.getState() == AVAILABLE || m.getState() == ACQUIRED) {
                return &m;
            }
        } else if (cursor) {
            if (i >= messages.size()) cursor->setPosition(position, version);//haven't yet got a message with that seq no
            else if (i == 0) cursor->valid = false;//reset
        }
        return 0;
    }

    T* find(const QueueCursor& cursor)
    {
        if (cursor.valid) return find(cursor.position, 0);
        else return 0;
    }

    void clean()
    {
        // QPID-4046: If a queue has multiple consumers, then it is possible for a large
        // collection of deleted messages to build up.  Limit the number of messages cleaned
        // up on each call to clean().
        size_t count = 0;
        while (messages.size() && messages.front().getState() == DELETED && count < 10) {
            messages.pop_front();
            count += 1;
        }
        head = (head > count) ? head - count : 0;
        QPID_LOG(debug, "clean(): " << messages.size() << " messages remain; head is now " << head);
    }

    void foreach(Messages::Functor f)
    {
        for (typename Deque::iterator i = messages.begin(); i != messages.end(); ++i) {
            if (i->getState() == AVAILABLE) {
                f(*i);
            }
        }
        clean();
    }

    void resetCursors()
    {
        ++version;
    }

    typedef std::deque<T> Deque;
    Deque messages;
    size_t head;
    int32_t version;
    Padding padding;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_INDEXEDDEQUE_H*/
