// Base64.java
//
// Copyright 2018 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.apigee.edgecallouts;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.Message;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.binary.Base64InputStream;

public class Base64 implements Execution {
    private final static String varprefix= "b64_";
    private final static byte[] linebreak = "\n".getBytes(StandardCharsets.UTF_8);
    private Map properties; // read-only
    private static final String variableReferencePatternString = "(.*?)\\{([^\\{\\} ]+?)\\}(.*?)";
    private static final Pattern variableReferencePattern = Pattern.compile(variableReferencePatternString);
    private enum Base64Action { Encode, Decode }

    public Base64 (Map properties) {
        this.properties = properties;
    }

    private static String varName(String s) { return varprefix + s;}

    private Base64Action getAction(MessageContext msgCtxt) throws Exception {
        String action = getSimpleRequiredProperty("action", msgCtxt);
        action = action.toLowerCase();
        if (action.equals("decode")) return Base64Action.Decode;
        if (action.equals("encode")) return Base64Action.Encode;
        throw new IllegalStateException("action value is unknown: (" + action + ")");
    }

    private boolean getStringOutput(MessageContext msgCtxt, boolean defaultValue) throws Exception {
        String wantStringOutput = getSimpleOptionalProperty("string-output", msgCtxt);
        if (wantStringOutput == null) return defaultValue;
        wantStringOutput = wantStringOutput.toLowerCase();
        return wantStringOutput.equals("true");
    }

    private int getLineLength(MessageContext msgCtxt, int defaultValue) throws Exception {
        String len = getSimpleOptionalProperty("line-length", msgCtxt);
        if (len == null) return defaultValue;
        int result = -1;
        try {
            result = Integer.parseInt(len);
        }
        catch (Exception e1) {/* gulp */}
        if (result<=0) return defaultValue;
        return result;
    }

    private String getMimeType(MessageContext msgCtxt) throws Exception {
        return getSimpleOptionalProperty("mime-type", msgCtxt);
    }

    private String getSimpleRequiredProperty(String propName, MessageContext msgCtxt) throws Exception {
        String value = (String) this.properties.get(propName);
        if (value == null) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        value = value.trim();
        if (value.equals("")) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        value = resolvePropertyValue(value, msgCtxt);
        if (value == null || value.equals("")) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        return value;
    }

    private String getSimpleOptionalProperty(String propName, MessageContext msgCtxt) throws Exception {
        String value = (String) this.properties.get(propName);
        if (value == null) { return null; }
        value = value.trim();
        if (value.equals("")) { return null; }
        value = resolvePropertyValue(value, msgCtxt);
        if (value == null || value.equals("")) { return null; }
        return value;
    }

    // If the value of a property contains a pair of curlies,
    // eg, {apiproxy.name}, then "resolve" the value by de-referencing
    // the context variable whose name appears between the curlies.
    private String resolvePropertyValue(String spec, MessageContext msgCtxt) {
        Matcher matcher = variableReferencePattern.matcher(spec);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
            sb.append(matcher.group(1));
            sb.append((String) msgCtxt.getVariable(matcher.group(2)));
            sb.append(matcher.group(3));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public ExecutionResult execute (final MessageContext msgCtxt,
                                    final ExecutionContext execContext) {
        try {
            Base64Action action = getAction(msgCtxt);
            InputStream content = msgCtxt.getMessage().getContentAsStream();
            int lineLength = getLineLength(msgCtxt,-1);
            InputStream is = new Base64InputStream(content, (action == Base64Action.Encode), lineLength, linebreak);
            byte[] bytes = IOUtils.toByteArray(is);
            boolean isBase64 = org.apache.commons.codec.binary.Base64.isBase64(bytes);
            if (isBase64) {
                msgCtxt.setVariable(varName("action"), action.name().toLowerCase());
                boolean wantStringDefault = (action == Base64Action.Encode);
                boolean wantString = getStringOutput(msgCtxt, wantStringDefault);
                if (wantString) {
                    msgCtxt.setVariable(varName("wantString"), wantString);
                    String encoded = StringUtils.newStringUtf8(bytes);
                    msgCtxt.setVariable(varName("result"), encoded);
                }
                else {
                    String mimeType = getMimeType(msgCtxt);
                    if (mimeType != null) {
                        msgCtxt.setVariable(varName("mimeType"), mimeType);
                    }
                    msgCtxt.setVariable(varName("result"), bytes);
                }
                return ExecutionResult.SUCCESS;
            }
            else {
                msgCtxt.setVariable(varName("error"), "not Base64");
                return ExecutionResult.ABORT;
            }
        }
        catch (Exception e) {
            //System.out.println(ExceptionUtils.getStackTrace(e));
            String error = e.toString();
            msgCtxt.setVariable(varName("exception"), error);
            int ch = error.lastIndexOf(':');
            if (ch >= 0) {
                msgCtxt.setVariable(varName("error"), error.substring(ch+2).trim());
            }
            else {
                msgCtxt.setVariable(varName("error"), error);
            }
            msgCtxt.setVariable(varName("stacktrace"), ExceptionUtils.getStackTrace(e));
            return ExecutionResult.ABORT;
        }
    }
}