package com.dotcms.staticpublish;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import javax.servlet.http.Cookie;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.UserProxy;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.web.LanguageWebAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.cache.LiveCache;
import com.dotmarketing.cache.WorkingCache;
import com.dotmarketing.cmis.proxy.DotInvocationHandler;
import com.dotmarketing.cmis.proxy.DotRequestProxy;
import com.dotmarketing.cmis.proxy.DotResponseProxy;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.filters.ClickstreamFilter;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.fileassets.business.IFileAsset;
import com.dotmarketing.portlets.htmlpageasset.business.HTMLPageAssetAPIImpl;
import com.dotmarketing.portlets.htmlpageasset.model.HTMLPageAsset;
import com.dotmarketing.portlets.htmlpageasset.model.IHTMLPage;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Constants;
import com.dotmarketing.util.CookieUtil;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.VelocityUtil;
import com.dotmarketing.util.WebKeys;
import com.dotmarketing.velocity.VelocityServlet;
import com.liferay.portal.model.User;
import com.liferay.util.FileUtil;

public final class StaticPublishUtil {

	private static class Holder {
		private static final StaticPublishUtil INSTANCE;

		static {
			INSTANCE = new StaticPublishUtil();

		}

	}

	static StaticPublishUtil getUtil() {
		return Holder.INSTANCE;
	}

	void writePageToDisk(File root, IHTMLPage page, Contentlet urlMapped) {
		if (root == null) {
			Logger.warn(this, "URI is not set for Bundler to write");
			return;
		}


		try {
			String uri = (urlMapped != null) 
					? APILocator.getContentletAPI().getUrlMapForContentlet(urlMapped,APILocator.getUserAPI().getSystemUser(), false) 
					: page.getURI();
					
			if(uri.contains("?")){
				uri = uri.substring(0, uri.indexOf("?"));
			}
			String staticFile = root.getPath() + uri;



			File file = new File(staticFile);

			String dir = staticFile.substring(0, staticFile.lastIndexOf(File.separator));
			new File(dir).mkdirs();

			if (file.exists())
				file.delete();

			file = new File(staticFile);

			BufferedWriter out = null;
			try {
				if (!file.exists()) {
					file.createNewFile();
				}

				User sys = APILocator.getUserAPI().getSystemUser();

				String html = getHTML((HTMLPageAsset) page, true, urlMapped, sys);

				FileWriter fstream = new FileWriter(file);
				out = new BufferedWriter(fstream);
				out.write(html);
				out.close();
			} catch (Exception e) {
				Logger.error(this.getClass(), e.getMessage(), e);
				throw new WorkflowActionFailureException(e.getMessage(), e);
			} finally {
				if (out != null) {
					out.close();
				}
			}

		} catch (Exception ex) {
			throw new WorkflowActionFailureException(ex.getMessage(), ex);
		}
	}

	void writeUrlMapToDisk(File root, Contentlet con) throws IOException {
		try {
			String detailPage = con.getStructure().getDetailPage();
			IHTMLPage detail = APILocator.getHTMLPageAssetAPI().fromContentlet(
					APILocator.getContentletAPI().findContentletByIdentifier(con.getStructure().getDetailPage(), true,con.getLanguageId(), APILocator.getUserAPI().getSystemUser(), false));
			writePageToDisk(root, detail, con);
		} catch (DotDataException | DotSecurityException e) {
			throw new WorkflowActionFailureException(e.getMessage(), e);
		}
	}

	void writeFileToDisk(File root, IFileAsset file) throws IOException {
		try {
			String staticFile = root.getPath() + file.getURI();
			File newFile = new File(staticFile);
			String dir = staticFile.substring(0, staticFile.lastIndexOf(File.separator));
			new File(dir).mkdirs();

			FileUtil.copyFile(file.getFileAsset(), newFile);

		} catch (DotDataException e) {
			throw new WorkflowActionFailureException(e.getMessage(), e);
		}
	}

