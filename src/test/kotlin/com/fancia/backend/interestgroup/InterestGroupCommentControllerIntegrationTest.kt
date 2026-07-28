package com.fancia.backend.interestgroup

import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [InterestGroupApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class InterestGroupCommentControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val interestGroupRepository: InterestGroupRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    beforeEach {
        resetAllRequests()
    }

    fun createGroupViaApi(userId: UUID): UUID {
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to emptyList<Any>(),
                                    "totalElements" to 0,
                                    "totalPages" to 0,
                                    "size" to 20,
                                    "number" to 0,
                                )
                            )
                        )
                )
        )
        val responseBody = mockMvc
            .post("/api/interest-groups") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Comment Test Group",
                        "description" to "Group for comment integration tests",
                        "tags" to emptyList<Any>(),
                        "links" to emptyList<Any>(),
                        "createdBy" to userId.toString(),
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(notNullValue()))
            }
            .andReturn()
            .response
            .contentAsString

        return UUID.fromString(jsonMapper.readTree(responseBody).get("id").asText())
    }

    test("should forward group wall comment creation to common-internal") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val commentId = UUID.randomUUID()
        val commonResponse = CommentResponse(
            id = commentId,
            targetId = groupId,
            resourceId = groupId,
            authorUserId = userId,
            body = "Hello group",
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )

        mockMvc
            .post("/api/interest-groups/$groupId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "targetId" to groupId.toString(),
                        "resourceId" to groupId.toString(),
                        "body" to "Hello group",
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(commentId.toString()))
                jsonPath("$.targetId", `is`(groupId.toString()))
                jsonPath("$.resourceId", `is`(groupId.toString()))
            }

        verify(
            postRequestedFor(urlPathEqualTo("/internal/comments"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(groupId.toString())))
                .withRequestBody(matchingJsonPath("$.resourceId", equalTo(groupId.toString())))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Hello group"))),
        )
    }

    test("should list group wall comments with targetId defaulting to groupId") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(groupId.toString()))
                .withQueryParam("resourceId", equalTo(groupId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/interest-groups/$groupId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(groupId.toString()))
                .withQueryParam("resourceId", equalTo(groupId.toString())),
        )
    }

    test("should list post comments when resourceId is post id") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val postId = UUID.randomUUID()
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(postId.toString()))
                .withQueryParam("resourceId", equalTo(postId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/interest-groups/$groupId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                param("resourceId", postId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(postId.toString()))
                .withQueryParam("resourceId", equalTo(postId.toString())),
        )
    }

    test("should list replies when targetId is parent comment id") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val parentCommentId = UUID.randomUUID()
        val pageResponse = mapOf(
            "content" to emptyList<Any>(),
            "totalElements" to 0,
            "totalPages" to 0,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(parentCommentId.toString()))
                .withQueryParam("resourceId", equalTo(groupId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(pageResponse))
                )
        )

        mockMvc
            .get("/api/interest-groups/$groupId/comments") {
                with(jwt().jwt { it.claim("userId", userId) })
                param("targetId", parentCommentId.toString())
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        verify(
            getRequestedFor(urlPathEqualTo("/internal/comments"))
                .withQueryParam("targetId", equalTo(parentCommentId.toString()))
                .withQueryParam("resourceId", equalTo(groupId.toString())),
        )
    }

    test("should forward like to common-internal with resourceId query param") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = postId,
            resourceId = postId,
            authorUserId = userId,
            body = "Post comment",
            createdAt = null,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments/$commentId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(comment))
                )
        )
        stubFor(
            post(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204))
        )

        mockMvc
            .post("/api/interest-groups/$groupId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
                param("resourceId", postId.toString())
            }
            .andExpect { status { isNoContent() } }

        verify(postRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    test("should forward unlike to common-internal") {
        val userId = UUID.randomUUID()
        val groupId = createGroupViaApi(userId)
        val commentId = UUID.randomUUID()
        val comment = CommentResponse(
            id = commentId,
            targetId = groupId,
            resourceId = groupId,
            authorUserId = userId,
            body = "Wall comment",
            createdAt = null,
        )
        stubFor(
            get(urlPathEqualTo("/internal/comments/$commentId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(comment))
                )
        )
        stubFor(
            delete(urlPathEqualTo("/internal/comments/$commentId/likes"))
                .willReturn(aResponse().withStatus(204))
        )

        mockMvc
            .delete("/api/interest-groups/$groupId/comments/$commentId/likes") {
                with(jwt().jwt { it.claim("userId", userId) })
            }
            .andExpect { status { isNoContent() } }

        verify(deleteRequestedFor(urlPathEqualTo("/internal/comments/$commentId/likes")))
    }

    afterSpec {
        interestGroupRepository.deleteAll()
    }
})
