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

#include "qpid/acl/AclReader.h"
#include "qpid/acl/AclData.h"

#include <cctype>
#include <cstring>
#include <fstream>
#include <sstream>
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"
#include <boost/lexical_cast.hpp>
#include <algorithm>

#include <iomanip> // degug
#include <iostream> // debug

#define ACL_FORMAT_ERR_LOG_PREFIX "ACL format error: " << fileName << ":" << lineNumber << ": "

namespace qpid {
namespace acl {

    AclReader::aclRule::aclRule(const AclResult r, const std::string n, const groupMap& groups) : res(r), actionAll(true), action(ACT_ACCESS), objStatus(NONE), object(OBJ_BROKER) {
        processName(n, groups);
    }
    AclReader::aclRule::aclRule(const AclResult r, const std::string n, const groupMap& groups, const Action a) : res(r), actionAll(false), action(a), objStatus(NONE) {
        processName(n, groups);
    }

    void AclReader::aclRule::setObjectType(const ObjectType o) {
        objStatus = VALUE;
        object = o;
    }

    void AclReader::aclRule::setObjectTypeAll() {
        objStatus = ALL;
    }

    bool AclReader::aclRule::addProperty(const SpecProperty p, const std::string v) {
        return props.insert(propNvPair(p, v)).second;
    }

    // Debug aid
    std::string AclReader::aclRule::toString() {
        std::ostringstream oss;
        oss << AclHelper::getAclResultStr(res) << " [";
        for (nsCitr itr = names.begin(); itr != names.end(); itr++) {
            if (itr != names.begin()) oss << ", ";
            oss << *itr;
        }
        oss << "]";
        if (actionAll) {
            oss << " *";
        } else {
            oss << " " << AclHelper::getActionStr(action);
        }
        if (objStatus == ALL) {
            oss << " *";
        } else if (objStatus == VALUE) {
            oss << " " << AclHelper::getObjectTypeStr(object);
        }
        for (pmCitr i=props.begin(); i!=props.end(); i++) {
            oss << " " << AclHelper::getPropertyStr(i->first) << "=" << i->second;
        }
        return oss.str();
    }

