package sg.edu.nus.serms.shared.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Versions are JSON int64 integers: never truncate fractions or coerce strings. */
public class StrictVersionDeserializer extends StdDeserializer<Long> {
  public StrictVersionDeserializer() { super(Long.class); }
  @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
      return (Long) context.handleUnexpectedToken(Long.class,parser);
    return parser.getLongValue();
  }
}
