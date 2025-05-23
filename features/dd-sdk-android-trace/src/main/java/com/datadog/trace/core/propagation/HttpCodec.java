package com.datadog.trace.core.propagation;

import static com.datadog.trace.api.TracePropagationStyle.TRACECONTEXT;

import com.datadog.trace.api.Config;
import com.datadog.trace.api.DD128bTraceId;
import com.datadog.trace.api.DD64bTraceId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.TraceConfig;
import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.TagContext;
import com.datadog.trace.core.DDSpanContext;
import com.datadog.trace.core.DDSpanLink;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.datadog.android.trace.internal.compat.function.Supplier;

public class HttpCodec {

  private static final Logger log = LoggerFactory.getLogger(HttpCodec.class);
  // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded
  static final String FORWARDED_KEY = "forwarded";
  static final String FORWARDED_FOR_KEY = "forwarded-for";
  static final String X_FORWARDED_PROTO_KEY = "x-forwarded-proto";
  static final String X_FORWARDED_HOST_KEY = "x-forwarded-host";
  static final String X_FORWARDED_KEY = "x-forwarded";
  static final String X_FORWARDED_FOR_KEY = "x-forwarded-for";
  static final String X_FORWARDED_PORT_KEY = "x-forwarded-port";

  // other headers which may contain real ip
  static final String X_CLIENT_IP_KEY = "x-client-ip";
  static final String TRUE_CLIENT_IP_KEY = "true-client-ip";
  static final String X_CLUSTER_CLIENT_IP_KEY = "x-cluster-client-ip";
  static final String X_REAL_IP_KEY = "x-real-ip";
  static final String USER_AGENT_KEY = "user-agent";
  static final String FASTLY_CLIENT_IP_KEY = "fastly-client-ip";
  static final String CF_CONNECTING_IP_KEY = "cf-connecting-ip";
  static final String CF_CONNECTING_IP_V6_KEY = "cf-connecting-ipv6";

  public static final String RUM_SESSION_ID_KEY = "session_id";

  public interface Injector {
    <C> void inject(
        final DDSpanContext context, final C carrier, final AgentPropagation.Setter<C> setter);
  }

  /** This interface defines propagated context extractor. */
  public interface Extractor {
    /**
     * Extracts a propagated context from the given carrier using the provided getter.
     *
     * @param carrier The carrier containing the propagated context.
     * @param getter The getter used to extract data from the carrier.
     * @param <C> The type of the carrier.
     * @return {@code null} for failed context extraction, a {@link TagContext} instance for partial
     *     context extraction or an {@link ExtractedContext} for complete context extraction.
     */
    <C> TagContext extract(final C carrier, final AgentPropagation.ContextVisitor<C> getter);

    /**
     * Cleans up any thread local resources associated with this extractor.
     *
     * <p>Implementations should override this method if they need to clean up any resources.
     *
     * <p><i>Currently only used from tests.</i>
     */
    default void cleanup() {}
  }

  public static Injector createInjector(
      Config config,
      Set<TracePropagationStyle> styles,
      Map<String, String> invertedBaggageMapping) {
    ArrayList<Injector> injectors =
        new ArrayList<>(createInjectors(config, styles, invertedBaggageMapping).values());
    return new CompoundInjector(injectors);
  }

  public static Map<TracePropagationStyle, Injector> allInjectorsFor(
      Config config, Map<String, String> reverseBaggageMapping) {
    return createInjectors(
        config, EnumSet.allOf(TracePropagationStyle.class), reverseBaggageMapping);
  }

