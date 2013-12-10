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

from qpid.messaging import *
from qpid.tests.messaging import Base
from qpidtoollibs.broker import EventHelper
import math

class EventTests (Base):
    """
    Test various qmf events
    """
    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_queue_declare(self):
        helper = EventHelper()

        # subscribe for queue declare events
        rcv = self.ssn.receiver(helper.eventAddress("org.apache.qpid.broker", "queueDeclare"))
        # create a queue
        snd = self.ssn.sender("myq; {create:always, delete:always}")
        # ensure we got an event
        event = helper.event(rcv.fetch(timeout=1))
        assert event.name, "org_apache_qpid_broker:queueDeclare"
        assert event.qName, "myq"

    def test_queue_delete(self):
        helper = EventHelper()

        rcv = self.ssn.receiver(helper.eventAddress("org.apache.qpid.broker", "queueDelete"))
        snd = self.ssn.sender("myq; {create:always, delete:always}")
        snd.close()

        event = helper.event(rcv.fetch(timeout=1))
        assert event.name, "org_apache_qpid_broker:queueDelete"
        assert event.qName, "myq"

    def test_queue_autodelete_exclusive(self):
        helper = EventHelper()

        rcv = self.ssn.receiver(helper.eventAddress("org.apache.qpid.broker", "queueDelete"))

        #create new session
        ssn2 = self.setup_session()
        snd = ssn2.sender("myq; {create:always, node:{x-declare:{auto-delete:True, exclusive:True}}}")
        ssn2.close()

        event = helper.event(rcv.fetch(timeout=5))
        assert event.name, "org_apache_qpid_broker:queueDelete"
        assert event.qName, "myq"

    def test_queue_autodelete_shared(self):
        helper = EventHelper()

        rcv = self.ssn.receiver(helper.eventAddress("org.apache.qpid.broker", "queueDelete"))

        rcv2 = self.ssn.receiver("myq; {create:always, node:{x-declare:{auto-delete:True}}}")
        rcv2.close()

        event = helper.event(rcv.fetch(timeout=5))
        assert event.name, "org_apache_qpid_broker:queueDelete"
        assert event.qName, "myq"

