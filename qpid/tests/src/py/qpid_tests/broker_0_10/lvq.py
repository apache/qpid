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
import math

class LVQTests (Base):
    """
    Test last value queue behaviour
    """ 

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()
        
    def test_simple(self):
        snd = self.ssn.sender("lvq; {create: sender, delete: sender, node: {x-declare:{arguments:{'qpid.last_value_queue_key':lvq-key}}}}",
                              durable=self.durable())
        snd.send(create_message("a", "a-1"))
        snd.send(create_message("b", "b-1"))
        snd.send(create_message("a", "a-2"))
        snd.send(create_message("a", "a-3"))
        snd.send(create_message("c", "c-1"))
        snd.send(create_message("c", "c-2"))

        rcv = self.ssn.receiver("lvq; {mode: browse}")
        assert fetch_all(rcv) == ["b-1", "a-3", "c-2"]

        snd.send(create_message("b", "b-2"))
        assert fetch_all(rcv) == ["b-2"]

        snd.send(create_message("c", "c-3"))
        snd.send(create_message("d", "d-1"))
        assert fetch_all(rcv) == ["c-3", "d-1"]

        snd.send(create_message("b", "b-3"))
        assert fetch_all(rcv) == ["b-3"]

        rcv.close()
        rcv = self.ssn.receiver("lvq; {mode: browse}")
        assert (fetch_all(rcv) == ["a-3", "c-3", "d-1", "b-3"])


def create_message(key, content):
    msg = Message(content=content)
    msg.properties["lvq-key"] = key
    return msg

def fetch_all(rcv):
    content = []
    while True:
        try:
            content.append(rcv.fetch(0).content)
        except Empty:
            break
    return content
