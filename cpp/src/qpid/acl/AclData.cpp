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
#include "qpid/acl/AclValidator.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/lexical_cast.hpp>
#include <boost/shared_ptr.hpp>
#include <sstream>
#include <iomanip>

namespace qpid {
namespace acl {

//
// Instantiate the keyword strings
//
const std::string AclData::ACL_KEYWORD_USER_SUBST        = "${user}";
const std::string AclData::ACL_KEYWORD_DOMAIN_SUBST      = "${domain}";
const std::string AclData::ACL_KEYWORD_USERDOMAIN_SUBST  = "${userdomain}";
const std::string AclData::ACL_KEYWORD_ALL               = "all";
const std::string AclData::ACL_KEYWORD_ACL               = "acl";
const std::string AclData::ACL_KEYWORD_GROUP             = "group";
const std::string AclData::ACL_KEYWORD_QUOTA             = "quota";
const std::string AclData::ACL_KEYWORD_QUOTA_CONNECTIONS = "connections";
const std::string AclData::ACL_KEYWORD_QUOTA_QUEUES      = "queues";
const char        AclData::ACL_SYMBOL_WILDCARD           = '*';
const std::string AclData::ACL_KEYWORD_WILDCARD          = "*";
const char        AclData::ACL_SYMBOL_LINE_CONTINUATION  = '\\';
const std::string AclData::ACL_KEYWORD_DEFAULT_EXCHANGE  = "amq.default";

//
// constructor
//
AclData::AclData():
    decisionMode(qpid::acl::DENY),
    transferAcl(false),
    aclSource("UNKNOWN"),
    connectionDecisionMode(qpid::acl::ALLOW),
    connQuotaRuleSettings(new quotaRuleSet),
    queueQuotaRuleSettings(new quotaRuleSet),
    connBWHostsGlobalRules(new bwHostRuleSet),
    connBWHostsUserRules(new bwHostUserRuleMap)
{
    for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++) {
        actionList[cnt]=0;
    }
}


//
// clear
//
void AclData::clear ()
{
    for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++) {
        if (actionList[cnt]) {
            for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++) {
                delete actionList[cnt][cnt1];
            }
        }
        delete[] actionList[cnt];
    }
    transferAcl = false;
    connectionDecisionMode = qpid::acl::ALLOW;
    connQuotaRuleSettings->clear();
    queueQuotaRuleSettings->clear();
    connBWHostsGlobalRules->clear();
    connBWHostsUserRules->clear();
}

void AclData::printDecisionRules(int userFieldWidth) {
    AclValidator validator;
    QPID_LOG(trace, "ACL: Decision rule cross reference");
    for (int act=0; act<acl::ACTIONSIZE; act++) {
        acl::Action action = acl::Action(act);
        for (int obj=0; obj<acl::OBJECTSIZE; obj++) {
            acl::ObjectType object = acl::ObjectType(obj);
            if (actionList[act] != NULL && actionList[act][obj] != NULL) {
                for (actObjItr aoitr = actionList[act][obj]->begin();
                    aoitr != actionList[act][obj]->end();
                    aoitr++) {
                    std::string user = (*aoitr).first;
                    ruleSetItr rsitr = (*aoitr).second.end();
                    for (size_t rCnt=0; rCnt < (*aoitr).second.size(); rCnt++) {
                        rsitr--;
                        std::vector<int> candidates;
                        validator.findPossibleLookupMatch(
                            action, object, rsitr->props, candidates);
                        std::stringstream ss;
                        std::string sep("");
                        for (std::vector<int>::const_iterator
                            itr = candidates.begin(); itr != candidates.end(); itr++) {
                            ss << sep << *itr;
                            sep = ",";
                        }
                        QPID_LOG(trace, "ACL: User: "
                            << std::setfill(' ') << std::setw(userFieldWidth +1) << std::left
                            << user << " "
                            << std::setfill(' ') << std::setw(acl::ACTION_STR_WIDTH +1) << std::left
                            << AclHelper::getActionStr(action)
                            << std::setfill(' ') << std::setw(acl::OBJECTTYPE_STR_WIDTH) << std::left
                            << AclHelper::getObjectTypeStr(object)
                            << " Rule: "
                            << rsitr->toString() << " may match Lookups : ("
                            << ss.str() << ")");
                    }
                }
            } else {
                // no rules for action/object
            }
        }
    }
}

