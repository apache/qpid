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

class Mgmt:
    """
    Simple QMF management utility (qpidtoollibs uses
    qpid.messaging.Message rather than swigged version)
    """
    def __init__(self, conn):
        self.conn = conn
        self.sess = self.conn.session()
        self.reply_to = "qmf.default.topic/direct.%s;{node:{type:topic}, link:{x-declare:{auto-delete:True,exclusive:True}}}" % \
            str(uuid4())
        self.reply_rx = self.sess.receiver(self.reply_to)
        self.reply_rx.capacity = 10
        self.tx = self.sess.sender("qmf.default.direct/broker")
        self.next_correlator = 1

    def close(self):
        self.conn.close()

    def list(self, class_name):
        props = {'method'             : 'request',
                 'qmf.opcode'         : '_query_request',
                 'x-amqp-0-10.app-id' : 'qmf2'}
        correlator = str(self.next_correlator)
        self.next_correlator += 1

        content = {'_what'      : 'OBJECT',
                 '_schema_id' : {'_class_name' : class_name.lower()}}

        message = Message(content, reply_to=self.reply_to, correlation_id=correlator,
                          properties=props, subject="broker")
        self.tx.send(message)


        response = self.reply_rx.fetch(10)
        if response.properties['qmf.opcode'] != '_query_response':
            raise Exception("bad response")
        items = []
        done = False
        while not done:
            for item in response.content:
                items.append(item['_values'])
            if 'partial' in response.properties:
                response = self.reply_rx.fetch(10)
            else:
                done = True
            self.sess.acknowledge()
        return items

    def do_qmf_method(self, method, arguments, addr="org.apache.qpid.broker:broker:amqp-broker", timeout=10):
        props = {'method'             : 'request',
                 'qmf.opcode'         : '_method_request',
                 'x-amqp-0-10.app-id' : 'qmf2'}
        correlator = str(self.next_correlator)
        self.next_correlator += 1

        content = {'_object_id'   : {'_object_name' : addr},
                   '_method_name' : method,
                   '_arguments'   : arguments}

        message = Message(content, reply_to=self.reply_to, correlation_id=correlator,
                          properties=props, subject="broker")
        self.tx.send(message)
        response = self.reply_rx.fetch(timeout)
        self.sess.acknowledge()
        if response.properties['qmf.opcode'] == '_exception':
          raise Exception("Exception from Agent: %r" % response.content['_values'])
        if response.properties['qmf.opcode'] != '_method_response':
          raise Exception("bad response: %r" % response.properties)
        return response.content['_arguments']

    def create(self, _type, name, properties={}):
        return self.do_qmf_method('create', {'type': _type, 'name': name, 'properties': properties})

    def delete(self, _type, name):
        return self.do_qmf_method('delete', {'type': _type, 'name': name})

    def reload_acl_file(self):
        self.do_qmf_method('reloadACLFile', {}, "org.apache.qpid.acl:acl:org.apache.qpid.broker:broker:amqp-broker")
