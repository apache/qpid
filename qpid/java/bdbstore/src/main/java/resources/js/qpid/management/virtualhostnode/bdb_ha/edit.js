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
        "dojo/_base/array",
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/window",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        'dojo/json',
        "dojo/query",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dojo/text!virtualhostnode/bdb_ha/edit.html",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, array, event, lang, win, dom, domConstruct, registry, parser, json, query, Memory, ObjectStore, template)
  {
    var fields = [ "storePath", "name", "groupName", "address", "durability",
                   "coalescingSync", "designatedPrimary", "priority",  "quorumOverride"];

    var bdbHaNodeEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode);
        this.dialog = registry.byId("editBDBHANodeDialog")
        this.saveButton = registry.byId("editBDBHANode.saveButton");
        this.cancelButton = registry.byId("editBDBHANode.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        for(var i = 0; i < fields.length; i++)
        {
            var fieldName = fields[i];
            this[fieldName] = registry.byId("editBDBHANode." + fieldName);
        }
        this.form = registry.byId("editBDBHANodeForm");
      },
      show: function(nodeName)
      {
        var that=this;
        this.nodeName = nodeName;
        this.query = "api/latest/virtualhostnode/" + encodeURIComponent(nodeName);
        xhr.get(
            {
              url: this.query,
              sync: true,
              handleAs: "json",
              load: function(data)
              {
                that._show(data[0]);
              }
            }
        );
      },
      destroy: function()
      {
        if (this.dialog)
        {
            this.dialog.destroyRecursive();
            this.dialog = null;
        }

        if (this.containerNode)
        {
            domConstruct.destroy(this.containerNode);
            this.containerNode = null;
        }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _save: function(e)
      {
        event.stop(e);
        if(this.form.validate())
        {
          var data = {};
          for(var i = 0; i < fields.length; i++)
          {
              var fieldName = fields[i];
              var widget = this[fieldName];
              if (!widget.get("disabled"))
              {
                  data[fieldName] = widget.hasOwnProperty("checked")? widget.get("checked"):widget.get("value");
              }
          }
          var success = false,failureReason=null;
          xhr.put({
              url: this.query,
              sync: true,
              handleAs: "json",
              headers: { "Content-Type": "application/json"},
              putData: json.stringify(data),
              load: function(x) {success = true; },
              error: function(error) {success = false; failureReason = error;}
          });

          if(success === true)
          {
              this.dialog.hide();
          }
          else
          {
              alert("Error:" + failureReason);
          }
          }
          else
          {
              alert('Form contains invalid data.  Please correct first');
          }
        },
        _show:function(node)
        {
          for(var i = 0; i < fields.length; i++)
          {
            var fieldName = fields[i];
            this[fieldName].set("value", node[fieldName]);
          }

          var overrideData = [{id: '0', name: 'Majority', selected: '1'}];

          if (node.remotereplicationnodes && node.remotereplicationnodes.length>1)
          {
            this["designatedPrimary"].set("disabled", true);
            this["priority"].set("disabled", false);
            this["quorumOverride"].set("disabled", false);
            var overrideLimit = Math.floor((node.remotereplicationnodes.length + 1)/2);
            for(var i = 1; i <= overrideLimit; i++)
            {
              overrideData.push({id: i, name: i + ""});
            }
          }
          else
          {
            this["designatedPrimary"].set("disabled", false);
            this["priority"].set("disabled", true);
            this["quorumOverride"].set("disabled", true);
          }
          var store = new Memory({data :overrideData, idProperty: "id" });
          this["quorumOverride"].set("store", new ObjectStore({objectStore: store}));
          this.dialog.show();
        }
    };

    bdbHaNodeEditor.init();

    return bdbHaNodeEditor;
  }
);