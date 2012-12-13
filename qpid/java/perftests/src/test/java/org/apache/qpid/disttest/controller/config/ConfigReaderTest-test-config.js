/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
jsonObject = {
  "_tests":
    QPID.iterations( { "__ACK_MODE": [ 0, 1 ] },
      {
          // this is a comment - it wouldn't be allowed if this were pure JSON

          "_name": "Test 1",
          "_queues": [
            {
              "_name": "Json-Queue-Name"
            }
          ],

          "_clients": QPID.times(2,
            {
              "_name": "repeatingClient__CLIENT_INDEX",
              "_connections": [
                {
                  "_name": "connection1",
                  "_sessions": [
                    {
                      "_sessionName": "session1",
                      "_acknowledgeMode": "__ACK_MODE",
                      "_consumers": []
                    }
                  ]
                }
              ]
            },
            "__CLIENT_INDEX"
        )
    })

}
