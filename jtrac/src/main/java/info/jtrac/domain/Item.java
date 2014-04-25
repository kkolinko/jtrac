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

import info.jtrac.util.DateUtils;
import info.jtrac.util.XmlUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

/**
 * This object represents a generic item which can be an issue, defect, task etc.
 * some logic for field accessors and conversion of keys to display values
 * is contained in the AbstractItem class
 */
public class Item extends AbstractItem {

	private Integer type;
	private Space space;
	private long sequenceNum;

	private Set<History> history;
	private Set<Item> children;
	private Set<Attachment> attachments;

	// should be ideally in form backing object but for convenience
	private String editReason;
	public static final Logger logger = LoggerFactory.getLogger(Item.class);

	@Override
	public String getRefId() {
		return getSpace().getPrefixCode() + "-" + sequenceNum;
	}

	public Map<Integer, String> getPermittedTransitions(User user) {
		return user.getPermittedTransitions(space, getStatus());
	}

	public List<Field> getEditableFieldList(User user) {
		return user.getEditableFieldList(space, getStatus());
	}

	public void add(History h) {
		if (this.history == null) {
			this.history = new LinkedHashSet<History>();
		}
		h.setParent(this);
		this.history.add(h);
	}

	public void add(Attachment attachment) {
		if (attachments == null) {
			attachments = new LinkedHashSet<Attachment>();
		}
		attachments.add(attachment);
	}

	public void addRelated(Item relatedItem, int relationType) {
		if (getRelatedItems() == null) {
			setRelatedItems(new LinkedHashSet<ItemItem>());
		}
		ItemItem itemItem = new ItemItem(this, relatedItem, relationType);
		getRelatedItems().add(itemItem);
	}

	/**
	 * Lucene DocumentCreator implementation
	 */
	@Override
	public Document createDocument() {
		Document d = new Document();
		d.add(new org.apache.lucene.document.Field("id", getId() + "", Store.YES, Index.NO));
		d.add(new org.apache.lucene.document.Field("type", "item", Store.YES, Index.NO));
		StringBuffer sb = new StringBuffer();
		if (getSummary() != null) {
			sb.append(getSummary());
		}
		if (getDetail() != null) {
			if (sb.length() > 0) {
				sb.append(" | ");
			}
			sb.append(getDetail());
		}
		d.add(new org.apache.lucene.document.Field("text", sb.toString(), Store.NO, Index.TOKENIZED));
		return d;
	}

	public History getLatestHistory() {
		if (history == null) {
			return null;
		}
		History out = null;
		for(History h : history) {
			out = h;
		}
		return out;
	}

	//===========================================================

