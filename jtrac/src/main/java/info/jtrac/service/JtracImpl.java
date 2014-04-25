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

package info.jtrac.service;

import static info.jtrac.domain.ColumnHeading.*;
import info.jtrac.domain.Attachment;
import info.jtrac.domain.BatchInfo;
import info.jtrac.domain.ColumnHeading;
import info.jtrac.domain.ColumnHeading.Tokens;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.FilterCriteria;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemRefId;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.ItemUser;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Role;
import info.jtrac.domain.Space;
import info.jtrac.domain.SpaceSequence;
import info.jtrac.domain.State;
import info.jtrac.domain.UploadedFile;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;
import info.jtrac.lucene.IndexSearcher;
import info.jtrac.lucene.Indexer;
import info.jtrac.mail.MailSender;
import info.jtrac.repository.JtracDao;

import java.io.File;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.acegisecurity.providers.encoding.PasswordEncoder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.wicket.util.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
//import info.jtrac.wicket.Processor;

/**
 * Jtrac Service Layer implementation This is where all the business logic is
 * For data persistence this delegates to JtracDao
 */
public class JtracImpl implements Jtrac {

	static class AttachmentFactory {

		private final File jtracHome;
		private final JtracDao jtracDao;

		public Attachment getAttachment(UploadedFile uploadedFile) {
			if (uploadedFile == null) {
				return null;
			}
			//		logger.debug("fileUpload not null");
			String fileName = Attachment.cleanFileName(uploadedFile.clientFilename);
			Attachment attachment = new Attachment();
			attachment.setFileName(fileName);
			jtracDao.storeAttachment(attachment);
			attachment.setFilePrefix(attachment.getId());
			writeToFile(uploadedFile.inputStream, attachment);
			return attachment;
		}

