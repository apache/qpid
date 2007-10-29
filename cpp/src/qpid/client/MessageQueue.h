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

#ifndef _MessageQueue_
#define _MessageQueue_
#include <iostream>
#include "qpid/sys/BlockingQueue.h"
#include "MessageListener.h"

namespace qpid {
namespace client {

/**
 * A MessageListener implementation that simply queues up
 * messages.
 *
 * \ingroup clientapi
 */
class MessageQueue : public MessageListener,
                     public sys::BlockingQueue<Message>
{
    std::queue<Message> messages;
  public:
    void received(Message& msg)
    {
        std::cout << "Adding message to queue: " << msg.getData() << std::endl;
        push(msg);
    }
};

}
}


#endif
