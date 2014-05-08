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
    function JsonNode(data)
    {
      var containerNode = data.containerNode;
      this.parent = data.parent;
      var that = this;
      xhr.get({url: "virtualhostnode/json/show.html",
        sync: true,
        load:  function(template) {
          containerNode.innerHTML = template;
          parser.parse(containerNode);
        }});
      this.storePath = query(".storePath", containerNode)[0];
    }

    JsonNode.prototype.update=function(data)
    {
      this.storePath.innerHTML = entities.encode(String(data.storePath));
    };

    return JsonNode;
});
