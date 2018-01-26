package com.butchjgo.uploader;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by butchjgo on 7/16/17.
 */
public class FshareUploader implements Uploader {
    private final int TIMEOUT = 3600;
    private final String LOGIN_URL = "https://www.fshare.vn/login";
    private final String HOME_URL = "https://www.fshare.vn/home";
    private final String UPLOAD_URL = "https://www.fshare.vn/api/session/upload";
    private final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:54.0) Gecko/20100101 Firefox/54.0";

    private BiFunction<String, CookieManager, BasicCookieStore> cookieStorageFactory = (url, cookieManager) -> {
        BasicCookieStore basicCookieStore = new BasicCookieStore();
        cookieManager.getCookieStore().getCookies().forEach(c -> {
            BasicClientCookie cookie = new BasicClientCookie(c.getName(), c.getValue());
            cookie.setDomain(url);
            cookie.setPath("/");
            basicCookieStore.addCookie(cookie);
        });
        return basicCookieStore;
    };
    private Function<CookieStore, CloseableHttpClient> httpClientFunction = (cookieStore -> {
        RequestConfig config = RequestConfig.custom().
                setConnectTimeout(TIMEOUT * 1000).
                setConnectionRequestTimeout(TIMEOUT * 1000).
                setSocketTimeout(TIMEOUT * 1000).build();

        Header userAgent = new BasicHeader(HttpHeaders.USER_AGENT, USER_AGENT);

        List<Header> headers = Arrays.asList(userAgent);

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(config).setDefaultHeaders(headers).setDefaultCookieStore(cookieStore).build();
    });


    private final BasicCookieStore BASIC_COOKIESTORAGE = new BasicCookieStore();
    private final CloseableHttpClient HTTP_CLIENT = httpClientFunction.apply(BASIC_COOKIESTORAGE);

    private String username;
    private String password;
    private CookieManager cookieManager = new CookieManager();
    private HttpURLConnection httpURLConnection;

    private String grantedUploadUrl = null;

    private String originResult;

    private void clientSetting() {
        if (httpURLConnection == null) return;
        httpURLConnection.setRequestProperty("User-Agent", USER_AGENT);
    }

    @Override
    public boolean doUpload(String filePath) throws IOException {
        boolean success = false;
        File file = new File(filePath);
        String output = null;
        long fileSize = file.length();

        InputStreamEntity streamEntity = new InputStreamEntity(new FileInputStream(file), fileSize, ContentType.APPLICATION_OCTET_STREAM);

        HttpPost httpPost = new HttpPost(grantedUploadUrl);
        httpPost.setEntity(streamEntity);
        try {
            CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                success = true;
                originResult = EntityUtils.toString(response.getEntity());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            grantedUploadUrl = null;
            return success;
        }
    }

    @Override
    public String getOriginResult() {
        return this.originResult;
    }

    @Override
    public boolean requestUpload(String fileName) {
        boolean success = false;
        File file = new File(fileName);
        String token = null;
        while (token == null) {
            token = getDataToken();
        }

        String url = null;
        long size = file.length();
        String name = file.getName();
        String sessionID = getSessionID();

        if (sessionID == null || file == null) return success;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("SESSID", sessionID);
        jsonObject.put("name", name);
        jsonObject.put("size", String.valueOf(size));
        jsonObject.put("path", "/");
        jsonObject.put("token", token);
        jsonObject.put("secured", 0);
        String postData = jsonObject.toString();
        try {
            URL uploadURL = new URL(UPLOAD_URL);
            httpURLConnection = (HttpURLConnection) uploadURL.openConnection();
            clientSetting();
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            httpURLConnection.setDoOutput(true);
            OutputStream os = httpURLConnection.getOutputStream();
            os.write(postData.getBytes());
            os.flush();
            String output = IOUtils.toString(httpURLConnection.getInputStream());
            grantedUploadUrl = (new JSONObject(output)).getString("location");
            success = true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return success;
        }

    }

    private String getSessionID() {
        String sessionID = null;
        for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if (c.getName().equals("session_id")) {
                sessionID = c.getValue();
                break;
            }
        }
        return sessionID;
    }

    @Override
    public boolean doLogin() {
        boolean success = false;
        String token = getLoginToken();
        if (token == null || token.trim().isEmpty()) return success;
        try {
            String postData = "fs_csrf=" +
                    token +
                    "&LoginForm%5Bemail%5D=" +
                    URLEncoder.encode(username, "UTF-8") +
                    "&LoginForm%5Bpassword%5D=" +
                    URLEncoder.encode(password, "UTF-8") +
                    "&LoginForm%5Bcheckloginpopup%5D=0&LoginForm%5BrememberMe%5D=0&LoginForm%5BrememberMe%5D=1&yt0=%C4%90%C4%83ng+nh%E1%BA%ADp";
            URL loginURL = new URL(LOGIN_URL);
            httpURLConnection = (HttpURLConnection) loginURL.openConnection();
            clientSetting();
            httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpURLConnection.setDoOutput(true);

            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(postData.getBytes());
            outputStream.flush();
            outputStream.close();

            String output = IOUtils.toString(httpURLConnection.getInputStream());

            if (output.contains(username)) success = true;

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return success;
        }
    }

    private String getDataToken() {
        String token = null;
        try {
            URL homeURL = new URL(HOME_URL);
            httpURLConnection = (HttpURLConnection) homeURL.openConnection();
            String html = IOUtils.toString(httpURLConnection.getInputStream());
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select("div.breadscum");
            Element element = elements.first();
            token = element.attr("data-token");
        } finally {
            return token;
        }
    }

    private String getLoginToken() {
        String token = null;
        try {
            URL loginURL = new URL(LOGIN_URL);

            httpURLConnection = (HttpURLConnection) loginURL.openConnection();
            clientSetting();

            String html = IOUtils.toString(httpURLConnection.getInputStream());
            Document doc = Jsoup.parseBodyFragment(html);
            Elements elements = doc.select("input[name=fs_csrf]");
            Element element = elements.first();
            token = element.attr("value");
        } finally {
            return token;
        }
    }

    public FshareUploader(String username, String password) {
        this.username = username;
        this.password = password;
        CookieHandler.setDefault(cookieManager);
    }
}
