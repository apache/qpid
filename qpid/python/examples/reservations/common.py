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

import traceback
from fnmatch import fnmatch
from qpid.messaging import *

class Dispatcher:

  def unhandled(self, msg):
    print "UNHANDLED MESSAGE: %s" % msg

  def ignored(self, msg):
    return False

  def dispatch(self, msg):
    try:
      if self.ignored(msg):
        return ()
      else:
        type = msg.properties.get("type")
        replies = getattr(self, "do_%s" % type, self.unhandled)(msg)
        if replies is None:
          return ()
        else:
          return replies
    except:
      traceback.print_exc()
      return ()

  def run(self, session):
    while self.running():
      msg = session.next_receiver().fetch()
      replies = self.dispatch(msg)

      count = len(replies)
      sequence = 1
      for to, r in replies:
        r.correlation_id = msg.correlation_id
        r.properties["count"] = count
        r.properties["sequence"] = sequence
        sequence += 1
        try:
          snd = session.sender(to)
          snd.send(r)
        except SendError, e:
          print e
        finally:
          snd.close()

      session.acknowledge(msg)

def get_status(msg):
  return msg.content["identity"], msg.content["status"], msg.content["owner"]

FREE = "free"
BUSY = "busy"

def match(value, patterns):
  for p in patterns:
    if fnmatch(value, p):
      return True
  return False
