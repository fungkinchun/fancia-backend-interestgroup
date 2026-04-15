package com.fancia.backend.interestgroup

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestConfig::class)
class InterestGroupApplicationTests {
    @Test
    fun contextLoads() {
    }
}
