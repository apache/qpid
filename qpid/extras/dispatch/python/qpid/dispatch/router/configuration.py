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

class Configuration(object):
    """
    This module manages and holds the configuration and tuning parameters for a router.
    """
    def __init__(self, overrides={}):
        ##
        ## Load default values
        ##
        self.values = { 'hello_interval'      :  1.0,
                        'hello_max_age'       :  3.0,
                        'ra_interval'         : 30.0,
                        'remote_ls_max_age'   : 60.0,
                        'mobile_addr_max_age' : 60.0  }

        ##
        ## Apply supplied overrides
        ##
        for k, v in overrides.items():
            self.values[k] = v

    def __getattr__(self, key):
        if key in self.values:
            return self.values[key]
        raise KeyError

    def __repr__(self):
        return "%r" % self.values

