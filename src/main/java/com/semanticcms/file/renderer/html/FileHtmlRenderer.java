/*
 * semanticcms-file-renderer-html - Files referenced in HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019  AO Industries, Inc.
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

import static com.aoindustries.encoding.JavaScriptInXhtmlAttributeEncoder.encodeJavaScriptInXhtmlAttribute;
import com.aoindustries.encoding.NewEncodingUtils;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.Path;
import com.aoindustries.net.URIEncoder;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.util.StringUtility;
import com.aoindustries.util.Tuple2;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.renderer.html.Headers;
import com.semanticcms.core.renderer.html.HtmlRenderer;
import com.semanticcms.core.renderer.html.PageIndex;
import com.semanticcms.core.resources.Resource;
import com.semanticcms.core.resources.ResourceConnection;
import com.semanticcms.core.resources.ResourceStore;
import com.semanticcms.core.servlet.ServletElementContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class FileHtmlRenderer {

	@FunctionalInterface
	public static interface FileImplBody<E extends Throwable> {
		void doBody(boolean discard) throws E, IOException, SkipPageException;
	}

	/**
	 * @param out Optional, when null meta data is verified but no output is generated
	 */
	public static void writeFileImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		com.semanticcms.file.model.File element
	) throws ServletException, IOException, SkipPageException {
		ResourceStore resourceStore;
		ResourceRef resourceRef;
		{
			Tuple2<ResourceStore, ResourceRef> resource = element.getResource();
			if(resource == null) throw new IllegalArgumentException("Resource not set on file: " + element);
			resourceStore = resource.getElement1();
			resourceRef = resource.getElement2();
		}
		BookRef bookRef = resourceRef.getBookRef();
		// Find the resource, if available
		Resource resource = resourceStore == null ? null : resourceStore.getResource(resourceRef.getPath());
		// Connect to resource
		ResourceConnection conn = resource == null ? null : resource.open();
		try {
			// Find the local file, if available
			File resourceFile;
			{
				if(conn == null || !conn.exists()) {
					resourceFile = null;
				} else {
					assert resource != null;
					try {
						resourceFile = resource.getFile();
					} catch(FileNotFoundException e) {
						// Resource removed between exists() and getFile()
						resourceFile = null;
					}
				}
			}
			// Check if is directory and filename matches required pattern for directory
			boolean isDirectory;
			if(resourceFile == null) {
				// In other book and not available, assume directory when ends in path separator
				isDirectory = resourceRef.getPath().toString().endsWith(Path.SEPARATOR_STRING);
			} else {
				// In accessible book, use attributes
				isDirectory = resourceFile.isDirectory();
				// When is a directory, must end in slash
				if(
					isDirectory
					&& !resourceRef.getPath().toString().endsWith(Path.SEPARATOR_STRING)
				) {
					throw new IllegalArgumentException(
						"References to directories must end in slash ("
						+ Path.SEPARATOR_CHAR
						+ "): "
						+ resourceRef
					);
				}
			}
			if(out != null) {
				BufferResult body = element.getBody();
				boolean hasBody = body.getLength() != 0;
				// Determine if local file opening is allowed
				final boolean isOpenFileAllowed = FileUtils.isOpenFileAllowed(servletContext, request);
				final boolean isExporting = Headers.isExporting(request);

				String elemId = element.getId();
				out.write("<a");
				if(elemId != null) {
					out.write(" id=\"");
					encodeTextInXhtmlAttribute(
						PageIndex.getRefIdInPage(request, element.getPage(), elemId),
						out
					);
					out.append('"');
				}
				if(!hasBody) {
					// TODO: Class like core:link, where providing empty class disables automatic class selection here
					String linkCssClass = HtmlRenderer.getInstance(servletContext).getLinkCssClass(element);
					if(linkCssClass != null) {
						out.write(" class=\"");
						encodeTextInXhtmlAttribute(linkCssClass, out);
						out.write('"');
					}
				}
				out.write(" href=\"");
				if(
					isOpenFileAllowed
					&& resourceFile != null
					&& !isExporting
				) {
					encodeTextInXhtmlAttribute(
						response.encodeURL(resourceFile.toURI().toASCIIString()),
						out
					);
				} else {
					final String urlPath;
					long lastModified;
					if(
						conn != null
						&& !isDirectory
						// Check for header disabling auto last modified
						&& !"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))
						&& conn.exists()
						&& (lastModified = conn.getLastModified()) != 0
					) {
						// Include last modified on file
						urlPath =
							request.getContextPath()
							+ bookRef.getPrefix()
							+ resourceRef.getPath()
							+ "?" + LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME
							+ "=" + LastModifiedServlet.encodeLastModified(lastModified)
						;
					} else {
						urlPath =
							request.getContextPath()
							+ bookRef.getPrefix()
							+ resourceRef.getPath()
						;
					}
					encodeTextInXhtmlAttribute(
						response.encodeURL(URIEncoder.encodeURI(urlPath)),
						out
					);
				}
				out.write('"');
				if(
					isOpenFileAllowed
					&& resourceFile != null
					&& !isExporting
				) {
					out.write(" onclick=\"");
					encodeJavaScriptInXhtmlAttribute("semanticcms_openfile_servlet.openFile(\"", out);
					NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(bookRef.getDomain().toString(), out);
					encodeJavaScriptInXhtmlAttribute("\", \"", out);
					NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(bookRef.getPath().toString(), out);
					encodeJavaScriptInXhtmlAttribute("\", \"", out);
					NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(resourceRef.getPath().toString(), out);
					encodeJavaScriptInXhtmlAttribute("\"); return false;", out);
					out.write('"');
				}
				out.write('>');
				if(!hasBody) {
					if(resourceFile == null) {
						String path = resourceRef.getPath().toString();
						int slashBefore;
						if(path.endsWith(Path.SEPARATOR_STRING)) {
							slashBefore = path.lastIndexOf(Path.SEPARATOR_STRING, path.length() - 2);
						} else {
							slashBefore = path.lastIndexOf(Path.SEPARATOR_STRING);
						}
						String filename = path.substring(slashBefore + 1);
						if(filename.isEmpty()) throw new IllegalArgumentException("Invalid filename for file: " + path);
						encodeTextInXhtml(filename, out);
					} else {
						encodeTextInXhtml(resourceFile.getName(), out);
						if(isDirectory) encodeTextInXhtml(Path.SEPARATOR_CHAR, out);
					}
				} else {
					body.writeTo(new NodeBodyWriter(element, out, new ServletElementContext(servletContext, request, response)));
				}
				out.write("</a>");
				long length;
				if(
					!hasBody
					&& conn != null
					&& !isDirectory
					&& conn.exists()
					&& (length = conn.getLength()) != -1
				) {
					out.write(" (");
					encodeTextInXhtml(StringUtility.getApproximateSize(length), out);
					out.write(')');
				}
			}
		} finally {
			// TODO: Close earlier?
			if(conn != null) conn.close();
		}
	}

	/**
	 * Make no instances.
	 */
	private FileHtmlRenderer() {
	}
}
