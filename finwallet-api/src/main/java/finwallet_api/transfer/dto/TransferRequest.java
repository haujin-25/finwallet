package finwallet_api.transfer.dto;

import jakarta.validation.constraints.Email;
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

@JsonDeserialize(using = TransferRequest.Deserializer.class)
public record TransferRequest(
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Recipient email must be valid")
        @Size(max = 255, message = "Recipient email must be at most 255 characters")
        String recipientEmail,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        Long amount,

        @CodePointLength(max = 500, message = "Description must be at most 500 characters")
        @Pattern(regexp = "[^\\x00]*", message = "Description must not contain a NUL character")
        String description,

        @NotBlank(message = "Idempotency key is required")
        @Size(max = 255, message = "Idempotency key must be at most 255 characters")
        @Pattern(regexp = "[A-Za-z0-9._:-]+",
                message = "Idempotency key may contain only letters, digits, periods, underscores, colons, and hyphens")
        String idempotencyKey
) {
    // Keep strict JSON token types local to transfers. Null and missing values
    // go through Bean Validation so required-field errors retain field names.
    static final class Deserializer extends ValueDeserializer<TransferRequest> {
        @Override
        public TransferRequest deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode body = context.readTree(parser);
            if (!body.isObject()) {
                return context.reportInputMismatch(this, "Expected a JSON object");
            }

            JsonNode amount = body.path("amount");
            if (!amount.isMissingNode() && !amount.isNull()
                    && (!amount.isIntegralNumber() || !amount.canConvertToLong())) {
                return context.reportInputMismatch(this, "Expected an integer amount in minor units");
            }

            return new TransferRequest(
                    readString(body, "recipientEmail", context),
                    amount.isIntegralNumber() ? amount.asLong() : null,
                    readString(body, "description", context),
                    readString(body, "idempotencyKey", context));
        }

        private String readString(JsonNode body, String field, DeserializationContext context) {
            JsonNode value = body.path(field);
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            if (!value.isString()) {
                return context.reportInputMismatch(this, "Expected a string for %s", field);
            }
            return value.asString();
        }
    }
}
