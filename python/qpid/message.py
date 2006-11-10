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
from sets import Set

class Message:

  COMMON_FIELDS = Set(("content", "method", "fields"))

  def __init__(self, method, fields, content = None):
    self.method = method
    self.fields = fields
    self.content = content

  def __len__(self):
    l = len(self.fields)
    if self.method.content:
      l += 1
    return len(self.fields)

  def _idx(self, idx):
    if idx < 0: idx += len(self)
    if idx < 0 or idx > len(self):
      raise IndexError(idx)
    return idx

  def __getitem__(self, idx):
    idx = self._idx(idx)
    if idx == len(self.fields):
      return self.content
    else:
      return self.fields[idx]

  def __setitem__(self, idx, value):
    idx = self._idx(idx)
    if idx == len(self.fields):
      self.content = value
    else:
      self.fields[idx] = value

  def _slot(self, attr):
    if attr in Message.COMMON_FIELDS:
      env = self.__dict__
      key = attr
    else:
      env = self.fields
      try:
        field = self.method.fields.bypyname[attr]
        key = self.method.fields.index(field)
      except KeyError:
        raise AttributeError(attr)
    return env, key

  def __getattr__(self, attr):
    env, key = self._slot(attr)
    return env[key]

  def __setattr__(self, attr, value):
    env, key = self._slot(attr)
    env[attr] = value

  STR = "%s %s content = %s"
  REPR = STR.replace("%s", "%r")

  def __str__(self):
    return Message.STR % (self.method, self.fields, self.content)

  def __repr__(self):
    return Message.REPR % (self.method, self.fields, self.content)
