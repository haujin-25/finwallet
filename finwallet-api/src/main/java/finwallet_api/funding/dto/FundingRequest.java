package finwallet_api.funding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = FundingRequest.Deserializer.class)
public record FundingRequest(
        @NotNull @Positive Long amount,
        @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
        @CodePointLength(max = 500) @Pattern(regexp = "[^\\x00]*") String description
) {
    static final class Deserializer extends ValueDeserializer<FundingRequest> {
        @Override
        public FundingRequest deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode body = context.readTree(parser);
            if (!body.isObject() || body.propertyNames().stream()
                    .anyMatch(field -> !field.equals("amount") && !field.equals("idempotencyKey")
                            && !field.equals("description"))) {
                return context.reportInputMismatch(this, "Invalid funding request fields");
            }
            JsonNode amount = body.path("amount");
            JsonNode key = body.path("idempotencyKey");
            JsonNode description = body.path("description");
            if ((!amount.isMissingNode() && !amount.isNull() &&
                    (!amount.isIntegralNumber() || !amount.canConvertToLong()))
                    || !isOptionalString(key) || !isOptionalString(description)) {
                return context.reportInputMismatch(this, "Invalid funding request types");
            }
            return new FundingRequest(amount.isIntegralNumber() ? amount.asLong() : null,
                    key.isString() ? key.asString() : null, description.isString() ? description.asString() : null);
        }

        private boolean isOptionalString(JsonNode value) {
            return value.isMissingNode() || value.isNull() || value.isString();
        }
    }
}
