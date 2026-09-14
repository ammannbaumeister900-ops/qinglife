package com.yicai.common.constant;

public interface CommonConstant {
    Integer NOAUTH = 401;

    Integer FAILED = 400;

    Integer SERVER = 500;

    long PAGE_SIZE = 20L;

    //短信验证码长度
    int CODE_LENGTH = 4;

    String YES = "1";
    String NO = "0";

    long REVISION = 0;

    //手机号正则
    String PHONE_PATTERN = "^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$";

    //加密key
    String ENCODINGKEY = "JupNetwjhLW3yeb7";

    int ONE = 1;
    int FIVE = 5;
}
