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

#include "qpid/acl/AclLexer.h"
#include "qpid/RefCounted.h"
#include "qpid/Exception.h"
#include <boost/shared_ptr.hpp>
#include <boost/concept_check.hpp>
#include <iostream>
#include <map>
#include <set>
#include <string>
#include <sstream>

namespace qpid {
namespace acl {

// ObjectType
const std::string objectNames[OBJECTSIZE] = {
    "broker", "connection", "exchange", "link", "method", "query", "queue" };

ObjectType AclHelper::getObjectType(const std::string& str) {
    for (int i=0; i< OBJECTSIZE; ++i) {
        if (str.compare(objectNames[i]) == 0)
            return ObjectType(i);
    }
    throw qpid::Exception("Acl illegal object name: " + str);
}

const std::string& AclHelper::getObjectTypeStr(const ObjectType o) {
    return objectNames[o];
}

// Action
const std::string actionNames[ACTIONSIZE] = {
    "access", "bind", "consume", "create", "delete",
    "move", "publish", "purge", "redirect", "reroute",
    "unbind", "update" };

Action AclHelper::getAction(const std::string& str) {
    for (int i=0; i< ACTIONSIZE; ++i) {
        if (str.compare(actionNames[i]) == 0)
            return Action(i);
    }
    throw qpid::Exception("Acl illegal action name: " + str);
}

const std::string& AclHelper::getActionStr(const Action a) {
    return actionNames[a];
}

// Property
// These are shared between broker and acl using code enums.
const std::string propertyNames[PROPERTYSIZE] = {
    "name", "durable", "owner", "routingkey", "autodelete", "exclusive", "type",
    "alternate", "queuename", "exchangename", "schemapackage",
    "schemaclass", "policytype", "paging", "host",

    "maxpages", "maxpagefactor",
    "maxqueuesize", "maxqueuecount", "maxfilesize", "maxfilecount"};

Property AclHelper::getProperty(const std::string& str) {
    for (int i=0; i< PROPERTYSIZE; ++i) {
        if (str.compare(propertyNames[i]) == 0)
            return Property(i);
    }
    throw qpid::Exception("Acl illegal property name: " + str);
}

const std::string& AclHelper::getPropertyStr(const Property p) {
    return propertyNames[p];
}

// SpecProperty
// These are shared between user acl files and acl using text.
const std::string specPropertyNames[SPECPROPSIZE] = {
    "name", "durable", "owner", "routingkey", "autodelete", "exclusive", "type",
    "alternate", "queuename", "exchangename", "schemapackage",
    "schemaclass", "policytype", "paging", "host",

     "queuemaxsizelowerlimit",  "queuemaxsizeupperlimit",
    "queuemaxcountlowerlimit", "queuemaxcountupperlimit",
      "filemaxsizelowerlimit",   "filemaxsizeupperlimit",
     "filemaxcountlowerlimit",  "filemaxcountupperlimit",
            "pageslowerlimit",         "pagesupperlimit",
       "pagefactorlowerlimit",    "pagefactorupperlimit" };

SpecProperty AclHelper::getSpecProperty(const std::string& str) {
    for (int i=0; i< SPECPROPSIZE; ++i) {
        if (str.compare(specPropertyNames[i]) == 0)
            return SpecProperty(i);
    }
    // Allow old names in ACL file as aliases for newly-named properties
    if (str.compare("maxqueuesize") == 0)
        return SPECPROP_MAXQUEUESIZEUPPERLIMIT;
    if (str.compare("maxqueuecount") == 0)
        return SPECPROP_MAXQUEUECOUNTUPPERLIMIT;
    throw qpid::Exception("Acl illegal spec property name: " + str);
}

const std::string& AclHelper::getPropertyStr(const SpecProperty p) {
    return specPropertyNames[p];
}

// AclResult
const std::string resultNames[RESULTSIZE] = {
    "allow", "allow-log", "deny", "deny-log" };

AclResult AclHelper::getAclResult(const std::string& str) {
    for (int i=0; i< RESULTSIZE; ++i) {
        if (str.compare(resultNames[i]) == 0)
            return AclResult(i);
    }
    throw qpid::Exception("Acl illegal result name: " + str);
}

const std::string& AclHelper::getAclResultStr(const AclResult r) {
    return resultNames[r];
}

bool AclHelper::resultAllows(const AclResult r) {
    bool answer = r == ALLOW || r == ALLOWLOG;
    return answer;
}

}} // namespace qpid::acl
