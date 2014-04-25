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

package info.jtrac.wicket;

import info.jtrac.domain.ColumnHeading;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.service.Jtrac;
import info.jtrac.service.JtracSecurityException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.PageParameters;

/**
 * item list page
 */
public class ItemListPage extends BasePage {

	public ItemListPage(PageParameters params) throws JtracSecurityException {
		ItemSearch itemSearch = getItemSearch(getJtrac(), getPrincipal(), params);
		JtracSession.get().setItemSearch(itemSearch);
		addComponents(itemSearch);
	}

	public ItemListPage(ItemSearch itemSearch) {
		addComponents(itemSearch);
	}

	private void addComponents(ItemSearch itemSearch) {
		add(new ItemListPanel("panel", itemSearch));
		add(new ItemRelatePanel("relate", false, itemSearch));
	}

	public static ItemSearch getItemSearch(Jtrac jtrac, User user, PageParameters params) throws JtracSecurityException {
		ItemSearch itemSearch = null;
		long spaceId = params.getLong("s", -1);
		if (spaceId > 0) {
			Space space = jtrac.loadSpace(spaceId);
			if (!user.isAllocatedToSpace(space.getId())) {
				throw new JtracSecurityException("User not allocated to space: " + spaceId + " in URL: " + params);
			}
			itemSearch = new ItemSearch(space);
		} else {
			itemSearch = new ItemSearch(user);
		}
		setPagingRelatedParameters(itemSearch, params);

		Map<String, Object> parameterValues = getParameterValues(params);
		jtrac.loadColumnFilterValues(user, itemSearch, parameterValues);
		setColumnVisibility(itemSearch.getColumnHeadings(), params.getString("cols", null));
		return itemSearch;
	}

	private static void setPagingRelatedParameters(ItemSearch itemSearch,
			PageParameters params) {
		itemSearch.setShowHistory(params.getBoolean("showHistory"));
		itemSearch.setPageSize(params.getInt("pageSize", 25));
		itemSearch.setSortDescending(!params.getBoolean("sortAscending"));
		itemSearch.setSortFieldName(params.getString("sortFieldName", "id"));
		itemSearch.setRelatingItemRefId(params.getString("relatingItemRefId", null));
	}

	private static Map<String, Object> getParameterValues(PageParameters params) {
		@SuppressWarnings("unchecked")
		Set<String> keySet = params.keySet();
		Map<String, Object> values = new HashMap<>();
		for (String name : keySet) {
			if (ColumnHeading.isValidFieldOrColumnName(name)) {
				values.put(name, params.getString(name));
			}
		}
		return values;
	}

	private static void setColumnVisibility(List<ColumnHeading> columnHeadings,
			String visibleFlags) {
		if (visibleFlags != null) {
			int i = 0;
			for (ColumnHeading ch : columnHeadings) {
				if (i >= visibleFlags.length()) {
					break;
				}
				char flag = visibleFlags.charAt(i);
				if (flag == '1') {
					ch.setVisible(true);
				} else {
					ch.setVisible(false);
				}
				i++;
			}
		}
	}


}
