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


#include "unit_test.h"
#include "test_tools.h"
#include "qpid/acl/AclLexer.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid;
using namespace qpid::acl;
using namespace boost::assign;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(AclTestSuite)

#define OBJ_ENUMS(e, s) \
    BOOST_CHECK_EQUAL(AclHelper::getObjectTypeStr((e)),(s)); \
    BOOST_CHECK_EQUAL(AclHelper::getObjectType((s)),(e))

QPID_AUTO_TEST_CASE(TestLexerObjectEnums) {
    BOOST_CHECK_EQUAL(OBJECTSIZE, 7);
    OBJ_ENUMS(OBJ_QUEUE,      "queue");
    OBJ_ENUMS(OBJ_EXCHANGE,   "exchange");
    OBJ_ENUMS(OBJ_BROKER,     "broker");
    OBJ_ENUMS(OBJ_LINK,       "link");
    OBJ_ENUMS(OBJ_METHOD,     "method");
    OBJ_ENUMS(OBJ_QUERY,      "query");
    OBJ_ENUMS(OBJ_CONNECTION, "connection");
    int maxLen = 0;
    for (int i=0; i<acl::OBJECTSIZE; i++) {
        int thisLen = AclHelper::getObjectTypeStr( ObjectType(i) ).length();
        if (thisLen > maxLen)
            maxLen = thisLen;
    }
    BOOST_CHECK_EQUAL(maxLen, acl::OBJECTTYPE_STR_WIDTH);
}

#define ACT_ENUMS(e, s) \
    BOOST_CHECK_EQUAL(AclHelper::getActionStr((e)),(s)); \
    BOOST_CHECK_EQUAL(AclHelper::getAction((s)),(e))

QPID_AUTO_TEST_CASE(TestLexerActionEnums) {
    BOOST_CHECK_EQUAL(ACTIONSIZE, 12);
    ACT_ENUMS(ACT_CONSUME,  "consume");
    ACT_ENUMS(ACT_PUBLISH,  "publish");
    ACT_ENUMS(ACT_CREATE,   "create");
    ACT_ENUMS(ACT_ACCESS,   "access");
    ACT_ENUMS(ACT_BIND,     "bind");
    ACT_ENUMS(ACT_UNBIND,   "unbind");
    ACT_ENUMS(ACT_DELETE,   "delete");
    ACT_ENUMS(ACT_PURGE,    "purge");
    ACT_ENUMS(ACT_UPDATE,   "update");
    ACT_ENUMS(ACT_MOVE,     "move");
    ACT_ENUMS(ACT_REDIRECT, "redirect");
    ACT_ENUMS(ACT_REROUTE,  "reroute");
    int maxLen = 0;
    for (int i=0; i<acl::ACTIONSIZE; i++) {
        int thisLen = AclHelper::getActionStr( Action(i) ).length();
        if (thisLen > maxLen)
            maxLen = thisLen;
    }
    BOOST_CHECK_EQUAL(maxLen, acl::ACTION_STR_WIDTH);
}

#define PROP_ENUMS(e, s) \
    BOOST_CHECK_EQUAL(AclHelper::getPropertyStr((e)),(s)); \
    BOOST_CHECK_EQUAL(AclHelper::getProperty((s)),(e))

QPID_AUTO_TEST_CASE(TestLexerPropertyEnums) {
    BOOST_CHECK_EQUAL(PROPERTYSIZE, 21);
    PROP_ENUMS(PROP_NAME,           "name");
    PROP_ENUMS(PROP_DURABLE,        "durable");
    PROP_ENUMS(PROP_OWNER,          "owner");
    PROP_ENUMS(PROP_ROUTINGKEY,     "routingkey");
    PROP_ENUMS(PROP_AUTODELETE,     "autodelete");
    PROP_ENUMS(PROP_EXCLUSIVE,      "exclusive");
    PROP_ENUMS(PROP_TYPE,           "type");
    PROP_ENUMS(PROP_ALTERNATE,      "alternate");
    PROP_ENUMS(PROP_QUEUENAME,      "queuename");
    PROP_ENUMS(PROP_EXCHANGENAME,   "exchangename");
    PROP_ENUMS(PROP_SCHEMAPACKAGE,  "schemapackage");
    PROP_ENUMS(PROP_SCHEMACLASS,    "schemaclass");
    PROP_ENUMS(PROP_POLICYTYPE,     "policytype");
    PROP_ENUMS(PROP_PAGING,         "paging");
    PROP_ENUMS(PROP_HOST,           "host");
    PROP_ENUMS(PROP_MAXPAGES,       "maxpages");
    PROP_ENUMS(PROP_MAXPAGEFACTOR,  "maxpagefactor");
    PROP_ENUMS(PROP_MAXQUEUESIZE,   "maxqueuesize");
    PROP_ENUMS(PROP_MAXQUEUECOUNT,  "maxqueuecount");
    PROP_ENUMS(PROP_MAXFILESIZE,    "maxfilesize");
    PROP_ENUMS(PROP_MAXFILECOUNT,   "maxfilecount");

}

