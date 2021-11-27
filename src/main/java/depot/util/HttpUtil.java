package depot.util;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpUtil {
    private static final Logger LOGGER = Logger.getLogger("HttpUtil");
    private static final BasicCookieStore COOKIE_STORE = new BasicCookieStore();
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(5, TimeUnit.SECONDS)
            .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
            .setResponseTimeout(5, TimeUnit.SECONDS)
            .build();
    private static final Header USER_AGENT_HEADER = new BasicHeader("user-agent", "pan.baidu.com");

    public static void clearCookie() {
        COOKIE_STORE.clear();
    }

    private static CloseableHttpClient buildHttpClient() {
        return HttpClients.custom()
                .setDefaultCookieStore(COOKIE_STORE)
                .build();
    }

    private static HttpGet buildGet(URI uri) {
        HttpGet httpGet = new HttpGet(uri);
        httpGet.setConfig(REQUEST_CONFIG);
        httpGet.setHeader(USER_AGENT_HEADER);
        return httpGet;
    }

    private static HttpPost buildPost(URI uri, Map<String, String> data) {
        HttpPost httpPost = new HttpPost(uri);
        httpPost.setConfig(REQUEST_CONFIG);
        httpPost.setHeader(USER_AGENT_HEADER);
        List<NameValuePair> dataList = data.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        httpPost.setEntity(new UrlEncodedFormEntity(dataList));
        return httpPost;
    }

    private static CloseableHttpResponse execute(CloseableHttpClient httpClient, HttpUriRequestBase request)
            throws IOException, ResponseFailedException {
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            LOGGER.info(request.getUri() + ": " + response.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (response.getCode() != HttpStatus.SC_SUCCESS) {
            throw new ResponseFailedException(response.toString());
        }
        return response;
    }

    public static class ResponseFailedException extends Exception {
        ResponseFailedException(String message) {
            super(message);
        }
    }

    public static String getAsString(URI uri)
            throws IOException, ResponseFailedException, ParseException {
        try (CloseableHttpClient httpClient = buildHttpClient();
             CloseableHttpResponse response = execute(httpClient, buildGet(uri))) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    public static String postAsString(URI uri, Map<String, String> data)
            throws IOException, ResponseFailedException, ParseException {
        try (CloseableHttpClient httpClient = buildHttpClient();
             CloseableHttpResponse response = execute(httpClient, buildPost(uri, data))) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    public static CloseableHttpResponse getAsResponse(URI uri)
            throws IOException, ResponseFailedException {
        return execute(buildHttpClient(), buildGet(uri));
    }

    public static CloseableHttpResponse postAsResponse(URI uri, Map<String, String> data)
            throws IOException, ResponseFailedException {
        return execute(buildHttpClient(), buildPost(uri, data));
    }

    public static InputStream getAsInputStream(URI uri)
            throws IOException, ResponseFailedException {
        return getAsResponse(uri).getEntity().getContent();
    }

    public static InputStream postAsInputStream(URI uri, Map<String, String> data)
            throws IOException, ResponseFailedException {
        return postAsResponse(uri, data).getEntity().getContent();
    }

    public static JSONObject getAsJsonObject(URI uri)
            throws IOException, ResponseFailedException, ParseException {
        return new JSONObject(getAsString(uri));
    }

    public static JSONObject postAsJsonObject(URI uri, Map<String, String> data)
            throws IOException, ResponseFailedException, ParseException {
        return new JSONObject(postAsString(uri, data));
    }
}
