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
from distutils.core import setup

setup(name="qpid-tools",
      version="0.14",
      author="Apache Qpid",
      author_email="dev@qpid.apache.org",
      scripts=["src/py/qpid-cluster",
               "src/py/qpid-cluster-store",
               "src/py/qpid-config",
               "src/py/qpid-printevents",
               "src/py/qpid-queue-stats",
               "src/py/qpid-route",
               "src/py/qpid-stat",
               "src/py/qpid-tool",
               "src/py/qmf-tool"],
      url="http://qpid.apache.org/",
      license="Apache Software License",
      description="Diagnostic and management tools for Apache Qpid brokers.")
