#ifndef QPID_ACLMODULE_ACL_H
#define QPID_ACLMODULE_ACL_H


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



#include "qpid/shared_ptr.h"
#include "qpid/RefCounted.h"
#include <map>
#include <set>
#include <string>


namespace qpid {

namespace acl {

enum ObjectType {QUEUE, EXCHANGE, BROKER, LINK, ROUTE, METHOD, OBJECTSIZE}; // OBJECTSIZE must be last in list
enum Action {CONSUME, PUBLISH, CREATE, ACCESS, BIND, UNBIND, DELETE, PURGE,
             UPDATE, ACTIONSIZE}; // ACTIONSIZE must be last in list
enum Property {NAME, DURABLE, OWNER, ROUTINGKEY, PASSIVE, AUTODELETE, EXCLUSIVE, TYPE, ALTERNATE,
               QUEUENAME, SCHEMAPACKAGE, SCHEMACLASS};
enum AclResult {ALLOW, ALLOWLOG, DENY, DENYLOG};	

} // namespace acl

namespace broker {


class AclModule
{

public:
   
   // effienty turn off ACL on message transfer.
   virtual bool doTransferAcl()=0;
   
   virtual bool authorise(const std::string& id, const acl::Action& action, const acl::ObjectType& objType, const std::string& name, 
       std::map<acl::Property, std::string>* params=0)=0;
   virtual bool authorise(const std::string& id, const acl::Action& action, const acl::ObjectType& objType, const std::string& ExchangeName, 
       const std::string& RoutingKey)=0;
   // create specilied authorise methods for cases that need faster matching as needed.

   virtual ~AclModule() {};
};

} // namespace broker

namespace acl {

class AclHelper {
  private:
    AclHelper(){}
  public:
    static inline ObjectType getObjectType(const std::string& str) {
        if (str.compare("queue") == 0) return QUEUE;
        if (str.compare("exchange") == 0) return EXCHANGE;
        if (str.compare("broker") == 0) return BROKER;
        if (str.compare("link") == 0) return LINK;
        if (str.compare("route") == 0) return ROUTE;
        if (str.compare("method") == 0) return METHOD;
        throw str;
    }
    static inline std::string getObjectTypeStr(const ObjectType o) {
        switch (o) {
          case QUEUE: return "queue";
          case EXCHANGE: return "exchange";
          case BROKER: return "broker";
          case LINK: return "link";
          case ROUTE: return "route";
          case METHOD: return "method";
          default: assert(false); // should never get here
        }
        return "";
    }
    static inline Action getAction(const std::string& str) {
        if (str.compare("consume") == 0) return CONSUME;
        if (str.compare("publish") == 0) return PUBLISH;
        if (str.compare("create") == 0) return CREATE;
        if (str.compare("access") == 0) return ACCESS;
        if (str.compare("bind") == 0) return BIND;
        if (str.compare("unbind") == 0) return UNBIND;
        if (str.compare("delete") == 0) return DELETE;
        if (str.compare("purge") == 0) return PURGE;
        if (str.compare("update") == 0) return UPDATE;
        throw str;
    }
    static inline std::string getActionStr(const Action a) {
        switch (a) {
          case CONSUME: return "consume";
          case PUBLISH: return "publish";
          case CREATE: return "create";
          case ACCESS: return "access";
          case BIND: return "bind";
          case UNBIND: return "unbind";
          case DELETE: return "delete";
          case PURGE: return "purge";
          case UPDATE: return "update";
          default: assert(false); // should never get here
        }
        return "";
    }
    static inline Property getProperty(const std::string& str) {
        if (str.compare("name") == 0) return NAME;
        if (str.compare("durable") == 0) return DURABLE;
        if (str.compare("owner") == 0) return OWNER;
        if (str.compare("routingkey") == 0) return ROUTINGKEY;
        if (str.compare("passive") == 0) return PASSIVE;
        if (str.compare("autodelete") == 0) return AUTODELETE;
        if (str.compare("exclusive") == 0) return EXCLUSIVE;
        if (str.compare("type") == 0) return TYPE;
        if (str.compare("alternate") == 0) return ALTERNATE;
        if (str.compare("queuename") == 0) return QUEUENAME;
        if (str.compare("schemapackage") == 0) return SCHEMAPACKAGE;
        if (str.compare("schemaclass") == 0) return SCHEMACLASS;
        throw str;
    }
    static inline std::string getPropertyStr(const Property p) {
        switch (p) {
          case NAME: return "name";
          case DURABLE: return "durable";
          case OWNER: return "owner";
          case ROUTINGKEY: return "routingkey";
          case PASSIVE: return "passive";
          case AUTODELETE: return "autodelete";
          case EXCLUSIVE: return "exclusive";
          case TYPE: return "type";
          case ALTERNATE: return "alternate";
          case QUEUENAME: return "queuename";
          case SCHEMAPACKAGE: return "schemapackage";
          case SCHEMACLASS: return "schemaclass";
          default: assert(false); // should never get here
        }
        return "";
    }
    static inline AclResult getAclResult(const std::string& str) {
        if (str.compare("allow") == 0) return ALLOW;
        if (str.compare("allow-log") == 0) return ALLOWLOG;
        if (str.compare("deny") == 0) return DENY;
        if (str.compare("deny-log") == 0) return DENYLOG;
        throw str;
    }
    static inline std::string getAclResultStr(const AclResult r) {
        switch (r) {
          case ALLOW: return "allow";
          case ALLOWLOG: return "allow-log";
          case DENY: return "deny";
          case DENYLOG: return "deny-log";
          default: assert(false); // should never get here
        }
        return "";
    }

