#!/bin/bash
#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

qpid-run org.apache.qpid.IBMPerfTest.JNDIBindConnectionFactory  amqp://guest:guest@clientid/testpath?brokerlist='vm://:1' amq.VMConnectionFactory
qpid-run org.apache.qpid.IBMPerfTest.JNDIBindQueue amq.Queue   direct://amq.direct//IBMPerfQueue1
qpid-run org.apache.qpid.IBMPerfTest.JNDIBindTopic amq.Topic1  topic://amq.topic/IBMPerfTopic1/
qpid-run org.apache.qpid.IBMPerfTest.JNDIBindTopic amq.Topic2  topic://amq.topic/IBMPerfTopic2/
qpid-run org.apache.qpid.IBMPerfTest.JNDIBindTopic amq.Topic3  topic://amq.topic/IBMPerfTopic3/
qpid-run org.apache.qpid.IBMPerfTest.JNDIBindTopic amq.Topic4  topic://amq.topic/IBMPerfTopic4/