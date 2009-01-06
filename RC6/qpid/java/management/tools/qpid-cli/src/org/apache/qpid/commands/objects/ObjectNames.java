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

package org.apache.qpid.commands.objects;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.MBeanInfo;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 20, 2008
 * Time: 8:39:01 AM
 * To change this template use File | Settings | File Templates.
 */
public class ObjectNames {
    public String querystring = null;
    public MBeanServerConnection mbsc;
    public Set set = null;
    public String attributes = "";
    public String attributevalues = "";// = null;

    /* method return the Set objects according to the Object type */
    public void ObjectNames(MBeanServerConnection mbsc) {
        this.mbsc = mbsc;
    }

    public Set returnObjects() {
        try {
            set = mbsc.queryNames(new ObjectName(querystring), null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return set;
    }

    public void echo(String str) {
        System.out.println(str);
    }
    /* display appropriate objects according to the ojbect type */

    public void displayobjects(String output, String seperator) {
        Iterator it = set.iterator();
        String line = "";
        String temp2 = "";
        int iterator = 0;
        try {
            do {
                ObjectName temp_object = null;
                if (it.hasNext()) {
                    temp_object = (ObjectName) it.next();
                    if (temp_object == null)
                        System.out.println("null test");
                }
                // echo(temp_object.getCanonicalKeyPropertyListString());
                MBeanInfo bean_info = mbsc.getMBeanInfo(temp_object);
                MBeanAttributeInfo[] attr_info = bean_info.getAttributes();
                if (attr_info == null) {
                    echo(temp_object.toString());
                    String temp = "";
                    while (temp_object.toString().length() > temp.length())
                        temp = "=" + temp;
                    if (output == null)
                        echo(temp);

                } else {
                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;

                        try {
                            String temp1 = attr.getName();
                            if (output == null) {
                                while (temp1.length() < 15)
                                    temp1 = " " + temp1;
                                attributes = attributes + temp1 + "|";
                            } else if (output.compareToIgnoreCase("csv") == 0)
                                attributes = attributes + temp1 + seperator;
                            else {
                                echo("Wrong output format current version is supporting only for CSV");
                                return;
                            }
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                    if (attributes.equalsIgnoreCase("")) {
                        echo(temp_object.toString());
                        String temp = "";
                        while (temp_object.toString().length() > temp.length())
                            temp = "=" + temp;
                        echo(temp);
                        echo("There are no attributes for this object Type");
                        return;
                    }
                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;
                        temp2 = null;
                        try {
                            toWrite = mbsc.getAttribute(temp_object, attr.getName());
                        } catch (Exception x) {
                            temp2 = "-";
                        }
                        if (toWrite != null)
                            temp2 = toWrite.toString();
                        else
                            temp2 = "-";
                        if (output == null) {

                            while (temp2.length() < 15)
                                temp2 = " " + temp2;

                            attributevalues = attributevalues + temp2 + "|";
                        } else if (output.compareToIgnoreCase("csv") == 0)
                            attributevalues = attributevalues + temp2 + seperator;

                        //echo(temp1 + "     " + temp2 + "      " + temp3);


                    }
                }
                iterator++;
                if (iterator == 1) {
                    echo(attributes);
                    for (int i = 0; i < attributes.length(); i++)
                        line = line + "-";
                    if (output == null)
                        echo(line);
                }
                echo(attributevalues);
                line = "";
                attributes = "";
                attributevalues = "";
            } while (it.hasNext());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void reportgenerator(String output, String seperator, List<String> column) {
        Iterator it = set.iterator();
        String line = "";
        String temp2 = "";
        int iterator = 0;
        try {
            do {
                ObjectName temp_object = null;
                if (it.hasNext()) {
                    temp_object = (ObjectName) it.next();
                    if (temp_object == null)
                        System.out.println("null test");
                }
                // echo(temp_object.getCanonicalKeyPropertyListString());
                MBeanInfo bean_info = mbsc.getMBeanInfo(temp_object);
                MBeanAttributeInfo[] attr_info = bean_info.getAttributes();
                if (attr_info == null) {
                    echo(temp_object.toString());
                    String temp = "";
                    while (temp_object.toString().length() > temp.length())
                        temp = "=" + temp;
                    if (output == null)
                        echo(temp);

                } else {
                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;

                        try {
                            String temp1 = attr.getName();
                            if (column.contains(temp1)) {
                                if (output == null) {
                                    while (temp1.length() < 15)
                                        temp1 = " " + temp1;
                                    attributes = attributes + temp1 + "|";
                                } else if (output.compareToIgnoreCase("csv") == 0)
                                    attributes = attributes + temp1 + seperator;
                                else {
                                    echo("Wrong output format current version is supporting only for CSV");
                                    return;
                                }
                            }
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                    if (attributes.equalsIgnoreCase("")) {
                        echo(temp_object.toString());
                        String temp = "";
                        while (temp_object.toString().length() > temp.length())
                            temp = "=" + temp;
                        echo(temp);
                        echo("There are no attributes for this object Type");
                        return;
                    }
                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;
                        temp2 = null;
                        if (column.contains(attr.getName())) {
                            try {
                                toWrite = mbsc.getAttribute(temp_object, attr.getName());
                            } catch (Exception x) {
                                temp2 = "-";
                            }
                            if (toWrite != null)
                                temp2 = toWrite.toString();
                            else
                                temp2 = "-";
                            if (output == null) {

                                while (temp2.length() < 15)
                                    temp2 = " " + temp2;

                                attributevalues = attributevalues + temp2 + "|";
                            } else if (output.compareToIgnoreCase("csv") == 0)
                                attributevalues = attributevalues + temp2 + seperator;

                            //echo(temp1 + "     " + temp2 + "      " + temp3);

                        }


                    }
                }
                iterator++;
                if (iterator == 1) {
                    echo(attributes);
                    for (int i = 0; i < attributes.length(); i++)
                        line = line + "-";
                    if (output == null)
                        echo(line);
                }
                echo(attributevalues);
                line = "";
                attributes = "";
                attributevalues = "";
            } while (it.hasNext());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void displayqueues(String output, String seperator) {
        Iterator it = set.iterator();
        String line = "";
        int iterator = 0;
        int attr_count = 0;
        String temp1 = "";
        String temp2 = "";
        try {
            do {
                attr_count = 0;
                ObjectName temp_object = null;
                if (it.hasNext()) {
                    temp_object = (ObjectName) it.next();
                }
                // echo(temp_object.getCanonicalKeyPropertyListString());
                MBeanInfo bean_info = mbsc.getMBeanInfo(temp_object);
                MBeanAttributeInfo[] attr_info = bean_info.getAttributes();
                if (attr_info == null) {
                    echo(temp_object.toString());
                    String temp = "";
                    while (temp_object.toString().length() > temp.length())
                        temp = "=" + temp;
                    if (output == null)
                        echo(temp);

                } else {
                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;
                        attr_count++;
                        try {
                            toWrite = mbsc.getAttribute(temp_object, attr.getName());
                            if (output == null) {
                                switch (attr_count) {
                                    case 1:
                                    case 3:
                                        temp1 = attr.getName();
                                        while (temp1.length() < 10)
                                            temp1 = " " + temp1;
                                        attributes = attributes + temp1 + "|";
                                        temp2 = toWrite.toString();
                                        while (temp2.length() < 10)
                                            temp2 = " " + temp2;
                                        attributevalues = attributevalues + temp2 + "|";
                                        break;
                                    case 6:
                                        temp1 = attr.getName();
                                        while (temp1.length() < 20)
                                            temp1 = " " + temp1;
                                        attributes = attributes + temp1 + "|";
                                        temp2 = toWrite.toString();
                                        while (temp2.length() < 20)
                                            temp2 = " " + temp2;
                                        attributevalues = attributevalues + temp2 + "|";
                                        break;
                                    case 7:
                                        temp1 = attr.getName();
                                        while (temp1.length() < 13)
                                            temp1 = " " + temp1;
                                        attributes = attributes + temp1 + "|";
                                        temp2 = toWrite.toString();
                                        while (temp2.length() < 13)
                                            temp2 = " " + temp2;
                                        attributevalues = attributevalues + temp2 + "|";
                                        break;
                                    case 9:
                                        temp1 = attr.getName();
                                        while (temp1.length() < 20)
                                            temp1 = " " + temp1;
                                        attributes = attributes + temp1 + "|";
                                        temp2 = toWrite.toString();
                                        while (temp2.length() < 20)
                                            temp2 = " " + temp2;
                                        attributevalues = attributevalues + temp2 + "|";
                                        break;
                                }
                            } else if (output.compareToIgnoreCase("csv") == 0) {
                                switch (attr_count) {
                                    case 1:
                                    case 3:
                                    case 6:
                                        temp1 = attr.getName();
                                        attributes = attributes + temp1 + seperator;
                                        temp2 = toWrite.toString();
                                        attributevalues = attributevalues + temp2 + seperator;
                                        break;
                                    case 7:
                                        temp1 = attr.getName();
                                        attributes = attributes + temp1 + seperator;
                                        temp2 = toWrite.toString();
                                        attributevalues = attributevalues + temp2 + seperator;
                                        break;
                                    case 9:
                                        temp1 = attr.getName();
                                        attributes = attributes + temp1 + seperator;
                                        temp2 = toWrite.toString();
                                        attributevalues = attributevalues + temp2 + seperator;
                                        break;
                                }
                            } else {
                                echo("Wrong output format specified currently CLI supports only csv output format");
                                return;
                            }


                        } catch (Exception x) {
                            x.printStackTrace();
                        }

                    }
                }
                iterator++;
                if (iterator == 1) {
                    for (int i = 0; i < attributes.length(); i++)
                        line = line + "-";
                    if (output == null)
                        echo(line);
                    echo(attributes);
                    if (output == null)
                        echo(line);
                }
                echo(attributevalues);
                line = "";
                attributes = "";
                attributevalues = "";
            } while (it.hasNext());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void displayinfo(String output, String seperator) {
        Iterator it = set.iterator();
        String temp1, temp2 = "";
        try {
            do {

                ObjectName temp_object = null;
                if (it.hasNext()) {
                    temp_object = (ObjectName) it.next();
                }
                // echo(temp_object.getCanonicalKeyPropertyListString());
                MBeanInfo bean_info = mbsc.getMBeanInfo(temp_object);
                MBeanAttributeInfo[] attr_info = bean_info.getAttributes();
                if (attr_info == null) {
                    echo(temp_object.toString());
                    String temp = "";
                    while (temp_object.toString().length() > temp.length())
                        temp = "=" + temp;
                    echo(temp);

                } else {
                    echo(temp_object.toString());
                    String temp = "";
                    while (temp_object.toString().length() > temp.length())
                        temp = "=" + temp;
                    echo(temp);

                    for (MBeanAttributeInfo attr : attr_info) {
                        Object toWrite = null;

                        try {
                            toWrite = mbsc.getAttribute(temp_object, attr.getName());
                        } catch (Exception x) {
                            temp2 = "-";
                        }
                        temp1 = attr.getName();
                        if (toWrite != null)
                            temp2 = toWrite.toString();

                        if (output == null) {
                            while (temp1.length() < 35)
                                temp1 = " " + temp1;

                            while (temp2.length() < 35)
                                temp2 = " " + temp2;
                            echo(temp1 + "     " + temp2);
                        } else if (output.compareToIgnoreCase("csv") == 0)
                            echo(temp1 + seperator + temp2);
                        else {
                            echo("Wrong output format specified currently CLI supports only csv output format");
                            return;
                        }
                    }
                    echo("");
                    echo("");

                }
            } while (it.hasNext());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void setQueryString(String object, String name, String vhost) {

    }

    public void setQueryStringforinfo(String object, String name, String virtualhost) {

    }

    public String getQueryString() {
        return querystring;
    }

    public Set getSet() {
        return set;
    }

}
