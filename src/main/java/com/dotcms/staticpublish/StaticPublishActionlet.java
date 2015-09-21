package com.dotcms.staticpublish;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.htmlpageasset.model.IHTMLPage;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.UtilMethods;

public class StaticPublishActionlet extends WorkFlowActionlet {

	private static final long serialVersionUID = 1L;

	@Override
	public List<WorkflowActionletParameter> getParameters() {
		List<WorkflowActionletParameter> params = new ArrayList<WorkflowActionletParameter>();

		return params;
	}

	@Override
	public String getName() {
		return "Static Publish Content";
	}

	@Override
	public String getHowTo() {
		return "Use the plugin properties to set the PATH to publish, currently : "
				+ StaticPublishPluginProperties.getProperty("STATIC_PUBLISH_FOLDER");
	}

	@Override
	public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
			throws WorkflowActionFailureException {

		Host host;

		try {
			host = APILocator.getHostAPI().find(processor.getContentlet().getHost(), processor.getUser(), false);

			String folder = StaticPublishPluginProperties.getProperty("STATIC_PUBLISH_FOLDER");
			if (!UtilMethods.isSet(folder)) {
				throw new WorkflowActionFailureException("Static Publish folder not set");
			}
			
			String x = Config.CONTEXT.getRealPath("/");
			if(!folder.startsWith(x) && ! folder.contains("-INF")){
				throw new WorkflowActionFailureException("Static Publishing can only happen to a folder under the path: " + x + " and cannot be to WEB-INF");
			}
			
			
			Language l = APILocator.getLanguageAPI().getLanguage(processor.getContentlet().getLanguageId());
			String langauge = l.getLanguageCode().toLowerCase();
			folder = folder.replace("$langauge", langauge);

			String hostname = host.getHostname().toLowerCase().replace(File.separator, "-");

			folder = folder.replace("$hostname", hostname);
			if (folder.endsWith(File.separator)) {
				folder = folder.substring(0, folder.length());
			}

			File root = new File(folder);
			root.mkdirs();
			Structure struc = processor.getContentlet().getStructure();
			int type = struc.getStructureType();
			// content
			if (type == 1 && UtilMethods.isSet(struc.getDetailPage())) {


				StaticPublishUtil.getUtil().writeUrlMapToDisk(root, processor.getContentlet());

			}
			// A File
			else if (type == 4) {
				StaticPublishUtil.getUtil().writeFileToDisk(root, APILocator.getFileAssetAPI().fromContentlet(processor.getContentlet()));
			}
			// an HTML Page
			else if (type == 5) {
				
				IHTMLPage page = APILocator.getHTMLPageAssetAPI().fromContentlet(processor.getContentlet());
				StaticPublishUtil.getUtil().writePageToDisk(root,
						page, null);
			}
		} catch (Exception e) {
			throw new WorkflowActionFailureException(e.getMessage(), e);
		}

	}

}