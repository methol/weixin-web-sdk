package io.github.swim2sun;

import io.github.swim2sun.weixin.Message;
import io.github.swim2sun.weixin.User;
import io.github.swim2sun.weixin.Weixin;

import java.util.concurrent.CountDownLatch;

/**
 * @author swim2sun
 * @version 1.0 2018-08-21.
 */
public class Sample {

  public static void main(String[] args) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Weixin weixin = Weixin.create();
    // 登录
    String qrcodeImgUrl =
        weixin.loginQrcode(
            (succeed, msg) -> {
              if (succeed) {
                System.out.println("登录成功");
                latch.countDown();
              }
            });
    System.out.println("请使用浏览器打开链接二维码链接，并使用微信扫描登录：" + qrcodeImgUrl);
    latch.await();
    // 获取所有联系人
    weixin.getContactList().forEach(user -> System.out.println(user.getNickName()));
    // 接收消息
    weixin.addMsgListener(msg -> System.out.println("收到消息：" + msg.getContent()));
    // 发送消息
    User receiver = weixin.findUserByNickname("Xiangyang Pro 2.0"); // 接收方的微信昵称
    Message msg = Message.builder().type(Message.Type.TEXT).content("测试").to(receiver).build();
    weixin.sendMsg(msg);
  }
}
