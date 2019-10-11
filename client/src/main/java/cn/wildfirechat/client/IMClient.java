package cn.wildfirechat.client;

import cn.wildfirechat.common.IMTopic;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.proto.WFCMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.xiaoleilu.loServer.pojos.InputRoute;
import io.moquette.spi.impl.security.AES;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.*;
import org.fusesource.mqtt.codec.MQTTFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static cn.wildfirechat.client.IMClient.ConnectionStatus.ConnectionStatus_Connected;
import static cn.wildfirechat.client.IMClient.ConnectionStatus.ConnectionStatus_Connecting;
import static cn.wildfirechat.client.IMClient.ConnectionStatus.ConnectionStatus_Unconnected;


public class IMClient implements Listener {
    private final String userId;
    private final String token;
    private final String clientId;
    private final String host;
    private final int port;
    /**
     * 连接状态回调
     */
    private ConnectionStatusCallback connectionStatusCallback;

    /**
     * 接收消息回调
     */
    private ReceiveMessageCallback receiveMessageCallback;

    /**
     * 好友验证请求回调
     */
    private FriendRequestCallback friendRequestCallback;

    /**
     * 通知好友请求拉取消息
     */
    private NotifyFriendRequestCallback notifyFriendRequestCallback;

    /**
     * 成为好友刷新列表回调
     */
    private FriendCallback friendCallback;

    private ConnectionStatus connectionStatus;
    private final static short KEEP_ALIVE = 30;// 低耗网络，但是又需要及时获取数据，心跳30s
    public final static long RECONNECTION_DELAY = 2000;

    protected String mqttServerIp;
    protected long mqttServerPort;
    private static byte[] commonSecret= {0x00,0x11,0x22,0x33,0x44,0x55,0x66,0x77,0x78,0x79,0x7A,0x7B,0x7C,0x7D,0x7E,0x7F};
    protected String privateSecret;

    private long messageHead;

    private transient MQTT mqtt = new MQTT();
    private transient CallbackConnection connection = null;

    private static final Logger log = LoggerFactory.getLogger(IMClient.class);

    public interface ReceiveMessageCallback {
        /**
         * 接收消息
         * @param messageList
         * @param messageHead
         * @param hasMore
         */
        void onReceiveMessages(List<WFCMessage.Message> messageList, long messageHead, boolean hasMore);

        /**
         * 撤回消息
         * @param messageUid
         */
        void onRecallMessage(long messageUid);
    }

    public interface ConnectionStatusCallback {
        void onConnectionStatusChanged(ConnectionStatus newStatus);
    }

    public interface SendMessageCallback {
        void onSuccess(long messageUid, long timestamp);
        void onFailure(int errorCode);
    }

    public interface GeneralCallback<T> {
        void onSuccess(T t);
        void onFailure(int errorCode);
    }

    public interface FriendRequestCallback {
        /**
         * 收到添加好友请求
         * @param friendRequests
         */
        void onSuccess(List<WFCMessage.FriendRequest> friendRequests);
    }

    public interface NotifyFriendRequestCallback {
        /**
         * 收到通知拉取消息
         */
        void notifyPullHandler();
    }

    public interface FriendCallback {
        /**
         * 添加好友成功，刷新好友列表
         * @param friends
         */
        void onSuccess(List<WFCMessage.Friend> friends);
    }

    public enum ConnectionStatus {
        ConnectionStatus_Unconnected,
        ConnectionStatus_Connecting,
        ConnectionStatus_Connected,
    }

    public IMClient(String userId, String token, String clientId, String host, int port) {
        this.userId = userId;

        byte[] data = Base64.getDecoder().decode(token);
        data = AES.AESDecrypt(data, commonSecret, false);
        String s = new String(data);
        String[] ss = s.split("\\|");

        this.token = ss[0];
        this.privateSecret = ss[1];
        this.clientId = clientId;
        this.host = host;
        this.port = port;
        AES.init(commonSecret);
    }


