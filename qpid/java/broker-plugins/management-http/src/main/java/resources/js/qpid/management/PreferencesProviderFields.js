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
        "dojo/dom",
        "dojo/query",
        "dojo/parser",
        "dojo/string",
        "dojo/json",
        "dojo/store/Memory",
        "dijit/registry",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dojox/html/entities",
        "dojo/text!../../showPreferencesProviderFields.html",
        "dojo/text!service/helper?action=ListPreferencesProvidersTypes",
        "qpid/common/util",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"], function (xhr, dom, query, parser, string, json, Memory, registry, FilteringSelect, ValidationTextBox, entities, template, preferencesProvidersTypes, util) {

  var PreferencesProviderFields = {};

  var types = [{id: "", name: "None"}];
  var typesArray = json.parse(preferencesProvidersTypes);
  for (var i =0 ; i < typesArray.length; i++)
  {
    types.push({id: typesArray[i], name: typesArray[i]});
  }

  var selectPreferencesProviderType = function(type)
  {
    if(type && string.trim(type) != "")
    {
      var disableOnEditing = PreferencesProviderFields.data? true: false;
      PreferencesProviderFields.name.set("disabled", disableOnEditing);
      PreferencesProviderFields.type.set("disabled", disableOnEditing);
      if (PreferencesProviderFields.currentType != type)
      {
        require(["qpid/management/authenticationprovider/preferences/" + type.toLowerCase() + "/add"],
        function(typeFields)
        {
          PreferencesProviderFields.currentType = null;
          if (PreferencesProviderFields.details )
          {
            PreferencesProviderFields.details.destroy();
          }
          PreferencesProviderFields.details = typeFields;
          typeFields.show(PreferencesProviderFields.fieldsContainer, PreferencesProviderFields.data);
          PreferencesProviderFields.currentType = type;
         });
      }
      else
      {
        PreferencesProviderFields.details.disable(false);
      }
    }
    else
    {
      PreferencesProviderFields.disable(true);
    }
  };

  PreferencesProviderFields.init = function(data)
  {
    this.data = data;
    this.id.value = data.id;
    this.name.set("value", data.name);
    this.type.set("value", data.type);
    selectPreferencesProviderType(data.type);
  };

  PreferencesProviderFields.show = function(node, provider, authenticationProviderName)
  {
    this.currentType = null;
    this.data = null;
    node.innerHTML = template;
    try
    {
      parser.parse(node);
    }
    catch(e)
    {
      console.error(e);
      return;
    }

    if (this.container)
    {
      this.container.destroyRecursive();
    }
    if (this.details )
    {
      this.details.destroy();
      delete this["details"];
    }
    this.container = registry.byNode(query(".preferencesProviderContainer", node)[0]);
    this.fieldsContainer = query(".preferencesProviderFieldsContainer", node)[0];
    this.type = registry.byNode(query(".preferencesProviderType", node)[0]);
    this.name = registry.byNode(query(".preferencesProviderName", node)[0]);
    this.name.set("regExpGen", util.nameOrContextVarRegexp);
    this.id = query("input[name='preferencesProviderId']", node)[0];
    this.id.value = null;
    this.type.set("store", new Memory({ data: types, idProperty: "id"}));
    this.type.on("change", selectPreferencesProviderType );
    this.type.startup();

    if (provider)
    {
      if (typeof provider == "object")
      {
        this.init(provider);
      }
      else if (typeof provider == "string")
      {
        var that = this;
        xhr.get({
          url: "api/latest/preferencesprovider/"  +encodeURIComponent(authenticationProviderName) + "/" + encodeURIComponent(provider),
          sync: true,
          content: { actuals: true },
          handleAs: "json"
        }).then(function(data){if (data && data[0]) { that.init(data[0]);}});
      }
    }
    else
    {
      this.disable(true);
    }
  };

  PreferencesProviderFields.disable = function(val)
  {
    this.name.set("disabled", val);
    if (this.details)
    {
      this.details.disable(val);
    }
  };

  PreferencesProviderFields.getValues = function()
  {
    var values = {};
    if (this.details)
    {
      values = this.details.getValues() || {};
    }
    values.name = this.name.get("value");
    if (this.id.value)
    {
      values.id = this.id.value;
    }
    values.type = this.type.get("value");
    return values;
  };

  PreferencesProviderFields.save = function(authenticationProviderName)
  {
    var success = true;
    if (this.type.value)
    {
      var data = this.getValues();
      xhr.put({url: "api/latest/preferencesprovider/"  +encodeURIComponent(authenticationProviderName) + "/" + encodeURIComponent(data.name),
        sync: true,
        handleAs: "json",
        headers: { "Content-Type": "application/json"},
        putData: json.stringify(data),
        load: function(x) {success = true;},
        error: function(error) {success = false; alert("Preferences Provider Error: " + error);}});
    }
    return success;
  };

  return PreferencesProviderFields;

});