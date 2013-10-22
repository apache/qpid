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

        std::string policyTypes[] = {"ring", "self-destruct", "reject"};
        std::vector<std::string> v(policyTypes, policyTypes + sizeof(policyTypes) / sizeof(std::string));
        validators.insert(Validator(acl::SPECPROP_POLICYTYPE,
                          boost::shared_ptr<PropertyType>(
                            new EnumPropertyType(v))));

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

}}
