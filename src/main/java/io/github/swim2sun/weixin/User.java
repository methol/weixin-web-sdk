package io.github.swim2sun.weixin;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.json.JSONObject;

/**
 * Weixin user
 *
 * @author swim2sun
 * @version 1.0 2018-08-20.
 */
@Builder
@ToString(exclude = {"uin", "headImgUrl"})
public class User {
  @Getter private long uin;
  @Getter private String userName;
  @Getter private String nickName;
  @Getter private String headImgUrl;

  static User parse(JSONObject jsonObject) {
    long uni = jsonObject.getLong("Uin");
    String userName = jsonObject.getString("UserName");
    String nickName = jsonObject.getString("NickName");
    String headImgUrl = jsonObject.getString("HeadImgUrl");
    return User.builder()
        .uin(uni)
        .userName(userName)
        .nickName(nickName)
        .headImgUrl(headImgUrl)
        .build();
  }
}