    public void connect() {
        if(route(userId, token)) {
            try {
                mqtt.setHost("tcp://" + mqttServerIp + ":" + mqttServerPort);
                mqtt.setVersion("3.1.1");
                mqtt.setKeepAlive((short)180);

                mqtt.setClientId(clientId);
                mqtt.setConnectAttemptsMax(100);
                // 设置重新连接的次数
                mqtt.setReconnectAttemptsMax(100);
                // 设置重连的间隔时间
                mqtt.setReconnectDelay(RECONNECTION_DELAY);
                // 设置心跳时间
                mqtt.setKeepAlive(KEEP_ALIVE);

                mqtt.setUserName(userId);
                byte[] password = AES.AESEncrypt(token, privateSecret);
                mqtt.setPassword(new UTF8Buffer(password));
                mqtt.setTracer(new Tracer(){
                    @Override
                    public void onReceive(MQTTFrame frame) {
                        log.info("recv: "+frame);
                    }

                    @Override
                    public void onSend(MQTTFrame frame) {
                        log.info("send: "+frame);
                    }

                    @Override
                    public void debug(String message, Object... args) {
                        log.info(String.format("debug: "+message, args));
                    }
                });
                connection = mqtt.callbackConnection();
                connection.listener(this);

                //connecting
                connectionStatus = ConnectionStatus_Connecting;
                if(connectionStatusCallback != null) {
                    connectionStatusCallback.onConnectionStatusChanged(connectionStatus);
                }

                connection.connect(new Callback<byte[]>() {
                    @Override
                    public void onSuccess(byte[] value) {
                        if (value != null) {
                            try {
                                WFCMessage.ConnectAckPayload ackPayload = WFCMessage.ConnectAckPayload.parseFrom(value);
                                messageHead = ackPayload.getMsgHead();
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                        }

                        log.info("MQTT client on connect success");
                        connectionStatus = ConnectionStatus_Connected;
                        if(connectionStatusCallback != null) {
                            connectionStatusCallback.onConnectionStatusChanged(connectionStatus);
                        }
                    }

                    @Override
                    public void onFailure(Throwable value) {
                        log.error("MQTT client on connect failure");
                        connectionStatus = ConnectionStatus_Unconnected;
                        if(connectionStatusCallback != null) {
                            connectionStatusCallback.onConnectionStatusChanged(connectionStatus);
                        }
                    }
                });


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect(boolean clearSession, final Callback<Void> onComplete) {
        this.connection.disconnect(clearSession, onComplete);
    }

    /**
     * 从指定消息id开始拉取消息
     * @param notifyMessage
     * @param head 消息id
     */
    public void pullMessage(WFCMessage.NotifyMessage notifyMessage, long head) {
        WFCMessage.PullMessageRequest request = WFCMessage.PullMessageRequest.newBuilder().setId(head).setType(notifyMessage.getType()).build();
        byte[] data = request.toByteArray();
        data = AES.AESEncrypt(data, privateSecret);
        connection.publish(IMTopic.PullMessageTopic, data, QoS.AT_LEAST_ONCE, false, new Callback<byte[]>() {
            @Override
            public void onSuccess(byte[] value) {
                byte[] data = verifDataBytes(value);
                try {
                    data = AES.AESDecrypt(data, privateSecret, true);
                    try {
                        WFCMessage.PullMessageResult result = WFCMessage.PullMessageResult.parseFrom(data);
                        if (receiveMessageCallback != null && result.getMessageList().size() > 0) {
                            List<WFCMessage.Message> messages = result.getMessageList();
                            List<WFCMessage.Message> out = new ArrayList<>();
                            for (WFCMessage.Message msg : messages) {
                                if (msg.getConversation().getType() == ProtoConstants.ConversationType.ConversationType_Private && msg.getConversation().getTarget().equals(userId)) {
                                    msg = msg.toBuilder().setConversation(msg.getConversation().toBuilder().setTarget(msg.getFromUser())).build();
                                }
                                out.add(msg);
                            }
                            receiveMessageCallback.onReceiveMessages(out, result.getHead(),false);
                        }
                        messageHead = result.getHead();
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Throwable value) {
                log.error("Publish topic={} onFailure", IMTopic.PullMessageTopic);
            }
        });
    }


    /**
     * 发送消息
     * @param conversation
     * @param messageContent
     * @param callback
     */
    public void sendMessage(WFCMessage.Conversation conversation, WFCMessage.MessageContent messageContent, final SendMessageCallback callback) {
        WFCMessage.Message message = WFCMessage.Message.newBuilder().setConversation(conversation).setContent(messageContent).setFromUser(userId).build();
        byte[] data = message.toByteArray();
        data = AES.AESEncrypt(data, privateSecret);
        connection.publish(IMTopic.SendMessageTopic, data, QoS.AT_LEAST_ONCE, false, new Callback<byte[]>() {
            @Override
            public void onSuccess(byte[] value) {
                if (value[0] == 0) {
                    byte[] data = getDataBytes(value);

                    data = AES.AESDecrypt(data, privateSecret, true);
                    ByteBuffer buffer = ByteBuffer.wrap(data, 0,16);

                    long messageUid = buffer.getLong();
                    long timestamp = buffer.getLong();
                    callback.onSuccess(messageUid, timestamp);
                } else {
                    callback.onFailure(value[0]);
                }
            }

            @Override
            public void onFailure(Throwable value) {
                callback.onFailure(-1);
            }
        });
    }

    /**
     * 拉取好友请求
     * @param friendRequestHead
     */
    public void pullAddFriendRequest(long friendRequestHead) {
        try {
            WFCMessage.Version request = WFCMessage.Version.newBuilder().setVersion(friendRequestHead).build();
            byte[] data = request.toByteArray();
            data = AES.AESEncrypt(data, privateSecret);
            connection.publish(IMTopic.FriendRequestPullTopic, data, QoS.AT_LEAST_ONCE, false, new Callback<byte[]>(){
                @Override
                public void onSuccess(byte[] value) {
                    byte[] data = verifDataBytes(value);
                    try {
                        data = AES.AESDecrypt(data, privateSecret, true);
                        try {
                            if(data!=null && data.length!=0){
                                WFCMessage.GetFriendRequestResult result = WFCMessage.GetFriendRequestResult.parseFrom(data);
                                if (friendRequestCallback != null && result.getEntryList().size() > 0) {
                                    List<WFCMessage.FriendRequest> friendRequests = result.getEntryList();
                                    friendRequestCallback.onSuccess(friendRequests);
                                }
                            }
                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onFailure(Throwable value) {
                    log.error("Publish topic={} onFailure",IMTopic.FriendRequestPullTopic);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    protected boolean route(String userId, String token) {
        HttpPost httpPost;
        try{
            HttpClient httpClient = HttpClientBuilder.create().build();
            httpPost = new HttpPost("http://" + host + ":" + port + "/route");
            InputRoute inputRoute = new InputRoute();
            inputRoute.setUserId(userId);
            inputRoute.setClientId(clientId);
            inputRoute.setToken(token);

            WFCMessage.RouteRequest routeRequest = WFCMessage.RouteRequest.newBuilder().setPlatform(0).build();

            WFCMessage.IMHttpWrapper request = WFCMessage.IMHttpWrapper.newBuilder().setClientId(clientId).setToken(token).setRequest("ROUTE").setData(ByteString.copyFrom(routeRequest.toByteArray())).build();
            byte[] data = AES.AESEncrypt(request.toByteArray(), privateSecret);
            data = Base64.getEncoder().encode(data);

            StringEntity entity = new StringEntity(new String(data), Charset.forName("UTF-8"));
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);

            byte[] cidByte = AES.AESEncrypt(clientId.getBytes(), commonSecret);
            cidByte = Base64.getEncoder().encode(cidByte);
            String cid = new String(cidByte);
            httpPost.setHeader("cid", cid);

            byte[] uidByte = AES.AESEncrypt(userId.getBytes(), commonSecret);
            uidByte = Base64.getEncoder().encode(uidByte);
            String uid = new String(uidByte);
            httpPost.setHeader("uid", uid);

            HttpResponse response = httpClient.execute(httpPost);
            if(response != null){
                if (response.getStatusLine().getStatusCode() != 200) {
                    log.error("Http response error {" + response.getStatusLine().getStatusCode() + "}");
                    return false;
                }
                HttpEntity resEntity = response.getEntity();
                if(resEntity != null){
                    try {
                        byte[] bytes = new byte[resEntity.getContent().available()];
                        resEntity.getContent().read(bytes);
                        if (bytes[0] == 0) {
                            byte[] bytes1 = getDataBytes(bytes);
                            byte[] rawData = AES.AESDecrypt(bytes1, privateSecret, true);
                            WFCMessage.RouteResponse routeResponse = WFCMessage.RouteResponse.parseFrom(rawData);
                            mqttServerIp = routeResponse.getHost();
                            mqttServerPort = routeResponse.getLongPort();
                            routeResponse.getShortPort();
                            return true;
                        } else {
                            log.error("the route failure:" + bytes[0]);
                            return false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (UnsupportedOperationException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                log.error("Http response nil");
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return false;
    }


    @Override
    public void onConnected() {
        log.info("connect wildfire server onConnected");
        connectionStatus = ConnectionStatus_Connecting;
        if(connectionStatusCallback != null) {
            connectionStatusCallback.onConnectionStatusChanged(connectionStatus);
        }
    }

    @Override
    public void onDisconnected() {
        log.info("connect wildfire server onDisconnected");
        connectionStatus = ConnectionStatus_Unconnected;
        if(connectionStatusCallback != null) {
            connectionStatusCallback.onConnectionStatusChanged(connectionStatus);
        }
    }

    @Override
    public void onPublish(UTF8Buffer topic, Buffer body, Runnable ack) {
        log.info("MQTT client onPublish Notify topic={}", topic.toString());
        ack.run();
        if (topic.toString().equals(IMTopic.NotifyMessageTopic)) {
            try {
                WFCMessage.NotifyMessage notifyMessage = WFCMessage.NotifyMessage.parseFrom(body.toByteArray());
                pullMessage(notifyMessage, messageHead);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else if(topic.toString().equals(IMTopic.NotifyFriendRequestTopic)){
           /**
             此处采用回调拉取最新的好友请求,方便内部传入消息id，准确拉取消息
           */
           if(notifyFriendRequestCallback != null){
               notifyFriendRequestCallback.notifyPullHandler();
           }else{
               throw new RuntimeException("Initialize setting notifyFriendRequestCallback property value");
           }
        }else if(topic.toString().equals(IMTopic.NotifyFriendTopic)){
            try {
                WFCMessage.Version request = WFCMessage.Version.newBuilder().setVersion(0).build();
                byte[] data = request.toByteArray();
                data = AES.AESEncrypt(data, privateSecret);
                connection.publish(IMTopic.FriendPullTopic, data, QoS.AT_LEAST_ONCE, false, new Callback<byte[]>(){
                    @Override
                    public void onSuccess(byte[] value) {
                        byte[] data = verifDataBytes(value);
                        try {
                            data = AES.AESDecrypt(data, privateSecret, true);
                            try {
                                WFCMessage.GetFriendsResult result = WFCMessage.GetFriendsResult.parseFrom(data);
                                if (friendCallback != null && result.getEntryList().size() > 0) {
                                    List<WFCMessage.Friend> friends = result.getEntryList();
                                    friendCallback.onSuccess(friends);
                                }
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onFailure(Throwable value) {
                        log.error("Publish topic={} onFailure",IMTopic.FriendPullTopic);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] verifDataBytes(byte[] value) {
        if (value == null || value.length == 0) {
            log.error("Not Invalide data");
        }
        if (value[0] != 0) {
            log.error("Pull message error with errorCode:" + value[0]);
        }
        return getDataBytes(value);
    }

    private byte[] getDataBytes(byte[] bytes) {
        byte[] bytes1 = new byte[bytes.length - 1];
        for (int i = 0; i < bytes1.length; i++) {
            bytes1[i] = bytes[i + 1];
        }
        return bytes1;
    }

    @Override
    public void onFailure(Throwable value) {
        log.error("connect wildfire server onDisconnected" + value.toString());
        if(connectionStatusCallback != null) {
            connectionStatusCallback.onConnectionStatusChanged(ConnectionStatus_Unconnected);
        }
    }

    public void setConnectionStatusCallback(ConnectionStatusCallback connectionStatusCallback) {
        this.connectionStatusCallback = connectionStatusCallback;
    }

    public void setReceiveMessageCallback(ReceiveMessageCallback receiveMessageCallback) {
        this.receiveMessageCallback = receiveMessageCallback;
    }

    public void setFriendRequestCallback(FriendRequestCallback friendRequestCallback) {
        this.friendRequestCallback = friendRequestCallback;
    }

    public void setNotifyFriendRequestCallback(NotifyFriendRequestCallback notifyFriendRequestCallback) {
        this.notifyFriendRequestCallback = notifyFriendRequestCallback;
    }

    public void setFriendCallback(FriendCallback friendCallback) {
        this.friendCallback = friendCallback;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getClientId() {
        return clientId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public static void main(String[] args) {
        //token与userid和clientid是绑定的，使用时一定要传入正确的userid和clientid，不然会认为token非法
        //clientId唯一代表一个设备，只能有一个登录。如果使用同一个clientId登录多次，会出现不可预料问题。
        IMClient client = new IMClient("zbzUzU88", "5m2fRxzwEVljVIUMSEcCqOAWdIRsWeSv8DgIJU9bU4MEsipo7UfD9SDyXhTuLvFaGeOIA6/jpxuVZHy5TNTZvveZtuc0ejkwiW/mvOGxbEDHXOwP3zRZsWayVFQzn9Nddomie0TMuMjVSk1DrKnJIRMGBqQa4QXGxAsfbiRs1nw=",
            "488ad2acc1d653801566034184818", "192.168.10.57", 80);
        final long[] friendRequestHead = {};
        //接收消息回调
        client.setReceiveMessageCallback(new ReceiveMessageCallback() {
            @Override
            public void onReceiveMessages(List<WFCMessage.Message> messageList,long messageHead ,boolean hasMore) {
                for (WFCMessage.Message message : messageList) {
                    if(message.getConversation().getType() == ProtoConstants.ConversationType.ConversationType_Private){
                        WFCMessage.MessageContent content = message.getContent();
                        String searchableContent = content.getSearchableContent();
                        System.out.println("私聊消息---------"+searchableContent);
                    }else if(message.getConversation().getType() == ProtoConstants.ConversationType.ConversationType_Group){
                        WFCMessage.MessageContent content = message.getContent();
                        String searchableContent = content.getSearchableContent();
                        System.out.println("群聊消息---------"+searchableContent);
                    }
                }
            }

            @Override
            public void onRecallMessage(long messageUid) {
                System.out.println("recalled messages");
            }
        });

        client.setNotifyFriendRequestCallback(()->client.pullAddFriendRequest(friendRequestHead[0]));

        //好友请求回调
        client.setFriendRequestCallback(new FriendRequestCallback() {
            @Override
            public void onSuccess(List<WFCMessage.FriendRequest> friendRequests) {
                System.out.println("FriendRequestCallback onSuccess");
                long max = 0L;
                for (WFCMessage.FriendRequest friendRequest : friendRequests) {
                    System.out.println(friendRequest.getReason()+"——>"+friendRequest.getToUid());
                    long updateDt = friendRequest.getUpdateDt();
                    if(max < updateDt){
                        max = updateDt;
                    }
                }
                friendRequestHead[0] = max;
                System.out.println(friendRequestHead[0]);
            }
        });

        client.setFriendCallback(new FriendCallback() {
            @Override
            public void onSuccess(List<WFCMessage.Friend> friends) {
                System.out.println("FriendCallback onSuccess");
                for (WFCMessage.Friend friend : friends) {
                    System.out.println(friend.getUid());
                }
            }
        });

        //连接状态
        client.setConnectionStatusCallback((ConnectionStatus newStatus) -> {
            if (newStatus == ConnectionStatus_Connected) {
                try {
                    //连接成功 拉取消息
                    WFCMessage.NotifyMessage notifyMessage = WFCMessage.NotifyMessage.newBuilder().setType(ProtoConstants.PullType.Pull_Normal).setHead(0).build();
                    client.pullMessage(notifyMessage, notifyMessage.getHead());

                    //拉取好友请求
                    client.pullAddFriendRequest(friendRequestHead[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        client.connect();

        try {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
