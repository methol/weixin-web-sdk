package io.github.swim2sun.weixin;

/**
 * Weixin Message Listener
 *
 * @author swim2sun
 * @version 1.0 2018-08-21.
 */
@FunctionalInterface
public interface WeixinMsgListener {

    /**
     * on receive weixin message
     *
     * @param message weixin message
     */
    void onReceiveMessage(Message message);

}
