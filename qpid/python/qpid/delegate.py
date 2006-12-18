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
Delegate implementation intended for use with the peer module.
"""

import threading, inspect
from spec import pythonize

class Delegate:

  def __init__(self):
    self.handlers = {}
    self.invokers = {}
    # initialize all the mixins
    self.invoke_all("init")

  def invoke_all(self, meth, *args, **kwargs):
    for cls in inspect.getmro(self.__class__):
      if hasattr(cls, meth):
        getattr(cls, meth)(self, *args, **kwargs)

  def dispatch(self, channel, message):
    method = message.method

    try:
      handler = self.handlers[method]
    except KeyError:
      name = "%s_%s" % (pythonize(method.klass.name),
                        pythonize(method.name))
      handler = getattr(self, name)
      self.handlers[method] = handler

    return handler(channel, message)

  def close(self, reason):
    self.invoke_all("close", reason)
