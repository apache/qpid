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

import spec, codec, connection, content, peer, delegate, client

class Struct:

  def __init__(self, type):
    self.__dict__["type"] = type
    self.__dict__["_values"] = {}

  def _check(self, attr):
    field = self.type.fields.byname.get(attr)
    if field == None:
      raise AttributeError(attr)
    return field

  def has(self, name):
    return self.type.fields.byname.has_key(name)

  def set(self, attr, value):
    self._check(attr)
    self._values[attr] = value

  def get(self, attr):
    field = self._check(attr)
    return self._values.get(attr, field.default())

  def __setattr__(self, attr, value):
    self.set(attr, value)

  def __getattr__(self, attr):
    return self.get(attr)

  def __setitem__(self, attr, value):
    self.set(attr, value)

  def __getitem__(self, attr):
    return self.get(attr)

  def __str__(self):
    return "%s %s" % (self.type.type, self._values)
