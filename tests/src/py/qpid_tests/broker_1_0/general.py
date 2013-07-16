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
from qpid.tests.messaging import Base

class GeneralTests (Base):
    """
    Miscellaneous tests for core AMQP 1.0 messaging behaviour.
    """
    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_request_response(self):
        snd_request = self.ssn.sender("#")
        rcv_response = self.ssn.receiver("#")

        #send request
        snd_request.send(Message(reply_to=rcv_response.source, id="a1", content="request"))

        #receive request
        rcv_request = self.ssn.receiver(snd_request.target)
        request = rcv_request.fetch(5)
        assert request.content == "request" and request.id == "a1", request
        #send response
        snd_response = self.ssn.sender(request.reply_to)
        snd_response.send(Message(correlation_id=request.id, content="response"))

        #receive response
        response = rcv_response.fetch(5)
        assert response.content == "response" and response.correlation_id == "a1", response

        self.ssn.acknowledge()
