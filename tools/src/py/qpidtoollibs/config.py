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

"""Utilities for managing configuration files"""
import os

QPID_ENV_PREFIX="QPID_"

def parse_qpidd_conf(config_file):
    """Parse a qpidd.conf configuration file into a dictionary"""
    f =  open(config_file)
    try:
        clean = filter(None, [line.split("#")[0].strip() for line in f]) # Strip comments and blanks
        def item(line): return [x.strip() for x in line.split("=")]
        config = dict(item(line) for line in clean if "=" in line)
    finally: f.close()
    def name(env_name): return env_name[len(QPID_ENV_PREFIX):].lower()
    env = dict((name(i[0]), i[1]) for i in os.environ.iteritems() if i[0].startswith(QPID_ENV_PREFIX))
    config.update(env)          # Environment takes precedence
    return config
