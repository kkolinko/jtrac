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

package info.jtrac.repository;

import info.jtrac.domain.Attachment;
import info.jtrac.domain.ColumnHeading;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.ItemUser;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Role;
import info.jtrac.domain.Space;
import info.jtrac.domain.SpaceSequence;
import info.jtrac.domain.State;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

import org.apache.commons.collections.ComparatorUtils;
import org.apache.log4j.Logger;
import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.jpa.HibernateEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * DAO Implementation using Spring Hibernate template
 * note usage of the Spring "init-method" and "destroy-method" options
 */
public class HibernateJtracDao implements JtracDao {

	private final Logger logger = Logger.getLogger(getClass());

	private SchemaHelper schemaHelper;
	@PersistenceContext
	private EntityManager entityManager;
	@Autowired
	private PlatformTransactionManager transactionManager;

	public void setSchemaHelper(SchemaHelper schemaHelper) {
		this.schemaHelper = schemaHelper;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public Item storeItem(Item item) {
		return entityManager.merge(item);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Item loadItem(long id) {
		return entityManager.find(Item.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void storeHistory(History history) {
		entityManager.merge(history);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public History loadHistory(long id) {
		return entityManager.find(History.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Item> findItems(long sequenceNum, String prefixCode) {
		return entityManager.createQuery("from Item item where item.sequenceNum = ? and item.space.prefixCode = ?", Item.class)
				.setParameter(1, sequenceNum)
				.setParameter(2, prefixCode)
				.getResultList()
				;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Item> findItems(ItemSearch itemSearch) {
		int pageSize = itemSearch.getPageSize();
		// TODO: if we are ordering by a custom column, we must load the whole
		// list to do an in-memory sort. we need to find a better way
		Field.Name sortFieldName = Field.isValidName(itemSearch.getSortFieldName()) ? Field.convertToName(itemSearch.getSortFieldName()) : null;
		// only trigger the in-memory sort for drop-down fields and when querying within a space
		// UI currently does not allow you to sort by custom field when querying across spaces, but check again
		boolean doInMemorySort = sortFieldName != null && sortFieldName.isDropDownType() && itemSearch.getSpace() != null;
		DetachedCriteria criteria = getCriteria(itemSearch);
		if (pageSize == -1 || doInMemorySort) {
			@SuppressWarnings("unchecked")
			List<Item> list = criteria.getExecutableCriteria(getSession()).list();
			if(!list.isEmpty() && doInMemorySort) {
				doInMemorySort(list, itemSearch);
			}
			itemSearch.setResultCount(list.size());
			if (pageSize != -1) {
				// order-by was requested on custom field, so we loaded all results, but only need one page
				int start = pageSize * itemSearch.getCurrentPage();
				int end = Math.min(start + itemSearch.getPageSize(), list.size());
				return list.subList(start, end);
			}
			return list;
		} else {
			// pagination
			if(itemSearch.isBatchMode()) {
				entityManager.clear();
			}
			int firstResult = pageSize * itemSearch.getCurrentPage();
			@SuppressWarnings("unchecked")
			List<Item> list = criteria.getExecutableCriteria(getSession())
			.setFirstResult(firstResult)
			.setMaxResults(pageSize)
			.list();
			if(!itemSearch.isBatchMode()) {
				criteria = getCriteriaForCount(itemSearch).criteria;
				criteria.setProjection(Projections.rowCount());
				Long count = (Long) criteria.getExecutableCriteria(getSession()).list().get(0);
				itemSearch.setResultCount(count);
			}
			return list;
		}
	}

	static final class SearchCriteria {
		public final DetachedCriteria criteria;
		public final DetachedCriteria parentCriteria;

		public SearchCriteria(DetachedCriteria criteria, DetachedCriteria parentCriteria) {
			this.criteria = criteria;
			this.parentCriteria = parentCriteria;
		}
	}

	public SearchCriteria getCriteriaForCount(ItemSearch itemSearch) {
		Collection<Long> itemIds = itemSearch.getItemIds();
		Space space = itemSearch.getSpace();

		DetachedCriteria criteria = DetachedCriteria.forClass(itemSearch.isShowHistory() ? History.class : Item.class);
		DetachedCriteria parentCriteria = itemSearch.isShowHistory() ? criteria.createCriteria("parent") : null;
		DetachedCriteria criteriaToChange =  parentCriteria == null ? criteria : parentCriteria;

		if (space == null) {
			criteriaToChange.add(Restrictions.in("space", itemSearch.getSelectedSpaces()));
		} else {
			criteriaToChange.add(Restrictions.eq("space", space));
		}
		if (itemIds != null) {
			criteriaToChange.add(Restrictions.in("id", itemIds));
		}
		for(ColumnHeading columnHeading : itemSearch.getColumnHeadings()) {
			addRestrictions(columnHeading, criteria);
		}
		return new SearchCriteria(criteria, parentCriteria);
	}

	// have to do this two step process as "order by" clause conflicts with "count (*)" clause
	// so the DAO has to use getCriteriaForCount() separately
	private DetachedCriteria getCriteria(ItemSearch itemSearch) {
		SearchCriteria searchCriteria = getCriteriaForCount(itemSearch);
		DetachedCriteria criteria = searchCriteria.criteria;
		DetachedCriteria parent = searchCriteria.parentCriteria;
		if (itemSearch.getSortFieldName() == null) { // can happen only for multi-space search
			itemSearch.setSortFieldName("id"); // effectively is a sort on created date
		}
		String sortFieldName = itemSearch.getSortFieldName();
		Space space = itemSearch.getSpace();
		if(sortFieldName.equals("id") || sortFieldName.equals("space")) {
			if (itemSearch.isShowHistory()) {
				// if showHistory: sort by item.id and then history.id
				if(itemSearch.isSortDescending()) {
					if(space == null) {
						DetachedCriteria parentSpace = parent.createCriteria("space");
						parentSpace.addOrder(Order.desc("name"));
					}
					criteria.addOrder(Order.desc("parent.id"));
					criteria.addOrder(Order.desc("id"));
				} else {
					if(space == null) {
						DetachedCriteria parentSpace = parent.createCriteria("space");
						parentSpace.addOrder(Order.asc("name"));
					}
					criteria.addOrder(Order.asc("parent.id"));
					criteria.addOrder(Order.asc("id"));
				}
			} else {
				if (itemSearch.isSortDescending()) {
					if(space == null) {
						DetachedCriteria parentSpace = criteria.createCriteria("space");
						parentSpace.addOrder(Order.desc("name"));
					}
					criteria.addOrder(Order.desc("id"));
				} else {
					if(space == null) {
						DetachedCriteria parentSpace = criteria.createCriteria("space");
						parentSpace.addOrder(Order.asc("name"));
					}
					criteria.addOrder(Order.asc("id"));
				}
			}
		} else {
			if (itemSearch.isSortDescending()) {
				criteria.addOrder(Order.desc(sortFieldName));
			} else {
				criteria.addOrder(Order.asc(sortFieldName));
			}
		}
		return criteria;
	}

	private void doInMemorySort(List<Item> list, ItemSearch itemSearch) {
		// we should never come here if search is across multiple spaces
		final Field field = itemSearch.getSpace().getMetadata().getField(itemSearch.getSortFieldName());
		final ArrayList<String> valueList = new ArrayList<String>(field.getOptions().keySet());
		Comparator<Item> comp = new Comparator<Item>() {
			@Override
			public int compare(Item left, Item right) {
				Object leftVal = left.getValue(field.getName());
				String leftValString = leftVal == null ? null : leftVal.toString();
				int leftInd = valueList.indexOf(leftValString);
				Object rightVal = right.getValue(field.getName());
				String rightValString = rightVal == null ? null : rightVal.toString();
				int rightInd = valueList.indexOf(rightValString);
				return leftInd - rightInd;
			}
		};
		@SuppressWarnings("unchecked")
		Comparator<Item> comparator = itemSearch.isSortDescending() ? (Comparator<Item>)ComparatorUtils.reversedComparator(comp) : comp;
		Collections.sort(list, comparator);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfAllItems() {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		return entityManager.createQuery(query).getResultList().get(0).intValue();
	}

	@SuppressWarnings("unchecked")
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Item> findAllItems(final int firstResult, final int batchSize) {
		entityManager.clear();
		Session session = getSession();
		Criteria criteria = session.createCriteria(Item.class);
		criteria.setCacheMode(CacheMode.IGNORE);
		criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		criteria.setFetchMode("history", FetchMode.JOIN);
		criteria.add(Restrictions.ge("id", (long) firstResult));
		criteria.add(Restrictions.lt("id", (long) firstResult + batchSize));
		return criteria.list();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeItem(Item item) {
		if (!entityManager.contains(item))
			item = entityManager.merge(item);
		entityManager.remove(item);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeItemItem(ItemItem itemItem) {
		if (!entityManager.contains(itemItem))
			itemItem = entityManager.merge(itemItem);
		entityManager.remove(itemItem);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<ItemUser> findItemUsersByUser(User user) {
		return entityManager.createQuery("from " + ItemUser.class.getName() + " iu where iu.user = ?", ItemUser.class).setParameter(1, user).getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeItemUser(ItemUser itemUser) {
		if (!entityManager.contains(itemUser))
			itemUser = entityManager.merge(itemUser);
		entityManager.remove(itemUser);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void storeAttachment(Attachment attachment) {
		entityManager.merge(attachment);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public Metadata storeMetadata(Metadata metadata) {
		return entityManager.merge(metadata);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Metadata loadMetadata(long id) {
		return entityManager.find(Metadata.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public Space storeSpace(Space space) {
		return entityManager.merge(space);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Space loadSpace(long id) {
		return entityManager.find(Space.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public UserSpaceRole loadUserSpaceRole(long id) {
		return entityManager.find(UserSpaceRole.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public long loadNextSequenceNum(final long spaceSequenceId) {
		entityManager.flush();
		Session session = getSession();
		session.setCacheMode(CacheMode.IGNORE);
		SpaceSequence ss = (SpaceSequence) session.get(SpaceSequence.class, spaceSequenceId);
		long next = ss.getAndIncrement();
		session.update(ss);
		session.flush();
		return next;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void storeSpaceSequence(SpaceSequence spaceSequence) {
		entityManager.merge(spaceSequence);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesByPrefixCode(String prefixCode) {
		return entityManager.createQuery("from Space space where space.prefixCode = ?", Space.class)
				.setParameter(1, prefixCode)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findAllSpaces() {
		return entityManager.createQuery("from Space space order by space.prefixCode", Space.class).getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesNotAllocatedToUser(long userId) {
		return entityManager.createQuery("from Space space where space not in"
				+ " (select usr.space from UserSpaceRole usr where usr.user.id = ?) order by space.name", Space.class)
				.setParameter(1, userId)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesWhereIdIn(List<Long> ids) {
		return entityManager.createQuery("from Space space where space.id in (:ids)", Space.class)
				.setParameter("ids", ids)
				.getResultList()
				;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesWhereGuestAllowed() {
		return entityManager.createQuery("from Space space join fetch space.metadata where space.guestAllowed = true", Space.class)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeSpace(Space space) {
		if (!entityManager.contains(space))
			space = entityManager.merge(space);
		entityManager.remove(space);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public User storeUser(User user) {
		return entityManager.merge(user);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public User loadUser(long id) {
		return entityManager.find(User.class, id);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeUser(User user) {
		if (!entityManager.contains(user))
			user = entityManager.merge(user);
		entityManager.remove(user);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findAllUsers() {
		return entityManager.createQuery("from User user order by user.name", User.class).getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersWhereIdIn(List<Long> ids) {
		return entityManager.createQuery("from User user where user.id in (:ids)", User.class)
				.setParameter("ids", ids)
				.getResultList()
				;
	}

	@SuppressWarnings("unchecked")
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersMatching(final String searchText, final String searchOn) {
		Session session = getSession();
		Criteria criteria = session.createCriteria(User.class);
		criteria.add(Restrictions.ilike(searchOn, searchText, MatchMode.ANYWHERE));
		criteria.addOrder(Order.asc("name"));
		return criteria.list();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersByLoginName(String loginName) {
		return entityManager.createQuery("from User user where user.loginName = ?", User.class)
				.setParameter(1, loginName)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersByEmail(String email) {
		return entityManager.createQuery("from User user where user.email = ?", User.class)
				.setParameter(1, email)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersNotAllocatedToSpace(long spaceId) {
		return entityManager.createQuery("from User user where user not in"
				+ " (select usr.user from UserSpaceRole usr where usr.space.id = ?) order by user.name", User.class)
				.setParameter(1, spaceId)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<UserSpaceRole> findUserRolesForSpace(long spaceId) {
		// join fetch for user object
		return entityManager.createQuery("select usr from UserSpaceRole usr join fetch usr.user"
				+ " where usr.space.id = ? order by usr.user.name", UserSpaceRole.class)
				.setParameter(1,  spaceId)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersWithRoleForSpace(long spaceId, String roleKey) {
		return entityManager.createQuery("from User user"
				+ " join user.userSpaceRoles as usr where usr.space.id = ?"
				+ " and usr.roleKey = ? order by user.name", User.class)
				.setParameter(1, spaceId)
				.setParameter(2, roleKey)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<UserSpaceRole> findSpaceRolesForUser(long userId) {
		String qlString = "select usr from UserSpaceRole usr"
				+ " left join fetch usr.space as space"
				+ " left join fetch space.metadata"
				+ " where usr.user.id = ? order by space.name";
		return entityManager.createQuery(qlString, UserSpaceRole.class)
				.setParameter(1, userId)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findSuperUsers() {
		return entityManager.createQuery("select usr.user from UserSpaceRole usr"
				+ " where usr.space is null and usr.roleKey = ?", User.class)
				.setParameter(1, Role.ROLE_ADMIN)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfHistoryInvolvingUser(User user) {
		Long count = entityManager.createQuery("select count(history) from History history where "
				+ " history.loggedBy = ? or history.assignedTo = ?", Long.class)
				.setParameter(1, user)
				.setParameter(2, user)
				.getResultList().get(0);
		return count.intValue();
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public CountsHolder loadCountsForUser(User user) {
		Collection<Space> spaces = user.getSpaces();
		if (spaces.size() == 0) {
			return null;
		}
		CountsHolder ch = new CountsHolder();
		List<Object[]> loggedByList = entityManager.createQuery("select item.space.id, count(item) from Item item"
				+ " where item.loggedBy.id = ? group by item.space.id", Object[].class)
				.setParameter(1, user.getId())
				.getResultList();
		List<Object[]> assignedToList = entityManager.createQuery("select item.space.id, count(item) from Item item"
				+ " where item.assignedTo.id = ? group by item.space.id", Object[].class)
				.setParameter(1, user.getId())
				.getResultList();
		List<Object[]> statusList = entityManager.createQuery("select item.space.id, count(item) from Item item"
				+ " where item.space in (:spaces) group by item.space.id", Object[].class)
				.setParameter("spaces", spaces)
				.getResultList();
		for(Object[] oa : loggedByList) {
			ch.addLoggedByMe((Long) oa[0], (Long) oa[1]);
		}
		for(Object[] oa : assignedToList) {
			ch.addAssignedToMe((Long) oa[0], (Long) oa[1]);
		}
		for(Object[] oa : statusList) {
			ch.addTotal((Long) oa[0], (Long) oa[1]);
		}
		return ch;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Counts loadCountsForUserSpace(User user, Space space) {
		List<Object[]> loggedByList = entityManager.createQuery("select status, count(item) from Item item"
				+ " where item.loggedBy.id = ? and item.space.id = ? group by item.status", Object[].class)
				.setParameter(1, user.getId())
				.setParameter(2, space.getId())
				.getResultList();
		List<Object[]> assignedToList = entityManager.createQuery("select status, count(item) from Item item"
				+ " where item.assignedTo.id = ? and item.space.id = ? group by item.status", Object[].class)
				.setParameter(1, user.getId())
				.setParameter(2, space.getId())
				.getResultList();

		List<Object[]> statusList = entityManager.createQuery("select status, count(item) from Item item"
				+ " where item.space.id = ? group by item.status", Object[].class)
				.setParameter(1, space.getId())
				.getResultList();
		Counts c = new Counts(true);
		for(Object[] oa : loggedByList) {
			c.addLoggedByMe((Integer) oa[0], (Long) oa[1]);
		}
		for(Object[] oa : assignedToList) {
			c.addAssignedToMe((Integer) oa[0], (Long) oa[1]);
		}
		for(Object[] oa : statusList) {
			c.addTotal((Integer) oa[0], (Long) oa[1]);
		}
		return c;
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersForSpace(long spaceId) {
		return entityManager.createQuery("select distinct u from User u join u.userSpaceRoles usr"
				+ " where usr.space.id = ? order by u.name", User.class)
				.setParameter(1, spaceId)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersForSpaceSet(Collection<Space> spaces) {
		return entityManager.createQuery("select u from User u join u.userSpaceRoles usr"
				+ " where usr.space in (:spaces) order by u.name", User.class)
				.setParameter("spaces", spaces)
				.getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeUserSpaceRole(UserSpaceRole userSpaceRole) {
		if (!entityManager.contains(userSpaceRole))
			userSpaceRole = entityManager.merge(userSpaceRole);
		entityManager.remove(userSpaceRole);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Config> findAllConfig() {
		return entityManager.createQuery("FROM " + Config.class.getName(), Config.class).getResultList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public Config storeConfig(Config config) {
		return entityManager.merge(config);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Config loadConfig(String param) {
		return entityManager.find(Config.class, param);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingFieldNotNull(Space space, Field field) {
		Criteria criteria = getSession().createCriteria(Item.class);
		criteria.add(Restrictions.eq("space", space));
		criteria.add(Restrictions.isNotNull(field.getName().toString()));
		criteria.setProjection(Projections.rowCount());
		int itemCount = (Integer) criteria.list().get(0);
		// even when no item has this field not null currently, items may have history with this field not null
		// because of the "parent" difference, cannot use AbstractItem and have to do a separate Criteria query
		criteria = getSession().createCriteria(History.class);
		criteria.createCriteria("parent").add(Restrictions.eq("space", space));
		criteria.add(Restrictions.isNotNull(field.getName().toString()));
		criteria.setProjection(Projections.rowCount());
		return itemCount + (Integer) criteria.list().get(0);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateFieldToNull(Space space, Field field) {
		int itemCount = entityManager.createQuery("update Item item set item." + field.getName() + " = null"
				+ " where item.space.id = ?")
				.setParameter(1, space.getId())
				.executeUpdate();
		logger.info("no of Item rows where " + field.getName() + " set to null = " + itemCount);
		int historyCount = entityManager.createQuery("update History history set history." + field.getName() + " = null"
				+ " where history.parent in ( from Item item where item.space.id = ? )")
				.setParameter(1, space.getId())
				.executeUpdate();
		logger.info("no of History rows where " + field.getName() + " set to null = " + historyCount);
		return itemCount;
	}

	Session getSession() {
		return entityManager.unwrap(HibernateEntityManager.class).getSession();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey) {
		Criteria criteria = getSession().createCriteria(Item.class);
		criteria.add(Restrictions.eq("space", space));
		criteria.add(Restrictions.eq(field.getName().toString(), optionKey));
		criteria.setProjection(Projections.rowCount());
		int itemCount = (Integer) criteria.list().get(0);
		// even when no item has this field value currently, items may have history with this field value
		// because of the "parent" difference, cannot use AbstractItem and have to do a separate Criteria query
		criteria = getSession().createCriteria(History.class);
		criteria.createCriteria("parent").add(Restrictions.eq("space", space));
		criteria.add(Restrictions.eq(field.getName().toString(), optionKey));
		criteria.setProjection(Projections.rowCount());
		return itemCount + (Integer) criteria.list().get(0);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey) {
		int itemCount = entityManager.createQuery("update Item item set item." + field.getName() + " = null"
				+ " where item.space.id = ?"
				+ " and item." + field.getName() + " = ?")
				.setParameter(1, space.getId())
				.setParameter(2, optionKey)
				.executeUpdate();
		logger.info("no of Item rows where " + field.getName() + " value '" + optionKey + "' replaced with null = " + itemCount);
		int historyCount = entityManager.createQuery("update History history set history." + field.getName() + " = null"
				+ " where history." + field.getName() + " = ?"
				+ " and history.parent in ( from Item item where item.space.id = ? )")
				.setParameter(1, optionKey)
				.setParameter(2, space.getId())
				.executeUpdate();
		logger.info("no of History rows where " + field.getName() + " value '" + optionKey + "' replaced with null = " + historyCount);
		return itemCount;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingStatus(Space space, int status) {
		Session session = getSession();
		Criteria criteria = session.createCriteria(Item.class);
		//		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		//		CriteriaQuery<Item> criteriaQuery = cb.createQuery(Item.class);
		//		Root<Item> count = criteriaQuery.from(Item.class);
		/* FIXME saki		criteriaQuery.select(count).where(restriction); */


		criteria.add(Restrictions.eq("space", space));
		criteria.add(Restrictions.eq("status", status));
		criteria.setProjection(Projections.rowCount());
		int itemCount = (Integer) criteria.list().get(0);
		// even when no item has this status currently, items may have history with this status
		// because of the "parent" difference, cannot use AbstractItem and have to do a separate Criteria query
		criteria = getSession().createCriteria(History.class);
		criteria.createCriteria("parent").add(Restrictions.eq("space", space));
		criteria.add(Restrictions.eq("status", status));
		criteria.setProjection(Projections.rowCount());
		return itemCount + (Integer) criteria.list().get(0);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateStatusToOpen(Space space, int status) {
		int itemCount = entityManager.createQuery("update Item item set item.status = " + State.OPEN
				+ " where item.status = ? and item.space.id = ?")
				.setParameter(1, status)
				.setParameter(2, space.getId())
				.executeUpdate();
		logger.info("no of Item rows where status changed from " + status + " to " + State.OPEN + " = " + itemCount);
		int historyCount = entityManager.createQuery("update History history set history.status = " + State.OPEN
				+ " where history.status = ?"
				+ " and history.parent in ( from Item item where item.space.id = ? )")
				.setParameter(1, status)
				.setParameter(2, space.getId())
				.executeUpdate();
		logger.info("no of History rows where status changed from " + status + " to " + State.OPEN + " = " + historyCount);
		return itemCount;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey) {
		return entityManager.createQuery("update UserSpaceRole usr set usr.roleKey = ?"
				+ " where usr.roleKey = ? and usr.space.id = ?")
				.setParameter(1, newRoleKey)
				.setParameter(2, oldRoleKey)
				.setParameter(3, space.getId())
				.executeUpdate();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateDeleteSpaceRole(Space space, String roleKey) {
		if (roleKey == null) {
			return entityManager.createQuery("delete UserSpaceRole usr where usr.space.id = ?")
					.setParameter(1, space.getId())
					.executeUpdate();
		} else {
			return entityManager.createQuery("delete UserSpaceRole usr"
					+ " where usr.space.id = ? and usr.roleKey = ?")
					.setParameter(1, space.getId())
					.setParameter(2, roleKey)
					.executeUpdate();
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateDeleteItemsForSpace(Space space) {
		int historyCount = entityManager.createQuery("delete History history where history.parent in"
				+ " ( from Item item where item.space.id = ? )")
				.setParameter(1, space.getId())
				.executeUpdate();
		logger.debug("deleted " + historyCount + " records from history");
		int itemItemCount = entityManager.createQuery("delete ItemItem itemItem where itemItem.item in"
				+ " ( from Item item where item.space.id = ? )")
				.setParameter(1, space.getId())
				.executeUpdate();
		logger.debug("deleted " + itemItemCount + " records from item_items");
		int itemCount = entityManager.createQuery("delete Item item where item.space.id = ?")
				.setParameter(1, space.getId())
				.executeUpdate();
		logger.debug("deleted " + itemCount + " records from items");
		return historyCount + itemItemCount + itemCount;
	}

	//==========================================================================

	/**
	 * note that this is automatically configured to run on startup
	 * as a spring bean "init-method"
	 */
	@PostConstruct
	@Transactional(propagation = Propagation.REQUIRED)
	public void createSchema() {
		try {
			entityManager.createQuery("from " + Item.class.getName() + " item where item.id = 1", Item.class).getResultList();
			logger.info("database schema exists, normal startup");
		} catch (Exception e) {
			logger.warn("expected database schema does not exist, will create. Error is: " + e.getMessage());
			schemaHelper.createSchema();
			logger.info("inserting default admin user into database");
			TransactionStatus transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
			storeUser(createAdminUser());
			transactionManager.commit(transactionStatus);
			logger.info("schema creation complete");
		}
		TransactionStatus transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
		List<SpaceSequence> ssList = entityManager.createQuery("FROM " + SpaceSequence.class.getName(), SpaceSequence.class).getResultList();
		Map<Long, SpaceSequence> ssMap = new HashMap<Long, SpaceSequence>(ssList.size());
		for(SpaceSequence ss : ssList) {
			ssMap.put(ss.getId(), ss);
		}
		//		entityManager.flush();
		//		entityManager.createQuery("FROM User").getResultList()
		@SuppressWarnings("unchecked")
		List<Object[]> list = entityManager.createQuery("select item.space.id, max(item.sequenceNum) from Item item group by item.space.id").getResultList();
		for(Object[] oa : list) {
			Long spaceId = (Long) oa[0];
			Long maxSeqNum = (Long) oa[1];
			SpaceSequence ss = ssMap.get(spaceId);
			logger.info("checking space sequence id: " + spaceId + ", max: " + maxSeqNum + ", next: " + ss.getNextSeqNum());
			if(ss.getNextSeqNum() <= maxSeqNum) {
				logger.warn("fixing sequence number for space id: " + spaceId
						+ ", was: " + ss.getNextSeqNum() + ", should be: " + (maxSeqNum + 1));
				ss.setNextSeqNum(maxSeqNum + 1);
				entityManager.merge(ss);
			}
		}
		transactionManager.commit(transactionStatus);
	}

	private User createAdminUser() {
		User admin = new User();
		admin.setLoginName("admin");
		admin.setName("Admin");
		admin.setEmail("admin");
		admin.setPassword("21232f297a57a5a743894a0e4a801fc3");
		admin.addSpaceWithRole(null, Role.ROLE_ADMIN);
		return admin;
	}


	/* get as hibernate restriction and append to passed in criteria that will be used to query the database */
	public void addRestrictions(final ColumnHeading columnHeading, DetachedCriteria criteria) {
		if (columnHeading.isField()) {
			switch (columnHeading.getField().getName().getType()) {
				case 1:
				case 2:
				case 3: {
					if (columnHeading.filterHasValueList()) {
						List<Object> values = columnHeading.getFilterCriteria().getValues();
						List<Integer> keys = new ArrayList<Integer>(values.size());
						for (Object o : values) {
							keys.add(new Integer(o.toString()));
						}
						criteria.add(Restrictions.in(columnHeading.getNameText(), keys));
					}
					break;
				}
				case 4: {// decimal number
					if (columnHeading.filterHasValue()) {
						Object value = columnHeading.getFilterCriteria().getValue();
						switch (columnHeading.getFilterCriteria().getExpression()) {
							case EQ: {
								criteria.add(Restrictions.eq(columnHeading.getNameText(), value));
								break;
							}
							case NOT_EQ: {
								criteria.add(Restrictions.not(Restrictions.eq(columnHeading.getName().getText(), value)));
								break;
							}
							case GT: {
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								break;
							}
							case LT: {
								criteria.add(Restrictions.lt(columnHeading.getNameText(), value));
								break;
							}
							case BETWEEN:  {
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								criteria.add(Restrictions.lt(columnHeading.getNameText(), columnHeading.getFilterCriteria().getValue2()));
								break;
							}
							default:
						}
					}
					break;
				}
				case 6: {// date
					if (columnHeading.filterHasValue()) {
						Object value = columnHeading.getFilterCriteria().getValue();
						switch (columnHeading.getFilterCriteria().getExpression()) {
							case EQ:
								criteria.add(Restrictions.eq(columnHeading.getNameText(), value));
								break;
							case NOT_EQ:
								criteria.add(Restrictions.not(Restrictions.eq(columnHeading.getNameText(), value)));
								break;
							case GT:
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								break;
							case LT:
								criteria.add(Restrictions.lt(columnHeading.getNameText(), value));
								break;
							case BETWEEN:
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								criteria.add(Restrictions.lt(columnHeading.getNameText(), columnHeading.getFilterCriteria().getValue2()));
								break;
							default:
						}
					}
					break;
				}
				case 5: {// free text
					if (columnHeading.filterHasValue()) {
						criteria.add(Restrictions.ilike(columnHeading.getNameText(), (String) columnHeading.getFilterCriteria().getValue(), MatchMode.ANYWHERE));
					}
					break;
				}
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		} else { // this is not a custom field but one of the "built-in" columns
			switch (columnHeading.getName()) {
				case ID: {
					if (columnHeading.filterHasValue()) {
						// should never come here for criteria: see ItemSearch#getRefId()
						throw new RuntimeException("should not come here for 'id'");
					}
					break;
				}
				case SUMMARY: {
					if (columnHeading.filterHasValue()) {
						criteria.add(Restrictions.ilike(columnHeading.getNameText(), (String) columnHeading.getFilterCriteria().getValue(), MatchMode.ANYWHERE));
					}
					break;
				}
				case DETAIL: {
					// do nothing, 'detail' already processed, see: ItemSearch#getSearchText()
					break;
				}
				case STATUS: {
					if (columnHeading.filterHasValueList()) {
						criteria.add(Restrictions.in(columnHeading.getNameText(), columnHeading.getFilterCriteria().getValues()));
					}
					break;
				}
				case ASSIGNED_TO:
				case LOGGED_BY: {
					if (columnHeading.filterHasValueList()) {
						criteria.add(Restrictions.in(columnHeading.getNameText(), columnHeading.getFilterCriteria().getValues()));
					}
					break;
				}
				case TIME_STAMP: {
					if (columnHeading.filterHasValue()) {
						Object value = columnHeading.getFilterCriteria().getValue();
						switch (columnHeading.getFilterCriteria().getExpression()) {
							case GT:
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								break;
							case LT:
								criteria.add(Restrictions.lt(columnHeading.getNameText(), value));
								break;
							case BETWEEN:
								criteria.add(Restrictions.gt(columnHeading.getNameText(), value));
								criteria.add(Restrictions.lt(columnHeading.getNameText(), columnHeading.getFilterCriteria().getValue2()));
								break;
							default:
						}
					}
					break;
				}
				case SPACE: {
					// already handled space as special case, see ItemSearch#getSelectedSpaces()
					break;
				}
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		}

	}


}