package dev.mytechprofile.gateway.connector.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import dev.mytechprofile.gateway.connector.ConnectionBearerTokenResolver;
import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;

/**
 * REST connector backed by OpenAPI operation discovery.
 *
 * <p>{@link #discover} parses the OpenAPI document into both public operation
 * descriptors and connector-private HTTP execution plans. Execution is generic:
 * it uses the discovered method, path, parameter locations, and JSON request body.
 */
public class OpenApiConnector implements Connector {

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final ConnectionBearerTokenResolver bearerTokenResolver;
    private final ConcurrentHashMap<String, Map<String, HttpOperation>> operationsByConnection =
            new ConcurrentHashMap<>();

    public OpenApiConnector(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            org.springframework.beans.factory.ObjectProvider<ConnectionBearerTokenResolver> bearerTokenResolver) {
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
        this.bearerTokenResolver = bearerTokenResolver.getIfAvailable(() -> connection -> null);
    }

    @Override
    public String type() {
        return "openapi";
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return ConnectorCapabilities.discoverable();
    }

    @Override
    public ConnectionDefinition configure(
            ConnectionDefinition connection, JsonNode configuration) {
        if (connection.baseUri() == null || "about".equalsIgnoreCase(connection.baseUri().getScheme())) {
            throw new IllegalArgumentException(
                    "OpenAPI connection '%s' requires base-url".formatted(connection.name()));
        }
        JsonNode authentication = configuration.path("authentication");
        String type = authentication.path("type").asString("none");
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> {
                return connection;
            }
            case "static-bearer" -> requireText(
                    connection.name(), authentication, "token", type);
            case "oauth2-client-credentials" -> {
                requireText(connection.name(), authentication, "tokenUri", type);
                requireText(connection.name(), authentication, "clientId", type);
                requireText(connection.name(), authentication, "clientSecret", type);
            }
            default -> throw new IllegalArgumentException(
                    "OpenAPI connection '%s' has unsupported authentication type '%s'"
                            .formatted(connection.name(), type));
        }
        return connection;
    }

    @Override
    public List<OperationDescriptor> discover(ConnectionDefinition connection) {
        OpenAPI api = readOpenApi(connection.specificationLocation());
        if (api.getPaths() == null || api.getPaths().isEmpty()) {
            operationsByConnection.put(connection.name(), Map.of());
            return List.of();
        }
        List<OperationDescriptor> descriptors = new ArrayList<>();
        Map<String, HttpOperation> executionPlans = new LinkedHashMap<>();
        api.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((method, operation) -> {
                    if (operation.getOperationId() != null && !operation.getOperationId().isBlank()) {
                        String operationId = operation.getOperationId();
                        if (executionPlans.containsKey(operationId)) {
                            throw new IllegalArgumentException(
                                    "duplicate OpenAPI operationId: " + operationId);
                        }
                        List<Parameter> parameters = mergeParameters(item, operation);
                        executionPlans.put(
                                operationId,
                                toExecutionPlan(path, method, parameters, operation));
                        descriptors.add(toDescriptor(method, operation, parameters));
                    }
                }));
        operationsByConnection.put(connection.name(), Map.copyOf(executionPlans));
        return List.copyOf(descriptors);
    }

    @Override
    public ExecutionResult execute(OperationInvocation invocation, ToolExecutionContext context) {
        HttpOperation operation = operationsByConnection
                .getOrDefault(invocation.connection().name(), Map.of())
                .get(invocation.operationId());
        if (operation == null) {
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.NOT_FOUND,
                    invocation.operationId(),
                    "unknown operation");
        }
        try {
            ConnectionDefinition connection = invocation.connection();
            Map<String, Object> arguments = invocation.arguments();
            Map<String, Object> pathVariables = new LinkedHashMap<>();
            Map<String, Object> body = new LinkedHashMap<>();
            HttpHeaders headers = new HttpHeaders();
            UriComponentsBuilder uri = UriComponentsBuilder
                    .fromUri(connection.baseUri())
                    .path(operation.path());

            for (HttpParameter parameter : operation.parameters()) {
                Object value = arguments.get(parameter.name());
                if (value == null) {
                    continue;
                }
                switch (parameter.location()) {
                    case "path" -> pathVariables.put(parameter.name(), value);
                    case "query" -> uri.queryParam(parameter.name(), value);
                    case "header" -> headers.add(parameter.name(), String.valueOf(value));
                    case "cookie" -> headers.add(HttpHeaders.COOKIE, parameter.name() + "=" + value);
                    default -> {
                        // OpenAPI parser may expose extension parameter locations; ignore safely.
                    }
                }
            }
            if (operation.hasJsonBody()) {
                Set<String> parameterNames = operation.parameters().stream()
                        .map(HttpParameter::name)
                        .collect(java.util.stream.Collectors.toSet());
                if (operation.bodyFields().isEmpty()) {
                    arguments.forEach((field, value) -> {
                        if (!parameterNames.contains(field)) {
                            body.put(field, value);
                        }
                    });
                } else {
                    operation.bodyFields().forEach(field -> {
                        if (arguments.containsKey(field)) {
                            body.put(field, arguments.get(field));
                        }
                    });
                }
            }

            URI requestUri = uri.buildAndExpand(pathVariables).encode().toUri();
            RestClient.RequestBodySpec request = clientFor(connection)
                    .method(operation.method())
                    .uri(requestUri)
                    .headers(outbound -> outbound.addAll(headers));
            if (!body.isEmpty()) {
                request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
            }
            ResponseEntity<JsonNode> response = request
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode payload = response.getBody();
            return new ExecutionResult.Success(
                    payload == null ? objectMapper.createObjectNode() : payload,
                    response.getStatusCode().value());
        } catch (RestClientResponseException ex) {
            return mapHttpFailure(invocation.operationId(), ex);
        } catch (RestClientException ex) {
            return mapTransportFailure(invocation.operationId(), ex);
        }
    }

    private RestClient clientFor(ConnectionDefinition connection) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (connection.connectTimeout() != null) {
            requestFactory.setConnectTimeout(connection.connectTimeout());
        }
        if (connection.readTimeout() != null) {
            requestFactory.setReadTimeout(connection.readTimeout());
        }
        RestClient.Builder builder = restClientBuilder
                .clone()
                .requestFactory(requestFactory)
                .baseUrl(connection.baseUri().toString());
        String token = bearerTokenResolver.resolve(connection.name());
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private ExecutionResult mapHttpFailure(String operationId, RestClientResponseException ex) {
        return DownstreamFailureMapper.fromHttp(operationId, ex.getStatusCode().value(), ex.getStatusText());
    }

    private ExecutionResult mapTransportFailure(String operationId, RestClientException ex) {
        return DownstreamFailureMapper.fromTransport(operationId, ex.getMessage());
    }

    private OpenAPI readOpenApi(String specificationLocation) {
        if (specificationLocation == null || specificationLocation.isBlank()) {
            throw new IllegalArgumentException("openapi connection requires specification");
        }
        try {
            Resource resource = new DefaultResourceLoader().getResource(specificationLocation);
            if (!resource.exists()) {
                throw new IllegalArgumentException("cannot read specification: " + specificationLocation);
            }
            String contents;
            try (InputStream in = resource.getInputStream()) {
                contents = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setResolveFully(true);
            options.setFlatten(false);
            SwaggerParseResult result = new OpenAPIV3Parser().readContents(contents, null, options);
            OpenAPI api = result.getOpenAPI();
            if (api == null) {
                throw new IllegalArgumentException(
                        "cannot parse specification: " + specificationLocation
                                + " messages=" + result.getMessages());
            }
            return api;
        } catch (IOException ex) {
            throw new IllegalArgumentException("cannot read specification: " + specificationLocation, ex);
        }
    }

    private void requireText(
            String connectionName, JsonNode authentication, String field, String type) {
        if (authentication.path(field).asString("").isBlank()) {
            throw new IllegalArgumentException(
                    "OpenAPI connection '%s' authentication '%s' requires %s"
                            .formatted(connectionName, type, field));
        }
    }

    private OperationDescriptor toDescriptor(
            PathItem.HttpMethod method, Operation operation, List<Parameter> parameters) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Parameter parameter : parameters) {
            properties.set(parameter.getName(), convertSchema(parameter.getSchema()));
            if (Boolean.TRUE.equals(parameter.getRequired())) {
                required.add(parameter.getName());
            }
        }
        if (operation.getRequestBody() != null
                && operation.getRequestBody().getContent() != null) {
            MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
            if (mediaType != null && mediaType.getSchema() != null) {
                Schema<?> body = mediaType.getSchema();
                if (body.getProperties() != null) {
                    body.getProperties().forEach((name, nested) ->
                            properties.set(name, convertSchema(nested)));
                }
                if (body.getRequired() != null) {
                    body.getRequired().forEach(required::add);
                }
            }
        }
        if (required.isEmpty()) {
            schema.remove("required");
        }
        return new OperationDescriptor(
                operation.getOperationId(),
                operation.getSummary() == null ? operation.getOperationId() : operation.getSummary(),
                schema,
                null,
                isReadMethod(method) ? OperationAccess.READ : OperationAccess.WRITE);
    }

    private List<Parameter> mergeParameters(PathItem item, Operation operation) {
        Map<String, Parameter> merged = new LinkedHashMap<>();
        if (item.getParameters() != null) {
            item.getParameters().forEach(parameter ->
                    merged.put(parameter.getIn() + ':' + parameter.getName(), parameter));
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter ->
                    merged.put(parameter.getIn() + ':' + parameter.getName(), parameter));
        }
        return List.copyOf(merged.values());
    }

    private HttpOperation toExecutionPlan(
            String path,
            PathItem.HttpMethod method,
            List<Parameter> parameters,
            Operation operation) {
        List<HttpParameter> httpParameters = parameters.stream()
                .map(parameter -> new HttpParameter(parameter.getName(), parameter.getIn()))
                .toList();
        Set<String> bodyFields = new LinkedHashSet<>();
        boolean hasJsonBody = false;
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
            if (mediaType != null) {
                hasJsonBody = true;
                if (mediaType.getSchema() != null && mediaType.getSchema().getProperties() != null) {
                    bodyFields.addAll(mediaType.getSchema().getProperties().keySet());
                }
            }
        }
        return new HttpOperation(
                HttpMethod.valueOf(method.name()),
                path,
                httpParameters,
                Set.copyOf(bodyFields),
                hasJsonBody);
    }

    private boolean isReadMethod(PathItem.HttpMethod method) {
        return method == PathItem.HttpMethod.GET
                || method == PathItem.HttpMethod.HEAD
                || method == PathItem.HttpMethod.OPTIONS
                || method == PathItem.HttpMethod.TRACE;
    }

    private JsonNode convertSchema(Schema<?> schema) {
        ObjectNode node = objectMapper.createObjectNode();
        if (schema == null) {
            node.put("type", "string");
            return node;
        }
        if (schema.getType() != null) {
            node.put("type", schema.getType());
        } else {
            node.put("type", "string");
        }
        if (schema.getDescription() != null) {
            node.put("description", schema.getDescription());
        }
        if (schema.getMinimum() != null) {
            node.put("minimum", schema.getMinimum().doubleValue());
        }
        if (schema.getMaximum() != null) {
            node.put("maximum", schema.getMaximum().doubleValue());
        }
        if (schema.getMinLength() != null) {
            node.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            node.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            node.put("pattern", schema.getPattern());
        }
        if (schema.getMinItems() != null) {
            node.put("minItems", schema.getMinItems());
        }
        if (schema.getMaxItems() != null) {
            node.put("maxItems", schema.getMaxItems());
        }
        if (schema.getUniqueItems() != null) {
            node.put("uniqueItems", schema.getUniqueItems());
        }
        if (schema.getItems() != null) {
            node.set("items", convertSchema(schema.getItems()));
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            ObjectNode properties = node.putObject("properties");
            schema.getProperties().forEach((name, property) ->
                    properties.set(name, convertSchema((Schema<?>) property)));
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                ArrayNode required = node.putArray("required");
                schema.getRequired().forEach(required::add);
            }
            node.put("additionalProperties", false);
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            ArrayNode enumNode = node.putArray("enum");
            schema.getEnum().forEach(value -> enumNode.add(String.valueOf(value)));
        }
        return node;
    }

    private record HttpOperation(
            HttpMethod method,
            String path,
            List<HttpParameter> parameters,
            Set<String> bodyFields,
            boolean hasJsonBody) {}

    private record HttpParameter(String name, String location) {}
}
