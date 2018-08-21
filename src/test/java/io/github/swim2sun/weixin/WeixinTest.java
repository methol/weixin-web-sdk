package io.github.swim2sun.weixin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weixin Tester.
 *
 * @author youxiangyang
 * @since
 *     <pre>八月 17, 2018</pre>
 *
 * @version 1.0
 */
public class WeixinTest {

  @Test
  public void testCreate() throws Exception {
    Weixin weixin = Weixin.create();
    Assertions.assertNotNull(weixin);
  }

  /** Method: loginQrcode(Consumer callback) */
  @Test
  public void testLoginQrcode() throws Exception {
    Weixin weixin = Weixin.create();
    CountDownLatch latch = new CountDownLatch(1);
    String url =
        weixin.loginQrcode(
            (s, msg) -> {
              try {
                assertTrue(s);
              } finally {
                latch.countDown();
              }
            });
    Assertions.assertNotNull(url);
    System.out.print("please open url in Browser, and scan QrCode using WeiXin APP: ");
    System.out.println(url);
    latch.await();
  }

  /** Method: getContact() */
  @Test
  public void testGetContact() throws Exception {
    Weixin weixin = Weixin.create();
    CountDownLatch latch = new CountDownLatch(1);
    String url =
        weixin.loginQrcode(
            (s, msg) -> {
              try {
                assertTrue(s);
              } finally {
                latch.countDown();
              }
            });
    Assertions.assertNotNull(url);
    System.out.print("please open url in Browser, and scan QrCode using WeiXin APP: ");
    System.out.println(url);
    latch.await();
    List<User> contactList = weixin.getContactList();
    assertTrue(contactList.size() > 0);
  }

  @Test
  public void testReceiveMsg() throws InterruptedException {
    Weixin weixin = Weixin.create();
    CountDownLatch latch = new CountDownLatch(1);
    String url = weixin.loginQrcode((s, msg) -> {});
    weixin.addMsgListener(
        msg -> {
          if (msg.getType() == Message.Type.TEXT) {
            String content = msg.getContent();
            assertNotNull(content);
            assertEquals(content, "测试");
            latch.countDown();
          }
        });
    Assertions.assertNotNull(url);
    System.out.print("please open url in Browser, and scan QrCode using WeiXin APP: ");
    System.out.println(url);
    latch.await();
  }

  @Test
  public void testSendMsg() throws InterruptedException {
    Weixin weixin = Weixin.create();
    CountDownLatch latch = new CountDownLatch(1);
    String url =
        weixin.loginQrcode(
            (s, msg) -> {
              Message message =
                  Message.builder()
                      .type(Message.Type.TEXT)
                      .content("测试")
                      .to(weixin.findUserByNickname("Xiangyang Pro 2.0"))
                      .build();
              weixin.sendMsg(message);
              latch.countDown();
            });
    Assertions.assertNotNull(url);
    System.out.print("please open url in Browser, and scan QrCode using WeiXin APP: ");
    System.out.println(url);
    latch.await();
    List<User> contactList = weixin.getContactList();
    assertTrue(contactList.size() > 0);
  }

  @Test
  public void testPattern() {
    Pattern pattern = Pattern.compile("\\{retcode:\"(.+?)\",selector:\"(.+?)\"\\}");
    assertTrue(pattern.matcher("window.synccheck={retcode:\"0\",selector:\"2\"}").find());
  }

  @Test
  public void testFindTicket() {
    String resp =
        "window.code=200;\n"
            + "window.redirect_uri=\"https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A6RKeM8RoHRyuNjSJJ7yBnH3@qrticket_0&uuid=AaqX3nzWNQ==&lang=zh_CN&scan=1534694733\";";
    Matcher m = Pattern.compile("ticket=(.+?)&uuid=(.+?)&lang=zh_CN&scan=(.+?)").matcher(resp);
    assertTrue(m.find());
  }

  @Test
  public void testGenerateDevice() {
    IntStream.range(0, 1000)
        .forEach(
            i -> {
              String did = Weixin.generateDeviceId();
              assertTrue(did.startsWith("e"));
              assertEquals(did.length(), 16);
            });
  }
}
