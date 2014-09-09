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
from qpidtoollibs import BrokerAgent

class ACLFile:
    def __init__(self, policy='data_dir/policy.acl'):
        self.f = open(policy,'w')

    def write(self,line):
        self.f.write(line)

    def close(self):
        self.f.close()

class ACLTests(TestBase010):

    # required for testing QMF methods
    def get_messaging_connection(self, user, passwd):
        parms = {'username':user, 'password':passwd, 'sasl_mechanisms':'PLAIN'}
        brokerurl="%s:%s" %(self.broker.host, self.broker.port)
        connection = qpid.messaging.Connection(brokerurl, **parms)
        connection.open()
        return connection

    # For connection limit tests this function
    #  throws if the connection won't start
    #  returns a connection that the caller can close if he likes.
    def get_connection(self, user, passwd):
        socket = connect(self.broker.host, self.broker.port)
        connection = Connection (sock=socket, username=user, password=passwd,
                                 mechanism="PLAIN")
        connection.start()
        return connection

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

    def port_q(self):
        return int(self.defines["port-q"])

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
            suffix = ', [ERROR: Expected= ' + expectedResult
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

    def test_nested_groups(self):
        """
        Test nested groups
        """

        aclf = self.get_acl_file()
        aclf.write('group user-consume martin@QPID ted@QPID\n')
        aclf.write('group group2 kim@QPID user-consume rob@QPID \n')
        aclf.write('acl allow anonymous all all \n')
        aclf.write('acl allow group2 create queue \n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('rob','rob')
        try:
            session.queue_declare(queue="rob_queue")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request");
            self.fail("Error during queue create request");



    def test_user_realm(self):
        """
        Test a user defined without a realm
        Ex. group admin rajith
        Note: a user name without a realm is interpreted as a group name
        """
        aclf = self.get_acl_file()
        aclf.write('group admin bob\n') # shouldn't be allowed
        aclf.write('acl deny admin bind exchange\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result.find("not defined yet.",0,len(result)) == -1):
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
        expected = "ding is not a valid value for 'policytype', possible values are one of"
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


    def test_illegal_filemaxsize_upper_limit_spec(self):
        """
        Test illegal file policy
        """
        #
        # Use filemaxsizeupperlimit
        #
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxsizeupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'filemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxsizeupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'filemaxsizeupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)



    def test_illegal_filemaxcount_upper_limit_spec(self):
        """
        Test illegal file policy
        """
        #
        # use maxfilecountupperlimit
        #
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxcountupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'filemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxcountupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'filemaxcountupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_filemaxsize_lower_limit_spec(self):
        """
        Test illegal file policy
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxsizelowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'filemaxsizelowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxsizelowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'filemaxsizelowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)



    def test_illegal_filemaxcount_lower_limit_spec(self):
        """
        Test illegal file policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxcountlowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'filemaxcountlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 filemaxcountlowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'filemaxcountlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_pages_lower_limit_spec(self):
        """
        Test illegal paged queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pageslowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'pageslowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pageslowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'pageslowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_pages_upper_limit_spec(self):
        """
        Test illegal paged queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagesupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'pagesupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagesupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'pagesupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_pagefactor_lower_limit_spec(self):
        """
        Test illegal paged queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagefactorlowerlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'pagefactorlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagefactorlowerlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'pagefactorlowerlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)


    def test_illegal_pagefactor_upper_limit_spec(self):
        """
        Test illegal paged queue policy
        """

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagefactorupperlimit=-1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "-1 is not a valid value for 'pagefactorupperlimit', " \
                   "values should be between 0 and 9223372036854775807";
        if (result.find(expected) == -1):
            self.fail(result)

        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=q2 pagefactorupperlimit=9223372036854775808\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        expected = "9223372036854775808 is not a valid value for 'pagefactorupperlimit', " \
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
        aclf.write('acl deny bob@QPID delete queue name=q4\n')
        aclf.write('acl deny bob@QPID create queue name=q5 maxqueuesize=1000 maxqueuecount=100\n')
        aclf.write('acl deny bob@QPID create queue name=q6 paging=true\n')
        aclf.write('acl deny bob@QPID purge queue name=q7\n')
        aclf.write('acl deny bob@QPID move queue name=q7\n')
        aclf.write('acl deny bob@QPID move queue name=q8 queuename=q7\n')
        aclf.write('acl deny bob@QPID redirect queue name=q7\n')
        aclf.write('acl deny bob@QPID redirect queue name=q8 queuename=q7\n')
        aclf.write('acl deny bob@QPID reroute queue name=q7\n')
        aclf.write('acl deny bob@QPID reroute queue name=q8 exchangename=amq.fanout\n')
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
            queue_options["qpid.paging"] = True
            session.queue_declare(queue="q6", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=q6, qpid.paging=True");
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

        # some queues needs to be created for testing purge / move / reroute / redirect
        session.queue_declare(queue="q7")
        session.queue_declare(queue="q8")
        session.queue_declare(queue="q9")
        try:
            session.queue_purge(queue="q7")
            self.fail("ACL should deny queue purge request for q7");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_purge(queue="q8")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue purge request for q8");

        # as we use QMF methods, it is easier to use BrokerAgent from messaging.connection and not use session object as above
        broker_agent = BrokerAgent(self.get_messaging_connection('bob','bob'))

        try:
            broker_agent.queueMoveMessages("q7", "q8", 0)
            self.fail("ACL should deny queue move request from q7 to q8");
        except Exception, e:
            self.assertTrue("'error_code': 7" in e.args[0])
            broker_agent = BrokerAgent(self.get_messaging_connection('bob','bob'))

        try:
            broker_agent.queueMoveMessages("q8", "q9", 0)
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue move request from q8 to q9");

        try:
            broker_agent.queueMoveMessages("q9", "q8", 0)
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue move request from q9 to q8");

        try:
            broker_agent.Redirect("q7", "q8")
            self.fail("ACL should deny queue redirect request from q7 to q8");
        except Exception, e:
            self.assertTrue("'error_code': 7" in e.args[0])
            broker_agent = BrokerAgent(self.get_messaging_connection('bob','bob'))

        try:
            broker_agent.Redirect("q8", "q9")
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue redirect request from q8 to q9");

        try:
            broker_agent.Redirect("q9", "q8")
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue redirect request from q9 to q8");

        try:
            broker_agent.getQueue('q7').reroute(0, False, "amq.fanout")
            self.fail("ACL should deny queue reroute request from q7 to amq.fanout");
        except Exception, e:
            self.assertTrue("'error_code': 7" in e.args[0])
            broker_agent = BrokerAgent(self.get_messaging_connection('bob','bob'))

        try:
            broker_agent.getQueue('q8').reroute(0, False, "amq.fanout")
            self.fail("ACL should deny queue reroute request from q8 to amq.fanout");
        except Exception, e:
            self.assertTrue("'error_code': 7" in e.args[0])
            broker_agent = BrokerAgent(self.get_messaging_connection('bob','bob'))

        try:
            broker_agent.getQueue('q8').reroute(0, False, "amq.direct")
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue reroute request from q8 to amq.direct");

        try:
            broker_agent.getQueue('q9').reroute(0, False, "amq.fanout")
        except Exception, e:
            if ("'error_code': 7" in e.args[0]):
                self.fail("ACL should allow queue reroute request from q9 to amq.fanout");

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
        aclf.write('acl allow bob@QPID create queue name=q7 policytype=self-destruct\n')
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
            session.queue_declare(queue="q7", arguments={"qpid.policy_type": "ring"})
            self.fail("ACL should not allow queue create request for q7 with policytype=ring");
        except qpid.session.SessionException, e:
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="q7", arguments={"qpid.policy_type": "self-destruct"})
        except qpid.session.SessionException, e:
            self.fail("ACL should allow queue create request for q7 with policytype=self-destruct");

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
   # ACL paged tests
   #=====================================

    def test_paged_allow_mode(self):
        """
        Test cases for paged acl in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID create queue name=qf1 pageslowerlimit=1000\n')
        aclf.write('acl deny bob@QPID create queue name=qf2 pagesupperlimit=100\n')
        aclf.write('acl deny bob@QPID create queue name=qf3 pagefactorlowerlimit=10\n')
        aclf.write('acl deny bob@QPID create queue name=qf4 pagefactorupperlimit=1\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.max_pages_loaded"] = 500
            session.queue_declare(queue="qf1", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qf1, qpid.paging=True, qpid.max_pages_loaded=500");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.max_pages_loaded"] = 500
            session.queue_declare(queue="qf2", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qf2, qpid.paging=True, qpid.max_pages_loaded=500");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.page_factor"] = 5
            session.queue_declare(queue="qf3", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qf3, qpid.paging=True, qpid.page_factor=5");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.page_factor"] = 5
            session.queue_declare(queue="qf4", arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qf4, qpid.paging=True, qpid.page_factor=5");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')


    def test_paged_deny_mode(self):
        """
        Test cases for paged acl in deny mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID create queue name=qf1 pageslowerlimit=100 pagesupperlimit=1000\n')
        aclf.write('acl allow bob@QPID create queue name=qf2 pagefactorlowerlimit=1 pagefactorupperlimit=10\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.max_pages_loaded"] = 500
            session.queue_declare(queue="qf1", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qf1, qpid.paging=True, qpid.max_pages_loaded=500");
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.paging"] = True
            queue_options["qpid.page_factor"] = 5
            session.queue_declare(queue="qf2", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qf2, qpid.paging=True, qpid.page_factor=5");
            session = self.get_session('bob','bob')


   #=====================================
   # ACL file tests
   #=====================================

    def test_file_allow_mode(self):
        """
        Test cases for file acl in allow mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny bob@QPID access queue name=qf1\n')
        aclf.write('acl deny bob@QPID create queue name=qf1 durable=true\n')
        aclf.write('acl deny bob@QPID create queue name=qf2 exclusive=true policytype=ring\n')
        aclf.write('acl deny bob@QPID access queue name=qf3\n')
        aclf.write('acl deny bob@QPID purge queue name=qf3\n')
        aclf.write('acl deny bob@QPID delete queue name=qf4\n')
        aclf.write('acl deny bob@QPID create queue name=qf5 filemaxsizeupperlimit=1000 filemaxcountupperlimit=100\n')
        aclf.write('acl deny bob@QPID create queue name=ABCDE queuemaxsizelowerlimit=900000 queuemaxsizeupperlimit=1024000 queuemaxcountlowerlimit=900 queuemaxcountupperlimit=2000 filemaxsizelowerlimit=0 filemaxsizeupperlimit=32 filemaxcountlowerlimit=0 filemaxcountupperlimit=4 policytype=ring durable=false autodelete=true\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "queue", "ABCDE", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"0",
                                                             "maxfilecount":"0" }, "deny")

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 200
            queue_options["qpid.file_size"] = 500
            session.queue_declare(queue="qf5", exclusive=True, durable=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qf5, qpid.file_size=500 and qpid.file_count=200");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 200
            queue_options["qpid.file_size"] = 100
            session.queue_declare(queue="qf2", exclusive=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qf2, qpid.file_size=100 and qpid.file_count=200 ");


    def test_file_deny_mode(self):
        """
        Test cases for queue acl in deny mode
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID access queue name=qfd1\n')
        aclf.write('acl allow bob@QPID create queue name=qfd1 durable=true\n')
        aclf.write('acl allow bob@QPID create queue name=qfd2 exclusive=true policytype=ring\n')
        aclf.write('acl allow bob@QPID access queue name=qfd3\n')
        aclf.write('acl allow bob@QPID purge queue name=qfd3\n')
        aclf.write('acl allow bob@QPID create queue name=qfd3\n')
        aclf.write('acl allow bob@QPID create queue name=qfd4\n')
        aclf.write('acl allow bob@QPID delete queue name=qfd4\n')
        aclf.write('acl allow bob@QPID create queue name=qfd5 filemaxsizeupperlimit=1000 filemaxcountupperlimit=100\n')
        aclf.write('acl allow bob@QPID create queue name=qfd6 filemaxsizelowerlimit=50 filemaxsizeupperlimit=100 filemaxcountlowerlimit=50 filemaxcountupperlimit=100\n')
        aclf.write('acl allow bob@QPID create queue name=ABCDE queuemaxsizelowerlimit=900000 queuemaxsizeupperlimit=1024000 queuemaxcountlowerlimit=900 queuemaxcountupperlimit=2000 filemaxsizelowerlimit=0 filemaxsizeupperlimit=32 filemaxcountlowerlimit=0 filemaxcountupperlimit=4 policytype=ring durable=false autodelete=true\n')
        aclf.write('acl allow bob@QPID create queue name=FGHIJ queuemaxsizelowerlimit=900000 queuemaxsizeupperlimit=1024000 queuemaxcountlowerlimit=900 queuemaxcountupperlimit=2000 filemaxsizelowerlimit=2 filemaxsizeupperlimit=32 filemaxcountlowerlimit=0 filemaxcountupperlimit=4 policytype=ring durable=false autodelete=true\n')
        aclf.write('acl allow bob@QPID create queue name=KLMNO queuemaxsizelowerlimit=900000 queuemaxsizeupperlimit=1024000 queuemaxcountlowerlimit=900 queuemaxcountupperlimit=2000 filemaxsizelowerlimit=0 filemaxsizeupperlimit=0 filemaxcountlowerlimit=0 filemaxcountupperlimit=4 policytype=ring durable=false autodelete=true\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "queue", "ABCDE", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"0",
                                                             "maxfilecount":"0" }, "allow")

        self.Lookup("bob@QPID", "create", "queue", "FGHIJ", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"1",
                                                             "maxfilecount":"0" }, "deny")

        self.Lookup("bob@QPID", "create", "queue", "FGHIJ", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"2",
                                                             "maxfilecount":"0" }, "allow")

        self.Lookup("bob@QPID", "create", "queue", "FGHIJ", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"32",
                                                             "maxfilecount":"0" }, "allow")

        self.Lookup("bob@QPID", "create", "queue", "FGHIJ", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"33",
                                                             "maxfilecount":"0" }, "deny")

        self.Lookup("bob@QPID", "create", "queue", "KLMNO", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"0",
                                                             "maxfilecount":"0" }, "allow")

        self.Lookup("bob@QPID", "create", "queue", "KLMNO", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"17",
                                                             "maxfilecount":"0" }, "allow")

        self.Lookup("bob@QPID", "create", "queue", "KLMNO", {"durable":"false",
                                                             "autodelete":"true",
                                                             "exclusive":"false",
                                                             "alternate":"",
                                                             "policytype":"ring",
                                                             "maxqueuesize":"1024000",
                                                             "maxqueuecount":"1000",
                                                             "maxfilesize":"33",
                                                             "maxfilecount":"0" }, "allow")

        try:
            session.queue_declare(queue="qfd1", durable=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qfd1 durable=true");

        try:
            session.queue_declare(queue="qfd1", durable=True, passive=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue passive declare request with name=qfd1 durable=true passive=true");

        try:
            session.queue_declare(queue="qfd1", durable=False, passive=False)
            self.fail("ACL should deny queue create request with name=qfd1 durable=true passive=false");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qfd2", exclusive=False)
            self.fail("ACL should deny queue create request with name=qfd2 exclusive=false");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 200
            queue_options["qpid.file_size"] = 500
            session.queue_declare(queue="qfd5", durable=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qfd5 filemaxsizeupperlimit=500 filemaxcountupperlimit=200");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 100
            queue_options["qpid.file_size"] = 500
            session.queue_declare(queue="qfd5", durable=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qfd5 filemaxsizeupperlimit=500 filemaxcountupperlimit=200");

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 49
            queue_options["qpid.file_size"] = 100
            session.queue_declare(queue="qfd6", durable=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qfd6 filemaxsizeupperlimit=100 filemaxcountupperlimit=49");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 101
            queue_options["qpid.file_size"] = 100
            session.queue_declare(queue="qfd6", durable=True, arguments=queue_options)
            self.fail("ACL should allow queue create request with name=qfd6 filemaxsizeupperlimit=100 filemaxcountupperlimit=101");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 100
            queue_options["qpid.file_size"] = 49
            session.queue_declare(queue="qfd6", durable=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qfd6 filemaxsizeupperlimit=49 filemaxcountupperlimit=100");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 100
            queue_options["qpid.file_size"] =101
            session.queue_declare(queue="qfd6", durable=True, arguments=queue_options)
            self.fail("ACL should deny queue create request with name=qfd6 filemaxsizeupperlimit=101 filemaxcountupperlimit=100");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.file_count"] = 50
            queue_options["qpid.file_size"] = 50
            session.queue_declare(queue="qfd6", durable=True, arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qfd6 filemaxsizeupperlimit=50 filemaxcountupperlimit=50");


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

    def test_qmf_query(self):
        aclf = self.get_acl_file()
        aclf.write('acl allow all access exchange\n')
        aclf.write('acl allow all bind exchange\n')
        aclf.write('acl allow all create queue\n')
        aclf.write('acl allow all access queue\n')
        aclf.write('acl allow all delete queue\n')
        aclf.write('acl allow all consume queue\n')
        aclf.write('acl allow all access method\n')
        aclf.write('acl allow bob@QPID access query name=org.apache.qpid.broker:queue:q1\n')
        aclf.write('acl allow bob@QPID access query schemaclass=exchange\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        bob = BrokerAdmin(self.config.broker, "bob", "bob")

        try:
            bob.query(object_name="org.apache.qpid.broker:queue:q1")
        except Exception, e:
            if ("unauthorized-access:" in e.args[0]):
                self.fail("ACL should allow queue QMF query for q1");

        try:
            bob.query(object_name="org.apache.qpid.broker:queue:q2")
            self.fail("ACL should deny queue QMF query for q2");
        except Exception, e:
            self.assertTrue("unauthorized-access:" in e.args[0])
            bob = BrokerAdmin(self.config.broker, "bob", "bob")

        try:
            bob.query(class_name="binding")
            self.fail("ACL should deny class binding QMF query");
        except Exception, e:
            self.assertTrue("unauthorized-access:" in e.args[0])
            bob = BrokerAdmin(self.config.broker, "bob", "bob")

        try:
            bob.query(class_name="exchange")
        except Exception, e:
            if ("unauthorized-access:" in e.args[0]):
                self.fail("ACL should allow class exchange QMF query");


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
            session.message_subscribe(queue='q3', destination='myq3')
            session.message_cancel(destination='myq3')
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
        aclf.write("acl deny bob@QPID publish exchange name=amq.default routingkey=restricted\n")
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

        self.LookupPublish("bob@QPID", "", "restricted", "deny")
        self.LookupPublish("bob@QPID", "", "another",    "allow")
        self.LookupPublish("joe@QPID", "", "restricted", "allow")


    def test_publish_acl_deny_mode(self):
        """
        Test various publish acl
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID publish exchange name=amq.direct routingkey=rk1\n')
        aclf.write('acl allow bob@QPID publish exchange name=amq.topic\n')
        aclf.write('acl allow bob@QPID publish exchange name=myEx routingkey=rk2\n')
        aclf.write('acl allow bob@QPID create exchange\n')
        aclf.write("acl allow bob@QPID publish exchange name=amq.default routingkey=unrestricted\n")
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

        self.LookupPublish("bob@QPID", "", "unrestricted", "allow")
        self.LookupPublish("bob@QPID", "", "another",      "deny")
        self.LookupPublish("joe@QPID", "", "unrestricted", "deny")


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
        aclf.write('acl allow bob@QPID update broker\n')
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
            for a in ['bind', 'unbind', 'access', 'publish']:
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
   # Routingkey lookup using Topic Exchange tests
   #=====================================

    def test_topic_exchange_publish_tests(self):
        """
        Test using QMF method hooks into ACL logic
        """
        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        aclf.write('acl allow-log uPlain1@COMPANY   publish exchange name=X routingkey=ab.cd.e\n')
        aclf.write('acl allow-log uPlain2@COMPANY   publish exchange name=X routingkey=.\n')
        aclf.write('acl allow-log uStar1@COMPANY    publish exchange name=X routingkey=a.*.b\n')
        aclf.write('acl allow-log uStar2@COMPANY    publish exchange name=X routingkey=*.x\n')
        aclf.write('acl allow-log uStar3@COMPANY    publish exchange name=X routingkey=x.x.*\n')
        aclf.write('acl allow-log uHash1@COMPANY    publish exchange name=X routingkey=a.#.b\n')
        aclf.write('acl allow-log uHash2@COMPANY    publish exchange name=X routingkey=a.#\n')
        aclf.write('acl allow-log uHash3@COMPANY    publish exchange name=X routingkey=#.a\n')
        aclf.write('acl allow-log uHash4@COMPANY    publish exchange name=X routingkey=a.#.b.#.c\n')
        aclf.write('acl allow-log uMixed1@COMPANY   publish exchange name=X routingkey=*.x.#.y\n')
        aclf.write('acl allow-log uMixed2@COMPANY   publish exchange name=X routingkey=a.#.b.*\n')
        aclf.write('acl allow-log uMixed3@COMPANY   publish exchange name=X routingkey=*.*.*.#\n')

        aclf.write('acl allow-log all publish exchange name=X routingkey=MN.OP.Q\n')
        aclf.write('acl allow-log all publish exchange name=X routingkey=M.*.N\n')
        aclf.write('acl allow-log all publish exchange name=X routingkey=M.#.N\n')
        aclf.write('acl allow-log all publish exchange name=X routingkey=*.M.#.N\n')

        aclf.write('acl deny-log all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        #                                  aclKey: "ab.cd.e"
        self.LookupPublish("uPlain1@COMPANY", "X", "ab.cd.e",   "allow-log")
        self.LookupPublish("uPlain1@COMPANY", "X", "abx.cd.e",  "deny-log")
        self.LookupPublish("uPlain1@COMPANY", "X", "ab.cd",     "deny-log")
        self.LookupPublish("uPlain1@COMPANY", "X", "ab.cd..e.", "deny-log")
        self.LookupPublish("uPlain1@COMPANY", "X", "ab.cd.e.",  "deny-log")
        self.LookupPublish("uPlain1@COMPANY", "X", ".ab.cd.e",  "deny-log")
        #                                  aclKey: "."
        self.LookupPublish("uPlain2@COMPANY", "X", ".",         "allow-log")

        #                                  aclKey: "a.*.b"
        self.LookupPublish("uStar1@COMPANY", "X", "a.xx.b",   "allow-log")
        self.LookupPublish("uStar1@COMPANY", "X", "a.b",      "deny-log")
        #                                  aclKey: "*.x"
        self.LookupPublish("uStar2@COMPANY", "X", "y.x",      "allow-log")
        self.LookupPublish("uStar2@COMPANY", "X", ".x",       "allow-log")
        self.LookupPublish("uStar2@COMPANY", "X", "x",        "deny-log")
        #                                  aclKey: "x.x.*"
        self.LookupPublish("uStar3@COMPANY", "X", "x.x.y",      "allow-log")
        self.LookupPublish("uStar3@COMPANY", "X", "x.x.",       "allow-log")
        self.LookupPublish("uStar3@COMPANY", "X", "x.x",        "deny-log")
        self.LookupPublish("uStar3@COMPANY", "X", "q.x.y",      "deny-log")

        #                                  aclKey: "a.#.b"
        self.LookupPublish("uHash1@COMPANY", "X", "a.b",         "allow-log")
        self.LookupPublish("uHash1@COMPANY", "X", "a.x.b",       "allow-log")
        self.LookupPublish("uHash1@COMPANY", "X", "a..x.y.zz.b", "allow-log")
        self.LookupPublish("uHash1@COMPANY", "X", "a.b.",        "deny-log")
        self.LookupPublish("uHash1@COMPANY", "X", "q.x.b",       "deny-log")

        #                                  aclKey: "a.#"
        self.LookupPublish("uHash2@COMPANY", "X", "a",         "allow-log")
        self.LookupPublish("uHash2@COMPANY", "X", "a.b",       "allow-log")
        self.LookupPublish("uHash2@COMPANY", "X", "a.b.c",     "allow-log")

        #                                  aclKey: "#.a"
        self.LookupPublish("uHash3@COMPANY", "X", "a",         "allow-log")
        self.LookupPublish("uHash3@COMPANY", "X", "x.y.a",     "allow-log")

        #                                  aclKey: "a.#.b.#.c"
        self.LookupPublish("uHash4@COMPANY", "X", "a.b.c",         "allow-log")
        self.LookupPublish("uHash4@COMPANY", "X", "a.x.b.y.c",     "allow-log")
        self.LookupPublish("uHash4@COMPANY", "X", "a.x.x.b.y.y.c", "allow-log")

        #                                  aclKey: "*.x.#.y"
        self.LookupPublish("uMixed1@COMPANY", "X", "a.x.y",          "allow-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.x.p.qq.y",     "allow-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.a.x.y",        "deny-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "aa.x.b.c",       "deny-log")

        #                                  aclKey: "a.#.b.*"
        self.LookupPublish("uMixed2@COMPANY", "X", "a.b.x",          "allow-log")
        self.LookupPublish("uMixed2@COMPANY", "X", "a.x.x.x.b.x",    "allow-log")

        #                                  aclKey: "*.*.*.#"
        self.LookupPublish("uMixed3@COMPANY", "X", "x.y.z",          "allow-log")
        self.LookupPublish("uMixed3@COMPANY", "X", "x.y.z.a.b.c",    "allow-log")
        self.LookupPublish("uMixed3@COMPANY", "X", "x.y",            "deny-log")
        self.LookupPublish("uMixed3@COMPANY", "X", "x",              "deny-log")

        # Repeat the keys with wildcard user spec
        self.LookupPublish("uPlain1@COMPANY", "X", "MN.OP.Q",        "allow-log")
        self.LookupPublish("uStar1@COMPANY" , "X", "M.xx.N",         "allow-log")
        self.LookupPublish("uHash1@COMPANY" , "X", "M.N",            "allow-log")
        self.LookupPublish("uHash1@COMPANY" , "X", "M.x.N",          "allow-log")
        self.LookupPublish("uHash1@COMPANY" , "X", "M..x.y.zz.N",    "allow-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.M.N",          "allow-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.M.p.qq.N",     "allow-log")

        self.LookupPublish("dev@QPID", "X", "MN.OP.Q",        "allow-log")
        self.LookupPublish("dev@QPID", "X", "M.xx.N",         "allow-log")
        self.LookupPublish("dev@QPID", "X", "M.N",            "allow-log")
        self.LookupPublish("dev@QPID", "X", "M.x.N",          "allow-log")
        self.LookupPublish("dev@QPID", "X", "M..x.y.zz.N",    "allow-log")
        self.LookupPublish("dev@QPID", "X", "a.M.N",          "allow-log")
        self.LookupPublish("dev@QPID", "X", "a.M.p.qq.N",     "allow-log")

    def test_topic_exchange_other_tests(self):
        """
        Test using QMF method hooks into ACL logic
        """
        action_list = ['access','bind','unbind']

        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        for action in action_list:
            aclf.write('acl allow-log uPlain1@COMPANY   ' + action + ' exchange name=X routingkey=ab.cd.e\n')
            aclf.write('acl allow-log uPlain2@COMPANY   ' + action + ' exchange name=X routingkey=.\n')
            aclf.write('acl allow-log uStar1@COMPANY    ' + action + ' exchange name=X routingkey=a.*.b\n')
            aclf.write('acl allow-log uStar2@COMPANY    ' + action + ' exchange name=X routingkey=*.x\n')
            aclf.write('acl allow-log uStar3@COMPANY    ' + action + ' exchange name=X routingkey=x.x.*\n')
            aclf.write('acl allow-log uHash1@COMPANY    ' + action + ' exchange name=X routingkey=a.#.b\n')
            aclf.write('acl allow-log uHash2@COMPANY    ' + action + ' exchange name=X routingkey=a.#\n')
            aclf.write('acl allow-log uHash3@COMPANY    ' + action + ' exchange name=X routingkey=#.a\n')
            aclf.write('acl allow-log uHash4@COMPANY    ' + action + ' exchange name=X routingkey=a.#.b.#.c\n')
            aclf.write('acl allow-log uMixed1@COMPANY   ' + action + ' exchange name=X routingkey=*.x.#.y\n')
            aclf.write('acl allow-log uMixed2@COMPANY   ' + action + ' exchange name=X routingkey=a.#.b.*\n')
            aclf.write('acl allow-log uMixed3@COMPANY   ' + action + ' exchange name=X routingkey=*.*.*.#\n')

            aclf.write('acl allow-log all ' + action + ' exchange name=X routingkey=MN.OP.Q\n')
            aclf.write('acl allow-log all ' + action + ' exchange name=X routingkey=M.*.N\n')
            aclf.write('acl allow-log all ' + action + ' exchange name=X routingkey=M.#.N\n')
            aclf.write('acl allow-log all ' + action + ' exchange name=X routingkey=*.M.#.N\n')

        aclf.write('acl deny-log all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        for action in action_list:
            #                                  aclKey: "ab.cd.e"
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd.e"},        "allow-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd.e"},        "allow-log")

            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd.e"},        "allow-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"abx.cd.e"},       "deny-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd"},          "deny-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd..e."},      "deny-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"ab.cd.e."},       "deny-log")
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":".ab.cd.e"},       "deny-log")
            #                                  aclKey: "."
            self.Lookup("uPlain2@COMPANY", action, "exchange", "X", {"routingkey":"."},              "allow-log")

            #                                  aclKey: "a.*.b"
            self.Lookup("uStar1@COMPANY",  action, "exchange", "X", {"routingkey":"a.xx.b"},         "allow-log")
            self.Lookup("uStar1@COMPANY",  action, "exchange", "X", {"routingkey":"a.b"},            "deny-log")
            #                                  aclKey: "*.x"
            self.Lookup("uStar2@COMPANY",  action, "exchange", "X", {"routingkey":"y.x"},            "allow-log")
            self.Lookup("uStar2@COMPANY",  action, "exchange", "X", {"routingkey":".x"},             "allow-log")
            self.Lookup("uStar2@COMPANY",  action, "exchange", "X", {"routingkey":"x"},              "deny-log")
            #                                  aclKey: "x.x.*"
            self.Lookup("uStar3@COMPANY",  action, "exchange", "X", {"routingkey":"x.x.y"},          "allow-log")
            self.Lookup("uStar3@COMPANY",  action, "exchange", "X", {"routingkey":"x.x."},           "allow-log")
            self.Lookup("uStar3@COMPANY",  action, "exchange", "X", {"routingkey":"x.x"},            "deny-log")
            self.Lookup("uStar3@COMPANY",  action, "exchange", "X", {"routingkey":"q.x.y"},          "deny-log")

            #                                  aclKey: "a.#.b"
            self.Lookup("uHash1@COMPANY",  action, "exchange", "X", {"routingkey":"a.b"},            "allow-log")
            self.Lookup("uHash1@COMPANY",  action, "exchange", "X", {"routingkey":"a.x.b"},          "allow-log")
            self.Lookup("uHash1@COMPANY",  action, "exchange", "X", {"routingkey":"a..x.y.zz.b"},    "allow-log")
            self.Lookup("uHash1@COMPANY",  action, "exchange", "X", {"routingkey":"a.b."},           "deny-log")
            self.Lookup("uHash1@COMPANY",  action, "exchange", "X", {"routingkey":"q.x.b"},          "deny-log")

            #                                  aclKey: "a.#"
            self.Lookup("uHash2@COMPANY",  action, "exchange", "X", {"routingkey":"a"},              "allow-log")
            self.Lookup("uHash2@COMPANY",  action, "exchange", "X", {"routingkey":"a.b"},            "allow-log")
            self.Lookup("uHash2@COMPANY",  action, "exchange", "X", {"routingkey":"a.b.c"},          "allow-log")

            #                                  aclKey: "#.a"
            self.Lookup("uHash3@COMPANY",  action, "exchange", "X", {"routingkey":"a"},              "allow-log")
            self.Lookup("uHash3@COMPANY",  action, "exchange", "X", {"routingkey":"x.y.a"},          "allow-log")

            #                                  aclKey: "a.#.b.#.c"
            self.Lookup("uHash4@COMPANY",  action, "exchange", "X", {"routingkey":"a.b.c"},          "allow-log")
            self.Lookup("uHash4@COMPANY",  action, "exchange", "X", {"routingkey":"a.x.b.y.c"},      "allow-log")
            self.Lookup("uHash4@COMPANY",  action, "exchange", "X", {"routingkey":"a.x.x.b.y.y.c"},  "allow-log")

            #                                  aclKey: "*.x.#.y"
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"a.x.y"},          "allow-log")
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"a.x.p.qq.y"},     "allow-log")
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"a.a.x.y"},        "deny-log")
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"aa.x.b.c"},       "deny-log")

            #                                  aclKey: "a.#.b.*"
            self.Lookup("uMixed2@COMPANY", action, "exchange", "X", {"routingkey":"a.b.x"},          "allow-log")
            self.Lookup("uMixed2@COMPANY", action, "exchange", "X", {"routingkey":"a.x.x.x.b.x"},    "allow-log")

            #                                  aclKey: "*.*.*.#"
            self.Lookup("uMixed3@COMPANY", action, "exchange", "X", {"routingkey":"x.y.z"},          "allow-log")
            self.Lookup("uMixed3@COMPANY", action, "exchange", "X", {"routingkey":"x.y.z.a.b.c"},    "allow-log")
            self.Lookup("uMixed3@COMPANY", action, "exchange", "X", {"routingkey":"x.y"},            "deny-log")
            self.Lookup("uMixed3@COMPANY", action, "exchange", "X", {"routingkey":"x"},              "deny-log")

            # Repeat the keys with wildcard user spec
            self.Lookup("uPlain1@COMPANY", action, "exchange", "X", {"routingkey":"MN.OP.Q"},        "allow-log")
            self.Lookup("uStar1@COMPANY" , action, "exchange", "X", {"routingkey":"M.xx.N"},         "allow-log")
            self.Lookup("uHash1@COMPANY" , action, "exchange", "X", {"routingkey":"M.N"},            "allow-log")
            self.Lookup("uHash1@COMPANY" , action, "exchange", "X", {"routingkey":"M.x.N"},          "allow-log")
            self.Lookup("uHash1@COMPANY" , action, "exchange", "X", {"routingkey":"M..x.y.zz.N"},    "allow-log")
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"a.M.N"},          "allow-log")
            self.Lookup("uMixed1@COMPANY", action, "exchange", "X", {"routingkey":"a.M.p.qq.N"},     "allow-log")

            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "MN.OP.Q"},      "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "M.xx.N"},       "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "M.N"},          "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "M.x.N"},        "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "M..x.y.zz.N"},  "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "a.M.N"},        "allow-log")
            self.Lookup("dev@QPID",        action, "exchange", "X", {"routingkey":  "a.M.p.qq.N"},   "allow-log")

   #=====================================
   # Connection limits
   #=====================================

    def test_connection_limits_cli_sets_all(self):

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



    def test_connection_limits_by_named_user(self):
        """
        Test ACL control connection limits
        """
        aclf = self.get_acl_file()
        aclf.write('quota connections 2 aliceCL@QPID bobCL@QPID\n')
        aclf.write('quota connections 0 evildude@QPID\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # By username should be able to connect twice per user
        try:
            conna1 = self.get_connection('aliceCL','aliceCL')
            conna2 = self.get_connection('aliceCL','aliceCL')
        except Exception, e:
            self.fail("Could not create two connections for user aliceCL: " + str(e))

        # Third session should fail
        try:
            conna3 = self.get_connection('aliceCL','aliceCL')
            self.fail("Should not be able to create third connection for user aliceCL")
        except Exception, e:
            result = None

        # Disconnecting should allow another session.
        conna1.close()
        try:
            conna3 = self.get_connection('aliceCL','aliceCL')
        except Exception, e:
            self.fail("Could not recreate second connection for user aliceCL: " + str(e))

        # By username should be able to connect twice per user
        try:
            connb1 = self.get_connection('bobCL','bobCL')
            connb2 = self.get_connection('bobCL','bobCL')
        except Exception, e:
            self.fail("Could not create two connections for user bobCL: " + str(e))

        # Third session should fail
        try:
            connb3 = self.get_connection('bobCL','bobCL')
            self.fail("Should not be able to create third connection for user bobCL")
        except Exception, e:
            result = None


        # User with quota of 0 is denied
        try:
            conne1 = self.get_connection('evildude','evildude')
            self.fail("Should not be able to create a connection for user evildude")
        except Exception, e:
            result = None


        # User not named in quotas is denied
        try:
            connc1 = self.get_connection('charlie','charlie')
            self.fail("Should not be able to create a connection for user charlie")
        except Exception, e:
            result = None

        # Clean up the connections
        conna2.close()
        conna3.close()
        connb1.close()
        connb2.close()



    def test_connection_limits_by_unnamed_all(self):
        """
        Test ACL control connection limits
        """
        aclf = self.get_acl_file()
        aclf.write('quota connections 2 aliceUA@QPID bobUA@QPID\n')
        aclf.write('quota connections 1 all\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # By username should be able to connect twice per user
        try:
            connectiona1 = self.get_connection('aliceUA','alice')
            connectiona2 = self.get_connection('aliceUA','alice')
        except Exception, e:
            self.fail("Could not create two connections for user alice: " + str(e))

        # Third connection should fail
        try:
            connectiona3 = self.get_connection('aliceUA','alice')
            self.fail("Should not be able to create third connection for user alice")
        except Exception, e:
            result = None

        # By username should be able to connect twice per user
        try:
            connectionb1 = self.get_connection('bobUA','bob')
            connectionb2 = self.get_connection('bobUA','bob')
        except Exception, e:
            self.fail("Could not create two connections for user bob: " + str(e))

        # Third connection should fail
        try:
            connectionb3 = self.get_connection('bobUA','bob')
            self.fail("Should not be able to create third connection for user bob")
        except Exception, e:
            result = None

        # User not named in quotas gets 'all' quota
        try:
            connectionc1 = self.get_connection('charlieUA','charlie')
        except Exception, e:
            self.fail("Could not create one connection for user charlie: " + str(e))

        # Next connection should fail
        try:
            connectionc2 = self.get_connection('charlieUA','charlie')
            self.fail("Should not be able to create second connection for user charlie")
        except Exception, e:
            result = None

        # Clean up the connections
        connectiona1.close()
        connectiona2.close()
        connectionb1.close()
        connectionb2.close()
        connectionc1.close()


    def test_connection_limits_by_group(self):
        """
        Test ACL control connection limits
        """
        aclf = self.get_acl_file()
        aclf.write('group stooges moeGR@QPID larryGR@QPID curlyGR@QPID\n')
        aclf.write('quota connections 2 aliceGR@QPID bobGR@QPID\n')
        aclf.write('quota connections 2 stooges charlieGR@QPID\n')
        aclf.write('# user and groups may be overwritten. Should use last value\n')
        aclf.write('quota connections 3 bobGR@QPID stooges\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # Alice gets 2
        try:
            connectiona1 = self.get_connection('aliceGR','alice')
            connectiona2 = self.get_connection('aliceGR','alice')
        except Exception, e:
            self.fail("Could not create two connections for user alice: " + str(e))

        # Third connection should fail
        try:
            connectiona3 = self.get_connection('aliceGR','alice')
            self.fail("Should not be able to create third connection for user alice")
        except Exception, e:
            result = None

        # Bob gets 3
        try:
            connectionb1 = self.get_connection('bobGR','bob')
            connectionb2 = self.get_connection('bobGR','bob')
            connectionb3 = self.get_connection('bobGR','bob')
        except Exception, e:
            self.fail("Could not create three connections for user bob: " + str(e))

        # Fourth connection should fail
        try:
            connectionb4 = self.get_connection('bobGR','bob')
            self.fail("Should not be able to create fourth connection for user bob")
        except Exception, e:
            result = None

        # Moe gets 3
        try:
            connectionm1 = self.get_connection('moeGR','moe')
            connectionm2 = self.get_connection('moeGR','moe')
            connectionm3 = self.get_connection('moeGR','moe')
        except Exception, e:
            self.fail("Could not create three connections for user moe: " + str(e))

        # Fourth connection should fail
        try:
            connectionb4 = self.get_connection('moeGR','moe')
            self.fail("Should not be able to create fourth connection for user ,pe")
        except Exception, e:
            result = None

        # User not named in quotas is denied
        try:
            connections1 = self.get_connection('shempGR','shemp')
            self.fail("Should not be able to create a connection for user shemp")
        except Exception, e:
            result = None

        # Clean up the connections
        connectiona1.close()
        connectiona2.close()
        connectionb1.close()
        connectionb2.close()
        connectionb3.close()
        connectionm1.close()
        connectionm2.close()
        connectionm3.close()


    def test_connection_limits_by_ip_address(self):
        """
        Test ACL control connection limits by ip address
        """
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

        sessionb1.close()
        sessionb2.close()

   #=====================================
   # User name substitution
   #=====================================

    def test_user_name_substitution(self):
        """
        Test name substitution internals, limits, and edge cases.
        """
        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        aclf.write('acl allow all create queue    name=tmp-${userdomain}\n')
        aclf.write('acl allow all create queue    name=${userdomain}-tmp\n')
        aclf.write('acl allow all create queue    name=tmp-${userdomain}-tmp\n')
        aclf.write('acl allow all create queue    name=tmp-${userdomain}-tmp-${userdomain}\n')
        aclf.write('acl allow all create  queue    name=temp0-${userdomain}\n')
        aclf.write('acl allow all access  queue    name=temp0-${userdomain}\n')
        aclf.write('acl allow all purge   queue    name=temp0-${userdomain}\n')
        aclf.write('acl allow all consume queue    name=temp0-${userdomain}\n')
        aclf.write('acl allow all delete  queue    name=temp0-${userdomain}\n')
        aclf.write('acl allow all create  exchange name=temp0-${userdomain}\n')
        aclf.write('acl allow all access  exchange name=temp0-${userdomain}\n')
        aclf.write('acl allow all bind    exchange name=temp0-${userdomain}\n')
        aclf.write('acl allow all unbind  exchange name=temp0-${userdomain}\n')
        aclf.write('acl allow all delete  exchange name=temp0-${userdomain}\n')
        aclf.write('acl allow all publish exchange name=temp0-${userdomain}\n')

        aclf.write('acl allow all   publish exchange name=X routingkey=${userdomain}.cd.e\n')
        aclf.write('acl allow all   publish exchange name=X routingkey=a.*.${userdomain}\n')
        aclf.write('acl allow all   publish exchange name=X routingkey=b.#.${userdomain}\n')
        aclf.write('acl allow all   publish exchange name=X routingkey=*.${userdomain}.#.y\n')

        aclf.write('acl allow all   create  queue    name=user-${user}\n')
        aclf.write('acl allow all   publish exchange name=U routingkey=${user}.cd.e\n')
        aclf.write('acl allow all   publish exchange name=U routingkey=a.*.${user}\n')
        aclf.write('acl allow all   publish exchange name=U routingkey=b.#.${user}\n')
        aclf.write('acl allow all   publish exchange name=U routingkey=*.${user}.#.y\n')

        aclf.write('acl allow all   create  queue    name=domain-${domain}\n')
        aclf.write('acl allow all   publish exchange name=D routingkey=${domain}.cd.e\n')
        aclf.write('acl allow all   publish exchange name=D routingkey=a.*.${domain}\n')
        aclf.write('acl allow all   publish exchange name=D routingkey=b.#.${domain}\n')
        aclf.write('acl allow all   publish exchange name=D routingkey=*.${domain}.#.y\n')

        # Resolving ${user}_${domain} into ${userdomain} works for everything but routing keys
        aclf.write('acl allow all   create  queue    name=mixed-OK-${user}_${domain}\n')
        # For routing keys ${user}_${domain} will be parsed into ${userdomain}.
        # Routing keys not be found when the rule specifies ${user}_${domain}.
        aclf.write('acl allow all   publish exchange name=NOGO routingkey=${user}_${domain}.cd.e\n')
        # This works since it is does not conflict with ${userdomain}
        aclf.write('acl allow all   publish exchange name=OK   routingkey=${user}___${domain}.cd.e\n')

        aclf.write('acl deny-log all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        self.Lookup("alice@QPID",   "create", "queue", "tmp-alice_QPID",              {}, "allow")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-tmp",                {}, "allow")
        self.Lookup("charlie@QPID", "create", "queue", "tmp-charlie_QPID-tmp",        {}, "allow")
        self.Lookup("dave@QPID",    "create", "queue", "tmp-dave_QPID-tmp-dave_QPID", {}, "allow")
        self.Lookup("ed@BIG.COM",   "create", "queue", "tmp-ed_BIG_COM",              {}, "allow")
        self.Lookup("c.e.r@BIG.GER.COM", "create", "queue", "tmp-c_e_r_BIG_GER_COM",  {}, "allow")
        self.Lookup("c@",           "create", "queue", "tmp-c_",                      {}, "allow")
        self.Lookup("someuser",     "create", "queue", "tmp-someuser",                {}, "allow")

        self.Lookup("alice@QPID",   "create", "queue", "tmp-${user}",                 {}, "deny-log")

        self.Lookup("bob@QPID",     "create", "exchange", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "access", "exchange", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "bind",   "exchange", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "unbind", "exchange", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "delete", "exchange", "temp0-bob_QPID", {}, "allow")
        self.LookupPublish("bob@QPID", "temp0-bob_QPID", "x", "allow")

        self.Lookup("bob@QPID",     "create",  "queue", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "access",  "queue", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "purge",   "queue", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "consume", "queue", "temp0-bob_QPID", {}, "allow")
        self.Lookup("bob@QPID",     "delete",  "queue", "temp0-bob_QPID", {}, "allow")

        self.Lookup("alice@QPID",   "access",  "queue", "temp0-bob_QPID", {}, "deny-log")

        #                                  aclKey: "${userdomain}.cd.e"
        self.LookupPublish("uPlain1@COMPANY", "X", "uPlain1_COMPANY.cd.e",   "allow")
        #                                  aclKey: "a.*.${userdomain}"
        self.LookupPublish("uStar1@COMPANY", "X", "a.xx.uStar1_COMPANY",   "allow")
        self.LookupPublish("uStar1@COMPANY", "X", "a.b",                   "deny-log")
        #                                  aclKey: "b.#.${userdomain}"
        self.LookupPublish("uHash1@COMPANY", "X", "b.uHash1_COMPANY",         "allow")
        self.LookupPublish("uHash1@COMPANY", "X", "b.x.uHash1_COMPANY",       "allow")
        self.LookupPublish("uHash1@COMPANY", "X", "b..x.y.zz.uHash1_COMPANY", "allow")
        self.LookupPublish("uHash1@COMPANY", "X", "b.uHash1_COMPANY.",        "deny-log")
        self.LookupPublish("uHash1@COMPANY", "X", "q.x.uHash1_COMPANY",       "deny-log")
        #                                  aclKey: "*.${userdomain}.#.y"
        self.LookupPublish("uMixed1@COMPANY", "X", "a.uMixed1_COMPANY.y",          "allow")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.uMixed1_COMPANY.p.qq.y",     "allow")
        self.LookupPublish("uMixed1@COMPANY", "X", "a.a.uMixed1_COMPANY.y",        "deny-log")
        self.LookupPublish("uMixed1@COMPANY", "X", "aa.uMixed1_COMPANY.b.c",       "deny-log")
        self.LookupPublish("uMixed1@COMPANY.COM", "X", "a.uMixed1_COMPANY_COM.y",  "allow")


        self.Lookup("bob@QPID",     "create", "queue", "user-bob",                {}, "allow")
        #                                  aclKey: "${user}.cd.e"
        self.LookupPublish("uPlain1@COMPANY", "U", "uPlain1.cd.e",   "allow")
        #                                  aclKey: "a.*.${user}"
        self.LookupPublish("uStar1@COMPANY", "U", "a.xx.uStar1",   "allow")
        self.LookupPublish("uStar1@COMPANY", "U", "a.b",                   "deny-log")
        #                                  aclKey: "b.#.${user}"
        self.LookupPublish("uHash1@COMPANY", "U", "b.uHash1",         "allow")
        self.LookupPublish("uHash1@COMPANY", "U", "b.x.uHash1",       "allow")
        self.LookupPublish("uHash1@COMPANY", "U", "b..x.y.zz.uHash1", "allow")
        self.LookupPublish("uHash1@COMPANY", "U", "b.uHash1.",        "deny-log")
        self.LookupPublish("uHash1@COMPANY", "U", "q.x.uHash1",       "deny-log")
        #                                  aclKey: "*.${user}.#.y"
        self.LookupPublish("uMixed1@COMPANY",     "U", "a.uMixed1.y",          "allow")
        self.LookupPublish("uMixed1@COMPANY",     "U", "a.uMixed1.p.qq.y",     "allow")
        self.LookupPublish("uMixed1@COMPANY",     "U", "a.a.uMixed1.y",        "deny-log")
        self.LookupPublish("uMixed1@COMPANY",     "U", "aa.uMixed1.b.c",       "deny-log")
        self.LookupPublish("uMixed1@COMPANY.COM", "U", "a.uMixed1.y",          "allow")


        self.Lookup("bob@QPID",     "create", "queue", "domain-QPID",                {}, "allow")
        #                                  aclKey: "${domain}.cd.e"
        self.LookupPublish("uPlain1@COMPANY", "D", "COMPANY.cd.e",         "allow")
        #                                  aclKey: "a.*.${domain}"
        self.LookupPublish("uStar1@COMPANY", "D", "a.xx.COMPANY",          "allow")
        self.LookupPublish("uStar1@COMPANY", "D", "a.b",                   "deny-log")
        #                                  aclKey: "b.#.${domain}"
        self.LookupPublish("uHash1@COMPANY", "D", "b.COMPANY",             "allow")
        self.LookupPublish("uHash1@COMPANY", "D", "b.x.COMPANY",           "allow")
        self.LookupPublish("uHash1@COMPANY", "D", "b..x.y.zz.COMPANY",     "allow")
        self.LookupPublish("uHash1@COMPANY", "D", "b.COMPANY.",            "deny-log")
        self.LookupPublish("uHash1@COMPANY", "D", "q.x.COMPANY",           "deny-log")
        #                                  aclKey: "*.${domain}.#.y"
        self.LookupPublish("uMixed1@COMPANY", "D", "a.COMPANY.y",          "allow")
        self.LookupPublish("uMixed1@COMPANY", "D", "a.COMPANY.p.qq.y",     "allow")
        self.LookupPublish("uMixed1@COMPANY", "D", "a.a.COMPANY.y",        "deny-log")
        self.LookupPublish("uMixed1@COMPANY", "D", "aa.COMPANY.b.c",       "deny-log")
        self.LookupPublish("uMixed1@COMPANY.COM", "D", "a.COMPANY_COM.y",  "allow")

        self.Lookup("uPlain1@COMPANY", "create", "queue", "mixed-OK-uPlain1_COMPANY", {}, "allow")
        self.LookupPublish("uPlain1@COMPANY", "NOGO", "uPlain1_COMPANY.cd.e",             "deny-log")
        self.LookupPublish("uPlain1@COMPANY", "OK",   "uPlain1___COMPANY.cd.e",           "allow")


   #=====================================
   # User name substitution details
   #=====================================
   #  User name substitution allows for three flavors of keyword in the Acl file.
   #  Given a user name of bob.user@QPID.COM the keywords are normalized and resolve as follows:
   #   ${userdomain} - bob_user_QPID_COM
   #   ${user}       - bob_user
   #   ${domain}     - QPID_COM
   #
   # The following substitution tests are very similar but differ in the flavor of keyword used
   # in the rules. The tests results using the different keywords differ slightly in how permissive
   # the rules become.
   #   ${userdomain} limits access to one authenticated user
   #   ${user}       limits access to a user name regardless of user's domain
   #   ${domain}     limits access to a domain regardless of user name
   #

    def test_user_name_substitution_userdomain(self):
        """
        Test a setup where users can create, bind, and publish to a main exchange and queue.
        Allow access to a single alternate exchange and queue.
        """
        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        # Create primary queue and exchange:
        #   allow predefined alternate
        #   deny  any other alternate
        #   allow no alternate
        aclf.write('acl allow all create  queue    name=${userdomain}-work alternate=${userdomain}-work2\n')
        aclf.write('acl deny  all create  queue    name=${userdomain}-work alternate=*\n')
        aclf.write('acl allow all create  queue    name=${userdomain}-work\n')
        aclf.write('acl allow all create  exchange name=${userdomain}-work alternate=${userdomain}-work2\n')
        aclf.write('acl deny  all create  exchange name=${userdomain}-work alternate=*\n')
        aclf.write('acl allow all create  exchange name=${userdomain}-work\n')
        # Create backup queue and exchange
        #   Deny any alternate
        aclf.write('acl deny  all create  queue    name=${userdomain}-work2 alternate=*\n')
        aclf.write('acl allow all create  queue    name=${userdomain}-work2\n')
        aclf.write('acl deny  all create  exchange name=${userdomain}-work2 alternate=*\n')
        aclf.write('acl allow all create  exchange name=${userdomain}-work2\n')
        # Bind/unbind primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${userdomain}-work routingkey=${userdomain} queuename=${userdomain}-work\n')
        aclf.write('acl allow all unbind  exchange name=${userdomain}-work routingkey=${userdomain} queuename=${userdomain}-work\n')
        # Bind/unbind backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${userdomain}-work2 routingkey=${userdomain} queuename=${userdomain}-work2\n')
        aclf.write('acl allow all unbind  exchange name=${userdomain}-work2 routingkey=${userdomain} queuename=${userdomain}-work2\n')
        # Access primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${userdomain}-work routingkey=${userdomain} queuename=${userdomain}-work\n')
        # Access backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${userdomain}-work2 routingkey=${userdomain} queuename=${userdomain}-work2\n')
        # Publish primary exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${userdomain}-work routingkey=${userdomain}\n')
        # Publish backup exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${userdomain}-work2 routingkey=${userdomain}\n')
        # deny mode
        aclf.write('acl deny all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # create queues
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work",    {},                             "allow")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work2",   {},                             "allow")
        self.Lookup("bob@QPID",     "create", "queue", "joe_QPID-work",    {},                             "deny")
        self.Lookup("bob@QPID",     "create", "queue", "joe_QPID-work2",   {},                             "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work3",   {},                             "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work",    {"alternate":"bob_QPID-work2"}, "allow")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work",    {"alternate":"joe_QPID-work2"}, "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob_QPID-work2",   {"alternate":"someexchange"},   "deny")
        # create exchanges
        self.Lookup("bob@QPID",     "create", "exchange", "bob_QPID-work", {},                             "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "bob_QPID-work2",{},                             "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "joe_QPID-work", {},                             "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "joe_QPID-work2",{},                             "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "bob_QPID-work3",{},                             "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "bob_QPID-work", {"alternate":"bob_QPID-work2"}, "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "bob_QPID-work2",{"alternate":"someexchange"},   "deny")
        # bind/unbind/access
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {},                                                     "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID"},                              "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {                         "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "allow")
        self.Lookup("bob@QPID", "bind", "exchange", "joe_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work"}, "deny")

        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {},                                                      "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID"},                               "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {                         "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "allow")
        self.Lookup("bob@QPID", "bind", "exchange", "joe_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work2"}, "deny")

        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {},                                                     "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID"},                              "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {                         "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "allow")
        self.Lookup("bob@QPID", "unbind", "exchange", "joe_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work"}, "deny")

        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {},                                                      "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID"},                               "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {                         "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "allow")
        self.Lookup("bob@QPID", "unbind", "exchange", "joe_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work2"}, "deny")

        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {},                                                     "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID"},                              "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {                         "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "allow")
        self.Lookup("bob@QPID", "access", "exchange", "joe_QPID-work", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work"}, "deny")

        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {},                                                      "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID"},                               "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {                         "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "allow")
        self.Lookup("bob@QPID", "access", "exchange", "joe_QPID-work2", {"routingkey":"bob_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {"routingkey":"joe_QPID", "queuename":"bob_QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob_QPID-work2", {"routingkey":"bob_QPID", "queuename":"joe_QPID-work2"}, "deny")
        # publish
        self.LookupPublish("bob@QPID", "bob_QPID-work",  "bob_QPID",        "allow")
        self.LookupPublish("bob@QPID", "bob_QPID-work2", "bob_QPID",        "allow")
        self.LookupPublish("bob@QPID", "joe_QPID-work",  "bob_QPID",        "deny")
        self.LookupPublish("bob@QPID", "joe_QPID-work2", "bob_QPID",        "deny")
        self.LookupPublish("bob@QPID", "bob_QPID-work",  "joe_QPID",        "deny")
        self.LookupPublish("bob@QPID", "bob_QPID-work2", "joe_QPID",        "deny")


    def test_user_name_substitution_user(self):
        """
        Test a setup where users can create, bind, and publish to a main exchange and queue.
        Allow access to a single backup exchange and queue.
        """
        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        # Create primary queue and exchange
        #   allow predefined alternate
        #   deny  any other alternate
        #   allow no alternate
        aclf.write('acl allow all create  queue    name=${user}-work alternate=${user}-work2\n')
        aclf.write('acl deny  all create  queue    name=${user}-work alternate=*\n')
        aclf.write('acl allow all create  queue    name=${user}-work\n')
        aclf.write('acl allow all create  exchange name=${user}-work alternate=${user}-work2\n')
        aclf.write('acl deny  all create  exchange name=${user}-work alternate=*\n')
        aclf.write('acl allow all create  exchange name=${user}-work\n')
        # Create backup queue and exchange
        #   Deny any alternate
        aclf.write('acl deny  all create  queue    name=${user}-work2 alternate=*\n')
        aclf.write('acl allow all create  queue    name=${user}-work2\n')
        aclf.write('acl deny  all create  exchange name=${user}-work2 alternate=*\n')
        aclf.write('acl allow all create  exchange name=${user}-work2\n')
        # Bind/unbind primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${user}-work routingkey=${user} queuename=${user}-work\n')
        aclf.write('acl allow all unbind  exchange name=${user}-work routingkey=${user} queuename=${user}-work\n')
        # Bind/unbind backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${user}-work2 routingkey=${user} queuename=${user}-work2\n')
        aclf.write('acl allow all unbind  exchange name=${user}-work2 routingkey=${user} queuename=${user}-work2\n')
        # Access primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${user}-work routingkey=${user} queuename=${user}-work\n')
        # Access backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${user}-work2 routingkey=${user} queuename=${user}-work2\n')
        # Publish primary exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${user}-work routingkey=${user}\n')
        # Publish backup exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${user}-work2 routingkey=${user}\n')
        # deny mode
        aclf.write('acl deny all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # create queues
        self.Lookup("bob@QPID",     "create", "queue", "bob-work",    {},                          "allow")
        self.Lookup("bob@QPID",     "create", "queue", "bob-work2",   {},                          "allow")
        self.Lookup("bob@QPID",     "create", "queue", "joe-work",    {},                          "deny")
        self.Lookup("bob@QPID",     "create", "queue", "joe-work2",   {},                          "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob-work3",   {},                          "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob-work",    {"alternate":"bob-work2"},   "allow")
        self.Lookup("bob@QPID",     "create", "queue", "bob-work",    {"alternate":"joe-work2"},   "deny")
        self.Lookup("bob@QPID",     "create", "queue", "bob-work2",   {"alternate":"someexchange"},"deny")
        # create exchanges
        self.Lookup("bob@QPID",     "create", "exchange", "bob-work", {},                          "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "bob-work2",{},                          "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "joe-work", {},                          "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "joe-work2",{},                          "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "bob-work3",{},                          "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "bob-work", {"alternate":"bob-work2"},   "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "bob-work2",{"alternate":"someexchange"},"deny")
        # bind/unbind/access
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {},                                           "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {"routingkey":"bob"},                         "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {                    "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {"routingkey":"bob", "queuename":"bob-work"}, "allow")
        self.Lookup("bob@QPID", "bind", "exchange", "joe-work", {"routingkey":"bob", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {"routingkey":"joe", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work", {"routingkey":"bob", "queuename":"joe-work"}, "deny")

        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {},                                            "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {"routingkey":"bob"},                          "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {                    "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "allow")
        self.Lookup("bob@QPID", "bind", "exchange", "joe-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {"routingkey":"joe", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"joe-work2"}, "deny")

        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {},                                           "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {"routingkey":"bob"},                         "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {                    "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {"routingkey":"bob", "queuename":"bob-work"}, "allow")
        self.Lookup("bob@QPID", "unbind", "exchange", "joe-work", {"routingkey":"bob", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {"routingkey":"joe", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work", {"routingkey":"bob", "queuename":"joe-work"}, "deny")

        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {},                                            "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {"routingkey":"bob"},                          "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {                    "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "allow")
        self.Lookup("bob@QPID", "unbind", "exchange", "joe-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {"routingkey":"joe", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"joe-work2"}, "deny")

        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {},                                           "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {"routingkey":"bob"},                         "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {                    "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {"routingkey":"bob", "queuename":"bob-work"}, "allow")
        self.Lookup("bob@QPID", "access", "exchange", "joe-work", {"routingkey":"bob", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {"routingkey":"joe", "queuename":"bob-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work", {"routingkey":"bob", "queuename":"joe-work"}, "deny")

        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {},                                            "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {"routingkey":"bob"},                          "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {                    "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "allow")
        self.Lookup("bob@QPID", "access", "exchange", "joe-work2", {"routingkey":"bob", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {"routingkey":"joe", "queuename":"bob-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "bob-work2", {"routingkey":"bob", "queuename":"joe-work2"}, "deny")
        # publish
        self.LookupPublish("bob@QPID", "bob-work",  "bob",        "allow")
        self.LookupPublish("bob@QPID", "bob-work2", "bob",        "allow")
        self.LookupPublish("bob@QPID", "joe-work",  "bob",        "deny")
        self.LookupPublish("bob@QPID", "joe-work2", "bob",        "deny")
        self.LookupPublish("bob@QPID", "bob-work",  "joe",        "deny")
        self.LookupPublish("bob@QPID", "bob-work2", "joe",        "deny")


    def test_user_name_substitution_domain(self):
        """
        Test a setup where users can create, bind, and publish to a main exchange and queue.
        Allow access to a single backup exchange and queue.
        """
        aclf = self.get_acl_file()
        aclf.write('# begin hack alert: allow anonymous to access the lookup debug functions\n')
        aclf.write('acl allow-log anonymous create  queue\n')
        aclf.write('acl allow-log anonymous all     exchange name=qmf.*\n')
        aclf.write('acl allow-log anonymous all     exchange name=amq.direct\n')
        aclf.write('acl allow-log anonymous all     exchange name=qpid.management\n')
        aclf.write('acl allow-log anonymous access  method   name=*\n')
        aclf.write('# end hack alert\n')
        # Create primary queue and exchange
        #   allow predefined alternate
        #   deny  any other alternate
        #   allow no alternate
        aclf.write('acl allow all create  queue    name=${domain}-work alternate=${domain}-work2\n')
        aclf.write('acl deny  all create  queue    name=${domain}-work alternate=*\n')
        aclf.write('acl allow all create  queue    name=${domain}-work\n')
        aclf.write('acl allow all create  exchange name=${domain}-work alternate=${domain}-work2\n')
        aclf.write('acl deny  all create  exchange name=${domain}-work alternate=*\n')
        aclf.write('acl allow all create  exchange name=${domain}-work\n')
        # Create backup queue and exchange
        #   Deny any alternate
        aclf.write('acl deny  all create  queue    name=${domain}-work2 alternate=*\n')
        aclf.write('acl allow all create  queue    name=${domain}-work2\n')
        aclf.write('acl deny  all create  exchange name=${domain}-work2 alternate=*\n')
        aclf.write('acl allow all create  exchange name=${domain}-work2\n')
        # Bind/unbind primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${domain}-work routingkey=${domain} queuename=${domain}-work\n')
        aclf.write('acl allow all unbind  exchange name=${domain}-work routingkey=${domain} queuename=${domain}-work\n')
        # Bind/unbind backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all bind    exchange name=${domain}-work2 routingkey=${domain} queuename=${domain}-work2\n')
        aclf.write('acl allow all unbind  exchange name=${domain}-work2 routingkey=${domain} queuename=${domain}-work2\n')
        # Access primary exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${domain}-work routingkey=${domain} queuename=${domain}-work\n')
        # Access backup exchange
        #  Use only predefined routingkey and queuename
        aclf.write('acl allow all access  exchange name=${domain}-work2 routingkey=${domain} queuename=${domain}-work2\n')
        # Publish primary exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${domain}-work routingkey=${domain}\n')
        # Publish backup exchange
        #  Use only predefined routingkey
        aclf.write('acl allow all publish exchange name=${domain}-work2 routingkey=${domain}\n')
        # deny mode
        aclf.write('acl deny all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # create queues
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work",    {},                            "allow")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work2",   {},                            "allow")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work3",   {},                            "deny")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work",    {"alternate":"QPID-work2"},    "allow")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work",    {"alternate":"bob_QPID-work2"},"deny")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work",    {"alternate":"joe_QPID-work2"},"deny")
        self.Lookup("bob@QPID",     "create", "queue", "QPID-work2",   {"alternate":"someexchange"},  "deny")
        # create exchanges
        self.Lookup("bob@QPID",     "create", "exchange", "QPID-work", {},                           "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "QPID-work2",{},                           "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "QPID-work3",{},                           "deny")
        self.Lookup("bob@QPID",     "create", "exchange", "QPID-work", {"alternate":"QPID-work2"},   "allow")
        self.Lookup("bob@QPID",     "create", "exchange", "QPID-work2",{"alternate":"someexchange"}, "deny")
        # bind/unbind/access
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work", {},                                             "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work", {"routingkey":"QPID"},                          "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work", {                     "queuename":"QPID-work"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work", {"routingkey":"QPID", "queuename":"QPID-work"}, "allow")

        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work2", {},                                              "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work2", {"routingkey":"QPID"},                           "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work2", {                     "queuename":"QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "bind", "exchange", "QPID-work2", {"routingkey":"QPID", "queuename":"QPID-work2"}, "allow")

        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work", {},                                             "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work", {"routingkey":"QPID"},                          "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work", {                     "queuename":"QPID-work"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work", {"routingkey":"QPID", "queuename":"QPID-work"}, "allow")

        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work2", {},                                              "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work2", {"routingkey":"QPID"},                           "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work2", {                     "queuename":"QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "unbind", "exchange", "QPID-work2", {"routingkey":"QPID", "queuename":"QPID-work2"}, "allow")

        self.Lookup("bob@QPID", "access", "exchange", "QPID-work", {},                                             "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work", {"routingkey":"QPID"},                          "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work", {                     "queuename":"QPID-work"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work", {"routingkey":"QPID", "queuename":"QPID-work"}, "allow")

        self.Lookup("bob@QPID", "access", "exchange", "QPID-work2", {},                                              "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work2", {"routingkey":"QPID"},                           "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work2", {                     "queuename":"QPID-work2"}, "deny")
        self.Lookup("bob@QPID", "access", "exchange", "QPID-work2", {"routingkey":"QPID", "queuename":"QPID-work2"}, "allow")
        # publish
        self.LookupPublish("bob@QPID", "QPID-work",  "QPID",        "allow")
        self.LookupPublish("bob@QPID", "QPID-work2", "QPID",        "allow")
        self.LookupPublish("joe@QPID", "QPID-work",  "QPID",        "allow")
        self.LookupPublish("joe@QPID", "QPID-work2", "QPID",        "allow")

   #=====================================
   # Queue per-user quota
   #=====================================

    def queue_quota(self, user, passwd, count, byPort=None):
        """ Helper method to:
        - create a number of queues (should succeed)
        - create too many queues (should fail)
        - create another queue after deleting a queue (should succeed)
        """

        try:
            if byPort:
                session = self.get_session_by_port(user, passwd, byPort)
            else:
                session = self.get_session(user, passwd)
        except Exception, e:
            self.fail("Unexpected error creating session for %s: %s" % (user, str(e)))

        # Should be able to create count queues per user
        try:
            for i in range(count):
                session.queue_declare(queue="%s%d" % (user, i))
        except Exception, e:
            self.fail("Could not create %s for %s: %s" % ("%s%d" % (user, i), user, str(e)))

        # next queue should fail
        try:
            session.queue_declare(queue="%s%d" % (user, count))
            self.fail("Should not be able to create another queue for user %s" % user)
        except Exception, e:
            if byPort:
                session = self.get_session_by_port(user, passwd, byPort)
            else:
                session = self.get_session(user, passwd)

        if count > 0:
            # Deleting a queue should allow another queue.
            session.queue_delete(queue="%s0" % user)
            try:
                session.queue_declare(queue="%s%d" % (user, count))
            except Exception, e:
                self.fail("Could not recreate additional queue for user %s: %s " % (user, str(e)))

        # Clean up
        for i in range(1, count+1):
            session.queue_delete(queue="%s%d" % (user, i))
        try:
            session.close()
        except Exception, e:
            pass

    def test_queue_per_named_user_quota(self):
        """
        Test ACL queue counting limits per named user.
        """
        aclf = self.get_acl_file()
        aclf.write('quota queues 2 ted@QPID carrol@QPID\n')
        aclf.write('quota queues 1 edward@QPID\n')
        aclf.write('quota queues 0 mick@QPID\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # named users should be able to create specified number of queues
        self.queue_quota("ted", 'ted', 2)
        self.queue_quota("carrol", 'carrol', 2)
        self.queue_quota("edward", 'edward', 1)

        # User with quota of 0 is denied
        self.queue_quota("mick", 'mick', 0)

        # User not named in quotas is denied
        self.queue_quota("dan", 'dan', 0)

    def test_queue_per_user_quota(self):
        """
        Test ACL queue counting limits.
        port_q has a limit of 2
        """
        # bob should be able to create two queues
        self.queue_quota("bob", 'bob', 2, self.port_q())

        # alice should be able to create two queues
        self.queue_quota("alice", 'alice', 2, self.port_q())


    def test_queue_limits_by_unnamed_all(self):
        """
        Test ACL control queue limits
        """
        aclf = self.get_acl_file()
        aclf.write('quota queues 2 aliceQUA@QPID bobQUA@QPID\n')
        aclf.write('quota queues 1 all\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # By username should be able to connect twice per user
        self.queue_quota('aliceQUA', 'alice', 2)
        self.queue_quota('bobQUA', 'bob', 2)

        # User not named in quotas gets 'all' quota
        self.queue_quota('charlieQUA', 'charlie', 1)


    def test_queue_limits_by_group(self):
        """
        Test ACL control queue limits
        """
        aclf = self.get_acl_file()
        aclf.write('group hobbits frodoGR@QPID samGR@QPID merryGR@QPID\n')
        aclf.write('quota queues 2 gandalfGR@QPID aragornGR@QPID\n')
        aclf.write('quota queues 2 hobbits rosieGR@QPID\n')
        aclf.write('# user and groups may be overwritten. Should use last value\n')
        aclf.write('quota queues 3 aragornGR@QPID hobbits\n')
        aclf.write('acl allow all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        # gandalf gets 2
        self.queue_quota('gandalfGR', 'gandalf', 2)

        # aragorn gets 3
        self.queue_quota('aragornGR', 'aragorn', 3)

        # frodo gets 3
        self.queue_quota('frodoGR', 'frodo', 3)

        # User not named in quotas is denied
        self.queue_quota('bilboGR', 'bilbo', 0)

    def test_queue_delete_with_properties(self):
        """
        Test cases for queue delete with properties
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID access queue\n')
        aclf.write('acl allow bob@QPID access exchange\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq1 durable=true\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq2 exclusive=true\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq3 policytype=ring\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq4 durable=false\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq5 exclusive=false\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq6 policytype=reject\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq7 autodelete=true\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq8 autodelete=false\n')
        aclf.write('acl allow bob@QPID create queue name=qdaq9\n')
        aclf.write('acl allow bob@QPID create exchange name=qdae9\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq1 durable=true\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq2 exclusive=true\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq3 policytype=ring\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq4 durable=false\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq5 exclusive=false\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq6 policytype=reject\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq7 autodelete=true\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq8 autodelete=false\n')
        aclf.write('acl deny  bob@QPID delete queue name=qdaq9 alternate=qdaq9a\n')
        aclf.write('acl allow all      access queue\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq1", durable=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq1 durable=true");

        try:
            session.queue_delete(queue="qdaq1")
            self.fail("ACL should deny queue delete request for qdaq1");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq2", exclusive=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq2 exclusive=true");

        try:
            session.queue_delete(queue="qdaq2")
            self.fail("ACL should deny queue delete request for qdaq2");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.policy_type"] = "ring"
            session.queue_declare(queue="qdaq3", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request for qdaq3 with policytype=ring");

        try:
            session.queue_delete(queue="qdaq3")
            self.fail("ACL should deny queue delete request for qdaq3");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq4", durable=False)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq4 durable=false");

        try:
            session.queue_delete(queue="qdaq4")
            self.fail("ACL should deny queue delete request for qdaq4");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq5", exclusive=False)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq5 exclusive=false");

        try:
            session.queue_delete(queue="qdaq5")
            self.fail("ACL should deny queue delete request for qdaq5");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            queue_options = {}
            queue_options["qpid.policy_type"] = "reject"
            session.queue_declare(queue="qdaq6", arguments=queue_options)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request for qdaq6 with policytype=reject");

        try:
            session.queue_delete(queue="qdaq6")
            self.fail("ACL should deny queue delete request for qdaq6");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq7", auto_delete=True)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq7 autodelete=true");

        try:
            session.queue_delete(queue="qdaq7")
            self.fail("ACL should deny queue delete request for qdaq7");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.queue_declare(queue="qdaq8", auto_delete=False)
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq8 autodelete=false");

        try:
            session.queue_delete(queue="qdaq8")
            self.fail("ACL should deny queue delete request for qdaq8");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='qdae9', type='direct')
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=qdae9");

        try:
            session.queue_declare(queue="qdaq9", alternate_exchange="qdae9")
        except qpid.session.SessionException, e:
            if (403 == e.args[0].error_code):
                self.fail("ACL should allow queue create request with name=qdaq9 alternate=qdaq9a");

        try:
            session.queue_delete(queue="qdaq9")
            self.fail("ACL should deny queue delete request for qdaq9");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')


    def test_exchange_delete_with_properties(self):
        """
        Test cases for exchange delete with properties
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID access exchange\n')
        aclf.write('acl allow bob@QPID create exchange\n')
        aclf.write('acl deny  bob@QPID delete exchange name=edae1 durable=true\n')
        aclf.write('acl deny  bob@QPID delete exchange name=edae2 alternate=edae2a\n')
        aclf.write('acl deny  bob@QPID delete exchange type=direct\n')
        aclf.write('acl allow bob@QPID delete exchange type=headers\n')
        aclf.write('acl allow anonymous all all\n')
        aclf.write('acl deny all all')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='edae1', type='direct', durable=True)
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=edae1");

        try:
            session.exchange_delete(exchange="edae1")
            self.fail("ACL should deny exchange delete request for edae1");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='edae2a', type='direct')
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=edae2a");

        try:
            session.exchange_declare(exchange='edae2', type='direct', alternate_exchange='edae2a')
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=edae2");

        try:
            session.exchange_delete(exchange="edae2")
            self.fail("ACL should deny exchange delete request for edae2");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_declare(exchange='edae3d', type='direct')
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=edae3d");

        try:
            session.exchange_declare(exchange='edae3h', type='headers')
        except qpid.session.SessionException, e:
            self.fail("ACL should allow exchange create request with name=eda3h");

        try:
            session.exchange_delete(exchange="edae3d")
            self.fail("ACL should deny exchange delete request for edae3d");
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            session = self.get_session('bob','bob')

        try:
            session.exchange_delete(exchange="edae3h")
        except qpid.session.SessionException, e:
            self.assertEqual(403,e.args[0].error_code)
            self.fail("ACL should allow exchange delete request for edae3h");

    #=====================================
    # 'create connection' tests
    #=====================================
#    def test_connect_mode_file_rejects_two_defaults(self):
#        """
#        Should reject a file with two connect mode statements
#        """
#        aclf = self.get_acl_file()
#        aclf.write('acl allow all create connection host=all\n')
#        aclf.write('acl allow all create connection host=all\n')
#        aclf.close()
#
#        result = self.reload_acl()
#        if (result):
#            pass
#        else:
#            self.fail(result)

    def test_connect_mode_accepts_host_spec_formats(self):
        """
        Should accept host specs of various forms
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow bob@QPID create connection host=all\n')
        aclf.write('acl allow bob@QPID create connection host=1.1.1.1\n')
        aclf.write('acl allow bob@QPID create connection host=1.1.1.1,2.2.2.2\n')
        aclf.write('acl allow bob@QPID create connection host=localhost\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

    def test_connect_mode_allow_all_mode(self):
        """
        Should allow one 'all', 'all'
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow all create connection host=all\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')


    def test_connect_mode_allow_all_localhost(self):
        """
        Should allow 'all' 'localhost'
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow all create connection host=localhost\n')
        aclf.write('acl deny  all create connection host=all\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')


    def test_connect_mode_global_deny(self):
        """
        Should allow 'all' 'localhost'
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow all create connection host=localhost\n')
        aclf.write('acl deny  all create connection host=all\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "connection", "", {"host":"127.0.0.1"}, "allow")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"127.0.0.2"}, "deny")


    def test_connect_mode_global_range(self):
        """
        Should allow 'all' 'localhost'
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow all create connection host=10.0.0.0,10.255.255.255\n')
        aclf.write('acl allow all create connection host=localhost\n')
        aclf.write('acl deny  all create connection host=all\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "connection", "", {"host":"0.0.0.0"},        "deny")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"9.255.255.255"},  "deny")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.0.0"},       "allow")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.255.255.255"}, "allow")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"11.0.0.0"},       "deny")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"255.255.255.255"},"deny")


    def test_connect_mode_nested_ranges(self):
        """
        Tests nested ranges for single user
        """
        aclf = self.get_acl_file()
        aclf.write('acl deny-log  bob@QPID create connection host=10.0.1.0,10.0.1.255\n')
        aclf.write('acl allow-log bob@QPID create connection host=10.0.0.0,10.255.255.255\n')
        aclf.write('acl deny-log  bob@QPID create connection host=all\n')
        aclf.write('acl allow all create connection host=localhost\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "connection", "", {"host":"0.0.0.0"},        "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"9.255.255.255"},  "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.0.0"},       "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.0.255"},     "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.1.0"},       "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.1.255"},     "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.2.0"},       "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.255.255.255"}, "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"11.0.0.0"},       "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"255.255.255.255"},"deny-log")


    def test_connect_mode_user_ranges(self):
        """
        Two user ranges should not interfere with each other
        """
        aclf = self.get_acl_file()
        aclf.write('acl allow-log bob@QPID create connection host=10.0.0.0,10.255.255.255\n')
        aclf.write('acl deny-log  bob@QPID create connection host=all\n')
        aclf.write('acl allow-log cat@QPID create connection host=192.168.0.0,192.168.255.255\n')
        aclf.write('acl deny-log  cat@QPID create connection host=all\n')
        aclf.write('acl allow all create connection host=localhost\n')
        aclf.write('acl allow all all\n')
        aclf.close()

        result = self.reload_acl()
        if (result):
            self.fail(result)

        session = self.get_session('bob','bob')

        self.Lookup("bob@QPID", "create", "connection", "", {"host":"0.0.0.0"},        "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"9.255.255.255"},  "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.0.0.0"},       "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"10.255.255.255"}, "allow-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"11.0.0.0"},       "deny-log")
        self.Lookup("bob@QPID", "create", "connection", "", {"host":"255.255.255.255"},"deny-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"0.0.0.0"},        "deny-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"192.167.255.255"},"deny-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"192.168.0.0"},    "allow-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"192.168.255.255"},"allow-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"192.169.0.0"},    "deny-log")
        self.Lookup("cat@QPID", "create", "connection", "", {"host":"255.255.255.255"},"deny-log")


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

    def query(self, object_name=None, class_name=None):
        content = { "_what": "OBJECT" }
        if object_name is not None:
	  content["_object_id"] = {"_object_name": object_name }
        if class_name is not None:
          content["_schema_id"] = {"_class_name": class_name }
        request = qpid.messaging.Message(reply_to=self.reply_to, content=content)
        request.properties["x-amqp-0-10.app-id"] = "qmf2"
        request.properties["qmf.opcode"] = "_query_request"
        self.sender.send(request)
        response = self.receiver.fetch()
        self.session.acknowledge()
        if response.properties['x-amqp-0-10.app-id'] == 'qmf2':
            if response.properties['qmf.opcode'] == '_query_response':
                return
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
