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
from qpid.saslmech.finder import get_sasl_mechanism
from my_sasl import MY_SASL
from my_sasl2 import MY_SASL2

class SaslFinderTests (TestCase):
  """Tests the ability to chose the a sasl mechanism from those available to be loaded"""

  def test_known_mechansim(self):

    supportedMechs = ["MY-SASL"]

    mech = get_sasl_mechanism(supportedMechs, "myuser", "mypass", namespace="qpid.tests.saslmech")

    self.assertTrue(isinstance(mech, MY_SASL), "Mechanism %s is of unexpected type" % mech)
    self.assertEquals("MY-SASL", mech.mechanismName())
    self.assertTrue(mech.sasl_options is None)

  def test_unknown_mechansim(self):

    supportedMechs = ["not_a_mech"]

    mech = get_sasl_mechanism(supportedMechs, "myuser", "mypass", namespace="qpid.tests.saslmech")

    self.assertTrue(mech == None, "Mechanism instance should be none")

  def test_sasl_mechanism_with_higher_priority_prefered(self):

    supportedMechs = ["MY-SASL", "MY-SASL2"]

    mech = get_sasl_mechanism(supportedMechs, "myuser", "mypass", namespace="qpid.tests.saslmech")

    self.assertTrue(isinstance(mech, MY_SASL), "Mechanism %s is of unexpected type" % mech)

  def test_sasl_mechanism_fallback_without_credentials(self):

    # MY-SASL requires username/password, MY-SASL2 does not
    supportedMechs = ["MY-SASL", "MY-SASL2"]

    mech = get_sasl_mechanism(supportedMechs, None, None, namespace="qpid.tests.saslmech")

    self.assertTrue(isinstance(mech, MY_SASL2), "Mechanism %s is of unexpected type" % mech)

  def test_sasl_mechansim_options(self):

    supportedMechs = ["MY-SASL"]

    sasl_options = {'hello': 'world'}
    mech = get_sasl_mechanism(supportedMechs, "myuser", "mypass", namespace="qpid.tests.saslmech", sasl_options=sasl_options)

    self.assertTrue(isinstance(mech, MY_SASL), "Mechanism %s is of unexpected type" % mech)
    self.assertEquals(sasl_options, mech.sasl_options)
