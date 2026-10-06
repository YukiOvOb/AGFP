package sg.edu.nus.serms.approval.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Prevents Jackson's default coercion of fractional numbers or strings into version integers. */
public class Int64VersionDeserializer extends StdDeserializer<Long> {
  public Int64VersionDeserializer() { super(Long.class); }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
      return (Long) context.handleUnexpectedToken(Long.class, parser);
    return parser.getLongValue();
  }
}
