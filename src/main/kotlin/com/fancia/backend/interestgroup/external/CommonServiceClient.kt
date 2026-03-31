package com.fancia.backend.interestgroup.external

import com.fancia.backend.shared.common.core.dto.TagsResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "common-service", path = "/common/api")
interface CommonServiceClient {
    @GetMapping("/tags")
    fun getTags(@RequestParam("search") search: Set<String>): TagsResponse
}