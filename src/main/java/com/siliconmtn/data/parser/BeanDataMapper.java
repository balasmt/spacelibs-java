package com.siliconmtn.data.parser;

// JDK 1.8.x
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

// Apache Bean Utils
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.converters.DateConverter;
import org.apache.commons.beanutils.converters.DateTimeConverter;

//Log4j 2.x
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.siliconmtn.data.util.EnumUtil;

/********************************************************************
 * <b>Title: </b>BeanDataMapper.java<br/>
 * <b>Description: </b>Parses out the bean setters and assigns the values based upon
 * the data map passed into the class <br/>
 * <b>Copyright: </b>Copyright (c) 2016<br/>
 * <b>Company: </b>Silicon Mountain Technologies
 * @author james
 * @version 3.x
 * @since Apr 8, 2016
 * Last Updated:
 * 	
 *******************************************************************/
public final class BeanDataMapper {
	private static final Logger log = LogManager.getLogger(BeanDataMapper.class);
	private static final DateTimeConverter dtConverter;

	static {
		// Bean utils requires that the date formats be set into the class
		dtConverter = new DateConverter();
		//dtConverter.setPatterns(Convert.loadDatePatterns());
		ConvertUtils.register(dtConverter, Date.class);
	}

	private BeanDataMapper() {
		//Hidden Default Constructor.
	}


	public static void parseBean(Object o, Map<String, String[]> data) {
		parseBean(o, data, "");
	}
	/**
	 * Takes the provided java bean and the data map and parse the data mapped 
	 * fields into the bean's member variables
	 * @param o Java Bean
	 * @param data Data to be mapped into the bean
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void parseBean(Object o, Map<String, String[]> data, String suffix) {

		for (Method m : o.getClass().getMethods()) {
			// Get the setters
			if (! m.getName().startsWith("set")) continue;

			// Parse out the set out of the method name and lowercase the first letter
			String fieldName = m.getName().substring(3);
			fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);

			//Create a Request FieldName with the passed Suffix.
			String reqFieldName = fieldName + suffix;
			
			//Lookup FieldValue using reqFieldName instead.
			Object fieldValue = data.get(reqFieldName);
			if (fieldValue == null) continue;
			
			// If the array of values is larger than 1, convert it to a List instead
			fieldValue = getBeanArrayValue(fieldValue, m);

			// Make sure the type is not an enum.  If so, convert it to the proper
			// Class type
			if (m.getParameterTypes()[0].isEnum()) {
				try {
					fieldValue = EnumUtil.safeValueOf((Class<Enum>) Class.forName(m.getParameterTypes()[0].getName()), fieldValue.toString());
				} catch(ClassNotFoundException cnfe) { /* Nothing to do */ }
			}

			// Assign value to the class
			try {
				BeanUtils.setProperty(o, fieldName, fieldValue);
			} catch (Exception e) {
				// Typically this is circumstantial (missing or uncastable data), not an error.
				// We don't print the exception stack here - it's just noise in the logs.
				// This exception is thrown when you pass a blank to a Date field (one scenario).
				// log level changed to debug. -JM- 04/03/18
				log.error(String.format("Unable to parse data for %s=%s (%s)", fieldName, fieldValue, e.getMessage()));
			}
		}
	}


	/**
	 * Gets the field value when the setter is an array
	 * @param fieldValue
	 * @param m
	 * @param dtConverter 
	 * @return
	 */
	static Object getBeanArrayValue(Object fieldValue, Method m) {

		if (((Object[]) fieldValue).length > 1) {
			fieldValue = createList( m.getParameterTypes()[0], (Object[]) fieldValue);
		} else {
			fieldValue = ((Object[])fieldValue)[0];
		}

		return fieldValue;
	}
	
	/**
	 * Convert an array of items into a converted list.  Maps and converts the 
	 * data types to the new types.  For example, if a field is set to a date and 
	 * a String date is passed in, the data will be converted to a Date object 
	 * and added to the list
	 * @param cls Generic type of the list.  Used to convert the data from a String / Object
	 * to the appropriate type.
	 * @param data Data to add to the List
	 * @return
	 */
	protected static List<?> createList(Class<?> cls, Object[] data) {
		// Create the converter and register the date converter
		ConvertUtilsBean cub = new ConvertUtilsBean();
		cub.register(dtConverter, Date.class);

		// Loop the items in the array and convert data types and add to list
		List<Object> coll = new ArrayList<>();
		try {
			for (Object o : data) {
				Object entry = cub.convert(o, cls);

				// Make sure the class types are compatible
				if (entry.getClass().isAssignableFrom(cls))
					coll.add(entry);
			}
		} catch (Exception e) { /* Nothing to do */ }

		return coll;
	}
}