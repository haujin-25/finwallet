package finwallet_api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = WalletAdministrationRequest.Deserializer.class)
public record WalletAdministrationRequest(
        @NotBlank
        @CodePointLength(max = 500)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*", message = "must be a single line of printable text")
        String reason
) {
    static final class Deserializer extends ValueDeserializer<WalletAdministrationRequest> {
        @Override
        public WalletAdministrationRequest deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode body = context.readTree(parser);
            if (!body.isObject() || body.propertyNames().stream().anyMatch(field -> !field.equals("reason"))) {
                return context.reportInputMismatch(this, "Expected a wallet administration reason");
            }
            JsonNode reason = body.path("reason");
            if (reason.isMissingNode() || reason.isNull()) {
                return new WalletAdministrationRequest(null);
            }
            if (!reason.isString()) {
                return context.reportInputMismatch(this, "Expected a string reason");
            }
            return new WalletAdministrationRequest(reason.asString());
        }
    }
}
