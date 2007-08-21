#ifndef _broker_PersistableMessage_h
#define _broker_PersistableMessage_h

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
#include "Persistable.h"
#include "qpid/framing/amqp_types.h"

namespace qpid {
namespace broker {

/**
 * The interface messages must expose to the MessageStore in order to
 * be persistable.
 */
    class PersistableMessage : public Persistable
{


    /**
    * Needs to be set false on Message construction, then
    * set once the broker has taken responsibility for the
    * message. For transient, once enqueued, for durable, once
    * stored.
    */
    bool enqueueCompleted;
 
    /**
    * Counts the number of times the message has been processed
    * async - thus when it == 0 the broker knows it has ownership
    * -> an async store can increment this counter if it writes a
    * copy to each queue, and case use this counter to know when all
    * the write are complete
    */
    int asyncCounter;

    /**
    * Needs to be set false on Message construction, then
    * set once the dequeueis complete, it gets set
    * For transient, once dequeued, for durable, once
    * dequeue record has been stored.
    */
    bool dequeueCompleted;

public:
    typedef boost::shared_ptr<PersistableMessage> shared_ptr;

    /**
     * @returns the size of the headers when encoded
     */
    virtual uint32_t encodedHeaderSize() const = 0;

    virtual ~PersistableMessage() {};
    PersistableMessage():
    enqueueCompleted(false),
    asyncCounter(0),
    dequeueCompleted(false){};
    
    inline bool isEnqueueComplete() {return enqueueCompleted;};
    inline void enqueueComplete() {
        if (asyncCounter<=1) {
     	    asyncCounter =0;
	    enqueueCompleted = true; 
        }else{
	    asyncCounter--;
	}
     };
    inline void enqueueAsync() {asyncCounter++;};
    inline bool isDequeueComplete() {return dequeueCompleted;};
    inline void dequeueComplete() {dequeueCompleted = true;};
    
};

}}


#endif
