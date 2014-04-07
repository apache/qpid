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

import re
from brokertest import BrokerTest
from qpid.messaging import Empty
from qmf.console import Session

import qpid.messaging, brokertest
brokertest.qm = qpid.messaging             # TODO aconway 2014-04-04: Tests fail with SWIG client.


def store_args(store_dir = None):
    """Return the broker args necessary to load the async store"""
    assert BrokerTest.store_lib
    if store_dir == None:
        return []
    return ["--store-dir", store_dir]

class Qmf:
    """
    QMF functions not yet available in the new QMF API. Remove this and replace with new API when it becomes available.
    """
    def __init__(self, broker):
        self.__session = Session()
        self.__broker = self.__session.addBroker("amqp://localhost:%d"%broker.port())

    def add_exchange(self, exchange_name, exchange_type, alt_exchange_name=None, passive=False, durable=False,
                     arguments = None):
        """Add a new exchange"""
        amqp_session = self.__broker.getAmqpSession()
        if arguments == None:
            arguments = {}
        if alt_exchange_name:
            amqp_session.exchange_declare(exchange=exchange_name, type=exchange_type,
                                          alternate_exchange=alt_exchange_name, passive=passive, durable=durable,
                                          arguments=arguments)
        else:
            amqp_session.exchange_declare(exchange=exchange_name, type=exchange_type, passive=passive, durable=durable,
                                          arguments=arguments)

    def add_queue(self, queue_name, alt_exchange_name=None, passive=False, durable=False, arguments = None):
        """Add a new queue"""
        amqp_session = self.__broker.getAmqpSession()
        if arguments == None:
            arguments = {}
        if alt_exchange_name:
            amqp_session.queue_declare(queue_name, alternate_exchange=alt_exchange_name, passive=passive,
                                       durable=durable, arguments=arguments)
        else:
            amqp_session.queue_declare(queue_name, passive=passive, durable=durable, arguments=arguments)

    def delete_queue(self, queue_name):
        """Delete an existing queue"""
        amqp_session = self.__broker.getAmqpSession()
        amqp_session.queue_delete(queue_name)

    def _query(self, name, _class, package, alt_exchange_name=None):
        """Qmf query function which can optionally look for the presence of an alternate exchange name"""
        try:
            obj_list = self.__session.getObjects(_class=_class, _package=package)
            found = False
            for obj in obj_list:
                if obj.name == name:
                    found = True
                    if alt_exchange_name != None:
                        alt_exch_list = self.__session.getObjects(_objectId=obj.altExchange)
                        if len(alt_exch_list) == 0 or alt_exch_list[0].name != alt_exchange_name:
                            return False
                    break
            return found
        except Exception:
            return False


    def query_exchange(self, exchange_name, alt_exchange_name=None):
        """Test for the presence of an exchange, and optionally whether it has an alternate exchange set to a known
        value."""
        return self._query(exchange_name, "exchange", "org.apache.qpid.broker", alt_exchange_name)

    def query_queue(self, queue_name, alt_exchange_name=None):
        """Test for the presence of an exchange, and optionally whether it has an alternate exchange set to a known
        value."""
        return self._query(queue_name, "queue", "org.apache.qpid.broker", alt_exchange_name)

    def queue_message_count(self, queue_name):
        """Query the number of messages on a queue"""
        queue_list = self.__session.getObjects(_class="queue", _name=queue_name)
        if len(queue_list):
            return queue_list[0].msgDepth

    def queue_empty(self, queue_name):
        """Check if a queue is empty (has no messages waiting)"""
        return self.queue_message_count(queue_name) == 0

    def get_objects(self, target_class, target_package="org.apache.qpid.broker"):
        return self.__session.getObjects(_class=target_class, _package=target_package)


    def close(self):
        self.__session.delBroker(self.__broker)
        self.__session = None


