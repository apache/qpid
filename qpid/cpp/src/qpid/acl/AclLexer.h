#ifndef QPID_ACL_ACLLEXER_H
#define QPID_ACL_ACLLEXER_H

/*
 *
 * Copyright (c) 2014 The Apache Software Foundation
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


#include "qpid/Exception.h"
#include "qpid/broker/BrokerImportExport.h"
#include <boost/shared_ptr.hpp>
#include <iostream>
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
        OBJ_BROKER,
        OBJ_CONNECTION,
        OBJ_EXCHANGE,
        OBJ_LINK,
        OBJ_METHOD,
        OBJ_QUERY,
        OBJ_QUEUE,
        OBJECTSIZE }; // OBJECTSIZE must be last in list

    const int OBJECTTYPE_STR_WIDTH = 10;

    // Action  shared between ACL spec and ACL authorise interface
    enum Action {
        ACT_ACCESS,
        ACT_BIND,
        ACT_CONSUME,
        ACT_CREATE,
        ACT_DELETE,
        ACT_MOVE,
        ACT_PUBLISH,
        ACT_PURGE,
        ACT_REDIRECT,
        ACT_REROUTE,
        ACT_UNBIND,
        ACT_UPDATE,
        ACTIONSIZE }; // ACTIONSIZE must be last in list

    const int ACTION_STR_WIDTH = 8;

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
        PROP_EXCHANGENAME,
        PROP_SCHEMAPACKAGE,
        PROP_SCHEMACLASS,
        PROP_POLICYTYPE,
        PROP_PAGING,
        PROP_HOST,
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
        SPECPROP_EXCHANGENAME    = PROP_EXCHANGENAME,
        SPECPROP_SCHEMAPACKAGE   = PROP_SCHEMAPACKAGE,
        SPECPROP_SCHEMACLASS     = PROP_SCHEMACLASS,
        SPECPROP_POLICYTYPE      = PROP_POLICYTYPE,
        SPECPROP_PAGING          = PROP_PAGING,
        SPECPROP_HOST            = PROP_HOST,

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
        SPECPROP_MAXPAGEFACTORUPPERLIMIT,
        SPECPROPSIZE              // SPECPROPSIZE must be last
    };

// AclResult  shared between ACL spec and ACL authorise interface
    enum AclResult {
        ALLOW,
        ALLOWLOG,
        DENY,
        DENYLOG,
        RESULTSIZE
    };


    QPID_BROKER_CLASS_EXTERN class AclHelper {
    private:
        AclHelper(){}
    public:
        static QPID_BROKER_EXTERN ObjectType            getObjectType(const std::string& str);
        static QPID_BROKER_EXTERN const std::string& getObjectTypeStr(const ObjectType o);
        static QPID_BROKER_EXTERN Action                    getAction(const std::string& str);
        static QPID_BROKER_EXTERN const std::string&     getActionStr(const Action a);
        static QPID_BROKER_EXTERN Property                getProperty(const std::string& str);
        static QPID_BROKER_EXTERN const std::string&   getPropertyStr(const Property p);
        static QPID_BROKER_EXTERN SpecProperty        getSpecProperty(const std::string& str);
        static QPID_BROKER_EXTERN const std::string&   getPropertyStr(const SpecProperty p);
        static QPID_BROKER_EXTERN AclResult              getAclResult(const std::string& str);
        static QPID_BROKER_EXTERN const std::string&  getAclResultStr(const AclResult r);
        static QPID_BROKER_EXTERN bool                   resultAllows(const AclResult r);

        typedef std::set<Property>                  propSet;
        typedef boost::shared_ptr<propSet>          propSetPtr;
        typedef std::pair<Action, propSetPtr>       actionPair;
        typedef std::map<Action, propSetPtr>        actionMap;
        typedef boost::shared_ptr<actionMap>        actionMapPtr;
        typedef std::pair<ObjectType, actionMapPtr> objectPair;
        typedef std::map<Property, std::string>     propMap;
        typedef propMap::const_iterator             propMapItr;
        typedef std::map<SpecProperty, std::string> specPropMap;
        typedef specPropMap::const_iterator         specPropMapItr;

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

#endif // QPID_ACL_ACLLEXER_H
