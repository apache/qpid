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

import qpid_messaging
import qmf2

class FindAgents(qmf2.ConsoleHandler):

  def __init__(self, session):
    qmf2.ConsoleHandler.__init__(self, session)

  def agentAdded(self, agent):
    print "Agent Added: %r" % agent

  def agentDeleted(self, agent, reason):
    print "Agent Deleted: %r reason: %s" % (agent, reason)

  def agentRestarted(self, agent):
    print "Agent Restarted: %r" % agent

  def agentSchemaUpdated(self, agent):
    print "Agent Schema Updated: %r" % agent

  def eventRaised(self, agent, data, timestamp, severity):
    print "Event: data=%r time=%d sev=%d" % (data.getProperties(), timestamp, severity)



url     = "localhost"
options = ""

connection = qpid_messaging.Connection(url, options)
connection.open()

session = qmf2.ConsoleSession(connection)
session.open()
session.setAgentFilter("[]")

main = FindAgents(session)
main.run()

