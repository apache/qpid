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

import sys

# TODO: need a better naming for this class now that it does the value
# stuff
class Invoker:

  def METHOD(self, name, resolved):
    method = lambda *args, **kwargs: self.invoke(resolved, args, kwargs)
    if sys.version_info[:2] > (2, 3):
      method.__name__ = resolved.pyname
      method.__doc__ = resolved.pydoc
      method.__module__ = self.__class__.__module__
    self.__dict__[name] = method
    return method

  def VALUE(self, name, resolved):
    self.__dict__[name] = resolved
    return resolved

  def ERROR(self, name, resolved):
    raise AttributeError("%s instance has no attribute '%s'" %
                         (self.__class__.__name__, name))

  def resolve_method(self, name):
    return ERROR, None

  def __getattr__(self, name):
    disp, resolved = self.resolve_method(name)
    return disp(name, resolved)
