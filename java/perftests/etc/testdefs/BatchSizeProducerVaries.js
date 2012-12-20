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

var jsonObject = {
    _tests:[]
};

var duration = 30000;

var txBatchSizes = [[1,1], [2,1], [5,1], [10,1], [20,1], [50,1], [100,1], [200,1], [400,1]];

var acknowledgeMode = 0;
var deliveryMode = 2;
var messageSize = 1024;

for(i=0; i < txBatchSizes.length ; i++)
{
    var producerBatchSize = txBatchSizes[i][0];
    var consumerBatchSize = txBatchSizes[i][1];
    var queueName = "txBatchSize" + producerBatchSize + "_" + consumerBatchSize;
    var destination = "direct://amq.direct//" + queueName + "?durable='true'";

    var test = {
      "_name": producerBatchSize,// hack - use test name to expose the producer batch size on the All result rows
      "_queues":[
        {
          "_name": queueName,
          "_durable": true
        }
      ],
      "_clients":[
        {
          "_name": "producingClient",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_acknowledgeMode": acknowledgeMode,
                  "_producers": [
                    {
                      "_name": "Producer1",
                      "_destinationName": destination,
                      "_messageSize": messageSize,
                      "_deliveryMode": deliveryMode,
                      "_batchSize": producerBatchSize,
                      "_maximumDuration": duration
                    }
                  ]
                }
              ]
            }
          ]
        },
        {
          "_name": "consumingClient",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_acknowledgeMode": acknowledgeMode,
                  "_consumers": [
                    {
                      "_name": "Consumer1",
                      "_destinationName": destination,
                      "_batchSize": consumerBatchSize,
                      "_maximumDuration": duration
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    };

    jsonObject._tests= jsonObject._tests.concat(test);
}
