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

import glob
import os
import subprocess

from brokertest import EXPECT_EXIT_OK
from qpid.datatypes import uuid4
from store_test import StoreTest, store_args
from qpid.messaging import Message

import qpid.messaging, brokertest
brokertest.qm = qpid.messaging             # TODO aconway 2014-04-04: Tests fail with SWIG client.

class ResizeTest(StoreTest):

    resize_tool = os.getenv("QPID_STORE_RESIZE_TOOL", "qpid-store-resize")
    print resize_tool
    def _resize_store(self, store_dir, queue_name, resize_num_files, resize_file_size, exp_fail):
        for f in glob.glob(os.path.join(store_dir, "*")):
            final_store_dir = os.path.join(f, queue_name)
            p = subprocess.Popen([self.resize_tool, final_store_dir, "--num-jfiles", str(resize_num_files),
                                  "--jfile-size-pgs", str(resize_file_size), "--quiet"], stdout = subprocess.PIPE,
                                  stderr = subprocess.STDOUT)
            res = p.wait()
            err_found = False
            try:
                for l in p.stdout:
                    if exp_fail:
                        err_found = True
                        print "[Expected error]:",
                    print l,
            finally:
                p.stdout.close()
            return res

    def _resize_test(self, queue_name, num_msgs, msg_size, resize_num_files, resize_file_size, init_num_files = 8,
                    init_file_size = 24, exp_fail = False, wait_time = None):
        # Using a sender will force the creation of an empty persistent queue which is needed for some tests
        broker = self.broker(store_args(), name="broker", expect=EXPECT_EXIT_OK, wait=wait_time)
        ssn = broker.connect().session()
        snd = ssn.sender("%s; {create:always, node:{durable:True}}" % queue_name)

        msgs = []
        for index in range(0, num_msgs):
            msg = Message(self.make_message(index, msg_size), durable=True, id=uuid4(), correlation_id="msg-%04d"%index)
            msgs.append(msg)
            snd.send(msg)
        broker.terminate()

        res = self._resize_store(os.path.join(self.dir, "broker", "rhm", "jrnl"), queue_name, resize_num_files,
                             resize_file_size, exp_fail)
        if res != 0:
            if exp_fail:
                return
            self.fail("ERROR: Resize operation failed with return code %d" % res)
        elif exp_fail:
            self.fail("ERROR: Resize operation succeeded, but a failure was expected")

        broker = self.broker(store_args(), name="broker")
        self.check_messages(broker, queue_name, msgs, True)

        # TODO: Check the physical files to check number and size are as expected.


class SimpleTest(ResizeTest):
    """
    Simple tests of the resize utility for resizing a journal to larger and smaller sizes.
    """

    def test_empty_store_same(self):
        self._resize_test(queue_name = "empty_store_same",
                          num_msgs = 0, msg_size = 0,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 8, resize_file_size = 24)

    def test_empty_store_up(self):
        self._resize_test(queue_name = "empty_store_up",
                          num_msgs = 0, msg_size = 0,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 16,  resize_file_size = 48)

    def test_empty_store_down(self):
        self._resize_test(queue_name = "empty_store_down",
                          num_msgs = 0, msg_size = 0,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 6, resize_file_size = 12)

# TODO: Put into long tests, make sure there is > 128GB free disk space
#    def test_empty_store_max(self):
#        self._resize_test(queue_name = "empty_store_max",
#                          num_msgs = 0, msg_size = 0,
#                          init_num_files = 8, init_file_size = 24,
#                          resize_num_files = 64, resize_file_size = 32768,
#                          wait_time = 120)

    def test_empty_store_min(self):
        self._resize_test(queue_name = "empty_store_min",
                          num_msgs = 0, msg_size = 0,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 4, resize_file_size = 1)

    def test_basic_up(self):
        self._resize_test(queue_name = "basic_up",
                          num_msgs = 100, msg_size = 10000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 16, resize_file_size = 48)

    def test_basic_down(self):
        self._resize_test(queue_name = "basic_down",
                          num_msgs = 100, msg_size = 10000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 4, resize_file_size = 15)

    def test_basic_low(self):
        self._resize_test(queue_name = "basic_low",
                          num_msgs = 100, msg_size = 10000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 4, resize_file_size = 4,
                          exp_fail = True)

    def test_basic_under(self):
        self._resize_test(queue_name = "basic_under",
                          num_msgs = 100, msg_size = 10000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 4, resize_file_size = 3,
                          exp_fail = True)

    def test_very_large_msg_up(self):
        self._resize_test(queue_name = "very_large_msg_up",
                          num_msgs = 4, msg_size = 2000000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 16, resize_file_size = 48)

    def test_very_large_msg_down(self):
        self._resize_test(queue_name = "very_large_msg_down",
                          num_msgs = 4, msg_size = 2000000,
                          init_num_files = 16, init_file_size = 64,
                          resize_num_files = 16, resize_file_size = 48)

    def test_very_large_msg_low(self):
        self._resize_test(queue_name = "very_large_msg_low",
                          num_msgs = 4, msg_size = 2000000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 7, resize_file_size = 20,
                          exp_fail = True)

    def test_very_large_msg_under(self):
        self._resize_test(queue_name = "very_large_msg_under",
                          num_msgs = 4, msg_size = 2000000,
                          init_num_files = 8, init_file_size = 24,
                          resize_num_files = 6, resize_file_size = 8,
                          exp_fail = True)
