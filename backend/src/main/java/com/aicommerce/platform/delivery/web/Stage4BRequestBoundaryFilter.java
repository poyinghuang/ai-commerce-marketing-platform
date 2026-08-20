package com.aicommerce.platform.delivery.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.text.Normalizer;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage4b.enabled:false}' == 'true'")
final class Stage4BRequestBoundaryFilter extends OncePerRequestFilter {
    static final int MAX_REQUEST_BYTES = 16 * 1024;
    private static final Pattern UUID_TOKEN=Pattern.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[1-5][0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}");
    private static final Pattern UUID_FIELD=Pattern.compile("\\\"[A-Za-z]*Uuid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern FORBIDDEN_KEY=Pattern.compile("\\\"[^\\\"]*(?:secret|credential|token|authorization|cookie|provider|account|actor|url|raw|schedule|targeting|placement|policy)[^\\\"]*\\\"\\s*:",Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_KEY=Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !(path.startsWith("/api/platforms/meta/") || path.startsWith("/api/platform-operations/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(request.getQueryString()!=null){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","query");return;}
        String path=request.getRequestURI();Matcher pathUuid=UUID_TOKEN.matcher(path);while(pathUuid.find())if(!pathUuid.group().equals(pathUuid.group().toLowerCase(Locale.ROOT))){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","path");return;}boolean mutationBody="POST".equals(request.getMethod())&&!path.endsWith("/retry")&&!path.endsWith("/reconcile");
        String contentType=request.getContentType();
        if((mutationBody&&!MediaType.APPLICATION_JSON_VALUE.equals(contentType))||(!mutationBody&&contentType!=null)){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","body");return;}
        byte[] body=request.getInputStream().readNBytes(MAX_REQUEST_BYTES+1);
        if(body.length>MAX_REQUEST_BYTES){reject(response,request,"PAYLOAD_TOO_LARGE",413,"Request body is too large",null);return;}
        if(!mutationBody&&!new String(body,StandardCharsets.UTF_8).isBlank()){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","body");return;}
        if(mutationBody){String json=new String(body,StandardCharsets.UTF_8);if(!Normalizer.isNormalized(json,Normalizer.Form.NFC)||FORBIDDEN_KEY.matcher(json).find()||hasDuplicateKey(json)){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","body");return;}Matcher uuid=UUID_FIELD.matcher(json);while(uuid.find())if(!canonicalUuid(uuid.group(1))){reject(response,request,"PLATFORM_REQUEST_INVALID",400,"Platform request is invalid","body");return;}}
        chain.doFilter(new CachedRequest(request,body),response);
    }

    private static boolean canonicalUuid(String value){try{return UUID_TOKEN.matcher(value).matches()&&java.util.UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException ignored){return false;}}
    private static boolean hasDuplicateKey(String json){Set<String> keys=new HashSet<>();Matcher key=JSON_KEY.matcher(json);while(key.find())if(!keys.add(key.group(1)))return true;return false;}

    private static void reject(HttpServletResponse response,HttpServletRequest request,String code,int status,String message,String field)throws IOException{
        response.setStatus(status);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId=String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        String fieldErrors=field==null?"[]":"[{\"field\":\""+field+"\",\"message\":\""+fieldMessage(field)+"\"}]";
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"requestId\":\""+requestId+"\",\"timestamp\":\""+Instant.now()+"\",\"path\":\""+request.getRequestURI()+"\",\"fieldErrors\":"+fieldErrors+"}");
    }
    private static String fieldMessage(String field){
        return switch(field){
            case "query"->"Query parameters are not allowed";
            case "path"->"Invalid path";
            case "If-Match"->"Invalid If-Match";
            case "body"->"Invalid request body";
            default->"Invalid value";
        };
    }

    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedRequest(HttpServletRequest request,byte[] body){super(request);this.body=body.clone();}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
        @Override public ServletInputStream getInputStream(){ByteArrayInputStream input=new ByteArrayInputStream(body);return new ServletInputStream(){@Override public boolean isFinished(){return input.available()==0;}@Override public boolean isReady(){return true;}@Override public void setReadListener(ReadListener listener){try{if(input.available()>0)listener.onDataAvailable();if(input.available()==0)listener.onAllDataRead();}catch(IOException e){listener.onError(e);}}@Override public int read(){return input.read();}@Override public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}};}
    }
}