    void AclReader::loadDecisionData(boost::shared_ptr<AclData> d) {
        d->clear();
        QPID_LOG(debug, "ACL: Load Rules");
        bool foundmode = false;
        bool foundConnectionMode = false;

        rlCitr i = rules.end();
        for (int cnt = rules.size(); cnt; cnt--) {
            i--;
            QPID_LOG(debug, "ACL: Processing " << std::setfill(' ') << std::setw(2)
                << cnt << " " << (*i)->toString());

            if (!(*i)->actionAll && (*i)->objStatus == aclRule::VALUE &&
                !validator.validateAllowedProperties(
                    (*i)->action, (*i)->object, (*i)->props, false)) {
                    // specific object/action has bad property
                    // this rule gets ignored
                    continue;
            } else {
                // action=all or object=none/all means the rule gets propagated
                // possibly to many places.
                // Invalid rule combinations are not propagated.
            }

            if (!foundmode && (*i)->actionAll && (*i)->names.size() == 1
                && (*((*i)->names.begin())).compare(AclData::ACL_KEYWORD_WILDCARD) == 0) {
                    d->decisionMode = (*i)->res;
                    QPID_LOG(debug, "ACL: FoundMode "
                        << AclHelper::getAclResultStr(d->decisionMode));
                    foundmode = true;
            } else if ((*i)->action == acl::ACT_CREATE && (*i)->object == acl::OBJ_CONNECTION) {
                // Intercept CREATE CONNECTION rules process them into separate lists to
                // be consumed in the connection approval code path.
                propMap::const_iterator pName = (*i)->props.find(SPECPROP_NAME);
                if (pName != (*i)->props.end()) {
                    throw Exception(QPID_MSG("ACL: CREATE CONNECTION rule " << cnt << " must not have a 'name' property"));
                }
                propMap::const_iterator pHost = (*i)->props.find(SPECPROP_HOST);
                if (pHost == (*i)->props.end()) {
                    throw Exception(QPID_MSG("ACL: CREATE CONNECTION rule " << cnt << " has no 'host' property"));
                }
                // create the connection rule
                bool allUsers = (*(*i)->names.begin()).compare(AclData::ACL_KEYWORD_WILDCARD) == 0;
                bool allHosts = pHost->second.compare(AclData::ACL_KEYWORD_ALL) == 0;
                AclBWHostRule bwRule((*i)->res, (allHosts ? "" : pHost->second));

                // apply the rule globally or to user list
                if (allUsers) {
                    if (allHosts) {
                        // allow one specification of allUsers,allHosts
                        if (foundConnectionMode) {
                            throw Exception(QPID_MSG("ACL: only one CREATE CONNECTION rule for user=all and host=all allowed"));
                        }
                        foundConnectionMode = true;
                        d->connectionDecisionMode = (*i)->res;
                        QPID_LOG(trace, "ACL: Found connection mode: " << AclHelper::getAclResultStr( (*i)->res ));
                    } else {
                        // Rules for allUsers but not allHosts go into the global list
                        globalHostRules->insert( globalHostRules->begin(), bwRule );
                    }
                } else {
                    // other rules go into binned rule sets for each user
                    for (nsCitr itr  = (*i)->names.begin();
                                itr != (*i)->names.end();
                                itr++) {
                        (*userHostRules)[(*itr)].insert( (*userHostRules)[(*itr)].begin(), bwRule);
                    }
                }
            } else {
                AclData::Rule rule(cnt, (*i)->res, (*i)->props);
                // Record which properties have the user substitution string
                for (pmCitr pItr=rule.props.begin(); pItr!=rule.props.end(); pItr++) {
                    if ((pItr->second.find(AclData::ACL_KEYWORD_USER_SUBST, 0)       != std::string::npos) ||
                        (pItr->second.find(AclData::ACL_KEYWORD_DOMAIN_SUBST, 0)     != std::string::npos) ||
                        (pItr->second.find(AclData::ACL_KEYWORD_USERDOMAIN_SUBST, 0) != std::string::npos)) {
                        rule.ruleHasUserSub[pItr->first] = true;
                    }
                }

                // Find possible routingkey property and cache its pattern
                for (pmCitr pItr=rule.props.begin(); pItr!=rule.props.end(); pItr++) {
                    if (acl::SPECPROP_ROUTINGKEY == pItr->first)
                    {
                        rule.pubRoutingKeyInRule = true;
                        rule.pubRoutingKey = (std::string)pItr->second;
                        rule.addTopicTest(rule.pubRoutingKey);
                    }
                }

                // Action -> Object -> map<user -> set<Rule> >
                std::ostringstream actionstr;
                for (int acnt = ((*i)->actionAll ? 0 : (*i)->action);
                    acnt < acl::ACTIONSIZE;
                    (*i)->actionAll ? acnt++ : acnt = acl::ACTIONSIZE) {

                    if (acnt == acl::ACT_PUBLISH)
                    {
                        d->transferAcl = true; // we have transfer ACL
                        // For Publish the only object should be Exchange
                        // and the only property should be routingkey.
                        // Go through the rule properties and find the name and the key.
                        // If found then place them specially for the lookup engine.
                        for (pmCitr pItr=(*i)->props.begin(); pItr!=(*i)->props.end(); pItr++) {
                            if (acl::SPECPROP_NAME == pItr->first)
                            {
                                rule.pubExchNameInRule = true;
                                rule.pubExchName = pItr->second;
                                rule.pubExchNameMatchesBlank = rule.pubExchName.compare(AclData::ACL_KEYWORD_DEFAULT_EXCHANGE) == 0;
                            }
                        }
                    }
                    actionstr << AclHelper::getActionStr((Action) acnt) << ",";

                    //find the Action, create if not exist
                    if (d->actionList[acnt] == NULL) {
                        d->actionList[acnt] =
                            new AclData::aclAction[qpid::acl::OBJECTSIZE];
                        for (int j = 0; j < qpid::acl::OBJECTSIZE; j++)
                            d->actionList[acnt][j] = NULL;
                    }

                    for (int ocnt = ((*i)->objStatus != aclRule::VALUE ? 0
                        : (*i)->object);
                        ocnt < acl::OBJECTSIZE;
                    (*i)->objStatus != aclRule::VALUE ? ocnt++ : ocnt = acl::OBJECTSIZE) {

                        //find the Object, create if not exist
                        if (d->actionList[acnt][ocnt] == NULL)
                            d->actionList[acnt][ocnt] =
                                new AclData::actionObject;

                        // add users and Rule to object set
                        bool allNames = false;
                        // check to see if names.begin is '*'
                        if ((*(*i)->names.begin()).compare(AclData::ACL_KEYWORD_WILDCARD) == 0)
                            allNames = true;

                        for (nsCitr itr  = (allNames ? names.begin() : (*i)->names.begin());
                                    itr != (allNames ? names.end()   : (*i)->names.end());
                                    itr++) {
                            if (validator.validateAllowedProperties(acl::Action(acnt),
                                                                    acl::ObjectType(ocnt),
                                                                    (*i)->props,
                                                                    false)) {
                                AclData::actObjItr itrRule =
                                    d->actionList[acnt][ocnt]->find(*itr);

                                if (itrRule == d->actionList[acnt][ocnt]->end()) {
                                    AclData::ruleSet rSet;
                                    rSet.push_back(rule);
                                    d->actionList[acnt][ocnt]->insert
                                        (make_pair(std::string(*itr), rSet));
                                } else {
                                        itrRule->second.push_back(rule);
                                }
                            } else {
                                // Skip propagating this rule as it will never match.
                            }
                        }
                    }
                }

                std::ostringstream objstr;
                for (int ocnt = ((*i)->objStatus != aclRule::VALUE ? 0 : (*i)->object);
                         ocnt < acl::OBJECTSIZE;
                                 (*i)->objStatus != aclRule::VALUE ? ocnt++ : ocnt = acl::OBJECTSIZE) {
                        objstr << AclHelper::getObjectTypeStr((ObjectType) ocnt) << ",";
                }

                bool allNames = ((*(*i)->names.begin()).compare(AclData::ACL_KEYWORD_WILDCARD) == 0);
                std::ostringstream userstr;
                for (nsCitr itr  = (allNames ? names.begin() : (*i)->names.begin());
                            itr != (allNames ? names.end()   : (*i)->names.end());
                            itr++) {
                    userstr << *itr << ",";
                }

                QPID_LOG(debug, "ACL: Adding actions {" <<
                    actionstr.str().substr(0,actionstr.str().length()-1)
                    << "} to objects {" <<
                    objstr.str().substr(0,objstr.str().length()-1)
                    << "} with props " <<
                    AclHelper::propertyMapToString(&rule.props)
                    << " for users {" <<
                    userstr.str().substr(0,userstr.str().length()-1)
                    << "}");
            }
        }

        // connection quota
        d->setConnQuotaRuleSettings(connQuota);
        // queue quota
        d->setQueueQuotaRuleSettings(queueQuota);
        // global B/W connection rules
        d->setConnGlobalRules(globalHostRules);
        // user B/W connection rules
        d->setConnUserRules(userHostRules);
    }


