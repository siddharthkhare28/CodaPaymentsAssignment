package com.codapayments.roundRobin.model;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

public class ProxyRequest<T> {
    private final String path;
    private final HttpMethod method;
    private final HttpHeaders headers;
    private final Map<String, String> queryParams;
    private final T body;

    public ProxyRequest(String path, HttpMethod method, Map<String, String> headers, 
                       Map<String, String> queryParams, T body) {
        this.path = path;
        this.method = method;
        this.headers = new HttpHeaders();
        if (headers != null) {
            headers.forEach(this.headers::add);
        }
        this.queryParams = queryParams;
        this.body = body;
    }

    public String getPath() {
        return path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public T getBody() {
        return body;
    }
}