#!/usr/bin/env python
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import sys
import qpid
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import uuid4
from qpid.testlib import TestBase010
from qmf.console import Session
from qpid.datatypes import Message
import qpid.messaging

class ACLFile:
    def __init__(self, policy='data_dir/policy.acl'):
        self.f = open(policy,'w')

    def write(self,line):
        self.f.write(line)

    def close(self):
        self.f.close()

class ACLTests(TestBase010):

    def get_session(self, user, passwd):
        socket = connect(self.broker.host, self.broker.port)
        connection = Connection (sock=socket, username=user, password=passwd,
                                 mechanism="PLAIN")
        connection.start()
        return connection.session(str(uuid4()))

    def port_i(self):
        return int(self.defines["port-i"])

    def port_u(self):
        return int(self.defines["port-u"])

    def get_session_by_port(self, user, passwd, byPort):
        socket = connect(self.broker.host, byPort)
        connection = Connection (sock=socket, username=user, password=passwd,
                                 mechanism="PLAIN")
        connection.start()
        return connection.session(str(uuid4()))

    def reload_acl(self):
        result = None
        try:
            self.broker_access.reloadAclFile()
        except Exception, e:
            result = str(e)
        return result

    def acl_lookup(self, userName, action, aclObj, aclObjName, propMap):
        result = {}
        try:
            result = self.broker_access.acl_lookup(userName, action, aclObj, aclObjName, propMap)
        except Exception, e:
            result['text'] = str(e)
            result['result'] = str(e)
        return result

    def acl_lookupPublish(self, userName, exchange, key):
        result = {}
        try:
            result = self.broker_access.acl_lookupPublish(userName, exchange, key)
        except Exception, e:
            result['text'] = str(e)
            result['result'] = str(e)
        return result

    def get_acl_file(self):
        return ACLFile(self.config.defines.get("policy-file", "data_dir/policy.acl"))

    def setUp(self):
        aclf = self.get_acl_file()
        aclf.write('acl allow all all\n')
        aclf.close()
        TestBase010.setUp(self)
        self.startBrokerAccess()
        self.reload_acl()

    def tearDown(self):
        aclf = self.get_acl_file()
        aclf.write('acl allow all all\n')
        aclf.close()
        self.reload_acl()
        TestBase010.tearDown(self)


    def Lookup(self, userName, action, aclObj, aclObjName, propMap, expectedResult):
        result = self.acl_lookup(userName, action, aclObj, aclObjName, propMap)
        if (result['result'] != expectedResult):
            suffix = ', [ERROR: Expected= ' + expectedResult
            if (result['result'] is None):
                suffix = suffix + ', Exception= ' + result['text'] + ']'
            else:
                suffix = suffix + ', Actual= ' + result['result'] + ']'
            self.fail('Lookup: name=' + userName + ', action=' + action + ', aclObj=' + aclObj + ', aclObjName=' + aclObjName + ', propertyMap=' + str(propMap) + suffix)


    def LookupPublish(self, userName, exchName, keyName, expectedResult):
        result = self.acl_lookupPublish(userName, exchName, keyName)
        if (result['result'] != expectedResult):
            if (result['result'] is None):
                suffix = suffix + ', Exception= ' + result['text'] + ']'
            else:
                suffix = suffix + ', Actual= ' + result['result'] + ']'
            self.fail('LookupPublish: name=' + userName + ', exchange=' + exchName + ', key=' + keyName + suffix)

    def AllBut(self, allList, removeList):
        tmpList = allList[:]
        for item in removeList:
            try:
                tmpList.remove(item)
            except Exception, e:
                self.fail("ERROR in AllBut() \nallList =  %s \nremoveList =  %s \nerror =  %s " \
                    % (allList, removeList, e))
        return tmpList

   #=====================================
   # ACL general tests
   #=====================================

    def test_deny_mode(self):
        """
        Test the deny all mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl allow bob@QPID create queue\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')
        try:
            session.queue_declare(queue="deny_queue")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request");
            self.fail("Error during queue create request");

        try:
            session.exchange_bind(exchange="amq.direct", queue="deny_queue", binding_key="routing_key")
            self.fail("ACL should deny queue bind request");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)

    def test_allow_mode(self):
        """
        Test the allow all mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID bind exchange\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')
        try:
            session.queue_declare(queue="allow_queue")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request");
            self.fail("Error during queue create request");

        try:
            session.exchange_bind(exchange="amq.direct", queue="allow_queue", binding_key="routing_key")
            self.fail("ACL should deny queue bind request");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)


    def test_allow_mode_with_specfic_allow_override(self):
        """
        Specific allow overrides a general deny
        """
        aclf = self.get_acl_file()
        aclf.write('group admins bob@QPID joe@QPID  \n')
        aclf.write('acl allow bob@QPID create queue \n')
        aclf.write('acl deny  admins   create queue \n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue='zed')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow create queue request");


   #=====================================
   # ACL file format tests
   #=====================================

    def test_empty_groups(self):
        """
        Test empty groups
        """
        aclf = self.get_acl_file()
        aclf.write('acl group\n')
        aclf.write('acl group admins bob@QPID joe@QPID\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("Insufficient tokens for acl definition",0,len(result)) == -1):
            self.fail("ACL Reader should reject the acl file due to empty group name")

    def test_illegal_acl_formats(self):
        """
        Test illegal acl formats
        """
        aclf = self.get_acl_file()
        aclf.write('acl group admins bob@QPID joe@QPID\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("Unknown ACL permission",0,len(result)) == -1):
            self.fail(result)

    def test_illegal_extension_lines(self):
        """
        Test illegal extension lines
        """

        aclf = self.get_acl_file()
        aclf.write('group admins bob@QPID \n')
        aclf.write('          \ \n')
        aclf.write('joe@QPID \n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("contains an illegal extension",0,len(result)) == -1):
            self.fail(result)

        if (result.find("Non-continuation line must start with \"group\" or \"acl\"",0,len(result)) == -1):
            self.fail(result)

    def test_illegal_extension_lines(self):
        """
        Test proper extention lines
        """
        aclf = self.get_acl_file()
        aclf.write('group test1 joe@EXAMPLE.com \\ \n') # should be allowed
        aclf.write('            jack@EXAMPLE.com \\ \n') # should be allowed
        aclf.write('jill@TEST.COM \\ \n') # should be allowed
        aclf.write('host/123.example.com@TEST.COM\n') # should be allowed
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

    def test_user_realm(self):
        """
        Test a user defined without a realm
        Ex. group admin rajith
        """
        aclf = self.get_acl_file()
        aclf.write('group admin bob\n') # shouldn't be allowed
        aclf.write('acl deny admin bind exchange\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("Username 'bob' must contain a realm",0,len(result)) == -1):
            self.fail(result)

    def test_allowed_chars_for_username(self):
        """
        Test a user defined without a realm
        Ex. group admin rajith
        """
        aclf = self.get_acl_file()
        aclf.write('group test1 joe@EXAMPLE.com\n') # should be allowed
        aclf.write('group test2 jack_123-jill@EXAMPLE.com\n') # should be allowed
        aclf.write('group test4 host/somemachine.example.com@EXAMPLE.COM\n') # should be allowed
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('group test1 joe$H@EXAMPLE.com\n') # shouldn't be allowed
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("Username \"joe$H@EXAMPLE.com\" contains illegal characters",0,len(result)) == -1):
            self.fail(result)

   #=====================================
   # ACL validation tests
   #=====================================

    def test_illegal_queue_policy(self):
        """
        Test illegal queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 exclusive=true policytype=ding\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "ding is not a valid value for 'policytype', possible values are one of" \
                   " { 'ring' 'ring_strict' 'flow_to_disk' 'reject' }";
        if (result.find(expected) == -1):
            self.fail(result)

    def test_illegal_queuemaxsize_upper_limit_spec(self):
        """
        Test illegal queue policy
        """
        #
        # Use maxqueuesize
        #
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 maxqueuesize=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 maxqueuesize=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        #
        # Use queuemaxsizeupperlimit
        #
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxsizeupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxsizeupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)



    def test_illegal_queuemaxcount_upper_limit_spec(self):
        """
        Test illegal queue policy
        """
        #
        # Use maxqueuecount
        #

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 maxqueuecount=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 maxqueuecount=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        #
        # use maxqueuecountupperlimit
        #
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxcountupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxcountupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_queuemaxsize_lower_limit_spec(self):
        """
        Test illegal queue policy
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxsizelowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxsizelowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxsizelowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxsizelowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)



    def test_illegal_queuemaxcount_lower_limit_spec(self):
        """
        Test illegal queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxcountlowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'queuemaxcountlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 queuemaxcountlowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'queuemaxcountlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


   #=====================================
   # ACL queue tests
   #=====================================

    def test_queue_allow_mode(self):
        """
        Test cases for queue acl in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID access queue name=q1\n')
        aclf.write('acl deny bob@QPID create queue name=q1 durable=true\n')
        aclf.write('acl deny bob@QPID create queue name=q2 exclusive=true policytype=ring\n')
        aclf.write('acl deny bob@QPID access queue name=q3\n')
        aclf.write('acl deny bob@QPID purge queue name=q3\n')
        aclf.write('acl deny bob@QPID delete queue name=q4\n')
        aclf.write('acl deny bob@QPID create queue name=q5 maxqueuesize=1000 maxqueuecount=100\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="q1", durable=True)
            self.fail("ACL should deny queue create request with name=q1 durable=true");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="q1", durable=True, passive=True)
            self.fail("ACL should deny queue passive declare request with name=q1 durable=true");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.policy_type"] = "ring"
            session.queue_declare(queue="q2", exclusive=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q2 exclusive=true qpid.policy_type=ring");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.policy_type"] = "ring_strict"
            session.queue_declare(queue="q2", exclusive=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=q2 exclusive=true qpid.policy_type=ring_strict");

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 200
            queue_options["qpid.max_size"] = 500
            session.queue_declare(queue="q5", exclusive=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q2, qpid.max_size=500 and qpid.max_count=200");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 200
            queue_options["qpid.max_size"] = 100
            session.queue_declare(queue="q2", exclusive=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=q2, qpid.max_size=100 and qpid.max_count=200 ");
        try:
            session.queue_declare(queue="q3", exclusive=True)
            session.queue_declare(queue="q4", durable=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request for q3 and q4 with any parameter");

        try:
            session.queue_query(queue="q3")
            self.fail("ACL should deny queue query request for q3");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_purge(queue="q3")
            self.fail("ACL should deny queue purge request for q3");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_purge(queue="q4")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue purge request for q4");

        try:
            session.queue_delete(queue="q4")
            self.fail("ACL should deny queue delete request for q4");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_delete(queue="q3")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue delete request for q3");


    def test_queue_deny_mode(self):
        """
        Test cases for queue acl in deny mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID access queue name=q1\n')
        aclf.write('acl allow bob@QPID create queue name=q1 durable=true\n')
        aclf.write('acl allow bob@QPID create queue name=q2 exclusive=true policytype=ring\n')
        aclf.write('acl allow bob@QPID access queue name=q3\n')
        aclf.write('acl allow bob@QPID purge queue name=q3\n')
        aclf.write('acl allow bob@QPID create queue name=q3\n')
        aclf.write('acl allow bob@QPID create queue name=q4\n')
        aclf.write('acl allow bob@QPID delete queue name=q4\n')
        aclf.write('acl allow bob@QPID create queue name=q5 maxqueuesize=1000 maxqueuecount=100\n')
        aclf.write('acl allow bob@QPID create queue name=q6 queuemaxsizelowerlimit=50 queuemaxsizeupperlimit=100 queuemaxcountlowerlimit=50 queuemaxcountupperlimit=100\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="q1", durable=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=q1 durable=true");

        try:
            session.queue_declare(queue="q1", durable=True, passive=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue passive declare request with name=q1 durable=true passive=true");

        try:
            session.queue_declare(queue="q1", durable=False, passive=False)
            self.fail("ACL should deny queue create request with name=q1 durable=true passive=false");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="q2", exclusive=False)
            self.fail("ACL should deny queue create request with name=q2 exclusive=false");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 200
            queue_options["qpid.max_size"] = 500
            session.queue_declare(queue="q5", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q5 maxqueuesize=500 maxqueuecount=200");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 100
            queue_options["qpid.max_size"] = 500
            session.queue_declare(queue="q5", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=q5 maxqueuesize=500 maxqueuecount=200");

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 49
            queue_options["qpid.max_size"] = 100
            session.queue_declare(queue="q6", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q6 maxqueuesize=100 maxqueuecount=49");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 101
            queue_options["qpid.max_size"] = 100
            session.queue_declare(queue="q6", arguments=queue_options)
            self.fail("ACL should allow queue create request with name=q6 maxqueuesize=100 maxqueuecount=101");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 100
            queue_options["qpid.max_size"] = 49
            session.queue_declare(queue="q6", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q6 maxqueuesize=49 maxqueuecount=100");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 100
            queue_options["qpid.max_size"] =101
            session.queue_declare(queue="q6", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q6 maxqueuesize=101 maxqueuecount=100");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.max_count"] = 50
            queue_options["qpid.max_size"] = 50
            session.queue_declare(queue="q6", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=q6 maxqueuesize=50 maxqueuecount=50");

        try:
            queue_options = {}
            queue_options["qpid.policy_type"] = "ring"
            session.queue_declare(queue="q2", exclusive=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request for q2 with exclusive=true policytype=ring");

        try:
            session.queue_declare(queue="q3")
            session.queue_declare(queue="q4")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request for q3 and q4");

        try:
            session.queue_query(queue="q4")
            self.fail("ACL should deny queue query request for q4");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_purge(queue="q4")
            self.fail("ACL should deny queue purge request for q4");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_purge(queue="q3")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue purge request for q3");

        try:
            session.queue_query(queue="q3")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue query request for q3");

        try:
            session.queue_delete(queue="q3")
            self.fail("ACL should deny queue delete request for q3");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_delete(queue="q4")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue delete request for q4");

   #=====================================
   # ACL exchange tests
   #=====================================

    def test_exchange_acl_allow_mode(self):
        session = self.get_session('bob','bob')
        session.queue_declare(queue="baz")

        """
        Test cases for exchange acl in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID access exchange name=testEx\n')
        aclf.write('acl deny bob@QPID create exchange name=testEx durable=true\n')
        aclf.write('acl deny bob@QPID create exchange name=ex1 type=direct\n')
        aclf.write('acl deny bob@QPID access exchange name=myEx queuename=q1 routingkey=rk1.*\n')
        aclf.write('acl deny bob@QPID bind exchange name=myEx queuename=q1 routingkey=rk1\n')
        aclf.write('acl deny bob@QPID unbind exchange name=myEx queuename=q1 routingkey=rk1\n')
        aclf.write('acl deny bob@QPID delete exchange name=myEx\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')
        session.queue_declare(queue='q1')
        session.queue_declare(queue='q2')
        session.exchange_declare(exchange='myEx', type='direct')

        try:
            session.exchange_declare(exchange='testEx', durable=True)
            self.fail("ACL should deny exchange create request with name=testEx durable=true");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='testEx', durable=True, passive=True)
            self.fail("ACL should deny passive exchange declare request with name=testEx durable=true passive=true");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='testEx', type='direct', durable=False)
        except qpid.session.SessionException, e:
            print e
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange create request for testEx with any parameter other than durable=true");

        try:
            session.exchange_declare(exchange='ex1', type='direct')
            self.fail("ACL should deny exchange create request with name=ex1 type=direct");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='myXml', type='direct')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange create request for myXml with any parameter");

        try:
            session.exchange_query(name='myEx')
            self.fail("ACL should deny exchange query request for myEx");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_bound(exchange='myEx', queue='q1', binding_key='rk1.*')
            self.fail("ACL should deny exchange bound request for myEx with queuename=q1 and routing_key='rk1.*' ");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_query(name='amq.topic')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange query request for exchange='amq.topic'");

        try:
            session.exchange_bound(exchange='myEx', queue='q1', binding_key='rk2.*')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange bound request for myEx with queuename=q1 and binding_key='rk2.*'");

        try:
            session.exchange_bind(exchange='myEx', queue='q1', binding_key='rk1')
            self.fail("ACL should deny exchange bind request with exchange='myEx' queuename='q1' bindingkey='rk1'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_bind(exchange='myEx', queue='q1', binding_key='x')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange bind request for exchange='myEx', queue='q1', binding_key='x'");

        try:
            session.exchange_bind(exchange='myEx', queue='q2', binding_key='rk1')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange bind request for exchange='myEx', queue='q2', binding_key='rk1'");

        try:
            session.exchange_unbind(exchange='myEx', queue='q1', binding_key='rk1')
            self.fail("ACL should deny exchange unbind request with exchange='myEx' queuename='q1' bindingkey='rk1'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_unbind(exchange='myEx', queue='q1', binding_key='x')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange unbind request for exchange='myEx', queue='q1', binding_key='x'");

        try:
            session.exchange_unbind(exchange='myEx', queue='q2', binding_key='rk1')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange unbind request for exchange='myEx', queue='q2', binding_key='rk1'");

        try:
            session.exchange_delete(exchange='myEx')
            self.fail("ACL should deny exchange delete request for myEx");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_delete(exchange='myXml')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange delete request for myXml");


    def test_exchange_acl_deny_mode(self):
        session = self.get_session('bob','bob')
        session.queue_declare(queue='bar')

        """
        Test cases for exchange acl in deny mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID create exchange name=myEx durable=true\n')
        aclf.write('acl allow bob@QPID bind exchange name=amq.topic queuename=bar routingkey=foo.*\n')
        aclf.write('acl allow bob@QPID unbind exchange name=amq.topic queuename=bar routingkey=foo.*\n')
        aclf.write('acl allow bob@QPID access exchange name=myEx queuename=q1 routingkey=rk1.*\n')
        aclf.write('acl allow bob@QPID delete exchange name=myEx\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='myEx', type='direct', durable=True, passive=False)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange create request for myEx with durable=true and passive=false");

        try:
            session.exchange_declare(exchange='myEx', type='direct', durable=False)
            self.fail("ACL should deny exchange create request with name=myEx durable=false");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_bind(exchange='amq.topic', queue='bar', binding_key='foo.bar')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange bind request for exchange='amq.topic', queue='bar', binding_key='foor.bar'");

        try:
            session.exchange_bind(exchange='amq.topic', queue='baz', binding_key='foo.bar')
            self.fail("ACL should deny exchange bind request for exchange='amq.topic', queue='baz', binding_key='foo.bar'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_bind(exchange='amq.topic', queue='bar', binding_key='fooz.bar')
            self.fail("ACL should deny exchange bind request for exchange='amq.topic', queue='bar', binding_key='fooz.bar'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_unbind(exchange='amq.topic', queue='bar', binding_key='foo.bar')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange unbind request for exchange='amq.topic', queue='bar', binding_key='foor.bar'");
        try:
            session.exchange_unbind(exchange='amq.topic', queue='baz', binding_key='foo.bar')
            self.fail("ACL should deny exchange unbind request for exchange='amq.topic', queue='baz', binding_key='foo.bar'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_unbind(exchange='amq.topic', queue='bar', binding_key='fooz.bar')
            self.fail("ACL should deny exchange unbind request for exchange='amq.topic', queue='bar', binding_key='fooz.bar'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_query(name='amq.topic')
            self.fail("ACL should deny exchange query request for amq.topic");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_bound(exchange='myEx', queue='q1', binding_key='rk2.*')
            self.fail("ACL should deny exchange bound request for amq.topic with queuename=q1 and routing_key='rk2.*' ");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_query(name='myEx')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange query request for exchange='myEx'");

        try:
            session.exchange_bound(exchange='myEx', queue='q1', binding_key='rk1.*')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange bound request for myEx with queuename=q1 and binding_key='rk1.*'");

        try:
            session.exchange_delete(exchange='myXml')
            self.fail("ACL should deny exchange delete request for myXml");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_delete(exchange='myEx')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow exchange delete request for myEx");

    def test_create_and_delete_exchange_via_qmf(self):
        """
        Test acl is enforced when creating/deleting via QMF
        methods. Note that in order to be able to send the QMF methods
        and receive the responses a significant amount of permissions
        need to be enabled (TODO: can the set below be narrowed down
        at all?)
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID create exchange\n')
        aclf.write('acl allow admin@QPID delete exchange\n')
        aclf.write('acl allow all access exchange\n')
        aclf.write('acl allow all bind exchange\n')
        aclf.write('acl allow all create queue\n')
        aclf.write('acl allow all access queue\n')
        aclf.write('acl allow all delete queue\n')
        aclf.write('acl allow all consume queue\n')
        aclf.write('acl allow all access method\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        bob = BrokerAdmin(self.config.broker, "bob", "bob")
        bob.create_exchange("my-exchange") #should pass
        #cleanup by deleting exchange
        try:
            bob.delete_exchange("my-exchange") #should fail
            self.fail("ACL should deny exchange delete request for my-exchange");
        except Exception, e:
            self.assertEqual(7,e.args[0]["error_code"])
            assert e.args[0]["error_text"].find("unauthorized-access") == 0
        admin = BrokerAdmin(self.config.broker, "admin", "admin")
        admin.delete_exchange("my-exchange") #should pass

        anonymous = BrokerAdmin(self.config.broker)
        try:
            anonymous.create_exchange("another-exchange") #should fail
            self.fail("ACL should deny exchange create request for another-exchange");
        except Exception, e:
            self.assertEqual(7,e.args[0]["error_code"])
            assert e.args[0]["error_text"].find("unauthorized-access") == 0


   #=====================================
   # ACL consume tests
   #=====================================

    def test_consume_allow_mode(self):
        """
        Test cases for consume in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID consume queue name=q1\n')
        aclf.write('acl deny bob@QPID consume queue name=q2\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')


        try:
            session.queue_declare(queue='q1')
            session.queue_declare(queue='q2')
            session.queue_declare(queue='q3')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow create queue request");

        try:
            session.message_subscribe(queue='q1', destination='myq1')
            self.fail("ACL should deny subscription for queue='q1'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.message_subscribe(queue='q2', destination='myq1')
            self.fail("ACL should deny subscription for queue='q2'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.message_subscribe(queue='q3', destination='myq1')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow subscription for q3");


    def test_consume_deny_mode(self):
        """
        Test cases for consume in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID consume queue name=q1\n')
        aclf.write('acl allow bob@QPID consume queue name=q2\n')
        aclf.write('acl allow bob@QPID create queue\n')
        aclf.write('acl allow anonymous all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')


        try:
            session.queue_declare(queue='q1')
            session.queue_declare(queue='q2')
            session.queue_declare(queue='q3')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow create queue request");

        try:
            session.message_subscribe(queue='q1', destination='myq1')
            session.message_subscribe(queue='q2', destination='myq2')
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow subscription for q1 and q2");

        try:
            session.message_subscribe(queue='q3', destination='myq3')
            self.fail("ACL should deny subscription for queue='q3'");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')


   #=====================================
   # ACL publish tests
   #=====================================

    def test_publish_acl_allow_mode(self):
        """
        Test various publish acl
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID publish exchange name=amq.direct routingkey=rk1\n')
        aclf.write('acl deny bob@QPID publish exchange name=amq.topic\n')
        aclf.write('acl deny bob@QPID publish exchange name=myEx routingkey=rk2\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        props = session.delivery_properties(routing_key="rk1")

        try:
            session.message_transfer(destination="amq.direct", message=Message(props,"Test"))
            self.fail("ACL should deny message transfer to name=amq.direct routingkey=rk1");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.message_transfer(destination="amq.topic", message=Message(props,"Test"))
            self.fail("ACL should deny message transfer to name=amq.topic");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='myEx', type='direct', durable=False)
            session.message_transfer(destination="myEx", message=Message(props,"Test"))
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow message transfer to exchange myEx with routing key rk1");


        props = session.delivery_properties(routing_key="rk2")
        try:
            session.message_transfer(destination="amq.direct", message=Message(props,"Test"))
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow message transfer to exchange amq.direct with routing key rk2");


    def test_publish_acl_deny_mode(self):
        """
        Test various publish acl
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID publish exchange name=amq.direct routingkey=rk1\n')
        aclf.write('acl allow bob@QPID publish exchange name=amq.topic\n')
        aclf.write('acl allow bob@QPID publish exchange name=myEx routingkey=rk2\n')
        aclf.write('acl allow bob@QPID create exchange\n')
        aclf.write('acl allow anonymous all all \n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        props = session.delivery_properties(routing_key="rk2")

        try:
            session.message_transfer(destination="amq.direct", message=Message(props,"Test"))
            self.fail("ACL should deny message transfer to name=amq.direct routingkey=rk2");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.message_transfer(destination="amq.topic", message=Message(props,"Test"))
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow message transfer to exchange amq.topic with any routing key");

        try:
            session.exchange_declare(exchange='myEx', type='direct', durable=False)
            session.message_transfer(destination="myEx", message=Message(props,"Test"))
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow message transfer to exchange myEx with routing key=rk2");

        props = session.delivery_properties(routing_key="rk1")

        try:
            session.message_transfer(destination="myEx", message=Message(props,"Test"))
            self.fail("ACL should deny message transfer to name=myEx routingkey=rk1");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.message_transfer(destination="amq.direct", message=Message(props,"Test"))
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow message transfer to exchange amq.direct with routing key rk1");

   #=====================================
   # ACL broker configuration tests
   #=====================================

    def test_broker_timestamp_config(self):
        """
        Test ACL control of the broker timestamp configuration
        """
        aclf = self.get_acl_file()
        # enable lots of stuff to allow QMF to work
        aclf.write('acl allow all create exchange\n')
        aclf.write('acl allow all access exchange\n')
        aclf.write('acl allow all bind exchange\n')
        aclf.write('acl allow all publish exchange\n')
        aclf.write('acl allow all create queue\n')
        aclf.write('acl allow all access queue\n')
        aclf.write('acl allow all delete queue\n')
        aclf.write('acl allow all consume queue\n')
        aclf.write('acl allow all access method\n')
        # this should let bob access the timestamp configuration
        aclf.write('acl allow bob@QPID access broker\n')
        aclf.write('acl allow admin@QPID all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        ts = None
        bob = BrokerAdmin(self.config.broker, "bob", "bob")
        ts = bob.get_timestamp_cfg() #should work
        bob.set_timestamp_cfg(ts);   #should work

        obo = BrokerAdmin(self.config.broker, "obo", "obo")
        try:
            ts = obo.get_timestamp_cfg() #should fail
            failed = False
        except Exception, e:
            failed = True
            self.assertEqual(7,e.args[0]["error_code"])
            assert e.args[0]["error_text"].find("unauthorized-access") == 0
        assert(failed)

        try:
            obo.set_timestamp_cfg(ts) #should fail
            failed = False
        except Exception, e:
            failed = True
            self.assertEqual(7,e.args[0]["error_code"])
            assert e.args[0]["error_text"].find("unauthorized-access") == 0
        assert(failed)

        admin = BrokerAdmin(self.config.broker, "admin", "admin")
        ts = admin.get_timestamp_cfg() #should pass
        admin.set_timestamp_cfg(ts) #should pass



   #=====================================
   # QMF Functional tests
   #=====================================

    def test_qmf_functional_tests(self):
        """
        Test using QMF method hooks into ACL logic
        """
        aclf = self.get_acl_file()
        aclf.write('group admins moe@COMPANY.COM \\\n')
        aclf.write('             larry@COMPANY.COM \\\n')
        aclf.write('             curly@COMPANY.COM \\\n')
        aclf.write('             shemp@COMPANY.COM\n')
        aclf.write('group auditors aaudit@COMPANY.COM baudit@COMPANY.COM caudit@COMPANY.COM \\\n')
        aclf.write('               daudit@COMPANY.COM eaduit@COMPANY.COM eaudit@COMPANY.COM\n')
        aclf.write('group tatunghosts tatung01@COMPANY.COM \\\n')
        aclf.write('      tatung02/x86.build.company.com@COMPANY.COM \\\n')
        aclf.write('      tatung03/x86.build.company.com@COMPANY.COM \\\n')
        aclf.write('      tatung04/x86.build.company.com@COMPANY.COM \n')
        aclf.write('group publishusers publish@COMPANY.COM x-pubs@COMPANY.COM\n')
        aclf.write('acl allow-log admins all all\n')
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        aclf.write('acl allow-log auditors all exchange name=company.topic routingkey=private.audit.*\n')
        aclf.write('acl allow-log tatunghosts  publish exchange name=company.topic  routingkey=tatung.*\n')
        aclf.write('acl allow-log tatunghosts  publish exchange name=company.direct routingkey=tatung-service-queue\n')
        aclf.write('acl allow-log publishusers create queue\n')
        aclf.write('acl allow-log publishusers publish exchange name=qpid.management routingkey=broker\n')
        aclf.write('acl allow-log publishusers publish exchange name=qmf.default.topic routingkey=*\n')
        aclf.write('acl allow-log publishusers publish exchange name=qmf.default.direct routingkey=*\n')
        aclf.write('acl allow-log all bind exchange name=company.topic  routingkey=tatung.*\n')
        aclf.write('acl allow-log all bind exchange name=company.direct routingkey=tatung-service-queue\n')
        aclf.write('acl allow-log all consume queue\n')
        aclf.write('acl allow-log all access exchange\n')
        aclf.write('acl allow-log all access queue\n')
        aclf.write('acl allow-log all create queue name=tmp.* durable=false autodelete=true exclusive=true policytype=ring\n')
        aclf.write('acl allow mrQ create queue queuemaxsizelowerlimit=100 queuemaxsizeupperlimit=200 queuemaxcountlowerlimit=300 queuemaxcountupperlimit=400\n')
        aclf.write('acl deny-log all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        #
        # define some group lists
        #
        g_admins = ['moe@COMPANY.COM', \
                    'larry@COMPANY.COM', \
                    'curly@COMPANY.COM', \
                    'shemp@COMPANY.COM']

        g_auditors = [ 'aaudit@COMPANY.COM','baudit@COMPANY.COM','caudit@COMPANY.COM', \
                       'daudit@COMPANY.COM','eaduit@COMPANY.COM','eaudit@COMPANY.COM']

        g_tatunghosts = ['tatung01@COMPANY.COM', \
                         'tatung02/x86.build.company.com@COMPANY.COM', \
                         'tatung03/x86.build.company.com@COMPANY.COM', \
                         'tatung04/x86.build.company.com@COMPANY.COM']

        g_publishusers = ['publish@COMPANY.COM', 'x-pubs@COMPANY.COM']

        g_public = ['jpublic@COMPANY.COM', 'me@yahoo.com']

        g_all = g_admins + g_auditors + g_tatunghosts + g_publishusers + g_public

        action_all = ['consume','publish','create','access','bind','unbind','delete','purge','update']

        #
        # Run some tests verifying against users who are in and who are out of given groups.
        #

        for u in g_admins:
            self.Lookup(u, "create", "queue", "anything", {"durable":"true"}, "allow-log")

        uInTest = g_auditors + g_admins
        uOutTest = self.AllBut(g_all, uInTest)

        for u in uInTest:
            self.LookupPublish(u, "company.topic", "private.audit.This", "allow-log")

        for u in uInTest:
            for a in action_all:
                self.Lookup(u, a, "exchange", "company.topic", {"routingkey":"private.audit.This"}, "allow-log")

        for u in uOutTest:
            self.LookupPublish(u, "company.topic", "private.audit.This", "deny-log")
            self.Lookup(u, "bind", "exchange", "company.topic", {"routingkey":"private.audit.This"}, "deny-log")

        uInTest = g_admins + g_tatunghosts
        uOutTest = self.AllBut(g_all, uInTest)

        for u in uInTest:
            self.LookupPublish(u, "company.topic",  "tatung.this2",         "allow-log")
            self.LookupPublish(u, "company.direct", "tatung-service-queue", "allow-log")

        for u in uOutTest:
            self.LookupPublish(u, "company.topic",  "tatung.this2",         "deny-log")
            self.LookupPublish(u, "company.direct", "tatung-service-queue", "deny-log")

        for u in uOutTest:
            for a in ["bind", "access"]:
                self.Lookup(u, a, "exchange", "company.topic",  {"routingkey":"tatung.this2"},         "allow-log")
                self.Lookup(u, a, "exchange", "company.direct", {"routingkey":"tatung-service-queue"}, "allow-log")

        uInTest = g_admins + g_publishusers
        uOutTest = self.AllBut(g_all, uInTest)

        for u in uInTest:
            self.LookupPublish(u, "qpid.management",    "broker",   "allow-log")
            self.LookupPublish(u, "qmf.default.topic",  "this3",    "allow-log")
            self.LookupPublish(u, "qmf.default.direct", "this4",    "allow-log")

        for u in uOutTest:
            self.LookupPublish(u, "qpid.management",    "broker",   "deny-log")
            self.LookupPublish(u, "qmf.default.topic",  "this3",    "deny-log")
            self.LookupPublish(u, "qmf.default.direct", "this4",    "deny-log")

        for u in uOutTest:
            for a in ["bind"]:
                self.Lookup(u, a, "exchange", "qpid.management",    {"routingkey":"broker"}, "deny-log")
                self.Lookup(u, a, "exchange", "qmf.default.topic",  {"routingkey":"this3"},  "deny-log")
                self.Lookup(u, a, "exchange", "qmf.default.direct", {"routingkey":"this4"},  "deny-log")
            for a in ["access"]:
                self.Lookup(u, a, "exchange", "qpid.management",    {"routingkey":"broker"}, "allow-log")
                self.Lookup(u, a, "exchange", "qmf.default.topic",  {"routingkey":"this3"},  "allow-log")
                self.Lookup(u, a, "exchange", "qmf.default.direct", {"routingkey":"this4"},  "allow-log")

        # Test against queue size limits

        self.Lookup('mrQ', 'create', 'queue', 'abc', {"maxqueuesize":"150", "maxqueuecount":"350"}, "allow")
        self.Lookup('mrQ', 'create', 'queue', 'def', {"maxqueuesize":"99",  "maxqueuecount":"350"}, "deny")
        self.Lookup('mrQ', 'create', 'queue', 'uvw', {"maxqueuesize":"201", "maxqueuecount":"350"}, "deny")
        self.Lookup('mrQ', 'create', 'queue', 'xyz', {"maxqueuesize":"150", "maxqueuecount":"299"}, "deny")
        self.Lookup('mrQ', 'create', 'queue', '',    {"maxqueuesize":"150", "maxqueuecount":"401"}, "deny")
        self.Lookup('mrQ', 'create', 'queue', '',    {"maxqueuesize":"0",   "maxqueuecount":"401"}, "deny")
        self.Lookup('mrQ', 'create', 'queue', '',    {"maxqueuesize":"150", "maxqueuecount":"0"  }, "deny")


   #=====================================
   # Connection limits
   #=====================================

    def test_connection_limits(self):
        """
        Test ACL control connection limits
        """
        # By username should be able to connect twice per user
        try:
            sessiona1 = self.get_session_by_port('alice','alice', self.port_u())
            sessiona2 = self.get_session_by_port('alice','alice', self.port_u())
        except Exception, e:
            self.fail("Could not create two connections for user alice: " + str(e))

        # Third session should fail
        try:
            sessiona3 = self.get_session_by_port('alice','alice', self.port_u())
            self.fail("Should not be able to create third connection for user alice")
        except Exception, e:
            result = None

        try:
            sessionb1 = self.get_session_by_port('bob','bob', self.port_u())
            sessionb2 = self.get_session_by_port('bob','bob', self.port_u())
        except Exception, e:
            self.fail("Could not create two connections for user bob: " + str(e))

        try:
            sessionb3 = self.get_session_by_port('bob','bob', self.port_u())
            self.fail("Should not be able to create third connection for user bob")
        except Exception, e:
            result = None

        # By IP address should be able to connect twice per client address
        try:
            sessionb1 = self.get_session_by_port('alice','alice', self.port_i())
            sessionb2 = self.get_session_by_port('bob','bob', self.port_i())
        except Exception, e:
            self.fail("Could not create two connections for client address: " + str(e))

        # Third session should fail
        try:
            sessionb3 = self.get_session_by_port('charlie','charlie', self.port_i())
            self.fail("Should not be able to create third connection for client address")
        except Exception, e:
            result = None


class BrokerAdmin:
    def __init__(self, broker, username=None, password=None):
        self.connection = qpid.messaging.Connection(broker)
        if username:
            self.connection.username = username
            self.connection.password = password
            self.connection.sasl_mechanisms = "PLAIN"
        self.connection.open()
        self.session = self.connection.session()
        self.sender = self.session.sender("qmf.default.direct/broker")
        self.reply_to = "responses-#; {create:always}"
        self.receiver = self.session.receiver(self.reply_to)

    def invoke(self, method, arguments):
        content = {
            "_object_id": {"_object_name": "org.apache.qpid.broker:broker:amqp-broker"},
            "_method_name": method,
            "_arguments": arguments
            }
        request = qpid.messaging.Message(reply_to=self.reply_to, content=content)
        request.properties["x-amqp-0-10.app-id"] = "qmf2"
        request.properties["qmf.opcode"] = "_method_request"
        self.sender.send(request)
        response = self.receiver.fetch()
        self.session.acknowledge()
        if response.properties['x-amqp-0-10.app-id'] == 'qmf2':
            if response.properties['qmf.opcode'] == '_method_response':
                return response.content['_arguments']
            elif response.properties['qmf.opcode'] == '_exception':
                raise Exception(response.content['_values'])
            else: raise Exception("Invalid response received, unexpected opcode: %s" % response.properties['qmf.opcode'])
        else: raise Exception("Invalid response received, not a qmfv2 method: %s" % response.properties['x-amqp-0-10.app-id'])
    def create_exchange(self, name, exchange_type=None, options={}):
        properties = options
        if exchange_type: properties["exchange_type"] = exchange_type
        self.invoke("create", {"type": "exchange", "name":name, "properties":properties})

    def create_queue(self, name, properties={}):
        self.invoke("create", {"type": "queue", "name":name, "properties":properties})

    def delete_exchange(self, name):
        self.invoke("delete", {"type": "exchange", "name":name})

    def delete_queue(self, name):
        self.invoke("delete", {"type": "queue", "name":name})

    def get_timestamp_cfg(self):
        return self.invoke("getTimestampConfig", {})

    def set_timestamp_cfg(self, receive):
        return self.invoke("getTimestampConfig", {"receive":receive})
