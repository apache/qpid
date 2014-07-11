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
    "queue", "exchange", "broker", "link", "method", "query" };

ObjectType AclHelper::getObjectType(const std::string& str) {
    for (int i=0; i< OBJECTSIZE; ++i) {
        if (str.compare(objectNames[i]) == 0)
            return ObjectType(i);
    }
    throw qpid::Exception(str);
}

const std::string& AclHelper::getObjectTypeStr(const ObjectType o) {
    return objectNames[o];
}

// Action
const std::string actionNames[ACTIONSIZE] = {
    "consume", "publish", "create", "access", "bind",
    "unbind", "delete", "purge", "update", "move",
    "redirect", "reroute" };

Action AclHelper::getAction(const std::string& str) {
    for (int i=0; i< ACTIONSIZE; ++i) {
        if (str.compare(actionNames[i]) == 0)
            return Action(i);
    }
    throw qpid::Exception(str);
}

const std::string& AclHelper::getActionStr(const Action a) {
    return actionNames[a];
}

// Property
// These are shared between broker and acl using code enums.
const std::string propertyNames[PROPERTYSIZE] = {
    "name", "durable", "owner", "routingkey", "autodelete", "exclusive", "type",
    "alternate", "queuename", "exchangename", "schemapackage",
    "schemaclass", "policytype", "paging",

    "maxpages", "maxpagefactor",
    "maxqueuesize", "maxqueuecount", "maxfilesize", "maxfilecount"};

Property AclHelper::getProperty(const std::string& str) {
    for (int i=0; i< PROPERTYSIZE; ++i) {
        if (str.compare(propertyNames[i]) == 0)
            return Property(i);
    }
    throw qpid::Exception(str);
}

const std::string& AclHelper::getPropertyStr(const Property p) {
    return propertyNames[p];
}

// SpecProperty
// These are shared between user acl files and acl using text.
const std::string specPropertyNames[SPECPROPSIZE] = {
    "name", "durable", "owner", "routingkey", "autodelete", "exclusive", "type",
    "alternate", "queuename", "exchangename", "schemapackage",
    "schemaclass", "policytype", "paging",

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
    throw qpid::Exception(str);
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
    throw qpid::Exception(str);
}

const std::string& AclHelper::getAclResultStr(const AclResult r) {
    return resultNames[r];
}

// This map contains the legal combinations of object/action/properties
// found in an ACL file
void AclHelper::loadValidationMap(objectMapPtr& map) {
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

    propSetPtr p5(new propSet);
    p5->insert(PROP_QUEUENAME);

    propSetPtr p6(new propSet);
    p6->insert(PROP_EXCHANGENAME);


    actionMapPtr a1(new actionMap);
    a1->insert(actionPair(ACT_ACCESS,   p0));
    a1->insert(actionPair(ACT_CREATE,   p4));
    a1->insert(actionPair(ACT_PURGE,    p0));
    a1->insert(actionPair(ACT_DELETE,   p0));
    a1->insert(actionPair(ACT_CONSUME,  p0));
    a1->insert(actionPair(ACT_MOVE,     p5));
    a1->insert(actionPair(ACT_REDIRECT, p5));
    a1->insert(actionPair(ACT_REROUTE,  p6));

    map->insert(objectPair(OBJ_QUEUE, a1));

    // == Links ==

    actionMapPtr a2(new actionMap);
    a2->insert(actionPair(ACT_CREATE,  p0));

    map->insert(objectPair(OBJ_LINK, a2));

    // == Method ==

    propSetPtr p7(new propSet);
    p7->insert(PROP_SCHEMAPACKAGE);
    p7->insert(PROP_SCHEMACLASS);

    actionMapPtr a4(new actionMap);
    a4->insert(actionPair(ACT_ACCESS, p7));

    map->insert(objectPair(OBJ_METHOD, a4));

    // == Query ==

    propSetPtr p8(new propSet);
    p8->insert(PROP_SCHEMACLASS);

    actionMapPtr a5(new actionMap);
    a5->insert(actionPair(ACT_ACCESS, p8));

    map->insert(objectPair(OBJ_QUERY, a5));

}

}} // namespace qpid::acl
