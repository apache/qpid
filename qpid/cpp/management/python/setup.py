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

import platform

from distutils.core import setup

pypi_long_description = """
# Python libraries for the Apache Qpid C++ broker

## qmf

The Qpid Management Framework (QMF).

## qpidtoollibs

A high-level BrokerAgent object for managing the C++ broker using QMF.

This library depends on the qpid.messaging python client to send AMQP
messages containing QMF commands to the broker.
"""

scripts = [
    "bin/qpid-config",
    "bin/qpid-ha",
    "bin/qpid-printevents",
    "bin/qpid-queue-stats",
    "bin/qpid-route",
    "bin/qpid-stat",
    "bin/qpid-tool",
]

if platform.system() == "Windows":
    scripts.append("bin/qpid-config.bat")
    scripts.append("bin/qpid-ha.bat")
    scripts.append("bin/qpid-printevents.bat")
    scripts.append("bin/qpid-queue-stats.bat")
    scripts.append("bin/qpid-route.bat")
    scripts.append("bin/qpid-stat.bat")
    scripts.append("bin/qpid-tool.bat")

setup(name="qpid-tools",
      version="0.35",
      author="Apache Qpid",
      author_email="users@qpid.apache.org",
      package_dir={'' : 'lib'},
      packages=["qpidtoollibs", "qmf"],
      scripts=scripts,
      data_files=[("libexec", ["bin/qpid-qls-analyze"]),
                  ("share/qpid-tools/python/qlslibs",
                   ["lib/qlslibs/__init__.py",
                    "lib/qlslibs/analyze.py",
                    "lib/qlslibs/efp.py",
                    "lib/qlslibs/err.py",
                    "lib/qlslibs/jrnl.py",
                    "lib/qlslibs/utils.py"])],
      url="http://qpid.apache.org/",
      license="Apache Software License",
      description="Python libraries for the Apache Qpid C++ broker",
      long_description=pypi_long_description,
      install_requires=["qpid-python >= 0.26",])
