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

from StringIO import StringIO
import json
import sys
import re
import argparse

def transform(data):
  if isinstance(data, tuple):
    (k, v) = data;
    return k, transform(v)
  elif isinstance(data, list):
    return [transform(i) for i in data]
  elif isinstance(data, dict):
    if "name" in data and data["name"] == objname:
      data[attrname] = attrvalue
    return data
  else:
    return data


parser = argparse.ArgumentParser(description='Adds (or updates) a attribute name/value pair of an existing object within a Java Broker config.json')
parser.add_argument("objectname", help='Name of the object e.g. httpManagement')
parser.add_argument("attrname", help='Name of the attribute to add or update e.g. httpBasicAuthenticationEnabled')
parser.add_argument("attrvalue", help='Value of the attribute e.g. true')
args = parser.parse_args()

objname = args.objectname
attrname = args.attrname
attrvalue = args.attrvalue

# Expects a config.json to be provided on stdin, write the resulting json to stdout.

lines = []
for line in sys.stdin:
  lines.append(line.rstrip())

# naive strip C style comments - this deals with the Apache licence comment present on the default config
input = re.sub("/\*.*?\*/", " ", "".join(lines), re.S)

data = json.load(StringIO(input))
data = dict([transform((k, v)) for k,v in data.items()])

json.dump(data, sys.stdout, sort_keys=True, indent=2)
