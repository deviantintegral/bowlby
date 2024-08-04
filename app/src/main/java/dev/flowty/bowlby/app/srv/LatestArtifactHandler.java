package dev.flowty.bowlby.app.srv;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import dev.flowty.bowlby.app.github.Entity.NamedArtifact;
import dev.flowty.bowlby.app.github.Entity.Repository;
import dev.flowty.bowlby.app.github.Entity.Run;
import dev.flowty.bowlby.app.github.Entity.Workflow;
import dev.flowty.bowlby.app.github.GithubApiClient;
import dev.flowty.bowlby.app.html.Html;

/**
 * Handles requests to:
 * <dl>
 * <dt><code>/latest/owner/repo/workflow</code></dt>
 * <dd>presents links to <code>/latest/owner/repo/workflow/artifact</code></dd>
 * <dt><code>/latest/owner/repo/workflow/artifact</code></dt>
 * <dd>redirects to <code>/artifacts/owner/repo/artifactId</code></dd>
 * </dl>
 */
public class LatestArtifactHandler implements HttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger( LatestArtifactHandler.class );

  /**
   * The set of repos that we allow ourselves to serve from
   */
  private final Set<Repository> repos;
  private final GithubApiClient client;
  private final Duration cacheValidity;

  private record LatestArtifact(Set<NamedArtifact> artifacts, Instant expiry) {
  }

  private final Map<Workflow, LatestArtifact> artifactCache = new HashMap<>();

  /**
   * @param repos         The set of repos that we allow ourselves to serve from
   * @param client        How to interact with github
   * @param cacheValidity How long latest artifact IDs will be cached for
   */
  public LatestArtifactHandler( Set<Repository> repos, GithubApiClient client,
      Duration cacheValidity ) {
    this.repos = repos;
    this.client = client;
    this.cacheValidity = cacheValidity;
  }

  @Override
  public void handle( HttpExchange exchange ) throws IOException {
    try {
      LOG.debug( "{}", exchange.getRequestURI() );
      if( !"GET".equalsIgnoreCase( exchange.getRequestMethod() ) ) {
        ServeUtil.showLinkForm( exchange, 501, "Only GET supported" );
        return;
      }

      Deque<String> path = Stream.of( exchange.getRequestURI().getPath().split( "/" ) )
          .filter( e -> !e.isEmpty() )
          .collect( toCollection( ArrayDeque::new ) );

      if( path.size() < 4 ) {
        ServeUtil.showLinkForm( exchange, 404, "insufficient path" );
        return;
      }
      if( !"latest".equals( path.poll() ) ) {
        ServeUtil.showLinkForm( exchange, 500, "unexpected root" );
      }
      Repository repo = new Repository( path.poll(), path.poll() );
      if( !repos.isEmpty() && !repos.contains( repo ) ) {
        // we're limited to particular repos, and that isn't one of them
        ServeUtil.showLinkForm( exchange, 403, "forbidden repository addressed" );
        return;
      }
      Workflow workflow = new Workflow( repo, path.poll() );

      LatestArtifact latest = getLatest( workflow );
      if( latest == null ) {
        ServeUtil.showLinkForm( exchange, 502, "Failed to find latest artifacts of " + workflow );
        return;
      }

      if( path.isEmpty() ) {
        // the path has no indication of which artifact we're interested in
        showArtifactLinks( exchange, 200, workflow, latest );
        return;
      }

      String desired = path.poll();
      NamedArtifact selected = latest.artifacts().stream()
          .filter( a -> a.name().equals( desired ) )
          .findFirst()
          .orElse( null );

      if( selected == null ) {
        // the path is asking for an artifact that doesn't exist
        showArtifactLinks( exchange, 404, workflow, latest );
        return;
      }

      ServeUtil.redirect( exchange, String.format(
          "/artifacts/%s/%s/%s/%s",
          workflow.repo().owner(), workflow.repo().repo(), selected.artifact().id(),
          path.stream().collect( joining( "/" ) ) ) );
    }
    catch( Exception e ) {
      LOG.error( "request handling failure!", e );
      ServeUtil.showLinkForm( exchange, 500, "Unexpected failure" );
    }
  }

  /**
   * The latest artifact probably won't change very often, so let's avoid hitting
   * the API every time
   *
   * @param workflow The workflow
   * @return The latest set of artifacts from that workflow
   */
  private LatestArtifact getLatest( Workflow workflow ) {

    // prune the cache
    Instant now = Instant.now();
    artifactCache.values().removeIf( v -> v.expiry.isBefore( now ) );

    LatestArtifact cached = artifactCache.get( workflow );
    if( cached != null ) {
      LOG.debug( "using cached artifact for {}, valid until {}",
          workflow, cached.expiry );
    }
    else {
      Run latest = client.getLatestRun( workflow );
      if( latest == null ) {
        return null;
      }
      Set<NamedArtifact> artifacts = client.getArtifacts( latest );
      if( artifacts == null ) {
        return null;
      }
      cached = new LatestArtifact( artifacts, now.plus( cacheValidity ) );
      artifactCache.put( workflow, cached );
    }

    return cached;
  }

  private void showArtifactLinks( HttpExchange exchange, int status, Workflow workflow,
      LatestArtifact latest ) throws IOException {
    BiConsumer<Html, NamedArtifact> artifactItem = ( html, artifact ) -> {
      html.li( i -> i.a(
          String.format( "/latest/%s/%s/%s/%s",
              workflow.repo().owner(),
              workflow.repo().repo(),
              workflow.name(),
              artifact.name() ),
          artifact.name() ) );
    };
    ServeUtil.respond( exchange, status, new Html()
        .head( h -> h
            .title( "bowlby" ) )
        .body( b -> b
            .h1( h -> h
                .a( "/", "bowlby" ) )
            .conditional( c -> c
                .p( "No artifacts found for " + workflow.name() ) )
            .on( latest.artifacts().isEmpty() )
            .conditional( c -> c
                .p( "These stable links will redirect to the latest artifacts for the ",
                    workflow.name(), " workflow. ",
                    "Feel free to append path components to address files within the artifacts" )
                .ul( u -> u
                    .repeat( artifactItem ).over( latest.artifacts() ) )
                .p( "If you use these links after ", latest.expiry,
                    ", then they might redirect to a newer artifact" ) )
            .on( !latest.artifacts().isEmpty() ) )
        .toString() );
  }

}