class StoreTest(BrokerTest):
    """
    This subclass of BrokerTest adds some convenience test/check functions
    """

    def _chk_empty(self, queue, receiver):
        """Check if a queue is empty (has no more messages)"""
        try:
            msg = receiver.fetch(timeout=0)
            self.assert_(False, "Queue \"%s\" not empty: found message: %s" % (queue, msg))
        except Empty:
            pass

    @staticmethod
    def make_message(msg_count, msg_size):
        """Make message content. Format: 'abcdef....' followed by 'msg-NNNN', where NNNN is the message count"""
        msg = "msg-%04d" % msg_count
        msg_len = len(msg)
        buff = ""
        if msg_size != None and msg_size > msg_len:
            for index in range(0, msg_size - msg_len):
                if index == msg_size - msg_len - 1:
                    buff += "-"
                else:
                    buff += chr(ord('a') + (index % 26))
        return buff + msg

    # Functions for formatting address strings

    @staticmethod
    def _fmt_csv(string_list, list_braces = None):
        """Format a list using comma-separation. Braces are optionally added."""
        if len(string_list) == 0:
            return ""
        first = True
        str_ = ""
        if list_braces != None:
            str_ += list_braces[0]
        for string in string_list:
            if string != None:
                if first:
                    first = False
                else:
                    str_ += ", "
                str_ += string
        if list_braces != None:
            str_ += list_braces[1]
        return str_

    def _fmt_map(self, string_list):
        """Format a map {l1, l2, l3, ...} from a string list. Each item in the list must be a formatted map
        element('key:val')."""
        return self._fmt_csv(string_list, list_braces="{}")

    def _fmt_list(self, string_list):
        """Format a list [l1, l2, l3, ...] from a string list."""
        return self._fmt_csv(string_list, list_braces="[]")

    def addr_fmt(self, node_name, **kwargs):
        """Generic AMQP to new address formatter. Takes common (but not all) AMQP options and formats an address
        string."""
        # Get keyword args
        node_subject = kwargs.get("node_subject")
        create_policy = kwargs.get("create_policy")
        delete_policy = kwargs.get("delete_policy")
        assert_policy = kwargs.get("assert_policy")
        mode = kwargs.get("mode")
        link = kwargs.get("link", False)
        link_name = kwargs.get("link_name")
        node_type = kwargs.get("node_type")
        durable = kwargs.get("durable", False)
        link_reliability = kwargs.get("link_reliability")
        x_declare_list = kwargs.get("x_declare_list", [])
        x_bindings_list = kwargs.get("x_bindings_list", [])
        x_subscribe_list = kwargs.get("x_subscribe_list", [])

        node_flag = not link and (node_type != None or durable or len(x_declare_list) > 0 or len(x_bindings_list) > 0)
        link_flag = link and (link_name != None or durable or link_reliability != None or len(x_declare_list) > 0 or
                             len(x_bindings_list) > 0 or len(x_subscribe_list) > 0)
        assert not (node_flag and link_flag)

        opt_str_list = []
        if create_policy != None:
            opt_str_list.append("create: %s" % create_policy)
        if delete_policy != None:
            opt_str_list.append("delete: %s" % delete_policy)
        if assert_policy != None:
            opt_str_list.append("assert: %s" % assert_policy)
        if mode != None:
            opt_str_list.append("mode: %s" % mode)
        if node_flag or link_flag:
            node_str_list = []
            if link_name != None:
                node_str_list.append("name: \"%s\"" % link_name)
            if node_type != None:
                node_str_list.append("type: %s" % node_type)
            if durable:
                node_str_list.append("durable: True")
            if link_reliability != None:
                node_str_list.append("reliability: %s" % link_reliability)
            if len(x_declare_list) > 0:
                node_str_list.append("x-declare: %s" % self._fmt_map(x_declare_list))
            if len(x_bindings_list) > 0:
                node_str_list.append("x-bindings: %s" % self._fmt_list(x_bindings_list))
            if len(x_subscribe_list) > 0:
                node_str_list.append("x-subscribe: %s" % self._fmt_map(x_subscribe_list))
            if node_flag:
                opt_str_list.append("node: %s" % self._fmt_map(node_str_list))
            else:
                opt_str_list.append("link: %s" % self._fmt_map(node_str_list))
        addr_str = node_name
        if node_subject != None:
            addr_str += "/%s" % node_subject
        if len(opt_str_list) > 0:
            addr_str += "; %s" % self._fmt_map(opt_str_list)
        return addr_str

    def snd_addr(self, node_name, **kwargs):
        """ Create a send (node) address"""
        # Get keyword args
        topic = kwargs.get("topic")
        topic_flag = kwargs.get("topic_flag", False)
        auto_create = kwargs.get("auto_create", True)
        auto_delete = kwargs.get("auto_delete", False)
        durable = kwargs.get("durable", False)
        exclusive = kwargs.get("exclusive", False)
        ftd_count = kwargs.get("ftd_count")
        ftd_size = kwargs.get("ftd_size")
        policy = kwargs.get("policy", "flow-to-disk")
        exchage_type = kwargs.get("exchage_type")

        create_policy = None
        if auto_create:
            create_policy = "always"
        delete_policy = None
        if auto_delete:
            delete_policy = "always"
        node_type = None
        if topic != None or topic_flag:
            node_type = "topic"
        x_declare_list = ["\"exclusive\": %s" % exclusive]
        if ftd_count != None or ftd_size != None:
            queue_policy = ["\'qpid.policy_type\': %s" % policy]
            if ftd_count:
                queue_policy.append("\'qpid.max_count\': %d" % ftd_count)
            if ftd_size:
                queue_policy.append("\'qpid.max_size\': %d" % ftd_size)
            x_declare_list.append("arguments: %s" % self._fmt_map(queue_policy))
        if exchage_type != None:
            x_declare_list.append("type: %s" % exchage_type)

        return self.addr_fmt(node_name, topic=topic, create_policy=create_policy, delete_policy=delete_policy,
                             node_type=node_type, durable=durable, x_declare_list=x_declare_list)

    def rcv_addr(self, node_name, **kwargs):
        """ Create a receive (link) address"""
        # Get keyword args
        auto_create = kwargs.get("auto_create", True)
        auto_delete = kwargs.get("auto_delete", False)
        link_name = kwargs.get("link_name")
        durable = kwargs.get("durable", False)
        browse = kwargs.get("browse", False)
        exclusive = kwargs.get("exclusive", False)
        binding_list = kwargs.get("binding_list", [])
        ftd_count = kwargs.get("ftd_count")
        ftd_size = kwargs.get("ftd_size")
        policy = kwargs.get("policy", "flow-to-disk")

        create_policy = None
        if auto_create:
            create_policy = "always"
        delete_policy = None
        if auto_delete:
            delete_policy = "always"
        mode = None
        if browse:
            mode = "browse"
        x_declare_list = ["\"exclusive\": %s" % exclusive]
        if ftd_count != None or ftd_size != None:
            queue_policy = ["\'qpid.policy_type\': %s" % policy]
            if ftd_count:
                queue_policy.append("\'qpid.max_count\': %d" % ftd_count)
            if ftd_size:
                queue_policy.append("\'qpid.max_size\': %d" % ftd_size)
            x_declare_list.append("arguments: %s" % self._fmt_map(queue_policy))
        x_bindings_list = []
        for binding in binding_list:
            x_bindings_list.append("{exchange: %s, key: %s}" % binding)
        if durable: reliability = 'at-least-once'
        else: reliability = None
        return self.addr_fmt(node_name, create_policy=create_policy, delete_policy=delete_policy, mode=mode, link=True,
                             link_name=link_name, durable=durable, x_declare_list=x_declare_list,
                             x_bindings_list=x_bindings_list, link_reliability=reliability)

    def check_message(self, broker, queue, exp_msg, transactional=False, empty=False, ack=True, browse=False):
        """Check that a message is on a queue by dequeuing it and comparing it to the expected message"""
        return self.check_messages(broker, queue, [exp_msg], transactional, empty, ack, browse)

    def check_messages(self, broker, queue, exp_msg_list, transactional=False, empty=False, ack=True, browse=False,
                       emtpy_flag=False):
        """Check that messages is on a queue by dequeuing them and comparing them to the expected messages"""
        if emtpy_flag:
            num_msgs = 0
        else:
            num_msgs = len(exp_msg_list)
        ssn = broker.connect().session(transactional=transactional)
        rcvr = ssn.receiver(self.rcv_addr(queue, browse=browse), capacity=num_msgs)
        if num_msgs > 0:
            try:
                recieved_msg_list = [rcvr.fetch(timeout=0) for i in range(num_msgs)]
            except Empty:
                self.assert_(False, "Queue \"%s\" is empty, unable to retrieve expected message %d." % (queue, i))
            for i in range(0, len(recieved_msg_list)):
                self.assertEqual(recieved_msg_list[i].content, exp_msg_list[i].content)
                self.assertEqual(recieved_msg_list[i].correlation_id, exp_msg_list[i].correlation_id)
        if empty:
            self._chk_empty(queue, rcvr)
        if ack:
            ssn.acknowledge()
            if transactional:
                ssn.commit()
            ssn.connection.close()
        else:
            if transactional:
                ssn.commit()
            return ssn


    # Functions for finding strings in the broker log file (or other files)

    @staticmethod
    def _read_file(file_name):
        """Returns the content of file named file_name as a string"""
        file_handle = file(file_name)
        try:
            return file_handle.read()
        finally:
            file_handle.close()

    def _get_hits(self, broker, search):
        """Find all occurrences of the search in the broker log (eliminating possible duplicates from msgs on multiple
        queues)"""
        # TODO: Use sets when RHEL-4 is no longer supported
        hits = []
        for hit in search.findall(self._read_file(broker.log)):
            if hit not in hits:
                hits.append(hit)
        return hits

    def _reconsile_hits(self, broker, ftd_msgs, release_hits):
        """Remove entries from list release_hits if they match the message id in ftd_msgs. Check for remaining
        release_hits."""
        for msg in ftd_msgs:
            found = False
            for hit in release_hits:
                if str(msg.id) in hit:
                    release_hits.remove(hit)
                    #print "Found %s in %s" % (msg.id, broker.log)
                    found = True
                    break
            if not found:
                self.assert_(False, "Unable to locate released message %s in log %s" % (msg.id, broker.log))
        if len(release_hits) > 0:
            err = "Messages were unexpectedly released in log %s:\n" % broker.log
            for hit in release_hits:
                err += "  %s\n" % hit
            self.assert_(False, err)

    def check_msg_release(self, broker, ftd_msgs):
        """ Check for 'Content released' messages in broker log for messages in ftd_msgs"""
        hits = self._get_hits(broker, re.compile("debug Message id=\"[0-9a-f-]{36}\"; pid=0x[0-9a-f]+: "
                                                 "Content released$", re.MULTILINE))
        self._reconsile_hits(broker, ftd_msgs, hits)

    def check_msg_release_on_commit(self, broker, ftd_msgs):
        """ Check for 'Content released on commit' messages in broker log for messages in ftd_msgs"""
        hits = self._get_hits(broker, re.compile("debug Message id=\"[0-9a-f-]{36}\"; pid=0x[0-9a-f]+: "
                                                 "Content released on commit$", re.MULTILINE))
        self._reconsile_hits(broker, ftd_msgs, hits)

    def check_msg_release_on_recover(self, broker, ftd_msgs):
        """ Check for 'Content released after recovery' messages in broker log for messages in ftd_msgs"""
        hits = self._get_hits(broker, re.compile("debug Message id=\"[0-9a-f-]{36}\"; pid=0x[0-9a-f]+: "
                                                 "Content released after recovery$", re.MULTILINE))
        self._reconsile_hits(broker, ftd_msgs, hits)

    def check_msg_block(self, broker, ftd_msgs):
        """Check for 'Content release blocked' messages in broker log for messages in ftd_msgs"""
        hits = self._get_hits(broker, re.compile("debug Message id=\"[0-9a-f-]{36}\"; pid=0x[0-9a-f]+: "
                                                 "Content release blocked$", re.MULTILINE))
        self._reconsile_hits(broker, ftd_msgs, hits)

    def check_msg_block_on_commit(self, broker, ftd_msgs):
        """Check for 'Content release blocked' messages in broker log for messages in ftd_msgs"""
        hits = self._get_hits(broker, re.compile("debug Message id=\"[0-9a-f-]{36}\"; pid=0x[0-9a-f]+: "
                                                 "Content release blocked on commit$", re.MULTILINE))
        self._reconsile_hits(broker, ftd_msgs, hits)
