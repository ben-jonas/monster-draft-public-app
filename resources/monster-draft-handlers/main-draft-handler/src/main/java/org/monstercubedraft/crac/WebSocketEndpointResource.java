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
 * <p>The lazy fetch in {@link #endpoint()} is the only thing that ever caches a value, and it's the
 * load-bearing guarantee. {@link #beforeCheckpoint} deliberately does <em>not</em> cache what it
 * fetches — it only warms up the SsmClient (TLS handshake, credential resolution, JIT/classloading
 * of the SDK request path), exactly like {@link DynamoDbClientResource}'s throwaway
 * {@code describeTable} call. If it cached the value instead, that value would be frozen into the
 * SnapStart snapshot and replayed unchanged into every future restored environment — including ones
 * spun up long after a redeploy of the API stack changes the real endpoint, since a snapshot is
 * only retaken when this function is republished, not when the API stack changes. Checkpointing
 * always happens before any real traffic reaches this function (this function can't receive
 * messages until the API stack, which owns the SQS integration, is itself deployed), so by leaving
 * {@code endpoint} unset here, every restored environment resolves a fresh value on its own first
 * real use instead.
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
      // Result deliberately discarded — see class javadoc. This call exists purely to warm up the
      // SSM client before the snapshot is taken; endpoint() does the real, cached fetch.
      ssmClient.getParameter(GetParameterRequest.builder().name(parameterName).build());
    } catch (Exception e) {
      System.out.println("Could not warm up SSM client during checkpoint: " + e);
    }
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {}

  /**
   * Returns the cached callback URL, blocking to fetch and cache it from SSM on first call (see
   * class javadoc for why {@link #beforeCheckpoint} never populates this itself). Throws if SSM is
   * unreachable or the parameter is missing, so callers processing an SQS message can let it fail
   * and retry rather than silently proceeding without an endpoint.
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
