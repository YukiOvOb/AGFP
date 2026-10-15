package sg.edu.nus.serms.approval.api;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;


/** Prevents Jackson's default coercion of fractional numbers or strings into version integers. */
public class Int64VersionDeserializer extends StdDeserializer<Long> {
  public Int64VersionDeserializer() { super(Long.class); }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
      return (Long) context.handleUnexpectedToken(Long.class, parser);
    return parser.getLongValue();
  }
}
