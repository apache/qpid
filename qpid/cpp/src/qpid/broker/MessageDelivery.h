#ifndef _broker_MessageDelivery_h
#define _broker_MessageDelivery_h

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
#include <boost/shared_ptr.hpp>
#include "DeliveryId.h"
#include "Consumer.h"
#include "qpid/framing/FrameHandler.h"

namespace qpid {
namespace broker {

class DeliveryToken;
class Message;
class Queue;

/**
 * Encapsulates the different options for message delivery currently supported.
 */
class MessageDelivery {
public:
    static boost::shared_ptr<DeliveryToken> getBasicGetToken(boost::shared_ptr<Queue> queue);
    static boost::shared_ptr<DeliveryToken> getBasicConsumeToken(const std::string& consumer);
    static boost::shared_ptr<DeliveryToken> getPreviewMessageDeliveryToken(const std::string& destination, 
                                                                           u_int8_t confirmMode, 
                                                                           u_int8_t acquireMode);
    static boost::shared_ptr<DeliveryToken> getMessageDeliveryToken(const std::string& destination, 
                                                                    u_int8_t confirmMode, 
                                                                    u_int8_t acquireMode);

    static void deliver(QueuedMessage& msg, framing::FrameHandler& out, 
                        DeliveryId deliveryTag, boost::shared_ptr<DeliveryToken> token, uint16_t framesize);
};

}
}


#endif  /*!_broker_MessageDelivery_h*/
