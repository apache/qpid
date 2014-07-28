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

#include "qpid/acl/AclValidator.h"
#include "qpid/acl/AclData.h"
#include "qpid/acl/AclLexer.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/StringUtils.h"
#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <numeric>
#include <sstream>
#include <iomanip>

namespace qpid {
namespace acl {

    AclValidator::IntPropertyType::IntPropertyType(int64_t i,int64_t j) : min(i), max(j){
    }

    bool AclValidator::IntPropertyType::validate(const std::string& val) {
        int64_t v;
        try
        {
            v = boost::lexical_cast<int64_t>(val);
        }catch(const boost::bad_lexical_cast&){
            return 0;
        }

        if (v < min || v >= max){
            return 0;
        }else{
            return 1;
        }
    }

    std::string AclValidator::IntPropertyType::allowedValues() {
        return "values should be between " +
            boost::lexical_cast<std::string>(min) + " and " +
            boost::lexical_cast<std::string>(max);
    }

    AclValidator::EnumPropertyType::EnumPropertyType(std::vector<std::string>& allowed): values(allowed){
    }

    bool AclValidator::EnumPropertyType::validate(const std::string& val) {
        for (std::vector<std::string>::iterator itr = values.begin(); itr != values.end(); ++itr ){
            if (val.compare(*itr) == 0){
                return 1;
            }
        }

        return 0;
    }

    std::string AclValidator::EnumPropertyType::allowedValues() {
        std::ostringstream oss;
        oss << "possible values are one of { ";
        for (std::vector<std::string>::iterator itr = values.begin(); itr != values.end(); itr++ ){
            oss << "'" << *itr << "' ";
        }
        oss << "}";
        return oss.str();
    }

