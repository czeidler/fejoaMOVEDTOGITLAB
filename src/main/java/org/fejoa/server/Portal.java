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
import org.fejoa.library.support.StreamHelper;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class Portal extends AbstractHandler {
    public static class Errors {
        final static public int OK = 0;
        final static public int ERROR = -1;
    }

    public class ResponseHandler {
        final private HttpServletResponse response;
        private boolean handled = false;
        final private MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        // TODO don't buffer the output data but send it directly!
        private ByteArrayOutputStream outputStream;

        public ResponseHandler(HttpServletResponse response) {
            this.response = response;
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        }

        public boolean isHandled() {
            return handled;
        }

        public void setResponseHeader(String header) {
            handled = true;
            builder.addTextBody(HTMLRequest.MESSAGE_KEY, header, ContentType.DEFAULT_TEXT);
        }

        public OutputStream addData() {
            if (!handled)
                return null;
            if (outputStream == null)
                outputStream = new ByteArrayOutputStream();
            return outputStream;
        }

        public void finish() throws IOException {
            if (outputStream != null) {
                builder.addBinaryBody(HTMLRequest.DATA_KEY, new ByteArrayInputStream(outputStream.toByteArray()),
                        ContentType.DEFAULT_BINARY, HTMLRequest.DATA_FILE);
            }
            HttpEntity entity = builder.build();
            response.getOutputStream().write(entity.getContentType().toString().getBytes());
            response.getOutputStream().write('\n');
            entity.writeTo(response.getOutputStream());
        }
    }

    final private List<JsonRequestHandler> jsonHandlers = new ArrayList<>();

    private void addJsonHandler(JsonRequestHandler handler) {
        jsonHandlers.add(handler);
    }

    public Portal() {
        addJsonHandler(new JsonPingHandler());
        addJsonHandler(new GitPushHandler());
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

        ResponseHandler responseHandler = new ResponseHandler(response);

        Part messagePart = request.getPart(HTMLRequest.MESSAGE_KEY);
        Part data = request.getPart(HTMLRequest.DATA_KEY);

        if (messagePart == null) {
            responseHandler.setResponseHeader("empty request!");
            responseHandler.finish();
            return;
        }

        StringWriter stringWriter = new StringWriter();
        StreamHelper.copy(messagePart.getInputStream(), stringWriter);

        String error = handleJson(responseHandler, stringWriter.toString(),
                (data != null) ? data.getInputStream() : null);

        if (!responseHandler.isHandled() || error != null)
            responseHandler.setResponseHeader(error);

        responseHandler.finish();
    }

    private String handleJson(ResponseHandler responseHandler, String message, InputStream data) {
        JsonRPCHandler jsonRPCHandler;
        try {
            jsonRPCHandler = new JsonRPCHandler(message);
        } catch (Exception e) {
            e.printStackTrace();
            return JsonRPCHandler.makeResult(-1, JsonRequestHandler.Errors.INVALID_JSON_REQUEST,
                    "can't parse json");
        }

        String method = jsonRPCHandler.getMethod();
        for (JsonRequestHandler handler : jsonHandlers) {
            if (!handler.getMethod().equals(method))
                continue;

            try {
                handler.handle(responseHandler, jsonRPCHandler, data);
            } catch (Exception e) {
                e.printStackTrace();
                return jsonRPCHandler.makeResult(JsonRequestHandler.Errors.EXCEPTION, e.getMessage());
            }
            if (responseHandler.isHandled())
                return null;
        }

        return jsonRPCHandler.makeResult(JsonRequestHandler.Errors.NO_HANDLER_FOR_REQUEST, "can't handle request");
    }
}