#define SPECPROP_ENUMS(e, s) \
    BOOST_CHECK_EQUAL(AclHelper::getPropertyStr((e)),(s)); \
    BOOST_CHECK_EQUAL(AclHelper::getSpecProperty((s)),(e))

QPID_AUTO_TEST_CASE(TestLexerSpecPropertyEnums) {
    BOOST_CHECK_EQUAL(SPECPROPSIZE, 27);
    SPECPROP_ENUMS(SPECPROP_NAME,          "name");
    SPECPROP_ENUMS(SPECPROP_DURABLE,       "durable");
    SPECPROP_ENUMS(SPECPROP_OWNER,         "owner");
    SPECPROP_ENUMS(SPECPROP_ROUTINGKEY,    "routingkey");
    SPECPROP_ENUMS(SPECPROP_AUTODELETE,    "autodelete");
    SPECPROP_ENUMS(SPECPROP_EXCLUSIVE,     "exclusive");
    SPECPROP_ENUMS(SPECPROP_TYPE,          "type");
    SPECPROP_ENUMS(SPECPROP_ALTERNATE,     "alternate");
    SPECPROP_ENUMS(SPECPROP_QUEUENAME,     "queuename");
    SPECPROP_ENUMS(SPECPROP_EXCHANGENAME,  "exchangename");
    SPECPROP_ENUMS(SPECPROP_SCHEMAPACKAGE, "schemapackage");
    SPECPROP_ENUMS(SPECPROP_SCHEMACLASS,   "schemaclass");
    SPECPROP_ENUMS(SPECPROP_POLICYTYPE,    "policytype");
    SPECPROP_ENUMS(SPECPROP_PAGING,        "paging");
    SPECPROP_ENUMS(SPECPROP_HOST,          "host");
    SPECPROP_ENUMS(SPECPROP_MAXQUEUESIZELOWERLIMIT,  "queuemaxsizelowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXQUEUESIZEUPPERLIMIT,  "queuemaxsizeupperlimit");
    SPECPROP_ENUMS(SPECPROP_MAXQUEUECOUNTLOWERLIMIT, "queuemaxcountlowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXQUEUECOUNTUPPERLIMIT, "queuemaxcountupperlimit");
    SPECPROP_ENUMS(SPECPROP_MAXFILESIZELOWERLIMIT,   "filemaxsizelowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXFILESIZEUPPERLIMIT,   "filemaxsizeupperlimit");
    SPECPROP_ENUMS(SPECPROP_MAXFILECOUNTLOWERLIMIT,  "filemaxcountlowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXFILECOUNTUPPERLIMIT,  "filemaxcountupperlimit");
    SPECPROP_ENUMS(SPECPROP_MAXPAGESLOWERLIMIT,      "pageslowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXPAGESUPPERLIMIT,      "pagesupperlimit");
    SPECPROP_ENUMS(SPECPROP_MAXPAGEFACTORLOWERLIMIT, "pagefactorlowerlimit");
    SPECPROP_ENUMS(SPECPROP_MAXPAGEFACTORUPPERLIMIT, "pagefactorupperlimit");

    BOOST_CHECK_EQUAL(AclHelper::getSpecProperty("maxqueuesize"),  SPECPROP_MAXQUEUESIZEUPPERLIMIT);
    BOOST_CHECK_EQUAL(AclHelper::getSpecProperty("maxqueuecount"), SPECPROP_MAXQUEUECOUNTUPPERLIMIT);
}

#define RESULT_ENUMS(e, s) \
    BOOST_CHECK_EQUAL(AclHelper::getAclResultStr((e)),(s)); \
    BOOST_CHECK_EQUAL(AclHelper::getAclResult((s)),(e))

QPID_AUTO_TEST_CASE(TestLexerResultEnums) {
    BOOST_CHECK_EQUAL(RESULTSIZE, 4);
    RESULT_ENUMS(ALLOW,    "allow");
    RESULT_ENUMS(ALLOWLOG, "allow-log");
    RESULT_ENUMS(DENY,     "deny");
    RESULT_ENUMS(DENYLOG,  "deny-log");
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
