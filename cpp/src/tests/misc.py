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

from qpid.tests.messaging.implementation import *
from qpid.tests.messaging import VersionTest

class MiscellaneousTests (VersionTest):
    """
    Tests for various aspects of qpidd behaviour
    """
    def test_exclusive(self):
        con = self.create_connection("amqp1.0", True)
        rcv = con.session().receiver("q; {create:always, node:{properties:{exclusive:True,auto-delete:True}}}")

        other = self.create_connection("amqp1.0", True)
        try:
            #can send to the queue
            snd = other.session().sender("q")

            #can browse the queue
            browser = other.session().receiver("q; {mode:browse}")

            #can't consume from the queue
            try:
                consumer = other.session().receiver("q")
                assert False, ("Should not be able to consume from exclusively owned queue")
            except LinkError, e: None
            try:
                exclusive = other.session().receiver("q; {create: always, node:{properties:{exclusive:True}}}")
                assert False, ("Should not be able to consume exclusively from exclusively owned queue")
            except LinkError, e: None
        finally:
            rcv.close()
            con.close()
            other.close()

