package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.ReportService
import com.fancia.backend.shared.common.moderation.core.dto.CreateReportRequest
import com.fancia.backend.shared.common.moderation.core.dto.ReportResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Report interest groups")
@SecurityRequirement(name = "bearerAuth")
class ReportController(
    private val reportService: ReportService,
) {
    @PostMapping
    @Operation(summary = "Submit an interest group report")
    fun create(
        @RequestBody @Valid request: CreateReportRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ReportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(request, jwt))
}
