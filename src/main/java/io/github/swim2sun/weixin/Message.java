package io.github.swim2sun.weixin;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * weixin message
 *
 * @author swim2sun
 * @version 1.0 2018-08-21.
 */
@Slf4j
@Builder
@ToString
public class Message {
  @Getter private Type type;
  @Getter private User from;
  @Getter private User to;
  @Getter private String content;

  private static final Random random = new Random();

  static Message of(Weixin weixin, JSONObject json) {
    String fromUserName = json.getString("FromUserName");
    String toUserName = json.getString("ToUserName");
    int msgType = json.getInt("MsgType");
    Type type = Type.of(msgType);
    if (Objects.isNull(type)) {
      log.warn("unknown message type: {}", msgType);
    }
    String content = json.getString("Content");
    return Message.builder()
        .from(weixin.findUser(fromUserName))
        .to(weixin.findUser(toUserName))
        .type(type)
        .content(content)
        .build();
  }

  JSONObject toJson(Weixin weixin) {
    if (Objects.isNull(from)) {
      from = weixin.getUser();
    }
    String msgId = generateId();
    return new JSONObject()
        .put("Type", type.getId())
        .put("Content", content)
        .put("FromUserName", from.getUserName())
        .put("ToUserName", to.getUserName())
        .put("LocalID", msgId)
        .put("ClientMsgId", msgId);
  }

  private static String generateId() {
    StringBuilder sb = new StringBuilder();
    sb.append(System.currentTimeMillis());
    IntStream.range(0, 4).forEach(i -> sb.append(random.nextInt(10)));
    return sb.toString();
  }

  public enum Type {
    TEXT(1);

    @Getter private final int id;

    Type(int id) {
      this.id = id;
    }

    public static Type of(int id) {
      return Stream.of(Type.values()).filter(t -> t.id == id).findAny().orElse(null);
    }
  }
}
