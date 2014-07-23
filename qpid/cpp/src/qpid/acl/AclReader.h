#ifndef QPID_ACL_ACLREADER_H
#define QPID_ACL_ACLREADER_H


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

#include <boost/shared_ptr.hpp>
#include <map>
#include <set>
#include <string>
#include <vector>
#include <sstream>
#include <memory>
#include "qpid/acl/AclData.h"
#include "qpid/acl/Acl.h"
#include "qpid/broker/AclModule.h"
#include "qpid/acl/AclValidator.h"

namespace qpid {
namespace acl {

class AclReader {
    typedef std::set<std::string>               nameSet;
    typedef nameSet::const_iterator             nsCitr;
    typedef boost::shared_ptr<nameSet>          nameSetPtr;

    typedef std::pair<std::string, nameSetPtr>  groupPair;
    typedef std::map<std::string, nameSetPtr>   groupMap;
    typedef groupMap::const_iterator            gmCitr;
    typedef std::pair<gmCitr, bool>             gmRes;

    typedef std::pair<SpecProperty, std::string> propNvPair;
    typedef std::map<SpecProperty, std::string>  propMap;
    typedef propMap::const_iterator              pmCitr;

    //
    // aclRule
    //
    // A temporary rule created during ACL file processing.
    //
    class aclRule {
      public:
        enum objectStatus {NONE, VALUE, ALL};

        AclResult       res;
        nameSet         names;
        bool            actionAll; // True if action is set to keyword "all"
        Action          action; // Ignored if action is set to keyword "all"
        objectStatus    objStatus;
        ObjectType      object; // Ignored for all status values except VALUE
        propMap         props;
      public:
        aclRule(const AclResult r, const std::string n, const groupMap& groups); // action = "all"
        aclRule(const AclResult r, const std::string n, const groupMap& groups, const Action a);
        void setObjectType(const ObjectType o);
        void setObjectTypeAll();
        bool addProperty(const SpecProperty p, const std::string v);
        std::string toString(); // debug aid
      private:
        void processName(const std::string& name, const groupMap& groups);
    };
    typedef boost::shared_ptr<AclData::quotaRuleSet> aclQuotaRuleSet;
    typedef boost::shared_ptr<aclRule>               aclRulePtr;
    typedef std::vector<aclRulePtr>                  ruleList;
    typedef ruleList::const_iterator                 rlCitr;

    typedef std::vector<std::string>                 tokList;
    typedef tokList::const_iterator                  tlCitr;

    typedef std::set<std::string>                    keywordSet;
    typedef keywordSet::const_iterator               ksCitr;
    typedef std::pair<std::string, std::string>      nvPair; // Name-Value pair

    typedef boost::shared_ptr<std::vector<acl::AclBWHostRule> >      aclGlobalHostRuleSet;
    typedef boost::shared_ptr<std::map<std::string, std::vector<acl::AclBWHostRule> > > aclUserHostRuleSet;

    std::string             fileName;
    int                     lineNumber;
    bool                    contFlag;
    std::string             groupName;
    nameSet                 names;
    groupMap                groups;
    ruleList                rules;
    AclValidator            validator;
    std::ostringstream      errorStream;

  public:
    AclReader(uint16_t cliMaxConnPerUser, uint16_t cliMaxQueuesPerUser);
    virtual ~AclReader();
    int read(const std::string& fn, boost::shared_ptr<AclData> d); // return=0 for success
    std::string getError();

  private:
    bool processLine(char* line);
    void loadDecisionData(boost::shared_ptr<AclData> d);
    int tokenize(char* line, tokList& toks);

    bool processGroupLine(tokList& toks, const bool cont);
    gmCitr addGroup(const std::string& groupName);
    void addName(const std::string& name, nameSetPtr groupNameSet);
    void addName(const std::string& name);
    void printNames() const; // debug aid
    int  printNamesFieldWidth() const;

    bool processAclLine(tokList& toks);
    void printRules() const; // debug aid
    void printConnectionRules(const std::string name, const AclData::bwHostRuleSet& rules) const;
    void printGlobalConnectRules() const;
    void printUserConnectRules() const;
    bool isValidUserName(const std::string& name);

    bool processQuotaLine(tokList& toks);
    bool processQuotaLine(tokList& toks, const std::string theNoun, uint16_t maxSpec, aclQuotaRuleSet theRules);
    bool processQuotaGroup(const std::string&, uint16_t, aclQuotaRuleSet theRules);
    void printQuotas(const std::string theNoun, aclQuotaRuleSet theRules) const;

    static bool isValidGroupName(const std::string& name);
    static nvPair splitNameValuePair(const std::string& nvpString);

    const uint16_t cliMaxConnPerUser;
    bool connQuotaRulesExist;
    aclQuotaRuleSet connQuota;

    const uint16_t cliMaxQueuesPerUser;
    bool queueQuotaRulesExist;
    aclQuotaRuleSet queueQuota;

    aclGlobalHostRuleSet  globalHostRules;
    aclUserHostRuleSet    userHostRules;
};

}} // namespace qpid::acl

#endif // QPID_ACL_ACLREADER_H
