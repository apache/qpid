#ifndef QPID_CLIENT_AMQP0_10_INCOMINGMESSAGES_H
#define QPID_CLIENT_AMQP0_10_INCOMINGMESSAGES_H

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
#include <boost/shared_ptr.hpp>
#include "qpid/client/AsyncSession.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/sys/BlockingQueue.h"
#include "qpid/sys/Time.h"

namespace qpid {

namespace framing{
class FrameSet;
}

namespace messaging {
class Message;
}

namespace client {
namespace amqp0_10 {

/**
 * 
 */
class IncomingMessages
{
  public:
    typedef boost::shared_ptr<qpid::framing::FrameSet> FrameSetPtr;
    class MessageTransfer
    {
      public:
        const std::string& getDestination();
        void retrieve(qpid::messaging::Message* message);
      private:
        FrameSetPtr content;
        IncomingMessages& parent;

        MessageTransfer(FrameSetPtr, IncomingMessages&);
      friend class IncomingMessages;
    };

    struct Handler
    {
        virtual ~Handler() {}
        virtual bool accept(MessageTransfer& transfer) = 0;
    };

    IncomingMessages(qpid::client::AsyncSession session);
    bool get(Handler& handler, qpid::sys::Duration timeout);
    //bool get(qpid::messaging::Message& message, qpid::sys::Duration timeout);
    //bool get(const std::string& destination, qpid::messaging::Message& message, qpid::sys::Duration timeout);
    void accept();
    void releaseAll();
    void releasePending(const std::string& destination);
  private:
    typedef std::deque<FrameSetPtr> FrameSetQueue;

    qpid::client::AsyncSession session;
    qpid::framing::SequenceSet unaccepted;
    boost::shared_ptr< sys::BlockingQueue<FrameSetPtr> > incoming;
    FrameSetQueue received;

    bool process(Handler*, qpid::sys::Duration);
    void retrieve(FrameSetPtr, qpid::messaging::Message*);

};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_INCOMINGMESSAGES_H*/
