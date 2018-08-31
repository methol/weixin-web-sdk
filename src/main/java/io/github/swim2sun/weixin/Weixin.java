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
  private Context context;
  @Getter private User user;
  private List<User> contactList;
  private Http http;
  private ExecutorService executorService;
  private Set<WeixinMsgListener> listeners;
  private AtomicInteger errorTimes;
  private volatile boolean online;

  // todo online method

  private Weixin() {
    context = new Context();
    http = new Http();
    listeners = new HashSet<>();
    executorService = Executors.newCachedThreadPool();
    errorTimes = new AtomicInteger(0);
    online = false;
  }

  /**
   * create one io.github.swim2sun.weixin.Weixin instance
   *
   * @return new io.github.swim2sun.weixin.Weixin instance
   */
  public static Weixin create() {
    return new Weixin();
  }

  public boolean online() {
    return online;
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
          if (succeed) {
            online = true;
          }
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
              context.getPassTicket(), System.currentTimeMillis(), context.getSkey());
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
        "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?lang=zh_CN&pass_ticket="
            + context.getPassTicket();
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
    this.context.setSkey(XmlUtil.get(resp, "skey"));
    this.context.setSid(XmlUtil.get(resp, "wxsid"));
    this.context.setUin(XmlUtil.get(resp, "wxuin"));
    this.context.setPassTicket(XmlUtil.get(resp, "pass_ticket"));
    init();
  }

  private void init() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?pass_ticket=%s&skey=%s&r=%s",
            context.getPassTicket(), context.getSkey(), System.currentTimeMillis());
    JSONObject baseRequest = getBaseRequest();
    JSONObject body = new JSONObject().put("BaseRequest", baseRequest);
    String respBody = http.postJson(url, body.toString());
    log.trace("weixin init response: {}", respBody);
    JSONObject resp = new JSONObject(respBody);
    checkState(resp.getJSONObject("BaseResponse").getInt("Ret") == 0, "ret not equals to 0");
    checkState(resp.getString("SKey").equals(context.getSkey()), "skey changed");
    this.user = User.parse(resp.getJSONObject("User"));
    log.debug("user info: {}", user);
    updateSyncKey(resp.getJSONObject("SyncKey"));
    statusNotify();
  }

  private JSONObject getBaseRequest() {
    return new JSONObject()
        .put("Uin", context.getUin())
        .put("Sid", context.getSid())
        .put("Skey", context.getSkey())
        .put("DeviceID", context.getDeviceId());
  }

  private void statusNotify() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=%s",
            context.getPassTicket());
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
        params.put("skey", context.getSkey());
        params.put("sid", context.getSid());
        params.put("uin", context.getUin());
        params.put("deviceid", context.getDeviceId());
        params.put("synckey", context.getSyncKeyStr());
        params.put("_", "" + System.currentTimeMillis());
        String resp = http.get(url, params);
        Matcher matcher = pattern.matcher(resp);
        checkState(matcher.find(), "can't find ret code: " + resp);
        String retCode = matcher.group(1);
        String selector = matcher.group(2);
        if (retCode.equals("1100")) {
          log.info("已下线");
          online = false;
          break;
        }
        if (retCode.equals("1102")) {
          log.warn("该账号手机上主动退出了");
          online = false;
          break;
        }
        checkState(retCode.equals("0"), "ret code not valid: " + retCode);
        if (selector.equals("2")) {
          sync();
        }
        if (selector.equals("3")) {
          log.warn("selector equals 3 !");
          Thread.sleep(60 * 1000);
        }
        if (errorTimes.intValue() > 0) {
          errorTimes.set(0);
        }
      }
    } catch (Exception e) {
      log.error("sync check error", e);
      int currentTimes = errorTimes.incrementAndGet();
      if (currentTimes >= 5) {
        log.warn("give up sync check");
        online = false;
        return;
      }
      syncCheck();
    }
  }

  private void sync() {
    String url =
        String.format(
            "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=%s&skey=%s&lang=zh_CN&pass_ticket=%s",
            context.getSid(), context.getSkey(), context.getPassTicket());
    JSONObject reqBody =
        new JSONObject()
            .put("BaseRequest", getBaseRequest())
            .put("SyncKey", context.getSyncKey())
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
    this.context.setSyncKeyStr(sb.toString());
    this.context.setSyncKey(syncKey);
  }
}
