package com.fancia.backend.interestgroup.core.controller

import com.fancia.backend.interestgroup.core.service.InterestGroupPostService
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/interest-groups/{groupId}/posts")
@Tag(name = "Interest Group Posts", description = "Posts on interest groups")
@SecurityRequirement(name = "bearerAuth")
class InterestGroupPostController(
    private val interestGroupPostService: InterestGroupPostService,
) {
    @Operation(summary = "Create post on interest group")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "403", description = "Not allowed to post on this group"),
            ApiResponse(responseCode = "404", description = "Group not found"),
        ]
    )
    @PostMapping
    fun createPost(
        @PathVariable @Parameter(description = "Interest group id") groupId: UUID,
        @RequestBody @Valid request: CreatePostBody,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        val post = interestGroupPostService.create(groupId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @Operation(summary = "List posts on interest group")
    @GetMapping
    fun listPosts(
        @PathVariable groupId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Filter by post kind (TEXT or POLL)")
        kind: PostKind?,
        @RequestParam(defaultValue = "false")
        @Parameter(description = "When true, only open poll posts")
        openOnly: Boolean,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(interestGroupPostService.list(groupId, kind, openOnly, pageable))
    }

    @Operation(summary = "Get post on interest group")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable groupId: UUID,
        @PathVariable postId: UUID,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(interestGroupPostService.get(groupId, postId))
    }

    @Operation(summary = "Update post")
    @PutMapping("/{postId}")
    fun updatePost(
        @PathVariable groupId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: UpdatePostRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(interestGroupPostService.update(groupId, postId, request, jwt))
    }

    @Operation(summary = "Like post")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @PathVariable groupId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        interestGroupPostService.like(groupId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @PathVariable groupId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        interestGroupPostService.unlike(groupId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Vote on poll post")
    @PostMapping("/{postId}/votes")
    fun voteOnPost(
        @PathVariable groupId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: CastPollVoteRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(interestGroupPostService.vote(groupId, postId, request, jwt))
    }
}
