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

import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.User;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

/**
 * dashboard page
 */
public class ItemViewPage extends BasePage {

	private Item item2;

	public Item getItem() {
		return item2;
	}

	public void setItem(Item item) {
		this.item2 = item;
	}

	public ItemViewPage(PageParameters params) {
		String refId = params.getString("0");
		logger.debug("item id parsed from url = '" + refId + "'");
		Item item = (
				refId.indexOf('-') != -1
				?	getJtrac().loadItemByRefId(refId) // this in the form SPACE-123
						:	getJtrac().loadItem(Long.parseLong(refId)) // internal id of type long
				);
		setItem(item);
		addComponents();
	}

	private void addComponents() {
		final ItemSearch itemSearch = JtracSession.get().getItemSearch();
		add(new ItemRelatePanel("relate", true, itemSearch));
		Link link = new Link("back") {
			@Override
			public void onClick() {
				itemSearch.setSelectedItemId(getItem().getId());
				if(itemSearch.getRefId() != null) {
					// user had entered item id directly, go back to search page
					setResponsePage(new ItemSearchFormPage(itemSearch));
				} else {
					setResponsePage(new ItemListPage(itemSearch));
				}
			}
		};
		if(itemSearch == null) {
			link.setVisible(false);
		}

		add(link);

		boolean isRelate = itemSearch != null && itemSearch.getRelatingItemRefId() != null;

		User user = getPrincipal();

		if(!user.isAllocatedToSpace(getItem().getSpace().getId())) {
			logger.debug("user is not allocated to space");
			throw new RestartResponseAtInterceptPageException(ErrorPage.class);
		}

		// Edit: Only if there is no history (related item) of the Item
		boolean hasHistory = getItem().hasHistory();

		// Edit: Also the owner of the item should change it.
		boolean canBeEdited = (getItem().wasLoggedBy(user) && getJtrac().isItemEditAllowed() && !hasHistory)
				|| user.isSuperUser()
				|| user.isAdminForSpace(getItem().getSpace().getId());
		add(new Link("edit") {
			@Override
			public void onClick() {
				setResponsePage(new ItemFormPage(getItem().getId()));
			}
		}.setVisible(canBeEdited));
		add(new ItemViewPanel("itemViewPanel", createItemModel(), isRelate || user.getId() == 0));

		if(user.isGuestForSpace(getItem().getSpace()) || isRelate) {
			add(new WebMarkupContainer("itemViewFormPanel").setVisible(false));
		} else {
			add(new ItemViewFormPanel("itemViewFormPanel", getItem()));
		}
	}

	private IModel/*<Item>*/ createItemModel() {
		return new PropertyModel(this, "item");
	}

}