	@Override
	public Space getSpace() {
		return space;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public void setSpace(Space space) {
		this.space = space;
	}

	public long getSequenceNum() {
		return sequenceNum;
	}

	public void setSequenceNum(long sequenceNum) {
		this.sequenceNum = sequenceNum;
	}

	public Set<History> getHistory() {
		return history;
	}

	public void setHistory(Set<History> history) {
		this.history = history;
	}

	public Set<Item> getChildren() {
		return children;
	}

	public void setChildren(Set<Item> children) {
		this.children = children;
	}

	public Set<Attachment> getAttachments() {
		return attachments;
	}

	public void setAttachments(Set<Attachment> attachments) {
		this.attachments = attachments;
	}

	public String getEditReason() {
		return editReason;
	}

	public void setEditReason(String editReason) {
		this.editReason = editReason;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(super.toString());
		sb.append("; type [").append(type);
		sb.append("]; space [").append(space);
		sb.append("]; sequenceNum [").append(sequenceNum);
		sb.append("]");
		return sb.toString();
	}

	public Element getAsXml() {
		// root
		Element root = XmlUtils.getNewElement("item");
		root.addAttribute("refId", this.getRefId());
		// related items
		if (this.getRelatedItems() != null && this.getRelatedItems().size() > 0) {
			Element relatedItems = root.addElement("relatedItems");
			for(ItemItem itemItem : this.getRelatedItems()) {
				Element relatedItem = relatedItems.addElement("relatedItem");
				relatedItem.addAttribute("refId", itemItem.getItem().getRefId());
				relatedItem.addAttribute("linkType", itemItem.getRelationText());
			}
		}
		// relating items
		if (this.getRelatingItems() != null && this.getRelatingItems().size() > 0) {
			Element relatingItems = root.addElement("relatingItems");
			for(ItemItem itemItem : this.getRelatingItems()) {
				Element relatingItem = relatingItems.addElement("relatingItem");
				relatingItem.addAttribute("refId", itemItem.getItem().getRefId());
				relatingItem.addAttribute("linkType", itemItem.getRelationText());
			}
		}
		// summary
		if (this.getSummary() != null) {
			root.addElement("summary").addText(this.getSummary());
		}
		// detail
		if (this.getDetail() != null) {
			root.addElement("detail").addText(this.getDetail());
		}
		// logged by
		Element loggedBy = root.addElement("loggedBy");
		// loggedBy.addAttribute("userId", item.getLoggedBy().getId() + "");
		loggedBy.addText(this.getLoggedBy().getName());
		// assigned to
		if (this.getAssignedTo() != null) {
			Element assignedTo = root.addElement("assignedTo");
			// assignedTo.addAttribute("userId", item.getAssignedTo().getId() + "");
			assignedTo.addText(this.getAssignedTo().getName());
		}
		// status
		Element status = root.addElement("status");
		status.addAttribute("statusId", this.getStatus() + "");
		status.addText(this.getStatusValue());
		// custom fields
		Map<Field.Name, Field> fields = this.getSpace().getMetadata().getFields();
		for(Field.Name fieldName : this.getSpace().getMetadata().getFieldOrder()) {
			Object value = this.getValue(fieldName);
			if(value != null) {
				Field field = fields.get(fieldName);
				Element customField = root.addElement(fieldName.getText());
				customField.addAttribute("label", field.getLabel());
				if(field.isDropDownType()) {
					customField.addAttribute("optionId", value + "");
				}
				customField.addText(this.getCustomValue(fieldName));
			}
		}
		// timestamp
		Element timestamp = root.addElement("timestamp");
		timestamp.addText(DateUtils.formatTimeStamp(this.getTimeStamp()));
		// history
		if (this.getHistory() != null) {
			Element historyRoot = root.addElement("history");
			for(History history : this.getHistory()) {
				Element event = historyRoot.addElement("event");
				// index
				event.addAttribute("eventId", (history.getIndex() + 1) + "");
				// logged by
				Element historyLoggedBy = event.addElement("loggedBy");
				// historyLoggedBy.addAttribute("userId", history.getLoggedBy().getId() + "");
				historyLoggedBy.addText(history.getLoggedBy().getName());
				// status
				if(history.getStatus() != null) {
					Element historyStatus = event.addElement("status");
					historyStatus.addAttribute("statusId", history.getStatus() + "");
					historyStatus.addText(history.getStatusValue());
				}
				// assigned to
				if(history.getAssignedTo() != null) {
					Element historyAssignedTo = event.addElement("assignedTo");
					// historyAssignedTo.addAttribute("userId", history.getAssignedTo().getId() + "");
					historyAssignedTo.addText(history.getAssignedTo().getName());
				}
				// attachment
				if(history.getAttachment() != null) {
					Element historyAttachment = event.addElement("attachment");
					historyAttachment.addAttribute("attachmentId", history.getAttachment().getId() + "");
					historyAttachment.addText(history.getAttachment().getFileName());
				}
				// comment
				if(history.getComment() != null) {
					Element historyComment = event.addElement("comment");
					historyComment.addText(history.getComment());
				}
				// timestamp
				Element historyTimestamp = event.addElement("timestamp");
				historyTimestamp.addText(DateUtils.formatTimeStamp(history.getTimeStamp()));
				// custom fields
				List<Field> editable = this.getSpace().getMetadata().getEditableFields();
				for(Field field : editable) {
					Object value = history.getValue(field.getName());
					if(value != null) {
						Element historyCustomField = event.addElement(field.getName().getText());
						historyCustomField.addAttribute("label", field.getLabel());
						if(field.isDropDownType()) {
							historyCustomField.addAttribute("optionId", value + "");
						}
						historyCustomField.addText(history.getCustomValue(field.getName()));
					}
				}
			}
		}
		return root;
	}

	public String getAsHtml(HttpServletRequest request, HttpServletResponse response,
			MessageSource ms, Locale loc) {

		boolean isWeb = request != null && response != null;

		String tableStyle = " class='jtrac'";
		String tdStyle = "";
		String thStyle = "";
		String altStyle = " class='alt'";
		String labelStyle = " class='label'";

		if (!isWeb) {
			// inline CSS so that HTML mail works across most mail-reader clients
			String tdCommonStyle = "border: 1px solid black";
			tableStyle = " class='jtrac' style='border-collapse: collapse; font-family: Arial; font-size: 75%'";
			tdStyle = " style='" + tdCommonStyle + "'";
			thStyle = " style='" + tdCommonStyle + "; background: #CCCCCC'";
			altStyle = " style='background: #e1ecfe'";
			labelStyle = " style='" + tdCommonStyle + "; background: #CCCCCC; font-weight: bold; text-align: right'";
		}

		StringBuffer sb = new StringBuffer();
		sb.append("<table width='100%'" + tableStyle + ">");
		sb.append("<tr" + altStyle + ">");
		sb.append("  <td" + labelStyle + ">" + Item.fmt("id", ms, loc) + "</td>");
		sb.append("  <td" + tdStyle + ">" + this.getRefId() + "</td>");
		sb.append("  <td" + labelStyle + ">" + Item.fmt("relatedItems", ms, loc) + "</td>");
		sb.append("  <td colspan='3'" + tdStyle + ">");
		if (this.getRelatedItems() != null || this.getRelatingItems() != null) {
			String flowUrlParam = null;
			String flowUrl = null;
			if (isWeb) {
				flowUrlParam = "_flowExecutionKey=" + request.getAttribute("flowExecutionKey");
				flowUrl = "/flow?" + flowUrlParam;
			}
			if (this.getRelatedItems() != null) {
				// ItemViewForm itemViewForm = null;
				if (isWeb) {
					// itemViewForm = (ItemViewForm) request.getAttribute("itemViewForm");
					sb.append("<input type='hidden' name='_removeRelated'/>");
				}
				for(ItemItem itemItem : this.getRelatedItems()) {
					String refId = itemItem.getRelatedItem().getRefId();
					if (isWeb) {
						String checked = "";
						//Set<Long> set = null; // itemViewForm.getRemoveRelated();
						//if (set != null && set.contains(itemItem.getId())) {
						//    checked = " checked='true'";
						//}
						String url = flowUrl + "&_eventId=viewRelated&itemId=" + itemItem.getRelatedItem().getId();
						refId = "<a href='" + response.encodeURL(request.getContextPath() + url) + "'>" + refId + "</a>"
								+ "<input type='checkbox' name='removeRelated' value='"
								+ itemItem.getId() + "' title='" + Item.fmt("remove", ms, loc) + "'" + checked + "/>";
					}
					sb.append(Item.fmt(itemItem.getRelationText(), ms, loc) + " " + refId + " ");
				}
			}
			if (this.getRelatingItems() != null) {
				for(ItemItem itemItem : this.getRelatingItems()) {
					String refId = itemItem.getItem().getRefId();
					if (isWeb) {
						String url = flowUrl + "&_eventId=viewRelated&itemId=" + itemItem.getItem().getId();
						refId = "<a href='" + response.encodeURL(request.getContextPath() + url) + "'>" + refId + "</a>";
					}
					sb.append(refId + " " + Item.fmt(itemItem.getRelationText() + "This", ms, loc) + ". ");
				}
			}
		}
		sb.append("  </td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("  <td width='15%'" + labelStyle + ">" + Item.fmt("status", ms, loc) + "</td>");
		sb.append("  <td" + tdStyle + ">" + this.getStatusValue() + "</td>");
		sb.append("  <td" + labelStyle + ">" + Item.fmt("loggedBy", ms, loc) + "</td>");
		sb.append("  <td" + tdStyle + ">" + this.getLoggedBy().getName() + "</td>");
		sb.append("  <td" + labelStyle + ">" + Item.fmt("assignedTo", ms, loc) + "</td>");
		sb.append("  <td width='15%'" + tdStyle + ">" + (this.getAssignedTo() == null ? "" : this.getAssignedTo().getName()) + "</td>");
		sb.append("</tr>");
		sb.append("<tr" + altStyle + ">");
		sb.append("  <td" + labelStyle + ">" + Item.fmt("summary", ms, loc) + "</td>");
		sb.append("  <td colspan='5'" + tdStyle + ">" + HtmlUtils.htmlEscape(this.getSummary()) + "</td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("  <td valign='top'" + labelStyle + ">" + Item.fmt("detail", ms, loc) + "</td>");
		sb.append("  <td colspan='5'" + tdStyle + ">" + Item.fixWhiteSpace(this.getDetail()) + "</td>");
		sb.append("</tr>");

		int row = 0;
		Map<Field.Name, Field> fields = this.getSpace().getMetadata().getFields();
		for(Field.Name fieldName : this.getSpace().getMetadata().getFieldOrder()) {
			Field field = fields.get(fieldName);
			sb.append("<tr" + (row % 2 == 0 ? altStyle : "") + ">");
			sb.append("  <td" + labelStyle + ">" + field.getLabel() + "</td>");
			sb.append("  <td colspan='5'" + tdStyle + ">" + this.getCustomValue(fieldName) + "</td>");
			sb.append("</tr>");
			row++;
		}
		sb.append("</table>");

		//=========================== HISTORY ==================================
		sb.append("<br/>&nbsp;<b" + tableStyle + ">" + Item.fmt("history", ms, loc) + "</b>");
		sb.append("<table width='100%'" + tableStyle + ">");
		sb.append("<tr>");
		sb.append("  <th" + thStyle + ">" + Item.fmt("loggedBy", ms, loc) + "</th><th" + thStyle + ">" + Item.fmt("status", ms, loc) + "</th>"
				+ "<th" + thStyle + ">" + Item.fmt("assignedTo", ms, loc) + "</th><th" + thStyle + ">" + Item.fmt("comment", ms, loc) + "</th><th" + thStyle + ">" + Item.fmt("timeStamp", ms, loc) + "</th>");
		List<Field> editable = this.getSpace().getMetadata().getEditableFields();
		for(Field field : editable) {
			sb.append("<th" + thStyle + ">" + field.getLabel() + "</th>");
		}
		sb.append("</tr>");

		if (this.getHistory() != null) {
			row = 1;
			for(History history : this.getHistory()) {
				sb.append("<tr valign='top'" + (row % 2 == 0 ? altStyle : "") + ">");
				sb.append("  <td" + tdStyle + ">" + history.getLoggedBy().getName() + "</td>");
				sb.append("  <td" + tdStyle + ">" + history.getStatusValue() +"</td>");
				sb.append("  <td" + tdStyle + ">" + (history.getAssignedTo() == null ? "" : history.getAssignedTo().getName()) + "</td>");
				sb.append("  <td" + tdStyle + ">");
				Attachment attachment = history.getAttachment();
				if (attachment != null) {
					if (request != null && response != null) {
						String href = response.encodeURL(request.getContextPath() + "/app/attachments/" + attachment.getFileName() +"?filePrefix=" + attachment.getFilePrefix());
						sb.append("<a target='_blank' href='" + href + "'>" + attachment.getFileName() + "</a>&nbsp;");
					} else {
						sb.append("(attachment:&nbsp;" + attachment.getFileName() + ")&nbsp;");
					}
				}
				sb.append(Item.fixWhiteSpace(history.getComment()));
				sb.append("  </td>");
				sb.append("  <td" + tdStyle + ">" + history.getTimeStamp() + "</td>");
				for(Field field : editable) {
					sb.append("<td" + tdStyle + ">" + history.getCustomValue(field.getName()) + "</td>");
				}
				sb.append("</tr>");
				row++;
			}
		}
		sb.append("</table>");
		return sb.toString();
	}

	/**
	 * does HTML escaping, converts tabs to spaces and converts leading
	 * spaces (for each multi-line) to as many '&nbsp;' sequences as required
	 */
	public static String fixWhiteSpace(String text) {
		if(text == null) {
			return "";
		}
		String temp = HtmlUtils.htmlEscape(text);
		BufferedReader reader = new BufferedReader(new StringReader(temp));
		StringBuilder sb = new StringBuilder();
		String s;
		boolean first = true;
		try {
			while((s = reader.readLine()) != null) {
				if(first) {
					first = false;
				} else {
					sb.append("<br/>");
				}
				if(s.startsWith(" ")) {
					int i;
					for(i = 0; i < s.length(); i++) {
						if(s.charAt(i) == ' ') {
							sb.append("&nbsp;");
						} else {
							break;
						}
					}
					s = s.substring(i);
				}
				sb.append(s);
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		return sb.toString().replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
	}

	public static String fmt(String key, MessageSource messageSource, Locale locale) {
		try {
			return messageSource.getMessage("item_view." + key, null, locale);
		} catch (Exception e) {
			return "???item_view." + key + "???";
		}
	}

	public boolean hasHistory() {
		return history != null && history.size() > 1;
	}

	public boolean wasLoggedBy(User user) {
		return user != null && getLoggedBy().getLoginName().equals(user.getLoginName());
	}

}
