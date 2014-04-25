/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.domain;

import static info.jtrac.domain.ColumnHeading.Name.*;
import info.jtrac.domain.FilterCriteria.Expression;
import info.jtrac.util.DateUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * used to render columns in the search results table
 * and also in the search filter screen
 */
public class ColumnHeading implements Serializable {

	private static final Map<String, Name> NAMES_MAP;

	// set up a static Map to resolve a String to our ColumnHeading.Name enum value
	static {
		NAMES_MAP = new HashMap<String, Name>();
		for (Name n : Name.values()) {
			NAMES_MAP.put(n.text, n);
		}
	}

	/**
	 * Resolve a String to a valid enum value for ColumnHeading.Name
	 */
	private static Name convertToName(String text) {
		Name n = NAMES_MAP.get(text);
		if (n == null) {
			throw new RuntimeException("Bad name " + text);
		}
		return n;
	}

	/**
	 * test if a given string is a valid column heading name
	 */
	public static boolean isValidName(String text) {
		return NAMES_MAP.containsKey(text);
	}

	public static boolean isValidFieldOrColumnName(String text) {
		return isValidName(text) || Field.isValidName(text);
	}

	public enum Name {

		ID("id"),
		SUMMARY("summary"),
		DETAIL("detail"),
		LOGGED_BY("loggedBy"),
		STATUS("status"),
		ASSIGNED_TO("assignedTo"),
		TIME_STAMP("timeStamp"),
		SPACE("space");

		private String text;

		Name(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public String toString() {
			return text;
		}

	}

	private Field field;
	private Name name;
	private String label;
	private boolean visible = true;

	private FilterCriteria filterCriteria = new FilterCriteria();

	public ColumnHeading(Name name) {
		this.name = name;
		if(name == DETAIL || name == SPACE) {
			visible = false;
		}
	}

	public ColumnHeading(Field field) {
		this.field = field;
		this.label = field.getLabel();
	}

	public boolean isField() {
		return field != null;
	}

	public boolean isDropDownType() {
		if(isField()) {
			return field.isDropDownType();
		} else {
			return name == LOGGED_BY
					|| name == ASSIGNED_TO
					|| name == STATUS;
		}
	}

	public static List<ColumnHeading> getColumnHeadings(Space s) {
		List<ColumnHeading> list = new ArrayList<ColumnHeading>();
		list.add(new ColumnHeading(ID));
		list.add(new ColumnHeading(SUMMARY));
		list.add(new ColumnHeading(DETAIL));
		list.add(new ColumnHeading(STATUS));
		list.add(new ColumnHeading(LOGGED_BY));
		list.add(new ColumnHeading(ASSIGNED_TO));
		for(Field f : s.getMetadata().getFieldList()) {
			list.add(new ColumnHeading(f));
		}
		list.add(new ColumnHeading(TIME_STAMP));
		return list;
	}

	public static List<ColumnHeading> getColumnHeadings() {
		List<ColumnHeading> list = new ArrayList<ColumnHeading>();
		list.add(new ColumnHeading(ID));
		list.add(new ColumnHeading(SPACE));
		list.add(new ColumnHeading(SUMMARY));
		list.add(new ColumnHeading(DETAIL));
		list.add(new ColumnHeading(LOGGED_BY));
		list.add(new ColumnHeading(ASSIGNED_TO));
		list.add(new ColumnHeading(TIME_STAMP));
		return list;
	}

	public List<Expression> getAsList(Expression... expressions) {
		List<Expression> list = new ArrayList<Expression>();
		for(Expression e : expressions) {
			list.add(e);
		}
		return list;
	}

	public boolean filterHasValueList() {
		if(filterCriteria.getExpression() != null
				&& filterCriteria.getValues() != null
				&& filterCriteria.getValues().size() > 0) {
			return true;
		}
		return false;
	}

	public boolean filterHasValue() {
		Object value = filterCriteria.getValue();
		if(filterCriteria.getExpression() != null && value != null && value.toString().trim().length() > 0) {
			return true;
		}
		return false;
	}

	private String prependExpression(String s) {
		return filterCriteria.getExpression().getKey() + "_" + s;
	}

	public String getQueryStringFromValueList() {
		if(!filterHasValueList()) {
			return null;
		}
		String temp = "";
		for(Object o : filterCriteria.getValues()) {
			if(temp.length() > 0) {
				temp = temp + "_";
			}
			temp = temp + o;
		}
		return prependExpression(temp);
	}

