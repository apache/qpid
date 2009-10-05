#!/usr/bin/env python

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

import os, signal, sys, unittest
from testlib import TestBaseCluster

class ShortTests(TestBaseCluster):
    """Basic cluster with async store tests"""
    
    def test_01_Initialization(self):
        """Start a single cluster containing several nodes, and stop it again"""
        try:
            clusterName = "cluster-01"
            self.createCheckCluster(clusterName, 5)
            self.stopCheckCluster(clusterName)
        except:
            self.killAllClusters(True)
            raise

    def test_02_MultipleClusterInitialization(self):
        """Start several clusters each with several nodes and stop them again"""
        try:
            for i in range(0, 5):
                clusterName = "cluster-02.%d" % i
                self.createCheckCluster(clusterName, 5)
            self.checkNumBrokers(25)
            self.killCluster("cluster-02.2")
            self.checkNumBrokers(20)
            self.stopAllCheck()
        except:
            self.killAllClusters(True)
            raise
        
    def test_03_AddRemoveNodes(self):
        """Create a multi-node cluster, then kill some nodes and add some new ones (not those killed)"""
        try:
            clusterName = "cluster-03"
            self.createCheckCluster(clusterName, 3)
            for i in range(3,8):
                self.createClusterNode(i, clusterName)
            self.checkNumClusterBrokers(clusterName, 8)
            self.killNode(2, clusterName)
            self.killNode(5, clusterName)
            self.killNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            self.createClusterNode(8, clusterName)
            self.createClusterNode(9, clusterName)
            self.checkNumClusterBrokers(clusterName, 7)
            self.stopAllCheck()
        except:
            self.killAllClusters(True)
            raise

    def test_04_RemoveRestoreNodes(self):
        """Create a multi-node cluster, then kill some of the nodes and restart them"""
        try:
            clusterName = "cluster-04"
            self.createCheckCluster(clusterName, 6)
            self.checkNumBrokers(6)
            self.killNode(1, clusterName)
            self.killNode(3, clusterName)
            self.killNode(4, clusterName)
            self.checkNumBrokers(3)
            self.createClusterNode(1, clusterName)
            self.createClusterNode(3, clusterName)
            self.createClusterNode(4, clusterName)
            self.checkNumClusterBrokers(clusterName, 6)
            self.killNode(2, clusterName)
            self.killNode(3, clusterName)
            self.killNode(4, clusterName)
            self.checkNumBrokers(3)
            self.createClusterNode(2, clusterName)
            self.createClusterNode(3, clusterName)
            self.createClusterNode(4, clusterName)
            self.checkNumClusterBrokers(clusterName, 6)
            self.stopAllCheck()
        except:
            self.killAllClusters(True)
            raise
        
    def test_05_KillAllNodesThenRecover(self):
        """Create a multi-node cluster, then kill *all* nodes, then restart the cluster"""
        try:
            clusterName = "cluster-05"
            self.createCheckCluster(clusterName, 6)
            self.killClusterCheck(clusterName)
            self.createCheckCluster(clusterName, 6)
            self.stopAllCheck()
        except:
            self.killAllClusters(True)
            raise
    
    def test_06_PublishConsume(self):
        """Publish then consume 100 messages from a single cluster"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-06", 3, "test-exchange-06", ["test-queue-06"])
            dh.sendMsgs(100)
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
    
    def test_07_MultiplePublishConsume(self):
        """Staggered publish and consume on a single cluster"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-07", 3, "test-exchange-07", ["test-queue-07"])
                                  #  tx  rx  nodes
                                  #   0   0  0 1 2
            dh.sendMsgs(20)       #  20   0  *
            dh.receiveMsgs(10, 1) #  20  10    *
            dh.sendMsgs(20, 2)    #  40  10      *
            dh.receiveMsgs(20, 0) #  40  30  *
            dh.sendMsgs(20, 1)    #  60  30    *
            dh.receiveMsgs(20, 2) #  60  50       *
            dh.sendMsgs(20, 0)    #  80  50  *
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
    
    def test_08_MsgPublishConsumeAddRemoveNodes(self):
        """Staggered publish and consume interleaved with adding and removing nodes on a single cluster"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-08", 3, "test-exchange-08", ["test-queue-08"])
                                  #  tx  rx  nodes
                                  #   0   0  0 1 2
            dh.sendMsgs(20)       #  20   0  *
            dh.addNodes(2)        #          0 1 2 3 4
            dh.sendMsgs(20, 1)    #  40   0    *  
            dh.killNode(0)        #          . 1 2 3 4
            dh.receiveMsgs(10, 2) #  40  10      *
            dh.killNode(2)        #          . 1 . 3 4
            dh.receiveMsgs(20, 3) #  40  30        *
            dh.addNodes()         #          . 1 . 3 4 5
            dh.sendMsgs(20, 4)    #  60  30          *
            dh.receiveMsgs(20, 5) #  60  50            *
            dh.addNodes()         #          . 1 . 3 4 5 6
            dh.sendMsgs(20, 6)    #  80  50              *
            dh.killNode(5)        #          . 1 . 3 4 . 6
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
     
    def test_09_MsgPublishConsumeRemoveRestoreNodes(self):
        """Publish and consume messages interleaved with adding and restoring previous nodes on a single cluster"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-09", 6, "test-exchange-09", ["test-queue-09"])
                                  #  tx  rx  nodes
                                  #   0   0  0 1 2 3 4 5
            dh.sendMsgs(20)       #  20   0  *
            dh.killNode(2)        #          0 1 . 3 4 5
            dh.sendMsgs(20, 1)    #  40   0    *
            dh.killNode(0)        #          . 1 . 3 4 5
            dh.receiveMsgs(10, 3) #  40  10        *
            dh.killNode(4)        #          . 1 . 3 . 5
            dh.receiveMsgs(20, 5) #  40  30            *
            dh.restoreNode(2)     #          . 1 2 3 . 5
            dh.sendMsgs(20, 1)    #  60  30    *
            dh.restoreNode(0)     #          0 1 2 3 . 5
            dh.receiveMsgs(20, 0) #  60  50  *
            dh.killNode(2)        #          0 1 . 3 . 5
            dh.restoreNode(2)     #          0 1 2 3 . 5
            dh.sendMsgs(20, 2)    #  80  50      *
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
   
    def test_10_LinearNodeKillCreateProgression(self):
        """Publish and consume messages while linearly killing all original nodes and replacing them with new ones"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-10", 4, "test-exchange-10", ["test-queue-10"])
                                        #  tx  rx  nodes
                                        #   0   0  0 1 2 3
            dh.sendMsgs(20)             #  20   0  *
            dh.receiveMsgs(10, 1)       #  20  10    *
            for i in range(0, 16):      # First loop:
                dh.killNode(i)          #          . 1 2 3
                dh.addNodes()           #          . 1 2 3 4
                dh.sendMsgs(20, i+1)    #  40  10    *
                dh.receiveMsgs(20, i+2) #  40  30      *
                                        # After loop:
                                        # 340 330  . . . . . . . . . . . . . . . . 16 17 18 19
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
    
    def test_11_CircularNodeKillRestoreProgression(self):
        """Publish and consume messages while circularly killing all original nodes and restoring them again"""
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-11", 4, "test-exchange-11", ["test-queue-11"])
                                                #  tx  rx  nodes
                                                #   0   0  0 1 2 3
            dh.sendMsgs(20, 3)                  #  20   0        *
            dh.receiveMsgs(10)                  #  20  10  *
            dh.killNode(0)                      #          . 1 2 3
            dh.killNode(1)                      #          . . 2 3
            for i in range(0, 16):              # First loop:
                dh.killNode((i + 2) % 4)        #          . . . 3
                dh.restoreNode(i % 4)           #          0 . . 3
                dh.sendMsgs(20, (i + 3) % 4)    #  40  10        *
                dh.receiveMsgs(20, (i + 4) % 4) #  40  30  *
                                                # After loop:
                                                # 340 330  . . 2 3
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise
        
    def test_12_KillAllNodesRecoverMessages(self):
        """Create a cluster, add and delete messages, kill all nodes then recover cluster and messages"""
        if not self._storeEnable:
            print " No store loaded, skipped"
            return
        try:
            dh = TestBaseCluster.DirectExchangeTestHelper(self, "cluster-12", 4, "test-exchange-12", ["test-queue-12"])
                                  #  tx  rx  nodes
                                  #   0   0  0 1 2 3
            dh.sendMsgs(20, 2)    #  20   0      *
            dh.receiveMsgs(10, 1) #  20  10    *
            dh.killNode(1)        #          0 . 2 3
            dh.sendMsgs(20, 0)    #  40  10  *
            dh.receiveMsgs(20, 3) #  40  30        *
            dh.killNode(2)        #          0 . . 3
            dh.addNodes(2)        #          0 . . 3 4 5
            dh.sendMsgs(20, 5)    #  60  30            *
            dh.receiveMsgs(20, 4) #  60  50          *
            dh.killCluster()      # cluster does not exist
            self.checkNumClusterBrokers("cluster-12", 0)
            dh.restoreCluster()   #  60  50  . . . . . .
            dh.restoreNodes()     #          0 1 2 3 4 5
            dh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise         
    
    def test_13_TopicExchange(self):
        """Create topic exchange in a cluster and make sure it behaves correctly"""
        try:
            topicQueueNameKeyList = {"test-queue-13-A" : "#.A", "test-queue-13-B" : "*.B", "test-queue-13-C" : "C.#", "test-queue-13-D" : "D.*"}
            th = TestBaseCluster.TopicExchangeTestHelper(self, "cluster-13", 4, "test-exchange-13", topicQueueNameKeyList)
             # Place initial messages
            th.sendMsgs("C.hello.A", 10)
            th.sendMsgs("hello.world", 10) # matches none of the queues
            th.sendMsgs("D.hello.A", 10)
            th.sendMsgs("hello.B", 20)
            th.sendMsgs("D.hello", 20)
            # Kill and add some nodes
            th.killNode(0)
            th.killNode(2)
            th.addNodes(2)
            # Pull 10 messages from each queue
            th.receiveMsgs(10)
            # Kill and add another node
            th.killNode(4)
            th.addNodes()
            # Add two more queues
            th.addQueues({"test-queue-13-E" : "#.bye.A", "test-queue-13-F" : "#.bye.B"})
            # Place more messages
            th.sendMsgs("C.bye.A", 10)
            th.sendMsgs("hello.bye", 20) # matches none of the queues
            th.sendMsgs("hello.bye.B", 20)
            th.sendMsgs("D.bye", 20)
            # Kill all nodes but one
            th.killNode(1)
            th.killNode(3)
            th.killNode(6)
            # Pull all remaining messages from each queue and check messages
            th.finalizeTest()
        except:
            self.killAllClusters(True)
            raise  
     
    def test_14_FanoutExchange(self):
        """Create fanout exchange in a cluster and make sure it behaves correctly"""
        try:
            fanoutQueueNameList = ["test-queue-14-A", "test-queue-14-B", "test-queue-14-C"]
            fh = TestBaseCluster.FanoutExchangeTestHelper(self, "cluster-14", 4, "test-exchange-14", fanoutQueueNameList)
            # Place initial 20 messages, retrieve 10
            fh.sendMsgs(20)
            fh.receiveMsgs(10)
            # Kill and add some nodes
            fh.killNode(0)
            fh.killNode(2)
            fh.addNodes(2)
            # Place another 20 messages, retrieve 20
            fh.sendMsgs(20)
            fh.receiveMsgs(20)
            # Kill and add another node
            fh.killNode(4)
            fh.addNodes()
            # Add another 2 queues
            fh.addQueues(["test-queue-14-D", "test-queue-14-E"])
            # Place another 20 messages, retrieve 20
            fh.sendMsgs(20)
            fh.receiveMsgs(20)     
            # Kill all nodes but one
            fh.killNode(1)
            fh.killNode(3)
            fh.killNode(6)
            # Check messages
            fh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise

class LongTests(TestBaseCluster):
    """Basic cluster with async store tests"""
    
    def test_01_TopicExchange(self):
        """Create topic exchange in a cluster and make sure it behaves correctly"""
        try:
            topicQueueNameKeyList = {"test-queue-13-A" : "#.A", "test-queue-13-B" : "*.B", "test-queue-13-C" : "C.#", "test-queue-13-D" : "D.*"}
            th = TestBaseCluster.TopicExchangeTestHelper(self, "cluster-13", 4, "test-exchange-13", topicQueueNameKeyList)
             # Place initial messages
            th.sendMsgs("C.hello.A", 10)
            th.sendMsgs("hello.world", 10) # matches none of the queues
            th.sendMsgs("D.hello.A", 10)
            th.sendMsgs("hello.B", 20)
            th.sendMsgs("D.hello", 20)
            # Kill and add some nodes
            th.killNode(0)
            th.killNode(2)
            th.addNodes(2)
            # Pull 10 messages from each queue
            th.receiveMsgs(10)
            # Kill and add another node
            th.killNode(4)
            th.addNodes()
            # Add two more queues
            th.addQueues({"test-queue-13-E" : "#.bye.A", "test-queue-13-F" : "#.bye.B"})
            # Place more messages
            th.sendMsgs("C.bye.A", 10)
            th.sendMsgs("hello.bye", 20) # matches none of the queues
            th.sendMsgs("hello.bye.B", 20)
            th.sendMsgs("D.bye", 20)
            # Kill all nodes but one
            th.killNode(1)
            th.killNode(3)
            th.killNode(6)
            # Pull all remaining messages from each queue and check messages
            th.finalizeTest()
        except:
            self.killAllClusters(True)
            raise  
     
    def test_02_FanoutExchange(self):
        """Create fanout exchange in a cluster and make sure it behaves correctly"""
        try:
            fanoutQueueNameList = ["test-queue-14-A", "test-queue-14-B", "test-queue-14-C"]
            fh = TestBaseCluster.FanoutExchangeTestHelper(self, "cluster-14", 4, "test-exchange-14", fanoutQueueNameList)
            # Place initial 20 messages, retrieve 10
            fh.sendMsgs(20)
            fh.receiveMsgs(10)
            # Kill and add some nodes
            fh.killNode(0)
            fh.killNode(2)
            fh.addNodes(2)
            # Place another 20 messages, retrieve 20
            fh.sendMsgs(20)
            fh.receiveMsgs(20)
            # Kill and add another node
            fh.killNode(4)
            fh.addNodes()
            # Add another 2 queues
            fh.addQueues(["test-queue-14-D", "test-queue-14-E"])
            # Place another 20 messages, retrieve 20
            fh.sendMsgs(20)
            fh.receiveMsgs(20)     
            # Kill all nodes but one
            fh.killNode(1)
            fh.killNode(3)
            fh.killNode(6)
            # Check messages
            fh.finalizeTest()
        except:
            self.killAllClusters(True)
            raise

# Start the test here
  
if __name__ == '__main__':
    if os.getenv("STORE_LIB") != None:
        print "NOTE: Store enabled for the following tests:"
    if not unittest.main(): sys.exit(1)
  
