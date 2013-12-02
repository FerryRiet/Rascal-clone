/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Davy Landman - Davy.Landman@cwi.nl
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
*******************************************************************************/

/*******************************************************************************
 * 
 * Warning this file is an experiment to determine the effect of collecting all
 * classes used by the Prelude in a single class. Overall effect seems to be circa 10%
 * reduction of import time.
 * 
 * Do not edit/change this code, but use the original code instead.
 * 
 */
package org.rascalmpl.library;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.CharSetUtils;
import org.apache.commons.lang.WordUtils;
import org.eclipse.imp.pdb.facts.IBool;
import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IDateTime;
import org.eclipse.imp.pdb.facts.IInteger;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IListWriter;
import org.eclipse.imp.pdb.facts.IMap;
import org.eclipse.imp.pdb.facts.IMapWriter;
import org.eclipse.imp.pdb.facts.INode;
import org.eclipse.imp.pdb.facts.IRational;
import org.eclipse.imp.pdb.facts.ISet;
import org.eclipse.imp.pdb.facts.ISetWriter;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IString;
import org.eclipse.imp.pdb.facts.ITuple;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.exceptions.FactTypeUseException;
import org.eclipse.imp.pdb.facts.io.ATermReader;
import org.eclipse.imp.pdb.facts.io.BinaryValueReader;
import org.eclipse.imp.pdb.facts.io.BinaryValueWriter;
import org.eclipse.imp.pdb.facts.io.StandardTextReader;
import org.eclipse.imp.pdb.facts.io.StandardTextWriter;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeFactory;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.TypeReifier;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.result.ICallableValue;
import org.rascalmpl.interpreter.staticErrors.UndeclaredNonTerminal;
import org.rascalmpl.interpreter.types.FunctionType;
import org.rascalmpl.interpreter.types.NonTerminalType;
import org.rascalmpl.interpreter.types.OverloadedFunctionType;
import org.rascalmpl.interpreter.types.ReifiedType;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.parser.gtd.IGTD;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.parser.gtd.exception.UndeclaredNonTerminalException;
import org.rascalmpl.unicode.UnicodeDetector;
import org.rascalmpl.unicode.UnicodeOutputStreamWriter;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.uptr.Factory;
import org.rascalmpl.values.uptr.ProductionAdapter;
import org.rascalmpl.values.uptr.SymbolAdapter;
import org.rascalmpl.values.uptr.TreeAdapter;
import org.rascalmpl.values.uptr.visitors.TreeVisitor;

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

public class Prelude {
	private final TypeFactory types ;
	private final IValueFactory values;
	private final Random random;
	
	public Prelude(IValueFactory values){
		super();
		
		this.values = values;
		this.types = TypeFactory.getInstance();
		this.tr = new TypeReifier(values);
		random = new Random();
	}
/*	// Only here for test purposes: 
	public IValue f1(IInteger x, IInteger y){
		return values.integer(x.intValue() + y.intValue());
	}
	
	public IValue f2(IInteger x, IList LS, IString y, IBool z){
		return values.string("x : " + x.intValue() + ", LS = " + LS + ", y ; " + y + ", z : " + z);
	}
*/	
	/*
	 * Boolean
	 */
	
	public IValue arbBool()  // get an arbitrary boolean value.}
	{
	  return values.bool(random.nextInt(2) == 1);
	}
	
	/*
	 * DateTime
	 */
	public IValue now()
	//@doc{Get the current datetime.}
	{
	   return values.datetime(Calendar.getInstance().getTimeInMillis());
	}

	public IValue createDate(IInteger year, IInteger month, IInteger day) 
	//@doc{Create a new date.}
	{
		return values.date(year.intValue(), month.intValue(), day.intValue());
	}
	
	public IValue createTime(IInteger hour, IInteger minute, IInteger second,
			IInteger millisecond)
	//@doc{Create a new time.}
	{
		return values.time(hour.intValue(), minute.intValue(), second.intValue(), millisecond.intValue());
	}

	public IValue createTime(IInteger hour, IInteger minute, IInteger second,
			IInteger millisecond, IInteger timezoneHourOffset, IInteger timezoneMinuteOffset)
	//@doc{Create a new time with the given numeric timezone offset.}
	{
		return values.time(hour.intValue(), minute.intValue(), second.intValue(),
				millisecond.intValue(), timezoneHourOffset.intValue(), timezoneMinuteOffset.intValue());
	}
	
	public IValue createDateTime(IInteger year, IInteger month, IInteger day, 
			IInteger hour, IInteger minute, IInteger second, IInteger millisecond)
	//@doc{Create a new datetime.}
	{
		return values.datetime(year.intValue(), month.intValue(), day.intValue(), hour.intValue(),
				minute.intValue(), second.intValue(), millisecond.intValue());
	}

	public IValue createDateTime(IInteger year, IInteger month, IInteger day,
			IInteger hour, IInteger minute, IInteger second, IInteger millisecond, 
			IInteger timezoneHourOffset, IInteger timezoneMinuteOffset)
	//@doc{Create a new datetime with the given numeric timezone offset.}
	{
		return values.datetime(year.intValue(), month.intValue(), day.intValue(), hour.intValue(),
				minute.intValue(), second.intValue(), millisecond.intValue(), timezoneHourOffset.intValue(),
				timezoneMinuteOffset.intValue());
	}
		
	public IValue joinDateAndTime(IDateTime date, IDateTime time)
	//@doc{Create a new datetime by combining a date and a time.}
	{
		return values.datetime(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth(),
				time.getHourOfDay(), time.getMinuteOfHour(), time.getSecondOfMinute(),
				time.getMillisecondsOfSecond(), time.getTimezoneOffsetHours(), time.getTimezoneOffsetMinutes());
	}

	public IValue splitDateTime(IDateTime dt)
	//@doc{Split an existing datetime into a tuple with the date and the time.}
	{
		return values.tuple(values.date(dt.getYear(), dt.getMonthOfYear(), dt.getDayOfMonth()),
				values.time(dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute(),
						dt.getMillisecondsOfSecond(), dt.getTimezoneOffsetHours(), dt.getTimezoneOffsetMinutes()));
	}
	
	
	public IValue incrementYears(IDateTime dt, IInteger n)
	//@doc{Increment the years by a given amount.}
	{
		return incrementDate(dt, Calendar.YEAR, "years", n);	
	}
	
	public IValue incrementMonths(IDateTime dt, IInteger n)
	//@doc{Increment the months by a given amount.}
	{
		return incrementDate(dt, Calendar.MONTH, "months", n);	
	}

	public IValue incrementDays(IDateTime dt, IInteger n)
	//@doc{Increment the days by a given amount.}
	{
		return incrementDate(dt, Calendar.DAY_OF_MONTH, "days", n);	
	}

	private String getTZString(int hourOffset, int minuteOffset) {
		String tzString = "GMT" + 
			((hourOffset < 0 || (0 == hourOffset && minuteOffset < 0)) ? "-" : "+") + 
			String.format("%02d",hourOffset >= 0 ? hourOffset : hourOffset * -1) +
			String.format("%02d",minuteOffset >= 0 ? minuteOffset : minuteOffset * -1);
		return tzString;
	}

	private final int millisInAMinute = 1000 * 60;
	private final int millisInAnHour = millisInAMinute * 60;

	private IValue incrementDTField(IDateTime dt, int field, IInteger amount) {
		Calendar cal = null;

		cal = dateTimeToCalendar(dt);
		
		// Make sure lenient is true, since this allows wrapping of fields. For
		// instance, if you have $2012-05-15, and subtract 15 months, this is
		// an error if lenient is false, but gives $2012-02-15 (as expected)
		// if lenient is true.
		cal.setLenient(true);

		cal.add(field, amount.intValue());

		// Turn the calendar back into a date, time, or datetime value
		if (dt.isDate()) {
			return calendarToDate(cal);
		} else {
			if (dt.isTime()) {
				return calendarToTime(cal);
			} else {
				return calendarToDateTime(cal);
			}
		}
	}

	private IValue calendarToDateTime(Calendar cal) {
		int timezoneHours = cal.get(Calendar.ZONE_OFFSET) / millisInAnHour;
		int timezoneMinutes = cal.get(Calendar.ZONE_OFFSET) % millisInAnHour / millisInAMinute;
		return createDateTime(values.integer(cal.get(Calendar.YEAR)),
				values.integer(cal.get(Calendar.MONTH)+1),
				values.integer(cal.get(Calendar.DAY_OF_MONTH)),
				values.integer(cal.get(Calendar.HOUR_OF_DAY)),
				values.integer(cal.get(Calendar.MINUTE)),
				values.integer(cal.get(Calendar.SECOND)),
				values.integer(cal.get(Calendar.MILLISECOND)),
				values.integer(timezoneHours),
				values.integer(timezoneMinutes));
	}

	private IValue calendarToTime(Calendar cal) {
		int timezoneHours = cal.get(Calendar.ZONE_OFFSET) / millisInAnHour;
		int timezoneMinutes = cal.get(Calendar.ZONE_OFFSET) % millisInAnHour / millisInAMinute;
		return createTime(values.integer(cal.get(Calendar.HOUR_OF_DAY)),
				values.integer(cal.get(Calendar.MINUTE)),
				values.integer(cal.get(Calendar.SECOND)),
				values.integer(cal.get(Calendar.MILLISECOND)),
				values.integer(timezoneHours),
				values.integer(timezoneMinutes));
	}

	private IValue calendarToDate(Calendar cal) {
		return createDate(values.integer(cal.get(Calendar.YEAR)),
				values.integer(cal.get(Calendar.MONTH)+1),
				values.integer(cal.get(Calendar.DAY_OF_MONTH)));
	}

	private Calendar dateTimeToCalendar(IDateTime dt) {
		Calendar cal;
		if (dt.isDate()) {
			cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
			cal.set(dt.getYear(), dt.getMonthOfYear()-1, dt.getDayOfMonth());
		} else {
			cal = Calendar.getInstance(TimeZone.getTimeZone(getTZString(dt.getTimezoneOffsetHours(), dt.getTimezoneOffsetMinutes())),Locale.getDefault());
			if (dt.isTime()) {
				cal.set(1970, 0, 1, dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute());
			} else {
				cal.set(dt.getYear(), dt.getMonthOfYear()-1, dt.getDayOfMonth(), dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute());
			}
			cal.set(Calendar.MILLISECOND, dt.getMillisecondsOfSecond());
		}
		return cal;
	}
	
	private IValue incrementTime(IDateTime dt, int field, String fieldName, IInteger amount) {
		if (dt.isDate())
			throw RuntimeExceptionFactory.invalidUseOfDateException("Cannot increment the " + fieldName + " on a date value.", null, null);
		
		return incrementDTField(dt, field, amount);
	}

	private IValue incrementDate(IDateTime dt, int field, String fieldName, IInteger amount) {
		if (dt.isTime())
			throw RuntimeExceptionFactory.invalidUseOfDateException("Cannot increment the " + fieldName + " on a time value.", null, null);
		
		return incrementDTField(dt, field, amount);
	}
	
	public IValue incrementHours(IDateTime dt, IInteger n)
	//@doc{Increment the hours by a given amount.}
	{
		return incrementTime(dt, Calendar.HOUR_OF_DAY, "hours", n);
	}		

	public IValue incrementMinutes(IDateTime dt, IInteger n)
	//@doc{Increment the minutes by a given amount.}
	{
		return incrementTime(dt, Calendar.MINUTE, "minutes", n);
	}		
	
	public IValue incrementSeconds(IDateTime dt, IInteger n)
	//@doc{Increment the seconds by a given amount.}
	{
		return incrementTime(dt, Calendar.SECOND, "seconds", n);
	}
	
	public IValue incrementMilliseconds(IDateTime dt, IInteger n)
	//@doc{Increment the milliseconds by a given amount.}
	{
		return incrementTime(dt, Calendar.MILLISECOND, "milliseconds", n);
	}

	public IValue decrementYears(IDateTime dt, IInteger n)
	//@doc{Decrement the years by a given amount.}
	{
		return incrementDate(dt, Calendar.YEAR, "years", n.negate());
	}		

	public IValue decrementMonths(IDateTime dt, IInteger n)
	//@doc{Decrement the months by a given amount.}
	{
		return incrementDate(dt, Calendar.MONTH, "months", n.negate());	}	

