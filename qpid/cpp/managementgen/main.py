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
#   http:#www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

from schema   import PackageSchema, SchemaClass
from generate import Generator
from optparse import OptionParser

# Set command line options
usage  = "usage: %prog [options] schema-document type-document template-directory out-directory"
parser = OptionParser (usage=usage)
parser.add_option ("-m", "--makefile", dest="makefile", metavar="FILE",
                   help="Makefile fragment")
parser.add_option ("-i", "--include-prefix", dest="include_prefix", metavar="PATH",
                   default="qpid/management/",
                   help="Prefix for #include of generated headers in generated source, default: qpid/management/")

(opts, args) = parser.parse_args ()

if len (args) < 4:
  parser.error ("Too few arguments")

schemafile  = args[0]
typefile    = args[1]
templatedir = args[2]
outdir      = args[3]

if opts.include_prefix == ".":
  opts.include_prefix = None

gen    = Generator     (outdir,   templatedir)
schema = PackageSchema (typefile, schemafile, opts)

gen.makeClassFiles  ("Class.h",   schema)
gen.makeClassFiles  ("Class.cpp", schema)
gen.makeMethodFiles ("Args.h",    schema)

if opts.makefile != None:
  gen.makeSingleFile ("Makefile.mk", opts.makefile, force=True)
