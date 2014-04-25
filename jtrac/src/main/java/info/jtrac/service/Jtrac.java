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

import info.jtrac.domain.BatchInfo;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Space;
import info.jtrac.domain.UploadedFile;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;

import java.io.File;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.acegisecurity.userdetails.UserDetailsService;

/**
 * Jtrac main business interface (Service Layer)
 */
public interface Jtrac extends UserDetailsService {

	Item storeItem(Item item, UploadedFile uploadedFile);
	void storeItems(List<Item> items);
	Item updateItem(Item item, User user);
	void storeHistoryForItem(long itemId, History history, UploadedFile uploadedFile);
	Item loadItem(long id);
	Item loadItemByRefId(String refId);
	History loadHistory(long id);
	List<Item> findItems(ItemSearch itemSearch);
	int loadCountOfAllItems();
	List<Item> findAllItems(int firstResult, int batchSize);
	void removeItem(Item item);
	void removeItemItem(ItemItem itemItem);
	//========================================================
	int loadCountOfRecordsHavingFieldNotNull(Space space, Field field);
	int bulkUpdateFieldToNull(Space space, Field field);
	int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey);
	int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey);
	int loadCountOfRecordsHavingStatus(Space space, int status);
	int bulkUpdateStatusToOpen(Space space, int status);
	int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey);
	int bulkUpdateDeleteSpaceRole(Space space, String roleKey);
	//========================================================
	User storeUser(User user);
	void storeUser(User user, String password, boolean sendNotifications);
	void removeUser(User user);
	User loadUser(long id);
	User loadUser(String loginName);
	List<User> findAllUsers();
	List<User> findUsersWhereIdIn(List<Long> ids);
	List<User> findUsersMatching(String searchText, String searchOn);
	List<User> findUsersForSpace(long spaceId);
	List<UserSpaceRole> findUserRolesForSpace(long spaceId);
	Map<Long, List<UserSpaceRole>> loadUserRolesMapForSpace(long spaceId);
	Map<Long, List<UserSpaceRole>> loadSpaceRolesMapForUser(long userId);
	List<User> findUsersWithRoleForSpace(long spaceId, String roleKey);
	List<User> findUsersForUser(User user);
	List<User> findUsersNotFullyAllocatedToSpace(long spaceId);
	int loadCountOfHistoryInvolvingUser(User user);
	//========================================================
	CountsHolder loadCountsForUser(User user);
	Counts loadCountsForUserSpace(User user, Space space);
	//========================================================
	Space storeSpace(Space space);
	Space loadSpace(long id);
	Space loadSpace(String prefixCode);
	List<Space> findAllSpaces();
	List<Space> findSpacesOfUserWhereIdIn(User user, List<Long> ids);
	List<Space> findSpacesWhereGuestAllowed();
	List<Space> findSpacesNotFullyAllocatedToUser(long userId);
	void removeSpace(Space space);
	//========================================================
	User storeUserSpaceRole(User user, Space space, String roleKey);
	UserSpaceRole loadUserSpaceRole(long id);
	User removeUserSpaceRole(UserSpaceRole userSpaceRole);
	//========================================================
	Metadata storeMetadata(Metadata metadata);
	Metadata loadMetadata(long id);
	//========================================================
	String generatePassword();
	String encodeClearText(String clearText);
	Map<String, String> getLocales();
	String getDefaultLocale();
	File getJtracHome();
	int getAttachmentMaxSizeInMb();
	int getSessionTimeoutInMinutes();
	//========================================================
	Map<String, String> loadAllConfig();
	Config storeConfig(Config config);
	String loadConfig(String param);
	//========================================================
	void rebuildIndexes(BatchInfo batchInfo);
	boolean validateTextSearchQuery(String text);
	//========================================================
	void executeHourlyTask();
	void executePollingTask();
	//========================================================
	String getReleaseVersion();
	String getReleaseTimestamp();
	void loadColumnFilterValues(User user, ItemSearch itemSearch, Map<String, Object> parameterValues);
	void writeAsXml(ItemSearch itemSearch, Writer writer);
	void writeAsXml(Writer writer);
	boolean isItemEditAllowed();
}
