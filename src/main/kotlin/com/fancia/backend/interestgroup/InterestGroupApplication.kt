package com.fancia.backend.interestgroup

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@EntityScan(
    basePackages = [
        "com.fancia.backend.interestgroup.core",
        "com.fancia.backend.shared.common.core.entity"
    ]
)
@EnableFeignClients
@SpringBootApplication
class InterestGroupApplication

fun main(args: Array<String>) {
    runApplication<InterestGroupApplication>(*args)
}
