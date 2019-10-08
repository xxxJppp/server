/*
 * This file is part of the Wildfire Chat package.
 * (c) Heavyrain2012 <heavyrain.lee@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package cn.wildfirechat.common;

public interface IMTopic {
	String SendMessageTopic = "MS";
    String MultiCastMessageTopic = "MMC";
    String RecallMessageTopic = "MR";
	String PullMessageTopic = "MP";
	String NotifyMessageTopic = "MN";
    String NotifyRecallMessageTopic = "RMN";
    String BroadcastMessageTopic = "MBC";

    String GetUserSettingTopic = "UG";
    String PutUserSettingTopic = "UP";
    String NotifyUserSettingTopic = "UN";

    String CreateGroupTopic = "GC";
	String AddGroupMemberTopic = "GAM";
	String KickoffGroupMemberTopic = "GKM";
	String QuitGroupTopic = "GQ";
	String DismissGroupTopic = "GD";
	String ModifyGroupInfoTopic = "GMI";
    String ModifyGroupAliasTopic = "GMA";
    String GetGroupInfoTopic = "GPGI";
    //拉取群组成员
    String GetGroupMemberTopic = "GPGM";
    String TransferGroupTopic = "GTG";
    String SetGroupManagerTopic = "GSM";

    String GetUserInfoTopic = "UPUI";
    String ModifyMyInfoTopic = "MMI";

	String GetQiniuUploadTokenTopic = "GQNUT";

	//添加好友请求
    String AddFriendRequestTopic = "FAR";
    //处理好友请求 同意或者拒绝
    String HandleFriendRequestTopic = "FHR";
    //ack确认好友请求
    String FriendRequestPullTopic = "FRP";
    //添加好友通知
    String NotifyFriendRequestTopic = "FRN";
    //好友请求未读记录
    String RriendRequestUnreadSyncTopic = "FRUS";

    String DeleteFriendTopic = "FDL";
    //拉取好友列表
    String FriendPullTopic = "FP";
    String NotifyFriendTopic = "FN";
    String BlackListUserTopic = "BLU";
    String SetFriendAliasTopic = "FALS";


    String UploadDeviceTokenTopic = "UDT";

    String UserSearchTopic = "US";

    String JoinChatroomTopic = "CRJ";
    String QuitChatroomTopic = "CRQ";
    String GetChatroomInfoTopic = "CRI";
    String GetChatroomMemberTopic = "CRMI";

    String RouteTopic = "ROUTE";

    String CreateChannelTopic = "CHC";
    String ModifyChannelInfoTopic = "CHMI";
    String TransferChannelInfoTopic = "CHT";
    String DestoryChannelInfoTopic = "CHD";
    String ChannelSearchTopic = "CHS";
    String ChannelListenTopic = "CHL";
    String ChannelPullTopic = "CHP";

    String GetTokenTopic = "GETTOKEN";

    String LoadRemoteMessagesTopic = "LRM";
}
