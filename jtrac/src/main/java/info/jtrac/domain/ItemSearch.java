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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Object that holds filter criteria when searching for Items
 * and also creates a Hibernate Criteria query to pass to the DAO
 */
public class ItemSearch implements Serializable {

	public Space space; // if null, means aggregate across all spaces
	private User user; // this will be set in the case space is null

	private int pageSize = 25;
	private int currentPage;
	private long resultCount;
	private String sortFieldName = "id";
	private boolean sortDescending = true;
	private boolean showHistory;
	private boolean batchMode;

	private long selectedItemId;
	private String relatingItemRefId;
	private Collection<Long> itemIds;

	public List<ColumnHeading> columnHeadings;
	private Map<String, FilterCriteria> filterCriteriaMap = new LinkedHashMap<String, FilterCriteria>();

	private String defaultVisibleFlags;


	public ItemSearch(User user) {
		this.user = user;
		this.columnHeadings = ColumnHeading.getColumnHeadings();
		this.defaultVisibleFlags = getVisibleFlags();
	}

	public ItemSearch(Space space) {
		this.space = space;
		this.columnHeadings = ColumnHeading.getColumnHeadings(space);
		this.defaultVisibleFlags = getVisibleFlags();
	}

	public String getDefaultVisibleFlags() {
		return defaultVisibleFlags;
	}

	public String getVisibleFlags() {
		StringBuilder visibleFlags = new StringBuilder();
		for(ColumnHeading ch : columnHeadings) {
			if(ch.isVisible()) {
				visibleFlags.append("1");
			} else  {
				visibleFlags.append("0");
			}
		}
		return visibleFlags.toString();
	}

	public List<Field> getFields() {
		if(space == null) {
			List<Field> list = new ArrayList<Field>(2);
			Field severity = new Field(Field.Name.SEVERITY);
			severity.initOptions();
			list.add(severity);
			Field priority = new Field(Field.Name.PRIORITY);
			priority.initOptions();
			list.add(priority);
			return list;
		} else {
			return space.getMetadata().getFieldList();
		}
	}

	private ColumnHeading getColumnHeading(ColumnHeading.Name name) {
		for(ColumnHeading ch : columnHeadings) {
			if(ch.getName() == name) {
				return ch;
			}
		}
		return null;
	}

	public ColumnHeading getColumnHeading(String name) {
		for(ColumnHeading ch : columnHeadings) {
			if(ch.getNameText().equals(name)) {
				return ch;
			}
		}
		return null;
	}

	private String getStringValue(ColumnHeading ch) {
		String s = (String) ch.getFilterCriteria().getValue();
		if(s == null || s.trim().length() == 0) {
			ch.getFilterCriteria().setExpression(null);
			return null;
		}
		return s;
	}

	public String getRefId() {
		ColumnHeading ch = getColumnHeading(ID);
		return getStringValue(ch);
	}

	public String getSearchText() {
		ColumnHeading ch = getColumnHeading(DETAIL);
		return getStringValue(ch);
	}

	public Collection<Space> getSelectedSpaces() {
		ColumnHeading ch = getColumnHeading(SPACE);
		List<Space> values = ch.getFilterCriteria().getValues();
		if(values == null || values.size() == 0) {
			ch.getFilterCriteria().setExpression(null);
			return user.getSpaces();
		}
		return values;
	}

	public void toggleSortDirection() {
		sortDescending = !sortDescending;
	}

	private <T>List<T> getSingletonList(T o) {
		List<T> list = new ArrayList<T>(1);
		list.add(o);
		return list;
	}

	public void setLoggedBy(User loggedBy) {
		ColumnHeading ch = getColumnHeading(LOGGED_BY);
		ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
		ch.getFilterCriteria().setValues(getSingletonList(loggedBy));
	}

	public void setAssignedTo(User assignedTo) {
		ColumnHeading ch = getColumnHeading(ASSIGNED_TO);
		ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
		ch.getFilterCriteria().setValues(getSingletonList(assignedTo));
	}

	public void setStatus(int i) {
		ColumnHeading ch = getColumnHeading(STATUS);
		ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
		ch.getFilterCriteria().setValues(getSingletonList(i));
	}

	public List<ColumnHeading> getColumnHeadingsToRender() {
		List<ColumnHeading> list = new ArrayList<ColumnHeading>(columnHeadings.size());
		for(ColumnHeading ch : columnHeadings) {
			if(ch.isVisible()) {
				list.add(ch);
			}
		}
		return list;
	}

	//==========================================================================

	public boolean isBatchMode() {
		return batchMode;
	}

	public void setBatchMode(boolean batchMode) {
		this.batchMode = batchMode;
	}

	public Space getSpace() {
		return space;
	}

	public void setSpace(Space space) {
		this.space = space;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(int currentPage) {
		this.currentPage = currentPage;
	}

	public long getResultCount() {
		return resultCount;
	}

	public void setResultCount(long resultCount) {
		this.resultCount = resultCount;
	}

	public String getSortFieldName() {
		return sortFieldName;
	}

	public void setSortFieldName(String sortFieldName) {
		this.sortFieldName = sortFieldName;
	}

	public boolean isSortDescending() {
		return sortDescending;
	}

	public void setSortDescending(boolean sortDescending) {
		this.sortDescending = sortDescending;
	}

	public boolean isShowHistory() {
		return showHistory;
	}

	public void setShowHistory(boolean showHistory) {
		this.showHistory = showHistory;
	}

	public long getSelectedItemId() {
		return selectedItemId;
	}

	public void setSelectedItemId(long selectedItemId) {
		this.selectedItemId = selectedItemId;
	}

	public String getRelatingItemRefId() {
		return relatingItemRefId;
	}

	public void setRelatingItemRefId(String relatingItemRefId) {
		this.relatingItemRefId = relatingItemRefId;
	}

	public Collection<Long> getItemIds() {
		return itemIds;
	}

	public void setItemIds(Collection<Long> itemIds) {
		this.itemIds = itemIds;
	}

	public List<ColumnHeading> getColumnHeadings() {
		return columnHeadings;
	}

	public void setColumnHeadings(List<ColumnHeading> columnHeadings) {
		this.columnHeadings = columnHeadings;
	}

	public Map<String, FilterCriteria> getFilterCriteriaMap() {
		return filterCriteriaMap;
	}

	public void setFilterCriteriaMap(Map<String, FilterCriteria> filterCriteriaMap) {
		this.filterCriteriaMap = filterCriteriaMap;
	}

}
