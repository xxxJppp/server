/*
 * This file is part of the Wildfire Chat package.
 * (c) Heavyrain2012 <heavyrain.lee@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package io.moquette.imhandler;

import cn.wildfirechat.pojos.InputAndFriendRequest;
import cn.wildfirechat.pojos.SendMessageData;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.proto.WFCMessage;
import com.google.gson.Gson;
import io.moquette.spi.impl.Qos1PublishHandler;
import io.netty.buffer.ByteBuf;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.common.IMTopic;
import io.tio.TioClientStarter;
import io.tio.TioPacket;
import org.tio.core.Tio;

import java.io.UnsupportedEncodingException;

import static cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS;
import static cn.wildfirechat.common.IMTopic.HandleFriendRequestTopic;

@Handler(IMTopic.AddFriendRequestTopic)
public class AddFriendHandler extends GroupHandler<WFCMessage.AddFriendRequest> {
    @Override
    public ErrorCode action(ByteBuf ackPayload, String clientID, String fromUser, boolean isAdmin, WFCMessage.AddFriendRequest request, Qos1PublishHandler.IMCallback callback) {
            long[] head = new long[1];
            ErrorCode errorCode = m_messagesStore.saveAddFriendRequest(fromUser, request, head);
            if (errorCode == ERROR_CODE_SUCCESS) {
                WFCMessage.User user = m_messagesStore.getUserInfo(request.getTargetUid());
                if (user != null && user.getType() == ProtoConstants.UserType.UserType_Normal) {
                    //Tio发送
                    InputAndFriendRequest inputAndFriendRequest = new InputAndFriendRequest();
                    inputAndFriendRequest.setUserId(fromUser);
                    inputAndFriendRequest.setFriendUid(request.getTargetUid());
                    inputAndFriendRequest.setReason(request.getReason());
                    TioPacket packet = new TioPacket();
                    try {
                        packet.setBody(new Gson().toJson(inputAndFriendRequest).getBytes(TioPacket.CHARSET));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Tio.send(TioClientStarter.clientChannelContext,packet);
                    publisher.publishNotification(IMTopic.NotifyFriendRequestTopic, request.getTargetUid(), head[0]);
                } else if(user != null && user.getType() == ProtoConstants.UserType.UserType_Robot) {
                    WFCMessage.HandleFriendRequest handleFriendRequest = WFCMessage.HandleFriendRequest.newBuilder().setTargetUid(fromUser).setStatus(ProtoConstants.FriendRequestStatus.RequestStatus_Accepted).build();
                    mServer.internalRpcMsg(request.getTargetUid(), null, handleFriendRequest.toByteArray(), 0, fromUser, HandleFriendRequestTopic, false);
                }
            }
            return errorCode;
    }
}
