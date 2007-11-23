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
parser = OptionParser ()
parser.add_option ("-o", "--outDir", dest="outdir", metavar="DIR",
                   help="Destination directory for generated files")
parser.add_option ("-t", "--typeFile", dest="typefile", metavar="FILE",
                   help="Schema type document (XML file)")
parser.add_option ("-s", "--schemaFile", dest="schemafile", metavar="FILE",
                   help="Schema defintion document (XML file)")
parser.add_option ("-i", "--templateDir", dest="templatedir", metavar="DIR",
                   help="Directory where template files can be found")
parser.add_option ("-m", "--makefile", dest="makefile", metavar="FILE",
                   help="Makefile fragment")

(opts, args) = parser.parse_args ()

if opts.outdir      == None or \
   opts.typefile    == None or \
   opts.schemafile  == None or \
   opts.templatedir == None or \
   opts.makefile    == None:
  parser.error ("Incorrect options, see --help for help")

gen    = Generator     (opts.outdir,   opts.templatedir)
schema = PackageSchema (opts.typefile, opts.schemafile)

gen.makeClassFiles  ("Class.h",   schema)
gen.makeClassFiles  ("Class.cpp", schema)
gen.makeMethodFiles ("Args.h",    schema)
gen.makeMakeFile    (opts.makefile)
