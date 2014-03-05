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


#include "qpid/RefCounted.h"
#include "qpid/Exception.h"
#include <boost/shared_ptr.hpp>
#include <map>
#include <set>
#include <string>
#include <sstream>

namespace qpid {

namespace acl {

    // Interface enumerations.
    // These enumerations define enum lists and implied text strings
    // to match. They are used in two areas:
    // 1. In the ACL specifications in the ACL file, file parsing, and
    //    internal rule storage.
    // 2. In the authorize interface in the rest of the broker where
    //    code requests the ACL module to authorize an action.

    // ObjectType  shared between ACL spec and ACL authorise interface
    enum ObjectType {
        OBJ_QUEUE,
        OBJ_EXCHANGE,
        OBJ_BROKER,
        OBJ_LINK,
        OBJ_METHOD,
        OBJECTSIZE }; // OBJECTSIZE must be last in list

    // Action  shared between ACL spec and ACL authorise interface
    enum Action {
        ACT_CONSUME,
        ACT_PUBLISH,
        ACT_CREATE,
        ACT_ACCESS,
        ACT_BIND,
        ACT_UNBIND,
        ACT_DELETE,
        ACT_PURGE,
        ACT_UPDATE,
        ACTIONSIZE }; // ACTIONSIZE must be last in list

    // Property used in ACL authorize interface
    enum Property {
        PROP_NAME,
        PROP_DURABLE,
        PROP_OWNER,
        PROP_ROUTINGKEY,
        PROP_AUTODELETE,
        PROP_EXCLUSIVE,
        PROP_TYPE,
        PROP_ALTERNATE,
        PROP_QUEUENAME,
        PROP_SCHEMAPACKAGE,
        PROP_SCHEMACLASS,
        PROP_POLICYTYPE,
        PROP_PAGING,
        PROP_MAXPAGES,
        PROP_MAXPAGEFACTOR,
        PROP_MAXQUEUESIZE,
        PROP_MAXQUEUECOUNT,
        PROP_MAXFILESIZE,
        PROP_MAXFILECOUNT,
        PROPERTYSIZE           // PROPERTYSIZE must be last in list
    };

    // Property used in ACL spec file
    // Note for properties common to file processing/rule storage and to
    // broker rule lookups the identical enum values are used.
    enum SpecProperty {
        SPECPROP_NAME            = PROP_NAME,
        SPECPROP_DURABLE         = PROP_DURABLE,
        SPECPROP_OWNER           = PROP_OWNER,
        SPECPROP_ROUTINGKEY      = PROP_ROUTINGKEY,
        SPECPROP_AUTODELETE      = PROP_AUTODELETE,
        SPECPROP_EXCLUSIVE       = PROP_EXCLUSIVE,
        SPECPROP_TYPE            = PROP_TYPE,
        SPECPROP_ALTERNATE       = PROP_ALTERNATE,
        SPECPROP_QUEUENAME       = PROP_QUEUENAME,
        SPECPROP_SCHEMAPACKAGE   = PROP_SCHEMAPACKAGE,
        SPECPROP_SCHEMACLASS     = PROP_SCHEMACLASS,
        SPECPROP_POLICYTYPE      = PROP_POLICYTYPE,
        SPECPROP_PAGING          = PROP_PAGING,

        SPECPROP_MAXQUEUESIZELOWERLIMIT,
        SPECPROP_MAXQUEUESIZEUPPERLIMIT,
        SPECPROP_MAXQUEUECOUNTLOWERLIMIT,
        SPECPROP_MAXQUEUECOUNTUPPERLIMIT,
        SPECPROP_MAXFILESIZELOWERLIMIT,
        SPECPROP_MAXFILESIZEUPPERLIMIT,
        SPECPROP_MAXFILECOUNTLOWERLIMIT,
        SPECPROP_MAXFILECOUNTUPPERLIMIT,
        SPECPROP_MAXPAGESLOWERLIMIT,
        SPECPROP_MAXPAGESUPPERLIMIT,
        SPECPROP_MAXPAGEFACTORLOWERLIMIT, 
        SPECPROP_MAXPAGEFACTORUPPERLIMIT };

// AclResult  shared between ACL spec and ACL authorise interface
    enum AclResult {
        ALLOW,
        ALLOWLOG,
        DENY,
        DENYLOG };

} // namespace acl

namespace broker {