    void AclReader::aclRule::processName(const std::string& name, const groupMap& groups) {
        if (name.compare(AclData::ACL_KEYWORD_ALL) == 0) {
            names.insert(AclData::ACL_KEYWORD_WILDCARD);
        } else {
            gmCitr itr = groups.find(name);
            if (itr == groups.end()) {
                names.insert(name);
            } else {
                names.insert(itr->second->begin(), itr->second->end());
            }
        }
    }

    AclReader::AclReader(uint16_t theCliMaxConnPerUser, uint16_t theCliMaxQueuesPerUser) :
        lineNumber(0), contFlag(false),
        cliMaxConnPerUser (theCliMaxConnPerUser),
        connQuotaRulesExist(false),
        connQuota(new AclData::quotaRuleSet),
        cliMaxQueuesPerUser (theCliMaxQueuesPerUser),
        queueQuotaRulesExist(false),
        queueQuota(new AclData::quotaRuleSet),
        globalHostRules(new AclData::bwHostRuleSet),
        userHostRules(new AclData::bwHostUserRuleMap) {
        names.insert(AclData::ACL_KEYWORD_WILDCARD);
    }

    AclReader::~AclReader() {}

    std::string AclReader::getError() {
        return errorStream.str();
    }

    int AclReader::read(const std::string& fn, boost::shared_ptr<AclData> d) {
        fileName = fn;
        lineNumber = 0;
        char buff[1024];
        std::ifstream ifs(fn.c_str(), std::ios_base::in);
        if (!ifs.good()) {
            errorStream << "Unable to open ACL file \"" << fn << "\": eof=" << (ifs.eof()?"T":"F") << "; fail=" << (ifs.fail()?"T":"F") << "; bad=" << (ifs.bad()?"T":"F");
            return -1;
        }
        // Propagate nonzero per-user max connection setting from CLI
        if (cliMaxConnPerUser > 0) {
            connQuotaRulesExist = true;
            (*connQuota)[AclData::ACL_KEYWORD_ALL] = cliMaxConnPerUser;
        }
        // Propagate nonzero per-user max queue setting from CLI
        if (cliMaxQueuesPerUser > 0) {
            queueQuotaRulesExist = true;
            (*queueQuota)[AclData::ACL_KEYWORD_ALL] = cliMaxQueuesPerUser;
        }
        // Loop to process the Acl file
        try {
            bool err = false;
            while (ifs.good()) {
                ifs.getline(buff, 1024);
                lineNumber++;
                if (std::strlen(buff) > 0 && buff[0] != '#') // Ignore blank lines and comments
                    err |= !processLine(buff);
            }
            if (!ifs.eof())
            {
                errorStream << "Unable to read ACL file \"" << fn << "\": eof=" << (ifs.eof()?"T":"F") << "; fail=" << (ifs.fail()?"T":"F") << "; bad=" << (ifs.bad()?"T":"F");
                ifs.close();
                return -2;
            }
            ifs.close();
            if (err) return -3;
            QPID_LOG(notice, "ACL: Read file \"" <<  fn << "\"");
        } catch (const std::exception& e) {
            errorStream << "Unable to read ACL file \"" << fn << "\": " << e.what();
            ifs.close();
            return -4;
        } catch (...) {
            errorStream << "Unable to read ACL file \"" << fn << "\": Unknown exception";
            ifs.close();
            return -5;
        }
        printNames();
        printRules();
        printQuotas(AclData::ACL_KEYWORD_QUOTA_CONNECTIONS, connQuota);
        printQuotas(AclData::ACL_KEYWORD_QUOTA_QUEUES, queueQuota);
        try {
            loadDecisionData(d);
        } catch (const std::exception& e) {
            errorStream << "Error loading decision data : " << e.what();
            return -6;
        }
        printGlobalConnectRules();
        printUserConnectRules();
        validator.tracePropertyDefs();
        d->printDecisionRules( printNamesFieldWidth() );

        return 0;
    }

