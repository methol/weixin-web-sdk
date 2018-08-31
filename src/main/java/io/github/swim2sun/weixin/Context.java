package io.github.swim2sun.weixin;

import lombok.Data;
import org.json.JSONObject;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * @author xyyou
 * @version 1.0 2018-08-31.
 */
@Data
class Context {
    private final String deviceId;
    private String passTicket;
    private String skey;
    private String sid;
    private String uin;
    private String syncKeyStr;
    private JSONObject syncKey;

    public Context() {
        deviceId = generateDeviceId();
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
