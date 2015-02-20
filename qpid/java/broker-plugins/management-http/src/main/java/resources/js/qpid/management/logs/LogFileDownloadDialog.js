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
define([
  "dojo/_base/declare",
  "dojo/_base/event",
  "dojo/_base/xhr",
  "dojo/_base/connect",
  "dojo/dom-construct",
  "dojo/query",
  "dojo/parser",
  "dojo/store/Memory",
  "dojo/data/ObjectStore",
  "qpid/management/UserPreferences",
  "dojo/number",
  "dijit/registry",
  "dijit/Dialog",
  "dijit/form/Button",
  "dojox/grid/EnhancedGrid",
  "dojo/text!../../../logs/showLogFileDownloadDialog.html",
  "dojo/domReady!"
], function(declare, event, xhr, connect, domConstruct, query, parser, Memory, ObjectStore, UserPreferences, number,
    registry, Dialog, Button, EnhancedGrid, template){


return declare("qpid.management.logs.LogFileDownloadDialog", null, {

    templateString: template,
    containerNode: null,
    widgetsInTemplate: true,
    logFileDialog: null,
    logFilesGrid: null,
    downloadLogsButton: null,
    closeButton: null,

    constructor: function(args){
        var that = this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode).then(function(instances){that._postParse();});
    },
    _postParse: function()
    {
        var that = this;
        this.logFileTreeDiv = query(".logFilesGrid", this.containerNode)[0];
        this.downloadLogsButton = registry.byNode(query(".downloadLogsButton", this.containerNode)[0]);
        this.closeButton = registry.byNode(query(".downloadLogsDialogCloseButton", this.containerNode)[0]);

        var self = this;
        this.closeButton.on("click", function(e){self._onCloseButtonClick(e);});
        this.downloadLogsButton.on("click", function(e){self._onDownloadButtonClick(e);});
        this.downloadLogsButton.set("disabled", true)

        this.logFileDialog = new Dialog({
          title:"Broker Log Files",
          style: "width: 600px",
          content: this.containerNode
        });

        var layout = [
          { name: "Appender", field: "appenderName", width: "auto"},
          { name: "Name", field: "name", width: "auto"},
          { name: "Size", field: "size", width: "60px",
              formatter: function(val){
                return val > 1024 ? (val > 1048576? number.round(val/1048576) + "MB": number.round(val/1024) + "KB") : val + "bytes";
              }
          },
          { name: "Last Modified", field: "lastModified", width: "250px",
              formatter: function(val) {
                return UserPreferences.formatDateTime(val, {addOffset: true, appendTimeZone: true});
              }
          }
        ];

        var gridProperties = {
            store: new ObjectStore({objectStore: new Memory({data: [], idProperty: "id"}) }),
            structure: layout,
            autoHeight: true,
            plugins: {
                     pagination: {
                         pageSizes: [10, 25, 50, 100],
                         description: true,
                         sizeSwitch: true,
                         pageStepper: true,
                         gotoButton: true,
                         maxPageStep: 4,
                         position: "bottom"
                     },
                     indirectSelection: {
                         headerSelector:true,
                         width:"20px",
                         styles:"text-align: center;"
                      }
            }
        };

        this.logFilesGrid = new EnhancedGrid(gridProperties, this.logFileTreeDiv);
        var self = this;
        var downloadButtonToggler = function(rowIndex){
            var data = self.logFilesGrid.selection.getSelected();
            self.downloadLogsButton.set("disabled",!data.length );
        };
        connect.connect(this.logFilesGrid.selection, 'onSelected',  downloadButtonToggler);
        connect.connect(this.logFilesGrid.selection, 'onDeselected',  downloadButtonToggler);
    },

    _onCloseButtonClick: function(evt){
        event.stop(evt);
        this.logFileDialog.hide();
    },

    _onDownloadButtonClick: function(evt){
        event.stop(evt);
        var data = this.logFilesGrid.selection.getSelected();
        if (data.length)
        {
            var query = "";
            for(var i = 0 ; i< data.length; i++)
            {
                if (i>0)
                {
                    query+="&";
                }
                query+="l="+encodeURIComponent(data[i].appenderName +'/' + data[i].name);
            }
            window.location="service/logfile?" + query;
            this.logFileDialog.hide();
        }
    },

    destroy: function(){
        this.inherited(arguments);
        if (this.logFileDialog)
        {
          this.logFileDialog.destroyRecursive();
          this.logFileDialog = null;
        }
    },

    showDialog: function(){
        var self = this;
        var requestArguments = {url: "service/logfilenames", sync: true, handleAs: "json"};
        xhr.get(requestArguments).then(function(data){
          try
          {
            self.logFilesGrid.store.objectStore.setData(data);
            self.logFilesGrid.startup();
            self.logFileDialog.startup();
            self.logFileDialog.show();
            self.logFilesGrid._refresh();

          }
          catch(e)
          {
            console.error(e);
          }
        });
    }

  });

});
