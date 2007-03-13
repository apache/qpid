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
package org.apache.qpid.util;

import java.io.File;
import java.util.*;

/**
 * An ClasspathScanner scans the classpath for classes that implement an interface or extend a base class and have names
 * that match a regular expression.
 *
 * <p/>In order to test whether a class implements an interface or extends a class, the class must be loaded (unless
 * the class files were to be scanned directly). Using this collector can cause problems when it scans the classpath,
 * because loading classes will initialize their statics, which in turn may cause undesired side effects. For this
 * reason, the collector should always be used with a regular expression, through which the class file names are
 * filtered, and only those that pass this filter will be tested. For example, if you define tests in classes that
 * end with the keyword "Test" then use the regular expression "Test$" to match this.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Find all classes matching type and name pattern on the classpath.
 * </table>
 */
public class ClasspathScanner
{
    static final int SUFFIX_LENGTH = ".class".length();

    /**
     * Scans the classpath and returns all classes that extend a specified class and match a specified name.
     * There is an flag that can be used to indicate that only Java Beans will be matched (that is, only those classes
     * that have a default constructor).
     *
     * @param matchingClass  The class or interface to match.
     * @param matchingRegexp The reular expression to match against the class name.
     * @param beanOnly       Flag to indicate that onyl classes with default constructors should be matched.
     *
     * @return All the classes that match this collector.
     */
    public static Collection<Class<?>> getMatches(Class<?> matchingClass, String matchingRegexp, boolean beanOnly)
    {
        String classPath = System.getProperty("java.class.path");
        Map result = new HashMap();

        for (String path : splitClassPath(classPath))
        {
            gatherFiles(new File(path), "", result);
        }

        return result.values();
    }

    private static void gatherFiles(File classRoot, String classFileName, Map result)
    {
        File thisRoot = new File(classRoot, classFileName);

        if (thisRoot.isFile())
        {
            if (matchesName(classFileName))
            {
                String className = classNameFromFile(classFileName);
                result.put(className, className);
            }

            return;
        }

        String[] contents = thisRoot.list();

        if (contents != null)
        {
            for (String content : contents)
            {
                gatherFiles(classRoot, classFileName + File.separatorChar + content, result);
            }
        }
    }

    private static boolean matchesName(String classFileName)
    {
        return classFileName.endsWith(".class") && (classFileName.indexOf('$') < 0) && (classFileName.indexOf("Test") > 0);
    }

    private static boolean matchesInterface()
    {
        return false;
    }

    /**
     * Takes a classpath (which is a series of paths) and splits it into its component paths.
     *
     * @param classPath The classpath to split.
     *
     * @return A list of the component paths that make up the class path.
     */
    private static List<String> splitClassPath(String classPath)
    {
        List<String> result = new LinkedList<String>();
        String separator = System.getProperty("path.separator");
        StringTokenizer tokenizer = new StringTokenizer(classPath, separator);

        while (tokenizer.hasMoreTokens())
        {
            result.add(tokenizer.nextToken());
        }

        return result;
    }

    /**
     * convert /a/b.class to a.b
     *
     * @param classFileName
     *
     * @return
     */
    private static String classNameFromFile(String classFileName)
    {

        String s = classFileName.substring(0, classFileName.length() - SUFFIX_LENGTH);
        String s2 = s.replace(File.separatorChar, '.');
        if (s2.startsWith("."))
        {
            return s2.substring(1);
        }

        return s2;
    }
}
