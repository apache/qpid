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

class Context:

  def __init__(self):
    self.containers = []

  def push(self, o):
    self.containers.append(o)

  def pop(self):
    return self.containers.pop()

class Values:

  def __init__(self, *values):
    self.values = values

  def validate(self, o, ctx):
    if not o in self.values:
      return "%s not in %s" % (o, self.values)

  def __str__(self):
    return self.value

class Types:

  def __init__(self, *types):
    self.types = types

  def validate(self, o, ctx):
    for t in self.types:
      if isinstance(o, t):
        return
    if len(self.types) == 1:
      return "%s is not a %s" % (o, self.types[0].__name__)
    else:
      return "%s is not one of: %s" % (o, ", ".join([t.__name__ for t in self.types]))

class List:

  def __init__(self, condition):
    self.condition = condition

  def validate(self, o, ctx):
    if not isinstance(o, list):
      return "%s is not a list" % o

    ctx.push(o)
    for v in o:
      err = self.condition.validate(v, ctx)
      if err: return err

class Map:

  def __init__(self, map, restricted=True):
    self.map = map
    self.restricted = restricted

  def validate(self, o, ctx):
    errors = []

    if not hasattr(o, "get"):
      return "%s is not a map" % o

    ctx.push(o)
    for k, t in self.map.items():
      v = o.get(k)
      if v is not None:
        err = t.validate(v, ctx)
        if err: errors.append("%s: %s" % (k, err))
    if self.restricted:
      for k in o:
        if not k in self.map:
          errors.append("%s: illegal key" % k)
    ctx.pop()

    if errors:
      return ", ".join(errors)

class And:

  def __init__(self, *conditions):
    self.conditions = conditions

  def validate(self, o, ctx):
    for c in self.conditions:
      err = c.validate(o, ctx)
      if err:
        return err
