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

#include "config.h"

#include "qpid/xml/XmlExchange.h"

#include "qpid/amqp/CharSequence.h"
#include "qpid/broker/DeliverableMessage.h"

#include "qpid/log/Statement.h"
#include "qpid/broker/FedOps.h"
#include "qpid/amqp/MapHandler.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"

#include "qpid/Plugin.h"

#include <xercesc/framework/MemBufInputSource.hpp>
#include <xercesc/util/XMLEntityResolver.hpp>

#ifdef XQ_EFFECTIVE_BOOLEAN_VALUE_HPP
#include <xqilla/ast/XQEffectiveBooleanValue.hpp>
#endif

#include <xqilla/ast/XQGlobalVariable.hpp>

#include <xqilla/context/ItemFactory.hpp>
#include <xqilla/xqilla-simple.hpp>

#include <boost/bind.hpp>
#include <functional>
#include <algorithm>
#include <iostream>
#include <sstream>

using namespace qpid::framing;
using namespace qpid::sys;
using qpid::management::Manageable;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {            

namespace {
const char* DUMMY("dummy");
}
class XmlNullResolver : public XERCES_CPP_NAMESPACE::XMLEntityResolver
{
 public:
    XERCES_CPP_NAMESPACE::InputSource* resolveEntity(XERCES_CPP_NAMESPACE::XMLResourceIdentifier* xmlri)
    {
        if (xmlri->getResourceIdentifierType() == XERCES_CPP_NAMESPACE::XMLResourceIdentifier::ExternalEntity) {
            return new XERCES_CPP_NAMESPACE::MemBufInputSource(0, 0, DUMMY);
        } else {
            return 0;
        }
    }
};

    
XQilla XmlBinding::xqilla;

XmlBinding::XmlBinding(const std::string& key, const Queue::shared_ptr queue, const std::string& _fedOrigin, Exchange* parent, 
                                    const ::qpid::framing::FieldTable& _arguments, const std::string& queryText )
    :      Binding(key, queue, parent, _arguments),
           xquery(),
           parse_message_content(true),
           fedOrigin(_fedOrigin)
{ 
    startManagement();

    QPID_LOG(trace, "Creating binding with query: " << queryText );

    try {  
        Query q(xqilla.parse(X(queryText.c_str())));
        xquery = q;
        
        QPID_LOG(trace, "Bound successfully with query: " << queryText );

        parse_message_content = false;

        if (xquery->getQueryBody()->getStaticAnalysis().areContextFlagsUsed()) {
            parse_message_content = true;
        }
        else {
            GlobalVariables &vars = const_cast<GlobalVariables&>(xquery->getVariables());
            for (GlobalVariables::iterator it = vars.begin(); it != vars.end(); ++it) {
                if ((*it)->getStaticAnalysis().areContextFlagsUsed()) {
                    parse_message_content = true;
                    break;
                } 
            }
        }
    }
    catch (XQException& e) {
        throw InternalErrorException(QPID_MSG("Could not parse xquery:"+ queryText));
    }
    catch (...) {
        throw InternalErrorException(QPID_MSG("Unexpected error - Could not parse xquery:"+ queryText));
    }    
}

     
XmlExchange::XmlExchange(const std::string& _name, Manageable* _parent, Broker* b) : Exchange(_name, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

XmlExchange::XmlExchange(const std::string& _name, bool _durable, bool autodelete,
                         const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, autodelete, _args, _parent, b), resolver(new XmlNullResolver)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}
    
bool XmlExchange::bind(Queue::shared_ptr queue, const std::string& bindingKey, const FieldTable* args)
{ 

    // Federation uses bind for unbind and reorigin comands as well as for binds.
    //
    // Both federated and local binds are done in this method.  Other
    // federated requests are done by calling the relevent methods.

    std::string fedOp;
    std::string fedTags;
    std::string fedOrigin;
    
    if (args) 
        fedOp = args->getAsString(qpidFedOp);
    if (! fedOp.empty())  {
        fedTags =  args->getAsString(qpidFedTags);
        fedOrigin = args->getAsString(qpidFedOrigin);
    }

    if (fedOp == fedOpUnbind) {
        return fedUnbind(fedOrigin, fedTags, queue, bindingKey, args);
    }
    else if (fedOp == fedOpReorigin) {
        fedReorigin();
        return true;
    }

    // OK, looks like we're really going to bind
    
    else if (fedOp.empty() || fedOp == fedOpBind) {

        std::string queryText = args->getAsString("xquery");

        RWlock::ScopedWlock l(lock);   
 
        XmlBinding::vector& bindings(bindingsMap[bindingKey]);
        XmlBinding::vector::ConstPtr p = bindings.snapshot();
       
        if (!p || std::find_if(p->begin(), p->end(), MatchQueueAndOrigin(queue, fedOrigin)) == p->end()) {

            XmlBinding::shared_ptr binding(new XmlBinding (bindingKey, queue, fedOrigin, this, *args, queryText));
            bindings.add(binding);

            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
            }
        } else {
            return false;
        }
    }
    else {
        QPID_LOG(warning, "Unknown Federation Op: " << fedOp);
    }
 
    routeIVE();
    propagateFedOp(bindingKey, fedTags, fedOp, fedOrigin, args);

    return true;
}