    AclValidator::AclValidator() : propertyIndex(1) {
        validators.insert(Validator(acl::SPECPROP_MAXQUEUESIZELOWERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXQUEUESIZEUPPERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXQUEUECOUNTLOWERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXQUEUECOUNTUPPERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXFILESIZELOWERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXFILESIZEUPPERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXFILECOUNTLOWERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXFILECOUNTUPPERLIMIT,
                                    boost::shared_ptr<PropertyType>(
                                        new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXPAGESLOWERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXPAGESUPPERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXPAGEFACTORLOWERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        validators.insert(Validator(acl::SPECPROP_MAXPAGEFACTORUPPERLIMIT,
                          boost::shared_ptr<PropertyType>(
                            new IntPropertyType(0,std::numeric_limits<int64_t>::max()))));

        std::string policyTypes[] = {"ring", "self-destruct", "reject"};
        std::vector<std::string> v(policyTypes, policyTypes + sizeof(policyTypes) / sizeof(std::string));
        validators.insert(Validator(acl::SPECPROP_POLICYTYPE,
                          boost::shared_ptr<PropertyType>(
                            new EnumPropertyType(v))));

        // Insert allowed action/object/property sets (generated manually 20140712)
#define RP registerProperties
        RP( "Broker::getTimestampConfig",
            "User querying message timestamp setting ",
            ACT_ACCESS,  OBJ_BROKER);
        RP( "ExchangeHandlerImpl::query",
            "AMQP 0-10 protocol received 'query'     ",
            ACT_ACCESS,  OBJ_EXCHANGE, "name");
        RP( "ExchangeHandlerImpl::bound",
            "AMQP 0-10 query binding                 ",
            ACT_ACCESS,  OBJ_EXCHANGE, "name queuename routingkey");
        RP( "ExchangeHandlerImpl::declare",
            "AMQP 0-10 exchange declare              ",
            ACT_ACCESS,  OBJ_EXCHANGE, "name type alternate durable autodelete");
        RP( "Authorise::access",
            "AMQP 1.0 exchange access                ",
            ACT_ACCESS,  OBJ_EXCHANGE, "name type durable");
        RP( "Authorise::access",
            "AMQP 1.0 node resolution                ",
            ACT_ACCESS,  OBJ_EXCHANGE, "name");
        RP( "ManagementAgent::handleMethodRequest",
            "Management method request               ",
            ACT_ACCESS,  OBJ_METHOD, "name schemapackage schemaclass");
        RP( "ManagementAgent::authorizeAgentMessage",
            "Management agent method request         ",
            ACT_ACCESS,  OBJ_METHOD, "name schemapackage schemaclass");
        RP( "ManagementAgent::handleGetQuery",
            "Management agent query                  ",
            ACT_ACCESS,  OBJ_QUERY, "name schemaclass");
        RP( "Broker::queryQueue",
            "QMF 'query queue' method                ",
            ACT_ACCESS,  OBJ_QUEUE, "name");
        RP( "QueueHandlerImpl::query",
            "AMQP 0-10 query                         ",
            ACT_ACCESS,  OBJ_QUEUE, "name");
        RP( "QueueHandlerImpl::declare",
            "AMQP 0-10 queue declare                 ",
            ACT_ACCESS,  OBJ_QUEUE, "name alternate durable exclusive autodelete policytype maxqueuecount maxqueuesize");
        RP( "Authorise::access",
            "AMQP 1.0 queue access                   ",
            ACT_ACCESS,  OBJ_QUEUE, "name alternate durable exclusive autodelete policytype maxqueuecount maxqueuesize");
        RP( "Authorise::access",
            "AMQP 1.0 node resolution                ",
            ACT_ACCESS,  OBJ_QUEUE, "name");
        RP( "Broker::bind",
            "AMQP 0-10 or QMF bind request           ",
            ACT_BIND,    OBJ_EXCHANGE, "name queuename routingkey");
        RP( "Authorise::outgoing",
            "AMQP 1.0 new outgoing link from exchange",
            ACT_BIND,    OBJ_EXCHANGE, "name queuename routingkey");
        RP( "MessageHandlerImpl::subscribe",
            "AMQP 0-10 subscribe request             ",
            ACT_CONSUME, OBJ_QUEUE, "name");
        RP( "Authorise::outgoing",
            "AMQP 1.0 new outgoing link from queue   ",
            ACT_CONSUME, OBJ_QUEUE, "name");
        RP( "ConnectionHandler",
            "TCP/IP connection creation              ",
            ACT_CREATE,  OBJ_CONNECTION, "host");
        RP( "Broker::createExchange",
            "Create exchange                         ",
            ACT_CREATE,  OBJ_EXCHANGE, "name type alternate durable autodelete");
        RP( "ConnectionHandler::Handler::open",
            "Interbroker link creation               ",
            ACT_CREATE,  OBJ_LINK);
        RP( "Authorise::interlink",
            "Interbroker link creation               ",
            ACT_CREATE,  OBJ_LINK);
        RP( "Broker::createQueue",
            "Create queue                            ",
            ACT_CREATE,  OBJ_QUEUE, "name alternate durable exclusive autodelete policytype paging maxpages maxpagefactor maxqueuecount maxqueuesize maxfilecount maxfilesize");
        RP( "Broker::deleteExchange",
            "Delete exchange                         ",
            ACT_DELETE,  OBJ_EXCHANGE, "name type alternate durable");
        RP( "Broker::deleteQueue",
            "Delete queue                            ",
            ACT_DELETE,  OBJ_QUEUE, "name alternate durable exclusive autodelete policytype");
        RP( "Broker::queueMoveMessages",
            "Management 'move queue' request         ",
            ACT_MOVE,    OBJ_QUEUE, "name queuename");
        RP( "SemanticState::route",
            "AMQP 0-10 received message processing   ",
            ACT_PUBLISH, OBJ_EXCHANGE, "name routingkey");
        RP( "Authorise::incoming",
            "AMQP 1.0 establish sender link to queue ",
            ACT_PUBLISH, OBJ_EXCHANGE, "routingkey");
        RP( "Authorise::route",
            "AMQP 1.0 received message processing    ",
            ACT_PUBLISH, OBJ_EXCHANGE, "name routingkey");
        RP( "Queue::ManagementMethod",
            "Management 'purge queue' request        ",
            ACT_PURGE,   OBJ_QUEUE, "name");
        RP( "QueueHandlerImpl::purge",
            "Management 'purge queue' request        ",
            ACT_PURGE,   OBJ_QUEUE, "name");
        RP( "Broker::queueRedirect",
            "Management 'redirect queue' request     ",
            ACT_REDIRECT,OBJ_QUEUE, "name queuename");
        RP( "Queue::ManagementMethod",
            "Management 'reroute queue' request      ",
            ACT_REROUTE, OBJ_QUEUE, "name exchangename");
        RP( "Broker::unbind",
            "Management 'unbind exchange' request    ",
            ACT_UNBIND,  OBJ_EXCHANGE, "name queuename routingkey");
        RP( "Broker::setTimestampConfig",
            "User modifying message timestamp setting",
            ACT_UPDATE,  OBJ_BROKER);
    }

    AclValidator::~AclValidator(){
    }

    /* Iterate through the data model and validate the parameters. */
    void AclValidator::validate(boost::shared_ptr<AclData> d) {

        for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){

            if (d->actionList[cnt]){

                for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++){

                    if (d->actionList[cnt][cnt1]){

                        std::for_each(d->actionList[cnt][cnt1]->begin(),
                                      d->actionList[cnt][cnt1]->end(),
                                      boost::bind(&AclValidator::validateRuleSet, this, _1));
                    }
                }
            }
        }
    }

    void AclValidator::validateRuleSet(std::pair<const std::string, qpid::acl::AclData::ruleSet>& rules){
        std::for_each(rules.second.begin(),
            rules.second.end(),
            boost::bind(&AclValidator::validateRule, this, _1));
    }

    void AclValidator::validateRule(qpid::acl::AclData::Rule& rule){
        std::for_each(rule.props.begin(),
            rule.props.end(),
            boost::bind(&AclValidator::validateProperty, this, _1));
    }

    void AclValidator::validateProperty(std::pair<const qpid::acl::SpecProperty, std::string>& prop){
        ValidatorItr itr = validators.find(prop.first);
        if (itr != validators.end()){
            QPID_LOG(debug,"ACL: Found validator for property '" << acl::AclHelper::getPropertyStr(itr->first)
                     << "'. " << itr->second->allowedValues());

            if (!itr->second->validate(prop.second)){
                QPID_LOG(debug, "ACL: Property failed validation. '" << prop.second << "' is not a valid value for '"
                    << AclHelper::getPropertyStr(prop.first) << "'");

                throw Exception( prop.second + " is not a valid value for '" +
                    AclHelper::getPropertyStr(prop.first) + "', " +
                    itr->second->allowedValues());
            }
        }
    }

    /**
     * validateAllowedProperties
     * verify that at least one lookup definition can satisfy this
     * action/object/props tuple.
     * Return false and conditionally emit a warning log entry if the
     * incoming definition can not be matched.
     */
    bool AclValidator::validateAllowedProperties(qpid::acl::Action action,
                                                 qpid::acl::ObjectType object,
                                                 const AclData::specPropertyMap& props,
                                                 bool emitLog) const {
        // No rules defined means no match
        if (!allowedSpecProperties[action][object].get()) {
            if (emitLog) {
                QPID_LOG(warning, "ACL rule ignored: Broker never checks for rules with action: '"
                    << AclHelper::getActionStr(action) << "' and object: '"
                    << AclHelper::getObjectTypeStr(object) << "'");
            }
            return false;
        }
        // two empty property sets is a match
        if (allowedSpecProperties[action][object]->size() == 0) {
            if ((props.size() == 0) ||
                (props.size() == 1 && props.find(acl::SPECPROP_NAME) != props.end())) {
                return true;
            }
        }
        // Scan vector of rules looking for one that matches all properties
        bool validRuleFound = false;
        for (std::vector<AclData::Rule>::const_iterator
            ruleItr  = allowedSpecProperties[action][object]->begin();
            ruleItr != allowedSpecProperties[action][object]->end() && !validRuleFound;
            ruleItr++) {
            // Scan one rule
            validRuleFound = true;
            for(AclData::specPropertyMapItr itr = props.begin();
                itr != props.end();
                itr++) {
                if ((*itr).first != acl::SPECPROP_NAME &&
                    ruleItr->props.find((*itr).first) ==
                    ruleItr->props.end()) {
                    // Test property not found in this rule
                    validRuleFound = false;
                    break;
                }
            }
        }
        if (!validRuleFound) {
            if (emitLog) {
                QPID_LOG(warning, "ACL rule ignored: Broker checks for rules with action: '"
                    << AclHelper::getActionStr(action) << "' and object: '"
                    << AclHelper::getObjectTypeStr(object)
                    << "' but will never match with property set: "
                    << AclHelper::propertyMapToString(&props));
            }
            return false;
        }
        return true;
    }

    /**
     * Return a list of indexes of definitions that this lookup might match
     */
    void AclValidator::findPossibleLookupMatch(qpid::acl::Action action,
                                               qpid::acl::ObjectType object,
                                               const AclData::specPropertyMap& props,
                                               std::vector<int>& result) const {
        if (!allowedSpecProperties[action][object].get()) {
            return;
        } else {
            // Scan vector of rules returning the indexes of all that match
            bool validRuleFound;
            for (std::vector<AclData::Rule>::const_iterator
                ruleItr  = allowedSpecProperties[action][object]->begin();
                ruleItr != allowedSpecProperties[action][object]->end();
                ruleItr++) {
                // Scan one rule
                validRuleFound = true;
                for(AclData::specPropertyMapItr
                    itr = props.begin(); itr != props.end(); itr++) {
                    if ((*itr).first != acl::SPECPROP_NAME &&
                        ruleItr->props.find((*itr).first) ==
                        ruleItr->props.end()) {
                        // Test property not found in this rule
                        validRuleFound = false;
                        break;
                    }
                }
                if (validRuleFound) {
                    result.push_back(ruleItr->rawRuleNum);
                }
            }
        }
        return;
    }

    /**
     * Emit trace log of original property definitions
     */
    void AclValidator::tracePropertyDefs() {
        QPID_LOG(trace, "ACL: Definitions of action, object, (allowed properties) lookups");
        for (int iA=0; iA<acl::ACTIONSIZE; iA++) {
            for (int iO=0; iO<acl::OBJECTSIZE; iO++) {
                if (allowedSpecProperties[iA][iO].get()) {
                    for (std::vector<AclData::Rule>::const_iterator
                        ruleItr  = allowedSpecProperties[iA][iO]->begin();
                        ruleItr != allowedSpecProperties[iA][iO]->end();
                        ruleItr++) {
                        std::string pstr;
                        for (AclData::specPropertyMapItr pMItr  = ruleItr->props.begin();
                            pMItr != ruleItr->props.end();
                            pMItr++) {
                            pstr += AclHelper::getPropertyStr((SpecProperty) pMItr-> first);
                            pstr += ",";
                        }
                        QPID_LOG(trace, "ACL: Lookup "
                            << std::setfill(' ') << std::setw(2)
                            << ruleItr->rawRuleNum << ": "
                            << ruleItr->lookupHelp << " "
                            << std::setfill(' ') << std::setw(acl::ACTION_STR_WIDTH +1) << std::left
                            << AclHelper::getActionStr(acl::Action(iA))
                            << std::setfill(' ') << std::setw(acl::OBJECTTYPE_STR_WIDTH) << std::left
                            << AclHelper::getObjectTypeStr(acl::ObjectType(iO))
                            << " (" << pstr.substr(0, pstr.length()-1) << ")");
                    }
                }
            }
        }
    }

    /**
     * Construct a record of all the calls that the broker will
     * make to acl::authorize and the properties for each call.
     * From that create the list of all the spec properties that
     * users are then allowed to specify in acl rule files.
     */
    void AclValidator::registerProperties(
        const std::string& source,
        const std::string& description,
        Action action,
        ObjectType object,
        const std::string& properties) {
        if (!allowedProperties[action][object].get()) {
            boost::shared_ptr<std::set<Property> > t1(new std::set<Property>());
            allowedProperties[action][object] = t1;
            boost::shared_ptr<std::vector<AclData::Rule> > t2(new std::vector<AclData::Rule>());
            allowedSpecProperties[action][object] = t2;
        }
        std::vector<std::string> props = split(properties, " ");
        AclData::specPropertyMap spm;
        for (size_t i=0; i<props.size(); i++) {
            Property prop = AclHelper::getProperty(props[i]);
            allowedProperties[action][object]->insert(prop);
            // Given that the broker will be calling with this property,
            // determine what user rule settings are allowed.
            switch (prop) {
                // Cases where broker and Acl file share property name and meaning
                case PROP_NAME:
                    spm[SPECPROP_NAME]="";
                    break;
                case PROP_DURABLE:
                    spm[SPECPROP_DURABLE]="";
                    break;
                case PROP_OWNER:
                    spm[SPECPROP_OWNER]="";
                    break;
                case PROP_ROUTINGKEY:
                    spm[SPECPROP_ROUTINGKEY]="";
                    break;
                case PROP_AUTODELETE:
                    spm[SPECPROP_AUTODELETE]="";
                    break;
                case PROP_EXCLUSIVE:
                    spm[SPECPROP_EXCLUSIVE]="";
                    break;
                case PROP_TYPE:
                    spm[SPECPROP_TYPE]="";
                    break;
                case PROP_ALTERNATE:
                    spm[SPECPROP_ALTERNATE]="";
                    break;
                case PROP_QUEUENAME:
                    spm[SPECPROP_QUEUENAME]="";
                    break;
                case PROP_EXCHANGENAME:
                    spm[SPECPROP_EXCHANGENAME]="";
                    break;
                case PROP_SCHEMAPACKAGE:
                    spm[SPECPROP_SCHEMAPACKAGE]="";
                    break;
                case PROP_SCHEMACLASS:
                    spm[SPECPROP_SCHEMACLASS]="";
                    break;
                case PROP_POLICYTYPE:
                    spm[SPECPROP_POLICYTYPE]="";
                    break;
                case PROP_PAGING:
                    spm[SPECPROP_PAGING]="";
                    break;
                case PROP_HOST:
                    spm[SPECPROP_HOST]="";
                    break;
                // Cases where broker supplies a property but Acl has upper/lower limit for it
                case PROP_MAXPAGES:
                    spm[SPECPROP_MAXPAGESLOWERLIMIT]="";
                    spm[SPECPROP_MAXPAGESUPPERLIMIT]="";
                    break;
                case PROP_MAXPAGEFACTOR:
                    spm[SPECPROP_MAXPAGEFACTORLOWERLIMIT]="";
                    spm[SPECPROP_MAXPAGEFACTORUPPERLIMIT]="";
                    break;
                case PROP_MAXQUEUESIZE:
                    spm[SPECPROP_MAXQUEUESIZELOWERLIMIT]="";
                    spm[SPECPROP_MAXQUEUESIZEUPPERLIMIT]="";
                    break;
                case PROP_MAXQUEUECOUNT:
                    spm[SPECPROP_MAXQUEUECOUNTLOWERLIMIT]="";
                    spm[SPECPROP_MAXQUEUECOUNTUPPERLIMIT]="";
                    break;
                case PROP_MAXFILESIZE:
                    spm[SPECPROP_MAXFILESIZELOWERLIMIT]="";
                    spm[SPECPROP_MAXFILESIZEUPPERLIMIT]="";
                    break;
                case PROP_MAXFILECOUNT:
                    spm[SPECPROP_MAXFILECOUNTLOWERLIMIT]="";
                    spm[SPECPROP_MAXFILECOUNTUPPERLIMIT]="";
                    break;
                default:
                    throw Exception( "acl::RegisterProperties no case for property: " +
                        AclHelper::getPropertyStr(prop) );
            }
        }
        AclData::Rule someProps(propertyIndex, acl::ALLOW, spm, source, description);
        propertyIndex++;
        allowedSpecProperties[action][object]->push_back(someProps);
    }

}}
