package io.github.swim2sun.weixin;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.swim2sun.weixin.Preconditions.checkState;

/**
 * {@code io.github.swim2sun.weixin.Weixin} Entity which hold one weixin account, manage account
 * status
 *
 * @author swim2sun
 * @version 1.0 2018-08-17.
 */
@Slf4j
public class Weixin implements AutoCloseable {
  private String deviceId;
  private String passTicket;
  private String skey;
  private String sid;
  private String uin;
  private String syncKeyStr;
  private JSONObject syncKey;
  @Getter private User user;
  private List<User> contactList;
  private Http http;
  private ExecutorService executorService;
  private Set<WeixinMsgListener> listeners;
  private AtomicInteger errorTimes;

  // todo online method

  private Weixin() {
    deviceId = generateDeviceId();
    http = new Http();
    listeners = new HashSet<>();
    executorService = Executors.newCachedThreadPool();
    errorTimes = new AtomicInteger(0);
  }

  /**
   * create one io.github.swim2sun.weixin.Weixin instance
   *
   * @return new io.github.swim2sun.weixin.Weixin instance
   */
  public static Weixin create() {
    return new Weixin();
  }

  /** close resources */
  @Override
  public void close() {
    executorService.shutdown();
  }

  /**
   * request login QrCode image
   *
   * @param callback login result callback function, first argument means whether login succeed,
   *     second argument is result message
   * @return qrCode image url
   */
  public String loginQrcode(BiConsumer<Boolean, String> callback) {
    String url = "https://login.weixin.qq.com/jslogin";
    Map<String, String> params = new HashMap<>();
    params.put("appid", "wx782c26e4c19acffb");
    params.put("fun", "new");
    params.put("lang", "zh_CN");
    params.put("_", "" + System.currentTimeMillis());
    String body = http.get(url, params);
    // body sample:  window.QRLogin.code = 200; window.QRLogin.uuid = "4fiTRaMOsw==";
    String[] result = body.split(";");
    checkState(result.length == 2, "unknown result format: " + body);
    checkState(result[0].endsWith("200"), "result code error: " + result[0]);
    Pattern pattern = Pattern.compile("\"(.+?)\"");
    Matcher matcher = pattern.matcher(result[1]);
    Preconditions.checkArgument(matcher.find(), "can't found uuid: " + result[1]);
    String uuid = matcher.group(1);
    executorService.execute(
        () -> {
          String code;
          do {
            code = queryLoginResult(uuid);
          } while (code.equals("408") || code.equals("201"));
          boolean succeed = "200".equals(code);
          callback.accept(succeed, succeed ? "success" : "fail");
        });
    return "https://login.weixin.qq.com/qrcode/" + uuid;
  }

  /**
   * get contact list of current account
   *
   * @return contact list
   */
  public List<User> getContactList() {
    if (Objects.isNull(contactList)) {
      String url =
          String.format(
              "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?lang=zh_CN&pass_ticket=%s&r=%s&seq=0&skey=%s",
              passTicket, System.currentTimeMillis(), skey);
      String respBody = http.get(url);
      JSONObject resp = new JSONObject(respBody);
      checkState(resp.getJSONObject("BaseResponse").getInt("Ret") == 0, "ret is not equals to 0");
      setContactList(resp.getJSONArray("MemberList"));
      log.debug(
          "contact:\n {}",
          contactList.stream().map(User::getNickName).collect(Collectors.joining("\n")));
    }
    return contactList;
  }

  /**
   * find user by username
   *
   * @param username weixin username
   * @return user instance
   */
  User findUserByUsername(String username) {
    if (user.getUserName().equals(username)) {
      return user;
    }
    return contactList
        .stream()
        .filter(u -> u.getUserName().equals(username))
        .findAny()
        .orElse(null);
  }

