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
define(["qpid/common/util", "dojo/query", "dojo/_base/array", "dojo/dom-construct", "dijit/registry", "dojo/domReady!"],
  function (util, query, array, domConstruct, registry)
  {
    var fieldNames = ["connectionUrl", "username", "connectionPoolType"];

    function JDBC(data)
    {
        var that = this;
        util.buildUI(data.containerNode, data.parent, "virtualhostnode/jdbc/show.html", fieldNames, this, function()
        {
            that.usernameAttributeContainer=query(".usernameAttributeContainer", data.containerNode)[0];
            that.connectionPoolTypeAttributeContainer=query(".connectionPoolTypeAttributeContainer", data.containerNode)[0];
        });
    }

    JDBC.prototype.update = function(data)
    {
        var previousConnectionPoolType = this.connectionPoolType ? this.connectionPoolType.innerHTML : null;
        util.updateUI(data, fieldNames, this);
        this.usernameAttributeContainer.style.display = data.username ? "block" : "none";
        if (data.connectionPoolType && (!this.poolDetails || previousConnectionPoolType != data.connectionPoolType))
        {
            var that = this;
            require(["qpid/management/store/pool/" + data.connectionPoolType.toLowerCase() + "/show"],
              function(PoolDetails)
              {
                var widgets = registry.findWidgets(that.connectionPoolTypeAttributeContainer);
                array.forEach(widgets, function(item) { item.destroyRecursive();});
                domConstruct.empty(that.connectionPoolTypeAttributeContainer);

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
