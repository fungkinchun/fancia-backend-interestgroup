package com.fancia.backend.interestgroup

import com.fancia.backend.interestgroup.core.entity.InterestGroup
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembership
import com.fancia.backend.interestgroup.core.entity.InterestGroupMembershipId
import com.fancia.backend.interestgroup.core.repository.InterestGroupMembershipRepository
import com.fancia.backend.interestgroup.core.repository.InterestGroupRepository
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.interestgroup.core.enums.MembershipStatus
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [InterestGroupApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class InterestGroupMembershipControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val interestGroupRepository: InterestGroupRepository,
    private val interestGroupMembershipRepository: InterestGroupMembershipRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    fun stubUser(userId: UUID, showGroups: Boolean?) {
        // ProfileResponse: non-null groupsCount = visible; omit = hidden.
        // null showGroups arg → default visible (legacy privacy default).
        val body = mutableMapOf<String, Any>(
            "id" to userId.toString(),
            "visibility" to "PUBLIC",
        )
        if (showGroups != false) {
            body["groupsCount"] = 0
        }
        stubFor(
            get(urlPathEqualTo("/api/users/$userId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(body)),
                ),
        )
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    test("should hide another user's group memberships when showGroups privacy is disabled") {
        val ownerId = UUID.randomUUID()
        val viewerId = UUID.randomUUID()
        stubUser(ownerId, showGroups = false)

        val group = interestGroupRepository.save(
            InterestGroup().apply {
                name = "Hidden group"
                slug = "hidden-group"
                description = "Test group"
                createdBy = ownerId
            },
        )
        interestGroupMembershipRepository.save(
            InterestGroupMembership().apply {
                id = InterestGroupMembershipId(group.id!!, ownerId)
                role = InterestGroupRole.ADMIN
                status = MembershipStatus.ACCEPTED
                interestGroup = group
            },
        )

        mockMvc
            .get("/api/interest-groups/users/$ownerId/memberships?page=0&size=10") {
                with(jwtFor(viewerId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", org.hamcrest.CoreMatchers.`is`(0))
            }
    }

    test("should show another user's group memberships when showGroups privacy is unset") {
        val ownerId = UUID.randomUUID()
        val viewerId = UUID.randomUUID()
        stubUser(ownerId, showGroups = null)

        val group = interestGroupRepository.save(
            InterestGroup().apply {
                name = "Visible group"
                slug = "visible-group"
                description = "Test group"
                createdBy = ownerId
            },
        )
        interestGroupMembershipRepository.save(
            InterestGroupMembership().apply {
                id = InterestGroupMembershipId(group.id!!, ownerId)
                role = InterestGroupRole.ADMIN
                status = MembershipStatus.ACCEPTED
                interestGroup = group
            },
        )

        mockMvc
            .get("/api/interest-groups/users/$ownerId/memberships?page=0&size=10") {
                with(jwtFor(viewerId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", org.hamcrest.CoreMatchers.`is`(1))
            }
    }

    test("should list memberships for the owner even when showGroups privacy is disabled") {
        val ownerId = UUID.randomUUID()
        stubUser(ownerId, showGroups = false)

        val group = interestGroupRepository.save(
            InterestGroup().apply {
                name = "Owner group"
                slug = "owner-group"
                description = "Test group"
                createdBy = ownerId
            },
        )
        interestGroupMembershipRepository.save(
            InterestGroupMembership().apply {
                id = InterestGroupMembershipId(group.id!!, ownerId)
                role = InterestGroupRole.ADMIN
                status = MembershipStatus.ACCEPTED
                interestGroup = group
            },
        )

        mockMvc
            .get("/api/interest-groups/users/$ownerId/memberships?page=0&size=10") {
                with(jwtFor(ownerId))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", org.hamcrest.CoreMatchers.`is`(1))
            }
    }
})
