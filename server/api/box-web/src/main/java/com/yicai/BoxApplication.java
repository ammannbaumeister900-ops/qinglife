package com.yicai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动程序
 *
 * @author ruoyi
 */

@SpringBootApplication
public class BoxApplication
{
    public static void main(String[] args)
    {
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(BoxApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  轻生活管理系统启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
