/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.fejoa.library.remote2.HTMLRequest;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.fejoa.library.remote2.RemoteMessage;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class Portal extends AbstractHandler {
    final private List<JsonRequestHandler> jsonHandlers = new ArrayList<>();

    private void addJsonHandler(JsonRequestHandler handler) {
        jsonHandlers.add(handler);
    }

    public Portal() {
        addJsonHandler(new JsonCreateAccountHandler());
    }

    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse response) throws IOException, ServletException {
        response.setContentType("text/plain;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        request.setHandled(true);

        final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement(
                System.getProperty("java.io.tmpdir"));
        if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
            request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG);
        }

        Part messagePart = request.getPart(HTMLRequest.MESSAGE_KEY);
        Part data = request.getPart(HTMLRequest.DATA_KEY);

        RemoteMessage returnMessage = handleJson(messagePart.toString(), data != null ? data.getInputStream() : null);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addTextBody(HTMLRequest.MESSAGE_KEY, returnMessage.message, ContentType.DEFAULT_TEXT);
        builder.addBinaryBody(HTMLRequest.DATA_KEY, returnMessage.binaryData, ContentType.DEFAULT_BINARY,
                HTMLRequest.DATA_FILE);
        HttpEntity entity = builder.build();
        response.getOutputStream().write(entity.getContentType().toString().getBytes());
        response.getOutputStream().write('\n');
        entity.writeTo(response.getOutputStream());
    }

    private RemoteMessage handleJson(String message, InputStream data) {

        try {
            JsonRPCHandler jsonRPCHandler = new JsonRPCHandler(message);
            String method = jsonRPCHandler.getMethod();
            for (JsonRequestHandler handler : jsonHandlers) {
                if (!handler.getMethod().equals(method))
                    continue;
                RemoteMessage returnMessage = handler.handle(jsonRPCHandler, jsonRPCHandler.getParams(), data);
                if (returnMessage != null)
                    return returnMessage;
            }

            return new RemoteMessage(jsonRPCHandler.makeError(JsonRequestHandler.Errors.NO_HANDLER_FOR_REQUEST,
                    "can't handle request"));
        } catch (Exception e) {
            e.printStackTrace();
            return new RemoteMessage(JsonRPCHandler.makeError(-1, JsonRequestHandler.Errors.INVALID_JSON_REQUEST,
                    "can't parse json"));
        }
    }
}