		private void writeToFile(InputStream inputStream, Attachment attachment) {
			File file = attachment.getFile(jtracHome);
			try {
				Files.writeTo(file, inputStream);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		}

		public AttachmentFactory(File jtracHome, JtracDao jtracDao) {
			this.jtracHome = jtracHome;
			this.jtracDao = jtracDao;
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(JtracImpl.class);

	private JtracDao dao;
	private PasswordEncoder passwordEncoder;
	private MailSender mailSender;
	private Indexer indexer;
	private IndexSearcher indexSearcher;
	private MessageSource messageSource;

	private Map<String, String> locales;
	private String defaultLocale = "en";
	private String releaseVersion;
	private String releaseTimestamp;
	private File jtracHome;
	private int attachmentMaxSizeInMb = 5;
	private int sessionTimeoutInMinutes = 30;

	public void setLocaleList(String[] array) {
		locales = new LinkedHashMap<String, String>();
		for (String localeString : array) {
			Locale locale = StringUtils.parseLocaleString(localeString);
			locales.put(localeString, localeString + " - " + locale.getDisplayName());
		}
		logger.info("available locales configured " + locales);
	}

	public void setDao(JtracDao dao) {
		this.dao = dao;
	}

	public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	public void setIndexSearcher(IndexSearcher indexSearcher) {
		this.indexSearcher = indexSearcher;
	}

	public void setIndexer(Indexer indexer) {
		this.indexer = indexer;
	}

	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public void setReleaseTimestamp(String releaseTimestamp) {
		this.releaseTimestamp = releaseTimestamp;
	}

	public void setReleaseVersion(String releaseVersion) {
		this.releaseVersion = releaseVersion;
	}

	public void setJtracHome(File jtracHome) {
		this.jtracHome = jtracHome;
	}

	@Override
	public File getJtracHome() {
		return jtracHome;
	}

	@Override
	public int getAttachmentMaxSizeInMb() {
		return attachmentMaxSizeInMb;
	}

	@Override
	public int getSessionTimeoutInMinutes() {
		return sessionTimeoutInMinutes;
	}

	/**
	 * this has not been factored into the util package or a helper class because
	 * it depends on the PasswordEncoder configured
	 */
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public String generatePassword() {
		byte[] ab = new byte[1];
		Random r = new Random();
		r.nextBytes(ab);
		return passwordEncoder.encodePassword(new String(ab), null).substring(24);
	}

	/**
	 * this has not been factored into the util package or a helper class because
	 * it depends on the PasswordEncoder configured
	 */
	@Override
	public String encodeClearText(String clearText) {
		return passwordEncoder.encodePassword(clearText, null);
	}

	@Override
	public Map<String, String> getLocales() {
		return locales;
	}

	@Override
	public String getDefaultLocale() {
		return defaultLocale;
	}

	@PostConstruct
	/**
	 * this is automatically called by spring init-method hook on startup, also
	 * called whenever config is edited to refresh TODO move config into a
	 * settings class to reduce service clutter
	 */
	public void init() {
		Map<String, String> config = loadAllConfig();
		initDefaultLocale(config.get("locale.default"));
		initMailSender(config);
		initAttachmentMaxSize(config.get("attachment.maxsize"));
		initSessionTimeout(config.get("session.timeout"));
	}

	private void initMailSender(Map<String, String> config) {
		this.mailSender = new MailSender(config, messageSource, defaultLocale);
	}

	private void initDefaultLocale(String localeString) {
		if (localeString == null || !locales.containsKey(localeString)) {
			logger.warn("invalid default locale configured = '" + localeString + "', using " + this.defaultLocale);
		} else {
			this.defaultLocale = localeString;
		}
		logger.info("default locale set to '" + this.defaultLocale + "'");
	}

	private void initAttachmentMaxSize(String s) {
		try {
			this.attachmentMaxSizeInMb = Integer.parseInt(s);
		} catch (Exception e) {
			logger.warn("invalid attachment max size '" + s + "', using " + attachmentMaxSizeInMb);
		}
		logger.info("attachment max size set to " + this.attachmentMaxSizeInMb + " MB");
	}

	private void initSessionTimeout(String s) {
		try {
			this.sessionTimeoutInMinutes = Integer.parseInt(s);
		} catch (Exception e) {
			logger.warn("invalid session timeout '" + s + "', using " + this.sessionTimeoutInMinutes);
		}
		logger.info("session timeout set to " + this.sessionTimeoutInMinutes + " minutes");
	}

	//==========================================================================



	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public synchronized Item storeItem(Item item, UploadedFile uploadedFile) {
		History history = new History(item);
		if (uploadedFile != null) {

			Attachment attachment = new AttachmentFactory(getJtracHome(), dao).getAttachment(uploadedFile);
			item.add(attachment);
			history.setAttachment(attachment);
		}
		// timestamp can be set by import, then retain
		Date now = item.getTimeStamp();
		if (now == null) {
			now = new Date();
		}
		item.setTimeStamp(now);
		history.setTimeStamp(now);
		item.add(history);
		item.setSequenceNum(dao.loadNextSequenceNum(item.getSpace().getId()));//FIXME saki
		// this will at the moment execute unnecessary updates (bug in Hibernate handling of "version" property)
		// see http://opensource.atlassian.com/projects/hibernate/browse/HHH-1401
		// TODO confirm if above does not happen anymore
		item = dao.storeItem(item);
		if (indexer != null) {
			indexer.index(item);
			indexer.index(history);
		}
		if (item.isSendNotifications()) {
			mailSender.send(item);
		}
		return item;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public synchronized void storeItems(List<Item> items) {
		for (Item item : items) {
			item.setSendNotifications(false);
			if (item.getStatus() == State.CLOSED) {
				// we support CLOSED items for import also but for consistency
				// simulate the item first created OPEN and then being CLOSED
				item.setStatus(State.OPEN);
				History history = new History();
				history.setTimeStamp(item.getTimeStamp());
				// need to do this as storeHistoryForItem does some role checks
				// and so to avoid lazy initialization exception
				history.setLoggedBy(loadUser(item.getLoggedBy().getId()));
				history.setAssignedTo(item.getAssignedTo());
				history.setComment("-");
				history.setStatus(State.CLOSED);
				history.setSendNotifications(false);
				storeItem(item, null);
				storeHistoryForItem(item.getId(), history, null);
			} else {
				storeItem(item, null);
			}
		}
	}

	@Override
	public synchronized Item updateItem(Item item, User user) {
		logger.debug("update item called");
		History history = new History(item);
		history.setAssignedTo(null);
		history.setStatus(null);
		history.setLoggedBy(user);
		history.setComment(item.getEditReason());
		history.setTimeStamp(new Date());
		item.add(history);
		item = dao.storeItem(item); // merge edits + history
		// TODO index?
		if (item.isSendNotifications()) {
			mailSender.send(item);
		}
		return item;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public synchronized void storeHistoryForItem(long itemId, History history, UploadedFile uploadedFile) {
		Item item = dao.loadItem(itemId);
		// first apply edits onto item record before we change the item status
		// the item.getEditableFieldList routine depends on the current State of the item
		for (Field field : item.getEditableFieldList(history.getLoggedBy())) {
			Object value = history.getValue(field.getName());
			if (value != null) {
				item.setValue(field.getName(), value);
			}
		}
		if (history.getStatus() != null) {
			item.setStatus(history.getStatus());
			item.setAssignedTo(history.getAssignedTo()); // this may be null, when closing
		}
		item.setItemUsers(history.getItemUsers());
		// may have been set if this is an import
		if (history.getTimeStamp() == null) {
			history.setTimeStamp(new Date());
		}
		Attachment attachment = new AttachmentFactory(jtracHome, dao).getAttachment(uploadedFile);
		if (attachment != null) {
			item.add(attachment);
			history.setAttachment(attachment);
		}
		item.add(history);
		dao.storeItem(item);
		if (indexer != null) {
			indexer.index(history);
		}
		if (history.isSendNotifications()) {
			mailSender.send(item);
		}
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Item loadItem(long id) {
		return dao.loadItem(id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Item loadItemByRefId(String refId) {
		ItemRefId itemRefId = new ItemRefId(refId); // throws runtime exception if invalid id
		List<Item> items = dao.findItems(itemRefId.getSequenceNum(), itemRefId.getPrefixCode());
		if (items.size() == 0) {
			return null;
		}
		return items.get(0);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public History loadHistory(long id) {
		return dao.loadHistory(id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Item> findItems(ItemSearch itemSearch) {
		String searchText = itemSearch.getSearchText();
		if (searchText != null) {
			List<Long> hits = indexSearcher.findItemIdsContainingText(searchText);
			if (hits.size() == 0) {
				itemSearch.setResultCount(0);
				return Collections.<Item> emptyList();
			}
			itemSearch.setItemIds(hits);
		}
		return dao.findItems(itemSearch);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfAllItems() {
		return dao.loadCountOfAllItems();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Item> findAllItems(int firstResult, int batchSize) {
		return dao.findAllItems(firstResult, batchSize);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeItem(Item item) {
		if (item.getRelatingItems() != null) {
			for (ItemItem itemItem : item.getRelatingItems()) {
				removeItemItem(itemItem);
			}
		}
		if (item.getRelatedItems() != null) {
			for (ItemItem itemItem : item.getRelatedItems()) {
				removeItemItem(itemItem);
			}
		}
		dao.removeItem(item);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeItemItem(ItemItem itemItem) {
		dao.removeItemItem(itemItem);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingFieldNotNull(Space space, Field field) {
		return dao.loadCountOfRecordsHavingFieldNotNull(space, field);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateFieldToNull(Space space, Field field) {
		return dao.bulkUpdateFieldToNull(space, field);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingFieldWithValue(Space space, Field field,
			int optionKey) {
		return dao.loadCountOfRecordsHavingFieldWithValue(space, field, optionKey);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateFieldToNullForValue(Space space, Field field,
			int optionKey) {
		return dao.bulkUpdateFieldToNullForValue(space, field, optionKey);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfRecordsHavingStatus(Space space, int status) {
		return dao.loadCountOfRecordsHavingStatus(space, status);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateStatusToOpen(Space space, int status) {
		return dao.bulkUpdateStatusToOpen(space, status);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey,
			String newRoleKey) {
		return dao.bulkUpdateRenameSpaceRole(space, oldRoleKey, newRoleKey);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int bulkUpdateDeleteSpaceRole(Space space, String roleKey) {
		return dao.bulkUpdateDeleteSpaceRole(space, roleKey);
	}

	// =========  Acegi UserDetailsService implementation ==========
	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public UserDetails loadUserByUsername(String loginName) {
		List<User> users = null;
		if (loginName.indexOf("@") != -1) {
			users = dao.findUsersByEmail(loginName);
		} else {
			users = dao.findUsersByLoginName(loginName);
		}
		if (users.size() == 0) {
			throw new UsernameNotFoundException("User not found for '" + loginName + "'");
		}
		logger.debug("loadUserByUserName success for '" + loginName + "'");
		User user = users.get(0);
		// if some spaces have guest access enabled, allocate these spaces as well
		Set<Space> userSpaces = user.getSpaces();
		for (Space s : findSpacesWhereGuestAllowed()) {
			if (!userSpaces.contains(s)) {
				user.addSpaceWithRole(s, Role.ROLE_GUEST);
			}
		}
		for (UserSpaceRole usr : user.getSpaceRoles()) {
			logger.debug("UserSpaceRole: " + usr);
			// this is a hack, the effect of the next line would be to
			// override hibernate lazy loading and get the space and associated metadata.
			// since this only happens only once on authentication and simplifies a lot of
			// code later because the security principal is "fully prepared",
			// this is hopefully pardonable.  The downside is that there may be as many extra db hits
			// as there are spaces allocated for the user.  Hibernate caching should alleviate this
			usr.isAbleToCreateNewItem();
		}
		return user;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public User loadUser(long id) {
		return dao.loadUser(id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public User loadUser(String loginName) {
		List<User> users = dao.findUsersByLoginName(loginName);
		if (users.size() == 0) {
			return null;
		}
		return users.get(0);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public User storeUser(User user) {
		user.clearNonPersistentRoles();
		return dao.storeUser(user);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void storeUser(User user, String password, boolean sendNotifications) {
		if (password == null) {
			password = generatePassword();
		}
		user.setPassword(encodeClearText(password));
		user = storeUser(user);
		if (sendNotifications) {
			mailSender.sendUserPassword(user, password);
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void removeUser(User user) {
		for (ItemUser iu : dao.findItemUsersByUser(user)) {
			dao.removeItemUser(iu);
		}
		dao.removeUser(user);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findAllUsers() {
		return dao.findAllUsers();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersWhereIdIn(List<Long> ids) {
		return dao.findUsersWhereIdIn(ids);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersMatching(String searchText, String searchOn) {
		return dao.findUsersMatching(searchText, searchOn);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersForSpace(long spaceId) {
		return dao.findUsersForSpace(spaceId);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<UserSpaceRole> findUserRolesForSpace(long spaceId) {
		return dao.findUserRolesForSpace(spaceId);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Map<Long, List<UserSpaceRole>> loadUserRolesMapForSpace(long spaceId) {
		List<UserSpaceRole> list = dao.findUserRolesForSpace(spaceId);
		Map<Long, List<UserSpaceRole>> map = new LinkedHashMap<Long, List<UserSpaceRole>>();
		for (UserSpaceRole usr : list) {
			long userId = usr.getUser().getId();
			List<UserSpaceRole> value = map.get(userId);
			if (value == null) {
				value = new ArrayList<UserSpaceRole>();
				map.put(userId, value);
			}
			value.add(usr);
		}
		return map;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Map<Long, List<UserSpaceRole>> loadSpaceRolesMapForUser(long userId) {
		List<UserSpaceRole> list = dao.findSpaceRolesForUser(userId);
		Map<Long, List<UserSpaceRole>> map = new LinkedHashMap<Long, List<UserSpaceRole>>();
		for (UserSpaceRole usr : list) {
			long spaceId = usr.getSpace() == null ? 0 : usr.getSpace().getId();
			List<UserSpaceRole> value = map.get(spaceId);
			if (value == null) {
				value = new ArrayList<UserSpaceRole>();
				map.put(spaceId, value);
			}
			value.add(usr);
		}
		return map;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersWithRoleForSpace(long spaceId, String roleKey) {
		return dao.findUsersWithRoleForSpace(spaceId, roleKey);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersForUser(User user) {
		Set<Space> spaces = user.getSpaces();
		if (spaces.size() == 0) {
			// this will happen when a user has no spaces allocated
			return Collections.emptyList();
		}
		// must be a better way to make this unique?
		List<User> users = dao.findUsersForSpaceSet(spaces);
		Set<User> userSet = new LinkedHashSet<User>(users);
		return new ArrayList<User>(userSet);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<User> findUsersNotFullyAllocatedToSpace(long spaceId) {
		// trying to reduce database hits and lazy loading as far as possible
		List<User> notAtAllAllocated = dao.findUsersNotAllocatedToSpace(spaceId);
		List<UserSpaceRole> usrs = dao.findUserRolesForSpace(spaceId);
		List<User> notFullyAllocated = new ArrayList<User>(notAtAllAllocated);
		if (usrs.size() == 0) {
			return notFullyAllocated;
		}
		Space space = usrs.get(0).getSpace();
		Set<UserSpaceRole> allocated = new HashSet<>(usrs);
		Set<String> roleKeys = new HashSet<>(space.getMetadata().getAllRoleKeys());
		Set<User> processed = new HashSet<User>(usrs.size());
		Set<User> superUsers = new HashSet<>(dao.findSuperUsers());
		for (UserSpaceRole usr : usrs) {
			User user = usr.getUser();
			if (processed.contains(user)) {
				continue;
			}
			processed.add(user);
			// not using the user object as it is db intensive
			boolean isSuperUser = superUsers.contains(user);
			for (String roleKey : roleKeys) {
				if (isSuperUser && Role.isAdmin(roleKey)) {
					continue;
				}
				UserSpaceRole temp = new UserSpaceRole(user, space, roleKey);
				if (!allocated.contains(temp)) {
					notFullyAllocated.add(user);
					break;
				}
			}
		}
		Collections.sort(notFullyAllocated);
		return notFullyAllocated;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public int loadCountOfHistoryInvolvingUser(User user) {
		return dao.loadCountOfHistoryInvolvingUser(user);
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public CountsHolder loadCountsForUser(User user) {
		return dao.loadCountsForUser(user);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Counts loadCountsForUserSpace(User user, Space space) {
		return dao.loadCountsForUserSpace(user, space);
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public User storeUserSpaceRole(User user, Space space, String roleKey) {
		user.addSpaceWithRole(space, roleKey);
		return storeUser(user);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public User removeUserSpaceRole(UserSpaceRole userSpaceRole) {
		User user = userSpaceRole.getUser();
		user.removeSpaceWithRole(userSpaceRole.getSpace(), userSpaceRole.getRoleKey());
		// dao.storeUser(user);
		dao.removeUserSpaceRole(userSpaceRole);
		return user;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public UserSpaceRole loadUserSpaceRole(long id) {
		return dao.loadUserSpaceRole(id);
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Space loadSpace(long id) {
		return dao.loadSpace(id);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Space loadSpace(String prefixCode) {
		List<Space> spaces = dao.findSpacesByPrefixCode(prefixCode);
		if (spaces.size() == 0) {
			return null;
		}
		return spaces.get(0);
	}

	@Override
	public Space storeSpace(Space space) {
		boolean newSpace = space.getId() == 0;
		space = dao.storeSpace(space);
		if (newSpace) {
			SpaceSequence ss = new SpaceSequence();
			ss.setNextSeqNum(1);
			ss.setId(space.getId());
			dao.storeSpaceSequence(ss);
		}
		return space;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findAllSpaces() {
		return dao.findAllSpaces();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesOfUserWhereIdIn(User user, List<Long> ids) {
		List<Space> spaces = dao.findSpacesWhereIdIn(ids);
		// for security, prevent URL spoofing to show spaces not allocated to user
		List<Space> filteredSpaces = new ArrayList<Space>();
		for (Space space : spaces) {
			if (user.isAllocatedToSpace(space.getId())) {
				filteredSpaces.add(space);
			}
		}
		return filteredSpaces;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesWhereGuestAllowed() {
		return dao.findSpacesWhereGuestAllowed();
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public List<Space> findSpacesNotFullyAllocatedToUser(long userId) {
		// trying to reduce database hits and lazy loading as far as possible
		List<Space> notAtAllAllocated = dao.findSpacesNotAllocatedToUser(userId);
		List<UserSpaceRole> usrs = dao.findSpaceRolesForUser(userId);
		List<Space> notFullyAllocated = new ArrayList<>(notAtAllAllocated);
		if (usrs.size() == 0) {
			return notFullyAllocated;
		}
		Set<UserSpaceRole> allocated = new HashSet<>(usrs);
		Set<Space> processed = new HashSet<Space>(usrs.size());
		User user = usrs.get(0).getUser();
		boolean isSuperUser = user.isSuperUser();
		for (UserSpaceRole usr : usrs) {
			Space space = usr.getSpace();
			if (space == null || processed.contains(space)) {
				continue;
			}
			processed.add(space);
			for (String roleKey : space.getMetadata().getAllRoleKeys()) {
				if (isSuperUser && Role.isAdmin(roleKey)) {
					continue;
				}
				UserSpaceRole temp = new UserSpaceRole(user, space, roleKey);
				if (!allocated.contains(temp)) {
					notFullyAllocated.add(space);
					break;
				}
			}
		}
		Collections.sort(notFullyAllocated);
		return notFullyAllocated;
	}

	@Override
	public void removeSpace(Space space) {
		logger.info("proceeding to delete space: " + space);
		dao.bulkUpdateDeleteSpaceRole(space, null);
		dao.bulkUpdateDeleteItemsForSpace(space);
		dao.removeSpace(space);
		logger.info("successfully deleted space");
	}

	//==========================================================================

	@Override
	public Metadata storeMetadata(Metadata metadata) {
		return dao.storeMetadata(metadata);
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Metadata loadMetadata(long id) {
		return dao.loadMetadata(id);
	}

	//==========================================================================

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public Map<String, String> loadAllConfig() {
		List<Config> list = dao.findAllConfig();
		Map<String, String> allConfig = new HashMap<String, String>(list.size());
		for (Config c : list) {
			allConfig.put(c.getParam(), c.getValue());
		}
		return allConfig;
	}

	// TODO must be some nice generic way to do this
	@Override
	public Config storeConfig(Config config) {
		config = dao.storeConfig(config);
		if (config.isMailConfig()) {
			initMailSender(loadAllConfig());
		} else if (config.isLocaleConfig()) {
			initDefaultLocale(config.getValue());
		} else if (config.isAttachmentConfig()) {
			initAttachmentMaxSize(config.getValue());
		} else if (config.isSessionTimeoutConfig()) {
			initSessionTimeout(config.getValue());
		}
		return config;
	}

	@Override
	@Transactional(propagation = Propagation.SUPPORTS)
	public String loadConfig(String param) {
		Config config = dao.loadConfig(param);
		if (config == null) {
			return null;
		}
		String value = config.getValue();
		if (value == null || value.trim().equals("")) {
			return null;
		}
		return value;
	}

	//========================================================

	@Override
	public void rebuildIndexes(BatchInfo batchInfo) {
		File file = new File(jtracHome + "/indexes");
		for (File f : file.listFiles()) {
			logger.debug("deleting file: " + f);
			f.delete();
		}
		logger.info("existing index files deleted successfully");
		int totalSize = dao.loadCountOfAllItems();
		batchInfo.setTotalSize(totalSize);
		logger.info("total items to index: " + totalSize);
		int firstResult = 0;
		long lastFetchedId = 0;
		while (true) {
			logger.info("processing batch starting from: " + firstResult + ", current: " + batchInfo.getCurrentPosition());
			List<Item> items = dao.findAllItems(firstResult, batchInfo.getBatchSize());
			for (Item item : items) {

				indexer.index(item);

				// currently history is indexed separately from item
				// not sure if this is a good thing, maybe it gives
				// more flexibility e.g. fine-grained search results

				int historyCount = 0;
				for (History history : item.getHistory()) {
					indexer.index(history);
					historyCount++;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("indexed item: " + item.getId() + " : " + item.getRefId() + ", history: " + historyCount);
				}
				batchInfo.incrementPosition();
				lastFetchedId = item.getId();
			}
			if (logger.isDebugEnabled()) {
				logger.debug("size of current batch: " + items.size());
				logger.debug("last fetched Id: " + lastFetchedId);
			}
			firstResult += batchInfo.getBatchSize();
			if (logger.isDebugEnabled()) {
				logger.debug("setting firstResult to: " + firstResult);
			}
			if (batchInfo.isComplete()) {
				logger.info("batch completed at position: " + batchInfo.getCurrentPosition());
				break;
			}
		}

	}

	@Override
	public boolean validateTextSearchQuery(String text) {
		return indexSearcher.validateQuery(text);
	}

	//==========================================================================

	@Override
	public void executeHourlyTask() {
		logger.debug("hourly task called");
	}

	/* configured to be called every five minutes */
	@Override
	public void executePollingTask() {
		logger.debug("polling task called");
	}

	//==========================================================================

	@Override
	public String getReleaseVersion() {
		return releaseVersion;
	}

	@Override
	public String getReleaseTimestamp() {
		return releaseTimestamp;
	}

	@Override
	public void loadColumnFilterValues(User user, ItemSearch itemSearch, Map<String, Object> parameterValues) {
		for (Entry<String, Object> entry : parameterValues.entrySet()) {
			String name = entry.getKey();
			ColumnHeading columnHeading = itemSearch.getColumnHeading(name);
			Object value = entry.getValue();
			loadFromQueryString(columnHeading, value == null ? null : value.toString(), user);
		}
	}

	/* load a querystring representation and initialize filter critera when acting on a bookmarkable url */
	private void loadFromQueryString(ColumnHeading columnHeading, String s,
			User user) {
		if (columnHeading.isField()) {
			switch (columnHeading.getField().getName().getType()) {
				//==============================================================
				case 1:
				case 2:
				case 3: {
					columnHeading.setValueListFromQueryString(s);
					break;
				}
				case 4: {// decimal number
					columnHeading.setValueFromQueryString(s, Double.class);
					break;
				}
				case 6: {// date
					columnHeading.setValueFromQueryString(s, Date.class);
					break;
				}
				case 5: {// free text
					columnHeading.setValueFromQueryString(s, String.class);
					break;
				}
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		} else { // this is not a custom field but one of the "built-in" columns
			switch (columnHeading.getName()) {
				case ID: {
					columnHeading.setValueFromQueryString(s, String.class);
					break;
				}
				case SUMMARY: {
					columnHeading.setValueFromQueryString(s, String.class);
					break;
				}
				case DETAIL: {
					columnHeading.setValueFromQueryString(s, String.class);
					break;
				}
				case STATUS: {
					columnHeading.setStatusListFromQueryString(s);
					break;
				}
				case ASSIGNED_TO:
				case LOGGED_BY: {
					Tokens tokens = convertStringToTokens(s);
					List<User> users = findUsersWhereIdIn(getAsListOfLong(tokens.remainingTokens));
					columnHeading.getFilterCriteria().setExpression(FilterCriteria.convertToExpression(tokens.firstToken));
					columnHeading.getFilterCriteria().setValues(users);
					break;
				}
				case TIME_STAMP: {
					columnHeading.setValueFromQueryString(s, Date.class);
					break;
				}
				case SPACE: {
					Tokens tokens = ColumnHeading.convertStringToTokens(s);
					columnHeading.getFilterCriteria().setExpression(FilterCriteria.convertToExpression(tokens.firstToken));
					List<Space> spaces = findSpacesOfUserWhereIdIn(user, ColumnHeading.getAsListOfLong(tokens.remainingTokens));
					columnHeading.getFilterCriteria().setValues(spaces);
					break;
				}
				default:
					throw new RuntimeException("Unknown Column Heading " + columnHeading.getName());
			}
		}

	}

	@Override
	public void writeAsXml(ItemSearch itemSearch, Writer writer) {
		final int batchSize = 500;
		int originalPageSize = itemSearch.getPageSize();
		int originalCurrentPage = itemSearch.getCurrentPage();

		// get the total count first
		itemSearch.setPageSize(0);
		itemSearch.setCurrentPage(0);
		findItems(itemSearch);
		long totalSize = itemSearch.getResultCount();
		Item.logger.debug("total count: " + totalSize);

		itemSearch.setBatchMode(true);
		itemSearch.setPageSize(batchSize);
		try {
			writer.write("<items>");
			int currentPage = 0;
			int currentItem = 0;
			while (true) {
				Item.logger.debug("processing batch starting from page: " + currentPage);
				itemSearch.setCurrentPage(currentPage);
				List<Item> items = findItems(itemSearch);
				for (Item item : items) {
					item.getAsXml().write(writer);
					currentItem++;
				}
				Item.logger.debug("size of current batch: " + items.size());
				if (currentItem >= totalSize) {
					Item.logger.info("batch completed at position: " + currentItem);
					break;
				} else {
					currentPage++;
				}
			}
			writer.write("</items>");
			writer.flush();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			itemSearch.setPageSize(originalPageSize);
			itemSearch.setCurrentPage(originalCurrentPage);
			itemSearch.setBatchMode(false);
		}
	}

	@Override
	public void writeAsXml(Writer writer) {
		final int batchSize = 500;
		int totalSize = loadCountOfAllItems();
		Item.logger.info("total count: " + totalSize);
		int firstResult = 0;
		int currentItem = 0;
		try {
			while (true) {
				Item.logger.info("processing batch starting from: " + firstResult + ", current: " + currentItem);
				List<Item> items = findAllItems(firstResult, batchSize);
				for (Item item : items) {
					item.getAsXml().write(writer);
					currentItem++;
				}
				Item.logger.debug("size of current batch: " + items.size());
				firstResult += batchSize;
				if (currentItem >= totalSize || firstResult > totalSize) {
					Item.logger.info("batch completed at position: " + currentItem);
					writer.flush();
					break;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isItemEditAllowed() {
		final Map<String, String> configMap = loadAllConfig();
		String shouldEdit = configMap.get("jtrac.edit.item");
		return "true".equals(shouldEdit);
	}

}
