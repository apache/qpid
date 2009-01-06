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
This module provides document parsing and transformation utilities for
both SGML and XML.
"""

import os, dom, transforms, parsers, sys
import xml.sax, types
from cStringIO import StringIO

def transform(node, *args):
  result = node
  for t in args:
    if isinstance(t, types.ClassType):
      t = t()
    result = result.dispatch(t)
  return result

def sgml_parse(source):
  if isinstance(source, basestring):
    source = StringIO(source)
    fname = "<string>"
  elif hasattr(source, "name"):
    fname = source.name
  p = parsers.SGMLParser()
  num = 1
  for line in source:
    p.feed(line)
    p.parser.line(fname, num, None)
    num += 1
  p.close()
  return p.parser.tree

def xml_parse(filename):
  if sys.version_info[0:2] == (2,3):
    # XXX: this is for older versions of python
    source = "file://%s" % os.path.abspath(filename) 
  else:
    source = filename
  p = parsers.XMLParser()
  xml.sax.parse(source, p)
  return p.parser.tree

def sexp(node):
  s = transforms.Sexp()
  node.dispatch(s)
  return s.out
