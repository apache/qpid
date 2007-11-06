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
from qpid.client import Client, Closed
from qpid.queue import Empty
from qpid.content import Content
from qpid.testlib import testrunner, TestBase
from struct import pack, unpack
from time import sleep

class DtxTests(TestBase):
    """
    Tests for the amqp dtx related classes.

    Tests of the form test_simple_xxx test the basic transactional
    behaviour. The approach here is to 'swap' a message from one queue
    to another by consuming and re-publishing in the same
    transaction. That transaction is then completed in different ways
    and the appropriate result verified.

    The other tests enforce more specific rules and behaviour on a
    per-method or per-field basis.        
    """

    XA_RBROLLBACK = 1
    XA_RBTIMEOUT = 2
    XA_OK = 0
    tx_counter = 0

    def reset_channel(self):
        self.channel.session_close()
        self.channel = self.client.channel(self.channel.id + 1)
        self.channel.session_open()

    def test_simple_commit(self):
        """        
        Test basic one-phase commit behaviour.     
        """
        channel = self.channel
        tx = self.xid("my-xid")
        self.txswap(tx, "commit")

        #neither queue should have any messages accessible
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(0, "queue-b")

        #commit
        self.assertEqual(self.XA_OK, channel.dtx_coordination_commit(xid=tx, one_phase=True).status)

        #should close and reopen channel to ensure no unacked messages are held
        self.reset_channel()

        #check result
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(1, "queue-b")
        self.assertMessageId("commit", "queue-b")

    def test_simple_prepare_commit(self):
        """        
        Test basic two-phase commit behaviour.     
        """
        channel = self.channel
        tx = self.xid("my-xid")
        self.txswap(tx, "prepare-commit")

        #prepare
        self.assertEqual(self.XA_OK, channel.dtx_coordination_prepare(xid=tx).status)

        #neither queue should have any messages accessible
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(0, "queue-b")

        #commit
        self.assertEqual(self.XA_OK, channel.dtx_coordination_commit(xid=tx, one_phase=False).status)

        self.reset_channel()

        #check result
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(1, "queue-b")
        self.assertMessageId("prepare-commit", "queue-b")


    def test_simple_rollback(self):
        """        
        Test basic rollback behaviour.     
        """
        channel = self.channel
        tx = self.xid("my-xid")
        self.txswap(tx, "rollback")

        #neither queue should have any messages accessible
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(0, "queue-b")

        #rollback
        self.assertEqual(self.XA_OK, channel.dtx_coordination_rollback(xid=tx).status)

        self.reset_channel()

        #check result
        self.assertMessageCount(1, "queue-a")
        self.assertMessageCount(0, "queue-b")
        self.assertMessageId("rollback", "queue-a")

    def test_simple_prepare_rollback(self):
        """        
        Test basic rollback behaviour after the transaction has been prepared.     
        """
        channel = self.channel
        tx = self.xid("my-xid")
        self.txswap(tx, "prepare-rollback")

        #prepare
        self.assertEqual(self.XA_OK, channel.dtx_coordination_prepare(xid=tx).status)

        #neither queue should have any messages accessible
        self.assertMessageCount(0, "queue-a")
        self.assertMessageCount(0, "queue-b")

        #rollback
        self.assertEqual(self.XA_OK, channel.dtx_coordination_rollback(xid=tx).status)

        self.reset_channel()

        #check result
        self.assertMessageCount(1, "queue-a")
        self.assertMessageCount(0, "queue-b")
        self.assertMessageId("prepare-rollback", "queue-a")    

    def test_select_required(self):
        """
        check that an error is flagged if select is not issued before
        start or end        
        """
        channel = self.channel
        tx = self.xid("dummy")
        try:
            channel.dtx_demarcation_start(xid=tx)
            
            #if we get here we have failed, but need to do some cleanup:
            channel.dtx_demarcation_end(xid=tx)
            channel.dtx_coordination_rollback(xid=tx)
            self.fail("Channel not selected for use with dtx, expected exception!")
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_start_already_known(self):
        """
        Verify that an attempt to start an association with a
        transaction that is already known is not allowed (unless the
        join flag is set).
        """
        #create two channels on different connection & select them for use with dtx:
        channel1 = self.channel
        channel1.dtx_demarcation_select()

        other = self.connect()
        channel2 = other.channel(1)
        channel2.session_open()
        channel2.dtx_demarcation_select()

        #create a xid
        tx = self.xid("dummy")
        #start work on one channel under that xid:
        channel1.dtx_demarcation_start(xid=tx)
        #then start on the other without the join set
        failed = False
        try:
            channel2.dtx_demarcation_start(xid=tx)
        except Closed, e:
            failed = True
            error = e

        #cleanup:
        if not failed:
            channel2.dtx_demarcation_end(xid=tx)
            other.close()
        channel1.dtx_demarcation_end(xid=tx)
        channel1.dtx_coordination_rollback(xid=tx)
        
        #verification:
        if failed: self.assertConnectionException(503, e.args[0])
        else: self.fail("Xid already known, expected exception!")                    

    def test_forget_xid_on_completion(self):
        """
        Verify that a xid is 'forgotten' - and can therefore be used
        again - once it is completed.
        """
        #do some transactional work & complete the transaction
        self.test_simple_commit()
        # channel has been reset, so reselect for use with dtx
        self.channel.dtx_demarcation_select()        
        
        #start association for the same xid as the previously completed txn
        tx = self.xid("my-xid")
        self.channel.dtx_demarcation_start(xid=tx)
        self.channel.dtx_demarcation_end(xid=tx)
        self.channel.dtx_coordination_rollback(xid=tx)

    def test_start_join_and_resume(self):
        """
        Ensure the correct error is signalled when both the join and
        resume flags are set on starting an association between a
        channel and a transcation.
        """
        channel = self.channel
        channel.dtx_demarcation_select()
        tx = self.xid("dummy")
        try:
            channel.dtx_demarcation_start(xid=tx, join=True, resume=True)
            #failed, but need some cleanup:
            channel.dtx_demarcation_end(xid=tx)
            channel.dtx_coordination_rollback(xid=tx)
            self.fail("Join and resume both set, expected exception!")
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_start_join(self):
        """        
        Verify 'join' behaviour, where a channel is associated with a
        transaction that is already associated with another channel.        
        """
        #create two channels & select them for use with dtx:
        channel1 = self.channel
        channel1.dtx_demarcation_select()

        channel2 = self.client.channel(2)
        channel2.session_open()
        channel2.dtx_demarcation_select()

        #setup
        channel1.queue_declare(queue="one", exclusive=True, auto_delete=True)
        channel1.queue_declare(queue="two", exclusive=True, auto_delete=True)
        channel1.message_transfer(content=Content(properties={'routing_key':"one", 'message_id':"a"}, body="DtxMessage"))
        channel1.message_transfer(content=Content(properties={'routing_key':"two", 'message_id':"b"}, body="DtxMessage"))

        #create a xid
        tx = self.xid("dummy")
        #start work on one channel under that xid:
        channel1.dtx_demarcation_start(xid=tx)
        #then start on the other with the join flag set
        channel2.dtx_demarcation_start(xid=tx, join=True)

        #do work through each channel
        self.swap(channel1, "one", "two")#swap 'a' from 'one' to 'two'
        self.swap(channel2, "two", "one")#swap 'b' from 'two' to 'one'

        #mark end on both channels
        channel1.dtx_demarcation_end(xid=tx)
        channel2.dtx_demarcation_end(xid=tx)
        
        #commit and check
        channel1.dtx_coordination_commit(xid=tx, one_phase=True)
        self.assertMessageCount(1, "one")
        self.assertMessageCount(1, "two")
        self.assertMessageId("a", "two")
        self.assertMessageId("b", "one")
        

    def test_suspend_resume(self):
        """
        Test suspension and resumption of an association
        """
        channel = self.channel
        channel.dtx_demarcation_select()

        #setup
        channel.queue_declare(queue="one", exclusive=True, auto_delete=True)
        channel.queue_declare(queue="two", exclusive=True, auto_delete=True)
        channel.message_transfer(content=Content(properties={'routing_key':"one", 'message_id':"a"}, body="DtxMessage"))
        channel.message_transfer(content=Content(properties={'routing_key':"two", 'message_id':"b"}, body="DtxMessage"))

        tx = self.xid("dummy")

        channel.dtx_demarcation_start(xid=tx)
        self.swap(channel, "one", "two")#swap 'a' from 'one' to 'two'
        channel.dtx_demarcation_end(xid=tx, suspend=True)

        channel.dtx_demarcation_start(xid=tx, resume=True)
        self.swap(channel, "two", "one")#swap 'b' from 'two' to 'one'
        channel.dtx_demarcation_end(xid=tx)
        
        #commit and check
        channel.dtx_coordination_commit(xid=tx, one_phase=True)
        self.assertMessageCount(1, "one")
        self.assertMessageCount(1, "two")
        self.assertMessageId("a", "two")
        self.assertMessageId("b", "one")

    def test_suspend_start_end_resume(self):        
        """
        Test suspension and resumption of an association with work
        done on another transaction when the first transaction is
        suspended
        """
        channel = self.channel
        channel.dtx_demarcation_select()

        #setup
        channel.queue_declare(queue="one", exclusive=True, auto_delete=True)
        channel.queue_declare(queue="two", exclusive=True, auto_delete=True)
        channel.message_transfer(content=Content(properties={'routing_key':"one", 'message_id':"a"}, body="DtxMessage"))
        channel.message_transfer(content=Content(properties={'routing_key':"two", 'message_id':"b"}, body="DtxMessage"))

        tx = self.xid("dummy")

        channel.dtx_demarcation_start(xid=tx)
        self.swap(channel, "one", "two")#swap 'a' from 'one' to 'two'
        channel.dtx_demarcation_end(xid=tx, suspend=True)

        channel.dtx_demarcation_start(xid=tx, resume=True)
        self.swap(channel, "two", "one")#swap 'b' from 'two' to 'one'
        channel.dtx_demarcation_end(xid=tx)
        
        #commit and check
        channel.dtx_coordination_commit(xid=tx, one_phase=True)
        self.assertMessageCount(1, "one")
        self.assertMessageCount(1, "two")
        self.assertMessageId("a", "two")
        self.assertMessageId("b", "one")

    def test_end_suspend_and_fail(self):
        """        
        Verify that the correct error is signalled if the suspend and
        fail flag are both set when disassociating a transaction from
        the channel        
        """
        channel = self.channel
        channel.dtx_demarcation_select()
        tx = self.xid("suspend_and_fail")
        channel.dtx_demarcation_start(xid=tx)
        try:
            channel.dtx_demarcation_end(xid=tx, suspend=True, fail=True)
            self.fail("Suspend and fail both set, expected exception!")
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

        #cleanup    
        other = self.connect()
        channel = other.channel(1)
        channel.session_open()
        channel.dtx_coordination_rollback(xid=tx)
        channel.session_close()
        other.close()
    

    def test_end_unknown_xid(self):
        """        
        Verifies that the correct exception is thrown when an attempt
        is made to end the association for a xid not previously
        associated with the channel
        """
        channel = self.channel
        channel.dtx_demarcation_select()
        tx = self.xid("unknown-xid")
        try:
            channel.dtx_demarcation_end(xid=tx)
            self.fail("Attempted to end association with unknown xid, expected exception!")
        except Closed, e:
            #FYI: this is currently *not* the exception specified, but I think the spec is wrong! Confirming...
            self.assertConnectionException(503, e.args[0])

    def test_end(self):
        """
        Verify that the association is terminated by end and subsequent
        operations are non-transactional        
        """
        channel = self.client.channel(2)
        channel.session_open()
        channel.queue_declare(queue="tx-queue", exclusive=True, auto_delete=True)

        #publish a message under a transaction
        channel.dtx_demarcation_select()
        tx = self.xid("dummy")
        channel.dtx_demarcation_start(xid=tx)
        channel.message_transfer(content=Content(properties={'routing_key':"tx-queue", 'message_id':"one"}, body="DtxMessage"))
        channel.dtx_demarcation_end(xid=tx)

        #now that association with txn is ended, publish another message
        channel.message_transfer(content=Content(properties={'routing_key':"tx-queue", 'message_id':"two"}, body="DtxMessage"))

        #check the second message is available, but not the first
        self.assertMessageCount(1, "tx-queue")
        self.subscribe(channel, queue="tx-queue", destination="results", confirm_mode=1)
        msg = self.client.queue("results").get(timeout=1)
        self.assertEqual("two", msg.content['message_id'])
        channel.message_cancel(destination="results")
        #ack the message then close the channel
        msg.complete()
        channel.session_close()

        channel = self.channel        
        #commit the transaction and check that the first message (and
        #only the first message) is then delivered
        channel.dtx_coordination_commit(xid=tx, one_phase=True)
        self.assertMessageCount(1, "tx-queue")
        self.assertMessageId("one", "tx-queue")

    def test_invalid_commit_one_phase_true(self):
        """
        Test that a commit with one_phase = True is rejected if the
        transaction in question has already been prepared.        
        """
        other = self.connect()
        tester = other.channel(1)
        tester.session_open()
        tester.queue_declare(queue="dummy", exclusive=True, auto_delete=True)
        tester.dtx_demarcation_select()
        tx = self.xid("dummy")
        tester.dtx_demarcation_start(xid=tx)
        tester.message_transfer(content=Content(properties={'routing_key':"dummy"}, body="whatever"))
        tester.dtx_demarcation_end(xid=tx)
        tester.dtx_coordination_prepare(xid=tx)
        failed = False
        try:
            tester.dtx_coordination_commit(xid=tx, one_phase=True)
        except Closed, e:
            failed = True
            error = e

        if failed:
            self.channel.dtx_coordination_rollback(xid=tx)
            self.assertConnectionException(503, e.args[0])
        else:
            tester.session_close()
            other.close()
            self.fail("Invalid use of one_phase=True, expected exception!")

    def test_invalid_commit_one_phase_false(self):
        """
        Test that a commit with one_phase = False is rejected if the
        transaction in question has not yet been prepared.        
        """
        """
        Test that a commit with one_phase = True is rejected if the
        transaction in question has already been prepared.        
        """
        other = self.connect()
        tester = other.channel(1)
        tester.session_open()
        tester.queue_declare(queue="dummy", exclusive=True, auto_delete=True)
        tester.dtx_demarcation_select()
        tx = self.xid("dummy")
        tester.dtx_demarcation_start(xid=tx)
        tester.message_transfer(content=Content(properties={'routing_key':"dummy"}, body="whatever"))
        tester.dtx_demarcation_end(xid=tx)
        failed = False
        try:
            tester.dtx_coordination_commit(xid=tx, one_phase=False)
        except Closed, e:
            failed = True
            error = e

        if failed:
            self.channel.dtx_coordination_rollback(xid=tx)
            self.assertConnectionException(503, e.args[0])
        else:
            tester.session_close()
            other.close()
            self.fail("Invalid use of one_phase=False, expected exception!")

    def test_implicit_end(self):
        """
        Test that an association is implicitly ended when the channel
        is closed (whether by exception or explicit client request)
        and the transaction in question is marked as rollback only.
        """
        channel1 = self.channel
        channel2 = self.client.channel(2)
        channel2.session_open()

        #setup:
        channel2.queue_declare(queue="dummy", exclusive=True, auto_delete=True)
        channel2.message_transfer(content=Content(properties={'routing_key':"dummy"}, body="whatever"))
        tx = self.xid("dummy")

        channel2.dtx_demarcation_select()
        channel2.dtx_demarcation_start(xid=tx)
        channel2.message_subscribe(queue="dummy", destination="dummy", confirm_mode=1)
        channel2.message_flow(destination="dummy", unit=0, value=1)
        channel2.message_flow(destination="dummy", unit=1, value=0xFFFFFFFF)
        self.client.queue("dummy").get(timeout=1).complete()
        channel2.message_cancel(destination="dummy")
        channel2.message_transfer(content=Content(properties={'routing_key':"dummy"}, body="whatever"))
        channel2.session_close()

        self.assertEqual(self.XA_RBROLLBACK, channel1.dtx_coordination_prepare(xid=tx).status)
        channel1.dtx_coordination_rollback(xid=tx)

    def test_get_timeout(self):
        """        
        Check that get-timeout returns the correct value, (and that a
        transaction with a timeout can complete normally)        
        """
        channel = self.channel
        tx = self.xid("dummy")

        channel.dtx_demarcation_select()
        channel.dtx_demarcation_start(xid=tx)
        self.assertEqual(0, channel.dtx_coordination_get_timeout(xid=tx).timeout)
        channel.dtx_coordination_set_timeout(xid=tx, timeout=60)
        self.assertEqual(60, channel.dtx_coordination_get_timeout(xid=tx).timeout)
        self.assertEqual(self.XA_OK, channel.dtx_demarcation_end(xid=tx).status)
        self.assertEqual(self.XA_OK, channel.dtx_coordination_rollback(xid=tx).status)        
        
    def test_set_timeout(self):
        """        
        Test the timeout of a transaction results in the expected
        behaviour        
        """
        #open new channel to allow self.channel to be used in checking te queue
        channel = self.client.channel(2)
        channel.session_open()
        #setup:
        tx = self.xid("dummy")
        channel.queue_declare(queue="queue-a", exclusive=True, auto_delete=True)
        channel.queue_declare(queue="queue-b", exclusive=True, auto_delete=True)
        channel.message_transfer(content=Content(properties={'routing_key':"queue-a", 'message_id':"timeout"}, body="DtxMessage"))

        channel.dtx_demarcation_select()
        channel.dtx_demarcation_start(xid=tx)
        self.swap(channel, "queue-a", "queue-b")
        channel.dtx_coordination_set_timeout(xid=tx, timeout=2)
        sleep(3)
        #check that the work has been rolled back already
        self.assertMessageCount(1, "queue-a")
        self.assertMessageCount(0, "queue-b")
        self.assertMessageId("timeout", "queue-a")
        #check the correct codes are returned when we try to complete the txn
        self.assertEqual(self.XA_RBTIMEOUT, channel.dtx_demarcation_end(xid=tx).status)
        self.assertEqual(self.XA_RBTIMEOUT, channel.dtx_coordination_rollback(xid=tx).status)        



    def test_recover(self):
        """
        Test basic recover behaviour
        """
        channel = self.channel

        channel.dtx_demarcation_select()
        channel.queue_declare(queue="dummy", exclusive=True, auto_delete=True)

        prepared = []
        for i in range(1, 10):
            tx = self.xid("tx%s" % (i))
            channel.dtx_demarcation_start(xid=tx)
            channel.message_transfer(content=Content(properties={'routing_key':"dummy"}, body="message%s" % (i)))
            channel.dtx_demarcation_end(xid=tx)
            if i in [2, 5, 6, 8]:
                channel.dtx_coordination_prepare(xid=tx)
                prepared.append(tx)
            else:    
                channel.dtx_coordination_rollback(xid=tx)

        xids = channel.dtx_coordination_recover().in_doubt
        
        #rollback the prepared transactions returned by recover
        for x in xids:
            channel.dtx_coordination_rollback(xid=x)            

        #validate against the expected list of prepared transactions
        actual = set(xids)
        expected = set(prepared)
        intersection = actual.intersection(expected)
        
        if intersection != expected:
            missing = expected.difference(actual)
            extra = actual.difference(expected)
            for x in missing:
                channel.dtx_coordination_rollback(xid=x)            
            self.fail("Recovered xids not as expected. missing: %s; extra: %s" % (missing, extra))

    def test_bad_resume(self):
        """
        Test that a resume on a session not selected for use with dtx fails
        """
        channel = self.channel
        try:
            channel.dtx_demarcation_start(resume=True)
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def xid(self, txid):
        DtxTests.tx_counter += 1
        branchqual = "v%s" % DtxTests.tx_counter
        return pack('!LBB', 0, len(txid), len(branchqual)) + txid + branchqual
        
    def txswap(self, tx, id):
        channel = self.channel
        #declare two queues:
        channel.queue_declare(queue="queue-a", exclusive=True, auto_delete=True)
        channel.queue_declare(queue="queue-b", exclusive=True, auto_delete=True)
        #put message with specified id on one queue:
        channel.message_transfer(content=Content(properties={'routing_key':"queue-a", 'message_id':id}, body="DtxMessage"))

        #start the transaction:
        channel.dtx_demarcation_select()        
        self.assertEqual(self.XA_OK, self.channel.dtx_demarcation_start(xid=tx).status)

        #'swap' the message from one queue to the other, under that transaction:
        self.swap(self.channel, "queue-a", "queue-b")

        #mark the end of the transactional work:
        self.assertEqual(self.XA_OK, self.channel.dtx_demarcation_end(xid=tx).status)

    def swap(self, channel, src, dest):
        #consume from src:
        channel.message_subscribe(destination="temp-swap", queue=src, confirm_mode=1)
        channel.message_flow(destination="temp-swap", unit=0, value=1)
        channel.message_flow(destination="temp-swap", unit=1, value=0xFFFFFFFF)
        msg = self.client.queue("temp-swap").get(timeout=1)
        channel.message_cancel(destination="temp-swap")
        msg.complete();        

        #re-publish to dest
        channel.message_transfer(content=Content(properties={'routing_key':dest, 'message_id':msg.content['message_id']},
                                                 body=msg.content.body))

    def assertMessageCount(self, expected, queue):
        self.assertEqual(expected, self.channel.queue_query(queue=queue).message_count)

    def assertMessageId(self, expected, queue):
        self.channel.message_subscribe(queue=queue, destination="results")
        self.channel.message_flow(destination="results", unit=0, value=1)
        self.channel.message_flow(destination="results", unit=1, value=0xFFFFFFFF)
        self.assertEqual(expected, self.client.queue("results").get(timeout=1).content['message_id'])
        self.channel.message_cancel(destination="results")