    bool AclReader::processLine(char* line) {
        bool ret = false;
        std::vector<std::string> toks;

        // Check for continuation
        char* contCharPtr = std::strrchr(line, AclData::ACL_SYMBOL_LINE_CONTINUATION);
        bool cont = contCharPtr != 0;
        if (cont) *contCharPtr = 0;

        int numToks = tokenize(line, toks);

        if (cont && numToks == 0){
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line \"" << lineNumber << "\" contains an illegal extension.";
            return false;
        }

        if (numToks && (toks[0].compare(AclData::ACL_KEYWORD_GROUP) == 0 || contFlag)) {
            ret = processGroupLine(toks, cont);
        } else if (numToks && toks[0].compare(AclData::ACL_KEYWORD_ACL) == 0) {
            ret = processAclLine(toks);
        } else if (numToks && toks[0].compare(AclData::ACL_KEYWORD_QUOTA) == 0) {
            ret = processQuotaLine(toks);
        } else {
            // Check for whitespace only line, ignore these
            bool ws = true;
            for (unsigned i=0; i<std::strlen(line) && ws; i++) {
                if (!std::isspace(line[i])) ws = false;
            }
            if (ws) {
                ret = true;
            } else {
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Non-continuation line must start with \""
                    << AclData::ACL_KEYWORD_GROUP << "\", \""
                    << AclData::ACL_KEYWORD_ACL << "\". or \""
                    << AclData::ACL_KEYWORD_QUOTA << "\".";
                ret = false;
            }
        }
        contFlag = cont;
        return ret;
    }

    int  AclReader::tokenize(char* line, std::vector<std::string>& toks) {
        const char* tokChars = " \t\n\f\v\r";
        int cnt = 0;
        char* cp = std::strtok(line, tokChars);
        while (cp != 0) {
            toks.push_back(std::string(cp));
            cnt++;
            cp = std::strtok(0, tokChars);
        }
        return cnt;
    }


    // Process 'quota' rule lines
    // Return true if the line is successfully processed without errors
    bool AclReader::processQuotaLine(tokList& toks) {
        const unsigned toksSize = toks.size();
        const unsigned minimumSize = 3;
        if (toksSize < minimumSize) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Insufficient tokens for quota definition.";
            return false;
        }

