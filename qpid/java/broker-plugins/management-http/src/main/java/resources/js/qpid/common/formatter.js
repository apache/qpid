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

define(function () {
    return {

        formatBytes: function formatBytes(amount)
        {
            var returnVal = { units: "B",
                              value: "0"};


            if(amount < 1000)
            {
                returnVal.value = amount.toPrecision(3);;
            }
            else if(amount < 1000 * 1024)
            {
                returnVal.units = "KB";
                returnVal.value = (amount / 1024).toPrecision(3);
            }
            else if(amount < 1000 * 1024 * 1024)
            {
                returnVal.units = "MB";
                returnVal.value = (amount / (1024 * 1024)).toPrecision(3);
            }
            else if(amount < 1000 * 1024 * 1024 * 1024)
            {
                returnVal.units = "GB";
                returnVal.value = (amount / (1024 * 1024 * 1024)).toPrecision(3);
            }

            return returnVal;

        },

        formatTime: function formatTime(amount)
        {
            var returnVal = { units: "ms",
                              value: "0"};

            if(amount < 1000)
            {
                returnVal.units = "ms";
                returnVal.value = amount.toString();
            }
            else if(amount < 1000 * 60)
            {
                returnVal.units = "s";
                returnVal.value = (amount / 1000).toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60)
            {
                returnVal.units = "min";
                returnVal.value = (amount / (1000 * 60)).toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24)
            {
                returnVal.units = "hr";
                returnVal.value = (amount / (1000 * 60 * 60)).toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24 * 7)
            {
                returnVal.units = "d";
                returnVal.value = (amount / (1000 * 60 * 60 * 24)).toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24 * 365)
            {
                returnVal.units = "wk";
                returnVal.value = (amount / (1000 * 60 * 60 * 24 * 7)).toPrecision(3);
            }
            else
            {
                returnVal.units = "yr";
                returnVal.value = (amount / (1000 * 60 * 60 * 24 * 365)).toPrecision(3);
            }

            return returnVal;
        }
    };
});