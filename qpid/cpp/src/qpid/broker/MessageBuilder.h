/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _MessageBuilder_
#define _MessageBuilder_

#include <qpid/QpidError.h>
#include <qpid/broker/Exchange.h>
#include <qpid/broker/Message.h>
#include <qpid/framing/AMQContentBody.h>
#include <qpid/framing/AMQHeaderBody.h>
#include <qpid/framing/BasicPublishBody.h>

namespace qpid {
    namespace broker {
        class MessageBuilder{
        public:
            class CompletionHandler{
            public:
                virtual void complete(Message::shared_ptr&) = 0;
                virtual ~CompletionHandler(){}
            };
            MessageBuilder(CompletionHandler* _handler);
            void initialise(Message::shared_ptr& msg);
            void setHeader(qpid::framing::AMQHeaderBody::shared_ptr& header);
            void addContent(qpid::framing::AMQContentBody::shared_ptr& content);
        private:
            Message::shared_ptr message;
            CompletionHandler* handler;

            void route();
        };
    }
}


#endif
