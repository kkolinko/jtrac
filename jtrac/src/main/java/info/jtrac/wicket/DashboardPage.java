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

import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.PropertyModel;

/**
 * dashboard page
 */
public class DashboardPage extends BasePage {

	public DashboardPage() {

		final User user = getPrincipal();
		setCurrentSpace(null);
		List<UserSpaceRole> spaceRoles = new ArrayList<UserSpaceRole>(user.getSpaceRoles());

		WebMarkupContainer table = new WebMarkupContainer("table");
		WebMarkupContainer message = new WebMarkupContainer("message");

		add(table);
		add(message);

		if(spaceRoles.size() > 0) {
			final CountsHolder countsHolder = getJtrac().loadCountsForUser(user);

			WebMarkupContainer hideLogged = new WebMarkupContainer("hideLogged");
			WebMarkupContainer hideAssigned = new WebMarkupContainer("hideAssigned");
			if(user.getId() == 0) {
				hideLogged.setVisible(false);
				hideAssigned.setVisible(false);
			}
			table.add(hideLogged);
			table.add(hideAssigned);

			table.add(new ListView("dashboardRows", spaceRoles) {
				@Override
				protected void populateItem(final ListItem listItem) {
					UserSpaceRole usr = (UserSpaceRole) listItem.getModelObject();
					Counts counts = countsHolder.getCounts().get(usr.getSpace().getId());
					if (counts == null) {
						counts = new Counts(false); // this can happen if fresh space
					}
					DashboardRowPanel dashboardRow = new DashboardRowPanel("dashboardRow", usr, counts);
					listItem.add(dashboardRow);
				}
			});

			// TODO panelize totals row and reduce redundant code
			WebMarkupContainer total = new WebMarkupContainer("total");

			if(spaceRoles.size() > 1) {

				total.add(new Link("search") {
					@Override
					public void onClick() {
						setResponsePage(ItemSearchFormPage.class);
					}
				});

				if(user.getId() > 0) {
					total.add(new Link("loggedByMe") {
						@Override
						public void onClick() {
							ItemSearch itemSearch = new ItemSearch(user);
							itemSearch.setLoggedBy(user);
							setResponsePage(ItemListPage.class, new PageParameters(Processor.getAsQueryString(itemSearch)));
						}
					}.add(new Label("loggedByMe", new PropertyModel(countsHolder, "totalLoggedByMe"))));

					total.add(new Link("assignedToMe") {
						@Override
						public void onClick() {
							ItemSearch itemSearch = new ItemSearch(user);
							itemSearch.setAssignedTo(user);
							setResponsePage(ItemListPage.class, new PageParameters(Processor.getAsQueryString(itemSearch)));
						}
					}.add(new Label("assignedToMe", new PropertyModel(countsHolder, "totalAssignedToMe"))));
				} else {
					total.add(new WebMarkupContainer("loggedByMe").setVisible(false));
					total.add(new WebMarkupContainer("assignedToMe").setVisible(false));
				}

				total.add(new Link("total") {
					@Override
					public void onClick() {
						ItemSearch itemSearch = new ItemSearch(user);
						setResponsePage(ItemListPage.class, new PageParameters(Processor.getAsQueryString(itemSearch)));
					}
				}.add(new Label("total", new PropertyModel(countsHolder, "totalTotal"))));

			} else {
				total.setVisible(false);
			}
			table.add(total);
			message.setVisible(false);
		} else {
			table.setVisible(false);
		}

	}

}
