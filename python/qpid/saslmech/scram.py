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

from hmac import HMAC
from binascii import b2a_hex
from sasl import Sasl
import os
import base64

class SCRAM_base(Sasl):

  def __init__(self, algorithm, user, password, name, sasl_options=None):
    Sasl.__init__(self, user, password, name, sasl_options)
    self.algorithm = algorithm
    self.client_nonce = b2a_hex(os.urandom(16))
    self.server_signature = None

  def initialResponse(self):
    name = self.user.replace("=","=3D").replace(",","=2C")
    self.client_first_message = "n=" + name + ",r=" + self.client_nonce
    return "n,," + self.client_first_message


  def response(self, challenge):
    if(self.server_signature):
      self.evaluateOutcome(challenge)
      return ""
    else:
      serverChallenge, salt, iterations = challenge.split(",")
      self.server_nonce = serverChallenge[2:]
      if self.server_nonce.find(self.client_nonce) != 0:
        raise SaslException("Server nonce does not start with client nonce")
      self.salt = base64.b64decode(salt[2:])

      iterations = int(iterations[2:])

      hmac = HMAC(key=self.password.replace("=","=3D").replace(",","=2C"),digestmod=self.algorithm)

      hmac.update(self.salt)
      hmac.update("\x00\x00\x00\x01")

      saltedPassword = hmac.digest()
      previous = saltedPassword

      for i in range(1,iterations):
        hmac = HMAC(key=self.password.replace("=","=3D").replace(",","=2C"),digestmod=self.algorithm)
        hmac.update(previous)
        previous = hmac.digest()
        saltedPassword = ''.join(chr(ord(a) ^ ord(b)) for a,b in zip(saltedPassword,previous))

      clientFinalMessageWithoutProof = "c=" + base64.b64encode("n,,") + ",r=" + self.server_nonce
      authMessage = self.client_first_message + "," + challenge + "," + clientFinalMessageWithoutProof

      clientKey = HMAC(key=saltedPassword,msg="Client Key",digestmod=self.algorithm).digest()
      hashFunc = self.algorithm()
      hashFunc.update(clientKey)
      storedKey = hashFunc.digest()

      clientSignature = HMAC(key=storedKey, msg=authMessage, digestmod=self.algorithm).digest()

      clientProof = ''.join(chr(ord(a) ^ ord(b)) for a,b in zip(clientKey,clientSignature))

      serverKey = HMAC(key=saltedPassword,msg="Server Key",digestmod=self.algorithm).digest()

      self.server_signature = HMAC(key=serverKey,msg=authMessage,digestmod=self.algorithm).digest()
      return clientFinalMessageWithoutProof + ",p=" + base64.b64encode(clientProof)


  def evaluateOutcome(self, challenge):
    serverVerification = challenge.split(",")[0]

    serverSignature = base64.b64decode(serverVerification[2:])
    if serverSignature != self.server_signature:
      raise SaslException("Server verification failed")
    return
