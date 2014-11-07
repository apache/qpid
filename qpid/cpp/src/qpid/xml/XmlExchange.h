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
#ifndef _XmlExchange_
#define _XmlExchange_

#include "qpid/broker/Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/CopyOnWriteArray.h"
#include "qpid/sys/Monitor.h"
#include "qpid/broker/Queue.h"

#include <xqilla/xqilla-simple.hpp>

#include <boost/scoped_ptr.hpp>

#include <map>
#include <vector>
#include <string>

namespace qpid {
namespace broker {

class Broker;

typedef boost::shared_ptr<XQQuery> Query;

struct XmlBinding : public Exchange::Binding {

     static XQilla xqilla;

     typedef boost::shared_ptr<XmlBinding> shared_ptr;
     typedef qpid::sys::CopyOnWriteArray<XmlBinding::shared_ptr> vector;
    
     Query xquery;
     bool parse_message_content;
     const std::string fedOrigin;   // empty for local bindings
    
     XmlBinding(const std::string& key, const Queue::shared_ptr queue, const std::string& fedOrigin, Exchange* parent, 
                const ::qpid::framing::FieldTable& _arguments, const std::string& );
        
};

class XmlNullResolver;

class XmlExchange : public virtual Exchange {

    typedef std::map<std::string, XmlBinding::vector> XmlBindingsMap;
    XmlBindingsMap bindingsMap;

    qpid::sys::RWlock lock;
    boost::shared_ptr<XmlNullResolver> resolver;

    bool matches(Query& query, Deliverable& msg, bool parse_message_content);

  public:
    static const std::string typeName;

    XmlExchange(const std::string& name, management::Manageable* parent = 0, Broker* broker = 0);
    XmlExchange(const std::string& _name, bool _durable, bool autodelete,
		const qpid::framing::FieldTable& _args, management::Manageable* parent = 0, Broker* broker = 0);

    virtual std::string getType() const { return typeName; }
        
    virtual bool bind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    virtual bool unbind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    virtual void route(Deliverable& msg);

    virtual bool isBound(Queue::shared_ptr queue, const std::string* const routingKey, const qpid::framing::FieldTable* const args);

    virtual void propagateFedOp(const std::string& bindingKey, const std::string& fedTags, const std::string& fedOp, const std::string& fedOrigin, const qpid::framing::FieldTable* args=0);

    virtual bool fedUnbind(const std::string& fedOrigin, const std::string& fedTags, Queue::shared_ptr queue, const std::string& bindingKey, const  qpid::framing::FieldTable* args);
    
    virtual void fedReorigin();

    virtual bool supportsDynamicBinding() { return true; }

    virtual ~XmlExchange();

    struct MatchOrigin {
        const std::string origin;
        MatchOrigin(const std::string& origin);
        bool operator()(XmlBinding::shared_ptr b);
    };

    struct MatchQueueAndOrigin {
        const Queue::shared_ptr queue;
        const std::string origin;
        MatchQueueAndOrigin(Queue::shared_ptr queue, const std::string& origin);
        bool operator()(XmlBinding::shared_ptr b);
    };

  protected:
    bool hasBindings();
  private:
    bool unbindLH(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);
};


}
}


#endif
