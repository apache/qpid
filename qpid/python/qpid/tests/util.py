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
from unittest import TestCase
from qpid.util import get_client_properties_with_defaults

class UtilTest (TestCase):

  def test_get_spec_recommended_client_properties(self):
    client_properties = get_client_properties_with_defaults(provided_client_properties={"mykey":"myvalue"})
    self.assertTrue("product" in client_properties)
    self.assertTrue("version" in client_properties)
    self.assertTrue("platform" in client_properties)

  def test_get_client_properties_with_provided_value(self):
    client_properties = get_client_properties_with_defaults(provided_client_properties={"mykey":"myvalue"})
    self.assertTrue("product" in client_properties)
    self.assertTrue("mykey" in client_properties)
    self.assertEqual("myvalue", client_properties["mykey"])

  def test_get_client_properties_with_no_provided_values(self):
    client_properties = get_client_properties_with_defaults(provided_client_properties=None)
    self.assertTrue("product" in client_properties)

    client_properties = get_client_properties_with_defaults()
    self.assertTrue("product" in client_properties)

  def test_get_client_properties_with_provided_value_that_overrides_default(self):
    client_properties = get_client_properties_with_defaults(provided_client_properties={"version":"myversion"})
    self.assertEqual("myversion", client_properties["version"])

