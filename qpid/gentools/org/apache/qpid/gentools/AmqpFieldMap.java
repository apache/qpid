/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.gentools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

@SuppressWarnings("serial")
public class AmqpFieldMap extends TreeMap<String, AmqpField> implements VersionConsistencyCheck
{
	public AmqpFieldMap getFieldMapForOrdinal(int ordinal)
	{
		AmqpFieldMap newMap = new AmqpFieldMap();
		Iterator<String> fItr = keySet().iterator();
		while (fItr.hasNext())
		{
			AmqpField field = get(fItr.next());
			TreeMap<Integer, AmqpVersionSet> ordinalMap = field.ordinalMap;
			AmqpVersionSet ordinalVersions = ordinalMap.get(ordinal);
			if (ordinalVersions != null)
				newMap.put(field.name, field);
		}
		return newMap;
	}
	
	public AmqpOrdinalFieldMap getMapForVersion(AmqpVersion version)
	{
		AmqpOrdinalFieldMap ordinalFieldMap = new AmqpOrdinalFieldMap();
		Iterator<String> fItr = keySet().iterator();
		while (fItr.hasNext())
		{
			AmqpField field = get(fItr.next());
			if (version == null || field.versionSet.contains(version))
			{
				String domain = "";
				boolean dFound = false;
				Iterator<String> dItr = field.domainMap.keySet().iterator();
				while (dItr.hasNext() && !dFound)
				{
					domain = dItr.next();
					AmqpVersionSet versionSet = field.domainMap.get(domain);
					if (version == null || versionSet.contains(version))
						dFound = true;
				}
				
				int ordinal = -1;
				boolean oFound = false;
				Iterator<Integer> oItr = field.ordinalMap.keySet().iterator();
				while (oItr.hasNext() && !oFound)
				{
					ordinal = oItr.next();
					AmqpVersionSet versionSet = field.ordinalMap.get(ordinal);
					if (version == null || versionSet.contains(version))
						oFound = true;
				}
				
				if (dFound && oFound)
				{
					String[] fieldDomainPair = {field.name, domain};
					ordinalFieldMap.put(ordinal, fieldDomainPair);
				}
			}
		}
		return ordinalFieldMap;
	}
	
	public boolean isDomainConsistent(Generator generator, AmqpVersionSet versionSet)
        throws AmqpTypeMappingException
	{
		if (size() != 1) // Only one field for this ordinal
			return false;
		return get(firstKey()).isConsistent(generator);
	}
	
	public int getNumFields(AmqpVersion version)
	{
		int fCntr = 0;
		Iterator<String> fItr = keySet().iterator();
		while (fItr.hasNext())
		{
			AmqpField field = get(fItr.next());
			if (field.versionSet.contains(version))
				fCntr++;
		}
		return fCntr;
	}
	
	public String parseFieldMap(Method commonGenerateMethod, Method mangledGenerateMethod,
		int indentSize, int tabSize, Generator codeGenerator)
        throws AmqpTypeMappingException, IllegalAccessException, InvocationTargetException
	{
		String indent = Utils.createSpaces(indentSize);
		String cr = Utils.lineSeparator;
		StringBuffer sb = new StringBuffer();
		
		if (commonGenerateMethod == null)
		{
			// Generate warnings in code if required methods are null.
			sb.append(indent + "/*********************************************************" + cr);
			sb.append(indent + " * WARNING: Generated code could be missing." + cr);
			sb.append(indent + " * In call to parseFieldMap(), generation method was null." + cr);
			sb.append(indent + " * Check for NoSuchMethodException on startup." + cr);
			sb.append(indent + " *********************************************************/" + cr);
		}

		Iterator<String> itr = keySet().iterator();
		while (itr.hasNext())
		{
			String fieldName = itr.next();
			AmqpField field = get(fieldName);
			if (field.isCodeTypeConsistent(codeGenerator))
			{
				// All versions identical - Common declaration
				String domainName = field.domainMap.firstKey();
				AmqpVersionSet versionSet = field.domainMap.get(domainName);
				String codeType = codeGenerator.getGeneratedType(domainName, versionSet.first());
				if (commonGenerateMethod != null)
					sb.append(commonGenerateMethod.invoke(codeGenerator, codeType, field, versionSet,
					    indentSize, tabSize, itr.hasNext()));
			}
			else if (mangledGenerateMethod != null) // Version-mangled
			{
				sb.append(mangledGenerateMethod.invoke(codeGenerator, field, indentSize, tabSize,
					itr.hasNext()));
			}
		}
		return sb.toString();		
	}
	