	public String getQueryStringFromValue(Class<?> clazz) {
		if(!filterHasValue()) {
			return null;
		}
		String temp = "";
		if(clazz.equals(Date.class)) {
			temp = DateUtils.format((Date) filterCriteria.getValue());
			if(filterCriteria.getValue2() != null) {
				temp = temp + "_" + DateUtils.format((Date) filterCriteria.getValue2());
			}
		} else {
			temp = filterCriteria.getValue() + "";
			if(filterCriteria.getValue2() != null) {
				temp = temp + "_" + filterCriteria.getValue2();
			}
		}
		return prependExpression(temp);
	}

	// TODO refactor code duplication
	public String getQueryStringFromUserList() {
		if(!filterHasValueList()) {
			return null;
		}
		String temp = "";
		List<User> users = filterCriteria.getValues();
		for(User u : users) {
			if(temp.length() > 0) {
				temp = temp + "_";
			}
			temp = temp + u.getId();
		}
		return prependExpression(temp);
	}

	// TODO refactor code duplication
	public String getQueryStringFromSpaceList() {
		if(!filterHasValueList()) {
			return null;
		}
		String temp = "";
		List<Space> spaces = filterCriteria.getValues();
		for(Space s : spaces) {
			if(temp.length() > 0) {
				temp = temp + "_";
			}
			temp = temp + s.getId();
		}
		return prependExpression(temp);
	}

	public static class Tokens {
		public final String firstToken;
		public final List<String> remainingTokens;

		public Tokens(String firstToken, List<String> remainingTokens) {
			this.firstToken = firstToken;
			this.remainingTokens = remainingTokens;
		}
	}

	public static Tokens convertStringToTokens(String s) {
		String [] tokens = s.split("_");
		String firstToken = tokens[0];

		List<String> remainingTokens = new ArrayList<String>();
		// ignore first token, this has been parsed as Expression above
		for(int i = 1; i < tokens.length; i++ ) {
			remainingTokens.add(tokens[i]);
		}
		return new Tokens(firstToken, remainingTokens);
	}

	public void setValueListFromQueryString(String raw) {
		Tokens tokens = convertStringToTokens(raw);
		filterCriteria.setExpression(FilterCriteria.convertToExpression(tokens.firstToken));
		filterCriteria.setValues(tokens.remainingTokens);
	}

	// TODO refactor with more methods in filtercriteria
	public void setValueFromQueryString(String raw, Class<?> clazz) {
		Tokens tokens = convertStringToTokens(raw);
		filterCriteria.setExpression(FilterCriteria.convertToExpression(tokens.firstToken));
		String v1 = tokens.remainingTokens.get(0);
		String v2 = tokens.remainingTokens.size() > 1 ? tokens.remainingTokens.get(1) : null;
		if(clazz.equals(Double.class)) {
			filterCriteria.setValue(new Double(v1));
			if(v2 != null) {
				filterCriteria.setValue2(new Double(v2));
			}
		} else if(clazz.equals(Date.class)) {
			filterCriteria.setValue(DateUtils.convert(v1));
			if(v2 != null) {
				filterCriteria.setValue2(DateUtils.convert(v2));
			}
		} else { // String
			filterCriteria.setValue(v1);
			if(v2 != null) {
				filterCriteria.setValue2(v2);
			}
		}

	}

	public void setStatusListFromQueryString(String raw) {
		Tokens tokens = convertStringToTokens(raw);
		filterCriteria.setExpression(FilterCriteria.convertToExpression(tokens.firstToken));
		List<Integer> statuses = new ArrayList<Integer>();
		for(String s : tokens.remainingTokens) {
			statuses.add(new Integer(s));
		}
		filterCriteria.setValues(statuses);
	}

	public static List<Long> getAsListOfLong(List<String> tokens) {
		List<Long> ids = new ArrayList<Long>();
		for(String s : tokens) {
			ids.add(new Long(s));
		}
		return ids;
	}

	/* custom accessor */
	public void setName(String nameAsString) {
		name = convertToName(nameAsString);
	}

	public String getNameText() {
		if(isField()) {
			return field.getName().getText();
		}
		return name.text;
	}

	//==========================================================================

	public Name getName() {
		return name;
	}

	public Field getField() {
		return field;
	}

	public String getLabel() {
		return label;
	}

	public FilterCriteria getFilterCriteria() {
		return filterCriteria;
	}

	public void setFilterCriteria(FilterCriteria filterCriteria) {
		this.filterCriteria = filterCriteria;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public int hashCode() {
		if(isField()) {
			return field.hashCode();
		}
		return name.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ColumnHeading)) {
			return false;
		}
		final ColumnHeading ch = (ColumnHeading) o;
		if(ch.isField()) {
			return ch.field.equals(field);
		}
		return ch.name.equals(name);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("name [").append(name);
		sb.append("]; filterCriteria [").append(filterCriteria);
		sb.append("]");
		return sb.toString();
	}

}
