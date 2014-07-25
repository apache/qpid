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
        "dojox/html/entities",
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
        "qpid/common/util",
        "dojo/text!editVirtualHost.html",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/form/NumberSpinner",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, entities, array, event, lang, win, dom, domConstruct, registry, parser, json, query, Memory, ObjectStore, util, template)
  {
    var fields = [ "queue.deadLetterQueueEnabled", "storeTransactionIdleTimeoutWarn", "storeTransactionIdleTimeoutClose", "storeTransactionOpenTimeoutWarn", "storeTransactionOpenTimeoutClose", "housekeepingCheckPeriod", "housekeepingThreadCount"];

    var virtualHostEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode);
        this.dialog = registry.byId("editVirtualHostDialog")
        this.saveButton = registry.byId("editVirtualHost.saveButton");
        this.cancelButton = registry.byId("editVirtualHost.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        for(var i = 0; i < fields.length; i++)
        {
            var fieldName = fields[i];
            this[fieldName] = registry.byId("editVirtualHost." + fieldName);
        }
        this.form = registry.byId("editVirtualHostForm");
      },
      show: function(hostData)
      {
        var that=this;
        this.hostName = hostData.hostName;
        this.query = "api/latest/virtualhost/" + encodeURIComponent(hostData.nodeName) + "/" + encodeURIComponent(hostData.hostName);
        this.dialog.set("title", "Edit Virtual Host - " + entities.encode(String(hostData.hostName)));
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
          var data = util.getFormWidgetValues(this.form);

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
            if (this[fieldName] instanceof dijit.form.CheckBox)
            {
                this[fieldName].set("checked", node[fieldName]);
            }
            else
            {
             this[fieldName].set("value", node[fieldName]);
            }

          }
          this.dialog.show();
        }
    };

    virtualHostEditor.init();

    return virtualHostEditor;
  }
);
