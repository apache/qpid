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

#
# Script for 32-bit powershell
#

[Reflection.Assembly]::LoadFile('W:\cpp\src\Debug\org.apache.qpid.messagingd.dll')
$conn = new-object Org.Apache.Qpid.Messaging.Connection("localhost:5672")
$conn.Open()
$sess = $conn.CreateSession()
$rcvr = $sess.CreateReceiver("amq.topic")
$sender = $sess.CreateSender("amq.topic")
$msg1 = new-object Org.Apache.Qpid.Messaging.Message("Hello world!")
$sender.Send($msg1)
$dur = new-object Org.Apache.Qpid.Messaging.Duration(1000)
$msg2 = $rcvr.Fetch($dur)
$msg2.GetContent()
