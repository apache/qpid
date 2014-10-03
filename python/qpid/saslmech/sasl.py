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

class SaslException(Exception): pass

class Sasl:

  def __init__(self, user, password, name, sasl_options = None):
    self.user = user
    self.password = password
    self.name = name
    self.sasl_options = sasl_options

  def prerequisitesOk(self):
    return self.user is not None and self.password is not None

  def initialResponse(self):
    return

  def response(self, challenge):
    return

  def priority(self):
    """Priority of the mechanism.  Mechanism with a higher value will be chosen in preference to those with a lower priority"""
    return 1

  def mechanismName(self):
    return self.name
