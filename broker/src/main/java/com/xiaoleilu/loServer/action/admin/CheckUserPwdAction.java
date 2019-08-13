/*
 * This file is part of the Wildfire Chat package.
 * (c) Heavyrain2012 <heavyrain.lee@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package com.xiaoleilu.loServer.action.admin;

import cn.wildfirechat.common.APIPath;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.InputGetUserInfo;
import cn.wildfirechat.pojos.InputOutputUserInfo;
import cn.wildfirechat.proto.WFCMessage;
import com.google.gson.Gson;
import com.xiaoleilu.loServer.RestResult;
import com.xiaoleilu.loServer.annotation.HttpMethod;
import com.xiaoleilu.loServer.annotation.Route;
import com.xiaoleilu.loServer.handler.Request;
import com.xiaoleilu.loServer.handler.Response;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;

import static cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS;

@Route(APIPath.Check_User_Pwd)
@HttpMethod("POST")
public class CheckUserPwdAction extends AdminAction {

    @Override
    public boolean isTransactionAction() {
        return true;
    }

    @Override
    public boolean action(Request request, Response response) {
        if (request.getNettyRequest() instanceof FullHttpRequest) {
            InputGetUserInfo inputUserId = getRequestBody(request.getNettyRequest(), InputGetUserInfo.class);
            if (inputUserId != null
                && (!StringUtil.isNullOrEmpty(inputUserId.getName())
                || !StringUtil.isNullOrEmpty(inputUserId.getPassword()))) {
                ArrayList<String> userIdRet = new ArrayList<>();
                ErrorCode errorCode = messagesStore.login(inputUserId.getName(), inputUserId.getPassword(), userIdRet);
                if (errorCode == ErrorCode.ERROR_CODE_SUCCESS) {
                    response.setStatus(HttpResponseStatus.OK);
                    RestResult result;
                    result = RestResult.resultOf(ErrorCode.ERROR_CODE_SUCCESS);
                    response.setContent(new Gson().toJson(result));
                }else if(errorCode == ErrorCode.ERROR_CODE_NOT_EXIST){
                    response.setStatus(HttpResponseStatus.OK);
                    RestResult result;
                    result = RestResult.resultOf(ErrorCode.ERROR_CODE_NOT_EXIST);
                    response.setContent(new Gson().toJson(result));
                }else if(errorCode == ErrorCode.ERROR_CODE_PASSWORD_INCORRECT){
                    response.setStatus(HttpResponseStatus.OK);
                    RestResult result;
                    result = RestResult.resultOf(ErrorCode.ERROR_CODE_NOT_EXIST);
                    response.setContent(new Gson().toJson(result));
                }
            } else {
                response.setStatus(HttpResponseStatus.OK);
                RestResult result = RestResult.resultOf(ErrorCode.INVALID_PARAMETER);
                response.setContent(new Gson().toJson(result));
            }
        }
        return true;
    }
}
