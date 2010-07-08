/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */

package org.mifos.reports.struts.action;

import java.io.File;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.mifos.framework.business.service.BusinessService;
import org.mifos.framework.components.logger.LoggerConstants;
import org.mifos.framework.components.logger.MifosLogManager;
import org.mifos.framework.components.logger.MifosLogger;
import org.mifos.framework.exceptions.ServiceException;
import org.mifos.framework.struts.action.BaseAction;
import org.mifos.reports.admindocuments.persistence.AdminDocumentPersistence;
import org.mifos.reports.admindocuments.struts.action.BirtAdminDocumentUploadAction;
import org.mifos.reports.business.ReportsBO;
import org.mifos.reports.business.ReportsJasperMap;
import org.mifos.reports.business.ReportsParamsMap;
import org.mifos.reports.business.dao.ReportsParamQueryDAO;
import org.mifos.reports.business.service.ReportsBusinessService;
import org.mifos.reports.persistence.ReportsPersistence;
import org.mifos.reports.struts.actionforms.ReportsUserParamsActionForm;
import org.mifos.reports.util.helpers.ReportsConstants;
import org.mifos.security.util.ActionSecurity;
import org.mifos.security.util.ReportActionSecurity;
import org.mifos.security.util.SecurityConstants;

/**
 * Control Class for Report Params
 */
public class ReportsUserParamsAction extends BaseAction {

    private final ReportsBusinessService reportsBusinessService;

    private static ReportsPersistence reportsPersistence;

    private MifosLogger logger = MifosLogManager.getLogger(LoggerConstants.ACCOUNTSLOGGER);

    public ReportsUserParamsAction() throws ServiceException {
        reportsBusinessService = new ReportsBusinessService();
        reportsPersistence = new ReportsPersistence();
    }

    @Override
    protected BusinessService getService() {
        return reportsBusinessService;
    }

    public static ActionSecurity getSecurity() {
        ReportActionSecurity security = new ReportActionSecurity("reportsUserParamsAction", "loadAddList");

        // FIXME: no associated activity exists for this constant
        security.allow("reportuserparamslist_path", SecurityConstants.ADMINISTER_REPORTPARAMS);

        for (ReportsBO report : new ReportsPersistence().getAllReports()) {
            security.allowReport(report.getReportId().intValue(), report.getActivityId());
        }

        // FIXME: no associated activity exists for this constant
        security.allow("loadAddList", SecurityConstants.ADMINISTER_REPORTPARAMS);
        security.allow("processReport", SecurityConstants.ADMINISTER_REPORTPARAMS);
        security.allow("reportsuserprocess_path", SecurityConstants.ADMINISTER_REPORTPARAMS);
        security.allow("loadAdminReport", SecurityConstants.CAN_VIEW_ADMIN_DOCUMENTS);

        return security;
    }

    /**
     * To allow loading Administrative documents
     */

    public ActionForward loadAdminReport(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        request.getSession().setAttribute("listOfAllParameters", new ReportsPersistence().getAllReportParams());

        String strReportId = request.getParameter("admindocId");
        String account_id = request.getParameter("globalAccountNum");
        if (strReportId == null || strReportId.equals("")) {
            strReportId = "0";
        }
        int reportId = Integer.parseInt(strReportId);
        String reportName = new AdminDocumentPersistence().getAdminDocumentById((short) reportId)
                .getAdminDocumentName();
        String filename = new AdminDocumentPersistence().getAdminDocumentById((short) reportId)
                .getAdminDocumentIdentifier();
        File file = new File(BirtAdminDocumentUploadAction.getAdminDocumentStorageDirectory(), filename);

        if (file.exists()) {
            filename = file.getAbsolutePath();
        }
        else {
            filename = "adminReport/" + filename;
        }
        if (filename.endsWith(".rptdesign")) {
            request.setAttribute("reportFile", filename);
            request.setAttribute("reportName", reportName);
            request.setAttribute("account_id", account_id);
            return mapping.findForward(ReportsConstants.ADMINDOCBIRTREPORTPATH);
        }
        return mapping.findForward(ReportsConstants.ADMINDOCBIRTREPORTPATH);
    }

    /**
     * Loads the Parameter Add page
     */
    public ActionForward loadAddList(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        logger.debug("In ReportsUserParamsAction:load Method: ");
        request.getSession().setAttribute("listOfAllParameters", new ReportsPersistence().getAllReportParams());
        ReportsParamQueryDAO paramDAO = new ReportsParamQueryDAO();
        ReportsUserParamsActionForm actionForm = (ReportsUserParamsActionForm) form;
        String strReportId = request.getParameter("reportId");
        if (strReportId == null) {
            strReportId = actionForm.getReportId() + "";
        }
        if (strReportId.equals("")) {
            strReportId = "0";
        }
        int reportId = Integer.parseInt(strReportId);
        String reportName = reportsPersistence.getReport((short) reportId).getReportName();

        List<ReportsJasperMap> reports = reportsPersistence.findJasperOfReportId(reportId);
        if (reports.size() > 0) {
            ReportsJasperMap reportFile = reports.get(0);
            String filename = reportFile.getReportJasper();
            File file = new File(BirtReportsUploadAction.getCustomReportStorageDirectory(), filename);

            if (file.exists()) {
                filename = file.getAbsolutePath();
            }
            else {
                filename = "report/" + filename;
            }
            if (filename.endsWith(".rptdesign")) {
                request.setAttribute("reportFile", filename);
                request.setAttribute("reportName", reportName);
                return mapping.findForward(ReportsConstants.BIRTREPORTPATH);
            }
        }

        actionForm.setReportId(reportId);
        request.getSession().setAttribute("listOfAllParametersForReportId",
                reportsPersistence.findParamsOfReportId(reportId));
        request.getSession().setAttribute("listOfReportJasper", reportsPersistence.findJasperOfReportId(reportId));

        List<ReportsParamsMap> reportParams = (List) request.getSession()
                .getAttribute("listOfAllParametersForReportId");
        Object[] obj = reportParams.toArray();
        if (obj != null && obj.length > 0) {

            for (int i = 0; i < obj.length; i++) {
                ReportsParamsMap rp = (ReportsParamsMap) obj[i];
                if (rp.getReportsParams().getType().equalsIgnoreCase("Query")) {
                    request.getSession().setAttribute("para" + (i + 1),
                            paramDAO.listValuesOfParameters(rp.getReportsParams()));
                }
            }
        }

        return mapping.findForward(ReportsConstants.ADDLISTREPORTSUSERPARAMS);
    }

    /**
     * Generate report in given export format
     */
    public ActionForward processReport(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        logger.debug("In ReportsUserParamsAction:processReport Method: ");
        ReportsUserParamsActionForm actionForm = (ReportsUserParamsActionForm) form;
        int reportId = actionForm.getReportId();
        String applPath = actionForm.getApplPath();
        String expType = actionForm.getExpFormat();
        String expFilename = reportsBusinessService.runReport(reportId, request, applPath, expType);
        request.getSession().setAttribute("expFileName", expFilename);
        actionForm.setExpFileName(expFilename);
        String forward = "";
        String error = (String) request.getSession().getAttribute("paramerror");
        if (error == null || error.equals("")) {
            forward = ReportsConstants.PROCESSREPORTSUSERPARAMS;
        } else {
            forward = ReportsConstants.ADDLISTREPORTSUSERPARAMS;
        }
        return mapping.findForward(forward);
    }
}
