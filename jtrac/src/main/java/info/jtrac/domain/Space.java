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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * A JTrac installation can be divided into different project
 * areas or workspaces.  The Space entity represents this concept.
 * The Metdata of a Space determines the type of
 * Items contained within the space.  Users can be mapped to a
 * space with different access permissions.
 */
public class Space implements Serializable, Comparable<Space> {

	private long id;
	private int version;
	private Integer type;
	private String prefixCode;
	private String name;
	private String description;
	private boolean guestAllowed;
	private Metadata metadata;

	public Space() {
		metadata = new Metadata();
	}

	//=======================================================

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public String getPrefixCode() {
		return prefixCode;
	}

	public void setPrefixCode(String prefixCode) {
		this.prefixCode = prefixCode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public boolean isGuestAllowed() {
		return guestAllowed;
	}

	public void setGuestAllowed(boolean guestAllowed) {
		this.guestAllowed = guestAllowed;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("id [").append(id);
		sb.append("]; prefixCode [").append(prefixCode);
		sb.append("]");
		return sb.toString();
	}

	@Override
	public int compareTo(Space s) {
		if(s == null) {
			return 1;
		}
		if(s.name == null) {
			if(name == null) {
				return 0;
			}
			return 1;
		}
		if(name == null) {
			return -1;
		}
		return name.compareTo(s.name);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Space)) {
			return false;
		}
		final Space s = (Space) o;
		return prefixCode.equals(s.getPrefixCode());
	}

	@Override
	public int hashCode() {
		return prefixCode.hashCode();
	}

	/**
	 * This is a rather 'deep' concept, first of course you need to restrict the next possible
	 * states that an item can be switched to based on the current state and the workflow defined.
	 * But what about who all it can be assigned to?  This will be the set of users who fall into roles
	 * that have permissions to transition FROM the state being switched to. Ouch.
	 * This is why the item_view / history update screen has to be Ajaxed so that the drop
	 * down list of users has to dynamically change based on the TO state
	 */
	public List<User> filterUsersAbleToTransitionFrom(List<UserSpaceRole> userSpaceRoles, int state) {
		Set<String> set = getMetadata().getRolesAbleToTransitionFrom(state);
		List<User> list = new ArrayList<User>(userSpaceRoles.size());
		for (UserSpaceRole usr : userSpaceRoles) {
			if (set.contains(usr.getRoleKey())) {
				list.add(usr.getUser());
			}
		}
		return list;
	}

	/**
	 * used to init backing form object in wicket corresponding to ItemUser / notifyList
	 */
	public static List<ItemUser> convertToItemUserList(List<UserSpaceRole> userSpaceRoles) {
	    List<ItemUser> itemUsers = new ArrayList<ItemUser>(userSpaceRoles.size());
	    Set<User> users = new HashSet<User>(itemUsers.size());
	    for (UserSpaceRole usr : userSpaceRoles) {
	        User user = usr.getUser();
	        // we need to do this check as now JTrac supports same user mapped
	        // more than once to a space with different roles
	        if (!users.contains(user)) {
	            users.add(user);
	            itemUsers.add(new ItemUser(user));
	        }
	    }
	    return itemUsers;
	}

}
