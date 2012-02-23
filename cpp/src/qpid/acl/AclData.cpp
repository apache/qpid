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

#include "qpid/acl/AclData.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace acl {

    AclData::AclData():decisionMode(qpid::acl::DENY),transferAcl(false),aclSource("UNKNOWN")
    {
        for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){
            actionList[cnt]=0;
        }
    }

    void AclData::clear ()
    {
        for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){
            if (actionList[cnt]){
                for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++)
                    delete actionList[cnt][cnt1]; 
            }
            delete[] actionList[cnt];
        }
    }

    bool AclData::matchProp(const std::string & src, const std::string& src1)
    {
        // allow wildcard on the end of strings...
        if (src.data()[src.size()-1]=='*') {
            return (src.compare(0, src.size()-1, src1, 0,src.size()-1  ) == 0);
        } else {
            return (src.compare(src1)==0) ;
        }
    }

    AclResult AclData::lookup(
        const std::string& id,
        const Action& action,
        const ObjectType& objType,
        const std::string& name,
        std::map<Property, std::string>* params) {

        QPID_LOG(debug, "ACL: Lookup for id:" << id
                    << " action:" << AclHelper::getActionStr((Action) action)
                    << " objectType:" << AclHelper::getObjectTypeStr((ObjectType) objType)
                    << " name:" << name
                    << " with params " << AclHelper::propertyMapToString(params));

        // A typical log looks like:
        // ACL: Lookup for id:bob@QPID action:create objectType:queue name:q2 with params { durable=false
        // passive=false autodelete=false exclusive=false alternate= policytype= maxqueuesize=0 maxqueuecount=0 }
        
        // Default result is blanket decision mode for the entire ACL list.
        AclResult aclresult = decisionMode;

        // Test for lists of rules at the intersection of the Action and the Object
        if (actionList[action] && actionList[action][objType])
        {
            // Find the list of rules for this actorId
            AclData::actObjItr itrRule = actionList[action][objType]->find(id);

            // If individual actorId not found then find a rule set for '*'.
            if (itrRule == actionList[action][objType]->end())
                itrRule = actionList[action][objType]->find("*");

            if (itrRule != actionList[action][objType]->end())
            {
                // A list of rules exists for this actor/action/object tuple.
                // Iterate the rule set to search for a matching rule.
                ruleSetItr rsItr = itrRule->second.end();
                for (int cnt = itrRule->second.size(); cnt != 0; cnt--)
                {
                    rsItr--;

                    QPID_LOG(debug, "ACL: checking rule " <<  rsItr->toString());

                    bool match = true;
                    bool limitChecked = true;

                    // Iterate this rule's properties
                    for (propertyMapItr rulePropMapItr  = rsItr->props.begin();
                                       (rulePropMapItr != rsItr->props.end()) && match;
                                        rulePropMapItr++)
                    {
                        // The property map's NAME property is handled specially
                        if (rulePropMapItr->first == acl::PROP_NAME)
                        {
                            if (matchProp(rulePropMapItr->second, name))
                            {
                                QPID_LOG(debug, "ACL: lookup name '" << name
                                        << "' matched with rule name '" << rulePropMapItr->second << "'");
                            }
                            else
                            {
                                match = false;
                                QPID_LOG(debug, "ACL: lookup name '" << name
                                        << "' didn't match with rule name '" << rulePropMapItr->second << "'");
                            }
                        }
                        else
                        {
                            if (params)
                            {
                                // The rule's property map non-NAME properties are
                                // to be found in the lookup's params list
                                propertyMapItr lookupParamItr = params->find(rulePropMapItr->first);
                                if (lookupParamItr == params->end())
                                {
                                    // Now the rule has a specified property that does not exist
                                    // in the caller's lookup params list. This rule does not match.
                                    match = false;
                                    QPID_LOG(debug, "ACL: lookup parameter map doesn't contain the rule property '"
                                            << AclHelper::getPropertyStr(rulePropMapItr->first) << "'");
                                }
                                else
                                {
                                    if ( rulePropMapItr->first == acl::PROP_QUEUEMAXCOUNT || rulePropMapItr->first == acl::PROP_QUEUEMAXSIZE )
                                    {
                                        assert ( rulePropMapItr->first == lookupParamItr->first );
                                        limitChecked &= compareIntMax(rulePropMapItr->first,
                                                                    boost::lexical_cast<std::string>(rulePropMapItr->second),
                                                                    boost::lexical_cast<std::string>(lookupParamItr->second));
                                    }
                                    else
                                    {
                                        if (matchProp(rulePropMapItr->second, lookupParamItr->second))
                                        {
                                            QPID_LOG(debug, "ACL: the pair("
                                            << AclHelper::getPropertyStr(lookupParamItr->first) << "," << lookupParamItr->second
                                                << ") given in lookup matched the pair("
                                                << AclHelper::getPropertyStr(rulePropMapItr->first) << "," << rulePropMapItr->second
                                                << ") given in the rule");
                                        }
                                        else
                                        {
                                            match = false;
                                            QPID_LOG(debug, "ACL: the pair("
                                            << AclHelper::getPropertyStr(lookupParamItr->first) << "," << lookupParamItr->second
                                                << ") given in lookup doesn't match the pair("
                                                << AclHelper::getPropertyStr(rulePropMapItr->first) << "," << rulePropMapItr->second
                                                << ") given in the rule");
                                        }
                                    }
                                }
                            }
                            else
                            {
                                // params don't exist.
                            }
                        }
                    }
                    if (match)
                    {
                        aclresult = rsItr->ruleMode;
                        if (!limitChecked)
                        {
                            // Now a lookup matched all rule properties but one of the numeric
                            // limits has failed. This has the effect of demoting an allow to a deny.
                            if (aclresult == acl::ALLOW)
                            {
                                aclresult  = acl::DENY;
                            }
                            else
                            {
                                if (aclresult == acl::ALLOWLOG)
                                {
                                    aclresult  = acl::DENYLOG;
                                }
                            }
                        }
                        QPID_LOG(debug,"ACL: Successful match, the decision is:" << AclHelper::getAclResultStr(aclresult));
                        return aclresult;
                    }
                    else
                    {
                        // this rule did not match the requested lookup
                    }
                }
            }
            else
            {
                // The Action-Object list has entries but not for this actorId nor for *.
            }
        }
        else
        {
            // The Action-Object list has no entries
        }

        QPID_LOG(debug,"ACL: No successful match, defaulting to the decision mode " << AclHelper::getAclResultStr(aclresult));
        return aclresult;
    }

    AclResult AclData::lookup(const std::string& id, const Action& action, const ObjectType& objType,
                              const std::string& /*Exchange*/ name, const std::string& RoutingKey)
    {

        QPID_LOG(debug, "ACL: Lookup for id:" << id << " action:" << AclHelper::getActionStr((Action) action)
            << " objectType:" << AclHelper::getObjectTypeStr((ObjectType) objType) << " exchange name:" << name
            << " with routing key " << RoutingKey);

        AclResult aclresult = decisionMode;

        if (actionList[action] && actionList[action][objType]){
            AclData::actObjItr itrRule = actionList[action][objType]->find(id);

            if (itrRule == actionList[action][objType]->end())
                itrRule = actionList[action][objType]->find("*");

            if (itrRule != actionList[action][objType]->end() ) {

                //loop the vector
                ruleSetItr rsItr = itrRule->second.end();
                for (int cnt = itrRule->second.size(); cnt != 0; cnt--) {
                    rsItr--;

                    // loop the names looking for match
                    bool match =true;
                    for (propertyMapItr pMItr = rsItr->props.begin(); (pMItr != rsItr->props.end()) && match; pMItr++)
                    {
                        //match name is exists first
                        if (pMItr->first == acl::PROP_NAME){
                            if (matchProp(pMItr->second, name)){  							     
                                QPID_LOG(debug, "ACL: lookup exchange name '" << name << "' matched with rule name '"
                                    << pMItr->second << "'");

                            }else{
                                match= false;
                                QPID_LOG(debug, "ACL: lookup exchange name '" << name << "' didn't match with rule name '"
                                    << pMItr->second << "'");
                            }    
                        }else if (pMItr->first == acl::PROP_ROUTINGKEY){
                            if (matchProp(pMItr->second, RoutingKey)){  
                                QPID_LOG(debug, "ACL: lookup key name '" << name << "' matched with rule routing key '"
                                    << pMItr->second << "'");
                            }else{
                                match= false;
                                QPID_LOG(debug, "ACL: lookup key name '" << name << "' didn't match with routing key '"
                                    << pMItr->second << "'");
                            } 
                        }
                    }
                    if (match){
                        aclresult = rsItr->ruleMode;
                        QPID_LOG(debug,"ACL: Successful match, the decision is:" << AclHelper::getAclResultStr(aclresult));
                        return aclresult;
                    }
                }
            }
        }
        QPID_LOG(debug,"ACL: No successful match, defaulting to the decision mode " << AclHelper::getAclResultStr(aclresult));
        return aclresult;

    }


    AclData::~AclData()
    {
        clear();
    }

    bool AclData::compareIntMax(const qpid::acl::Property theProperty,
                                const std::string         theAclValue,
                                const std::string         theLookupValue) {    
        uint64_t aclMax   (0);
        uint64_t paramMax (0);
        
        try {
            aclMax = boost::lexical_cast<uint64_t>(theAclValue);
        } catch(const boost::bad_lexical_cast&) {
            assert (false);
            return false;
        }
        
        try {
            paramMax = boost::lexical_cast<uint64_t>(theLookupValue);
        } catch(const boost::bad_lexical_cast&) {
            QPID_LOG(error,"ACL: Error evaluating rule. " <<
                "Illegal value given in lookup for property '" <<
                AclHelper::getPropertyStr(theProperty) << "' : " <<
                theLookupValue);
            return false;
        }
        
        QPID_LOG(debug, "ACL: Numeric comparison for property " <<
            AclHelper::getPropertyStr(theProperty)  <<
            " (value given in lookup = " <<
            theLookupValue <<
            ", value give in rule = " <<
            theAclValue << " )");
        
        if (( aclMax ) && ( paramMax == 0 || paramMax > aclMax)){
            QPID_LOG(debug, "ACL: Max limit exceeded for property '"
                << AclHelper::getPropertyStr(theProperty) << "'");
            return false;
        }

        return true;
    }
}}