        if (toks[1].compare(AclData::ACL_KEYWORD_QUOTA_CONNECTIONS) == 0) {
            if (processQuotaLine(toks, AclData::ACL_KEYWORD_QUOTA_CONNECTIONS, AclData::getConnectMaxSpec(), connQuota)) {
                // We have processed a connection quota rule
                connQuotaRulesExist = true;
                return true;
            }
        } else if (toks[1].compare(AclData::ACL_KEYWORD_QUOTA_QUEUES) == 0) {
            if (processQuotaLine(toks, AclData::ACL_KEYWORD_QUOTA_QUEUES, AclData::getConnectMaxSpec(), queueQuota)) {
                // We have processed a queue quota rule
                queueQuotaRulesExist = true;
                return true;
            }
        } else {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Quota type \"" << toks[1] << "\" unrecognized.";
            return false;
        }
        return false;
    }


    // Process quota rule lines
    // Return true if the line is successfully processed without errors
    bool AclReader::processQuotaLine(tokList& toks, const std::string theNoun, uint16_t maxSpec, aclQuotaRuleSet theRules) {
        const unsigned toksSize = toks.size();

        uint16_t nEntities(0);
        try {
        	nEntities = boost::lexical_cast<uint16_t>(toks[2]);
        } catch(const boost::bad_lexical_cast&) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", " << theNoun << " quota value \"" << toks[2]
                << "\" cannot be converted to a 16-bit unsigned integer.";
            return false;
        }

        // limit check the setting
        if (nEntities > maxSpec)
        {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", " << theNoun << " quota value \"" << toks[2]
                << "\" exceeds maximum configuration setting of "
                << maxSpec;
            return false;
        }

        // Apply the ount to all names in rule
        for (unsigned idx = 3; idx < toksSize; idx++) {
            if (groups.find(toks[idx]) == groups.end()) {
                // This is the name of an individual, not a group
                (*theRules)[toks[idx]] = nEntities;
            } else {
                if (!processQuotaGroup(toks[idx], nEntities, theRules))
                    return false;
            }
        }