bool XmlExchange::unbind(Queue::shared_ptr queue, const std::string& bindingKey, const FieldTable* args)
{
    RWlock::ScopedWlock l(lock);
    return unbindLH(queue, bindingKey, args);
}

bool XmlExchange::unbindLH(Queue::shared_ptr queue, const std::string& bindingKey, const FieldTable* args)
{
    /*
     *  When called directly, no qpidFedOrigin argument will be
     *  present. When called from federation, it will be present. 
     *
     *  This is a bit of a hack - the binding needs the origin, but
     *  this interface, as originally defined, would not supply one.
     *
     *  Note: caller must hold Wlock
     */
    std::string fedOrigin;
    if (args) fedOrigin = args->getAsString(qpidFedOrigin);

    if (bindingsMap[bindingKey].remove_if(MatchQueueAndOrigin(queue, fedOrigin))) {
        if (mgmtExchange != 0) {
            mgmtExchange->dec_bindingCount();
        }
        if (bindingsMap[bindingKey].empty()) bindingsMap.erase(bindingKey);
        if (bindingsMap.empty()) checkAutodelete();
        return true;
    } else {
        return false;
    }
}

namespace {
class DefineExternals : public qpid::amqp::MapHandler
{
  public:
    DefineExternals(DynamicContext* c) : context(c) { assert(context); }
    void handleBool(const qpid::amqp::CharSequence& key, bool value) { process(std::string(key.data, key.size), (int) value); }
    void handleUint8(const qpid::amqp::CharSequence& key, uint8_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleUint16(const qpid::amqp::CharSequence& key, uint16_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleUint32(const qpid::amqp::CharSequence& key, uint32_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleUint64(const qpid::amqp::CharSequence& key, uint64_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleInt8(const qpid::amqp::CharSequence& key, int8_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleInt16(const qpid::amqp::CharSequence& key, int16_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleInt32(const qpid::amqp::CharSequence& key, int32_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleInt64(const qpid::amqp::CharSequence& key, int64_t value) { process(std::string(key.data, key.size), (int) value); }
    void handleFloat(const qpid::amqp::CharSequence& key, float value) { process(std::string(key.data, key.size), value); }
    void handleDouble(const qpid::amqp::CharSequence& key, double value) { process(std::string(key.data, key.size), value); }
    void handleString(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::CharSequence& /*encoding*/)
    {
        process(std::string(key.data, key.size), std::string(value.data, value.size));
    }
    void handleVoid(const qpid::amqp::CharSequence&) {}
  private:
    void process(const std::string& key, double value)
    {
        QPID_LOG(trace, "XmlExchange, external variable (double): " << key << " = " << value);
        Item::Ptr item = context->getItemFactory()->createDouble(value, context);
        context->setExternalVariable(X(key.c_str()), item);
    }
    void process(const std::string& key, int value)
    {
        QPID_LOG(trace, "XmlExchange, external variable (int):" << key << " = " << value);
        Item::Ptr item = context->getItemFactory()->createInteger(value, context);
        context->setExternalVariable(X(key.c_str()), item);
    }
    void process(const std::string& key, const std::string& value)
    {
        QPID_LOG(trace, "XmlExchange, external variable (string):" << key << " = " << value);
        Item::Ptr item = context->getItemFactory()->createString(X(value.c_str()), context);
        context->setExternalVariable(X(key.c_str()), item);
    }

    DynamicContext* context;
};

}

bool XmlExchange::matches(Query& query, Deliverable& msg, bool parse_message_content) 
{
    std::string msgContent;

    try {
        QPID_LOG(trace, "matches: query is [" << UTF8(query->getQueryText()) << "]");

        boost::scoped_ptr<DynamicContext> context(query->createDynamicContext());
        if (!context.get()) {
            throw InternalErrorException(QPID_MSG("Query context looks munged ..."));
        }

        if (parse_message_content) {

            if (resolver) context->setXMLEntityResolver(resolver.get());
            msgContent = msg.getMessage().getContent();

            QPID_LOG(trace, "matches: message content is [" << msgContent << "]");

            XERCES_CPP_NAMESPACE::MemBufInputSource xml((const XMLByte*) msgContent.c_str(), 
                                                        msgContent.length(), "input" );

            // This will parse the document using either Xerces or FastXDM, depending
            // on your XQilla configuration. FastXDM can be as much as 10x faster.

            Sequence seq(context->parseDocument(xml));

            if(!seq.isEmpty() && seq.first()->isNode()) {
                context->setContextItem(seq.first());
                context->setContextPosition(1);
                context->setContextSize(1);
            }
        }

        DefineExternals f(context.get());
        msg.getMessage().processProperties(f);

        Result result = query->execute(context.get());
#ifdef XQ_EFFECTIVE_BOOLEAN_VALUE_HPP
        Item::Ptr first_ = result->next(context.get());
        Item::Ptr second_ = result->next(context.get());
        return XQEffectiveBooleanValue::get(first_, second_, context.get(), 0);
#else 
        return result->getEffectiveBooleanValue(context.get(), 0);
#endif
    }
    catch (XQException& e) {
        QPID_LOG(warning, "Could not parse XML content (or message headers):" << msgContent);
    }
    catch (...) {
        QPID_LOG(warning, "Unexpected error routing message: " << msgContent);
    }
    return 0;
}

// Future optimization: If any query in a binding for a given routing key requires
// message content, parse the message once, and use that parsed form for all bindings.
//
// Future optimization: XQilla does not currently do document projection for data
// accessed via the context item. If there is a single query for a given routing key,
// and it accesses document data, this could be a big win.
//
// Document projection often is not a win if you have multiple queries on the same data.
// But for very large messages, if all these queries are on the first part of the data,
// it could still be a big win.

void XmlExchange::route(Deliverable& msg)
{
    const std::string& routingKey = msg.getMessage().getRoutingKey();
    PreRoute pr(msg, this);
    try {
        XmlBinding::vector::ConstPtr p;
        BindingList b(new std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >);
        {
            RWlock::ScopedRlock l(lock);
            p = bindingsMap[routingKey].snapshot();
        }

        if (p.get()) {
            for (std::vector<XmlBinding::shared_ptr>::const_iterator i = p->begin(); i != p->end(); i++) {
                   if (matches((*i)->xquery, msg, (*i)->parse_message_content)) {
                       b->push_back(*i);
                }
             }
        }
        // else allow stats to be counted, even for non-matched messages
        doRoute(msg, b);
    } catch (...) {
        QPID_LOG(warning, "XMLExchange " << getName() << ": exception routing message with query " << routingKey);
    }
}


bool XmlExchange::isBound(Queue::shared_ptr queue, const std::string* const bindingKey, const FieldTable* const) 
{
    RWlock::ScopedRlock l(lock);
    if (bindingKey) {
        XmlBindingsMap::iterator i = bindingsMap.find(*bindingKey);

        if (i == bindingsMap.end())
            return false;
        if (!queue)
            return true;
        XmlBinding::vector::ConstPtr p = i->second.snapshot();
        return p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end();
    } else if (!queue) {
        //if no queue or routing key is specified, just report whether any bindings exist
        return bindingsMap.size() > 0;
    } else {
        for (XmlBindingsMap::iterator i = bindingsMap.begin(); i != bindingsMap.end(); i++) {
            XmlBinding::vector::ConstPtr p = i->second.snapshot();
            if (p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end()) return true;
        }
        return false;
    }

}

XmlExchange::~XmlExchange() 
{
    if (mgmtExchange != 0)
        mgmtExchange->debugStats("destroying");
    bindingsMap.clear();
}

void XmlExchange::propagateFedOp(const std::string& bindingKey, const std::string& fedTags, const std::string& fedOp, const std::string& fedOrigin, const qpid::framing::FieldTable* args)
{
    FieldTable nonFedArgs;

    if (args) {            
        for (qpid::framing::FieldTable::ValueMap::const_iterator i=args->begin(); i != args->end(); ++i)  {
            const std::string& name(i->first);
            if (name != qpidFedOp &&
                name != qpidFedTags &&
                name != qpidFedOrigin)  {
                nonFedArgs.insert((*i));
            }
        }
    }

    FieldTable* propArgs  = (nonFedArgs.count() > 0 ? &nonFedArgs : 0);
    Exchange::propagateFedOp(bindingKey, fedTags, fedOp, fedOrigin, propArgs);
}

bool XmlExchange::fedUnbind(const std::string& fedOrigin, const std::string& fedTags, Queue::shared_ptr queue, const std::string& bindingKey, const FieldTable* args)
{
    RWlock::ScopedWlock l(lock);

    if (unbindLH(queue, bindingKey, args)) {
        propagateFedOp(bindingKey, fedTags, fedOpUnbind, fedOrigin); 
        return true;
    }
    return false;
}

void XmlExchange::fedReorigin()
{
    std::vector<std::string> keys2prop;
    {
        RWlock::ScopedRlock l(lock);   
        for (XmlBindingsMap::iterator i = bindingsMap.begin(); i != bindingsMap.end(); ++i) {
            XmlBinding::vector::ConstPtr p = i->second.snapshot();
            if (std::find_if(p->begin(), p->end(), MatchOrigin(std::string())) != p->end()) {
                keys2prop.push_back(i->first);
            }
        }
    }   /* lock dropped */
    for (std::vector<std::string>::const_iterator key = keys2prop.begin();
         key != keys2prop.end(); key++) {
        propagateFedOp( *key, std::string(), fedOpBind, std::string());
    }
}


XmlExchange::MatchOrigin::MatchOrigin(const std::string& _origin) : origin(_origin) {}

bool XmlExchange::MatchOrigin::operator()(XmlBinding::shared_ptr b)
{
    return b->fedOrigin == origin;
}


XmlExchange::MatchQueueAndOrigin::MatchQueueAndOrigin(Queue::shared_ptr _queue, const std::string& _origin) : queue(_queue), origin(_origin) {}

bool XmlExchange::MatchQueueAndOrigin::operator()(XmlBinding::shared_ptr b)
{
    return b->queue == queue and b->fedOrigin == origin;
}


const std::string XmlExchange::typeName("xml");

bool XmlExchange::hasBindings()
{
    RWlock::ScopedRlock l(lock);
    return !bindingsMap.empty();
}
}
}