	public String parseFieldMapOrdinally(Method generateMethod, Method bitGenerateMethod,
		int indentSize, int tabSize, Generator codeGenerator)
//	int indentSize, int tabSize, AmqpVersionSet versionSet, AmqpDomainMap globalDomainMap)
	    throws AmqpTypeMappingException, IllegalAccessException, InvocationTargetException
	{
		String indent = Utils.createSpaces(indentSize);
		String cr = Utils.lineSeparator;
		StringBuffer sb = new StringBuffer();	    

		// Generate warnings in code if required methods are null.
		if (generateMethod == null || bitGenerateMethod == null)
		{
			sb.append(indent + "/***********************************************" + cr);
			sb.append(indent + " * WARNING: In call to parseFieldMapOrdinally():" + cr);
			if (generateMethod == null)
				sb.append(indent + " *  => generateMethod is null." + cr);
			if (bitGenerateMethod == null)
				sb.append(indent + " *  => bitGenerateMethod is null." + cr);
			sb.append(indent + " * Generated code could be missing." + cr);
			sb.append(indent + " * Check for NoSuchMethodException on startup." + cr);
			sb.append(indent + " ***********************************************/" + cr);
		}

		/* We must process elements in ordinal order because adjacent booleans (bits)
		 * must be combined into a single byte (in groups of up to 8). Start with shared
		 * declarations until an ordinal divergence is found. (For most methods where
		 * there is no difference between versions, this will simplify the generated
		 * code. */

		ArrayList<String> bitFieldList = new ArrayList<String>();
		boolean ordinalDivergenceFlag = false;
		int ordinal = 0;
		while (ordinal < size() && !ordinalDivergenceFlag)
		{
			/* Since the getFieldMapOrdinal() function may map more than one Field to
			 * an ordinal, the number of ordinals may be less than the total number of
			 * fields in the fieldMap. Check for empty fieldmaps... */
			AmqpFieldMap ordinalFieldMap = getFieldMapForOrdinal(ordinal);
			if (ordinalFieldMap.size() > 0)
			{
				if (ordinalFieldMap.isDomainConsistent(codeGenerator, codeGenerator.globalVersionSet))
				{
					String fieldName = ordinalFieldMap.firstKey();
					String domain = ordinalFieldMap.get(fieldName).domainMap.firstKey();
					String domainType = codeGenerator.getDomainType(domain,
						codeGenerator.globalVersionSet.first());
					if (domainType.compareTo("bit") == 0)
						bitFieldList.add(fieldName);
					else if (bitFieldList.size() > 0)
					{
						// End of bit types - handle deferred bit type generation
						if (bitGenerateMethod != null)
							sb.append(bitGenerateMethod.invoke(codeGenerator, bitFieldList, ordinal,
								indentSize, tabSize));
						bitFieldList.clear();
					}
					if (!ordinalDivergenceFlag)
					{
						// Defer generation of bit types until all adjacent bits have been
						// accounted for.
						if (bitFieldList.size() == 0 && generateMethod != null)
							sb.append(generateMethod.invoke(codeGenerator, domainType, fieldName, ordinal,
								indentSize, tabSize));
					}
					ordinal++;
				}
				else
				{
					ordinalDivergenceFlag = true;
				}
			}
		}

		// Check if there is still more to do under a version-specific breakout
		if (ordinalDivergenceFlag && ordinal< size())
		{
			// 1. Cycle through all versions in order, create outer if(version) structure
			AmqpVersion[] versionArray = new AmqpVersion[codeGenerator.globalVersionSet.size()];
			codeGenerator.globalVersionSet.toArray(versionArray);
			for (int v=0; v<versionArray.length; v++)
			{
				sb.append(indent);
				if (v > 0)
					sb.append("else ");
				sb.append("if (major == " + versionArray[v].getMajor() + " && minor == " +
					versionArray[v].getMinor() + ")" + cr);
				sb.append(indent + "{" + cr);
				
				// 2. Cycle though each ordinal from where we left off in the loop above.
				ArrayList<String> bitFieldList2 = new ArrayList<String>(bitFieldList);
				for (int o = ordinal; o<size(); o++)
				{
					AmqpFieldMap ordinalFieldMap = getFieldMapForOrdinal(o);
					if (ordinalFieldMap.size() > 0)
					{
						// 3. Cycle through each of the fields that have this ordinal.
						Iterator<String> i = ordinalFieldMap.keySet().iterator();
						while (i.hasNext())
						{
							String fieldName = i.next();
							AmqpField field = ordinalFieldMap.get(fieldName);

							// 4. Some fields may have more than one ordinal - match by both
							//    ordinal and version.
							Iterator<Integer> j = field.ordinalMap.keySet().iterator();
							while (j.hasNext())
							{
								int thisOrdinal = j.next();
								AmqpVersionSet v1 = field.ordinalMap.get(thisOrdinal);
								if (thisOrdinal == o && v1.contains(versionArray[v]))
								{
									// 5. Now get the domain for this version
									int domainCntr = 0;
									Iterator<String> k = field.domainMap.keySet().iterator();
									while (k.hasNext())
									{
										// Mangle domain-divergent field names
										String mangledFieldName = fieldName;
										if (field.domainMap.size() > 1)
											mangledFieldName += "_" + (domainCntr++);
										String domainName = k.next();
										AmqpVersionSet v2 = field.domainMap.get(domainName);
										if (v2.contains(versionArray[v]))
										{
											// 6. (Finally!!) write the declaration
											String domainType = codeGenerator.getDomainType(domainName,
												versionArray[v]);
											if (domainType.compareTo("bit") == 0)
												bitFieldList2.add(mangledFieldName);
											else if (bitFieldList2.size() > 0)
											{
												// End of bit types - handle deferred bit type generation
												if (bitGenerateMethod != null)
													sb.append(bitGenerateMethod.invoke(codeGenerator,
														bitFieldList2, o, indentSize + tabSize,
														tabSize));
												bitFieldList2.clear();
											}
											// Defer generation of bit types until all adjacent bits have
											// been accounted for.
											if (bitFieldList2.size() == 0 && generateMethod != null)
												sb.append(generateMethod.invoke(codeGenerator, domainType,
														mangledFieldName, o, indentSize + tabSize, tabSize));
										}
									}
								}
							}
						}
					}
				}
				// Check for remaining deferred bits
				if (bitFieldList2.size() > 0 && bitGenerateMethod != null)
					sb.append(bitGenerateMethod.invoke(codeGenerator, bitFieldList2, size(),
						indentSize + tabSize, tabSize));
				sb.append(indent + "}" + cr);
			}
		}
		// Check for remaining deferred bits
		else if (bitFieldList.size() > 0 && bitGenerateMethod != null)
			sb.append(bitGenerateMethod.invoke(codeGenerator, bitFieldList, size(),
				indentSize, tabSize));
		return sb.toString();		
	}
	
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet)
	{
		Iterator<String> fItr = keySet().iterator();
		while (fItr.hasNext())
		{
			AmqpField field = get(fItr.next());
			if (!field.isVersionConsistent(globalVersionSet))
				return false;
		}
		return true;
	}
}
