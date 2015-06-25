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


from logging import getLogger

log = getLogger("qpid.saslmech")

def get_sasl_mechanism(mechanismNames, username, password, namespace="qpid.saslmech", sasl_options=None):
  """Given a list of SASL mechanism names, dynamically loads a SASL implementation
     from namespace qpid.sasl.mech respecting a mechanism priority"""

  log.debug("Supported mechanism : %s", mechanismNames)

  instances = []
  for mechanismName in mechanismNames:
    convertedName = mechanismName.replace("-","_")
    canonicalName = "%s.%s.%s" % (namespace, convertedName.lower(), convertedName)
    try:
      log.debug("Checking for SASL implementation %s for mechanism %s", canonicalName, mechanismName)
      clazz = _get_class(canonicalName)
      log.debug("Found SASL implementation")
      instance = clazz(username, password, mechanismName, sasl_options)
      if (instance.prerequisitesOk()):
        instances.append(instance)
      else:
        log.debug("SASL mechanism %s unavailable as the prerequistes for this mechanism have not been met", mechanismName)
    except (ImportError, AttributeError), e:
      # Unknown mechanism - this is normal if the server supports mechanism that the client does not
      log.debug("Could not load implementation for %s", canonicalName)
      pass

  if instances:
    instances.sort(key=lambda x : x.priority(), reverse=True)
    sasl = instances[0]
    log.debug("Selected SASL mechanism %s", sasl.mechanismName())
    return sasl
  else:
    return None

def _get_class( kls ):
  parts = kls.split('.')
  module = ".".join(parts[:-1])
  m = __import__( module )
  for comp in parts[1:]:
    m = getattr(m, comp)
  return m



