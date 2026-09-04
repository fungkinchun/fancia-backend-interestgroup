package com.fancia.backend.interestgroup.external

import com.fancia.backend.interestgroup.config.FeignConfig
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.user.core.dto.ProfileResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(
    name = "user-service",
    path = "/api",
    configuration = [FeignConfig::class],
)
interface UserServiceClient {
    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: UUID): ProfileResponse

    @PostMapping("/blocked")
    fun block(@RequestBody request: CreateBlockedResourceRequest): BlockedResourceResponse
}
