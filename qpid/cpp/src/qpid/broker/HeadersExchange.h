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
#ifndef _HeadersExchange_
#define _HeadersExchange_

#include <vector>
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/CopyOnWriteArray.h"
#include "qpid/sys/Mutex.h"
#include "qpid/broker/Queue.h"

namespace qpid {
namespace broker {


class HeadersExchange : public virtual Exchange {

    struct BoundKey
    {
        Binding::shared_ptr binding;
        qpid::framing::FieldTable args;
        FedBinding fedBinding;
        BoundKey(Binding::shared_ptr binding_, const qpid::framing::FieldTable& args_) : binding(binding_), args(args_) {}
    };

    struct MatchArgs
    {
        const Queue::shared_ptr queue;
        const qpid::framing::FieldTable* args;
        MatchArgs(Queue::shared_ptr q, const qpid::framing::FieldTable* a);
        bool operator()(const BoundKey & bk);
    };

    struct MatchKey
    {
        const Queue::shared_ptr queue;
        const std::string& key;
        MatchKey(Queue::shared_ptr q, const std::string& k);
        bool operator()(const BoundKey & bk);
    };

    struct FedUnbindModifier
    {
        std::string queueName;
        std::string fedOrigin;
        bool shouldUnbind;
        bool shouldPropagate;
        FedUnbindModifier();
        FedUnbindModifier(const std::string& queueName, const std::string& origin);
        bool operator()(BoundKey & bk);
    };

    typedef qpid::sys::CopyOnWriteArray<BoundKey> Bindings;

    Bindings bindings;
    qpid::sys::Mutex lock;
  protected:
    void getNonFedArgs(const framing::FieldTable* args,
                       framing::FieldTable& nonFedArgs);
    bool hasBindings();

  public:
    QPID_BROKER_EXTERN static const std::string typeName;

    QPID_BROKER_EXTERN HeadersExchange(const std::string& name,
                                       management::Manageable* parent = 0, Broker* broker = 0);
    QPID_BROKER_EXTERN HeadersExchange(const std::string& _name,
                                       bool _durable, bool autodelete,
                                       const qpid::framing::FieldTable& _args,
                                       management::Manageable* parent = 0, Broker* broker = 0);

    virtual std::string getType() const { return typeName; }

    QPID_BROKER_EXTERN virtual bool bind(Queue::shared_ptr queue,
                                         const std::string& routingKey,
                                         const qpid::framing::FieldTable* args);

    virtual bool unbind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    QPID_BROKER_EXTERN virtual void route(Deliverable& msg);

    QPID_BROKER_EXTERN virtual bool isBound(Queue::shared_ptr queue,
                                            const std::string* const routingKey,
                                            const qpid::framing::FieldTable* const args);

    QPID_BROKER_EXTERN virtual ~HeadersExchange();

    virtual bool supportsDynamicBinding() { return true; }

    static QPID_BROKER_EXTERN bool match(const qpid::framing::FieldTable& bindArgs, const qpid::broker::Message& msg);
    static bool equal(const qpid::framing::FieldTable& bindArgs, const qpid::framing::FieldTable& msgArgs);
};



}
}

#endif