  /**
   * find user by user nick name
   *
   * @param nickName user's nick name
   * @return user instance
   */
  public User findUserByNickname(String nickName) {
    return contactList
        .stream()
        .filter(u -> u.getNickName().equals(nickName))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("can't find user: " + nickName));
  }

  /**
   * send weixin message
   *
   * @param msg weixin message
   */
  public void sendMsg(Message msg) {
    String url =
        "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?lang=zh_CN&pass_ticket=" + passTicket;
    JSONObject reqBody =
        new JSONObject()
            .put("BaseRequest", getBaseRequest())
            .put("Msg", msg.toJson(this))
            .put("Scene", "0");
    String resp = http.postJson(url, reqBody.toString());
    JSONObject respBody = new JSONObject(resp);
    checkState(respBody.getJSONObject("BaseResponse").getInt("Ret") == 0, "ret != 0");
  }

  /**
   * add weixin message listener, system will notify listener on receive message
   *
   * @param msgListener weixin message listener
   */
  public void addMsgListener(WeixinMsgListener msgListener) {
    listeners.add(msgListener);
  }

  /**
   * query login result
   *
   * @param uuid login uuid
   * @return 408 - timeout, 201 - scanned, 200 - login succeed
   */
  private String queryLoginResult(String uuid) {
    Map<String, String> params = new HashMap<>();
    params.put("uuid", uuid);
    params.put("tip", "0");
    params.put("_", "" + System.currentTimeMillis());
    String resp = http.get("https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login", params);
    Pattern pattern = Pattern.compile("window.code=(.+?);");
    Matcher matcher = pattern.matcher(resp);
    checkState(matcher.find(), "unknown response: " + resp);
    String code = matcher.group(1);
    if ("200".equals(code)) {
      Matcher m = Pattern.compile("ticket=(.+?)&uuid=(.+?)&lang=zh_CN&scan=(.+?)").matcher(resp);
      Preconditions.checkArgument(m.find(), "can't find ticket: " + resp);
      String ticket = m.group(1);
      uuid = m.group(2);
      String scan = m.group(3);
      newLoginPage(ticket, uuid, scan);
    }
    return code;
  }

  private void newLoginPage(String ticket, String uuid, String scan) {
    Map<String, String> params = new HashMap<>();
    params.put("ticket", ticket);
    params.put("uuid", uuid);
    params.put("scan", scan);
    params.put("fun", "new");
    params.put("version", "v2");
    params.put("lang", "zh_CN");
    String resp = http.get("https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage", params);
    String ret = XmlUtil.get(resp, "ret");
    checkState("0".equals(ret), "ret not equals '0' : " + resp);
    this.skey = XmlUtil.get(resp, "skey");
    this.sid = XmlUtil.get(resp, "wxsid");
    this.uin = XmlUtil.get(resp, "wxuin");
    this.passTicket = XmlUtil.get(resp, "pass_ticket");
    init();
  }

  private void init() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?pass_ticket=%s&skey=%s&r=%s",
            passTicket, skey, System.currentTimeMillis());
    JSONObject baseRequest = getBaseRequest();
    JSONObject body = new JSONObject().put("BaseRequest", baseRequest);
    String respBody = http.postJson(url, body.toString());
    log.trace("weixin init response: {}", respBody);
    JSONObject resp = new JSONObject(respBody);
    checkState(resp.getJSONObject("BaseResponse").getInt("Ret") == 0, "ret not equals to 0");
    checkState(resp.getString("SKey").equals(skey), "skey changed");
    this.user = User.parse(resp.getJSONObject("User"));
    log.debug("user info: {}", user);
    updateSyncKey(resp.getJSONObject("SyncKey"));
    statusNotify();
  }

  private JSONObject getBaseRequest() {
    return new JSONObject()
        .put("Uin", uin)
        .put("Sid", sid)
        .put("Skey", skey)
        .put("DeviceID", deviceId);
  }

  private void statusNotify() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=%s",
            passTicket);
    JSONObject reqBody =
        new JSONObject()
            .put("BaseRequest", getBaseRequest())
            .put("Code", 3)
            .put("FromUserName", user.getUserName())
            .put("ToUserName", user.getUserName())
            .put("ClientMsgId", System.currentTimeMillis());
    String respBody = http.postJson(url, reqBody.toString());
    checkState(
        new JSONObject(respBody).getJSONObject("BaseResponse").getInt("Ret") == 0, "ret != 0");
    getContactList();
    executorService.execute(this::syncCheck);
  }

  private void setContactList(JSONArray jsonArray) {
    int len = jsonArray.length();
    this.contactList = new ArrayList<>(len);
    for (int i = 0; i < len; i++) {
      contactList.add(User.parse(jsonArray.getJSONObject(i)));
    }
    log.info("find {} user in contact", len);
  }

  private void syncCheck() {
    String url = "https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck";
    Pattern pattern = Pattern.compile("\\{retcode:\"(.+?)\",selector:\"(.+?)\"\\}");
    try {
      while (true) {
        Map<String, String> params = new HashMap<>();
        params.put("r", "" + System.currentTimeMillis());
        params.put("skey", skey);
        params.put("sid", sid);
        params.put("uin", uin);
        params.put("deviceid", deviceId);
        params.put("synckey", syncKeyStr);
        params.put("_", "" + System.currentTimeMillis());
        String resp = http.get(url, params);
        Matcher matcher = pattern.matcher(resp);
        checkState(matcher.find(), "can't find ret code: " + resp);
        String retCode = matcher.group(1);
        String selector = matcher.group(2);
        if (retCode.equals("1100")) {
          log.info("已下线");
          break;
        }
        checkState(retCode.equals("0"), "ret code not valid: " + retCode);
        if (selector.equals("2")) {
          sync();
        }
        if (errorTimes.intValue() > 0) {
          errorTimes.set(0);
        }
      }
    } catch (Exception e) {
      log.error("synce check error", e);
      if (errorTimes.intValue() >= 5) {
        log.warn("give up sync check");
        return;
      }
      syncCheck();
    }
  }

  private void sync() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=%s&skey=%s&lang=zh_CN&pass_ticket=%s",
            sid, skey, passTicket);
    JSONObject reqBody =
        new JSONObject()
            .put("BaseRequest", getBaseRequest())
            .put("SyncKey", syncKey)
            .put("rr", ~System.currentTimeMillis());
    String respBody = http.postJson(url, reqBody.toString());
    JSONObject resp = new JSONObject(respBody);
    checkState(resp.getJSONObject("BaseResponse").getInt("Ret") == 0, "ret != 0");
    updateSyncKey(resp.getJSONObject("SyncKey"));
    if (resp.getInt("AddMsgCount") > 0) {
      JSONArray msgObjArr = resp.getJSONArray("AddMsgList");
      for (int i = 0; i < msgObjArr.length(); i++) {
        Message message = Message.of(this, msgObjArr.getJSONObject(i));
        log.info("receive: {}", message);
        if (message.getType() == null) {
          continue;
        }
        listeners
            .parallelStream()
            .forEach(
                msgListener -> {
                  try {
                    msgListener.onReceiveMessage(message);
                  } catch (Exception e) {
                    log.error("msg listener has error", e);
                  }
                });
      }
    }
  }

  private void updateSyncKey(JSONObject syncKey) {
    JSONArray list = syncKey.getJSONArray("List");
    int len = list.length();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      JSONObject pair = list.getJSONObject(i);
      sb.append(pair.getInt("Key")).append("_").append(pair.getInt("Val")).append("|");
    }
    sb.deleteCharAt(sb.length() - 1);
    this.syncKeyStr = sb.toString();
    this.syncKey = syncKey;
  }

  /**
   * generate device id
   *
   * @return e + 15 * random number
   */
  static String generateDeviceId() {
    Random random = new Random();
    StringBuilder sb = new StringBuilder("e");
    IntStream.range(0, 15).forEach(i -> sb.append(random.nextInt(10)));
    return sb.toString();
  }
}
