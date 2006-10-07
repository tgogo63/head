package org.mifos.application.checklist.business;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.mifos.application.checklist.exceptions.CheckListException;
import org.mifos.application.checklist.persistence.CheckListPersistence;
import org.mifos.application.checklist.util.resources.CheckListConstants;
import org.mifos.application.master.business.SupportedLocalesEntity;
import org.mifos.framework.business.BusinessObject;
import org.mifos.framework.exceptions.PersistenceException;

public abstract class CheckListBO extends BusinessObject {

	private final Short checklistId;

	private String checklistName;

	private Short checklistStatus;

	private Set<CheckListDetailEntity> checklistDetails;

	private SupportedLocalesEntity supportedLocales;

	public CheckListBO() {
		this.checklistId = null;
		checklistDetails = new LinkedHashSet<CheckListDetailEntity>();
		
	}

	protected CheckListBO(String checkListName, Short checkListStatus,
			List<String> details, Short localeId, Short userId)
			throws CheckListException {
		setCreateDetails(userId, new Date());
		this.checklistId = null;

		if (details.size() > 0) {
			setCheckListDetails(details, localeId);
		} else {
			throw new CheckListException(
					CheckListConstants.CHECKLIST_CREATION_EXCEPTION);
		}
		if (checkListName != null) {
			this.checklistName = checkListName;
		} else {
			throw new CheckListException(
					CheckListConstants.CHECKLIST_CREATION_EXCEPTION);
		}
		this.checklistStatus = checkListStatus;
		this.supportedLocales = new SupportedLocalesEntity(localeId);
	}

	public Short getChecklistId() {
		return checklistId;
	}

	public String getChecklistName() {
		return this.checklistName;
	}

	private void setChecklistName(String checklistName) {
		this.checklistName = checklistName;
	}

	public Short getChecklistStatus() {
		return this.checklistStatus;
	}

	private void setChecklistStatus(Short checklistStatus) {
		this.checklistStatus = checklistStatus;
	}

	public Set<CheckListDetailEntity> getChecklistDetails() {
		return this.checklistDetails;

	}

	private void setChecklistDetails(
			Set<CheckListDetailEntity> checklistDetailSet) {
		this.checklistDetails = checklistDetailSet;
	}

	public SupportedLocalesEntity getSupportedLocales() {
		return this.supportedLocales;
	}

	private void setSupportedLocales(SupportedLocalesEntity supportedLocales) {
		this.supportedLocales = supportedLocales;
	}

	public void addChecklistDetail(CheckListDetailEntity checkListDetailEntity) {
		checklistDetails.add(checkListDetailEntity);
	}

	public void save() throws CheckListException {
		try {
			new CheckListPersistence().createOrUpdate(this);
		} catch (PersistenceException e) {
			throw new CheckListException(e);
		}
	}
	
	private void setCheckListDetails(List<String> details,Short locale) {
		checklistDetails = new HashSet<CheckListDetailEntity>();
		for (String detail : details) {
			CheckListDetailEntity checkListDetailEntity = new CheckListDetailEntity(
					detail, Short.valueOf("1"), this, locale);
			checklistDetails.add(checkListDetailEntity);
		}
	}

}
