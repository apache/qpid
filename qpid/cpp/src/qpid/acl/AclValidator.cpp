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
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/StringUtils.h"
#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <numeric>
#include <sstream>

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

    AclValidator::AclValidator(){
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
        RP("Broker::queryQueue",                    ACT_ACCESS,  OBJ_QUEUE);
        RP("Broker::getTimestampConfig",            ACT_ACCESS,  OBJ_BROKER);
        RP("Broker::setTimestampConfig",            ACT_UPDATE,  OBJ_BROKER);
        RP("Broker::queueRedirect",                 ACT_REDIRECT,OBJ_QUEUE, "queuename");
        RP("Broker::queueMoveMessages",             ACT_MOVE,    OBJ_QUEUE, "queuename");
        RP("Broker::createQueue",                   ACT_CREATE,  OBJ_QUEUE, "alternate durable exclusive autodelete policytype paging maxpages maxpagefactor maxqueuecount maxqueuesize maxfilecount maxfilesize");
        RP("Broker::deleteQueue",                   ACT_DELETE,  OBJ_QUEUE, "alternate durable exclusive autodelete policytype");
        RP("Broker::createExchange",                ACT_CREATE,  OBJ_EXCHANGE, "type alternate durable autodelete");
        RP("Broker::deleteExchange",                ACT_DELETE,  OBJ_EXCHANGE, "type alternate durable");
        RP("Broker::bind",                          ACT_BIND,    OBJ_EXCHANGE, "queuename routingkey");
        RP("Broker::unbind",                        ACT_UNBIND,  OBJ_EXCHANGE, "queuename routingkey");
        RP("ConnectionHandler::Handler::open",      ACT_CREATE,  OBJ_LINK);
        RP("Queue::ManagementMethod",               ACT_PURGE,   OBJ_QUEUE);
        RP("Queue::ManagementMethod",               ACT_REROUTE, OBJ_QUEUE, "exchangename");
        RP("SemanticState::route",                  ACT_PUBLISH, OBJ_EXCHANGE, "routingkey");
        RP("ExchangeHandlerImpl::declare",          ACT_ACCESS,  OBJ_EXCHANGE, "type alternate durable autodelete");
        RP("ExchangeHandlerImpl::query",            ACT_ACCESS,  OBJ_EXCHANGE);
        RP("ExchangeHandlerImpl::bound",            ACT_ACCESS,  OBJ_EXCHANGE, "queuename routingkey");
        RP("QueueHandlerImpl::query",               ACT_ACCESS,  OBJ_QUEUE);
        RP("QueueHandlerImpl::declare",             ACT_ACCESS,  OBJ_QUEUE, "alternate durable exclusive autodelete policytype maxqueuecount maxqueuesize");
        RP("QueueHandlerImpl::purge",               ACT_PURGE,   OBJ_QUEUE);
        RP("MessageHandlerImpl::subscribe",         ACT_CONSUME, OBJ_QUEUE);
        RP("Authorise::access",                     ACT_ACCESS,  OBJ_EXCHANGE, "type durable");
        RP("Authorise::access",                     ACT_ACCESS,  OBJ_QUEUE, "alternate durable exclusive autodelete policytype maxqueuecount maxqueuesize");
        RP("Authorise::incoming",                   ACT_PUBLISH, OBJ_EXCHANGE);
        RP("Authorise::outgoing",                   ACT_BIND,    OBJ_EXCHANGE, "queuename routingkey");
        RP("Authorise::outgoing",                   ACT_CONSUME, OBJ_QUEUE);
        RP("Authorise::route",                      ACT_PUBLISH, OBJ_EXCHANGE, "routingkey");
        RP("Authorise::interlink",                  ACT_CREATE,  OBJ_LINK);
        RP("Authorise::access",                     ACT_ACCESS,  OBJ_EXCHANGE);
        RP("Authorise::access",                     ACT_ACCESS,  OBJ_QUEUE);
        RP("ManagementAgent::handleMethodRequest",  ACT_ACCESS,  OBJ_METHOD, "schemapackage schemaclass");
        RP("ManagementAgent::handleGetQuery",       ACT_ACCESS,  OBJ_QUERY, "schemaclass");
        RP("ManagementAgent::authorizeAgentMessage",ACT_ACCESS,  OBJ_METHOD, "schemapackage schemaclass");
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
                    }//if
                }//for
            }//if
        }//for
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
     * Construct a record of all the calls that the broker will
     * make to acl::authorize and the properties for each call.
     * From that create the list of all the spec properties that
     * users are then allowed to specify in acl rule files.
     */
    void AclValidator::registerProperties(
        const std::string& /* source */,
        Action action,
        ObjectType object,
        const std::string& properties) {
        if (!allowedProperties[action][object].get()) {
            boost::shared_ptr<std::set<Property> > t1(new std::set<Property>());
            allowedProperties[action][object] = t1;
            boost::shared_ptr<std::set<SpecProperty> > t2(new std::set<SpecProperty>());
            allowedSpecProperties[action][object] = t2;
        }
        std::vector<std::string> props = split(properties, " ");
        for (size_t i=0; i<props.size(); i++) {
            Property prop = AclHelper::getProperty(props[i]);
            allowedProperties[action][object]->insert(prop);
            // Given that the broker will be calling with this property,
            // determine what user rule settings are allowed.
            switch (prop) {
                // Cases where broker supplies a property but Acl has upper/lower limit for it
                case PROP_MAXPAGES:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXPAGESLOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXPAGESUPPERLIMIT);
                    break;
                case PROP_MAXPAGEFACTOR:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXPAGEFACTORLOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXPAGEFACTORUPPERLIMIT);
                    break;
                case PROP_MAXQUEUESIZE:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXQUEUESIZELOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXQUEUESIZEUPPERLIMIT);
                    break;
                case PROP_MAXQUEUECOUNT:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXQUEUECOUNTLOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXQUEUECOUNTUPPERLIMIT);
                    break;
                case PROP_MAXFILESIZE:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXFILESIZELOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXFILESIZEUPPERLIMIT);
                    break;
                case PROP_MAXFILECOUNT:
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXFILECOUNTLOWERLIMIT);
                    allowedSpecProperties[action][object]->insert(SPECPROP_MAXFILECOUNTUPPERLIMIT);
                    break;
                default:
                    // Cases where broker supplies a property and Acl matches it directly
                    allowedSpecProperties[action][object]->insert( SpecProperty(prop) );
                    break;
            }
        }
    }

}}
