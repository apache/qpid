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

    //
    // constructor
    //
    AclData::AclData():
        decisionMode(qpid::acl::DENY),
        transferAcl(false),
        aclSource("UNKNOWN")
    {
        for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++)
        {
            actionList[cnt]=0;
        }
    }


    //
    // clear
    //
    void AclData::clear ()
    {
        for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++)
        {
            if (actionList[cnt])
            {
                for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++)
                    delete actionList[cnt][cnt1];
            }
            delete[] actionList[cnt];
        }
    }


    //
    // matchProp
    //
    // Compare a rule's property name with a lookup name,
    // The rule's name may contains a trailing '*' to specify a wildcard match.
    //
    bool AclData::matchProp(const std::string& ruleStr,
                            const std::string& lookupStr)
    {
        // allow wildcard on the end of rule strings...
        if (ruleStr.data()[ruleStr.size()-1]=='*')
        {
            return ruleStr.compare(0,
                                   ruleStr.size()-1,
                                   lookupStr,
                                   0,
                                   ruleStr.size()-1  ) == 0;
        }
        else
        {
            return ruleStr.compare(lookupStr) == 0;
        }
    }


    //
    // lookup
    //
    // The ACL main business logic function of matching rules and declaring
    // an allow or deny result.
    //
    AclResult AclData::lookup(
        const std::string&               id,
        const Action&                    action,
        const ObjectType&                objType,
        const std::string&               name,
        std::map<Property, std::string>* params)
    {
        QPID_LOG(debug, "ACL: Lookup for id:" << id
                    << " action:" << AclHelper::getActionStr((Action) action)
                    << " objectType:" << AclHelper::getObjectTypeStr((ObjectType) objType)
                    << " name:" << name
                    << " with params " << AclHelper::propertyMapToString(params));

        // A typical log looks like:
        // ACL: Lookup for id:bob@QPID action:create objectType:queue name:q2
        //  with params { durable=false passive=false autodelete=false
        //  exclusive=false alternate= policytype= maxqueuesize=0
        //  maxqueuecount=0 }

        // Default result is blanket decision mode for the entire ACL list.
        AclResult aclresult = decisionMode;

        // Test for lists of rules at the intersection of the Action & Object
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

                    // Iterate this rule's properties. A 'match' is true when
                    // all of the rule's properties are found to be satisfied
                    // in the lookup param list. The lookup may specify things
                    // (they usually do) that are not in the rule properties but
                    // these things don't interfere with the rule match.

                    for (specPropertyMapItr rulePropMapItr  = rsItr->props.begin();
                                           (rulePropMapItr != rsItr->props.end()) && match;
                                            rulePropMapItr++)
                    {
                        // The rule property map's NAME property is given in
                        // the calling args and not in the param map.
                        if (rulePropMapItr->first == acl::SPECPROP_NAME)
                        {
                            if (matchProp(rulePropMapItr->second, name))
                            {
                                QPID_LOG(debug, "ACL: lookup name '" << name
                                    << "' matched with rule name '"
                                    << rulePropMapItr->second << "'");
                            }
                            else
                            {
                                match = false;
                                QPID_LOG(debug, "ACL: lookup name '" << name
                                    << "' didn't match with rule name '"
                                    << rulePropMapItr->second << "'");
                            }
                        }
                        else
                        {
                            if (params)
                            {
                                // The rule's property map non-NAME properties
                                //  found in the lookup's params list.
                                // In some cases the param's index is not the same
                                //  as rule's index.
                                propertyMapItr lookupParamItr;
                                switch (rulePropMapItr->first)
                                {
                                case acl::SPECPROP_MAXQUEUECOUNTUPPERLIMIT:
                                case acl::SPECPROP_MAXQUEUECOUNTLOWERLIMIT:
                                    lookupParamItr = params->find(PROP_MAXQUEUECOUNT);
                                    break;

                                case acl::SPECPROP_MAXQUEUESIZEUPPERLIMIT:
                                case acl::SPECPROP_MAXQUEUESIZELOWERLIMIT:
                                    lookupParamItr = params->find(PROP_MAXQUEUESIZE);
                                    break;

                                default:
                                    lookupParamItr = params->find((Property)rulePropMapItr->first);
                                    break;
                                };

                                if (lookupParamItr == params->end())
                                {
                                    // Now the rule has a specified property
                                    // that does not exist in the caller's
                                    // lookup params list.
                                    // This rule does not match.
                                    match = false;
                                    QPID_LOG(debug, "ACL: lookup parameter map doesn't contain the rule property '"
                                        << AclHelper::getPropertyStr(rulePropMapItr->first) << "'");
                                }
                                else
                                {
                                    // Now account for the business of rules
                                    // whose property indexes are mismatched.
                                    switch (rulePropMapItr->first)
                                    {
                                    case acl::SPECPROP_MAXQUEUECOUNTUPPERLIMIT:
                                    case acl::SPECPROP_MAXQUEUESIZEUPPERLIMIT:
                                        limitChecked &=
                                            compareIntMax(
                                                rulePropMapItr->first,
                                                boost::lexical_cast<std::string>(rulePropMapItr->second),
                                                boost::lexical_cast<std::string>(lookupParamItr->second));
                                        break;

                                    case acl::SPECPROP_MAXQUEUECOUNTLOWERLIMIT:
                                    case acl::SPECPROP_MAXQUEUESIZELOWERLIMIT:
                                        limitChecked &=
                                            compareIntMin(
                                                rulePropMapItr->first,
                                                boost::lexical_cast<std::string>(rulePropMapItr->second),
                                                boost::lexical_cast<std::string>(lookupParamItr->second));
                                        break;

                                    default:
                                        if (matchProp(rulePropMapItr->second, lookupParamItr->second))
                                        {
                                            QPID_LOG(debug, "ACL: the pair("
                                                << AclHelper::getPropertyStr(lookupParamItr->first)
                                                << "," << lookupParamItr->second
                                                << ") given in lookup matched the pair("
                                                << AclHelper::getPropertyStr(rulePropMapItr->first) << ","
                                                << rulePropMapItr->second
                                                << ") given in the rule");
                                        }
                                        else
                                        {
                                            match = false;
                                            QPID_LOG(debug, "ACL: the pair("
                                                << AclHelper::getPropertyStr(lookupParamItr->first)
                                                << "," << lookupParamItr->second
                                                << ") given in lookup doesn't match the pair("
                                                << AclHelper::getPropertyStr(rulePropMapItr->first)
                                                << "," << rulePropMapItr->second
                                                << ") given in the rule");
                                        }
                                        break;
                                    };
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
                            // Now a lookup matched all rule properties but one
                            //  of the numeric limit checks has failed.
                            // Demote allow rules to corresponding deny rules.
                            switch (aclresult)
                            {
                            case acl::ALLOW:
                                aclresult = acl::DENY;
                                break;
                            case acl::ALLOWLOG:
                                aclresult = acl::DENYLOG;
                                break;
                            default:
                                break;
                            };
                        }
                        QPID_LOG(debug,"ACL: Successful match, the decision is:"
                            << AclHelper::getAclResultStr(aclresult));
                        return aclresult;
                    }
                    else
                    {
                        // This rule did not match the requested lookup and
                        // does not contribute to an ACL decision.
                    }
                }
            }
            else
            {
                // The Action-Object list has entries but not for this actorId
                // nor for *.
            }
        }
        else
        {
            // The Action-Object list has no entries.
        }

        QPID_LOG(debug,"ACL: No successful match, defaulting to the decision mode "
            << AclHelper::getAclResultStr(aclresult));
        return aclresult;
    }


    //
    // lookup
    //
    // The ACL main business logic function of matching rules and declaring
    // an allow or deny result.
    //
    AclResult AclData::lookup(
        const std::string&              id,
        const Action&                   action,
        const ObjectType&               objType,
        const std::string& /*Exchange*/ name,
        const std::string&              RoutingKey)
    {

        QPID_LOG(debug, "ACL: Lookup for id:" << id
            << " action:" << AclHelper::getActionStr((Action) action)
            << " objectType:" << AclHelper::getObjectTypeStr((ObjectType) objType)
            << " exchange name:" << name
            << " with routing key " << RoutingKey);

        AclResult aclresult = decisionMode;

        if (actionList[action] && actionList[action][objType]){
            AclData::actObjItr itrRule = actionList[action][objType]->find(id);

            if (itrRule == actionList[action][objType]->end())
                itrRule = actionList[action][objType]->find("*");

            if (itrRule != actionList[action][objType]->end() )
            {
                //loop the vector
                ruleSetItr rsItr = itrRule->second.end();
                for (int cnt = itrRule->second.size(); cnt != 0; cnt--)
                {
                    rsItr--;

                    // loop the names looking for match
                    bool match =true;
                    for (specPropertyMapItr pMItr  = rsItr->props.begin();
                                           (pMItr != rsItr->props.end()) && match;
                                            pMItr++)
                    {
                        //match name is exists first
                        switch (pMItr->first)
                        {
                        case acl::SPECPROP_NAME:
                            if (matchProp(pMItr->second, name))
                            {
                                QPID_LOG(debug, "ACL: lookup exchange name '"
                                    << name << "' matched with rule name '"
                                    << pMItr->second << "'");

                            }
                            else
                            {
                                match= false;
                                QPID_LOG(debug, "ACL: lookup exchange name '"
                                    << name << "' did not match with rule name '"
                                    << pMItr->second << "'");
                            }
                            break;

                        case acl::SPECPROP_ROUTINGKEY:
                            if (matchProp(pMItr->second, RoutingKey))
                            {
                                QPID_LOG(debug, "ACL: lookup key name '"
                                    << name << "' matched with rule routing key '"
                                    << pMItr->second << "'");
                            }
                            else
                            {
                                match= false;
                                QPID_LOG(debug, "ACL: lookup key name '"
                                    << name << "' did not match with rule routing key '"
                                    << pMItr->second << "'");
                            }
                            break;

                        default:
                            // Don't care
                            break;
                        };
                    }
                    if (match){
                        aclresult = rsItr->ruleMode;
                        QPID_LOG(debug,"ACL: Successful match, the decision is:"
                            << AclHelper::getAclResultStr(aclresult));
                        return aclresult;
                    }
                }
            }
        }
        QPID_LOG(debug,"ACL: No successful match, defaulting to the decision mode "
            << AclHelper::getAclResultStr(aclresult));
        return aclresult;

    }


    //
    //
    //
    AclData::~AclData()
    {
        clear();
    }


    //
    // Limit check a MAX int limit
    //
    bool AclData::compareIntMax(const qpid::acl::SpecProperty theProperty,
                                const std::string             theAclValue,
                                const std::string             theLookupValue)
    {
        uint64_t aclMax   (0);
        uint64_t paramMax (0);

        try
        {
            aclMax = boost::lexical_cast<uint64_t>(theAclValue);
        }
        catch(const boost::bad_lexical_cast&)
        {
            assert (false);
            return false;
        }

        try
        {
            paramMax = boost::lexical_cast<uint64_t>(theLookupValue);
        }
        catch(const boost::bad_lexical_cast&)
        {
            QPID_LOG(error,"ACL: Error evaluating rule. "
                << "Illegal value given in lookup for property '"
                << AclHelper::getPropertyStr(theProperty)
                << "' : " << theLookupValue);
            return false;
        }

        QPID_LOG(debug, "ACL: Numeric greater-than comparison for property "
            << AclHelper::getPropertyStr(theProperty)
            << " (value given in lookup = " << theLookupValue
            << ", value give in rule = " << theAclValue << " )");

        if (( aclMax ) && ( paramMax == 0 || paramMax > aclMax))
        {
            QPID_LOG(debug, "ACL: Max limit exceeded for property '"
                << AclHelper::getPropertyStr(theProperty) << "'");
            return false;
        }

        return true;
    }


    //
    // limit check a MIN int limit
    //
    bool AclData::compareIntMin(const qpid::acl::SpecProperty theProperty,
                                const std::string             theAclValue,
                                const std::string             theLookupValue)
    {
        uint64_t aclMin   (0);
        uint64_t paramMin (0);

        try
        {
            aclMin = boost::lexical_cast<uint64_t>(theAclValue);
        }
        catch(const boost::bad_lexical_cast&)
        {
            assert (false);
            return false;
        }

        try
        {
            paramMin = boost::lexical_cast<uint64_t>(theLookupValue);
        }
        catch(const boost::bad_lexical_cast&)
        {
            QPID_LOG(error,"ACL: Error evaluating rule. "
                << "Illegal value given in lookup for property '"
                << AclHelper::getPropertyStr(theProperty)
                << "' : " << theLookupValue);
            return false;
        }

        QPID_LOG(debug, "ACL: Numeric less-than comparison for property "
            << AclHelper::getPropertyStr(theProperty)
            << " (value given in lookup = " << theLookupValue
            << ", value give in rule = " << theAclValue << " )");

        if (( aclMin ) && ( paramMin == 0 || paramMin < aclMin))
        {
            QPID_LOG(debug, "ACL: Min limit exceeded for property '"
                << AclHelper::getPropertyStr(theProperty) << "'");
            return false;
        }

        return true;
    }

}}