  private static Map<TracePropagationStyle, Injector> createInjectors(
      Config config,
      Set<TracePropagationStyle> propagationStyles,
      Map<String, String> reverseBaggageMapping) {
    EnumMap<TracePropagationStyle, Injector> result = new EnumMap<>(TracePropagationStyle.class);
    for (TracePropagationStyle style : propagationStyles) {
      switch (style) {
        case DATADOG:
          result.put(style, DatadogHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case B3SINGLE:
          result.put(
              style,
              B3HttpCodec.newSingleInjector(config.isTracePropagationStyleB3PaddingEnabled()));
          break;
        case B3MULTI:
          result.put(
              style,
              B3HttpCodec.newMultiInjector(config.isTracePropagationStyleB3PaddingEnabled()));
          break;
        case HAYSTACK:
          result.put(style, HaystackHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case XRAY:
          result.put(style, XRayHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case NONE:
          result.put(style, NoneCodec.INJECTOR);
          break;
        case TRACECONTEXT:
          result.put(style, W3CHttpCodec.newInjector(reverseBaggageMapping));
          break;
        default:
          log.debug("No implementation found to inject propagation style: {}", style);
          break;
      }
    }
    return result;
  }

  public static Extractor createExtractor(
      Config config, Supplier<TraceConfig> traceConfigSupplier) {
    final List<Extractor> extractors = new ArrayList<>();
    for (final TracePropagationStyle style : config.getTracePropagationStylesToExtract()) {
      switch (style) {
        case DATADOG:
          extractors.add(DatadogHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case B3SINGLE:
          extractors.add(B3HttpCodec.newSingleExtractor(config, traceConfigSupplier));
          break;
        case B3MULTI:
          extractors.add(B3HttpCodec.newMultiExtractor(config, traceConfigSupplier));
          break;
        case HAYSTACK:
          extractors.add(HaystackHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case XRAY:
          extractors.add(XRayHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case NONE:
          extractors.add(NoneCodec.EXTRACTOR);
          break;
        case TRACECONTEXT:
          extractors.add(W3CHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        default:
          log.debug("No implementation found to extract propagation style: {}", style);
          break;
      }
    }
    switch (extractors.size()) {
      case 0:
        return StubExtractor.INSTANCE;
      case 1:
        return extractors.get(0);
      default:
        return new CompoundExtractor(extractors, config.isTracePropagationExtractFirst());
    }
  }

  public static class CompoundInjector implements Injector {

    private final List<Injector> injectors;

    public CompoundInjector(final List<Injector> injectors) {
      this.injectors = injectors;
    }

    @Override
    public <C> void inject(
        final DDSpanContext context, final C carrier, final AgentPropagation.Setter<C> setter) {
      log.debug("Inject context {}", context);
      // Update session ide before injecting propagation tags
      final String sessionId = (String) context.getTags().get(RUM_SESSION_ID_KEY);
      if (sessionId != null) {
        context.getPropagationTags().updateRumSessionId(sessionId);
      }

      for (final Injector injector : injectors) {
        injector.inject(context, carrier, setter);
      }
    }
  }

  private static class StubExtractor implements Extractor {
    private static final StubExtractor INSTANCE = new StubExtractor();

    @Override
    public <C> TagContext extract(C carrier, AgentPropagation.ContextVisitor<C> getter) {
      return null;
    }
  }

  public static class CompoundExtractor implements Extractor {
    private final List<Extractor> extractors;
    private final boolean extractFirst;

    public CompoundExtractor(final List<Extractor> extractors, boolean extractFirst) {
      this.extractors = extractors;
      this.extractFirst = extractFirst;
    }

    @Override
    public <C> TagContext extract(
        final C carrier, final AgentPropagation.ContextVisitor<C> getter) {
      ExtractedContext context = null;
      TagContext partialContext = null;
      // Extract and cache all headers in advance
      ExtractionCache<C> extractionCache = new ExtractionCache<>(carrier, getter);

      for (final Extractor extractor : this.extractors) {
        TagContext extracted = extractor.extract(extractionCache, extractionCache);
        // Check if context is valid
        if (extracted instanceof ExtractedContext) {
          ExtractedContext extractedContext = (ExtractedContext) extracted;
          // If no prior valid context, store it as first valid context
          if (context == null) {
            context = extractedContext;
            // Stop extraction if only extracting first valid context and drop everything else
            if (this.extractFirst) {
              break;
            }
          }
          // If another valid context is extracted
          else {
            if (traceIdMatch(context.getTraceId(), extractedContext.getTraceId())) {
              boolean comingFromTraceContext = extracted.getPropagationStyle() == TRACECONTEXT;
              if (comingFromTraceContext) {
                // Propagate newly extracted W3C tracestate to first valid context
                String extractedTracestate =
                    extractedContext.getPropagationTags().getW3CTracestate();
                context.getPropagationTags().updateW3CTracestate(extractedTracestate);
              }
            } else {
              // Terminate extracted context and add it as span link
              context.addTerminatedContextLink(DDSpanLink.from((ExtractedContext) extracted));
              // TODO Note: Other vendor tracestate will be lost here
            }
          }
        }
        // Check if context is at least partial to keep it as first valid partial context found
        else if (extracted != null && partialContext == null) {
          partialContext = extracted;
        }
      }

      if (context != null) {
        log.debug("Extract complete context {}", context);
        return context;
      } else if (partialContext != null) {
        log.debug("Extract incomplete context {}", partialContext);
        return partialContext;
      } else {
        log.debug("Extract no context");
        return null;
      }
    }
  }

  private static class ExtractionCache<C>
      implements AgentPropagation.KeyClassifier,
          AgentPropagation.ContextVisitor<ExtractionCache<?>> {
    /** Cached context key-values (even indexes are header names, odd indexes are header values). */
    private final List<String> keysAndValues;

    public ExtractionCache(C carrier, AgentPropagation.ContextVisitor<C> getter) {
      this.keysAndValues = new ArrayList<>(32);
      getter.forEachKey(carrier, this);
    }

    @Override
    public boolean accept(String key, String value) {
      this.keysAndValues.add(key);
      this.keysAndValues.add(value);
      return true;
    }

    @Override
    public void forEachKey(ExtractionCache<?> carrier, AgentPropagation.KeyClassifier classifier) {
      List<String> keysAndValues = carrier.keysAndValues;
      for (int i = 0; i < keysAndValues.size(); i += 2) {
        classifier.accept(keysAndValues.get(i), keysAndValues.get(i + 1));
      }
    }
  }

  /**
   * Checks if trace identifier matches, even if they are not encoded using the same size (64-bit vs
   * 128-bit).
   *
   * @param a A trace identifier to check.
   * @param b Another trace identifier to check.
   * @return {@code true} if the trace identifiers matches, {@code false} otherwise.
   */
  private static boolean traceIdMatch(DDTraceId a, DDTraceId b) {
    if (a instanceof DD128bTraceId && b instanceof DD128bTraceId
        || a instanceof DD64bTraceId && b instanceof DD64bTraceId) {
      return a.equals(b);
    } else {
      return a.toLong() == b.toLong();
    }
  }

  /** URL encode value */
  static String encode(final String value) {
    String encoded = value;
    try {
      encoded = URLEncoder.encode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.debug("Failed to encode value - {}", value);
    }
    return encoded;
  }

  /**
   * Encodes baggage value according <a href="https://www.w3.org/TR/baggage/#value">W3C RFC</a>.
   *
   * @param value The baggage value.
   * @return The encoded baggage value.
   */
  static String encodeBaggage(final String value) {
    // Fix encoding to comply with https://www.w3.org/TR/baggage/#value and use percent-encoding
    // (RFC3986)
    // for space ( ) instead of plus (+) from 'application/x-www-form' MIME encoding
    return encode(value).replace("+", "%20");
  }

  /** URL decode value */
  static String decode(final String value) {
    String decoded = value;
    try {
      decoded = URLDecoder.decode(value, "UTF-8");
    } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
      log.debug("Failed to decode value - {}", value);
    }
    return decoded;
  }

  static String firstHeaderValue(final String value) {
    if (value == null) {
      return null;
    }

    int firstComma = value.indexOf(',');
    return firstComma == -1 ? value : value.substring(0, firstComma).trim();
  }
}