    class Connection;

    class AclModule
    {

    public:

        // Some ACLs are invoked on every message transfer.
        // doTransferAcl pervents time consuming ACL calls on a per-message basis.
        virtual bool doTransferAcl()=0;

        virtual uint16_t getMaxConnectTotal()=0;

        virtual bool userAclRules()=0;

        virtual bool authorise(
            const std::string&                    id,
            const acl::Action&                    action,
            const acl::ObjectType&                objType,
            const std::string&                    name,
            std::map<acl::Property, std::string>* params=0)=0;

        virtual bool authorise(
            const std::string&      id,
            const acl::Action&      action,
            const acl::ObjectType&  objType,
            const std::string&      ExchangeName,
            const std::string&      RoutingKey)=0;

        // Add specialized authorise() methods as required.

        /** Approve connection by counting connections total, per-IP, and
         *  per-user.
         */
        virtual bool approveConnection (const Connection& connection)=0;

        /** Approve queue creation by counting per-user.
         */
        virtual bool approveCreateQueue(const std::string& userId, const std::string& queueName)=0;
        virtual void recordDestroyQueue(const std::string& queueName)=0;

        virtual ~AclModule() {};
    };
} // namespace broker

namespace acl {

    class AclHelper {
    private:
        AclHelper(){}
    public:
        static inline ObjectType getObjectType(const std::string& str) {
            if (str.compare("queue")    == 0) return OBJ_QUEUE;
            if (str.compare("exchange") == 0) return OBJ_EXCHANGE;
            if (str.compare("broker")   == 0) return OBJ_BROKER;
            if (str.compare("link")     == 0) return OBJ_LINK;
            if (str.compare("method")   == 0) return OBJ_METHOD;
            throw qpid::Exception(str);
        }
        static inline std::string getObjectTypeStr(const ObjectType o) {
            switch (o) {
            case OBJ_QUEUE:    return "queue";
            case OBJ_EXCHANGE: return "exchange";
            case OBJ_BROKER:   return "broker";
            case OBJ_LINK:     return "link";
            case OBJ_METHOD:   return "method";
            default: assert(false); // should never get here
            }
            return "";
        }
        static inline Action getAction(const std::string& str) {
            if (str.compare("consume") == 0) return ACT_CONSUME;
            if (str.compare("publish") == 0) return ACT_PUBLISH;
            if (str.compare("create")  == 0) return ACT_CREATE;
            if (str.compare("access")  == 0) return ACT_ACCESS;
            if (str.compare("bind")    == 0) return ACT_BIND;
            if (str.compare("unbind")  == 0) return ACT_UNBIND;
            if (str.compare("delete")  == 0) return ACT_DELETE;
            if (str.compare("purge")   == 0) return ACT_PURGE;
            if (str.compare("update")  == 0) return ACT_UPDATE;
            throw qpid::Exception(str);
        }
        static inline std::string getActionStr(const Action a) {
            switch (a) {
            case ACT_CONSUME: return "consume";
            case ACT_PUBLISH: return "publish";
            case ACT_CREATE:  return "create";
            case ACT_ACCESS:  return "access";
            case ACT_BIND:    return "bind";
            case ACT_UNBIND:  return "unbind";
            case ACT_DELETE:  return "delete";
            case ACT_PURGE:   return "purge";
            case ACT_UPDATE:  return "update";
            default: assert(false); // should never get here
            }
            return "";
        }
        static inline Property getProperty(const std::string& str) {
            if (str.compare("name")          == 0) return PROP_NAME;
            if (str.compare("durable")       == 0) return PROP_DURABLE;
            if (str.compare("owner")         == 0) return PROP_OWNER;
            if (str.compare("routingkey")    == 0) return PROP_ROUTINGKEY;
            if (str.compare("autodelete")    == 0) return PROP_AUTODELETE;
            if (str.compare("exclusive")     == 0) return PROP_EXCLUSIVE;
            if (str.compare("type")          == 0) return PROP_TYPE;
            if (str.compare("alternate")     == 0) return PROP_ALTERNATE;
            if (str.compare("queuename")     == 0) return PROP_QUEUENAME;
            if (str.compare("schemapackage") == 0) return PROP_SCHEMAPACKAGE;
            if (str.compare("schemaclass")   == 0) return PROP_SCHEMACLASS;
            if (str.compare("policytype")    == 0) return PROP_POLICYTYPE;
            if (str.compare("paging")        == 0) return PROP_PAGING;
            if (str.compare("maxpages")      == 0) return PROP_MAXPAGES;
            if (str.compare("maxpagefactor") == 0) return PROP_MAXPAGEFACTOR;
            if (str.compare("maxqueuesize")  == 0) return PROP_MAXQUEUESIZE;
            if (str.compare("maxqueuecount") == 0) return PROP_MAXQUEUECOUNT;
            if (str.compare("maxfilesize")   == 0) return PROP_MAXFILESIZE;
            if (str.compare("maxfilecount")  == 0) return PROP_MAXFILECOUNT;
            throw qpid::Exception(str);
        }
        static inline std::string getPropertyStr(const Property p) {
            switch (p) {
            case PROP_NAME:          return "name";
            case PROP_DURABLE:       return "durable";
            case PROP_OWNER:         return "owner";
            case PROP_ROUTINGKEY:    return "routingkey";
            case PROP_AUTODELETE:    return "autodelete";
            case PROP_EXCLUSIVE:     return "exclusive";
            case PROP_TYPE:          return "type";
            case PROP_ALTERNATE:     return "alternate";
            case PROP_QUEUENAME:     return "queuename";
            case PROP_SCHEMAPACKAGE: return "schemapackage";
            case PROP_SCHEMACLASS:   return "schemaclass";
            case PROP_POLICYTYPE:    return "policytype";
            case PROP_PAGING:        return "paging";
            case PROP_MAXPAGES:      return "maxpages";
            case PROP_MAXPAGEFACTOR: return "maxpagefactor";
            case PROP_MAXQUEUESIZE:  return "maxqueuesize";
            case PROP_MAXQUEUECOUNT: return "maxqueuecount";
            case PROP_MAXFILESIZE:   return "maxfilesize";
            case PROP_MAXFILECOUNT:  return "maxfilecount";
            default: assert(false); // should never get here
            }
            return "";
        }
        static inline SpecProperty getSpecProperty(const std::string& str) {
            if (str.compare("name")          == 0) return SPECPROP_NAME;
            if (str.compare("durable")       == 0) return SPECPROP_DURABLE;
            if (str.compare("owner")         == 0) return SPECPROP_OWNER;
            if (str.compare("routingkey")    == 0) return SPECPROP_ROUTINGKEY;
            if (str.compare("autodelete")    == 0) return SPECPROP_AUTODELETE;
            if (str.compare("exclusive")     == 0) return SPECPROP_EXCLUSIVE;
            if (str.compare("type")          == 0) return SPECPROP_TYPE;
            if (str.compare("alternate")     == 0) return SPECPROP_ALTERNATE;
            if (str.compare("queuename")     == 0) return SPECPROP_QUEUENAME;
            if (str.compare("schemapackage") == 0) return SPECPROP_SCHEMAPACKAGE;
            if (str.compare("schemaclass")   == 0) return SPECPROP_SCHEMACLASS;
            if (str.compare("policytype")    == 0) return SPECPROP_POLICYTYPE;
            if (str.compare("paging")        == 0) return SPECPROP_PAGING;
            if (str.compare("queuemaxsizelowerlimit")   == 0) return SPECPROP_MAXQUEUESIZELOWERLIMIT;
            if (str.compare("queuemaxsizeupperlimit")   == 0) return SPECPROP_MAXQUEUESIZEUPPERLIMIT;
            if (str.compare("queuemaxcountlowerlimit")  == 0) return SPECPROP_MAXQUEUECOUNTLOWERLIMIT;
            if (str.compare("queuemaxcountupperlimit")  == 0) return SPECPROP_MAXQUEUECOUNTUPPERLIMIT;
            if (str.compare("filemaxsizelowerlimit")    == 0) return SPECPROP_MAXFILESIZELOWERLIMIT;
            if (str.compare("filemaxsizeupperlimit")    == 0) return SPECPROP_MAXFILESIZEUPPERLIMIT;
            if (str.compare("filemaxcountlowerlimit")   == 0) return SPECPROP_MAXFILECOUNTLOWERLIMIT;
            if (str.compare("filemaxcountupperlimit")   == 0) return SPECPROP_MAXFILECOUNTUPPERLIMIT;
            if (str.compare("pageslowerlimit")          == 0) return SPECPROP_MAXPAGESLOWERLIMIT;
            if (str.compare("pagesupperlimit")          == 0) return SPECPROP_MAXPAGESUPPERLIMIT;
            if (str.compare("pagefactorlowerlimit")     == 0) return SPECPROP_MAXPAGEFACTORLOWERLIMIT;
            if (str.compare("pagefactorupperlimit")     == 0) return SPECPROP_MAXPAGEFACTORUPPERLIMIT;
            // Allow old names in ACL file as aliases for newly-named properties
            if (str.compare("maxqueuesize")             == 0) return SPECPROP_MAXQUEUESIZEUPPERLIMIT;
            if (str.compare("maxqueuecount")            == 0) return SPECPROP_MAXQUEUECOUNTUPPERLIMIT;
            throw qpid::Exception(str);
        }
        static inline std::string getPropertyStr(const SpecProperty p) {
            switch (p) {
                case SPECPROP_NAME:          return "name";
                case SPECPROP_DURABLE:       return "durable";
                case SPECPROP_OWNER:         return "owner";
                case SPECPROP_ROUTINGKEY:    return "routingkey";
                case SPECPROP_AUTODELETE:    return "autodelete";
                case SPECPROP_EXCLUSIVE:     return "exclusive";
                case SPECPROP_TYPE:          return "type";
                case SPECPROP_ALTERNATE:     return "alternate";
                case SPECPROP_QUEUENAME:     return "queuename";
                case SPECPROP_SCHEMAPACKAGE: return "schemapackage";
                case SPECPROP_SCHEMACLASS:   return "schemaclass";
                case SPECPROP_POLICYTYPE:    return "policytype";
                case SPECPROP_PAGING:        return "paging";
                case SPECPROP_MAXQUEUESIZELOWERLIMIT:  return "queuemaxsizelowerlimit";
                case SPECPROP_MAXQUEUESIZEUPPERLIMIT:  return "queuemaxsizeupperlimit";
                case SPECPROP_MAXQUEUECOUNTLOWERLIMIT: return "queuemaxcountlowerlimit";
                case SPECPROP_MAXQUEUECOUNTUPPERLIMIT: return "queuemaxcountupperlimit";
                case SPECPROP_MAXFILESIZELOWERLIMIT:   return "filemaxsizelowerlimit";
                case SPECPROP_MAXFILESIZEUPPERLIMIT:   return "filemaxsizeupperlimit";
                case SPECPROP_MAXFILECOUNTLOWERLIMIT:  return "filemaxcountlowerlimit";
                case SPECPROP_MAXFILECOUNTUPPERLIMIT:  return "filemaxcountupperlimit";
                case SPECPROP_MAXPAGESLOWERLIMIT:      return "pageslowerlimit";
                case SPECPROP_MAXPAGESUPPERLIMIT:      return "pagesupperlimit";
                case SPECPROP_MAXPAGEFACTORLOWERLIMIT: return "pagefactorlowerlimit";
                case SPECPROP_MAXPAGEFACTORUPPERLIMIT: return "pagefactorupperlimit";
                default: assert(false); // should never get here
            }
            return "";
        }
        static inline AclResult getAclResult(const std::string& str) {
            if (str.compare("allow")     == 0) return ALLOW;
            if (str.compare("allow-log") == 0) return ALLOWLOG;
            if (str.compare("deny")      == 0) return DENY;
            if (str.compare("deny-log")  == 0) return DENYLOG;
            throw qpid::Exception(str);
        }
        static inline std::string getAclResultStr(const AclResult r) {
            switch (r) {
            case ALLOW:    return "allow";
            case ALLOWLOG: return "allow-log";
            case DENY:     return "deny";
            case DENYLOG:  return "deny-log";
            default: assert(false); // should never get here
            }
            return "";
        }

