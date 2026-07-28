package com.fancia.backend.interestgroup

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.interestgroup.mapper.toEntity
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [InterestGroupApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class InterestGroupControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val interestGroupRepository: InterestGroupRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }

    fun stubCreateTag(name: String): UUID {
        val tagId = UUID.randomUUID()
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "id" to tagId.toString(),
                                            "name" to name,
                                            "type" to "TOPIC",
                                        ),
                                    ),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 1,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
        return tagId
    }

    test("should create a new interest group") {
        stubCreateTag("good")
        val testUserId = UUID.randomUUID()
        val response = mockMvc
            .post("/api/interest-groups") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to "testInterestGroup",
                    "description" to "string",
                    "tags" to listOf(mapOf("name" to "good", "type" to "TOPIC")),
                    "links" to listOf(
                        mapOf(
                            "type" to "WEBSITE",
                            "url" to "https://example.com"
                        ),
                        mapOf(
                            "type" to "INSTAGRAM",
                            "url" to "https://instagram.com/test22"
                        )
                    ),
                    "createdBy" to UUID.randomUUID().toString()
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testInterestGroup"))
                jsonPath("$.id", `is`(notNullValue()))
                jsonPath("$.links.length()", `is`(2))
                jsonPath("$.links[0].type", `is`("WEBSITE"))
                jsonPath("$.links[0].url", `is`("https://example.com"))
            }
        val createdGroup = response.toInterestGroup(jsonMapper)
        val found = interestGroupRepository.findByIdOrNull(createdGroup.id!!)
        found?.id shouldBe createdGroup.id
        found?.links?.size shouldBe 2
        createdGroup.links.size shouldBe 2
    }


    test("should reject interest group when name exceeds max length") {
        val testUserId = UUID.randomUUID()
        val longName = "a".repeat(256)
        mockMvc
            .post("/api/interest-groups") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to longName,
                    "description" to "string",
                    "tags" to emptyList<String>(),
                    "links" to emptyList<Any>(),
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.status", `is`(400))
                jsonPath("$.errorCode", `is`("VALIDATION_ERROR"))
                jsonPath("$.detail", `is`("Validation failed: Interest group name must be at most 255 characters"))
            }
    }

    test("should list interestGroups") {
        val group = interestGroupRepository.findAll().first { it.name == "testInterestGroup" }
        val tagId = group.tags.first()
        mockMvc
            .get("/api/interest-groups?tagIds=$tagId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testInterestGroup"))
                jsonPath("$.content[0].tags[0]", `is`(group.tags.first().toString()))
                jsonPath("$.content[0].links.length()", `is`(2))
            }
    }

    test("should get interest group by id") {
        val group = interestGroupRepository.findAll().first { it.name == "testInterestGroup" }
        mockMvc
            .get("/api/interest-groups/${group.id}") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(group.id.toString()))
                jsonPath("$.name", `is`("testInterestGroup"))
                jsonPath("$.links.length()", `is`(2))
            }
    }

    test("should return 404 when interest group does not exist") {
        mockMvc
            .get("/api/interest-groups/${UUID.randomUUID()}") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode", `is`("INTEREST_GROUP_NOT_FOUND"))
            }
    }

    test("should not list interestGroups because of wrong tag") {
        mockMvc
            .get("/api/interest-groups?tagIds=${UUID.randomUUID()}&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }
    }

    afterSpec {
        interestGroupRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toInterestGroup(jsonMapper: JsonMapper): InterestGroup =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<InterestGroupResponse>() {})
                .toEntity()
        }