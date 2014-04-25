package info.jtrac.wicket;

import static info.jtrac.domain.FilterCriteria.Expression.*;
import info.jtrac.domain.ColumnHeading;
import info.jtrac.domain.FilterCriteria.Expression;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Space;
import info.jtrac.domain.State;
import info.jtrac.domain.User;
import info.jtrac.service.Jtrac;
import info.jtrac.wicket.yui.YuiCalendar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.PropertyModel;

/**
 * also see description below for the private getProcessor() method
 */
public abstract class Processor implements Serializable {

	/* return the possible expressions (equals, greater-than etc) to show on filter UI for selection */
	public abstract List<Expression> getValidFilterExpressions();

	/* return the wicket ui fragment that will be shown over ajax (based on selected expression) */
	public abstract Fragment getFilterUiFragment(MarkupContainer container, User user, Space space, Jtrac jtrac);

	/* return a querystring representation of the filter criteria to create a bookmarkable url */
	public abstract String getAsQueryString();

	/**
	 * this routine is a massive if-then construct that acts as a factory for the
	 * right implementation of the responsibilities defined in the "Processor"
	 * class (above) based on the type of ColumnHeading - the right implementation
	 * will be returned. having everything in one place below, makes it easy to
	 * maintain, as the logic of each of the methods are closely interdependent
	 * for a given column type for e.g. the kind of hibernate criteria needed
	 * depends on what is made available on the UI
	 */
	public static Processor getProcessor(final ColumnHeading columnHeading) {
		if (columnHeading.isField()) {
			switch (columnHeading.getField().getName().getType()) {
				//==============================================================
				case 1:
				case 2:
				case 3:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(IN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "multiSelect", container);
							final Map<String, String> options = columnHeading.getField().getOptions();
							JtracCheckBoxMultipleChoice choice = new JtracCheckBoxMultipleChoice("values", new ArrayList<>(options.keySet()), new IChoiceRenderer() {
								@Override
								public Object getDisplayValue(Object o) {
									return options.get(o);
								}
								@Override
								public String getIdValue(Object o, int i) {
									return o.toString();
								}
							});
							fragment.add(choice);
							choice.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "values"));
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValueList();
						}
					};
					//==============================================================
				case 4: // decimal number
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(EQ, NOT_EQ, GT, LT, BETWEEN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "textField", container);
							TextField textField = new TextField("value", Double.class);
							textField.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "value"));
							fragment.add(textField);
							if (columnHeading.getFilterCriteria().getExpression() == BETWEEN) {
								TextField textField2 = new TextField("value2", Double.class);
								textField.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "value2"));
								fragment.add(textField2);
							} else {
								fragment.add(new WebMarkupContainer("value2").setVisible(false));
							}
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(Double.class);
						}
					};
					//==============================================================
				case 6: // date
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(EQ, NOT_EQ, GT, LT, BETWEEN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "dateField", container);
							YuiCalendar calendar = new YuiCalendar("value", new PropertyModel(columnHeading.getFilterCriteria(), "value"), false);
							fragment.add(calendar);
							if (columnHeading.getFilterCriteria().getExpression() == BETWEEN) {
								YuiCalendar calendar2 = new YuiCalendar("value2", new PropertyModel(columnHeading.getFilterCriteria(), "value2"), false);
								fragment.add(calendar2);
							} else {
								fragment.add(new WebMarkupContainer("value2").setVisible(false));
							}
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(Date.class);
						}
					};
					//==============================================================
				case 5: // free text
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(CONTAINS);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							return getTextFieldFragment(columnHeading, container);
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(String.class);
						}
					};
					//==============================================================
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		} else { // this is not a custom field but one of the "built-in" columns
			switch (columnHeading.getName()) {
				//==============================================================
				case ID:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(EQ);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							return getTextFieldFragment(columnHeading, container);
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(String.class);
						}
					};
					//==============================================================
				case SUMMARY:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(CONTAINS);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							return getTextFieldFragment(columnHeading, container);
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(String.class);
						}
					};
					//==============================================================
				case DETAIL:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(CONTAINS);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							return getTextFieldFragment(columnHeading, container);
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(String.class);
						}
					};

					//==============================================================
				case STATUS:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(IN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "multiSelect", container);
							// status selectable only when context space is not null
							final Map<Integer, String> options = space.getMetadata().getStatesMap();
							options.remove(State.NEW);
							JtracCheckBoxMultipleChoice choice = new JtracCheckBoxMultipleChoice("values", new ArrayList<>(options.keySet()), new IChoiceRenderer() {
								@Override
								public Object getDisplayValue(Object o) {
									return options.get(o);
								}
								@Override
								public String getIdValue(Object o, int i) {
									return o.toString();
								}
							});
							fragment.add(choice);
							choice.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "values"));
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValueList();
						}
					};
					//==============================================================
				case ASSIGNED_TO:
				case LOGGED_BY:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(IN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "multiSelect", container);
							List<User> users = null;
							if (space == null) {
								users = jtrac.findUsersForUser(user);
							} else {
								users = jtrac.findUsersForSpace(space.getId());
							}
							JtracCheckBoxMultipleChoice choice = new JtracCheckBoxMultipleChoice("values", users, new IChoiceRenderer() {
								@Override
								public Object getDisplayValue(Object o) {
									return ((User) o).getName();
								}
								@Override
								public String getIdValue(Object o, int i) {
									return ((User) o).getId() + "";
								}
							});
							fragment.add(choice);
							choice.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "values"));
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromUserList();
						}
					};
					//==============================================================
				case TIME_STAMP:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(BETWEEN, GT, LT);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "dateField", container);
							YuiCalendar calendar = new YuiCalendar("value", new PropertyModel(columnHeading.getFilterCriteria(), "value"), false);
							fragment.add(calendar);
							if (columnHeading.getFilterCriteria().getExpression() == BETWEEN) {
								YuiCalendar calendar2 = new YuiCalendar("value2", new PropertyModel(columnHeading.getFilterCriteria(), "value2"), false);
								fragment.add(calendar2);
							} else {
								fragment.add(new WebMarkupContainer("value2").setVisible(false));
							}
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromValue(Date.class);
						}
					};
					//==============================================================
				case SPACE:
					return new Processor() {
						@Override
						public List<Expression> getValidFilterExpressions() {
							return columnHeading.getAsList(IN);
						}
						@Override
						public Fragment getFilterUiFragment(MarkupContainer container,
								User user, Space space, Jtrac jtrac) {
							Fragment fragment = new Fragment("fragParent", "multiSelect", container);
							List<Space> spaces = new ArrayList<>(user.getSpaces());
							JtracCheckBoxMultipleChoice choice = new JtracCheckBoxMultipleChoice("values", spaces, new IChoiceRenderer() {
								@Override
								public Object getDisplayValue(Object o) {
									return ((Space) o).getName();
								}
								@Override
								public String getIdValue(Object o, int i) {
									return ((Space) o).getId() + "";
								}
							});
							fragment.add(choice);
							choice.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "values"));
							return fragment;
						}
						@Override
						public String getAsQueryString() {
							return columnHeading.getQueryStringFromSpaceList();
						}
					};
					//==============================================================
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		}
	}

	public static Map<String, String> getAsQueryString(ItemSearch itemSearch) {
		Map<String, String> map = new HashMap<String, String>();
		if(itemSearch.space != null) {
			map.put("s", itemSearch.space.getId() + "");
		}
		for(ColumnHeading ch : itemSearch.columnHeadings) {
			String s = Processor.getProcessor(ch).getAsQueryString();
			if(s != null) {
				map.put(ch.getNameText(), s);
			}
		}
		String visibleFlags = itemSearch.getVisibleFlags();
		if(!visibleFlags.equals(itemSearch.getDefaultVisibleFlags())) {
			map.put("cols", visibleFlags.toString());
		}
		if (itemSearch.isShowHistory()) {
			map.put("showHistory", "true");
		}
		if (itemSearch.getPageSize() != 25) {
			map.put("pageSize", itemSearch.getPageSize() + "");
		}
		if (!itemSearch.isSortDescending()) {
			map.put("sortAscending", "true");
		}
		if (!itemSearch.getSortFieldName().equals("id")) {
			map.put("sortFieldName", itemSearch.getSortFieldName());
		}
		if (itemSearch.getRelatingItemRefId() != null) {
			map.put("relatingItemRefId", itemSearch.getRelatingItemRefId());
		}
		return map;
	}

	public static Fragment getTextFieldFragment(ColumnHeading columnHeading, MarkupContainer container) {
		Fragment fragment = new Fragment("fragParent", "textField", container);
		TextField textField = new TextField("value", String.class);
		textField.setModel(new PropertyModel(columnHeading.getFilterCriteria(), "value"));
		fragment.add(textField);
		fragment.add(new WebMarkupContainer("value2").setVisible(false));
		return fragment;
	}

}