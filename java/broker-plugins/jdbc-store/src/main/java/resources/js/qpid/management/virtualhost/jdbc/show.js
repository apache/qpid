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
define(["qpid/common/util", "dojo/query", "dojo/domReady!"],
  function (util, query)
  {
    var fieldNames = ["connectionUrl", "username", "connectionPoolType"];

    function JDBC(data)
    {
        util.buildUI(data.containerNode, data.parent, "virtualhostnode/jdbc/show.html", fieldNames, this);
        this.usernameAttributeContainer=query(".usernameAttributeContainer", data.containerNode)[0];
        this.connectionPoolTypeAttributeContainer=query(".connectionPoolTypeAttributeContainer", data.containerNode)[0];
    }

    JDBC.prototype.update = function(data)
    {
        util.updateUI(data, fieldNames, this);
        this.usernameAttributeContainer.style.display = data.username ? "block" : "none";
        if (!this.poolDetails)
        {
            var that = this;
            require(["qpid/management/store/pool/" + data.connectionPoolType.toLowerCase() + "/show"],
              function(PoolDetails)
              {
                that.poolDetails = new PoolDetails({containerNode:that.connectionPoolTypeAttributeContainer, parent: that});
                that.poolDetails.update(data);
              }
            );
        }
        else
        {
            this.poolDetails.update(data);
        }
    }

    return JDBC;
  }
);