	public IValue decrementDays(IDateTime dt, IInteger n)
	//@doc{Decrement the days by a given amount.}
	{
		return incrementDate(dt, Calendar.DAY_OF_MONTH, "days", n.negate());
	}

	public IValue decrementHours(IDateTime dt, IInteger n)
	//@doc{Decrement the hours by a given amount.}
	{
		return incrementTime(dt, Calendar.HOUR_OF_DAY, "hours", n.negate());
	}		

	public IValue decrementMinutes(IDateTime dt, IInteger n)
	//@doc{Decrement the minutes by a given amount.}
	{
		return incrementTime(dt, Calendar.MINUTE, "minutes", n.negate());
	}		

	public IValue decrementSeconds(IDateTime dt, IInteger n)
	//@doc{Decrement the seconds by a given amount.}
	{
		return incrementTime(dt, Calendar.SECOND, "seconds", n.negate());	
	}		

	public IValue decrementMilliseconds(IDateTime dt, IInteger n)
	//@doc{Decrement the milliseconds by a given amount.}
	{
		return incrementTime(dt, Calendar.MILLISECOND, "milliseconds", n.negate());
	}		

	public IValue createDurationInternal(IDateTime dStart, IDateTime dEnd) {
		// dStart and dEnd both have to be dates, times, or datetimes
		Calendar startCal = Calendar.getInstance();
		startCal.setTimeInMillis(dStart.getInstant());
		Calendar endCal = Calendar.getInstance();
		endCal.setTimeInMillis(dEnd.getInstant());
		
		IValue duration = null;
		if (dStart.isDate()) {
			if (dEnd.isDate()) {
				duration = values.tuple(
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.YEAR)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MONTH)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.DAY_OF_MONTH)),
						values.integer(0), values.integer(0), values.integer(0),
						values.integer(0));
			} else if (dEnd.isTime()) {
				throw RuntimeExceptionFactory.invalidUseOfTimeException("Cannot determine the duration between a date with no time and a time with no date.", null, null);	
			} else {
				throw RuntimeExceptionFactory.invalidUseOfDateTimeException("Cannot determine the duration between a date with no time and a datetime.", null, null);					
			}
		} else if (dStart.isTime()) {
			if (dEnd.isTime()) {
				duration = values.tuple(
						values.integer(0),
						values.integer(0),
						values.integer(0),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.HOUR_OF_DAY)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MINUTE)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.SECOND)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MILLISECOND)));
			} else if (dEnd.isDate()) {
				throw RuntimeExceptionFactory.invalidUseOfDateException("Cannot determine the duration between a time with no date and a date with no time.", null, null);	
			} else {
				throw RuntimeExceptionFactory.invalidUseOfDateTimeException("Cannot determine the duration between a time with no date and a datetime.", null, null);					
			}
		} else {
			if (dEnd.isDateTime()) {
				duration = values.tuple(
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.YEAR)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MONTH)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.DAY_OF_MONTH)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.HOUR_OF_DAY)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MINUTE)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.SECOND)),
						values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.MILLISECOND)));
			} else if (dEnd.isDate()) {
				throw RuntimeExceptionFactory.invalidUseOfDateException("Cannot determine the duration between a datetime and a date with no time.", null, null);	
			} else {
				throw RuntimeExceptionFactory.invalidUseOfTimeException("Cannot determine the duration between a datetime and a time with no date.", null, null);					
			}
		}
		return duration;
	}
	
	public IValue parseDate(IString inputDate, IString formatString)
	//@doc{Parse an input date given as a string using the given format string}
	{	
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue());
			fmt.parse(inputDate.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			return values.date(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputDate.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputDate.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		}
	}
	
	public IValue parseDateInLocale(IString inputDate, IString formatString, IString locale) 
	//@doc{Parse an input date given as a string using a specific locale and format string}
	{
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue(), new Locale(locale.getValue()));
			fmt.parse(inputDate.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			return values.date(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputDate.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputDate.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue parseTime(IString inputTime, IString formatString) 
	//@doc{Parse an input time given as a string using the given format string}
	{
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue());
			fmt.parse(inputTime.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			// The value for zone offset comes back in milliseconds. The number of
			// hours is thus milliseconds / 1000 (to get to seconds) / 60 (to get to minutes)
			// / 60 (to get to hours). Minutes is this except for the last division,
			// but then we use mod 60 since this gives us total # of minutes, including
			// the hours we have already computed.
			int zoneHours = cal.get(Calendar.ZONE_OFFSET) / (1000 * 60 * 60);
			int zoneMinutes = (cal.get(Calendar.ZONE_OFFSET) / (1000 * 60)) % 60; 
			return values.time(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND), zoneHours, zoneMinutes);
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputTime.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input date: " + inputTime.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		}
	}
	
	public IValue parseTimeInLocale(IString inputTime, IString formatString, IString locale) 
	//@doc{Parse an input time given as a string using a specific locale and format string}
	{
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue(), new Locale(locale.getValue()));
			fmt.parse(inputTime.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			// The value for zone offset comes back in milliseconds. The number of
			// hours is thus milliseconds / 1000 (to get to seconds) / 60 (to get to minutes)
			// / 60 (to get to hours). Minutes is this except for the last division,
			// but then we use mod 60 since this gives us total # of minutes, including
			// the hours we have already computed.
			int zoneHours = cal.get(Calendar.ZONE_OFFSET) / (1000 * 60 * 60);
			int zoneMinutes = (cal.get(Calendar.ZONE_OFFSET) / (1000 * 60)) % 60; 
			return values.time(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND), zoneHours, zoneMinutes);
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input time: " + inputTime.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input time: " + inputTime.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		}
	}

	public IString printSymbol(IConstructor symbol, IBool withLayout) {
	  return values.string(SymbolAdapter.toString(symbol, withLayout.getValue()));
	}
	
	public IValue parseDateTime(IString inputDateTime, IString formatString) 
	//@doc{Parse an input datetime given as a string using the given format string}
	{
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue());
			fmt.setLenient(false);
			fmt.parse(inputDateTime.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			int zoneHours = cal.get(Calendar.ZONE_OFFSET) / (1000 * 60 * 60);
			int zoneMinutes = (cal.get(Calendar.ZONE_OFFSET) / (1000 * 60)) % 60; 
			return values.datetime(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND), zoneHours, zoneMinutes);
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input datetime: " + inputDateTime.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input datetime: " + inputDateTime.getValue() + 
					" using format string: " + formatString.getValue(), null, null);
		}			
	}
	
	public IValue parseDateTimeInLocale(IString inputDateTime, IString formatString, IString locale) 
	//@doc{Parse an input datetime given as a string using a specific locale and format string}
	{
		try {
			java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(formatString.getValue(), new Locale(locale.getValue()));
			fmt.parse(inputDateTime.getValue());
			java.util.Calendar cal = fmt.getCalendar();
			int zoneHours = cal.get(Calendar.ZONE_OFFSET) / (1000 * 60 * 60);
			int zoneMinutes = (cal.get(Calendar.ZONE_OFFSET) / (1000 * 60)) % 60; 
			return values.datetime(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND), zoneHours, zoneMinutes);
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input datetime: " + inputDateTime.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		} catch (ParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input datetime: " + inputDateTime.getValue() + 
					" using format string: " + formatString.getValue() + " in locale: " + locale.getValue(), null, null);
		}
	}

	private Calendar getCalendarForDate(IDateTime inputDate) {
		if (inputDate.isDate() || inputDate.isDateTime()) {
			Calendar cal = Calendar.getInstance(TimeZone.getDefault(),Locale.getDefault());
			cal.setLenient(false);
			cal.set(inputDate.getYear(), inputDate.getMonthOfYear()-1, inputDate.getDayOfMonth());
			return cal;
		} else {
			throw new IllegalArgumentException("Cannot get date for a datetime that only represents the time");
		}
	}
	
	private Calendar getCalendarForTime(IDateTime inputTime) {
		if (inputTime.isTime() || inputTime.isDateTime()) {
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(getTZString(inputTime.getTimezoneOffsetHours(),inputTime.getTimezoneOffsetMinutes())),Locale.getDefault());
			cal.setLenient(false);
			cal.set(Calendar.HOUR_OF_DAY, inputTime.getHourOfDay());
			cal.set(Calendar.MINUTE, inputTime.getMinuteOfHour());
			cal.set(Calendar.SECOND, inputTime.getSecondOfMinute());
			cal.set(Calendar.MILLISECOND, inputTime.getMillisecondsOfSecond());
			return cal;
		} else {
			throw new IllegalArgumentException("Cannot get time for a datetime that only represents the date");
		}
	}

	private Calendar getCalendarForDateTime(IDateTime inputDateTime) {
		if (inputDateTime.isDateTime()) {
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(getTZString(inputDateTime.getTimezoneOffsetHours(),inputDateTime.getTimezoneOffsetMinutes())),Locale.getDefault());
			cal.setLenient(false);
			cal.set(inputDateTime.getYear(), inputDateTime.getMonthOfYear()-1, inputDateTime.getDayOfMonth(), inputDateTime.getHourOfDay(), inputDateTime.getMinuteOfHour(), inputDateTime.getSecondOfMinute());
			cal.set(Calendar.MILLISECOND, inputDateTime.getMillisecondsOfSecond());
			return cal;
		} else {
			throw new IllegalArgumentException("Cannot get date and time for a datetime that only represents the date or the time");
		}
	}

	public IValue printDate(IDateTime inputDate, IString formatString) 
	//@doc{Print an input date using the given format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue()); 
			Calendar cal = getCalendarForDate(inputDate);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time with format " + formatString.getValue(), null, null);
		}
	}

	public IValue printDate(IDateTime inputDate) 
	//@doc{Print an input date using a default format string}
	{
		SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd"); 
		Calendar cal = getCalendarForDate(inputDate);
		sd.setCalendar(cal);
		return values.string(sd.format(cal.getTime()));
	}
	
	public IValue printDateInLocale(IDateTime inputDate, IString formatString, IString locale) 
	//@doc{Print an input date using a specific locale and format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue(),new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForDate(inputDate);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time with format " + formatString.getValue() + ", in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue printDateInLocale(IDateTime inputDate, IString locale) 
	//@doc{Print an input date using a specific locale and a default format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd",new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForDate(inputDate);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue printTime(IDateTime inputTime, IString formatString) 
	//@doc{Print an input time using the given format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue()); 
			Calendar cal = getCalendarForTime(inputTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time with format: " + formatString.getValue(), null, null);
		}			
	}
	
	public IValue printTime(IDateTime inputTime) 
	//@doc{Print an input time using a default format string}
	{
		SimpleDateFormat sd = new SimpleDateFormat("HH:mm:ss.SSSZ"); 
		Calendar cal = getCalendarForTime(inputTime);
		sd.setCalendar(cal);
		return values.string(sd.format(cal.getTime()));
	}
	
	public IValue printTimeInLocale(IDateTime inputTime, IString formatString, IString locale) 
	//@doc{Print an input time using a specific locale and format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue(),new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForTime(inputTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue printTimeInLocale(IDateTime inputTime, IString locale) 
	//@doc{Print an input time using a specific locale and a default format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat("HH:mm:ss.SSSZ",new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForTime(inputTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print time in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue printDateTime(IDateTime inputDateTime, IString formatString) 
	//@doc{Print an input datetime using the given format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue()); 
			Calendar cal = getCalendarForDateTime(inputDateTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print datetime using format string: " + formatString.getValue(), null, null);
		}		
	}

	public IValue printDateTime(IDateTime inputDateTime) 
	//@doc{Print an input datetime using a default format string}
	{
		SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ"); 
		Calendar cal = getCalendarForDateTime(inputDateTime);
		sd.setCalendar(cal);
		return values.string(sd.format(cal.getTime()));
	}
	
	public IValue printDateTimeInLocale(IDateTime inputDateTime, IString formatString, IString locale) 
	//@doc{Print an input datetime using a specific locale and format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat(formatString.getValue(),new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForDateTime(inputDateTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print datetime using format string: " + formatString.getValue() +
					" in locale: " + locale.getValue(), null, null);
		}
	}

	public IValue printDateTimeInLocale(IDateTime inputDateTime, IString locale) 
	//@doc{Print an input datetime using a specific locale and a default format string}
	{
		try {
			SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ",new ULocale(locale.getValue())); 
			Calendar cal = getCalendarForDateTime(inputDateTime);
			sd.setCalendar(cal);
			return values.string(sd.format(cal.getTime()));
		} catch (IllegalArgumentException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print datetime in locale: " + locale.getValue(), null, null);
		}
	}
	
    public IValue daysDiff(IDateTime dtStart, IDateTime dtEnd)
    //@doc{Increment the years by a given amount.}
    {
            if (!(dtStart.isTime() || dtEnd.isTime())) {
                    Calendar startCal = Calendar.getInstance();
                    startCal.setTimeInMillis(dtStart.getInstant());
                    Calendar endCal = Calendar.getInstance();
                    endCal.setTimeInMillis(dtEnd.getInstant());
                    
                    return values.integer(startCal.fieldDifference(endCal.getTime(), Calendar.DAY_OF_MONTH));
            }
            throw RuntimeExceptionFactory.invalidUseOfTimeException("Both inputs must include dates.", null, null);
    }

    /*
	 * Graph
	 */
	
	private HashMap<IValue,Distance> distance;
	private HashMap<IValue, IValue> pred;
	private HashSet<IValue> settled;
	private PriorityQueue<IValue> Q;
	private int MAXDISTANCE = 10000;
	
	private HashMap<IValue, LinkedList<IValue>> adjacencyList;
	
	private void buildAdjacencyListAndDistance(ISet G){
		adjacencyList = new HashMap<IValue, LinkedList<IValue>> ();
		distance = new HashMap<IValue, Distance>();
		
		for(IValue v : G){
			ITuple tup = (ITuple) v;
			IValue from = tup.get(0);
			IValue to = tup.get(1);
			
			if(distance.get(from) == null)
				distance.put(from, new Distance(MAXDISTANCE));
			if(distance.get(to) == null)
				distance.put(to, new Distance(MAXDISTANCE));
			
			LinkedList<IValue> adjacencies = adjacencyList.get(from);
			if(adjacencies == null)
				adjacencies = new LinkedList<IValue>();
			adjacencies.add(to);
			adjacencyList.put(from, adjacencies);
		}
	}
	
	public IValue shortestPathPair(ISet G, IValue From, IValue To){
		buildAdjacencyListAndDistance(G);
		distance.put(From, new Distance(0));
		
		pred = new HashMap<IValue, IValue>();
		settled = new HashSet<IValue>();
		Q = new PriorityQueue<IValue>(G.size(), new NodeComparator(distance));
		Q.add(From);
		
		while(!Q.isEmpty()){
			IValue u = Q.remove();
			if(u.isEqual(To))	
				return extractPath(From, u);
			settled.add(u);
			relaxNeighbours(u);
		}
		return values.list();
	}
	
	private void relaxNeighbours(IValue u){
		LinkedList<IValue> adjacencies = adjacencyList.get(u);
		if(adjacencies != null) {
			for(IValue v : adjacencyList.get(u)){
				if(!settled.contains(v)){
					Distance dv = distance.get(v);
					Distance du = distance.get(u);
					if(dv.intval > du.intval + 1){  // 1 is default weight of each edge
						dv.intval = du.intval + 1;
						pred.put(v,u);
						Q.add(v);
					}
				}
			}
		}
	}
	
	private IList extractPath(IValue start, IValue u){
		IListWriter w = values.listWriter();
		
		if(!start.isEqual(u)){
			w.insert(u);
			while(!pred.get(u).isEqual(start)){
				u = pred.get(u);
				w.insert(u);
			}
			// TODO Check if a path was found at all; it could be that we just hit the root of the graph.
		}
		w.insert(start);
		return w.done();
	}
	
	public void print(IValue arg, IEvaluatorContext eval){
		PrintWriter currentOutStream = eval.getStdOut();
		
		try{
			if(arg.getType().isString()){
				currentOutStream.print(((IString) arg).getValue().toString());
			}
			else if(arg.getType().isSubtypeOf(Factory.Tree)){
				currentOutStream.print(TreeAdapter.yield((IConstructor) arg));
			}
			else if (arg.getType().isSubtypeOf(Factory.Type)) {
				currentOutStream.print(SymbolAdapter.toString((IConstructor) ((IConstructor) arg).get("symbol"), false));
			}
			else{
				currentOutStream.print(arg.toString());
			}
		}finally{
			currentOutStream.flush();
		}
	}
	
	public void iprint(IValue arg, IEvaluatorContext eval){
		StandardTextWriter w = new StandardTextWriter(true, 2);
		
		try {
			w.write(arg, eval.getStdOut());
		} 
		catch (IOException e) {
			RuntimeExceptionFactory.io(values.string("Could not print indented value"), eval.getCurrentAST(), eval.getStackTrace());
		}
		finally{
			eval.getStdOut().flush();
		}
	}
	
	public void iprintToFile(ISourceLocation sloc, IValue arg, IEvaluatorContext eval) {
		StandardTextWriter w = new StandardTextWriter(true, 2);
		StringWriter sw = new StringWriter();

		try {
			w.write(arg, sw);
			writeFile(sloc, values.list(values.string(sw.toString())), eval);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), eval.getCurrentAST(), null);		
		}
	}
	
	public void iprintln(IValue arg, IEvaluatorContext eval){
		StandardTextWriter w = new StandardTextWriter(true, 2);
		
		try {
			w.write(arg, eval.getStdOut());
			eval.getStdOut().println();
		} 
		catch (IOException e) {
			RuntimeExceptionFactory.io(values.string("Could not print indented value"), eval.getCurrentAST(), eval.getStackTrace());
		}
		finally{
			eval.getStdOut().flush();
		}
	}
	
	public void println(IEvaluatorContext eval) {
		eval.getStdOut().println();
		eval.getStdOut().flush();
	}
	
	public void println(IValue arg, IEvaluatorContext eval){
		PrintWriter currentOutStream = eval.getStdOut();
		
		try{
			if(arg.getType().isString()){
				currentOutStream.print(((IString) arg).getValue());
			}
			else if(arg.getType().isSubtypeOf(Factory.Tree)){
				currentOutStream.print(TreeAdapter.yield((IConstructor) arg));
			}
			else if (arg.getType().isSubtypeOf(Factory.Type)) {
				currentOutStream.print(SymbolAdapter.toString((IConstructor) ((IConstructor) arg).get("symbol"), false));
			}
			else{
				currentOutStream.print(arg.toString());
			}
			currentOutStream.println();
		}finally{
			currentOutStream.flush();
		}
	}
	
	public void rprintln(IValue arg, IEvaluatorContext eval){
		PrintWriter currentOutStream = eval.getStdOut();
		
		try{
			currentOutStream.print(arg.toString());
			currentOutStream.println();
		}finally{
			currentOutStream.flush();
		}
	}
	
	public void rprint(IValue arg, IEvaluatorContext eval){
		PrintWriter currentOutStream = eval.getStdOut();
		
		try{
			currentOutStream.print(arg.toString());
		}finally{
			currentOutStream.flush();
		}
	}

	@Deprecated
	public IValue readFile(IString filename){
		IListWriter w = values.listWriter(types.stringType());
		
		BufferedReader in = null;
		try{
			in = new BufferedReader(new FileReader(filename.getValue()));
			java.lang.String line;

			do {
				line = in.readLine();
				if(line != null){
					w.append(values.string(line));
				}
			} while (line != null);
		}catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(values.sourceLocation(filename.getValue()), null, null);
		}catch(IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
				}
			}
		}
		return w.done();
	}
	
	public IValue exists(ISourceLocation sloc, IEvaluatorContext ctx) {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
		return values.bool(ctx.getResolverRegistry().exists(sloc.getURI()));
	}
	
	public IValue lastModified(ISourceLocation sloc, IEvaluatorContext ctx) {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		try {
			return values.datetime(ctx.getResolverRegistry().lastModified(sloc.getURI()));
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, null, null);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), ctx.getStackTrace());
		}
	}
	
	public IValue isDirectory(ISourceLocation sloc, IEvaluatorContext ctx) {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
		return values.bool(ctx.getResolverRegistry().isDirectory(sloc.getURI()));
	}
	
	public IValue isFile(ISourceLocation sloc, IEvaluatorContext ctx) {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
		return values.bool(ctx.getResolverRegistry().isFile(sloc.getURI()));
	}
	
	public void mkDirectory(ISourceLocation sloc, IEvaluatorContext ctx) throws IOException {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
		ctx.getResolverRegistry().mkDirectory(sloc.getURI());
	}
	
	public IValue listEntries(ISourceLocation sloc, IEvaluatorContext ctx) {
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		try {
			java.lang.String [] entries = ctx.getResolverRegistry().listEntries(sloc.getURI());
			IListWriter w = values.listWriter();
			for(java.lang.String entry : entries){
				w.append(values.string(entry));
			}
			return w.done();
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), ctx.getStackTrace());
		} 
	}
	
	public ISet charsets() {
		ISetWriter w = values.setWriter();
		for (String s : Charset.availableCharsets().keySet()) {
			w.insert(values.string(s));
		}
		return w.done();
	} 
	
	public IValue readFile(ISourceLocation sloc, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  Reader reader = null;
	  
		try {
			Charset c = ctx.getResolverRegistry().getCharset(sloc.getURI());
			if (c != null) {
				return readFileEnc(sloc, values.string(c.name()), ctx);
			}
			sloc = ctx.getHeap().resolveSourceLocation(sloc);
		  reader = ctx.getResolverRegistry().getCharacterReader(sloc.getURI());
      return consumeInputStream(sloc, reader, ctx);
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}
		finally {
		  if (reader != null) {
		    try {
          reader.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
        }
		  }
		}
	}
	
	public IValue readFileEnc(ISourceLocation sloc, IString charset, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		try {
			return consumeInputStream(sloc, ctx.getResolverRegistry().getCharacterReader(sloc.getURI(), charset.getValue()), ctx);
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}
	}

	private IValue consumeInputStream(ISourceLocation sloc, Reader in, IEvaluatorContext ctx) {
		StringBuilder result = new StringBuilder(1024 * 1024);
		try{
			char[] buf = new char[4096];
			int count;

			while((count = in.read(buf)) != -1) {
				result.append(new java.lang.String(buf, 0, count));
			}
			
			java.lang.String str = result.toString();
			
			if(sloc.hasOffsetLength() && sloc.getOffset() != -1){
				str = str.substring(sloc.getOffset(), sloc.getOffset() + sloc.getLength());
			}
			
			return values.string(str);
		}catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}
	}
	
	public IValue md5HashFile(ISourceLocation sloc, IEvaluatorContext ctx){
		StringBuilder result = new StringBuilder(1024 * 1024);
		
		InputStream in = null;
		try{
			in = ctx.getResolverRegistry().getInputStream(sloc.getURI());
			MessageDigest md = MessageDigest.getInstance("MD5");
			in = new DigestInputStream(in, md);
			byte[] buf = new byte[4096];
			int count;

			while((count = in.read(buf)) != -1){
				result.append(new java.lang.String(buf, 0, count));
			}
			
			return values.string(new String(md.digest()));
		}catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
		} catch (NoSuchAlgorithmException e) {
			throw RuntimeExceptionFactory.io(values.string("Cannot load MD5 digest algorithm"), ctx.getCurrentAST(), null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}
	}

	public void writeFile(ISourceLocation sloc, IList V, IEvaluatorContext ctx) {
		writeFile(sloc, V, false, ctx);
	}
	
	public void writeFileEnc(ISourceLocation sloc, IString charset, IList V, IEvaluatorContext ctx) {
		writeFileEnc(sloc, charset, V, false, ctx);
	}
	
	private void writeFile(ISourceLocation sloc, IList V, boolean append, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		IString charset = values.string("UTF8");
		if (append) {
			// in case the file already has a encoding, we have to correctly append that.
			InputStream in = null;
			Charset detected = null;
			try {
				detected = ctx.getResolverRegistry().getCharset(sloc.getURI());
				if (detected == null) {
					in = ctx.getResolverRegistry().getInputStream(sloc.getURI());
					detected = UnicodeDetector.estimateCharset(in);
				}
			}catch(FileNotFoundException fnfex){
				throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
			} catch (IOException e) {
				throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
			}
			finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
					}
				}
			}
			if (detected != null)
				charset = values.string(detected.name());
			else {
				charset = values.string(Charset.defaultCharset().name());
			}
		}
		writeFileEnc(sloc, charset, V, append, ctx);
	}
	
	public IBool canEncode(IString charset) {
		return values.bool(Charset.forName(charset.getValue()).canEncode());
	}
	
	private void writeFileEnc(ISourceLocation sloc, IString charset, IList V, boolean append, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		OutputStreamWriter out = null;
		
		if (!Charset.forName(charset.getValue()).canEncode()) {
		    throw RuntimeExceptionFactory.illegalArgument(charset, null, null);
		}
		
		try{
			out = new UnicodeOutputStreamWriter(ctx.getResolverRegistry().getOutputStream(sloc.getURI(), append), charset.getValue(), append);
			
			for(IValue elem : V){
				if (elem.getType().isString()) {
					out.append(((IString) elem).getValue());
				}else if (elem.getType().isSubtypeOf(Factory.Tree)) {
					out.append(TreeAdapter.yield((IConstructor) elem));
				}else{
					out.append(elem.toString());
				}
			}
		}catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
		}finally{
			if(out != null){
				try{
					out.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}

		return;
	}
	
	public void writeFileBytes(ISourceLocation sloc, IList blist, IEvaluatorContext ctx){
		sloc = ctx.getHeap().resolveSourceLocation(sloc);
		BufferedOutputStream out=null;
		try{
			OutputStream stream = ctx.getResolverRegistry().getOutputStream(sloc.getURI(), false);
			out = new BufferedOutputStream(stream);
			Iterator<IValue> iter = blist.iterator();
			while (iter.hasNext()){
				IValue ival = iter.next();
				out.write((byte) (((IInteger) ival).intValue()));
			}
			out.flush();
			out.close();
		}catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
		}finally{
			if(out != null){
				try{
					out.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}
		return;
	}
	
	public void appendToFile(ISourceLocation sloc, IList V, IEvaluatorContext ctx){
		writeFile(sloc, V, true, ctx);
	}
	public void appendToFileEnc(ISourceLocation sloc, IString charset, IList V, IEvaluatorContext ctx){
		writeFileEnc(sloc, charset, V, true, ctx);
	}
	
	public IList readFileLines(ISourceLocation sloc, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  Reader reader = null;
	  
		try {
			Charset detected = ctx.getResolverRegistry().getCharset(sloc.getURI());
			if (detected != null) {
				return readFileLinesEnc(sloc, values.string(detected.name()), ctx);
			}
			reader = ctx.getResolverRegistry().getCharacterReader(sloc.getURI());
      return consumeInputStreamLines(sloc, reader, ctx);
		}catch(MalformedURLException e){
		    throw RuntimeExceptionFactory.malformedURI(sloc.toString(), null, null);
		}catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
		} finally {
		  if (reader != null) {
		    try {
          reader.close();
        } catch (IOException e) {
          // forgot about it
        }
		  }
		}
	}
	
	public IList readFileLinesEnc(ISourceLocation sloc, IString charset, IEvaluatorContext ctx){
	  sloc = ctx.getHeap().resolveSourceLocation(sloc);
	  
		try {
			return consumeInputStreamLines(sloc, ctx.getResolverRegistry().getCharacterReader(sloc.getURI(),charset.getValue()), ctx);
		}catch(MalformedURLException e){
		    throw RuntimeExceptionFactory.malformedURI(sloc.toString(), null, null);
		}catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
		}
	}

	private IList consumeInputStreamLines(ISourceLocation sloc,	Reader stream, IEvaluatorContext ctx ) {
		IListWriter w = values.listWriter();
		
		BufferedReader in = null;
		try{
			in = new BufferedReader(stream);
			java.lang.String line;
			
			int i = 0;
//			int offset = sloc.getOffset();
			int beginLine = sloc.hasLineColumn() ? sloc.getBeginLine() : -1;
			int beginColumn = sloc.hasLineColumn() ? sloc.getBeginColumn() : -1;
			int endLine = sloc.hasLineColumn() ? sloc.getEndLine() : -1;
			int endColumn = sloc.hasLineColumn() ? sloc.getEndColumn() : -1;

			do{
				line = in.readLine();
				i++;
				if(line != null){
					if(!sloc.hasOffsetLength()){
						w.append(values.string(line));
					}else{
						if(!sloc.hasLineColumn()){
							endColumn = line.length();
						}
						if(i == beginLine){
							if(i == endLine){
								w.append(values.string(line.substring(beginColumn, Math.min(endColumn, line.length()))));
							}else{
								w.append(values.string(line.substring(beginColumn)));
							}
						}else if(i > beginLine){
							if(i == endLine){
								w.append(values.string(line.substring(0, Math.min(endColumn, line.length()))));
							}
							else if(i < endLine){
								w.append(values.string(line));
							}
						}
					}
				}
			}while(line != null);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}

		return w.done();
	}
	
	public IList readFileBytes(ISourceLocation sloc, IEvaluatorContext ctx){
		IListWriter w = values.listWriter();
		sloc = ctx.getHeap().resolveSourceLocation(sloc);
		
		BufferedInputStream in = null;
		try{
			InputStream stream = ctx.getResolverRegistry().getInputStream(sloc.getURI());
			in = new BufferedInputStream(stream);
			int read;
			final int size = 256;
			byte bytes[] = new byte[size];
			
			do{
				read = in.read(bytes);
				for (int i = 0; i < read; i++) {
					w.append(values.integer(bytes[i] & 0xff));
				}
			}while(read != -1);
		}catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc, ctx.getCurrentAST(), null);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), ctx.getCurrentAST(), null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), ctx.getCurrentAST(), null);
				}
			}
		}

		return w.done();
	}
	
	public IString createLink(IString title, IString target) {
		return values.string("\uE007["+title.getValue().replaceAll("\\]", "_")+"]("+target.getValue()+")");
	}
	
	/*
	 * List
	 */
	
	private WeakReference<IList> indexes;

	
	
	/**
	 * A mini class to wrap a lessThan function
	 */
	private class Less {
		private final ICallableValue less;

		Less(ICallableValue less) {
			this.less = less;
		}

		public boolean less(IValue x, IValue y) {
			return ((IBool) less.call(new Type[] { x.getType(), y.getType() },
					new IValue[] { x, y }, null).getValue()).getValue();
		}
	}
	
	private class Sorting {
	  private final IValue[] array;
	  private final int size;
    private final Less less;

	  private void swap(int i, int j) {
	    IValue tmp = array[i];
	    array[i] = array[j];
	    array[j] = tmp;
	  }
	  
    public Sorting(IValue[] array, Less less) {
	    this.array = array;
	    this.size = array.length;
	    this.less = less;
    }
    
    /**
     * @throws IllegalArgument if comparator is illegal (i.e., if pivot equals pivot)
     */
    public Sorting sort() {
      if (size == 0) {
        return this;
      }
      if(less.less(array[0], array[0])) {
    	  throw RuntimeExceptionFactory.illegalArgument(less.less, null, null, "Bad comparator: Did you use less-or-equals instead of less-than?");
      }
      sort(0, size - 1);

      return this;
    }
    
    public Sorting shuffle() {
      for (int i = 0; i < size; i++) {
        swap(i, i + (int) (Math.random() * (size-i)));
      }
      return this;
    }
    
    private void sort(int low, int high) {
      IValue pivot = array[low + (high-low)/2];
      int oldLow = low;
      int oldHigh = high;
      
      while (low < high) {
        for ( ; less.less(array[low], pivot); low++); 
        for ( ; less.less(pivot, array[high]); high--); 

        if (low <= high) {
          swap(low, high);
          low++;
          high--;
        }
      }
      
      if (oldLow < high)
        sort(oldLow, high);
      if (low < oldHigh)
        sort(low, oldHigh);
    }
	}
	
	public IValue elementAt(IList lst, IInteger index) {
		if(lst.length() == 0)
			throw RuntimeExceptionFactory.emptyList(null, null);
		try {
			int i = index.intValue();
			if(index.intValue() < 0)
				i = i + lst.length();
			return lst.get(i);
		} catch (IndexOutOfBoundsException e){
			 throw RuntimeExceptionFactory.indexOutOfBounds(index, null, null);
		}
	}
	
	public IList sort(IList l, IValue cmpv){
		IValue[] tmpArr = new IValue[l.length()];
		for(int i = 0 ; i < l.length() ; i++){
			tmpArr[i] = l.get(i);
		}

		// we randomly swap some elements to make worst case complexity unlikely
		new Sorting(tmpArr, new Less((ICallableValue) cmpv)).shuffle().sort();


		IListWriter writer = values.listWriter();
		writer.append(tmpArr);
		return writer.done();
	}
	
	public IList sort(ISet l, IValue cmpv) {
		IValue[] tmpArr = new IValue[l.size()];
		int i = 0;
		
		// we assume that the set is reasonably randomly ordered, such
		// that the worst case of quicksort is unlikely
		for (IValue elem : l){
			tmpArr[i++] = elem;
		}
		
		new Sorting(tmpArr, new Less((ICallableValue) cmpv)).sort();
		
		IListWriter writer = values.listWriter();
		for(IValue v : tmpArr){
			writer.append(v);
		}
		
		return writer.done();
	}
	
	private IList makeUpTill(int from,int len){
		IListWriter writer = values.listWriter();
		for(int i = from ; i < len; i++){
			writer.append(values.integer(i));
		}
		return writer.done();
	}
	
	public IValue delete(IList lst, IInteger n)
	// @doc{delete -- delete nth element from list}
	{
		try {
			return lst.delete(n.intValue());
		} catch (IndexOutOfBoundsException e){
			 throw RuntimeExceptionFactory.indexOutOfBounds(n, null, null);
		}
	}
	
	public IValue domain(IList lst)
	//@doc{domain -- a list of all legal index values for a list}
	{
		ISetWriter w = values.setWriter();
		int len = lst.length();
		for (int i = 0; i < len; i++){
			w.insert(values.integer(i));
		}
		return w.done();
	}
	
	public IValue head(IList lst)
	// @doc{head -- get the first element of a list}
	{
	   if(lst.length() > 0){
	      return lst.get(0);
	   }
	   
	   throw RuntimeExceptionFactory.emptyList(null, null);
	}

	public IValue head(IList lst, IInteger n)
	  throws IndexOutOfBoundsException
	// @doc{head -- get the first n elements of a list}
	{
	   try {
	      return lst.sublist(0, n.intValue());
	   } catch(IndexOutOfBoundsException e){
		   IInteger end = values.integer(n.intValue() - 1);
	      throw RuntimeExceptionFactory.indexOutOfBounds(end, null, null);
	   }
	}

	public IValue getOneFrom(IList lst)
	//@doc{getOneFrom -- get an arbitrary element from a list}
	{
		int n = lst.length();
		if(n > 0){
			return lst.get(random.nextInt(n));
		}
		
		throw RuntimeExceptionFactory.emptyList(null, null);
	}

	public IValue insertAt(IList lst, IInteger n, IValue elm)
	  throws IndexOutOfBoundsException
	 //@doc{insertAt -- add an element at a specific position in a list}
	 {
	 	IListWriter w = values.listWriter();
	 	
	 	int k = n.intValue();
	    if(k >= 0 && k <= lst.length()){
	      if(k == lst.length()){
	      	w.insert(elm);
	      }
	      for(int i = lst.length()-1; i >= 0; i--) {
	        w.insert(lst.get(i));
	        if(i == k){
	        	w.insert(elm);
	        }
	      }
	      return w.done();
	    }
	    
		throw RuntimeExceptionFactory.indexOutOfBounds(n, null, null);
	 }
	
	public IValue isEmpty(IList lst)
	//@doc{isEmpty -- is list empty?}
	{
		return values.bool(lst.length() == 0);
	}

	public IValue reverse(IList lst)
	//@doc{reverse -- elements of a list in reverse order}
	{
		return lst.reverse();
	}

	public IValue size(IList lst)
	//@doc{size -- number of elements in a list}
	{
	   return values.integer(lst.length());
	}

	 public IValue slice(IList lst, IInteger start, IInteger len)
	 //@doc{slice -- sublist from start of length len}
	 {
		try {
			return lst.sublist(start.intValue(), len.intValue());
		} catch (IndexOutOfBoundsException e){
			IInteger end = values.integer(start.intValue() + len.intValue());
			throw RuntimeExceptionFactory.indexOutOfBounds(end, null, null);
		}
	 }

	 public IValue tail(IList lst)
	 //@doc{tail -- all but the first element of a list}
	 {
	 	try {
	 		return lst.sublist(1, lst.length()-1);
	 	} catch (IndexOutOfBoundsException e){
	 		throw RuntimeExceptionFactory.emptyList(null, null);
	 	}
	 }
	 
	  public IValue tail(IList lst, IInteger len)
	 //@doc{tail -- last n elements of a list}
	 {
	 	int lenVal = len.intValue();
	 	int lstLen = lst.length();
	 
	 	try {
	 		return lst.sublist(lstLen - lenVal, lenVal);
	 	} catch (IndexOutOfBoundsException e){
	 		IInteger end = values.integer(lenVal - lstLen);
	 		throw RuntimeExceptionFactory.indexOutOfBounds(end, null, null);
	 	}
	 }
	  
	public IValue take(IInteger len, IList lst) {
	   //@doc{take -- take n elements of from front of a list}
		int lenVal = len.intValue();
		int lstLen = lst.length();
		if(lenVal >= lstLen){
			return lst;
		} else {
			return lst.sublist(0, lenVal);
		}
	}

	public IValue drop(IInteger len, IList lst) {
	   //@doc{drop -- remove n elements of from front of a list}
		int lenVal = len.intValue();
		int lstLen = lst.length();
		if(lenVal >= lstLen){
			return values.list();
		} else {
			return lst.sublist(lenVal, lstLen - lenVal);
		}
	}
	
	public IValue upTill(IInteger ni) {
		//@doc{Returns the list 0..n, this is slightly faster than [0,1..n], since the returned values are shared}
		int n = ni.intValue();
		if(indexes == null || indexes.get() == null) {
			IList l = makeUpTill(0, n);
			indexes = new WeakReference<IList>(l);
			return indexes.get();
		} else {
			IList l = indexes.get(); // strong ref
			if(l == null || n >= l.length()){ 
				l = makeUpTill(0,n);
				indexes =  new WeakReference<IList>(l);
				return l;
			}
			return l.sublist(0, n);
		} 
	}
	
	public IValue prefix(IList lst) {
		   //@doc{Return all but the last element of a list}
			int lstLen = lst.length();
			if(lstLen <= 1){
				return values.list();
			} else {
				return lst.sublist(0, lstLen - 1);
			}
		}


	 
	public IValue takeOneFrom(IList lst)
	//@doc{takeOneFrom -- remove an arbitrary element from a list, returns the element and the modified list}
	{
	   int n = lst.length();
	   
	   if(n > 0){
	   	  int k = random.nextInt(n);
	   	  IValue pick = lst.get(0);
	   	  IListWriter w = values.listWriter();
	  
	      for(int i = n - 1; i >= 0; i--) {
	         if(i == k){
	         	pick = lst.get(i);
	         } else {
	            w.insert(lst.get(i));
	         }
	      }
	      return values.tuple(pick, w.done());
	   	}
	   
	   throw RuntimeExceptionFactory.emptyList(null, null);
	}
	
	private class IValueWrap {
		private final IValue ori;
		public IValueWrap(IValue ori) {
			this.ori = ori;
		}
		@Override
		public int hashCode() {
			return ori.hashCode();
		}
		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (obj instanceof IValueWrap) {
				return ori.isEqual(((IValueWrap)obj).ori);
			}
			return false;
		}
		public IValue getValue() {
			return ori;
		}
	}
	
	public IMap toMap(IList lst)
	// @doc{toMap -- convert a list of tuples to a map; first value in old tuples is associated with a set of second values}
	{
		Map<IValueWrap,ISetWriter> hm = new HashMap<IValueWrap,ISetWriter>();

		for (IValue v : lst) {
			ITuple t = (ITuple) v;
			IValueWrap key = new IValueWrap(t.get(0));
			IValue val = t.get(1);
			ISetWriter wValSet = hm.get(key);
			if(wValSet == null){
				wValSet = values.setWriter();
				hm.put(key, wValSet);
			}
			wValSet.insert(val);
		}
		
		IMapWriter w = values.mapWriter();
		for(IValueWrap v : hm.keySet()){
			w.put(v.getValue(), hm.get(v).done());
		}
		return w.done();
	}
	
	public IValue toMapUnique(IList lst)
	//@doc{toMapUnique -- convert a list of tuples to a map; result should be a map}
	{
	   if(lst.length() == 0){
	      return values.mapWriter().done();
	   }
	  
	   IMapWriter w = values.mapWriter();
	   Set<IValueWrap> seenKeys = new HashSet<IValueWrap>();
	   for(IValue v : lst){
		   ITuple t = (ITuple) v;
		   IValueWrap key = new IValueWrap(t.get(0));
		   if(seenKeys.contains(key)) 
				throw RuntimeExceptionFactory.MultipleKey(key.getValue(), null, null);
		   seenKeys.add(key);
	     w.put(key.getValue(), t.get(1));
	   }
	   return w.done();
	}

	public IValue toSet(IList lst)
	//@doc{toSet -- convert a list to a set}
	{
	  ISetWriter w = values.setWriter();
	  
	  for(IValue v : lst){
	    w.insert(v);
	  }
		
	  return w.done();
	}

	public IValue toString(IList lst)
	//@doc{toString -- convert a list to a string}
	{
		return values.string(lst.toString());
	}
	
	/*
	 * Map
	 */
	
	public IValue domain(IMap M)
	//@doc{domain -- return the domain (keys) of a map}

	{
	  ISetWriter w = values.setWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(entry.getKey());
	  }
	  return w.done();
	}

	public IValue getOneFrom(IMap m)  
	//@doc{getOneFrom -- return arbitrary key of a map}
	{
	   int i = 0;
	   int sz = m.size();
	   if(sz == 0){
	      throw RuntimeExceptionFactory.emptyMap(null, null);
	   }
	   int k = random.nextInt(sz);
	   Iterator<Entry<IValue,IValue>> iter = m.entryIterator();
	  
	   while(iter.hasNext()){
	      if(i == k){
	      	return (iter.next()).getKey();
	      }
	      iter.next();
	      i++;
	   }
	   return null;
	}
	
	public IValue invertUnique(IMap M)
	//@doc{invertUnique -- return map with key and value inverted; values are unique}
	{
		IMapWriter w = values.mapWriter();
		HashSet<IValue> seenValues = new HashSet<IValue>();
		Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
		while (iter.hasNext()) {
			Entry<IValue,IValue> entry = iter.next();
			IValue key = entry.getKey();
			IValue val = entry.getValue();
			if(seenValues.contains(val)) 
					throw RuntimeExceptionFactory.MultipleKey(val, null, null);
			seenValues.add(val);
			w.put(val, key);
		}
		return w.done();
	}
	
	public IValue invert(IMap M)
	//@doc{invert -- return map with key and value inverted; values are not unique and are collected in a set}
	{
		HashMap<IValue,ISetWriter> hm = new HashMap<IValue,ISetWriter>();
		Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
		while (iter.hasNext()) {
			Entry<IValue,IValue> entry = iter.next();
			IValue key = entry.getKey();
			IValue val = entry.getValue();
			ISetWriter wKeySet = hm.get(val);
			if(wKeySet == null){
				wKeySet = values.setWriter();
				hm.put(val, wKeySet);
			}
			wKeySet.insert(key);
		}
		
		IMapWriter w = values.mapWriter();
		
		iter = M.entryIterator();
		for(IValue v : hm.keySet()){
			w.put(v, hm.get(v).done());
		}
		return w.done();
	}
	
	public IValue isEmpty(IMap M)
	//@doc{isEmpty -- is map empty?}
	{
		return values.bool(M.size() == 0);
	}

	public IValue range(IMap M)
	//@doc{range -- return the range (values) of a map}
	{
	  ISetWriter w = values.setWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(entry.getValue());
	  }
	  return w.done();
	}

	public IValue size(IMap M)
	{
		return values.integer(M.size());
	}

	public IValue toList(IMap M)
	//@doc{toList -- convert a map to a list}
	{
	  IListWriter w = values.listWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(values.tuple(entry.getKey(), entry.getValue()));
	  }
	  return w.done();
	}

	public IValue toRel(IMap M)
	//@doc{toRel -- convert a map to a relation}
	{
	  ISetWriter w = values.setWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(values.tuple(entry.getKey(), entry.getValue()));
	  }
	  return w.done();
	}
	  
	public IValue toString(IMap M)
	{
	  return values.string(M.toString());
	}

	/*
	 * Node
	 */

	public IValue arity(INode T)
	//@doc{arity -- number of children of a node}
	{
	   return values.integer(T.arity());
	}

	public IValue getChildren(INode T)
	//@doc{getChildren -- get the children of a node}
	{
		IListWriter w = values.listWriter();
		
		for(IValue v : T.getChildren()){
			w.append(v);
		}
		return w.done();
	}

	public IValue getName(INode T)
	//@doc{getName -- get the function name of a node}
	{
		return values.string(T.getName());
	}

	public IValue makeNode(IString N, IList V)
	//@doc{makeNode -- create a node given its function name and arguments}
	{
	    IList argList = V;
		IValue args[] = new IValue[argList.length()];
		int i = 0;
		for(IValue v : argList){
			args[i++] = v;
		}
		return values.node(N.getValue(), args);
	}
	
	public IValue readATermFromFile(IString fileName){
	//@doc{readATermFromFile -- read an ATerm from a named file}
		ATermReader atr = new ATermReader();
		try {
			FileInputStream stream = new FileInputStream(fileName.getValue());
			IValue result = atr.read(values, stream);
			stream.close();
			return result;
		} catch (FactTypeUseException e) {
			e.printStackTrace();
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		} catch (IOException e) {
			e.printStackTrace();
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);

		}
	}
	
	public IValue toString(INode T)
	//@doc{toString -- convert a node to a string}
	{
		return values.string(T.toString());
	}
	
	public IMap getAnnotations(INode node) {
		java.util.Map<java.lang.String,IValue> map = node.asAnnotatable().getAnnotations();
		IMapWriter w = values.mapWriter();
		
		for (Entry<java.lang.String,IValue> entry : map.entrySet()) {
			w.put(values.string(entry.getKey()), entry.getValue());
		}
		
		return w.done();
	}
	
	public INode setAnnotations(INode node, IMap annotations) {
		java.util.Map<java.lang.String,IValue> map = new HashMap<java.lang.String,IValue>();
		
		for (IValue key : annotations) {
			IValue value = annotations.get(key);
			map.put(((IString) key).getValue(), value);
		}
		
		return node.asAnnotatable().setAnnotations(map);
	}
	
	public INode delAnnotations(INode node) {
		return node.asAnnotatable().removeAnnotations();
	}
	
	public INode delAnnotation(INode node, IString label) {
		return node.asAnnotatable().removeAnnotation(label.getValue());
	}
	
	/*
	 * ParseTree
	 */
	
	private final TypeReifier tr;

	public IValue parse(IValue start, ISourceLocation input, IEvaluatorContext ctx) {
		return parse(start, values.mapWriter().done(), input, ctx);
	}
	
	public IValue parse(IValue start, IMap robust, ISourceLocation input, IEvaluatorContext ctx) {
		Type reified = start.getType();
		IConstructor startSort = checkPreconditions(start, reified);
		
		try {
			IConstructor pt = ctx.getEvaluator().parseObject(ctx.getEvaluator().getMonitor(), startSort, robust, input.getURI());

			if (TreeAdapter.isAppl(pt)) {
				if (SymbolAdapter.isStart(TreeAdapter.getType(pt))) {
					pt = (IConstructor) TreeAdapter.getArgs(pt).get(1);
				}
			}
			return pt;
		}
		catch (ParseError pe) {
			ISourceLocation errorLoc = values.sourceLocation(pe.getLocation(), pe.getOffset(), pe.getLength(), pe.getBeginLine() + 1, pe.getEndLine() + 1, pe.getBeginColumn(), pe.getEndColumn());
			throw RuntimeExceptionFactory.parseError(errorLoc, ctx.getCurrentAST(), ctx.getStackTrace());
		}
		catch (UndeclaredNonTerminalException e){
			throw new UndeclaredNonTerminal(e.getName(), e.getClassName(), ctx.getCurrentAST());
		}
	}

	public IValue parse(IValue start, IString input, IEvaluatorContext ctx) {
		return parse(start, values.mapWriter().done(), input, ctx);
	}
	
	public IValue parse(IValue start, IMap robust, IString input, IEvaluatorContext ctx) {
		Type reified = start.getType();
		IConstructor startSort = checkPreconditions(start, reified);
		try {
			IConstructor pt = ctx.getEvaluator().parseObject(ctx.getEvaluator().getMonitor(), startSort, robust, input.getValue());

			if (TreeAdapter.isAppl(pt)) {
				if (SymbolAdapter.isStart(TreeAdapter.getType(pt))) {
					pt = (IConstructor) TreeAdapter.getArgs(pt).get(1);
				}
			}

			return pt;
		}
		catch (ParseError pe) {
			ISourceLocation errorLoc = values.sourceLocation(pe.getLocation(), pe.getOffset(), pe.getLength(), pe.getBeginLine() + 1, pe.getEndLine() + 1, pe.getBeginColumn(), pe.getEndColumn());
			throw RuntimeExceptionFactory.parseError(errorLoc, null, null);
		}
		catch (UndeclaredNonTerminalException e){
			throw new UndeclaredNonTerminal(e.getName(), e.getClassName(), ctx.getCurrentAST());
		}
	}
	
	public IValue parse(IValue start, IString input, ISourceLocation loc, IEvaluatorContext ctx) {
		return parse(start, values.mapWriter().done(), input, loc, ctx);
	}
	
	public IValue parse(IValue start, IMap robust, IString input, ISourceLocation loc, IEvaluatorContext ctx) {
		Type reified = start.getType();
		IConstructor startSort = checkPreconditions(start, reified);
		try {
			IConstructor pt = ctx.getEvaluator().parseObject(ctx.getEvaluator().getMonitor(), startSort, robust, input.getValue(), loc);

			if (TreeAdapter.isAppl(pt)) {
				if (SymbolAdapter.isStart(TreeAdapter.getType(pt))) {
					pt = (IConstructor) TreeAdapter.getArgs(pt).get(1);
				}
			}

			return pt;
		}
		catch (ParseError pe) {
			ISourceLocation errorLoc = values.sourceLocation(pe.getLocation(), pe.getOffset(), pe.getLength(), pe.getBeginLine(), pe.getEndLine(), pe.getBeginColumn(), pe.getEndColumn());
			throw RuntimeExceptionFactory.parseError(errorLoc, null, null);
		}
		catch (UndeclaredNonTerminalException e){
			throw new UndeclaredNonTerminal(e.getName(), e.getClassName(), ctx.getCurrentAST());
		}
	}
	
	public IString saveParser(ISourceLocation outFile, IEvaluatorContext ctx) {
		
		IGTD<IConstructor, IConstructor, ISourceLocation> parser = org.rascalmpl.semantics.dynamic.Import.getParser(ctx.getEvaluator(), (ModuleEnvironment) ctx.getCurrentEnvt().getRoot(), URIUtil.invalidURI(), false);
		Class<IGTD<IConstructor, IConstructor, ISourceLocation>> parserClass = (Class<IGTD<IConstructor, IConstructor, ISourceLocation>>) parser.getClass();
		
		
		try(OutputStream outStream = ctx.getResolverRegistry().getOutputStream(outFile.getURI(), false)) {
			ctx.getEvaluator().getParserGenerator().saveToJar(parserClass, outStream);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(ctx.getValueFactory().string("Unable to save to output file '" + outFile.getURI() + "'"), ctx.getCurrentAST(), ctx.getStackTrace());
		}
		return ctx.getValueFactory().string(parserClass.getName());

	}
	public IString unparse(IConstructor tree) {
		return values.string(TreeAdapter.yield(tree));
	}
	
	private IConstructor makeConstructor(Type returnType, String name, IEvaluatorContext ctx,  IValue ...args) {
		IValue value = ctx.getEvaluator().call(returnType.getName(), name, args);
		Type type = value.getType();
		if (type.isAbstractData()) {
			return (IConstructor)value;
		}
		throw RuntimeExceptionFactory.implodeError("Calling of constructor " + name + " did not return a constructor", null, null);
	}
	
	private java.lang.String unescapedConsName(IConstructor tree) {
		java.lang.String x = TreeAdapter.getConstructorName(tree);
		if (x != null) {
			x = x.replaceAll("\\\\", "");
		}
		return x;
	}

	private Set<Type> findConstructors(Type type, java.lang.String constructorName, int arity,  TypeStore store) {
		Set<Type> constructors = new HashSet<Type>();
		
		for (Type constructor : store.lookupConstructor(type, constructorName)) {
			if (constructor.getArity() == arity)
				constructors.add(constructor);
		}
		
		return constructors;
	}

	
