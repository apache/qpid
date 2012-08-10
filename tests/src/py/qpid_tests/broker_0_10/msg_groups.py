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
import qmf.console

from time import sleep
#
# Tests the Broker's support for message groups
#

class MultiConsumerMsgGroupTests(Base):
    """
    Tests for the behavior of multi-consumer message groups.  These tests allow
    a messages from the same group be consumed by multiple different clients as
    long as each message is processed "in sequence".  See QPID-3346 for
    details.
    """

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_simple(self):
        """ Verify simple acquire/accept actions on a set of grouped
        messages shared between two receivers.
        """
        ## Create a msg group queue

        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","A","A","B","B","B","C","C","C"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        ## Queue = a-0, a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
        ## Owners= ---, ---, ---, ---, ---, ---, ---, ---, ---,

        # create consumers on separate sessions: C1,C2
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 should acquire A-0, then C2 should acquire B-3

        m1 = c1.fetch(0);
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        m2 = c2.fetch(0);
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 3

        # C1 Acknowledge A-0
        c1.session.acknowledge(m1);

        # C2 should next acquire A-1
        m3 = c2.fetch(0);
        assert m3.properties['THE-GROUP'] == 'A'
        assert m3.content['index'] == 1

        # C1 should next acquire C-6, since groups A&B are held by c2
        m4 = c1.fetch(0);
        assert m4.properties['THE-GROUP'] == 'C'
        assert m4.content['index'] == 6

        ## Queue = XXX, a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
        ## Owners= ---, ^C2, +C2, ^C2, +C2, +C2, ^C1, +C1, +C1,

        # C2 Acknowledge B-3, freeing up the rest of B group
        c2.session.acknowledge(m2);

        ## Queue = XXX, a-1, a-2, XXX, b-4, b-5, c-6, c-7, c-8...
        ## Owners= ---, ^C2, +C2, ---, ---, ---, ^C1, +C1, +C1,

        # C1 should now acquire B-4, since it is next "free"
        m5 = c1.fetch(0);
        assert m5.properties['THE-GROUP'] == 'B'
        assert m5.content['index'] == 4

        ## Queue = XXX, a-1, a-2, XXX, b-4, b-5, c-6, c-7, c-8...
        ## Owners= ---, ^C2, +C2, ---, ^C1, +C1, ^C1, +C1, +C1,

        # C1 acknowledges C-6, freeing the C group
        c1.session.acknowledge(m4)

        ## Queue = XXX, a-1, a-2, XXX, b-4, b-5, XXX, c-7, c-8...
        ## Owners= ---, ^C2, +C2, ---, ^C1, +C1, ---, ---, ---

        # C2 should next fetch A-2, followed by C-7
        m7 = c2.fetch(0);
        assert m7.properties['THE-GROUP'] == 'A'
        assert m7.content['index'] == 2

        m8 = c2.fetch(0);
        assert m8.properties['THE-GROUP'] == 'C'
        assert m8.content['index'] == 7

        ## Queue = XXX, a-1, a-2, XXX, b-4, b-5, XXX, c-7, c-8...
        ## Owners= ---, ^C2, ^C2, ---, ^C1, +C1, ---, ^C2, +C2

        # have C2 ack all fetched messages, freeing C-8
        c2.session.acknowledge()

        ## Queue = XXX, XXX, XXX, XXX, b-4, b-5, XXX, XXX, c-8...
        ## Owners= ---, ---, ---, ---, ^C1, +C1, ---, ---, ---

        # the next fetch of C2 would get C-8, since B-5 is "owned"
        m9 = c2.fetch(0);
        assert m9.properties['THE-GROUP'] == 'C'
        assert m9.content['index'] == 8

        ## Queue = XXX, XXX, XXX, XXX, b-4, b-5, XXX, XXX, c-8...
        ## Owners= ---, ---, ---, ---, ^C1, +C1, ---, ---, ^C2

        # C1 acks B-4, freeing B-5 for consumption
        c1.session.acknowledge(m5)

        ## Queue = XXX, XXX, XXX, XXX, XXX, b-5, XXX, XXX, c-8...
        ## Owners= ---, ---, ---, ---, ---, ^C2, ---, ---, ^C2

        # the next fetch of C2 would get B-5
        m10 = c2.fetch(0);
        assert m10.properties['THE-GROUP'] == 'B'
        assert m10.content['index'] == 5

        # there should be no more left for C1:
        try:
            mx = c1.fetch(0)
            assert False     # should never get here
        except Empty:
            pass

        c1.session.acknowledge()
        c2.session.acknowledge()
        c1.close()
        c2.close()
        snd.close()

    def test_simple_browse(self):
        """ Test the behavior of a browsing subscription on a message grouping
        queue.
        """

        ## Create a msg group queue

        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        ## Queue = A-0, B-1, A-2, b-3, C-4
        ## Owners= ---, ---, ---, ---, ---

        # create consumer and browser
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q; {mode: browse}", options={"capacity":0})

        m2 = b1.fetch(0);
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 0

        # C1 should acquire A-0

        m1 = c1.fetch(0);
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        ## Queue = A-0, B-1, A-2, b-3, C-4
        ## Owners= ^C1, ---, +C1, ---, ---

        m2 = b1.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 1

        # verify that the browser may see A-2, even though its group is owned
        # by C1
        m2 = b1.fetch(0)
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 2

        m2 = b1.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 3

        # verify the consumer can own groups currently seen by the browser
        m3 = c1.fetch(0);
        assert m3.properties['THE-GROUP'] == 'B'
        assert m3.content['index'] == 1

        m2 = b1.fetch(0)
        assert m2.properties['THE-GROUP'] == 'C'
        assert m2.content['index'] == 4

    def test_release(self):
        """ Verify releasing a message can free its assocated group
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","A","B","B"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 2

        # C1 release m1, and the first group

        s1.acknowledge(m1, Disposition(RELEASED, set_redelivered=True))

        # C2 should be able to get group 'A', msg 'A-0' now
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 0

    def test_reject(self):
        """ Verify rejecting a message can free its associated group
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","A","B","B"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 2

        # C1 rejects m1, and the first group is released
        s1.acknowledge(m1, Disposition(REJECTED))

        # C2 should be able to get group 'A', msg 'A-1' now
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 1

    def test_close(self):
        """ Verify behavior when a consumer that 'owns' a group closes.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","A","B","B"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 will own group A
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # C2 will own group B
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 2

        # C1 shuffles off the mortal coil...
        c1.close();

        # but the session (s1) remains active, so "A" remains blocked
        # from c2, c2 should fetch the next B-3

        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 3

        # and there should be no more messages available for C2
        try:
            m2 = c2.fetch(0)
            assert False     # should never get here
        except Empty:
            pass

        # close session s1, releasing the A group
        s1.close()

        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 0

        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'A'
        assert m2.content['index'] == 1

        # and there should be no more messages now
        try:
            m2 = c2.fetch(0)
            assert False     # should never get here
        except Empty:
            pass

    def test_transaction(self):
        """ Verify behavior when using transactions.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","A","B","B","A","B"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.conn.session(transactional=True)
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.conn.session(transactional=True)
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 gets group A
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # C2 gets group B
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 2

        s1.acknowledge(m1)  # A-0 consumed, A group freed
        s2.acknowledge(m2)  # B-2 consumed, B group freed

        s1.commit()    # A-0 consumption done, A group now free
        s2.rollback()  # releases B-2, and group B

        ## Q: ["A1","B2","B3","A4","B5"]

        # C2 should be able to get the next A
        m3 = c2.fetch(0)
        assert m3.properties['THE-GROUP'] == 'A'
        assert m3.content['index'] == 1

        # C1 should be able to get B-2
        m4 = c1.fetch(0)
        assert m4.properties['THE-GROUP'] == 'B'
        assert m4.content['index'] == 2

        s2.acknowledge(m3)  # C2 consumes A-1
        s1.acknowledge(m4)  # C1 consumes B-2
        s1.commit()    # C1 consume B-2 occurs, free group B

        ## Q: [["A1",]"B3","A4","B5"]

        # A-1 is still considered owned by C2, since the commit has yet to
        # occur, so the next available to C1 would be B-3
        m5 = c1.fetch(0)   # B-3
        assert m5.properties['THE-GROUP'] == 'B'
        assert m5.content['index'] == 3

        # and C2 should find A-4 available, since it owns the A group
        m6 = c2.fetch(0)  # A-4
        assert m6.properties['THE-GROUP'] == 'A'
        assert m6.content['index'] == 4

        s2.acknowledge(m6)  # C2 consumes A-4

        # uh-oh, A-1 and A-4 released, along with A group
        s2.rollback()

        ## Q: ["A1",["B3"],"A4","B5"]
        m7 = c1.fetch(0)   # A-1 is found
        assert m7.properties['THE-GROUP'] == 'A'
        assert m7.content['index'] == 1

        ## Q: [["A1"],["B3"],"A4","B5"]
        # since C1 "owns" both A and B group, C2 should find nothing available
        try:
            m8 = c2.fetch(0)
            assert False    # should not get here
        except Empty:
            pass

        # C1 next gets A4
        m9 = c1.fetch(0)
        assert m9.properties['THE-GROUP'] == 'A'
        assert m9.content['index'] == 4

        s1.acknowledge()

        ## Q: [["A1"],["B3"],["A4"],"B5"]
        # even though C1 acknowledges A1,B3, and A4, B5 is still considered
        # owned as the commit has yet to take place
        try:
            m10 = c2.fetch(0)
            assert False    # should not get here
        except Empty:
            pass

        # now A1,B3,A4 dequeued, B5 should be free
        s1.commit()

        ## Q: ["B5"]
        m11 = c2.fetch(0)
        assert m11.properties['THE-GROUP'] == 'B'
        assert m11.content['index'] == 5

        s2.acknowledge()
        s2.commit()

    def test_send_transaction(self):
        """ Verify behavior when sender is using transactions.
        """
        ssn = self.conn.session(transactional=True)
        snd = ssn.sender("msg-group-q; {create:always, delete:sender," +
                         " node: {x-declare: {arguments:" +
                         " {'qpid.group_header_key':'THE-GROUP'," +
                         "'qpid.shared_msg_group':1}}}}")

        msg = Message(content={'index':0}, properties={"THE-GROUP": "A"})
        snd.send(msg)
        msg = Message(content={'index':1}, properties={"THE-GROUP": "B"})
        snd.send(msg)
        snd.session.commit()
        msg = Message(content={'index':2}, properties={"THE-GROUP": "A"})
        snd.send(msg)

        # Queue: [A0,B1, (uncommitted: A2) ]

        s1 = self.conn.session(transactional=True)
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.conn.session(transactional=True)
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 gets A0, group A
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # C2 gets B2, group B
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 1

        # Since A2 uncommitted, there should be nothing left to fetch
        try:
            mX = c1.fetch(0)
            assert False    # should not get here
        except Empty:
            pass
        try:
            mX = c2.fetch(0)
            assert False    # should not get here
        except Empty:
            pass

        snd.session.commit()
        msg = Message(content={'index':3}, properties={"THE-GROUP": "B"})
        snd.send(msg)

        # Queue: [A2, (uncommitted: B3) ]

        # B3 has yet to be committed, so C2 should see nothing available:
        try:
            mX = c2.fetch(0)
            assert False    # should not get here
        except Empty:
            pass

        # but A2 should be available to C1
        m3 = c1.fetch(0)
        assert m3.properties['THE-GROUP'] == 'A'
        assert m3.content['index'] == 2

        # now make B3 available
        snd.session.commit()

        # C1 should still be done:
        try:
            mX = c1.fetch(0)
            assert False    # should not get here
        except Empty:
            pass

        # but C2 should find the new B
        m4 = c2.fetch(0)
        assert m4.properties['THE-GROUP'] == 'B'
        assert m4.content['index'] == 3

        # extra: have C1 rollback, verify C2 finds the released 'A' messages
        c1.session.rollback()

        ## Q: ["A0","A2"]

        # C2 should be able to get the next A
        m5 = c2.fetch(0)
        assert m5.properties['THE-GROUP'] == 'A'
        assert m5.content['index'] == 0

        m6 = c2.fetch(0)
        assert m6.properties['THE-GROUP'] == 'A'
        assert m6.content['index'] == 2

        c2.session.acknowledge()
        c2.session.commit()

    def test_query(self):
        """ Verify the queue query method against message groups
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","C","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        m1 = c1.fetch(0)
        m2 = c2.fetch(0)

        # at this point, group A should be owned by C1, group B by C2, and
        # group C should be available

        # now setup a QMF session, so we can call methods
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        brokers = self.qmf_session.getObjects(_class="broker")
        assert len(brokers) == 1
        broker = brokers[0]

        # verify the query method call's group information
        rc = broker.query("queue", "msg-group-q")
        assert rc.status == 0
        assert rc.text == "OK"
        results = rc.outArgs['results']
        assert 'qpid.message_group_queue' in results
        q_info = results['qpid.message_group_queue']
        assert 'group_header_key' in q_info and q_info['group_header_key'] == "THE-GROUP"
        assert 'group_state' in q_info and len(q_info['group_state']) == 3
        for g_info in q_info['group_state']:
            assert 'group_id' in g_info
            if g_info['group_id'] == "A":
                assert g_info['msg_count'] == 3
                assert g_info['consumer'] != ""
            elif g_info['group_id'] == "B":
                assert g_info['msg_count'] == 2
                assert g_info['consumer'] != ""
            elif g_info['group_id'] == "C":
                assert g_info['msg_count'] == 2
                assert g_info['consumer'] == ""
            else:
                assert(False)    # should never get here
        self.qmf_session.delBroker(self.qmf_broker)

    def test_purge_free(self):
        """ Verify we can purge a queue of all messages of a given "unowned"
        group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," + 
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # now setup a QMF session, so we can call methods
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        queue = self.qmf_session.getObjects(_class="queue", name="msg-group-q")[0]
        assert queue
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "B" }}
        assert queue.msgDepth == 6
        rc = queue.purge(0, msg_filter)
        assert rc.status == 0
        queue.update()
        assert queue.msgDepth == 4

        # verify all B's removed....
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q; {mode: browse}", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] != 'B'
                count += 1
        except Empty:
            pass
        assert count == 4

        self.qmf_session.delBroker(self.qmf_broker)

    def test_purge_acquired(self):
        """ Verify we can purge messages from an acquired group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # acquire group "A"
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # now setup a QMF session, so we can purge group A
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        queue = self.qmf_session.getObjects(_class="queue", name="msg-group-q")[0]
        assert queue
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "A" }}
        assert queue.msgDepth == 6
        rc = queue.purge(0, msg_filter)
        assert rc.status == 0
        queue.update()
        queue.msgDepth == 4   # the pending acquired A still counts!
        s1.acknowledge()

        # verify all other A's removed....
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q; {mode: browse}", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] != 'A'
                count += 1
        except Empty:
            pass
        assert count == 3   # only 3 really available
        s1.acknowledge()    # ack the consumed A-0
        self.qmf_session.delBroker(self.qmf_broker)

    def test_purge_count(self):
        """ Verify we can purge a fixed number of messages from an acquired
        group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # acquire group "A"
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # now setup a QMF session, so we can purge group A
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        queue = self.qmf_session.getObjects(_class="queue", name="msg-group-q")[0]
        assert queue
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "A" }}
        assert queue.msgDepth == 6
        rc = queue.purge(1, msg_filter)
        assert rc.status == 0
        queue.update()
        queue.msgDepth == 5   # the pending acquired A still counts!

        # verify all other A's removed....
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q; {mode: browse}", options={"capacity":0})
        count = 0
        a_count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                if m2.properties['THE-GROUP'] != 'A':
                    count += 1
                else:
                    a_count += 1
        except Empty:
            pass
        assert count == 3   # non-A's
        assert a_count == 1 # assumes the acquired message was not the one purged and regular browsers don't get acquired messages
        s1.acknowledge()    # ack the consumed A-0
        self.qmf_session.delBroker(self.qmf_broker)

    def test_move_all(self):
        """ Verify we can move messages from an acquired group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # set up destination queue
        rcvr = self.ssn.receiver("dest-q; {create:always, delete:receiver," +
                                 " node: {x-declare: {arguments:" +
                                 " {'qpid.group_header_key':'THE-GROUP'," +
                                 "'qpid.shared_msg_group':1}}}}")

        # acquire group "A"
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # now setup a QMF session, so we can move what's left of group A
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        brokers = self.qmf_session.getObjects(_class="broker")
        assert len(brokers) == 1
        broker = brokers[0]
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "A" }}
        rc = broker.queueMoveMessages("msg-group-q", "dest-q", 0, msg_filter)
        assert rc.status == 0

        # verify all other A's removed from msg-group-q
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] != 'A'
                count += 1
        except Empty:
            pass
        assert count == 3   # only 3 really available

        # verify the moved A's are at the dest-q
        s2 = self.setup_session()
        b1 = s2.receiver("dest-q; {mode: browse}", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] == 'A'
                assert m2.content['index'] == 2 or m2.content['index'] == 5
                count += 1
        except Empty:
            pass
        assert count == 2 # two A's moved

        s1.acknowledge()    # ack the consumed A-0
        self.qmf_session.delBroker(self.qmf_broker)

    def test_move_count(self):
        """ Verify we can move a fixed number of messages from an acquired group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # set up destination queue
        rcvr = self.ssn.receiver("dest-q; {create:always, delete:receiver," +
                                 " node: {x-declare: {arguments:" +
                                 " {'qpid.group_header_key':'THE-GROUP'," +
                                 "'qpid.shared_msg_group':1}}}}")

        # now setup a QMF session, so we can move group B
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        brokers = self.qmf_session.getObjects(_class="broker")
        assert len(brokers) == 1
        broker = brokers[0]
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "B" }}
        rc = broker.queueMoveMessages("msg-group-q", "dest-q", 3, msg_filter)
        assert rc.status == 0

        # verify all B's removed from msg-group-q
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q; {mode: browse}", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] != 'B'
                count += 1
        except Empty:
            pass
        assert count == 4

        # verify the moved B's are at the dest-q
        s2 = self.setup_session()
        b1 = s2.receiver("dest-q; {mode: browse}", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] == 'B'
                assert m2.content['index'] == 1 or m2.content['index'] == 3
                count += 1
        except Empty:
            pass
        assert count == 2

        self.qmf_session.delBroker(self.qmf_broker)

    def test_reroute(self):
        """ Verify we can reroute messages from an acquired group.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        # create a topic exchange for the reroute
        rcvr = self.ssn.receiver("reroute-q; {create: always, delete:receiver," +
                                 " node: {type: topic}}")

        # acquire group "A"
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # now setup a QMF session, so we can reroute group A
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        queue = self.qmf_session.getObjects(_class="queue", name="msg-group-q")[0]
        assert queue
        msg_filter = { 'filter_type' : 'header_match_str',
                       'filter_params' : { 'header_key' : "THE-GROUP",
                                           'header_value' : "A" }}
        assert queue.msgDepth == 6
        rc = queue.reroute(0, False, "reroute-q", msg_filter)
        assert rc.status == 0
        queue.update()
        queue.msgDepth == 4   # the pending acquired A still counts!

        # verify all other A's removed....
        s2 = self.setup_session()
        b1 = s2.receiver("msg-group-q", options={"capacity":0})
        count = 0
        try:
            while True:
                m2 = b1.fetch(0)
                assert m2.properties['THE-GROUP'] != 'A'
                count += 1
        except Empty:
            pass
        assert count == 3   # only 3 really available

        # and what of reroute-q?
        count = 0
        try:
            while True:
                m2 = rcvr.fetch(0)
                assert m2.properties['THE-GROUP'] == 'A'
                assert m2.content['index'] == 2 or m2.content['index'] == 5
                count += 1
        except Empty:
            pass
        assert count == 2

        s1.acknowledge()    # ack the consumed A-0
        self.qmf_session.delBroker(self.qmf_broker)

    def test_queue_delete(self):
        """ Test deleting a queue while consumers are active.
        """

        ## Create a msg group queue

        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A","B","C"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        ## Queue = A-0, B-1, A-2, b-3, C-4
        ## Owners= ---, ---, ---, ---, ---

        # create consumers
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 should acquire A-0
        m1 = c1.fetch(0);
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        # c2 acquires B-1
        m2 = c2.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 1

        # with group A and B owned, and C free, delete the
        # queue
        snd.close()
        self.ssn.close()

    def test_default_group_id(self):
        """ Verify the queue assigns the default group id should a message
        arrive without a group identifier.
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        m = Message(content={}, properties={"NO-GROUP-HEADER":"HA-HA"})
        snd.send(m)

        # now setup a QMF session, so we can call methods
        self.qmf_session = qmf.console.Session()
        self.qmf_broker = self.qmf_session.addBroker(str(self.broker))
        brokers = self.qmf_session.getObjects(_class="broker")
        assert len(brokers) == 1
        broker = brokers[0]

        # grab the group state off the queue, and verify the default group is
        # present ("qpid.no-group" is the broker default)
        rc = broker.query("queue", "msg-group-q")
        assert rc.status == 0
        assert rc.text == "OK"
        results = rc.outArgs['results']
        assert 'qpid.message_group_queue' in results
        q_info = results['qpid.message_group_queue']
        assert 'group_header_key' in q_info and q_info['group_header_key'] == "THE-GROUP"
        assert 'group_state' in q_info and len(q_info['group_state']) == 1
        g_info = q_info['group_state'][0]
        assert 'group_id' in g_info
        assert g_info['group_id'] == 'qpid.no-group'

        self.qmf_session.delBroker(self.qmf_broker)


    def test_transaction_order(self):
        """ Verify that rollback does not reorder the messages with respect to
        the consumer (QPID-3804)
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","A"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            snd.send(m)

        s1 = self.conn.session(transactional=True)
        c1 = s1.receiver("msg-group-q", options={"capacity":0})

        # C1 gets group A
        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0
        s1.acknowledge(m1)

        s1.rollback()  # release A back to the queue

        # the order should be preserved as follows:

        m1 = c1.fetch(0)
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        m2 = c1.fetch(0)
        assert m2.properties['THE-GROUP'] == 'B'
        assert m2.content['index'] == 1

        m3 = c1.fetch(0)
        assert m3.properties['THE-GROUP'] == 'A'
        assert m3.content['index'] == 2

        s1.commit()

        c1.close()
        s1.close()
        snd.close()


    def test_ttl_expire(self):
        """ Verify that expired (TTL) group messages are skipped correctly
        """
        snd = self.ssn.sender("msg-group-q; {create:always, delete:sender," +
                              " node: {x-declare: {arguments:" +
                              " {'qpid.group_header_key':'THE-GROUP'," +
                              "'qpid.shared_msg_group':1}}}}")

        groups = ["A","B","C","A","B","C"]
        messages = [Message(content={}, properties={"THE-GROUP": g}) for g in groups]
        index = 0
        for m in messages:
            m.content['index'] = index
            index += 1
            if m.properties['THE-GROUP'] == 'B':
                m.ttl = 1;
            snd.send(m)

        sleep(2)  # let all B's expire

        # create consumers on separate sessions: C1,C2
        s1 = self.setup_session()
        c1 = s1.receiver("msg-group-q", options={"capacity":0})
        s2 = self.setup_session()
        c2 = s2.receiver("msg-group-q", options={"capacity":0})

        # C1 should acquire A-0, then C2 should acquire C-2, Group B should
        # expire and never be fetched

        m1 = c1.fetch(0);
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 0

        m2 = c2.fetch(0);
        assert m2.properties['THE-GROUP'] == 'C'
        assert m2.content['index'] == 2

        m1 = c1.fetch(0);
        assert m1.properties['THE-GROUP'] == 'A'
        assert m1.content['index'] == 3

        m2 = c2.fetch(0);
        assert m2.properties['THE-GROUP'] == 'C'
        assert m2.content['index'] == 5

        # there should be no more left for either consumer
        try:
            mx = c1.fetch(0)
            assert False     # should never get here
        except Empty:
            pass
        try:
            mx = c2.fetch(0)
            assert False     # should never get here
        except Empty:
            pass

        c1.session.acknowledge()
        c2.session.acknowledge()
        c1.close()
        c2.close()
        snd.close()


class StickyConsumerMsgGroupTests(Base):
    """
    Tests for the behavior of sticky-consumer message groups.  These tests
    expect all messages from the same group be consumed by the same clients.
    See QPID-3347 for details.
    """
    pass  # TBD
