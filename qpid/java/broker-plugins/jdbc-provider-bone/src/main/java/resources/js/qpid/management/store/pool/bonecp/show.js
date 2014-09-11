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
define(["dojo/_base/xhr",
        "dojo/_base/lang",
        "dojo/_base/connect",
        "dojo/parser",
        "dojo/string",
        "dojox/html/entities",
        "dojo/query",
        "dojo/json",
        "dojo/domReady!"],
  function (xhr, lang, connect, parser, json, entities, query, json)
  {
    var fieldNames = ["maxConnectionsPerPartition", "minConnectionsPerPartition", "partitionCount"];

    function BoneCP(data)
    {
      var containerNode = data.containerNode;
      this.parent = data.parent;
      var that = this;
      xhr.get({url: "store/pool/bonecp/show.html",
        sync: true,
        load:  function(template) {
          containerNode.innerHTML = template;
          parser.parse(containerNode);
        }});
      for(var i=0; i<fieldNames.length;i++)
      {
        var fieldName = fieldNames[i];
        this[fieldName]= query("." + fieldName, containerNode)[0];
      }
    }

    BoneCP.prototype.update=function(data)
    {

      for(var i=0; i<fieldNames.length;i++)
      {
        var fieldName = fieldNames[i];
        var value = data && data.context ? data.context["qpid.jdbcstore.bonecp."+fieldName] : "";
        this[fieldName].innerHTML= value?entities.encode(String(value)):"";
      }
    };

    return BoneCP;
});
