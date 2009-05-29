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

class ClusterTests(TestBaseCluster):
    """Basic cluster with async store tests"""
    
    def test_Cluster_01_Initialization(self):
        """Start a single cluster containing several nodes, and stop it again"""
        try:
            clusterName = "cluster-01"
            self.createCheckCluster(clusterName, 5)
            self.checkNumBrokers(5)
            self.stopCheckCluster(clusterName)
        except:
            self.killAllClusters()
            raise

    def test_Cluster_02_MultipleClusterInitialization(self):
        """Start several clusters each with several nodes and stop them again"""
        try:
            for i in range(0, 5):
                clusterName = "cluster-02.%d" % i
                self.createCluster(clusterName, 5)
            self.checkNumBrokers(25)
            self.killCluster("cluster-02.2")
            self.checkNumBrokers(20)
            self.stopCheckAll()
        except:
            self.killAllClusters()
            raise
        
    def test_Cluster_03_AddRemoveNodes(self):
        """Create a multi-node cluster, then kill some nodes and add some new ones (not those killed)"""
        try:
            clusterName = "cluster-03"
            self.createCheckCluster(clusterName, 3)
            for i in range(4,9):
                self.createClusterNode(i, clusterName)
            self.checkNumClusterBrokers(clusterName, 8)
            self.killNode(2, clusterName)
            self.killNode(5, clusterName)
            self.killNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            self.createClusterNode(9, clusterName)
            self.createClusterNode(10, clusterName)
            self.checkNumClusterBrokers(clusterName, 7)
            self.stopCheckAll()
        except:
            self.killAllClusters()
            raise

    def test_Cluster_04_RemoveRestoreNodes(self):
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
            self.stopCheckAll()
        except:
            self.killAllClusters()
            raise
        
    def test_Cluster_05_KillAllNodesThenRecover(self):
        """Create a multi-node cluster, then kill *all* nodes, then restart the cluster"""
        try:
            clusterName = "cluster-05"
            self.createCheckCluster(clusterName, 6)
            self.killClusterCheck(clusterName)
            self.createCheckCluster(clusterName, 6)
            self.stopCheckAll()
        except:
            self.killAllClusters()
            raise
    
    def test_Cluster_06_PublishConsume(self):
        """Publish then consume 100 messages from a single cluster"""
        try:
            clusterName = "cluster-06"
            self.createCheckCluster(clusterName, 3)
            self.sendReceiveMsgs(0, clusterName, "test-exchange-06", "test-queue-06", 100)
            self.stopCheckAll()
        except:
            self.killAllClusters()
            raise
    
    def test_Cluster_07_MultiplePublishConsume(self):
        """Staggered publish and consume on a single cluster"""
        try:
            clusterName = "cluster-07"
            exchangeName = "test-exchange-07"
            queueName = "test-queue-07"
            self.createCheckCluster(clusterName, 3)
            self.createBindDirectExchangeQueue(0, clusterName, exchangeName, queueName)
            txMsgs  = self.sendMsgs(0, clusterName, exchangeName, queueName, 20) # 20, 0
            rxMsgs  = self.receiveMsgs(1, clusterName, queueName, 10) # 10, 10
            txMsgs += self.sendMsgs(2, clusterName, exchangeName, queueName, 20) # 30, 10
            rxMsgs += self.receiveMsgs(0, clusterName, queueName, 20) # 10, 30
            txMsgs += self.sendMsgs(1, clusterName, exchangeName, queueName, 20) # 30, 30 
            rxMsgs += self.receiveMsgs(2, clusterName, queueName, 20) # 10, 50
            txMsgs += self.sendMsgs(0, clusterName, exchangeName, queueName, 20) # 30, 50
            rxMsgs += self.receiveMsgs(1, clusterName, queueName, 30) # 0, 80
            self.stopCheckAll()
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise
    
    def test_Cluster_08_MsgPublishConsumeAddRemoveNodes(self):
        """Staggered publish and consume interleaved with adding and removing nodes on a single cluster"""
        try:
            clusterName = "cluster-08"
            exchangeName = "test-exchange-08"
            queueName = "test-queue-08"
            self.createCheckCluster(clusterName, 3)
            self.createBindDirectExchangeQueue(0, clusterName, exchangeName, queueName)
            txMsgs = self.sendMsgs(0, clusterName, exchangeName, queueName, 20) # 20, 0
            for i in range(3,6):
                self.createClusterNode(i, clusterName)
            self.checkNumClusterBrokers(clusterName, 6)
            txMsgs += self.sendMsgs(1, clusterName, exchangeName, queueName, 20) # 40, 0
            self.killNode(0, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            rxMsgs = self.receiveMsgs(2, clusterName, queueName, 10) # 30, 10
            self.killNode(2, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            rxMsgs += self.receiveMsgs(3, clusterName, queueName, 20) # 10, 30
            self.createClusterNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            txMsgs += self.sendMsgs(4, clusterName, exchangeName, queueName, 20) # 30, 30 
            rxMsgs += self.receiveMsgs(5, clusterName, queueName, 20) # 10, 50
            self.createClusterNode(7, clusterName)
            self.checkNumClusterBrokers(clusterName, 6)
            txMsgs += self.sendMsgs(6, clusterName, exchangeName, queueName, 20) # 30, 50
            rxMsgs += self.receiveMsgs(1, clusterName, queueName, 30) # 0, 80
            self.stopCheckAll()
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise
     
    def test_Cluster_09_MsgPublishConsumeRemoveRestoreNodes(self):
        """Publish and consume messages interleaved with adding and restoring previous nodes on a single cluster"""
        try:
            clusterName = "cluster-09"
            exchangeName = "test-exchange-09"
            queueName = "test-queue-09"
            self.createCheckCluster(clusterName, 6)
            self.createBindDirectExchangeQueue(0, clusterName, exchangeName, queueName)
            txMsgs = self.sendMsgs(0, clusterName, exchangeName, queueName, 20) # 20, 0
            self.killNode(2, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            txMsgs += self.sendMsgs(1, clusterName, exchangeName, queueName, 20) # 40, 0
            self.killNode(0, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            rxMsgs = self.receiveMsgs(3, clusterName, queueName, 10) # 30, 10
            self.killNode(4, clusterName)
            self.checkNumClusterBrokers(clusterName, 3)
            rxMsgs += self.receiveMsgs(5, clusterName, queueName, 20) # 10, 30
            self.createClusterNode(2, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            txMsgs += self.sendMsgs(1, clusterName, exchangeName, queueName, 20) # 30, 30
            self.createClusterNode(0, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            rxMsgs += self.receiveMsgs(2, clusterName, queueName, 20) # 10, 50
            self.createClusterNode(4, clusterName)
            self.checkNumClusterBrokers(clusterName, 6)
            txMsgs += self.sendMsgs(0, clusterName, exchangeName, queueName, 20) # 30, 50
            rxMsgs += self.receiveMsgs(4, clusterName, queueName, 30) # 0, 80
            self.stopCheckAll()
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise
   
    def test_Cluster_10_LinearNodeKillCreateProgression(self):
        """Publish and consume messages while linearly killing all original nodes and replacing them with new ones"""
        try:
            clusterName = "cluster-10"
            exchangeName = "test-exchange-10"
            queueName = "test-queue-10"
            self.createCheckCluster(clusterName, 4)
            self.createBindDirectExchangeQueue(2, clusterName, exchangeName, queueName)
            txMsgs = self.sendMsgs(2, clusterName, exchangeName, queueName, 20)
            rxMsgs = self.receiveMsgs(3, clusterName, queueName, 10)
            for i in range(0, 16):
                self.killNode(i, clusterName)
                self.createClusterNode(i+4, clusterName)
                self.checkNumClusterBrokers(clusterName, 4)
                txMsgs += self.sendMsgs(i+1, clusterName, exchangeName, queueName, 20)
                rxMsgs += self.receiveMsgs(i+2, clusterName, queueName, 20)
            rxMsgs += self.receiveMsgs(16, clusterName, queueName, 10)
            self.stopCheckAll()
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise
    
    def test_Cluster_11_CircularNodeKillRestoreProgression(self):
        """Publish and consume messages while circularly killing all original nodes and restoring them again"""
        try:
            clusterName = "cluster-11"
            exchangeName = "test-exchange-11"
            queueName = "test-queue-11"
            self.createCheckCluster(clusterName, 4)
            self.createBindDirectExchangeQueue(2, clusterName, exchangeName, queueName)
            txMsgs = self.sendMsgs(3, clusterName, exchangeName, queueName, 20)
            rxMsgs = self.receiveMsgs(0, clusterName, queueName, 10)
            self.killNode(0, clusterName)
            self.killNode(1, clusterName)
            for i in range(0, 16):
                self.killNode((i + 2) % 4, clusterName)
                self.createClusterNode(i % 4, clusterName)
                self.checkNumClusterBrokers(clusterName, 2)
                txMsgs += self.sendMsgs((i + 3) % 4, clusterName, exchangeName, queueName, 20)
                rxMsgs += self.receiveMsgs((i + 4) % 4, clusterName, queueName, 20)
            rxMsgs += self.receiveMsgs(3, clusterName, queueName, 10)
            self.stopCheckAll()
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise
        
    def test_Cluster_12_KillAllNodesRecoverMessages(self):
        """Create a cluster, add and delete messages, kill all nodes then recover cluster and messages"""
        if not self._storeEnable:
            print " No store loaded, skipped"
            return
        try:
            clusterName = "cluster-12"
            exchangeName = "test-exchange-12"
            queueName = "test-queue-12"
            self.createCheckCluster(clusterName, 4)
            self.createBindDirectExchangeQueue(2, clusterName, exchangeName, queueName)
            txMsgs  = self.sendMsgs(0, clusterName, exchangeName, queueName, 20)
            rxMsgs  = self.receiveMsgs(1, clusterName, queueName, 10)
            txMsgs += self.sendMsgs(2, clusterName, exchangeName, queueName, 20)
            rxMsgs += self.receiveMsgs(3, clusterName, queueName, 20)
            self.killNode(0, clusterName)
            self.createClusterNode(4, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            txMsgs += self.sendMsgs(4, clusterName, exchangeName, queueName, 20)
            rxMsgs += self.receiveMsgs(1, clusterName, queueName, 20)
            self.killNode(2, clusterName)
            self.createClusterNode(0, clusterName)
            self.createClusterNode(5, clusterName)
            self.checkNumClusterBrokers(clusterName, 5)
            txMsgs += self.sendMsgs(0, clusterName, exchangeName, queueName, 20)
            rxMsgs += self.receiveMsgs(3, clusterName, queueName, 20)
            self.killAllClusters()
            self.checkNumClusterBrokers(clusterName, 0)
            self.createCluster(clusterName)
            self.createClusterNode(3, clusterName) # last node to be used
            self.createClusterNode(0, clusterName)
            self.createClusterNode(1, clusterName)
            self.createClusterNode(2, clusterName)
            self.createClusterNode(4, clusterName)
            self.createClusterNode(5, clusterName)
            rxMsgs += self.receiveMsgs(0, clusterName, queueName, 10)
            if txMsgs != rxMsgs:
                print "txMsgs=%s" % txMsgs
                print "rxMsgs=%s" % rxMsgs
                self.fail("Send - receive message mismatch")
        except:
            self.killAllClusters()
            raise         
    
    def test_Cluster_13_TopicExchange(self):
        """Create topic exchange in a cluster and make sure it replicates correctly"""
        try:
            clusterName = "cluster-13"
            self.createCheckCluster(clusterName, 4)
            topicExchangeName = "test-exchange-13"
            topicQueueNameKeyList = {"test-queue-13-A" : "#.A", "test-queue-13-B" : "#.B", "test-queue-13-C" : "C.#", "test-queue-13-D" : "D.#"}
            self.createBindTopicExchangeQueues(2, clusterName, topicExchangeName, topicQueueNameKeyList)
            
            # Place initial messages
            txMsgsA = txMsgsC = self.sendMsgs(3, clusterName, topicExchangeName, "C.hello.A", 10) # (10, 0, 10, 0)
            self.sendMsgs(2, clusterName, topicExchangeName, "hello", 10) # Should not go to any queue
            txMsgsD = self.sendMsgs(1, clusterName, topicExchangeName, "D.hello.A", 10) # (20, 0, 10, 10)
            txMsgsA += txMsgsD
            txMsgsB = self.sendMsgs(0, clusterName, topicExchangeName, "hello.B", 20) # (20, 20, 10, 10)
            # Kill and add some nodes
            self.killNode(0, clusterName)
            self.killNode(2, clusterName)
            self.createClusterNode(4, clusterName)
            self.createClusterNode(5, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            # Pull 10 messages from each queue
            rxMsgsA =  self.receiveMsgs(1, clusterName, "test-queue-13-A", 10) # (10, 20, 10, 10)           
            rxMsgsB =  self.receiveMsgs(3, clusterName, "test-queue-13-B", 10) # (10, 10, 10, 10)                
            rxMsgsC =  self.receiveMsgs(4, clusterName, "test-queue-13-C", 10) # (10, 10, 0, 10)            
            rxMsgsD =  self.receiveMsgs(5, clusterName, "test-queue-13-D", 10) # (10, 10, 0, 0)
            # Kill and add another node
            self.killNode(4, clusterName)
            self.createClusterNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            # Add two more queues
            self.createBindTopicExchangeQueues(6, clusterName, topicExchangeName, {"test-queue-13-E" : "#.bye.A", "test-queue-13-F" : "#.bye.B"})
            # Place more messages
            txMsgs = self.sendMsgs(3, clusterName, topicExchangeName, "C.bye.A", 10) # (20, 10, 10, 0, 10, 0)
            txMsgsA += txMsgs
            txMsgsC += txMsgs
            txMsgsE  = txMsgs
            self.sendMsgs(1, clusterName, topicExchangeName, "bye", 20) # Should not go to any queue
            txMsgs = self.sendMsgs(5, clusterName, topicExchangeName, "D.bye.B", 20) # (20, 30, 10, 20, 10, 20)
            txMsgsB += txMsgs
            txMsgsD += txMsgs
            txMsgsF  = txMsgs
            # Kill all nodes but one
            self.killNode(1, clusterName)
            self.killNode(3, clusterName)
            self.killNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 1)
            # Pull all remaining messages from each queue
            rxMsgsA += self.receiveMsgs(5, clusterName, "test-queue-13-A", 20)         
            rxMsgsB += self.receiveMsgs(5, clusterName, "test-queue-13-B", 30)               
            rxMsgsC += self.receiveMsgs(5, clusterName, "test-queue-13-C", 10)          
            rxMsgsD += self.receiveMsgs(5, clusterName, "test-queue-13-D", 20)
            rxMsgsE  = self.receiveMsgs(5, clusterName, "test-queue-13-E", 10)
            rxMsgsF  = self.receiveMsgs(5, clusterName, "test-queue-13-F", 20)
            # Check messages
            self.stopCheckAll()
            if txMsgsA != rxMsgsA:
                self.fail("Send - receive message mismatch for queue A")
            if txMsgsB != rxMsgsB:
                self.fail("Send - receive message mismatch for queue B")
            if txMsgsC != rxMsgsC:
                self.fail("Send - receive message mismatch for queue C")
            if txMsgsD != rxMsgsD:
                self.fail("Send - receive message mismatch for queue D")
            if txMsgsE != rxMsgsE:
                self.fail("Send - receive message mismatch for queue E")
            if txMsgsF != rxMsgsF:
                self.fail("Send - receive message mismatch for queue F")
        except:
            self.killAllClusters()
            raise  
     
    def test_Cluster_14_FanoutExchange(self):
        """Create fanout exchange in a cluster and make sure it replicates correctly"""
        try:
            clusterName = "cluster-14"
            self.createCheckCluster(clusterName, 4)
            fanoutExchangeName = "test-exchange-14"
            fanoutQueueNameList = ["test-queue-14-A", "test-queue-14-B", "test-queue-14-C"]
            self.createBindFanoutExchangeQueues(2, clusterName, fanoutExchangeName, fanoutQueueNameList)
            
            # Place initial 20 messages, retrieve 10
            txMsg = self.sendMsgs(3, clusterName, fanoutExchangeName, None, 20)
            rxMsgA =  self.receiveMsgs(1, clusterName, "test-queue-14-A", 10)     
            rxMsgB =  self.receiveMsgs(3, clusterName, "test-queue-14-B", 10)           
            rxMsgC =  self.receiveMsgs(0, clusterName, "test-queue-14-C", 10)       
            # Kill and add some nodes
            self.killNode(0, clusterName)
            self.killNode(2, clusterName)
            self.createClusterNode(4, clusterName)
            self.createClusterNode(5, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            # Place another 20 messages, retrieve 20
            txMsg += self.sendMsgs(3, clusterName, fanoutExchangeName, None, 20)
            rxMsgA += self.receiveMsgs(1, clusterName, "test-queue-14-A", 20)     
            rxMsgB += self.receiveMsgs(3, clusterName, "test-queue-14-B", 20)           
            rxMsgC += self.receiveMsgs(4, clusterName, "test-queue-14-C", 20)       
            # Kill and add another node
            self.killNode(4, clusterName)
            self.createClusterNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 4)
            # Add another 2 queues
            self.createBindFanoutExchangeQueues(6, clusterName, fanoutExchangeName, ["test-queue-14-D", "test-queue-14-E"])
            # Place another 20 messages, retrieve 20
            tmp = self.sendMsgs(3, clusterName, fanoutExchangeName, None, 20)
            txMsg += tmp
            rxMsgA += self.receiveMsgs(1, clusterName, "test-queue-14-A", 20)     
            rxMsgB += self.receiveMsgs(3, clusterName, "test-queue-14-B", 20)           
            rxMsgC += self.receiveMsgs(6, clusterName, "test-queue-14-C", 20)       
            rxMsgD  = self.receiveMsgs(6, clusterName, "test-queue-14-D", 10)       
            rxMsgE  = self.receiveMsgs(6, clusterName, "test-queue-14-E", 10)       
            # Kill all nodes but one
            self.killNode(1, clusterName)
            self.killNode(3, clusterName)
            self.killNode(6, clusterName)
            self.checkNumClusterBrokers(clusterName, 1)
            # Pull all remaining messages from each queue
            rxMsgA += self.receiveMsgs(5, clusterName, "test-queue-14-A", 10)           
            rxMsgB += self.receiveMsgs(5, clusterName, "test-queue-14-B", 10)             
            rxMsgC += self.receiveMsgs(5, clusterName, "test-queue-14-C", 10)            
            rxMsgD += self.receiveMsgs(5, clusterName, "test-queue-14-D", 10)            
            rxMsgE += self.receiveMsgs(5, clusterName, "test-queue-14-E", 10)            
            # Check messages
            self.stopCheckAll()
            if txMsg != rxMsgA:
                self.fail("Send - receive message mismatch for queue A")
            if txMsg != rxMsgB:
                self.fail("Send - receive message mismatch for queue B")
            if txMsg != rxMsgC:
                self.fail("Send - receive message mismatch for queue C")
            if tmp != rxMsgD:
                self.fail("Send - receive message mismatch for queue D")
            if tmp != rxMsgE:
                self.fail("Send - receive message mismatch for queue E")
        except:
            self.killAllClusters()
            raise

# Start the test here
  
if __name__ == '__main__':
    if os.getenv("STORE_ENABLE") != None:
        print "NOTE: Store enabled for the following tests:"
    if not unittest.main(): sys.exit(1)
  
