/*
 * 	一、服务条款
	1. 被授权者务必尊重知识产权，严格保证不恶意传播产品源码、不得直接对授权的产品本身进行二次转售或倒卖、不得对授权的产品进行简单包装后声称为自己的产品等。否则我们有权利收回产品授权，并根据事态轻重追究相应法律责任。
	2. 被授权者可将授权后的产品用于任意符合国家法律法规的应用平台，但一个授权使用不得超过5个域名和5个项目。
	3. 授权 t-io 官方的付费产品（如：t-io官方文档阅读权限、tio-study等），不支持退款。
	4. 我们有义务为被授权者提供有效期内的产品下载、更新和维护，一旦过期，被授权者无法享有相应权限。终身授权则不受限制。
	t-io
	5. t-io 官方的付费产品中的”tio-study”并不保证源代码中绝对不出现BUG，用户遇到BUG时希望可以及时反馈给t-io，相信在大众的努力下，”tio-study”这个产品会越来越成熟，越来越普及
	6. 本条款主要针对恶意用户，在实施过程中会保持灵活度，毕竟谁也不想找麻烦，所以请良心用户可以放心使用！
	7. 本站有停止运营的风险，如果本站主动暂停或停止运营，本站有义务提前30天告知所有用户，以便用户作好相应准备；如果本站受不可抗拒因素暂停或停止运营（包括但不限于遭受DDOS攻击、SSL临时过期、手续升级等），本站不承担任何责任
	
	二、免责声明
	服务条款第2条已经说得很清楚，被授权者只能将产品应用于符合国家法律法规的应用平台，如果被授权者违背该条，由此产生的法律后果由被授权者独自承担，与t-io及t-io作者无关
 */


package io.tio;

import org.tio.client.intf.ClientAioHandler;
import org.tio.core.ChannelContext;
import org.tio.core.TioConfig;
import org.tio.core.exception.AioDecodeException;
import org.tio.core.intf.Packet;

import java.nio.ByteBuffer;

/**
 * 
 * @author tanyaowu
 */
public class TioClientAioHandler implements ClientAioHandler {
	private static TioPacket heartbeatPacket = new TioPacket();


	/**
	 * 解码：把接收到的ByteBuffer，解码成应用可以识别的业务消息包
	 * 总的消息结构：消息头 + 消息体
	 * 消息头结构：    4个字节，存储消息体的长度
	 * 消息体结构：   对象的json串的byte[]
	 */
	@Override
	public TioPacket decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext channelContext) throws AioDecodeException {
		//收到的数据组不了业务包，则返回null以告诉框架数据不够
		if (readableLength < TioPacket.HEADER_LENGTH) {
			return null;
		}

		//读取消息体的长度
		int bodyLength = buffer.getInt();

		//数据不正确，则抛出AioDecodeException异常
		if (bodyLength < 0) {
			throw new AioDecodeException("bodyLength [" + bodyLength + "] is not right, remote:" + channelContext.getClientNode());
		}

		//计算本次需要的数据长度
		int neededLength = TioPacket.HEADER_LENGTH + bodyLength;
		//收到的数据是否足够组包
		int isDataEnough = readableLength - neededLength;
		// 不够消息体长度(剩下的buffe组不了消息体)
		if (isDataEnough < 0) {
			return null;
		} else //组包成功
		{
			TioPacket imPacket = new TioPacket();
			if (bodyLength > 0) {
				byte[] dst = new byte[bodyLength];
				buffer.get(dst);
				imPacket.setBody(dst);
			}
			return imPacket;
		}
	}

	/**
	 * 编码：把业务消息包编码为可以发送的ByteBuffer
	 * 总的消息结构：消息头 + 消息体
	 * 消息头结构：    4个字节，存储消息体的长度
	 * 消息体结构：   对象的json串的byte[]
	 */
	@Override
	public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext channelContext) {
		TioPacket helloPacket = (TioPacket) packet;
		byte[] body = helloPacket.getBody();
		int bodyLen = 0;
		if (body != null) {
			bodyLen = body.length;
		}

		//bytebuffer的总长度是 = 消息头的长度 + 消息体的长度
		int allLen = TioPacket.HEADER_LENGTH + bodyLen;
		//创建一个新的bytebuffer
		ByteBuffer buffer = ByteBuffer.allocate(allLen);
		//设置字节序
		buffer.order(tioConfig.getByteOrder());

		//写入消息头----消息头的内容就是消息体的长度
		buffer.putInt(bodyLen);

		//写入消息体
		if (body != null) {
			buffer.put(body);
		}
		return buffer;
	}
	
	/**
	 * 处理消息
	 */
	@Override
	public void handler(Packet packet, ChannelContext channelContext) throws Exception {
		TioPacket helloPacket = (TioPacket) packet;
		byte[] body = helloPacket.getBody();
		if (body != null) {
			String str = new String(body, TioPacket.CHARSET);
			System.out.println("收到消息：" + str);
		}

		return;
	}

	/**
	 * 此方法如果返回null，框架层面则不会发心跳；如果返回非null，框架层面会定时发本方法返回的消息包
	 */
	@Override
	public TioPacket heartbeatPacket(ChannelContext channelContext) {
		return heartbeatPacket;
	}
}