//	private Type findConstructor(Type type, java.lang.String constructorName, int arity,  TypeStore store) {
//		for (Type candidate: store.lookupConstructor(type, constructorName)) {
//			// It finds the first with suitable arity, so this is inaccurate
//			// if there are overloaded constructors with the same arity
//			if (arity == candidate.getArity()) {
//				return candidate;
//			}
//		}
//		return null;
//	}

	public IValue implode(IValue reifiedType, IConstructor tree, IEvaluatorContext ctx) {
		TypeStore store = new TypeStore();
		Type type = tr.valueToType((IConstructor) reifiedType, store);
		try {
			IValue result = implode(store, type, tree, false, ctx); 
			if (isUntypedNodeType(type) && !type.isTop() && (TreeAdapter.isList(tree) || TreeAdapter.isOpt(tree))) {
				// Ensure the result is actually a node, even though
				// the tree given to implode is a list.
				result = values.node("", result);
			}
			return result;
		}
		catch (Backtrack b) {
			throw b.exception;
		}
	}

	@SuppressWarnings("serial")
	private static class Backtrack extends RuntimeException {
		Throw exception;
		public Backtrack(Throw exception) {
			this.exception = exception;
		}
		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
	
	private IValue[] implodeArgs(TypeStore store, Type type, IList args, IEvaluatorContext ctx) {
		int length = args.length();
		IValue implodedArgs[] = new IValue[length];
		for (int i = 0; i < length; i++) {
			Type argType = isUntypedNodeType(type) ? type : type.getFieldType(i);
			implodedArgs[i] = implode(store, argType, (IConstructor)args.get(i), false, ctx);
		}
		return implodedArgs;
	}
	
	
	private IValue implode(TypeStore store, Type type, IConstructor tree, boolean splicing, IEvaluatorContext ctx) {

		// always yield if expected type is str, except if regular 
		if (type.isString() && !splicing) {
			return values.string(TreeAdapter.yield(tree));
		}

		if (SymbolAdapter.isStartSort(TreeAdapter.getType(tree))) {
			IList args = TreeAdapter.getArgs(tree);
			IConstructor before = (IConstructor) args.get(0);
			IConstructor ast = (IConstructor) args.get(1);
			IConstructor after = (IConstructor) args.get(2);
			IValue result = implode(store, type, ast, splicing, ctx);
			if (result.getType().isNode()) {
				IMapWriter comments = values.mapWriter();
				comments.putAll((IMap)((INode)result).asAnnotatable().getAnnotation("comments"));
				IList beforeComments = extractComments(before);
				if (!beforeComments.isEmpty()) {
					comments.put(values.integer(-1), beforeComments);
				}
				IList afterComments = extractComments(after);
				if (!afterComments.isEmpty()) {
					comments.put(values.integer(((INode)result).arity()), afterComments);
				}
				result = ((INode)result).asAnnotatable().setAnnotation("comments", comments.done());
			}
			return result;
		}
		
		if (TreeAdapter.isLexical(tree)) {
			java.lang.String constructorName = unescapedConsName(tree);
			java.lang.String yield = TreeAdapter.yield(tree);
			if (constructorName != null) {
				// make a single argument constructor  with yield as argument
				// if there is a singleton constructor with a str argument
				if (!type.isAbstractData() && !isUntypedNodeType(type)) {
					throw RuntimeExceptionFactory.illegalArgument(tree, null, null, "Constructor (" + constructorName + ") should match with abstract data type and not with " + type);
				}
				
				if (isUntypedNodeType(type)) {
					return values.node(constructorName, values.string(yield));
				}
				
				Set<Type> conses = findConstructors(type, constructorName, 1, store);
				Iterator<Type> iter = conses.iterator();
				while (iter.hasNext()) {
					try {
						@SuppressWarnings("unused")
						Type cons = iter.next();
						ISourceLocation loc = TreeAdapter.getLocation(tree);
						IConstructor ast = makeConstructor(type, constructorName, ctx, values.string(yield));
						return ast.asAnnotatable().setAnnotation("location", loc);
					}
					catch (Backtrack b) {
						continue;
					}
				}
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Cannot find a constructor " + type));
			}
			if (type.isInteger()) {
				return values.integer(yield);
			}
			if (type.isReal()) {
				return values.real(yield);
			}
			if (type.isBool()) {
				if (yield.equals("true")) {
					return values.bool(true);
				}
				if (yield.equals("false")) {
					return values.bool(false);
				}
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Bool type does not match with " + yield));
			}
			if (type.isString() || isUntypedNodeType(type)) {
				// NB: in "node space" all lexicals become strings
				return values.string(yield);
			}
			
			throw RuntimeExceptionFactory.illegalArgument(tree, null, null, "Missing lexical constructor");
		}
		
		//Set implementation added here by Jurgen at 19/07/12 16:45
		if (TreeAdapter.isList(tree)) {
			if (type.isList() || splicing || isUntypedNodeType(type)) {
				// if in node space, we also make a list; 
				// NB: this breaks type safety if the top-level tree
				// is itself a list.
				
				Type elementType = type;
				if (!splicing && !isUntypedNodeType(type)) {
					elementType = type.getElementType();
				}
				IListWriter w = values.listWriter();
				for (IValue arg: TreeAdapter.getListASTArgs(tree)) {
					w.append(implode(store, elementType, (IConstructor) arg, false, ctx));
				}
				return w.done();
			}
			else if (type.isSet()) {
				Type elementType = splicing ? type : type.getElementType();
				ISetWriter w = values.setWriter();
				for (IValue arg: TreeAdapter.getListASTArgs(tree)) {
					w.insert(implode(store, elementType, (IConstructor) arg, false, ctx));
				}
				return w.done();
			}
			else {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Cannot match list with " + type));
			}
		}
		//Changes end here
		
		if (TreeAdapter.isOpt(tree) && type.isBool()) {
			IList args = TreeAdapter.getArgs(tree);
			if (args.isEmpty()) {
				return values.bool(false);
			}
			return values.bool(true);
		}
		
		if (TreeAdapter.isOpt(tree)) {
			if (!type.isList() && !isUntypedNodeType(type)) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Optional should match with a list and not " + type));
			}
			Type elementType = isUntypedNodeType(type) ? type : type.getElementType();
			IListWriter w = values.listWriter();
			for (IValue arg: TreeAdapter.getASTArgs(tree)) {
				IValue implodedArg = implode(store, elementType, (IConstructor) arg, true, ctx);
				if (implodedArg instanceof IList) {
					// splicing
					for (IValue nextArg: (IList)implodedArg) {
						w.append(nextArg);
					}
				}
				else {
					w.append(implodedArg);
				}
				// opts should have one argument (if any at all)
				break;
			}
			return w.done();
		}
		
		if (TreeAdapter.isAmb(tree)) {
			if (!type.isSet()) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Ambiguous node should match with set and not " + type));
			}
			Type elementType = type.getElementType();
			ISetWriter w = values.setWriter();
			for (IValue arg: TreeAdapter.getAlternatives(tree)) {
				w.insert(implode(store, elementType, (IConstructor) arg, false, ctx));
			}
			return w.done();
		}
		
		if (ProductionAdapter.hasAttribute(TreeAdapter.getProduction(tree), Factory.Attribute_Bracket)) {
			return implode(store, type, (IConstructor) TreeAdapter.getASTArgs(tree).get(0), false, ctx);
		}
		
		if (TreeAdapter.isAppl(tree)) {
			IList args = TreeAdapter.getASTArgs(tree);
			
			int j = 0;
			IMapWriter cw = values.mapWriter();
			IListWriter aw = values.listWriter();
			for (IValue kid : TreeAdapter.getArgs(tree)) {
				if (TreeAdapter.isLayout((IConstructor) kid)) {
					IList cts = extractComments((IConstructor) kid);
					if (!cts.isEmpty()) {
					  cw.put(values.integer(j), cts);
					}
					j++;
				}
				else if (!TreeAdapter.isLiteral((IConstructor) kid) && 
						!TreeAdapter.isCILiteral((IConstructor) kid) && 
						!TreeAdapter.isEmpty((IConstructor) kid)) {
					aw.append(kid);
				}
			}
			args = aw.done();
			int length = args.length();
			IMap comments = cw.done();
			
//			// this could be optimized.
//			i = 0;
//			int length = args.length();
//			while (i < length) {
//				if (TreeAdapter.isEmpty((IConstructor) args.get(i))) {
//					length--;
//					args = args.delete(i);
//				}
//				else {
//					i++;
//				}
//			}
			
			
			java.lang.String constructorName = unescapedConsName(tree);			
			
			if (constructorName == null) {
				if (length == 1) {
					// jump over injection
					return implode(store, type, (IConstructor) args.get(0), splicing, ctx);
				}
				
				
				// make a tuple if we're in node space
				if (isUntypedNodeType(type)) {
					return values.tuple(implodeArgs(store, type, args, ctx));
				}

				if (!type.isTuple()) {
					throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Constructor does not match with " + type));
				}
				
				if (length != type.getArity()) {
					throw new Backtrack(RuntimeExceptionFactory.arityMismatch(type.getArity(), length, null, null));
				}

				return values.tuple(implodeArgs(store, type, args, ctx));
			}
			
			// if in node space, make untyped nodes
			if (isUntypedNodeType(type)) {
				INode ast = values.node(constructorName, implodeArgs(store, type, args, ctx));
				return ast.asAnnotatable().setAnnotation("location", TreeAdapter.getLocation(tree)).asAnnotatable().setAnnotation("comments", comments);
			}
			
			// make a typed constructor
			if (!type.isAbstractData()) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, "Constructor (" + constructorName + ") should match with abstract data type and not with " + type));
			}

			Set<Type> conses = findConstructors(type, constructorName, length, store);
			Iterator<Type> iter = conses.iterator();
			while (iter.hasNext()) {
				try {
					Type cons = iter.next();
					ISourceLocation loc = TreeAdapter.getLocation(tree);
					IValue[] implodedArgs = implodeArgs(store, cons, args, ctx);
					IConstructor ast = makeConstructor(type, constructorName, ctx, implodedArgs);
					return ast.asAnnotatable().setAnnotation("location", loc).asAnnotatable().setAnnotation("comments", comments);
				}
				catch (Backtrack b) {
					continue;
				}
			}
			
		}
		
		throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, null, null, 
				"Cannot find a constructor for " + type));
	}
	
	private IList extractComments(IConstructor layout) {
		final IListWriter comments = values.listWriter();
		TreeVisitor<RuntimeException> visitor = new TreeVisitor<RuntimeException>() {

			@Override
			public IConstructor visitTreeAppl(IConstructor arg)
					 {
				if (TreeAdapter.isComment(arg)) {
					comments.append(values.string(TreeAdapter.yield(arg)));
				}
				else {
					for (IValue t: TreeAdapter.getArgs(arg)) {
						t.accept(this);
					}
				}
				return arg;
			}

			@Override
			public IConstructor visitTreeAmb(IConstructor arg)
					 {
				return arg;
			}

			@Override
			public IConstructor visitTreeChar(IConstructor arg)
					 {
				return arg;
			}

			@Override
			public IConstructor visitTreeCycle(IConstructor arg)
					 {
				return arg;
			}
			
		};
		
		layout.accept(visitor);
		return comments.done();
	}

	private boolean isUntypedNodeType(Type type) {
		return (type.isNode() && !type.isConstructor() && !type.isAbstractData()) 
				|| type.isTop();
	}
	
	
	
	
	private static IConstructor checkPreconditions(IValue start, Type reified) {
		if (!(reified instanceof ReifiedType)) {
		   throw RuntimeExceptionFactory.illegalArgument(start, null, null, "A reified type is required instead of " + reified);
		}
		
		Type nt = reified.getTypeParameters().getFieldType(0);
		
		if (!(nt instanceof NonTerminalType)) {
			throw RuntimeExceptionFactory.illegalArgument(start, null, null, "A non-terminal type is required instead of  " + nt);
		}
		
		IConstructor symbol = ((NonTerminalType) nt).getSymbol();
		
		return symbol;
	}
	
	/*
	 * Rational
	 */

	public IValue numerator(IRational n)
	{
		return n.numerator();
	}

	public IValue denominator(IRational n)
	{
	  return n.denominator();
	}

	public IValue remainder(IRational n)
	{
	  return n.remainder();
	}
	
	/*
	 * Relation
	 */
	
	/*
	 * Set
	 */
	public IValue getOneFrom(ISet st)
	// @doc{getOneFrom -- pick a random element from a set}
	{
		int sz = st.size();

		if (sz == 0) {
			throw RuntimeExceptionFactory.emptySet(null, null);
		}
		int k = random.nextInt(sz);
		int i = 0;

		for (IValue v : st) {
			if (i == k) {
				return v;
			}
			i++;
		}
		
		throw RuntimeExceptionFactory.emptySet(null, null);
	}

	public IValue isEmpty(ISet st)
	//@doc{isEmpty -- is set empty?}
	{
		return values.bool(st.size() == 0);
	}
	
	public IValue size(ISet st)
	// @doc{size -- number of elements in a set}
	{
		return values.integer(st.size());
	}
	
	public IMap index(ISet s) {
		Map<IValue, ISetWriter> map = new HashMap<IValue, ISetWriter>(s.size());
		
		for (IValue t : s) {
			ITuple tuple = (ITuple) t;
			IValue key = tuple.get(0);
			IValue value = tuple.get(1);
			
			ISetWriter writer = map.get(key);
			if (writer == null) {
				writer = values.setWriter();
				map.put(key, writer);
			}
			writer.insert(value);
		}
		
		IMapWriter mapWriter = values.mapWriter();
		for (IValue key : map.keySet()) {
			mapWriter.put(key, map.get(key).done());
		}
		
		return mapWriter.done();
	}
	
	public IMap index(IList s) {
		Map<IValue, ISetWriter> map = new HashMap<IValue, ISetWriter>(s.length());
		
		for (IValue t : s) {
			ITuple tuple = (ITuple) t;
			IValue key = tuple.get(0);
			IValue value = tuple.get(1);
			
			ISetWriter writer = map.get(key);
			if (writer == null) {
				writer = values.setWriter();
				map.put(key, writer);
			}
			writer.insert(value);
		}
		
		IMapWriter mapWriter = values.mapWriter();
		for (IValue key : map.keySet()) {
			mapWriter.put(key, map.get(key).done());
		}
		
		return mapWriter.done();
	}

	public IValue takeOneFrom(ISet st)
	// @doc{takeOneFrom -- remove an arbitrary element from a set,
	//      returns the element and the modified set}
	{
		int n = st.size();

		if (n > 0) {
			int i = 0;
			int k = random.nextInt(n);
			IValue pick = null;
			ISetWriter w = values.setWriter();

			for (IValue v : st) {
				if (i == k) {
					pick = v;
				} else {
					w.insert(v);
				}
				i++;
			}
			return values.tuple(pick, w.done());
		}
		throw RuntimeExceptionFactory.emptySet(null, null);
	}

	public IValue toList(ISet st)
	// @doc{toList -- convert a set to a list}
	{
		IListWriter w = values.listWriter();

		for (IValue v : st) {
			w.insert(v);
		}

		return w.done();
	}

	public IValue toMap(ISet st)
	// @doc{toMap -- convert a set of tuples to a map; value in old map is associated with a set of keys in old map}
	{
		Map<IValueWrap,ISetWriter> hm = new HashMap<IValueWrap,ISetWriter>();

		for (IValue v : st) {
			ITuple t = (ITuple) v;
			IValueWrap key = new IValueWrap(t.get(0));
			IValue val = t.get(1);
			ISetWriter wValSet = hm.get(key);
			if(wValSet == null){
				wValSet = values.setWriter();
				hm.put(key, wValSet);
			}
			wValSet.insert(val);
		}
		
		IMapWriter w = values.mapWriter();
		for(IValueWrap v : hm.keySet()){
			w.put(v.getValue(), hm.get(v).done());
		}
		return w.done();
	}
	
	public IValue toMapUnique(ISet st)
	// @doc{toMapUnique -- convert a set of tuples to a map; keys are unique}
	{
		IMapWriter w = values.mapWriter();
		HashSet<IValueWrap> seenKeys = new HashSet<IValueWrap>();

		for (IValue v : st) {
			ITuple t = (ITuple) v;
			IValueWrap key = new IValueWrap(t.get(0));
			IValue val = t.get(1);
			if(seenKeys.contains(key)) { 
				throw RuntimeExceptionFactory.MultipleKey(key.getValue(), null, null);
			}
			seenKeys.add(key);
			w.put(key.getValue(), val);
		}
		return w.done();
	}

	public IValue toString(ISet st)
	// @doc{toString -- convert a set to a string}
	{
		return values.string(st.toString());
	}
	
	/*
	 * String
	 */
	
	public IBool isValidCharacter(IInteger i) {
		return values.bool(Character.isValidCodePoint(i.intValue()));
	}
	
	public IValue stringChar(IInteger i) {
		int intValue = i.intValue();
		if (Character.isValidCodePoint(intValue)) {
			return values.string(intValue);
		}
		else {
			throw RuntimeExceptionFactory.illegalArgument(i, null, null);
		}
	}
	
	public IValue stringChars(IList lst){
		int[] chars = new int[lst.length()];
		
		for (int i = 0; i < lst.length(); i ++) {
			chars[i] = ((IInteger) lst.get(i)).intValue();
			if (!Character.isValidCodePoint(chars[i])) {
				throw RuntimeExceptionFactory.illegalArgument(values.integer(chars[i]), null, null);
			}
		}
		
		return values.string(chars);
	}
	
	public IValue charAt(IString s, IInteger i) throws IndexOutOfBoundsException
	//@doc{charAt -- return the character at position i in string s.}
	{
	  try {
		return values.integer(s.charAt(i.intValue()));
	  }
	  catch (IndexOutOfBoundsException e) {
	    throw RuntimeExceptionFactory.indexOutOfBounds(i, null, null);
	  }
	}

	public IValue endsWith(IString s, IString suffix)
	//@doc{endWith -- returns true if string s ends with given string suffix.}
	{
	  return values.bool(s.getValue().endsWith(suffix.getValue()));
	}
	
	public IString trim(IString s) {
		return values.string(s.getValue().trim());
	}
	
	public IString squeeze(IString src, IString charSet) {
		//@{http://commons.apache.org/lang/api-2.6/index.html?org/apache/commons/lang/text/package-summary.html}
		String s = CharSetUtils.squeeze(src.getValue(), charSet.getValue());
		return values.string(s);
	}
	
	public IString capitalize(IString src) {
		return values.string(WordUtils.capitalize(src.getValue()));
	}
	
	public IString uncapitalize(IString src) {
		return values.string(WordUtils.uncapitalize(src.getValue()));
	}
	
	public IList split(IString sep, IString src) {
		String[] lst = src.getValue().split(Pattern.quote(sep.getValue()));
		IListWriter lw = values.listWriter();
		for (String s: lst) {
			lw.append(values.string(s));
		}
		return lw.done();
	}
	
	public IString wrap(IString src, IInteger wrapLength) {
		String s = WordUtils.wrap(src.getValue(), wrapLength.intValue());
		return values.string(s);
	}

	public IValue format(IString s, IString dir, IInteger n, IString pad)
	//@doc{format -- return string of length n, with s placed according to dir (left/center/right) and padded with pad}
	{
	    StringBuffer res = new StringBuffer();
	    int sLen = s.getValue().length();
	    int nVal = n.intValue();
	    if(sLen > nVal){
	       return s;
	    }
	    int padLen = pad.getValue().length();
	    java.lang.String dirVal = dir.getValue();
	    int start;
	    
	    if(dirVal.equals("right"))
	       start = nVal - sLen;
	    else if(dirVal.equals("center"))
	       start = (nVal - sLen)/2;
	    else
	       start = 0;
	    
	    int i = 0;
	    while(i < start){
	         if(i + padLen < start){
	         	res.append(pad.getValue());
	         	i+= padLen;
	         } else {
	         	res.append(pad.getValue().substring(0, start - i));
	         	i += start -i;
	         }
	    }
	    res.append(s.getValue());
	    i = start + sLen;
	    while(i < nVal){
	         if(i + padLen < nVal){
	         	res.append(pad.getValue());
	         	i += padLen;
	         } else {
	         	res.append(pad.getValue().substring(0, nVal - i));
	         	i += nVal - i;
	         }
	    }
	    return values.string(res.toString());
	}
	
	public IValue isEmpty(IString s)
	//@doc{isEmpty -- is string empty?}
	{
		return values.bool(s.getValue().length() == 0);
	}

	public IValue reverse(IString s)
	//@doc{reverse -- return string with all characters in reverse order.}
	{
	   return s.reverse();
	}

	public IValue size(IString s)
	//@doc{size -- return the length of string s.}
	{
	  return values.integer(s.length());
	}

	public IValue startsWith(IString s, IString prefix)
	//@doc{startsWith -- return true if string s starts with the string prefix.}
	{
	  return values.bool(s.getValue().startsWith(prefix.getValue()));
	}

	public IValue substring(IString s, IInteger begin) {
		try {
			return s.substring(begin.intValue());
		} catch (IndexOutOfBoundsException e) {
			throw RuntimeExceptionFactory.indexOutOfBounds(begin, null, null);
		}
	}
	
	public IValue substring(IString s, IInteger begin, IInteger end) {
		try {
			return s.substring(begin.intValue(),end.intValue());
		} catch (IndexOutOfBoundsException e) {
			int bval = begin.intValue();
			IInteger culprit = (bval < 0 || bval >= s.length()) ? begin : end;
		    throw RuntimeExceptionFactory.indexOutOfBounds(culprit, null, null);
		}
	
	}
	
	public IValue toInt(IString s)
	//@doc{toInt -- convert a string s to integer}
	{
		try {
			java.lang.String sval = s.getValue();
			boolean isNegative = false;
			int radix = 10;
			
			if (sval.equals("0")) {
				return values.integer(0);
			}
			
			if (sval.startsWith("-")) {
				isNegative = true;
				sval = sval.substring(1);
			}
			if (sval.startsWith("0x") || sval.startsWith("0X")) {
				radix = 16;
				sval = sval.substring(2);
			} else if (sval.startsWith("0")) {
				radix = 8;
				sval = sval.substring(1);
			}
			BigInteger bi = new BigInteger(isNegative ? "-" + sval : sval, radix);
			return values.integer(bi.toString());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument(null, null);
		}
	}
	
	public IValue toInt(IString s, IInteger r)
	{
		try {
			java.lang.String sval = s.getValue();
			boolean isNegative = false;
			int radix = r.intValue();
			
			if (sval.equals("0")) {
				return values.integer(0);
			}
			
			if (sval.startsWith("-")) {
				isNegative = true;
				sval = sval.substring(1);
			}
			BigInteger bi = new BigInteger(isNegative ? "-" + sval : sval, radix);
			return values.integer(bi.toString());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument(null, null);
		}
	}
	
	public IValue toReal(IString s)
	//@doc{toReal -- convert a string s to a real}
	{
		try {
			return values.real(s.getValue());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument(null, null);
		}
	}
	
	public IValue toReal(IRational s)
  //@doc{toReal -- convert a string s to a real}
  {
      return s.toReal();
  }
	
	public IValue toLowerCase(IString s)
	//@doc{toLowerCase -- convert all characters in string s to lowercase.}
	{
	  return values.string(s.getValue().toLowerCase());
	}

	public IValue toUpperCase(IString s)
	//@doc{toUpperCase -- convert all characters in string s to uppercase.}
	{
	  return values.string(s.getValue().toUpperCase());
	}
	
	private boolean match(char[] subject, int i, char [] pattern){
		if(i + pattern.length > subject.length)
			return false;
		for(int k = 0; k < pattern.length; k++){
			if(subject[i] != pattern[k])
				return false;
			i++;
		}
		return true;
	}
	
	public IValue replaceAll(IString str, IString find, IString replacement){
		StringBuilder b = new StringBuilder(str.getValue().length() * 2); 
		char [] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		char [] replChars = replacement.getValue().toCharArray();
		
		int i = 0;
		boolean matched = false;
		while(i < input.length){
			if(match(input,i,findChars)){
				matched = true;
				b.append(replChars);
				i += findChars.length;
			} else {
				b.append(input[i]);
				i++;
			}
		}
		return (!matched) ? str : values.string(b.toString());
	}
	
	public IValue replaceFirst(IString str, IString find, IString replacement){
		StringBuilder b = new StringBuilder(str.getValue().length() * 2); 
		char [] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		char [] replChars = replacement.getValue().toCharArray();
		
		int i = 0;
		boolean matched = false;
		while(i < input.length){
			if(!matched && match(input,i,findChars)){
				matched = true;
				b.append(replChars);
				i += findChars.length;
				
			} else {
				b.append(input[i]);
				i++;
			}
		}
		return (!matched) ? str : values.string(b.toString());
	}
	
	public IValue replaceLast(IString str, IString find, IString replacement){
		StringBuilder b = new StringBuilder(str.getValue().length() * 2); 
		char [] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		char [] replChars = replacement.getValue().toCharArray();
		
		int i = input.length - findChars.length;
		while(i >= 0){
			if(match(input,i,findChars)){
				for(int j = 0; j < i; j++)
					b.append(input[j]);
				b.append(replChars);
				for(int j = i + findChars.length; j < input.length; j++)
					b.append(input[j]);
				return values.string(b.toString());
			}
			i--;
		}
		return str;
	}
	
	
	public IValue escape(IString str, IMap substitutions) {
		StringBuilder b = new StringBuilder(str.getValue().length() * 2); 
		char[] input = str.getValue().toCharArray();
		
		for (char c : input) {
			IString sub = (IString) substitutions.get(values.string(Character.toString(c)));
			if (sub != null) {
				b.append(sub.getValue());
			}
			else {
				b.append(c);
			}
		}
		return values.string(b.toString());
	}
	
	public IValue contains(IString str, IString find){
		return values.bool(str.getValue().indexOf(find.getValue()) >= 0);
	}
	
	public IValue findAll(IString str, IString find){
		char[] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		IListWriter w = values.listWriter();
		
		for(int i = 0; i <= input.length - findChars.length; i++){
			if(match(input, i, findChars)){
				w.append(values.integer(i));
			}
		}
		return w.done();
	}
	
	public IValue findFirst(IString str, IString find){
		char[] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		
		for(int i = 0; i <= input.length - findChars.length; i++){
			if(match(input, i, findChars)){
				 return values.integer(i);
			}
		}
		return values.integer(-1);
	}
	
	public IValue findLast(IString str, IString find){
		char[] input = str.getValue().toCharArray();
		char [] findChars = find.getValue().toCharArray();
		
		for(int i = input.length - findChars.length; i >= 0; i--){
			if(match(input, i, findChars)){
				 return values.integer(i);
			}
		}
		return values.integer(-1);
	}
	
	/*
	 *  !!EXPERIMENTAL!!
	 * Tuple
	 */
	
	public IList fieldsOf(IValue v){
		if(!v.getType().isTuple())
			throw RuntimeExceptionFactory.illegalArgument(v, null, null, "argument of type tuple is required");
		ITuple tp = (ITuple) v;
		Type tt = tp.getType();
		int a = tt.getArity();
		IListWriter w = values.listWriter();
		for(int i = 0; i < a; i++){
			String fname = tt.getFieldName(i);
			if(fname == null)
				fname = "";
			w.append(values.string(fname));
		}
		return w.done();
	}
	
	/*
	 * ValueIO
	 */
	
	public IValue readBinaryValueFile(IValue type, ISourceLocation loc, IEvaluatorContext ctx){
		
//		TypeStore store = ctx.getCurrentEnvt().getStore();
		TypeStore store = new TypeStore();
		ModuleEnvironment pt = ctx.getHeap().getModule("ParseTree");
		if(pt != null){
			store.importStore(pt.getStore());
		}
		Type start = tr.valueToType((IConstructor) type, store);
		loc = ctx.getHeap().resolveSourceLocation(loc);
		
		InputStream in = null;
		try{
			in = new BufferedInputStream(ctx.getResolverRegistry().getInputStream(loc.getURI()));
			return new BinaryValueReader().read(values, store, start, in);
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}catch(Exception e){
			e.printStackTrace();
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
				}
			}
		}
	}
	
	public IValue readTextValueFile(IValue type, ISourceLocation loc, IEvaluatorContext ctx){
	  loc = ctx.getHeap().resolveSourceLocation(loc);
	  
		TypeStore store = ctx.getCurrentEnvt().getStore();
		Type start = tr.valueToType((IConstructor) type, store);
		
		InputStream in = null;
		try{
			in = new BufferedInputStream(ctx.getResolverRegistry().getInputStream(loc.getURI()));
			return new StandardTextReader().read(values, store, start, new InputStreamReader(in, "UTF8"));
		}catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}finally{
			if(in != null){
				try{
					in.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
				}
			}
		}
	}
	
	public IValue readTextValueString(IValue type, IString input, IEvaluatorContext ctx) {
//		TypeStore store = ctx.getCurrentEnvt().getStore();
		TypeStore store = new TypeStore();
		ModuleEnvironment pt = ctx.getHeap().getModule("ParseTree");
		if(pt != null){
			store.importStore(pt.getStore());
		}
		Type start = tr.valueToType((IConstructor) type, store);
		
		StringReader in = new StringReader(input.getValue());
		try {
			return new StandardTextReader().read(values, store, start, in);
		} catch (FactTypeUseException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}
	}
	
	public void writeBinaryValueFile(ISourceLocation loc, IValue value, IEvaluatorContext ctx){
	  loc = ctx.getHeap().resolveSourceLocation(loc);
	  
		OutputStream out = null;
		try{
			out = ctx.getResolverRegistry().getOutputStream(loc.getURI(), false); 
			new BinaryValueWriter().write(value, out);
		}catch (IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
		}finally{
			if(out != null){
				try{
					out.close();
				}catch(IOException ioex){
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
				}
			}
		}
	}
	
	public void writeTextValueFile(ISourceLocation loc, IValue value, IEvaluatorContext ctx){
	  loc = ctx.getHeap().resolveSourceLocation(loc);
	  
		OutputStream out = null;
		try{
			out = ctx.getResolverRegistry().getOutputStream(loc.getURI(), false);
			new StandardTextWriter().write(value, new OutputStreamWriter(out, "UTF8"));
		}
		 catch(IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}
		finally {
			if (out != null) {
				try {
					out.flush();
					out.close();
				}
				catch(IOException ioex) {
					throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()), null, null);
				}
			}
		}
	}
	
	public IBool rexpMatch(IString s, IString re) {
		if(Pattern.matches(re.getValue(), s.getValue()))
			return values.bool(true);
		else
			return values.bool(false);
	}

	public IList getTraversalContext(IEvaluatorContext ctx) {
		return ctx.getEvaluator().__getCurrentTraversalEvaluator().getContext();
	}
}

// Utilities used by Graph
//TODO: Why is this code in the library? This should be done in pure Rascal.

class Distance{
	public int intval;
	
	Distance(int n){
		intval = n;
	}
}

class NodeComparator implements Comparator<IValue> {
	private final HashMap<IValue,Distance> distance;
	
	NodeComparator(HashMap<IValue,Distance> distance){
		this.distance = distance;
	}

	public int compare(IValue arg0, IValue arg1) {
		int d0 = distance.get(arg0).intval;
		int d1 = distance.get(arg1).intval;
		
		return d0 < d1 ? -1 : ((d0 == d1) ? 0 : 1);
	}
}
