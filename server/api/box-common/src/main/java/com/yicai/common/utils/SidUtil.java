package com.yicai.common.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;

import java.time.LocalDateTime;

public class SidUtil {


    public static String normalSid(){
        return IdUtil.getSnowflake(1,1).nextIdStr();
    }


    /**
     * Created by zz at 2021/8/10 下午 8:10
     * Descr: 兑换订单号
     */
    public static String exchangeTradeNo() {
        return "E"+ DateUtil.format(LocalDateTime.now(), "yyMMddHHmm")
                + (System.currentTimeMillis()+"").substring(5);
    }

    /**
     * Created by zz at 2021/8/10 下午 8:16
     * Descr:  生成开箱订单号
     */
    public static String boxTradeNo() {
        return "B"+ DateUtil.format(LocalDateTime.now(), "yyMMddHHmm")
                + (System.currentTimeMillis()+"").substring(5);
    }

    public static String deliveryTradeNo() {
        return "T"+ DateUtil.format(LocalDateTime.now(), "yyMMddHHmm")
                + (System.currentTimeMillis()+"").substring(5);
    }

}