        return true;
    }


    // Process quota group expansion
    // Return true if the quota is applied to all members of the group
    bool AclReader::processQuotaGroup(const std::string& theGroup, uint16_t theQuota, aclQuotaRuleSet theRules) {
        gmCitr citr = groups.find(theGroup);

        if (citr == groups.end()) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Failed to expand group \"" << theGroup << "\".";
            return false;
        }

        for (nsCitr gni=citr->second->begin(); gni!=citr->second->end(); gni++) {
            if (groups.find(*gni) == groups.end()) {
                (*theRules)[*gni] = theQuota;
            } else {
                if (!processQuotaGroup(*gni, theQuota, theRules))
                    return false;
            }
        }
        return true;
    }


    void AclReader::printQuotas(const std::string theNoun, aclQuotaRuleSet theRules) const {
        QPID_LOG(debug, "ACL: " << theNoun << " quota: " << (*theRules).size() << " rules found:");
        int cnt = 1;
        for (AclData::quotaRuleSetItr itr=(*theRules).begin();
                                      itr != (*theRules).end();
                                      ++itr,++cnt) {
            QPID_LOG(debug, "ACL: quota " << cnt << " : " << (*itr).second
                << " " << theNoun << " for " << (*itr).first)
        }
    }


    // Return true if the line is successfully processed without errors
    // If cont is true, then groupName must be set to the continuation group name
    bool AclReader::processGroupLine(tokList& toks, const bool cont) {
        const unsigned toksSize = toks.size();

        if (contFlag) {
            gmCitr citr = groups.find(groupName);
            for (unsigned i = 0; i < toksSize; i++) {
                if (isValidGroupName(toks[i])) {
                    if (toks[i] == groupName) {
                        QPID_LOG(debug, "ACL: Line: " << lineNumber
                            << ", Ignoring recursive sub-group \"" << toks[i] << "\".");
                        continue;
                    } else if (groups.find(toks[i]) == groups.end()) {
                        errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                            << ", Sub-group \"" << toks[i] << "\" not defined yet.";
                        return false;
                    }
                } else if (!isValidUserName(toks[i])) return false;
                addName(toks[i], citr->second);
            }
        } else {
            const unsigned minimumSize = (cont ? 2 : 3);
            if (toksSize < minimumSize) {
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Insufficient tokens for group definition.";
                return false;
            }
            if (!isValidGroupName(toks[1])) {
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Group name \"" << toks[1] << "\" contains illegal characters.";
                return false;
            }
            gmCitr citr = addGroup(toks[1]);
            if (citr == groups.end()) return false;
            for (unsigned i = 2; i < toksSize; i++) {
                if (isValidGroupName(toks[i])) {
                    if (toks[i] == groupName) {
                        QPID_LOG(debug, "ACL: Line: " << lineNumber
                            << ", Ignoring recursive sub-group \"" << toks[i] << "\".");
                        continue;
                    } else if (groups.find(toks[i]) == groups.end()) {
                        errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                            << ", Sub-group \"" << toks[i] << "\" not defined yet.";
                        return false;
                    }
                } else if (!isValidUserName(toks[i])) return false;
                addName(toks[i], citr->second);
            }
        }
        return true;
    }

    // Return true if sucessfully added group
    AclReader::gmCitr AclReader::addGroup(const std::string& newGroupName) {
        gmCitr citr = groups.find(newGroupName);
        if (citr != groups.end()) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Duplicate group name \"" << newGroupName << "\".";
            return groups.end();
        }
        groupPair p(newGroupName, nameSetPtr(new nameSet));
        gmRes res = groups.insert(p);
        assert(res.second);
        groupName = newGroupName;
        return res.first;
    }

    void AclReader::addName(const std::string& name, nameSetPtr groupNameSet) {
        gmCitr citr = groups.find(name);
        if (citr != groups.end()) {
            // This is a previously defined group: add all the names in that group to this group
            groupNameSet->insert(citr->second->begin(), citr->second->end());
        } else {
            // Not a known group name
            groupNameSet->insert(name);
            addName(name);
        }
    }

    void AclReader::addName(const std::string& name) {
        names.insert(name);
    }

    /**
     * Emit debug logs exposing the name lists
     */
    void AclReader::printNames() const {
        QPID_LOG(debug, "ACL: Group list: " << groups.size() << " groups found:" );
        std::string tmp("ACL: ");
        for (gmCitr i=groups.begin(); i!= groups.end(); i++) {
            tmp += "  \"";
            tmp += i->first;
            tmp +=  "\":";
            for (nsCitr j=i->second->begin(); j!=i->second->end(); j++) {
                tmp += " ";
                tmp += *j;
            }
            QPID_LOG(debug, tmp);
            tmp = "ACL: ";
        }
        QPID_LOG(debug, "ACL: name list: " << names.size() << " names found:" );
        tmp = "ACL: ";
        for (nsCitr k=names.begin(); k!=names.end(); k++) {
            tmp += " ";
            tmp += *k;
        }
        QPID_LOG(debug, tmp);
    }

    /**
     * compute the width of longest user name
     */
    int AclReader::printNamesFieldWidth() const {
        std::string::size_type max = 0;
        for (nsCitr k=names.begin(); k!=names.end(); k++) {
            max = std::max(max, (*k).length());
        }
        return max;
    }

    bool AclReader::processAclLine(tokList& toks) {
        const unsigned toksSize = toks.size();
        if (toksSize < 4) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Insufficient tokens for acl definition.";
            return false;
        }

        AclResult res;
        try {
            res = AclHelper::getAclResult(toks[1]);
        } catch (...) {
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Unknown ACL permission \"" << toks[1] << "\".";
            return false;
        }

        bool actionAllFlag = toks[3].compare(AclData::ACL_KEYWORD_ALL) == 0;
        bool userAllFlag   = toks[2].compare(AclData::ACL_KEYWORD_ALL) == 0;
        Action action;
        if (actionAllFlag) {

            if (userAllFlag && toksSize > 4) {
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Tokens found after action \"all\".";
                return false;
            }
            action = ACT_CONSUME; // dummy; compiler must initialize action for this code path
        } else {
            try {
                action = AclHelper::getAction(toks[3]);
            } catch (...) {
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Unknown action \"" << toks[3] << "\".";
                return false;
            }
        }

        // Create rule obj; then add object (if any) and properties (if any)
        aclRulePtr rule;
        if (actionAllFlag) {
            rule.reset(new aclRule(res, toks[2], groups));
        } else {
            rule.reset(new aclRule(res, toks[2], groups, action));
        }

        if (toksSize >= 5) { // object name-value pair
            if (toks[4].compare(AclData::ACL_KEYWORD_ALL) == 0) {
                rule->setObjectTypeAll();
            } else {
                try {
                    rule->setObjectType(AclHelper::getObjectType(toks[4]));
                } catch (...) {
                    errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                        << ", Unknown object \"" << toks[4] << "\".";
                    return false;
                }
            }
        }

        if (toksSize >= 6) { // property name-value pair(s)
            for (unsigned i=5; i<toksSize; i++) {
                nvPair propNvp = splitNameValuePair(toks[i]);
                if (propNvp.second.size() == 0) {
                    errorStream << ACL_FORMAT_ERR_LOG_PREFIX <<  "Line : " << lineNumber
                        <<", Badly formed property name-value pair \""
                        << propNvp.first << "\". (Must be name=value)";
                    return false;
                }
                SpecProperty prop;
                try {
                    prop = AclHelper::getSpecProperty(propNvp.first);
                } catch (...) {
                    errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                        << ", Unknown property \"" << propNvp.first << "\".";
                    return false;
                }
                rule->addProperty(prop, propNvp.second);
            }
        }
        // Check if name (toks[2]) is group; if not, add as name of individual
        if (toks[2].compare(AclData::ACL_KEYWORD_ALL) != 0) {
            if (groups.find(toks[2]) == groups.end()) {
                addName(toks[2]);
            }
        }

        rules.push_back(rule);

        return true;
    }

    // Debug aid
    void AclReader::printRules() const {
        QPID_LOG(debug, "ACL: Rule list: " << rules.size() << " ACL rules found:");
        int cnt = 1;
        for (rlCitr i=rules.begin(); i<rules.end(); i++,cnt++) {
            QPID_LOG(debug, "ACL:   " << std::setfill(' ') << std::setw(2) << cnt << " " << (*i)->toString());
            if (!(*i)->actionAll && (*i)->objStatus == aclRule::VALUE) {
                (void)validator.validateAllowedProperties((*i)->action, (*i)->object, (*i)->props, true);
            }
        }
    }

    void AclReader::printConnectionRules(const std::string name, const AclData::bwHostRuleSet& rules) const {
        QPID_LOG(debug, "ACL: " << name << " Connection Rule list : " << rules.size() << " rules found :");
        int cnt = 1;
        for (AclData::bwHostRuleSetItr i=rules.begin(); i<rules.end(); i++,cnt++) {
            QPID_LOG(debug, "ACL:   " << std::setfill(' ') << std::setw(2) << cnt << " " << i->toString());
        }
    }

    void AclReader::printGlobalConnectRules() const {
        printConnectionRules("global", *globalHostRules);
    }

    void AclReader::printUserConnectRules() const {
        QPID_LOG(debug, "ACL: User Connection Rule lists : " << userHostRules->size() << " user lists found :");
        int cnt = 1;
        for (AclData::bwHostUserRuleMapItr i=userHostRules->begin(); i!=userHostRules->end(); i++,cnt++) {
            printConnectionRules(std::string((*i).first), (*i).second);
        }
    }

    // Static function
    // Return true if the name is well-formed (ie contains legal characters)
    bool AclReader::isValidGroupName(const std::string& name) {
        for (unsigned i=0; i<name.size(); i++) {
            const char ch = name.at(i);
            if (!std::isalnum(ch) && ch != '-' && ch != '_') return false;
        }
        return true;
    }

    // Static function
    // Split name-value pair around '=' char of the form "name=value"
    AclReader::nvPair AclReader::splitNameValuePair(const std::string& nvpString) {
        std::size_t pos = nvpString.find("=");
        if (pos == std::string::npos || pos == nvpString.size() - 1) {
            return nvPair(nvpString, "");
        }
        return nvPair(nvpString.substr(0, pos), nvpString.substr(pos+1));
    }

    // Returns true if a username has the name@realm format
    bool AclReader::isValidUserName(const std::string& name){
        size_t pos = name.find('@');
        if ( pos == std::string::npos || pos == name.length() -1){
            errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                << ", Username '" << name << "' must contain a realm";
            return false;
        }
        for (unsigned i=0; i<name.size(); i++) {
            const char ch = name.at(i);
            if (!std::isalnum(ch) && ch != '-' && ch != '_' && ch != '@' && ch != '.' && ch != '/'){
                errorStream << ACL_FORMAT_ERR_LOG_PREFIX << "Line : " << lineNumber
                    << ", Username \"" << name << "\" contains illegal characters.";
                return false;
            }
        }
        return true;
    }

}} // namespace qpid::acl
