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

import qpid_messaging
from qmf2 import *


class ExampleAgent(AgentHandler):
  """
  This example agent is implemented as a single class that inherits AgentHandler.
  It does not use a separate thread since once set up, it is driven strictly by
  incoming method calls.
  """

  def __init__(self, url):
    ##
    ## Create and open a messaging connection to a broker.
    ##
    self.connection = qpid_messaging.Connection(url, "{reconnect:True}")
    self.session = None
    self.connection.open()

    ##
    ## Create, configure, and open a QMFv2 agent session using the connection.
    ##
    self.session = AgentSession(self.connection, "{interval:30}")
    self.session.setVendor('profitron.com')
    self.session.setProduct('blastinator')
    self.session.setAttribute('attr1', 1000)
    self.session.open()

    ##
    ## Initialize the parent class.
    ##
    AgentHandler.__init__(self, self.session)


  def shutdown(self):
    """
    Clean up the session and connection.
    """
    if self.session:
      self.session.close()
    self.connection.close()


  def method(self, handle, methodName, args, subtypes, addr, userId):
    """
    Handle incoming method calls.
    """
    if addr == self.controlAddr:
      self.control.methodCount += 1

      try:
        if methodName == "stop":
          self.session.methodSuccess(handle)
          self.cancel()

        elif methodName == "echo":
          handle.addReturnArgument("sequence", args["sequence"])
          handle.addReturnArgument("map", args["map"])
          self.session.methodSuccess(handle)

        elif methodName == "event":
          ev = Data(self.sch_event)
          ev.text = args['text']
          self.session.raiseEvent(ev, args['severity'])
          self.session.methodSuccess(handle)

        elif methodName == "fail":
          if args['useString']:
            self.session.raiseException(handle, args['stringVal'])
          else:
            ex = Data(self.sch_exception)
            ex.whatHappened = "It Failed"
            ex.howBad = 75
            ex.details = args['details']
            self.session.raiseException(handle, ex)

        elif methodName == "create_child":
          name = args['name']
          child = Data(self.sch_child)
          child.name = name
          addr = self.session.addData(child, name)
          handle.addReturnArgument("childAddr", addr.asMap())
          self.session.methodSuccess(handle)
      except BaseException, e:
        self.session.raiseException(handle, "%r" % e)


  def setupSchema(self):
    """
    Create and register the schema for this agent.
    """
    package = "com.profitron.bntor"

    ##
    ## Declare a schema for a structured exception that can be used in failed
    ## method invocations.
    ##
    self.sch_exception = Schema(SCHEMA_TYPE_DATA, package, "exception")
    self.sch_exception.addProperty(SchemaProperty("whatHappened", SCHEMA_DATA_STRING))
    self.sch_exception.addProperty(SchemaProperty("howBad", SCHEMA_DATA_INT))
    self.sch_exception.addProperty(SchemaProperty("details", SCHEMA_DATA_MAP))

    ##
    ## Declare a control object to test methods against.
    ##
    self.sch_control = Schema(SCHEMA_TYPE_DATA, package, "control")
    self.sch_control.addProperty(SchemaProperty("state", SCHEMA_DATA_STRING))
    self.sch_control.addProperty(SchemaProperty("methodCount", SCHEMA_DATA_INT))

    stopMethod = SchemaMethod("stop", desc="Stop Agent")
    stopMethod.addArgument(SchemaProperty("message", SCHEMA_DATA_STRING, direction=DIR_IN))
    self.sch_control.addMethod(stopMethod)

    echoMethod = SchemaMethod("echo", desc="Echo Arguments")
    echoMethod.addArgument(SchemaProperty("sequence", SCHEMA_DATA_INT, direction=DIR_IN_OUT))
    echoMethod.addArgument(SchemaProperty("map", SCHEMA_DATA_MAP, direction=DIR_IN_OUT))
    self.sch_control.addMethod(echoMethod)

    eventMethod = SchemaMethod("event", desc="Raise an Event")
    eventMethod.addArgument(SchemaProperty("text", SCHEMA_DATA_STRING, direction=DIR_IN))
    eventMethod.addArgument(SchemaProperty("severity", SCHEMA_DATA_INT, direction=DIR_IN))
    self.sch_control.addMethod(eventMethod)

    failMethod = SchemaMethod("fail", desc="Expected to Fail")
    failMethod.addArgument(SchemaProperty("useString", SCHEMA_DATA_BOOL, direction=DIR_IN))
    failMethod.addArgument(SchemaProperty("stringVal", SCHEMA_DATA_STRING, direction=DIR_IN))
    failMethod.addArgument(SchemaProperty("details", SCHEMA_DATA_MAP, direction=DIR_IN))
    self.sch_control.addMethod(failMethod)

    createMethod = SchemaMethod("create_child", desc="Create Child Object")
    createMethod.addArgument(SchemaProperty("name", SCHEMA_DATA_STRING, direction=DIR_IN))
    createMethod.addArgument(SchemaProperty("childAddr", SCHEMA_DATA_MAP, direction=DIR_OUT))
    self.sch_control.addMethod(createMethod)

    ##
    ## Declare a child object
    ##
    self.sch_child = Schema(SCHEMA_TYPE_DATA, package, "child")
    self.sch_child.addProperty(SchemaProperty("name", SCHEMA_DATA_STRING))

    ##
    ## Declare the event class
    ##
    self.sch_event = Schema(SCHEMA_TYPE_EVENT, package, "event")
    self.sch_event.addProperty(SchemaProperty("text", SCHEMA_DATA_STRING))

    ##
    ## Register our schemata with the agent session.
    ##
    self.session.registerSchema(self.sch_exception)
    self.session.registerSchema(self.sch_control)
    self.session.registerSchema(self.sch_child)
    self.session.registerSchema(self.sch_event)


  def populateData(self):
    """
    Create a control object and give it to the agent session to manage.
    """
    self.control = Data(self.sch_control)
    self.control.state = "OPERATIONAL"
    self.control.methodCount = 0
    self.controlAddr = self.session.addData(self.control, "singleton")


try:
  agent = ExampleAgent("localhost")
  agent.setupSchema()
  agent.populateData()
  agent.run()  # Use agent.start() to launch the agent in a separate thread
  agent.shutdown()
except Exception, e:
  print "Exception Caught:", e


