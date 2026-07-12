package org.monstercubedraft.crac;

import java.util.Objects;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * Lazily resolves and caches MainDraftHandler's WebSocket callback URL from SSM Parameter Store
 * (published by MonsterDraftPublicAppApiStack).
 *
 * <p>The lazy fetch in {@link #endpoint()} is the load-bearing guarantee, not {@link
 * #beforeCheckpoint}: on the very first deploy, this function's SnapStart snapshot is taken before
 * the API stack has published the parameter (LambdaStack deploys before ApiStack), so priming will
 * fail there and every restored instance — including ones spun up by a scale-out event — falls
 * through to {@link #endpoint()} on first use. Priming only starts skipping the SSM round trip on
 * an invocation's critical path once a later deploy republishes the function after the parameter
 * exists.
 */
public class WebSocketEndpointResource implements Resource {

  private final SsmClient ssmClient;
  private final String parameterName;
  private volatile String endpoint;

  public WebSocketEndpointResource(String parameterName) {
    this(SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build(), parameterName);
  }

  public WebSocketEndpointResource(SsmClient ssmClient, String parameterName) {
    this.ssmClient = Objects.requireNonNull(ssmClient);
    this.parameterName = Objects.requireNonNull(parameterName);
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    try {
      fetch();
    } catch (Exception e) {
      // Best-effort; see class javadoc. endpoint() retries on first real use if this didn't land.
      System.out.println("Could not prime WebSocket callback URL from SSM during checkpoint: " + e);
    }
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {}

  /**
   * Returns the cached callback URL, blocking to fetch and cache it from SSM on first use if
   * priming didn't already populate it. Throws if SSM is unreachable/the parameter is missing, so
   * callers processing an SQS message can let it fail and retry rather than silently proceeding
   * without an endpoint.
   */
  public String endpoint() {
    String cached = endpoint;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (endpoint == null) {
        fetch();
      }
      return endpoint;
    }
  }

  private void fetch() {
    endpoint =
        ssmClient
            .getParameter(GetParameterRequest.builder().name(parameterName).build())
            .parameter()
            .value();
  }
}
