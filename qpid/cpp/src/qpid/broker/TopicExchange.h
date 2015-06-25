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
#ifndef _TopicExchange_
#define _TopicExchange_

#include <map>
#include <vector>
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/TopicKeyNode.h"


namespace qpid {
namespace broker {

class TopicExchange : public virtual Exchange {

    class Normalizer;

    struct BindingKey {        // binding for this node
        Binding::vector bindingVector;
        FedBinding fedBinding;
    };

    typedef TopicKeyNode<BindingKey> BindingNode;

    BindingKey *getQueueBinding(Queue::shared_ptr queue, const std::string& pattern);
    bool deleteBinding(Queue::shared_ptr queue,
                       const std::string& routingKey,
                       BindingKey *bk);

    class ReOriginIter;
    class BindingsFinderIter;
    class QueueFinderIter;

    BindingNode bindingTree;
    unsigned long nBindings;
    qpid::sys::RWlock lock;     // protects bindingTree and nBindings
    qpid::sys::RWlock cacheLock;     // protects cache
    std::map<std::string, BindingList> bindingCache; // cache of matched routes.

    class ClearCache {
    private:
        qpid::sys::RWlock* cacheLock;
        std::map<std::string, BindingList>* bindingCache;
        bool cleared; 
    public:
        ClearCache(qpid::sys::RWlock* l, std::map<std::string, BindingList>* bc) :
            cacheLock(l), bindingCache(bc),cleared(false) {};
        void clearCache() {
            qpid::sys::RWlock::ScopedWlock l(*cacheLock);
            if (!cleared) {
                bindingCache->clear();
                cleared =true;
            }
        };
        ~ClearCache(){ 
            clearCache();
        };
    };

public:
    QPID_BROKER_EXTERN static const std::string typeName;

    static QPID_BROKER_EXTERN std::string normalize(const std::string& pattern);

    QPID_BROKER_EXTERN TopicExchange(const std::string& name,
                                     management::Manageable* parent = 0, Broker* broker = 0);
    QPID_BROKER_EXTERN TopicExchange(const std::string& _name,
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

    QPID_BROKER_EXTERN virtual ~TopicExchange();
    virtual bool supportsDynamicBinding() { return true; }

    class TopicExchangeTester;
    friend class TopicExchangeTester;
  protected:
    bool hasBindings();
};


}
}

#endif
