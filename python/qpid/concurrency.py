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

import inspect, time

def synchronized(meth):
  args, vargs, kwargs, defs = inspect.getargspec(meth)
  scope = {}
  scope["meth"] = meth
  exec """
def %s%s:
  %s
  %s._lock.acquire()
  try:
    return meth%s
  finally:
    %s._lock.release()
""" % (meth.__name__, inspect.formatargspec(args, vargs, kwargs, defs),
       repr(inspect.getdoc(meth)), args[0],
       inspect.formatargspec(args, vargs, kwargs, defs,
                             formatvalue=lambda x: ""),
       args[0]) in scope
  return scope[meth.__name__]

class Waiter(object):

  def __init__(self, condition):
    self.condition = condition

  def wait(self, predicate, timeout=None):
    passed = 0
    start = time.time()
    while not predicate():
      if timeout is None:
        # using the timed wait prevents keyboard interrupts from being
        # blocked while waiting
        self.condition.wait(3)
      elif passed < timeout:
        self.condition.wait(timeout - passed)
      else:
        return False
      passed = time.time() - start
    return True

  def notify(self):
    self.condition.notify()

  def notifyAll(self):
    self.condition.notifyAll()
