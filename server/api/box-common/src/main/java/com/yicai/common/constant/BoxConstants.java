package com.yicai.common.constant;

/**
 * 通用常量信息
 *
 * @author ruoyi
 */
public class BoxConstants
{
    /**
     * 上架状态 -- 商品未上架
     */
    public static final long NO_SHELF = 0;

    /**
     * 上架状态 -- 商品已上架
     */
    public static final long ON_SHELF = 1;

    /**
     * 上架状态 -- 已下架
     */
    public static final long OFF_SHELF = -1;

    /**
     * 钱包日志变更类型 -- 加
     */
    public static final Integer CHANGE_TYPE_ADD = 1;

    /**
     * 钱包日志变更类型 -- 减
     */
    public static final Integer CHANGE_TYPE_SUBTRACT = -1;

    /**
     * 钱包日志钱包类型 -- 金币
     */
    public static final  Long WALLET_TYPE_COIN = 1L;

    /**
     * 钱包日志钱包类型 -- 积分
     */
    public static final  Long WALLET_TYPE_POINT = 2L;



}