        typedef std::set<Property>                  propSet;
        typedef boost::shared_ptr<propSet>          propSetPtr;
        typedef std::pair<Action, propSetPtr>       actionPair;
        typedef std::map<Action, propSetPtr>        actionMap;
        typedef boost::shared_ptr<actionMap>        actionMapPtr;
        typedef std::pair<ObjectType, actionMapPtr> objectPair;
        typedef std::map<ObjectType, actionMapPtr>  objectMap;
        typedef objectMap::const_iterator           omCitr;
        typedef boost::shared_ptr<objectMap>        objectMapPtr;
        typedef std::map<Property, std::string>     propMap;
        typedef propMap::const_iterator             propMapItr;
        typedef std::map<SpecProperty, std::string> specPropMap;
        typedef specPropMap::const_iterator         specPropMapItr;

        // This map contains the legal combinations of object/action/properties
        // found in an ACL file
        static void loadValidationMap(objectMapPtr& map) {
            if (!map.get()) return;
            map->clear();
            propSetPtr p0; // empty ptr, used for no properties

            // == Exchanges ==

            propSetPtr p1(new propSet);
            p1->insert(PROP_TYPE);
            p1->insert(PROP_ALTERNATE);
            p1->insert(PROP_DURABLE);

            propSetPtr p2(new propSet);
            p2->insert(PROP_ROUTINGKEY);

            propSetPtr p3(new propSet);
            p3->insert(PROP_QUEUENAME);
            p3->insert(PROP_ROUTINGKEY);

            actionMapPtr a0(new actionMap);
            a0->insert(actionPair(ACT_CREATE,  p1));
            a0->insert(actionPair(ACT_DELETE,  p0));
            a0->insert(actionPair(ACT_ACCESS,  p0));
            a0->insert(actionPair(ACT_BIND,    p2));
            a0->insert(actionPair(ACT_UNBIND,  p2));
            a0->insert(actionPair(ACT_ACCESS,  p3));
            a0->insert(actionPair(ACT_PUBLISH, p0));

            map->insert(objectPair(OBJ_EXCHANGE, a0));

            // == Queues ==

            propSetPtr p4(new propSet);
            p4->insert(PROP_ALTERNATE);
            p4->insert(PROP_DURABLE);
            p4->insert(PROP_EXCLUSIVE);
            p4->insert(PROP_AUTODELETE);
            p4->insert(PROP_POLICYTYPE);
            p4->insert(PROP_PAGING);
            p4->insert(PROP_MAXPAGES);
            p4->insert(PROP_MAXPAGEFACTOR);
            p4->insert(PROP_MAXQUEUESIZE);
            p4->insert(PROP_MAXQUEUECOUNT);

            actionMapPtr a1(new actionMap);
            a1->insert(actionPair(ACT_ACCESS,  p0));
            a1->insert(actionPair(ACT_CREATE,  p4));
            a1->insert(actionPair(ACT_PURGE,   p0));
            a1->insert(actionPair(ACT_DELETE,  p0));
            a1->insert(actionPair(ACT_CONSUME, p0));

            map->insert(objectPair(OBJ_QUEUE, a1));

            // == Links ==

            actionMapPtr a2(new actionMap);
            a2->insert(actionPair(ACT_CREATE,  p0));

            map->insert(objectPair(OBJ_LINK, a2));

            // == Method ==

            propSetPtr p5(new propSet);
            p5->insert(PROP_SCHEMAPACKAGE);
            p5->insert(PROP_SCHEMACLASS);

            actionMapPtr a4(new actionMap);
            a4->insert(actionPair(ACT_ACCESS, p5));

            map->insert(objectPair(OBJ_METHOD, a4));
        }

        //
        // properyMapToString
        //
        template <typename T>
        static std::string propertyMapToString(
            const std::map<T, std::string>* params)
        {
            std::ostringstream ss;
            ss << "{";
            if (params)
            {
                for (typename std::map<T, std::string>::const_iterator
                     pMItr = params->begin(); pMItr != params->end(); pMItr++)
                {
                    ss << " " << getPropertyStr((T) pMItr-> first)
                    << "=" << pMItr->second;
                }
            }
            ss << " }";
            return ss.str();
        }

    };


}} // namespace qpid::acl

#endif // QPID_ACLMODULE_ACL_H
