package io.github.swim2sun.weixin;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.CookieManager;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Http util
 *
 * @author swim2sun
 * @version 1.0 2018-08-17.
 */
@Slf4j
public class Http {
  private OkHttpClient httpClient;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  public Http() {
    CookieManager cookieManager = new CookieManager();
    httpClient =
        new OkHttpClient.Builder()
            .cookieJar(new JavaNetCookieJar(cookieManager))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
  }

  public String get(String url, Map<String, String> params) {
    Objects.requireNonNull(params);
    if (params.size() > 0) {
      HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
      params.forEach(builder::addQueryParameter);
      HttpUrl httpUrl = builder.build();
      url = httpUrl.toString();
    }
    Request request = new Request.Builder().url(url).build();
    log.debug("GET {}", url);
    try {
      Response response = httpClient.newCall(request).execute();
      String respBody = response.body().string();
      log.trace("GET RESPONSE - {}", respBody);
      return respBody;
    } catch (IOException e) {
      throw new RuntimeException("send request error", e);
    }
  }

  public String get(String url) {
    return get(url, Collections.emptyMap());
  }

  public String postJson(String url, String json) {
    RequestBody body = RequestBody.create(JSON, json);
    Request request = new Request.Builder().url(url).post(body).build();
    log.debug("POST {} BODY {}", url, json);
    try {
      Response response = httpClient.newCall(request).execute();
      String respBody = response.body().string();
      log.trace("POST RESPONSE - {}", respBody);
      return respBody;
    } catch (IOException e) {
      throw new RuntimeException("send request error", e);
    }
  }
}
