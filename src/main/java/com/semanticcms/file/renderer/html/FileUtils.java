/*
 * semanticcms-file-renderer-html - Files referenced in HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-file-renderer-html.
 *
 * semanticcms-file-renderer-html is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-file-renderer-html is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-file-renderer-html.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.file.renderer.html;

import com.semanticcms.core.controller.CapturePage;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.file.model.File;
import com.semanticcms.openfile.servlet.OpenFile;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class FileUtils {

	private static final Logger logger = Logger.getLogger(FileUtils.class.getName());

	private static class IsOpenFileAllowedLock {}
	private static final IsOpenFileAllowedLock isOpenFileAllowedLock = new IsOpenFileAllowedLock();
	private static boolean openFileNotFound;

	/**
	 * Determines if local file opening is allowed.
	 *
	 * Uses reflection to avoid hard dependency on semanticcms-openfile-servlet.
	 *
	 * @see  OpenFile#isAllowed(javax.servlet.ServletContext, javax.servlet.ServletRequest)
	 */
	public static boolean isOpenFileAllowed(ServletContext servletContext, ServletRequest request) throws ServletException {
		synchronized(isOpenFileAllowedLock) {
			// If failed once, fail quickly the second time
			if(openFileNotFound) return false;
			try {
				Class<?> openFileClass = Class.forName("com.semanticcms.openfile.servlet.OpenFile");
				Method isAllowedMethod = openFileClass.getMethod("isAllowed", ServletContext.class, ServletRequest.class);
				return (Boolean)isAllowedMethod.invoke(null, servletContext, request);
			} catch(ClassNotFoundException e) {
				logger.warning("Unable to open local files, if desktop integration is desired, add the semanticcms-openfile-servlet package.");
				openFileNotFound = true;
				return false;
			} catch(ReflectiveOperationException e) {
				throw new ServletException(e);
			}
		}
	}

	public static boolean hasFile(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		final boolean recursive
	) throws ServletException, IOException {
		final SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		return CapturePage.traversePagesAnyOrder(
			servletContext,
			request,
			response,
			page,
			CaptureLevel.META,
			(Page p) -> {
				for(Element e : p.getElements()) {
					if((e instanceof File) && !((File)e).isHidden()) {
						return true;
					}
				}
				return null;
			},
			(Page p) -> recursive ? p.getChildRefs() : null,
			// Child is in accessible book
			(PageRef childPage) -> semanticCMS.getBook(childPage.getBookRef()).isAccessible()
		) != null;
	}

	/**
	 * Make no instances.
	 */
	private FileUtils() {
	}
}
