package com.yicai.web.integration;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Same real HTTP contract with production security/configuration/schema guards enabled. */
@ActiveProfiles(value="prod", inheritProfiles=false)
@TestPropertySource(properties="ruoyi.imagePath=https://example.invalid/images/")
class ProductionLoginHttpIT extends LoginHttpIT {
}
