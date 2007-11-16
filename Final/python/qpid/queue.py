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
This module augments the standard python multithreaded Queue
implementation to add a close() method so that threads blocking on the
content of a queue can be notified if the queue is no longer in use.
"""

from Queue import Queue as BaseQueue, Empty, Full

class Closed(Exception): pass

class Queue(BaseQueue):

  END = object()

  def close(self):
    self.put(Queue.END)

  def get(self, block = True, timeout = None):
    result = BaseQueue.get(self, block, timeout)
    if result == Queue.END:
      # this guarantees that any other waiting threads or any future
      # calls to get will also result in a Closed exception
      self.put(Queue.END)
      raise Closed()
    else:
      return result
