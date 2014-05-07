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
        "dijit/registry",
        "qpid/common/UpdatableStore",
        "dojo/domReady!"],
  function (xhr, lang, connect, parser, json, entities, query, json, registry, UpdatableStore)
  {
    function BdbNode(containerNode)
    {
      var that = this;
      xhr.get({url: "virtualhostnode/bdb/show.html",
        sync: true,
        load:  function(template) {
          containerNode.innerHTML = template;
          parser.parse(containerNode);
        }});
      this.storePath = query(".storePath", containerNode)[0];
      this.environmentConfigurationPanel = registry.byNode(query(".environmentConfigurationPanel", containerNode)[0]);
      this.environmentConfigurationGrid = new UpdatableStore([],
          query(".environmentConfiguration", containerNode)[0],
          [ {name: 'Name', field: 'id', width: '50%'}, {name: 'Value', field: 'value', width: '50%'} ],
          null,
          null,
          null, true );
    }

    BdbNode.prototype.update=function(data)
    {
      this.storePath.innerHTML = entities.encode(String(data.storePath));
      if (data.environmentConfiguration)
      {
        this.environmentConfigurationPanel.domNode.style.display="block";
        var conf = data.environmentConfiguration;
        var settings = [];
        for(var propName in conf)
        {
          if(conf.hasOwnProperty(propName))
          {
            settings.push({"id": propName, "value": conf[propName]});
          }
        }
        var changed = this.environmentConfigurationGrid.update(settings);
        if (changed)
        {
          this.environmentConfigurationGrid.grid._refresh();
        }
      }
      else
      {
        this.environmentConfigurationPanel.domNode.style.display="none";
      }
    };

    return BdbNode;
});
