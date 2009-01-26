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

from spec010 import Control

def METHOD(module, inst):
  method = lambda self, *args, **kwargs: self.invoke(inst, args, kwargs)
  if sys.version_info[:2] > (2, 3):
    method.__name__ = str(inst.pyname)
    method.__doc__ = str(inst.pydoc)
    method.__module__ = module
  return method

def generate(spec, module, predicate=lambda x: True):
  dict = {"spec": spec}

  for name, enum in spec.enums.items():
    dict[name] = enum

  for name, st in spec.structs_by_name.items():
    dict[name] = METHOD(module, st)

  for st in spec.structs.values():
    dict[st.name] = METHOD(module, st)

  for name, inst in spec.instructions.items():
    if predicate(inst):
      dict[name] = METHOD(module, inst)

  return dict

def invoker(name, spec, predicate=lambda x: True):
  return type("%s_%s_%s" % (name, spec.major, spec.minor),
              (), generate(spec, invoker.__module__, predicate))

def command_invoker(spec):
  is_command = lambda cmd: cmd.track == spec["track.command"].value
  return invoker("CommandInvoker", spec, is_command)

def control_invoker(spec):
  is_control = lambda inst: isinstance(inst, Control)
  return invoker("ControlInvoker", spec, is_control)
