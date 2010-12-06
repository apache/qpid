#!/usr/bin/env python

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

import time, string, traceback
from brokertest import *
from qpid.messaging import *


try:
    import java.lang.System
    _cp = java.lang.System.getProperty("java.class.path"); 
except ImportError: 
    _cp = checkenv("QP_CP")

class Formatter:

  def __init__(self, message):
    self.message = message
    self.environ = {"M": self.message,
                    "P": self.message.properties,
                    "C": self.message.content}

  def __getitem__(self, st):
    return eval(st, self.environ)

# The base test case has support for launching the generic
# receiver and sender through the TestLauncher with all the options.
# 
class JavaClientTest(BrokerTest):
    """Base Case for Java Test cases"""

    client_class = "org.apache.qpid.testkit.TestLauncher" 

    # currently there is no transparent reconnection.
    # temp hack: just creating the queue here and closing it.
    def start_error_watcher(self,broker=None):
        ssn = broker.connect().session()
        err_watcher = ssn.receiver("control; {create:always}", capacity=1)
        ssn.close()  

    def store_module_args(self):
        if BrokerTest.store_lib:
            return ["--load-module", BrokerTest.store_lib]
        else:
            print "Store module not present."
            return [""]

    def client(self,**options):
        cmd =  ["java","-cp",_cp] 

        cmd += ["-Dtest_name=" + options.get("test_name", "UNKNOWN")]
        cmd += ["-Dhost=" + options.get("host","127.0.0.1")]
        cmd += ["-Dport=" + str(options.get("port",5672))]
        cmd += ["-Dcon_count=" + str(options.get("con_count",1))]
        cmd += ["-Dssn_per_con=" + str(options.get("ssn_per_con",1))]        
        cmd += ["-Duse_unique_dests=" + str(options.get("use_unique_dests",False))]
        cmd += ["-Dcheck_for_dups=" + str(options.get("check_for_dups",False))]
        cmd += ["-Ddurable=" + str(options.get("durable",False))]
        cmd += ["-Dtransacted=" + str(options.get("transacted",False))]
        cmd += ["-Dreceiver=" + str(options.get("receiver",False))]
        cmd += ["-Dsync_rcv=" + str(options.get("sync_rcv",False))]
        cmd += ["-Dsender=" + str(options.get("sender",False))]
        cmd += ["-Dmsg_size=" + str(options.get("msg_size",256))]
        cmd += ["-Dtx_size=" + str(options.get("tx_size",10))]
        cmd += ["-Dmsg_count=" + str(options.get("msg_count",1000))]
        cmd += ["-Dmax_prefetch=" + str(options.get("max_prefetch",500))]
        cmd += ["-Dsync_ack=" + str(options.get("sync_ack",False))]
        cmd += ["-Dsync_persistence=" + str(options.get("sync_pub",False))]
        cmd += ["-Dsleep_time=" + str(options.get("sleep_time",1000))]
        cmd += ["-Dfailover=" + options.get("failover", "failover_exchange")]
        cmd += ["-Djms_durable_sub=" + str(options.get("jms_durable_sub", False))]  
        cmd += ["-Dlog.level=" + options.get("log.level", "warn")]  
        cmd += [self.client_class]
        cmd += [options.get("address", "my_queue; {create: always}")] 

        #print str(options.get("port",5672))  
        return cmd

    # currently there is no transparent reconnection.
    # temp hack: just creating a receiver and closing session soon after.
    def monitor_clients(self,broker=None,run_time=600,error_ck_freq=60):
        ssn = broker.connect().session()
        err_watcher = ssn.receiver("control; {create:always}", capacity=1)
        i = run_time/error_ck_freq
        is_error = False   
        for j in range(i):    
            not_empty = True
            while not_empty:            
                try:   
                    m = err_watcher.fetch(timeout=error_ck_freq)
                    ssn.acknowledge()
                    print "Java process notified of an error"
                    self.print_error(m) 
                    is_error = True
                except messaging.Empty, e: 
                    not_empty = False              
                    
        ssn.close()
        return is_error

    def print_error(self,msg):
        print msg.properties.get("exception-trace")

    def verify(self, receiver,sender):
        sender_running = receiver.is_running()
        receiver_running = sender.is_running()

        self.assertTrue(receiver_running,"Receiver has exited prematually")
        self.assertTrue(sender_running,"Sender has exited prematually")

    def start_sender_and_receiver(self,**options):

        receiver_opts = options
        receiver_opts["receiver"]=True
        receiver = self.popen(self.client(**receiver_opts),
                                          expect=EXPECT_RUNNING) 

        sender_opts = options
        sender_opts["sender"]=True
        sender = self.popen(self.client(**sender_opts),
                                        expect=EXPECT_RUNNING) 

        return receiver, sender

    def start_cluster(self,count=2,expect=EXPECT_RUNNING,**options):
        if options.get("durable",False)==True:
            cluster = Cluster(self, count=count, expect=expect, args=self.store_module_args())
        else:
            cluster = Cluster(self, count=count)    
        return cluster  

