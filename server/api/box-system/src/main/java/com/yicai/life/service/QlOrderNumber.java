package com.yicai.life.service;

import java.text.SimpleDateFormat;
import java.util.*;

/** Existing human-readable order number format, shared by miniapp and backend order creation. */
public final class QlOrderNumber {
    private QlOrderNumber() {}
    public static String create(Date now) {
        return "QL"+new SimpleDateFormat("yyyyMMddHHmmss").format(now)+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT);
    }
}
