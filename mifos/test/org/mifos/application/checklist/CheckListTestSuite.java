package org.mifos.application.checklist;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.mifos.application.checklist.business.TestCheckListBO;
import org.mifos.application.checklist.business.service.TestCheckListBusinessService;
import org.mifos.application.checklist.persistence.TestCheckListPersistence;
import org.mifos.framework.MifosTestSuite;

public class CheckListTestSuite extends MifosTestSuite {

	public static void main(String[] args) {
		Test testSuite = suite();
		TestRunner.run(testSuite);
	}

	public static Test suite() {
		TestSuite testSuite = new CheckListTestSuite();
		testSuite.addTestSuite(TestCheckListBO.class);
		testSuite.addTestSuite(TestCheckListBusinessService.class);
		testSuite.addTestSuite(TestCheckListPersistence.class);
		return testSuite;
	}
}
