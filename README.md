# Weixin Web SDK
![JDK](https://img.shields.io/badge/JDK-1.8%2B-orange.svg?style=flat) 
![Travis (.org)](https://img.shields.io/travis/swim2sun/weixin-web-sdk.svg) 
![GitHub tag](https://img.shields.io/github/tag/swim2sun/weixin-web-sdk.svg) 
![GitHub license](https://img.shields.io/github/license/swim2sun/weixin-web-sdk.svg)

微信网页版SDK，可用于构建微信机器人

## 1. Install

### 1.1 Maven

在`pom.xml`中添加依赖：

```xml
<dependency>
  <groupId>io.github.swim2sun</groupId>
  <artifactId>weixin-web-sdk</artifactId>
  <version>1.0</version>
</dependency>
```

### 1.2 Gradle

在`build.gradle`中添加依赖：

```groovy
    compile('io.github.swim2sun:weixin-web-sdk:1.0')
```

## 2. Features

* 微信登录
* 获取联系人
* 接收消息
* 发送消息

> 目前暂时只支持文本消息，后续版本会逐步增加其他类型的消息

## 3. Example

```java
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
    User receiver = weixin.findUserByNickname("xxx"); // 接收方的微信昵称
    Message msg = Message.builder().type(Message.Type.TEXT).content("测试").to(receiver).build();
    weixin.sendMsg(msg);
  }
}
```