    typedef std::set<Property> propSet;
    typedef boost::shared_ptr<propSet> propSetPtr;
    typedef std::pair<Action, propSetPtr> actionPair;
    typedef std::map<Action, propSetPtr> actionMap;
    typedef boost::shared_ptr<actionMap> actionMapPtr;
    typedef std::pair<ObjectType, actionMapPtr> objectPair;
    typedef std::map<ObjectType, actionMapPtr> objectMap;
    typedef objectMap::const_iterator omCitr;
    typedef boost::shared_ptr<objectMap> objectMapPtr;

    // This map contains the legal combinations of object/action/properties found in an ACL file
    static void loadValidationMap(objectMapPtr& map) {
        if (!map.get()) return;
        map->clear();
        propSetPtr p0; // empty ptr, used for no properties

        // == Exchanges ==

        propSetPtr p1(new propSet);
        p1->insert(TYPE);
        p1->insert(ALTERNATE);
        p1->insert(PASSIVE);
        p1->insert(DURABLE);

        propSetPtr p2(new propSet);
        p2->insert(ROUTINGKEY);

        propSetPtr p3(new propSet);
        p3->insert(QUEUENAME);
        p3->insert(ROUTINGKEY);

        actionMapPtr a0(new actionMap);
        a0->insert(actionPair(CREATE,  p1));
        a0->insert(actionPair(DELETE,  p0));
        a0->insert(actionPair(ACCESS,  p0));
        a0->insert(actionPair(BIND,    p2));
        a0->insert(actionPair(UNBIND,  p2));
        a0->insert(actionPair(ACCESS,  p3));
        a0->insert(actionPair(PUBLISH, p0));

        map->insert(objectPair(EXCHANGE, a0));

        // == Queues ==

        propSetPtr p4(new propSet);
        p3->insert(ALTERNATE);
        p3->insert(PASSIVE);
        p3->insert(DURABLE);
        p3->insert(EXCLUSIVE);
        p3->insert(AUTODELETE);

        actionMapPtr a1(new actionMap);
        a1->insert(actionPair(ACCESS,  p0));
        a1->insert(actionPair(CREATE,  p4));
        a1->insert(actionPair(PURGE,   p0));
        a1->insert(actionPair(DELETE,  p0));
        a1->insert(actionPair(CONSUME, p0));

        map->insert(objectPair(QUEUE, a1));

        // == Links ==

        actionMapPtr a2(new actionMap);
        a2->insert(actionPair(CREATE,  p0));
        
        map->insert(objectPair(LINK, a2));

        // == Route ==

        actionMapPtr a3(new actionMap);
        a3->insert(actionPair(CREATE,  p0));
        a3->insert(actionPair(DELETE,  p0));
        
        map->insert(objectPair(ROUTE, a3));

        // == Method ==

        propSetPtr p5(new propSet);
        p5->insert(SCHEMAPACKAGE);
        p5->insert(SCHEMACLASS);

        actionMapPtr a4(new actionMap);
        a4->insert(actionPair(ACCESS, p5));

        map->insert(objectPair(METHOD, a4));
    }
};

    
}} // namespace qpid::acl

#endif // QPID_ACLMODULE_ACL_H
