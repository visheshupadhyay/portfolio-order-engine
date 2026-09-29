package com.vishesh.orderengine.config;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import io.swagger.v3.oas.models.OpenAPI;

/*
 * Documentation-contract tests: protect the generated OpenAPI JSON from
 * accidentally losing client-facing metadata, security requirements, status
 * codes, or response schemas during controller refactoring.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OpenApiConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OpenAPI openAPI;

	@Test
	public void providesOrderEngineApiMetadata() {
		assertEquals("Order Engine API", openAPI.getInfo().getTitle());
		assertEquals("1.0", openAPI.getInfo().getVersion());
	}

	@Test
	public void documentsFindOrderOperation() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags[0].name").value("Orders"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}']['get']['summary']")
						.value("Find an order by ID"));
	}

	@Test
	public void documentsRemainingOrderOperations() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders']['get']['summary']")
						.value("List orders"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['summary']")
						.value("Create an order"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}/pay']['post']['summary']")
						.value("Pay an order"));
	}

	@Test
	public void documentsCreateOrderResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['responses']['201']['description']")
						.value("Order created"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['responses']['400']['description']")
						.value("Invalid order request"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['responses']['409']['description']")
						.value("Order ID already exists"));
	}

	@Test
	public void documentsCreateOrderErrorBody() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['responses']['400']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['post']['responses']['409']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"));
	}

	@Test
	public void documentsBasicAuthenticationForOrderEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['components']['securitySchemes']['basicAuth']['type']")
						.value("http"))
				.andExpect(jsonPath(
						"$['components']['securitySchemes']['basicAuth']['scheme']")
						.value("basic"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}']['get']['security'][0]['basicAuth']")
						.exists());
	}

	@Test
	public void documentsFindOrderResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}']['get']['responses']['200']['description']")
						.value("Order found"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}']['get']['responses']['404']['description']")
						.value("Order not found"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}']['get']['responses']['404']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"));
	}

	@Test
	public void documentsListOrdersResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders']['get']['responses']['200']['description']")
						.value("Orders returned"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['get']['responses']['200']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/OrderPageResponse"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['get']['responses']['400']['description']")
						.value("Invalid list query parameters"))
				.andExpect(jsonPath(
						"$['paths']['/orders']['get']['responses']['400']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"));
	}

	@Test
	public void documentsPayOrderResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}/pay']['post']['responses']['200']['description']")
						.value("Order paid or already paid"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}/pay']['post']['responses']['200']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/OrderResponse"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}/pay']['post']['responses']['404']['description']")
						.value("Order not found"))
				.andExpect(jsonPath(
						"$['paths']['/orders/{id}/pay']['post']['responses']['404']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"));
	}

	@Test
	public void documentsCursorOrderListOperation() throws Exception {
		// Generated JSON is the client contract; these paths catch accidental OpenAPI annotation regressions.
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$['paths']['/orders/cursor']['get']['summary']")
						.value("List orders using cursor pagination"))
				.andExpect(jsonPath("$['paths']['/orders/cursor']['get']['responses']['200']['description']")
						.value("Cursor batch returned"))
				.andExpect(jsonPath("$['paths']['/orders/cursor']['get']['responses']['400']['description']")
						.value("Invalid cursor query parameters"))
				.andExpect(jsonPath(
						"$['paths']['/orders/cursor']['get']['responses']['400']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/ApiError"))
				.andExpect(jsonPath(
						"$['paths']['/orders/cursor']['get']['responses']['200']"
								+ "['content']['application/json']['schema']['$ref']")
						.value("#/components/schemas/OrderCursorPageResponse"));
	}

}