//
// matchProp
//
// Compare a rule's property name with a lookup name,
// The rule's name may contain a trailing '*' to specify a wildcard match.
//
bool AclData::matchProp(const std::string& ruleStr,
                        const std::string& lookupStr)
{
    // allow wildcard on the end of rule strings...
    if (ruleStr.data()[ruleStr.size()-1]==ACL_SYMBOL_WILDCARD) {
        return ruleStr.compare(0,
                               ruleStr.size()-1,
                               lookupStr,
                               0,
                               ruleStr.size()-1  ) == 0;
    } else {
        return ruleStr.compare(lookupStr) == 0;
    }
}


//
// lookupMatchRule
//
// Check a single rule and if it's a match return the decision
//
bool AclData::lookupMatchRule(
    const ruleSetItr&                rsItr,
    const std::string&               id,
    const std::string&               name,
    const std::map<Property, std::string>* params,
    AclResult&                       aclresult)
{
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
                            rulePropMapItr++) {
        // The rule property map's NAME property is given in
        // the calling args and not in the param map.
        if (rulePropMapItr->first == acl::SPECPROP_NAME)
        {
            // substitute user name into object name
            bool result;
            if (rsItr->ruleHasUserSub[PROP_NAME]) {
                std::string sName(rulePropMapItr->second);
                substituteUserId(sName, id);
                result = matchProp(sName, name);
            } else {
                result = matchProp(rulePropMapItr->second, name);
            }

            if (result) {
                QPID_LOG(debug, "ACL: lookup name '" << name
                            << "' matched with rule name '"
                            << rulePropMapItr->second << "'");
            } else {
                match = false;
                QPID_LOG(debug, "ACL: lookup name '" << name
                            << "' didn't match with rule name '"
                            << rulePropMapItr->second << "'");
            }
        } else {
            if (params) {
                // The rule's property map non-NAME properties
                //  found in the lookup's params list.
                // In some cases the param's index is not the same
                //  as rule's index.
                propertyMapItr lookupParamItr;
                switch (rulePropMapItr->first) {
                case acl::SPECPROP_MAXPAGESLOWERLIMIT:
                case acl::SPECPROP_MAXPAGESUPPERLIMIT:
                    lookupParamItr = params->find(PROP_MAXPAGES);
                    break;

                case acl::SPECPROP_MAXPAGEFACTORLOWERLIMIT:
                case acl::SPECPROP_MAXPAGEFACTORUPPERLIMIT:
                    lookupParamItr = params->find(PROP_MAXPAGEFACTOR);
                    break;

                case acl::SPECPROP_MAXQUEUECOUNTUPPERLIMIT:
                case acl::SPECPROP_MAXQUEUECOUNTLOWERLIMIT:
                    lookupParamItr = params->find(PROP_MAXQUEUECOUNT);
                    break;

                case acl::SPECPROP_MAXQUEUESIZEUPPERLIMIT:
                case acl::SPECPROP_MAXQUEUESIZELOWERLIMIT:
                    lookupParamItr = params->find(PROP_MAXQUEUESIZE);
                    break;

                case acl::SPECPROP_MAXFILECOUNTUPPERLIMIT:
                case acl::SPECPROP_MAXFILECOUNTLOWERLIMIT:
                    lookupParamItr = params->find(PROP_MAXFILECOUNT);
                    break;

                case acl::SPECPROP_MAXFILESIZEUPPERLIMIT:
                case acl::SPECPROP_MAXFILESIZELOWERLIMIT:
                    lookupParamItr = params->find(PROP_MAXFILESIZE);
                    break;

                default:
                    lookupParamItr = params->find((Property)rulePropMapItr->first);
                    break;
                };

                if (lookupParamItr == params->end()) {
                    // Now the rule has a specified property
                    // that does not exist in the caller's
                    // lookup params list.
                    // This rule does not match.
                    match = false;
                    QPID_LOG(debug, "ACL: lookup parameter map doesn't contain the rule property '"
                                << AclHelper::getPropertyStr(rulePropMapItr->first) << "'");
                } else {
                    // Now account for the business of rules
                    // whose property indexes are mismatched.
                    switch (rulePropMapItr->first) {
                    case acl::SPECPROP_MAXQUEUECOUNTUPPERLIMIT:
                    case acl::SPECPROP_MAXQUEUESIZEUPPERLIMIT:
                    case acl::SPECPROP_MAXFILECOUNTUPPERLIMIT:
                    case acl::SPECPROP_MAXFILESIZEUPPERLIMIT:
                    case acl::SPECPROP_MAXPAGESUPPERLIMIT:
                    case acl::SPECPROP_MAXPAGEFACTORUPPERLIMIT:
                        limitChecked &=
                            compareInt(
                                rulePropMapItr->first,
                                boost::lexical_cast<std::string>(rulePropMapItr->second),
                                boost::lexical_cast<std::string>(lookupParamItr->second),
                                true);
                        break;

                    case acl::SPECPROP_MAXQUEUECOUNTLOWERLIMIT:
                    case acl::SPECPROP_MAXQUEUESIZELOWERLIMIT:
                    case acl::SPECPROP_MAXFILECOUNTLOWERLIMIT:
                    case acl::SPECPROP_MAXFILESIZELOWERLIMIT:
                    case acl::SPECPROP_MAXPAGESLOWERLIMIT:
                    case acl::SPECPROP_MAXPAGEFACTORLOWERLIMIT:
                        limitChecked &=
                            compareInt(
                                rulePropMapItr->first,
                                boost::lexical_cast<std::string>(rulePropMapItr->second),
                                boost::lexical_cast<std::string>(lookupParamItr->second),
                                false);
                        break;

                    default:
                        bool result;
                        if ((SPECPROP_ALTERNATE  == rulePropMapItr->first && rsItr->ruleHasUserSub[PROP_ALTERNATE])  ||
                            (SPECPROP_QUEUENAME  == rulePropMapItr->first && rsItr->ruleHasUserSub[PROP_QUEUENAME])) {
                            // These properties are allowed to have username substitution
                            std::string sName(rulePropMapItr->second);
                            substituteUserId(sName, id);
                            result = matchProp(sName, lookupParamItr->second);
                        } else if (SPECPROP_ROUTINGKEY == rulePropMapItr->first) {
                            // Routing key is allowed to have username substitution
                            // and it gets topic exchange matching
                            if (rsItr->ruleHasUserSub[PROP_ROUTINGKEY]) {
                                std::string sKey(lookupParamItr->second);
                                substituteKeywords(sKey, id);
                                result = rsItr->matchRoutingKey(sKey);
                            } else {
                                result = rsItr->matchRoutingKey(lookupParamItr->second);
                            }
                        } else {
                            // Rules without substitution
                            result = matchProp(rulePropMapItr->second, lookupParamItr->second);
                        }

                        if (result) {
                            QPID_LOG(debug, "ACL: the pair("
                                     << AclHelper::getPropertyStr(lookupParamItr->first)
                                     << "," << lookupParamItr->second
                                     << ") given in lookup matched the pair("
                                     << AclHelper::getPropertyStr(rulePropMapItr->first) << ","
                                     << rulePropMapItr->second
                                     << ") given in the rule");
                        } else {
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
            } else {
                // params don't exist.
            }
        }
    }
    if (match) {
        aclresult = rsItr->ruleMode;
        if (!limitChecked) {
            // Now a lookup matched all rule properties but one
            //  of the numeric limit checks has failed.
            // Demote allow rules to corresponding deny rules.
            switch (aclresult) {
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
    } else {
        // This rule did not match the requested lookup and
        // does not contribute to an ACL decision.
    }
    return match;
}

//
// lookup - general ACL lookup
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
    if (actionList[action] && actionList[action][objType]) {
        // Find the list of rules for this actorId
        AclData::actObjItr itrRule = actionList[action][objType]->find(id);

        // If individual actorId not found then find a rule set for '*'.
        if (itrRule == actionList[action][objType]->end()) {
            itrRule = actionList[action][objType]->find(ACL_KEYWORD_WILDCARD);
        }
        if (itrRule != actionList[action][objType]->end()) {
            // A list of rules exists for this actor/action/object tuple.
            // Iterate the rule set to search for a matching rule.
            ruleSetItr rsItr = itrRule->second.end();
            for (int cnt = itrRule->second.size(); cnt != 0; cnt--) {
                rsItr--;
                if (lookupMatchRule(rsItr, id, name, params, aclresult)) {
                    return aclresult;
                }
            }
        } else {
            // The Action-Object list has entries but not for this actorId
            // nor for *.
        }
    } else {
        // The Action-Object list has no entries.
    }

    QPID_LOG(debug,"ACL: No successful match, defaulting to the decision mode "
             << AclHelper::getAclResultStr(aclresult));
    return aclresult;
}


//
// lookupMatchPublishExchangeRule
//
// check a single publish exchange rule
//
bool AclData::lookupMatchPublishExchangeRule(
    const ruleSetItr&                rsItr,
    const std::string&               id,
    const std::string&               name,
    const std::string&               routingKey,
    AclResult&                       aclresult)
{
    QPID_LOG(debug, "ACL: checking rule " <<  rsItr->toString());

    // Search on exchange name and routing key only if specfied in rule.
    bool match =true;
    if (rsItr->pubExchNameInRule) {
        // substitute user name into object name
        bool result;

        if (rsItr->ruleHasUserSub[PROP_NAME]) {
            std::string sName(rsItr->pubExchName);
            substituteUserId(sName, id);
            result = matchProp(sName, name);
        }
        else if (rsItr->pubExchNameMatchesBlank) {
            result = name.empty();
        } else {
            result = matchProp(rsItr->pubExchName, name);
        }

        if (result) {
            QPID_LOG(debug, "ACL: Rule: " << rsItr->rawRuleNum << " lookup exchange name '"
                    << name << "' matched with rule name '"
                    << rsItr->pubExchName << "'");
        } else {
            match= false;
            QPID_LOG(debug, "ACL: Rule: " << rsItr->rawRuleNum << " lookup exchange name '"
                    << name << "' did not match with rule name '"
                    << rsItr->pubExchName << "'");
        }
    }

    if (match && rsItr->pubRoutingKeyInRule) {
        if ((routingKey.find(ACL_KEYWORD_USER_SUBST, 0)       != std::string::npos) ||
            (routingKey.find(ACL_KEYWORD_DOMAIN_SUBST, 0)     != std::string::npos) ||
            (routingKey.find(ACL_KEYWORD_USERDOMAIN_SUBST, 0) != std::string::npos)) {
            // The user is not allowed to present a routing key with the substitution key in it
            QPID_LOG(debug, "ACL: Rule: " << rsItr->rawRuleNum <<
                    " User-specified routing key has substitution wildcard:" << routingKey
                    << ". Rule match prohibited.");
            match = false;
        } else {
            bool result;
            if (rsItr->ruleHasUserSub[PROP_ROUTINGKEY]) {
                std::string sKey(routingKey);
                substituteKeywords(sKey, id);
                result = rsItr->matchRoutingKey(sKey);
            } else {
                result = rsItr->matchRoutingKey(routingKey);
            }

            if (result) {
                QPID_LOG(debug, "ACL: Rule: " << rsItr->rawRuleNum << " lookup key name '"
                        << routingKey << "' matched with rule routing key '"
                        << rsItr->pubRoutingKey << "'");
            } else {
                QPID_LOG(debug, "ACL: Rule: " << rsItr->rawRuleNum << " lookup key name '"
                        << routingKey << "' did not match with rule routing key '"
                        << rsItr->pubRoutingKey << "'");
                match = false;
            }
        }
    }

    if (match) {
        aclresult = rsItr->ruleMode;
        QPID_LOG(debug,"ACL: Rule: " << rsItr->rawRuleNum << " Successful match, the decision is:"
                << AclHelper::getAclResultStr(aclresult));
    }
    return match;
}

//
// lookup - special PUBLISH EXCHANGE lookup
//
// The ACL main business logic function of matching rules and declaring
// an allow or deny result. This lookup is the fastpath per-message
// lookup to verify if a user is allowed to publish to an exchange with
// a given key.
//
AclResult AclData::lookup(
    const std::string&              id,
    const Action&                   action,
    const ObjectType&               objType,
    const std::string& /*Exchange*/ name,
    const std::string&              routingKey)
{

    QPID_LOG(debug, "ACL: Lookup for id:" << id
             << " action:" << AclHelper::getActionStr((Action) action)
             << " objectType:" << AclHelper::getObjectTypeStr((ObjectType) objType)
             << " exchange name:" << name
             << " with routing key " << routingKey);

    AclResult aclresult = decisionMode;

    if (actionList[action] && actionList[action][objType]){
        AclData::actObjItr itrRule = actionList[action][objType]->find(id);

        if (itrRule == actionList[action][objType]->end()) {
            itrRule = actionList[action][objType]->find(ACL_KEYWORD_WILDCARD);
        }
        if (itrRule != actionList[action][objType]->end() ) {
            // Found a rule list for this user-action-object set.
            // Search the rule list for a matching rule.
            ruleSetItr rsItr = itrRule->second.end();
            for (int cnt = itrRule->second.size(); cnt != 0; cnt--) {
                rsItr--;

                if (lookupMatchPublishExchangeRule(rsItr, id, name, routingKey, aclresult)) {
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
void AclData::setConnQuotaRuleSettings (
    boost::shared_ptr<quotaRuleSet> quotaPtr)
{
    connQuotaRuleSettings = quotaPtr;
}


//
// getConnQuotaForUser
//
// Return the true or false value of connQuotaRulesExist,
//  indicating whether any kind of lookup was done or not.
//
// When lookups are performed return the result value of
//  1. The user's setting else
//  2. The 'all' user setting else
//  3. Zero
// When lookups are not performed then return a result value of Zero.
//
bool AclData::getConnQuotaForUser(const std::string& theUserName,
                                  uint16_t* theResult) const {
    if (this->enforcingConnectionQuotas()) {
        // look for this user explicitly
        quotaRuleSetItr nameItr = (*connQuotaRuleSettings).find(theUserName);
        if (nameItr != (*connQuotaRuleSettings).end()) {
            QPID_LOG(trace, "ACL: Connection quota for user " << theUserName
                     << " explicitly set to : " << (*nameItr).second);
            *theResult = (*nameItr).second;
        } else {
            // Look for the 'all' user
            nameItr = (*connQuotaRuleSettings).find(ACL_KEYWORD_ALL);
            if (nameItr != (*connQuotaRuleSettings).end()) {
                QPID_LOG(trace, "ACL: Connection quota for user " << theUserName
                         << " chosen through value for 'all' : " << (*nameItr).second);
                *theResult = (*nameItr).second;
            } else {
                // Neither userName nor "all" found.
                QPID_LOG(trace, "ACL: Connection quota for user " << theUserName
                         << " absent in quota settings. Return value : 0");
                *theResult = 0;
            }
        }
    } else {
        // Rules do not exist
        QPID_LOG(trace, "ACL: Connection quota for user " << theUserName
                 << " unavailable; quota settings are not specified. Return value : 0");
        *theResult = 0;
    }
    return this->enforcingConnectionQuotas();
}

//
//
//
void AclData::setQueueQuotaRuleSettings (
    boost::shared_ptr<quotaRuleSet> quotaPtr)
{
    queueQuotaRuleSettings = quotaPtr;
}


//
// getQueueQuotaForUser
//
// Return the true or false value of queueQuotaRulesExist,
//  indicating whether any kind of lookup was done or not.
//
// When lookups are performed return the result value of
//  1. The user's setting else
//  2. The 'all' user setting else
//  3. Zero
// When lookups are not performed then return a result value of Zero.
//
bool AclData::getQueueQuotaForUser(const std::string& theUserName,
                                   uint16_t* theResult) const {
    if (this->enforcingQueueQuotas()) {
        // look for this user explicitly
        quotaRuleSetItr nameItr = (*queueQuotaRuleSettings).find(theUserName);
        if (nameItr != (*queueQuotaRuleSettings).end()) {
            QPID_LOG(trace, "ACL: Queue quota for user " << theUserName
                     << " explicitly set to : " << (*nameItr).second);
            *theResult = (*nameItr).second;
        } else {
            // Look for the 'all' user
            nameItr = (*queueQuotaRuleSettings).find(ACL_KEYWORD_ALL);
            if (nameItr != (*queueQuotaRuleSettings).end()) {
                QPID_LOG(trace, "ACL: Queue quota for user " << theUserName
                         << " chosen through value for 'all' : " << (*nameItr).second);
                *theResult = (*nameItr).second;
            } else {
                // Neither userName nor "all" found.
                QPID_LOG(trace, "ACL: Queue quota for user " << theUserName
                         << " absent in quota settings. Return value : 0");
                *theResult = 0;
            }
        }
    } else {
        // Rules do not exist
        QPID_LOG(trace, "ACL: Queue quota for user " << theUserName
                 << " unavailable; quota settings are not specified. Return value : 0");
        *theResult = 0;
    }
    return this->enforcingQueueQuotas();
}

void AclData::setConnGlobalRules (boost::shared_ptr<bwHostRuleSet> cgr) {
    connBWHostsGlobalRules = cgr;
}

void AclData::setConnUserRules (boost::shared_ptr<bwHostUserRuleMap> hurm) {
    connBWHostsUserRules = hurm;
}

AclResult AclData::isAllowedConnection(const std::string& userName,
                                       const std::string& hostName,
                                       std::string& logText) {
    bool decisionMade(false);
    AclResult result(ALLOW);
    for (bwHostRuleSetItr it=connBWHostsGlobalRules->begin();
                          it!=connBWHostsGlobalRules->end(); it++) {
        if (it->getAclHost().match(hostName)) {
            // This host matches a global spec and controls the
            // allow/deny decision for this connection.
            result = it->getAclResult();
            logText = QPID_MSG("global rule " << it->toString()
                    << (AclHelper::resultAllows(result) ? " allows" : " denies")
                    << " connection for host " << hostName << ", user "
                    << userName);
            decisionMade = true;
            break;
        } else {
            // This rule in the global spec doesn't match and
            // does not control the allow/deny decision.
        }
    }

    // Run user black/white list check
    if (!decisionMade) {
        bwHostUserRuleMapItr itrRule = connBWHostsUserRules->find(userName);
        if (itrRule != connBWHostsUserRules->end()) {
            for (bwHostRuleSetItr it=(*itrRule).second.begin();
                 it!=(*itrRule).second.end(); it++) {
                if (it->getAclHost().match(hostName)) {
                    // This host matches a user spec and controls the
                    // allow/deny decision for this connection.
                    result = it->getAclResult();
                    logText = QPID_MSG("global rule " << it->toString()
                            << (AclHelper::resultAllows(result) ? " allows" : " denies")
                            << " connection for host " << hostName << ", user "
                            << userName);
                    decisionMade = true;
                    break;
                } else {
                    // This rule in the user's spec doesn't match and
                    // does not control the allow/deny decision.
                }
            }
        }
    }

    // Apply global connection mode
    if (!decisionMade) {
        result = connectionDecisionMode;
        logText = QPID_MSG("default connection policy "
                << (AclHelper::resultAllows(result) ? "allows" : "denies")
                << " connection for host " << hostName << ", user "
                << userName);
    }
    return result;
}

//
//
//
AclData::~AclData()
{
    clear();
}


//
// Limit check an int limit
//
bool AclData::compareInt(const qpid::acl::SpecProperty theProperty,
                         const std::string             theAclValue,
                         const std::string             theLookupValue,
                         bool                          theMaxFlag)
{
    uint64_t aclRuleValue (0);
    uint64_t lookupValue  (0);

    QPID_LOG(debug, "ACL: "
             << (theMaxFlag ? "Upper" : "Lower") << "-limit comparison for property "
             << AclHelper::getPropertyStr(theProperty)
             << ". Success if lookup(" << theLookupValue
             << ") "
             << (theMaxFlag ? "<=" : ">=") << " rule(" << theAclValue << ")");

    try {
        aclRuleValue = boost::lexical_cast<uint64_t>(theAclValue);
    }
    catch(const boost::bad_lexical_cast&) {
        assert (false);
        return false;
    }

    if (aclRuleValue == 0) {
        QPID_LOG(debug, "ACL: Comparison is always true when ACL rule value is zero");
        return true;
    }

    try {
        lookupValue = boost::lexical_cast<uint64_t>(theLookupValue);
    }
    catch(const boost::bad_lexical_cast&) {
        QPID_LOG(error,"ACL: Illegal value given in lookup for property '"
                 << AclHelper::getPropertyStr(theProperty)
                 << "' : " << theLookupValue);
        return false;
    }

    bool result =
        (theMaxFlag ? lookupValue > aclRuleValue : lookupValue < aclRuleValue);
    if ( result ) {
        QPID_LOG(debug, "ACL: Limit exceeded for property '"
                 << AclHelper::getPropertyStr(theProperty) << "'");
        return false;
    }

    return true;
}

const std::string DOMAIN_SEPARATOR("@");
const std::string PERIOD(".");
const std::string UNDERSCORE("_");
//
// substituteString
//   Given a name string from an Acl rule, substitute the replacement into it
//   wherever the placeholder directs.
//
void AclData::substituteString(std::string& targetString,
                               const std::string& placeholder,
                               const std::string& replacement)
{
    assert (!placeholder.empty());
    if (placeholder.empty()) {
        return;
    }
    size_t start_pos(0);
    while((start_pos = targetString.find(placeholder, start_pos)) != std::string::npos) {
        targetString.replace(start_pos, placeholder.length(), replacement);
        start_pos += replacement.length();
    }
}


//
// normalizeUserId
//   Given a name string return it in a form usable as topic keys:
//     change "@" and "." to "_".
//
std::string AclData::normalizeUserId(const std::string& userId)
{
    std::string normalId(userId);
    substituteString(normalId, DOMAIN_SEPARATOR, UNDERSCORE);
    substituteString(normalId, PERIOD,           UNDERSCORE);
    return normalId;
}


//
// substituteUserId
//   Given an Acl rule and an authenticated userId
//   do the keyword substitutions on the rule.
//
void AclData::substituteUserId(std::string& ruleString,
                               const std::string& userId)
{
    size_t locDomSeparator(0);
    std::string user("");
    std::string domain("");
    std::string userdomain = normalizeUserId(userId);

    locDomSeparator = userId.find(DOMAIN_SEPARATOR);
    if (std::string::npos == locDomSeparator) {
        // "@" not found. There's just a user name
        user   = normalizeUserId(userId);
    } else {
        // "@" found, split the names. Domain may be blank.
        user   = normalizeUserId(userId.substr(0,locDomSeparator));
        domain = normalizeUserId(userId.substr(locDomSeparator+1));
    }

    substituteString(ruleString, ACL_KEYWORD_USER_SUBST,       user);
    substituteString(ruleString, ACL_KEYWORD_DOMAIN_SUBST,     domain);
    substituteString(ruleString, ACL_KEYWORD_USERDOMAIN_SUBST, userdomain);
}


//
// substituteKeywords
//   Given an Acl rule and an authenticated userId
//   do reverse keyword substitutions on the rule.
//   That is, replace the normalized name in the rule string with
//   the keyword that represents it. This stragegy is used for
//   topic key lookups where the keyword string proper is in the
//   topic key search tree.
//
void AclData::substituteKeywords(std::string& ruleString,
                                 const std::string& userId)
{
    size_t locDomSeparator(0);
    std::string user("");
    std::string domain("");
    std::string userdomain = normalizeUserId(userId);

    locDomSeparator = userId.find(DOMAIN_SEPARATOR);
    if (std::string::npos == locDomSeparator) {
        // "@" not found. There's just a user name
        user   = normalizeUserId(userId);
    } else {
        // "@" found, split the names
        user   = normalizeUserId(userId.substr(0,locDomSeparator));
        domain = normalizeUserId(userId.substr(locDomSeparator+1));
    }
    std::string oRule(ruleString);
    substituteString(ruleString, userdomain, ACL_KEYWORD_USERDOMAIN_SUBST);
    substituteString(ruleString, user,       ACL_KEYWORD_USER_SUBST);
    substituteString(ruleString, domain,     ACL_KEYWORD_DOMAIN_SUBST);
}
}}
