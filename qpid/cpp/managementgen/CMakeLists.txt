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
project(qpidc-qmfgen)
cmake_minimum_required(VERSION 2.4.0 FATAL_ERROR)

install(PROGRAMS qmf-gen DESTINATION managementgen
        COMPONENT all-source)
install(FILES    qmfgen/__init__.py
                 qmfgen/generate.py
                 qmfgen/schema.py
                 qmfgen/templates/Args.h
                 qmfgen/templates/Class.cpp
                 qmfgen/templates/Class.h
                 qmfgen/templates/Event.cpp
                 qmfgen/templates/Event.h
                 qmfgen/templates/Makefile.mk
                 qmfgen/templates/Package.cpp
                 qmfgen/templates/Package.h
                 qmfgen/management-types.xml
        DESTINATION managementgen
        COMPONENT all-source)
