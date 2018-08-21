package io.github.swim2sun.weixin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assertion util
 *
 * @author swim2sun
 * @version 1.0 2018-08-17.
 */
class Preconditions {

  static void checkArgument(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  static void checkState(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}

/**
 * simple xml util
 *
 * @author swim2sun
 * @version 1.0 2018-08-17.
 */
class XmlUtil {

  static String get(String xml, String key) {
    Matcher matcher = Pattern.compile("<" + key + ">(.+?)</" + key + ">").matcher(xml);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

}
