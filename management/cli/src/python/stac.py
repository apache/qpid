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

"""
Bridges between Python and Java, to provide a simpler and more Pythonesque environment.
Certified to be free of dead parrots.
"""
# Imports
from java.lang import *
import org.amqp.blaze.management.jmx.AMQConsole as AMQConsole
import org.amqp.blaze.stac.jmx.MBeanServerConnectionContext as MBeanServerConnectionContext

# Globals
commandPrompt = ""
proxied = 0
connected = 0
amqConsole = None
connectionContext = None

# Functions
def connect(url="", username="", password=""):
  """
  Connects to an AMQP broker. The URL must be in the form amq://host:port/context
  """
  try:
    global connected
    global connectionContext
    if connected==1:
      print "Already Connected!"
      return

    try:
       parsedURL = parseURL(url)
    except URLFormatError, ufe:
       print "Invalid URL: " + ufe.msg
       return

    amqConsole = AMQConsole(parsedURL['host'], parsedURL['port'], username, password, parsedURL['context'])

    amqConsole.initialise()
    amqConsole.registerAllMBeans()
    connectionContext = MBeanServerConnectionContext()
    connectionContext.connect()
    connected = 1
  except Exception, e:
    updateGlobals()
    print e
    e.printStackTrace()
    cause = e.getCause()
    if cause != None:
        cause.printStackTrace()
  else:
    updateGlobals();

def disconnect():
    """
    Disconnects from the broker
    """
    global connected
    global connectionContext

    if connected==0:
      print "Not connected!"
      return
    try:
        connectionContext.disconnect()
        connected = 0
    except Exception, e:
        updateGlobals()
        print e
    else:
        updateGlobals()

def quit():
    global connected
    if connected != 0:
        disconnect()    

def ls():
    """
    Lists the current mbean
    """
    global connected
    if connected == 0:
        print "Not connected!"
        return

    connectionContext.ls()

def cd(beanName):
    """
    Changes the current mbean
    """
    global connected
    global connectionContext
    if connected == 0:
        print "Not connected!"
        return

    try:
        connectionContext.cd(beanName)
    except Exception, e:
        updateGlobals()
        msg = "Error: " + e.getMessage()
        print msg
    else:
        updateGlobals()

def invoke(methodName):
    """
    Invokes an operation of the current mbean
    """
    global connected
    global connectionContext

    if connected == 0:
        print "Not connected!"
        return

    try:
        connectionContext.invoke(methodName, None)
    except Exception, e:
        updateGlobals()
        msg = "Error: " + e.getMessage()
        print msg
    else:
        updateGlobals()

class URLFormatError(Exception):
    """Exception raised for errors in format of the URL

    Attributes:
        expression -- input expression in which the error occurred
        message -- explanation of the error
    """

    def __init__(self, url, message):
        self.url = url
        self.msg = message

def parseURL(url):
    """
    Parses an AMQ URL into host, port and context components returning them in a dictionary
    """
    idx = url.find("amq://")
    errorMsg = "Invalid URL - must be format amq://hostname:port/vhost"
    if idx != 0:
        raise URLFormatError(url, errorMsg)

    hostEndIdx = url.find(":", 6)
    if hostEndIdx == -1:
        raise URLFormatError(url, errorMsg)

    hostname = url[6:hostEndIdx]

    portIdx = url.find("/", hostEndIdx + 1)
    port = url[hostEndIdx + 1:portIdx]
    vhost = url[portIdx + 1:]
    if portIdx == -1:
        raise URLFormatError(url, errorMsg)

    return {'host':hostname,'port':int(port),'context':vhost}

def updateGlobals():
    global commandPrompt
    global connectionContext
    if connected == 1:
        commandPrompt = "AMQ:connected#" + connectionContext.getCurrentMBean().getAttributeValue("name", "java.lang.String") + "> "
    else:
        commandPrompt = "AMQ:disconnected> "
# Classes


# Global execution

# Update all the global variables - this is called to sync everything at the start
updateGlobals()