	String getHTML(HTMLPageAsset page, boolean liveMode, Contentlet urlMap, User user) throws DotStateException, DotDataException,
			DotSecurityException {
		/*
		 * The below code is copied from VelocityServlet.doLiveMode() and
		 * modified to parse a HTMLPage. Replaced the request and response
		 * objects with DotRequestProxy and DotResponseProxyObjects.
		 */
		Identifier id = APILocator.getIdentifierAPI().find(page.getIdentifier());
		Host host = APILocator.getHostAPI().find(id.getHostId(), APILocator.getUserAPI().getSystemUser(), false);

		InvocationHandler dotInvocationHandler = new DotInvocationHandler(new HashMap());

		DotRequestProxy requestProxy = (DotRequestProxy) Proxy.newProxyInstance(DotRequestProxy.class.getClassLoader(),
				new Class[] { DotRequestProxy.class }, dotInvocationHandler);

		DotResponseProxy responseProxy = (DotResponseProxy) Proxy.newProxyInstance(DotResponseProxy.class.getClassLoader(),
				new Class[] { DotResponseProxy.class }, dotInvocationHandler);

		StringWriter out = new StringWriter();
		Context context = null;

		// Map with all identifier inodes for a given uri.
		String idStr = id.getInode();


		long langId = page.getLanguageId();
		String uri = page.getURI();
		// Checking the path is really live using the livecache

		responseProxy.setContentType("text/html");
		requestProxy.setAttribute("User-Agent", Constants.USER_AGENT_DOTCMS_BROWSER);
		requestProxy.setAttribute("idInode", String.valueOf(idStr));

		/* Set long lived cookie regardless of who this is */
		String _dotCMSID = UtilMethods.getCookieValue(requestProxy.getCookies(), com.dotmarketing.util.WebKeys.LONG_LIVED_DOTCMS_ID_COOKIE);

		if (!UtilMethods.isSet(_dotCMSID)) {
			/* create unique generator engine */
			Cookie idCookie = CookieUtil.createCookie();
			responseProxy.addCookie(idCookie);
		}

		requestProxy.put("host", host);
		requestProxy.put("host_id", host.getIdentifier());
		requestProxy.put("uri", uri);
		requestProxy.put("user", user);
		if(urlMap!=null){
		requestProxy.setAttribute(WebKeys.WIKI_CONTENTLET, urlMap.getIdentifier());
		requestProxy.setAttribute(WebKeys.WIKI_CONTENTLET_INODE, urlMap.getInode());
		requestProxy.setAttribute(WebKeys.CLICKSTREAM_IDENTIFIER_OVERRIDE, urlMap.getIdentifier());
		requestProxy.setAttribute(WebKeys.WIKI_CONTENTLET_URL, APILocator.getContentletAPI().getUrlMapForContentlet(urlMap, user, false));
		}
		Identifier ident = APILocator.getIdentifierAPI().find(host, uri);



		IHTMLPage pageProxy = new HTMLPageAsset();
		pageProxy.setIdentifier(ident.getInode());
		try {



			requestProxy.setAttribute(WebKeys.HTMLPAGE_LANGUAGE, Long.toString(langId));

			WebAPILocator.getLanguageWebAPI().checkSessionLocale(requestProxy);

			context = VelocityUtil.getWebContext(requestProxy, responseProxy);

			context.put("language", Long.toString(langId));

			if (!liveMode) {
				requestProxy.setAttribute(WebKeys.PREVIEW_MODE_SESSION, "true");
				context.put("PREVIEW_MODE", new Boolean(true));
			} else {
				context.put("PREVIEW_MODE", new Boolean(false));
			}

			context.put("host", host);
			VelocityEngine ve = VelocityUtil.getEngine();


			requestProxy.setAttribute("velocityContext", context);

			String VELOCITY_HTMLPAGE_EXTENSION = Config.getStringProperty("VELOCITY_HTMLPAGE_EXTENSION");
			String vTempalate = (liveMode) 
					? "/live/" + idStr + "_" + langId + "." + VELOCITY_HTMLPAGE_EXTENSION 
					: "/working/" + idStr +"_" + langId + "." + VELOCITY_HTMLPAGE_EXTENSION;

			ve.getTemplate(vTempalate).merge(context, out);

		} catch (Exception e1) {
			Logger.error(this, e1.getMessage(), e1);
		} finally {
			context = null;
			VelocityServlet.velocityCtx.remove();
		}

		if (Config.getBooleanProperty("ENABLE_CLICKSTREAM_TRACKING", false)) {
			Logger.debug(HTMLPageAssetAPIImpl.class, "Into the ClickstreamFilter");
			// Ensure that clickstream is recorded only once per request.
			if (requestProxy.getAttribute(ClickstreamFilter.FILTER_APPLIED) == null) {
				requestProxy.setAttribute(ClickstreamFilter.FILTER_APPLIED, Boolean.TRUE);

				if (user != null) {
					UserProxy userProxy = null;
					try {
						userProxy = com.dotmarketing.business.APILocator.getUserProxyAPI().getUserProxy(user,
								APILocator.getUserAPI().getSystemUser(), false);
					} catch (DotRuntimeException e) {
						e.printStackTrace();
					} catch (DotSecurityException e) {
						e.printStackTrace();
					} catch (DotDataException e) {
						e.printStackTrace();
					}

				}
			}
		}

		return out.toString();
	}

}
