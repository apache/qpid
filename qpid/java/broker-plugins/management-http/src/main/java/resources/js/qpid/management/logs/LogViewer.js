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
        "dojo/parser",
        "dojo/query",
        "dojo/date/locale",
        "dijit/registry",
        "qpid/management/UserPreferences",
        "qpid/common/grid/GridUpdater",
        "qpid/common/grid/UpdatableGrid",
        "qpid/management/logs/LogFileDownloadDialog",
        "dojo/text!../../../logs/showLogViewer.html",
        "dojo/domReady!"],
       function (xhr, parser, query, locale, registry, UserPreferences, GridUpdater, UpdatableGrid, LogFileDownloadDialog, markup) {

           var defaulGridRowLimit = 4096;
           var currentTimeZone;

           function dataTransformer(data)
           {
             for(var i=0; i < data.length; i++)
             {
               data[i].time = UserPreferences.addTimeZoneOffsetToUTC(data[i].timestamp);
             }
             return data;
           }

           function LogViewer(name, parent, controller) {
               var self = this;

               this.name = name;
               this.lastLogId = 0;
               this.contentPane = null;
               this.downloadLogsButton = null;
               this.downloadLogDialog = null;
           }

           LogViewer.prototype.getTitle = function() {
               return "Log Viewer";
           };

           LogViewer.prototype.open = function(contentPane) {
               var self = this;
               this.contentPane = contentPane;
               this.contentPane.containerNode.innerHTML = markup;

               parser.parse(this.contentPane.containerNode).then(function(instances){self._postParse();});
           };
           LogViewer.prototype._postParse = function()
           {
               var self = this;

               this.downloadLogsButton = registry.byNode(query(".downloadLogs", this.contentPane.containerNode)[0]);
               this.downloadLogDialog = new LogFileDownloadDialog();

               this.downloadLogsButton.on("click", function(evt){
                   self.downloadLogDialog.showDialog();
               });
               this._buildGrid();
           };

           LogViewer.prototype._buildGrid = function() {
               var self = this;
               currentTimeZone = UserPreferences.getTimeZoneDescription();

               var gridStructure = [
                    {
                      hidden: false,
                      name: "ID",
                      field: "id",
                      width: "50px",
                      datatype: "number",
                      filterable: true
                    },
                    {
                      name: "Date", field: "time", width: "100px", datatype: "date",
                        formatter: function(val) {
                        return UserPreferences.formatDateTime(val, {selector:"date"});
                      }
                    },
                    { name: "Time ", field: "time", width: "100px", datatype: "time",
                     formatter: function(val) {
                       return UserPreferences.formatDateTime(val, {selector:"time"});
                     }
                   },
                   {
                     name: "Time zone",
                     field: "time",
                     width: "80px",
                     datatype: "string",
                     hidden: true,
                     filterable: false,
                     formatter: function(val) {
                       return currentTimeZone;
                     }
                   },
                   { name: "Level", field: "level", width: "50px", datatype: "string", autoComplete: true, hidden: true},
                   { name: "Logger", field: "logger", width: "150px", datatype: "string", autoComplete: false, hidden: true},
                   { name: "Thread", field: "thread", width: "100px", datatype: "string", hidden: true},
                   { name: "Log Message", field: "message", width: "auto", datatype: "string"}
               ];

               var gridNode = query("#broker-logfile", this.contentPane.containerNode)[0];
               try
               {
                 var updater = new GridUpdater({
                     updatable: false,
                     serviceUrl: function()
                     {
                       return "service/logrecords?lastLogId=" + self.lastLogId;
                     },
                     onUpdate: function(items)
                     {
                       if (items)
                       {
                         var maxId = -1;
                         for(var i in items)
                         {
                           var item = items[i];
                           if (item.id > maxId)
                           {
                             maxId = item.id
                           }
                         }
                         if (maxId != -1)
                         {
                           self.lastLogId = maxId
                         }
                       }
                     },
                     append: true,
                     appendLimit: defaulGridRowLimit,
                     dataTransformer: dataTransformer
                 });
                 this.grid = new UpdatableGrid(updater.buildUpdatableGridArguments({
                     structure: gridStructure,
                     selectable: true,
                     selectionMode: "none",
                     sortInfo: -1,
                     sortFields: [{attribute: 'id', descending: true}],
                     plugins:{
                       nestedSorting:true,
                       enhancedFilter:{defaulGridRowLimit: defaulGridRowLimit,displayLastUpdateTime:true},
                       indirectSelection: false,
                       pagination: {defaultPageSize: 10}
                     }
                  }), gridNode);
                 var onStyleRow = function(row)
                 {
                   var item = self.grid.getItem(row.index);
                   if(item){
                      var level = self.grid.store.getValue(item, "level", null);
                      var changed = false;
                      if(level == "ERROR"){
                          row.customClasses += " redBackground";
                          changed = true;
                      } else if(level == "WARN"){
                          row.customClasses += " yellowBackground";
                          changed = true;
                      } else if(level == "DEBUG"){
                          row.customClasses += " grayBackground";
                          changed = true;
                      }
                      if (changed)
                      {
                          self.grid.focus.styleRow(row);
                      }
                   }
                 };
                 this.grid.on("styleRow", onStyleRow);
                 this.grid.startup();
                 UserPreferences.addListener(this);
               }
               catch(err)
               {
                 if (console && console.error)
                 {
                   console.error(err);
                 }
               }
           };

           LogViewer.prototype.close = function() {
             UserPreferences.removeListener(this);
             if (this.grid)
             {
                 this.grid.destroy();
                 this.grid = null;
             }
             if (this.downloadLogDialog)
             {
                 this.downloadLogDialog.destroy();
                 this.downloadLogDialog = null;
             }
             if (this.downloadLogsButton)
             {
                 this.downloadLogsButton.destroy();
                 this.downloadLogsButton = null;
             }
           };

           LogViewer.prototype.onPreferencesChange = function(data)
           {
             currentTimeZone = UserPreferences.getTimeZoneDescription();
             dataTransformer(this.grid.updater.memoryStore.data);
             this.grid._refresh();
           };

           return LogViewer;
       });