class ConcurrencyTest(JavaClientTest):
    """A concurrency test suite for the JMS client"""
    skip = False

    def base_case(self,**options):
        if self.skip :
            print "Skipping test"
            return

        cluster = self.start_cluster(count=2,**options)      
        self.start_error_watcher(broker=cluster[0])
        options["port"] = port=cluster[0].port() 

        options["use_unique_dests"]=True
        options["address"]="amq.topic" 
        receiver, sender = self.start_sender_and_receiver(**options)
        self.monitor_clients(broker=cluster[0],run_time=180)
        self.verify(receiver,sender)

    def test_multiplexing_con(self):
        """Tests multiple sessions on a single connection""" 

        self.base_case(ssn_per_con=25,test_name=self.id())

    def test_multiplexing_con_with_tx(self):
        """Tests multiple transacted sessions on a single connection""" 

        self.base_case(ssn_per_con=25,transacted=True,test_name=self.id())

    def test_multiplexing_con_with_sync_rcv(self):
        """Tests multiple sessions with sync receive""" 

        self.base_case(ssn_per_con=25,sync_rcv=True,test_name=self.id())

    def test_multiplexing_con_with_durable_sub(self):
        """Tests multiple sessions with durable subs""" 

        self.base_case(ssn_per_con=25,durable=True,jms_durable_sub=True,test_name=self.id())

    def test_multiplexing_con_with_sync_ack(self):
        """Tests multiple sessions with sync ack""" 

        self.base_case(ssn_per_con=25,sync_ack=True,test_name=self.id())

    def test_multiplexing_con_with_sync_pub(self):
        """Tests multiple sessions with sync pub""" 

        self.base_case(ssn_per_con=25,sync_pub=True,durable=True,test_name=self.id())

    def test_multiple_cons_and_ssns(self):
        """Tests multiple connections and sessions""" 

        self.base_case(con_count=10,ssn_per_con=25,test_name=self.id())


class SoakTest(JavaClientTest):
    """A soak test suite for the JMS client"""

    def base_case(self,**options):
        cluster = self.start_cluster(count=4, expect=EXPECT_EXIT_FAIL,**options)
        options["port"] = port=cluster[0].port()  
        self.start_error_watcher(broker=cluster[0])
        options["use_unique_dests"]=True
        options["address"]="amq.topic" 
        receiver,sender = self.start_sender_and_receiver(**options)
        is_error = self.monitor_clients(broker=cluster[0],run_time=30,error_ck_freq=30)

        if (is_error):
            print "The sender or receiver didn't start properly. Exiting test."
            return       
        else:
            "Print no error !" 

        # grace period for java clients to get the failover properly setup.
        time.sleep(30) 
        error_msg= None
        # Kill original brokers, start new ones.
        try:
            for i in range(8):
                cluster[i].kill()
                b=cluster.start()
                self.monitor_clients(broker=b,run_time=30,error_ck_freq=30)
                print "iteration : " + str(i)
        except ConnectError, e1:
            error_msg = "Unable to connect to new cluster node : " + traceback.format_exc(e1)

        except SessionError, e2:
            error_msg = "Session error while connected to new cluster node : " + traceback.format_exc(e2)

        self.verify(receiver,sender)
        if error_msg:      
            raise Exception(error_msg)      

     
    def test_failover(self) :
        """Test basic failover""" 

        self.base_case(test_name=self.id())


    def test_failover_with_durablesub(self):
        """Test failover with durable subscriber""" 

        self.base_case(durable=True,jms_durable_sub=True,test_name=self.id())


    def test_failover_with_sync_rcv(self):
        """Test failover with sync receive""" 

        self.base_case(sync_rcv=True,test_name=self.id())


    def test_failover_with_sync_ack(self):
        """Test failover with sync ack""" 

        self.base_case(sync_ack=True,test_name=self.id())


    def test_failover_with_noprefetch(self):
        """Test failover with no prefetch""" 

        self.base_case(max_prefetch=1,test_name=self.id())


    def test_failover_with_multiple_cons_and_ssns(self):
        """Test failover with multiple connections and sessions""" 

        self.base_case(use_unique_dests=True,address="amq.topic",
                       con_count=10,ssn_per_con=25,test_name=self.id())
