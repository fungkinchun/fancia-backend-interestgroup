package com.fancia.backend.interestgroup

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [InterestGroupApplication::class])
@AutoConfigureMockMvc
@Testcontainers
class InterestGroupControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val interestGroupRepository: InterestGroupRepository,
    private var objectMapper: JsonMapper
) : FunSpec({
    test("should create a new interest group") {
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
                content = objectMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testInterestGroup"))
                jsonPath("$.id", `is`(notNullValue()))
            }
        val createdGroup = response.toInterestGroup(objectMapper)
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
}) {
    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
    }
}

private fun ResultActionsDsl.toInterestGroup(objectMapper: JsonMapper): InterestGroup =
    andReturn()
        .response
        .contentAsString
        .let { objectMapper.readValue(it, object : TypeReference<InterestGroup>() {}) }