# Weixin Web SDK
![JDK](https://img.shields.io/badge/JDK-1.8%2B-orange.svg?style=flat) 
![Travis (.org)](https://img.shields.io/travis/swim2sun/weixin-web-sdk.svg) 
![GitHub tag](https://img.shields.io/github/tag/swim2sun/weixin-web-sdk.svg) 
![GitHub license](https://img.shields.io/github/license/swim2sun/weixin-web-sdk.svg)

微信网页版SDK，可用于构建微信机器人

## Example

```java
Weixin weixin = Weixin.create();
CountDownLatch latch = new CountDownLatch(1);
// 登录
String qrcodeImgUrl = weixin.loginQrcode((succeed, msg) -> {
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
```
