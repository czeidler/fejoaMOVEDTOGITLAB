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
import org.fejoa.library.remote.HTMLRequest;
import org.fejoa.library.remote.JsonRPCHandler;
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
        final static public int FOLLOW_UP_JOB = 1;

        final static public int DONE = 0;
        final static public int OK = 0;
        final static public int ERROR = -1;
        final static public int EXCEPTION = -2;

        // json handler
        final static public int NO_HANDLER_FOR_REQUEST = -10;
        final static public int INVALID_JSON_REQUEST = -11;

        // access
        final static public int ACCESS_DENIED = -20;

        // migration
        final static public int MIGRATION_ALREADY_STARTED = -30;
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

    final private String baseDir;
    final private List<JsonRequestHandler> jsonHandlers = new ArrayList<>();

    private void addJsonHandler(JsonRequestHandler handler) {
        jsonHandlers.add(handler);
    }

    public Portal(String baseDir) {
        this.baseDir = baseDir;

        addJsonHandler(new JsonPingHandler());
        addJsonHandler(new WatchHandler());
        addJsonHandler(new GitPushHandler());
        addJsonHandler(new GitPullHandler());
        addJsonHandler(new CreateAccountHandler());
        addJsonHandler(new RootLoginRequestHandler());
        addJsonHandler(new CommandHandler());
        addJsonHandler(new AccessRequestHandler());
        addJsonHandler(new StartMigrationHandler());
        addJsonHandler(new RemotePullHandler());
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

        Session session = new Session(baseDir, httpServletRequest.getSession());
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
                (data != null) ? data.getInputStream() : null, session);

        if (!responseHandler.isHandled() || error != null)
            responseHandler.setResponseHeader(error);

        responseHandler.finish();
    }

    private String handleJson(ResponseHandler responseHandler, String message, InputStream data, Session session) {
        JsonRPCHandler jsonRPCHandler;
        try {
            jsonRPCHandler = new JsonRPCHandler(message);
        } catch (Exception e) {
            e.printStackTrace();
            return JsonRPCHandler.makeResult(-1, Errors.INVALID_JSON_REQUEST,
                    "can't parse json");
        }

        String method = jsonRPCHandler.getMethod();
        for (JsonRequestHandler handler : jsonHandlers) {
            if (!handler.getMethod().equals(method))
                continue;

            try {
                handler.handle(responseHandler, jsonRPCHandler, data, session);
            } catch (Exception e) {
                e.printStackTrace();
                return jsonRPCHandler.makeResult(Errors.EXCEPTION, e.getMessage());
            }
            if (responseHandler.isHandled())
                return null;
        }

        return jsonRPCHandler.makeResult(Errors.NO_HANDLER_FOR_REQUEST, "can't handle request");
    }
}