package com.fancia.backend.interestgroup

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
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
    private val jacksonMapper: JsonMapper,
    private val wiremock: WireMockContainer
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }
    test("should create a new interest group") {
        val mockResponse = mapOf(
            "content" to listOf(
                mapOf(
                    "name" to "good"
                )
            ),
            "totalElements" to 1,
            "totalPages" to 1,
            "size" to 20,
            "number" to 0
        )
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .withQueryParam("search", equalTo("good"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jacksonMapper.writeValueAsString(
                                mockResponse
                            )
                        )
                )
        )
        val testUserId = UUID.randomUUID()
        val response = mockMvc
            .post("/api/interestGroups") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to "testInterestGroup",
                    "description" to "string",
                    "tags" to listOf("good"),
                    "createdBy" to UUID.randomUUID().toString()
                )
                content = jacksonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testInterestGroup"))
                jsonPath("$.id", `is`(notNullValue()))
            }
        val createdGroup = response.toInterestGroup(jacksonMapper)
        val found = interestGroupRepository.findByIdOrNull(createdGroup.id!!)
        createdGroup shouldBe found
    }


    test("should list interestGroups") {
        mockMvc
            .get("/api/interestGroups?tags=good&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testInterestGroup"))
                jsonPath("$.content[0].tags[0]", `is`("good"))
            }
    }

    test("should not list interestGroups because of wrong tag") {
        mockMvc
            .get("/api/interestGroups?tags=bad&page=0&size=3") {
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

private fun ResultActionsDsl.toInterestGroup(objectMapper: JsonMapper): InterestGroup =
    andReturn()
        .response
        .contentAsString
        .let { objectMapper.readValue(it, object : TypeReference<InterestGroup>() {}) }