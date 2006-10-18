package org.mifos.framework.components.fieldConfiguration.persistence.service;

import java.util.List;

import org.mifos.framework.MifosTestCase;
import org.mifos.framework.components.fieldConfiguration.business.EntityMaster;
import org.mifos.framework.components.fieldConfiguration.business.FieldConfigurationEntity;
import org.mifos.framework.exceptions.PersistenceException;

public class TestFieldConfigurationPersistenceService extends MifosTestCase{
	
	private static FieldConfigurationPersistenceService fieldConfigurationPersistenceService = new FieldConfigurationPersistenceService();

	public void testGetEntityMasterList() throws PersistenceException{
		List<EntityMaster> entityMasterList = fieldConfigurationPersistenceService.getEntityMasterList();
		assertEquals(entityMasterList.size(),23);
	}
	
	public void testGetListOfFields() throws NumberFormatException, PersistenceException{
		List<FieldConfigurationEntity> fieldList=fieldConfigurationPersistenceService.getListOfFields(Short.valueOf("22"));
		assertEquals(fieldList.size(),5);
	}

}
